package com.ysj.lib.camera.capture.recorder.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.annotation.GuardedBy
import com.ysj.lib.camera.capture.recorder.encode.config.VideoEncoderConfig
import com.ysj.lib.camera.capture.recorder.encode.config.mime
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 视频编码器。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
class VideoEncoder constructor(private val executor: Executor, config: VideoEncoderConfig) : Encoder<Encoder.SurfaceInput> {

    companion object {
        private const val TAG = "VideoEncoder"

        private val EMPTY_EXECUTOR = Executor { }
    }

    private enum class Status {
        INITIALIZED,
        STARTED,
        PAUSED,
        PENDING_STOP,
        PENDING_RELEASE,
        RELEASED,
    }

    private val format: MediaFormat = config.toMediaFormat()
    private val codec: MediaCodec = MediaCodec.createEncoderByType(format.mime)

    @GuardedBy("this")
    private var encoderCallback: EncoderCallback = EncoderCallback.EMPTY

    @GuardedBy("this")
    private var encoderCallbackExecutor: Executor = EMPTY_EXECUTOR

    private var isSignalEnd = false

    private var state = Status.INITIALIZED

    override val input: Encoder.SurfaceInput = SurfaceInput()

    init {
        Log.i(TAG, "init: $format , ${codec.name}")
        reset()
    }

    override fun start() = executor.execute {
        when (state) {
            Status.STARTED,
            Status.PENDING_STOP,
            Status.PENDING_RELEASE -> throw IllegalStateException("Video encoder is started")
            Status.RELEASED -> throw IllegalStateException("Video encoder is released")
            Status.INITIALIZED -> {
                codec.start()
                state = Status.STARTED
            }
            Status.PAUSED -> TODO("Not yet implemented")
        }
    }

    override fun stop() = executor.execute {
        when (state) {
            Status.INITIALIZED, Status.PENDING_STOP, Status.PENDING_RELEASE -> Unit
            Status.STARTED,
            Status.PAUSED -> {
                signalEndOfInputStream()
                state = Status.PENDING_STOP
            }
            Status.RELEASED -> throw IllegalStateException("Video encoder is released")
        }
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun release() = executor.execute {
        when (state) {
            Status.INITIALIZED -> onRelease()
            Status.STARTED,
            Status.PAUSED,
            Status.PENDING_STOP -> {
                signalEndOfInputStream()
                state = Status.PENDING_RELEASE
            }
            Status.PENDING_RELEASE, Status.RELEASED -> Unit
        }
    }

    @Synchronized
    override fun setEncoderCallback(callback: EncoderCallback, executor: Executor) {
        this.encoderCallback = callback
        this.encoderCallbackExecutor = executor
    }

    override fun requestKeyFrame() = executor.execute {
        when (state) {
            Status.RELEASED -> throw IllegalStateException("Video encoder is released")
            Status.STARTED -> {
                val bundle = Bundle()
                bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                codec.setParameters(bundle)
            }
            else -> Unit
        }
    }

    private fun signalEndOfInputStream() {
        if (isSignalEnd) {
            return
        }
        codec.signalEndOfInputStream()
        isSignalEnd = true
        Log.d(TAG, "signalEndOfInputStream")
    }

    private fun onStop() {
        codec.flush()
        codec.stop()
        reset()
        callback(callback = { onEncodeStop() })
        Log.d(TAG, "onStop")
    }

    private fun onRelease() {
        codec.reset()
        codec.release()
        (input as SurfaceInput).release()
        this.state = Status.RELEASED
        Log.d(TAG, "onRelease")
    }

    private fun reset() {
        codec.reset()
        codec.setCallback(MediaCodecCallback())
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        (input as SurfaceInput).resetSurface()
        state = Status.INITIALIZED
        isSignalEnd = false
    }

    private fun handleEncodeError(t: Throwable) = executor.execute {
        when (state) {
            Status.INITIALIZED -> {
                callback(callback = { onEncodeError(t) })
                reset()
            }
            Status.STARTED,
            Status.PENDING_STOP,
            Status.PENDING_RELEASE,
            Status.PAUSED -> {
                callback(callback = { onEncodeError(t) })
                reset()
            }
            Status.RELEASED -> Unit
        }
    }

    private inline fun callback(crossinline callback: EncoderCallback.() -> Unit, onRejected: (() -> Unit) = {}) {
        val encoderCallback: EncoderCallback
        val encoderCallbackExecutor: Executor
        synchronized(this) {
            encoderCallback = this.encoderCallback
            encoderCallbackExecutor = this.encoderCallbackExecutor
        }
        try {
            encoderCallbackExecutor.execute {
                callback(encoderCallback)
            }
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "Unable to post to the supplied executor.", e)
            onRejected.invoke()
        }
    }

    private inner class MediaCodecCallback : MediaCodec.Callback() {

        private var hasFirstData = false

        private var lastPresentationTimeUs = 0L

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) = executor.execute {
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                codec.releaseOutputBuffer(index, false)
                when (state) {
                    Status.PENDING_STOP -> onStop()
                    Status.PENDING_RELEASE -> {
                        onStop()
                        onRelease()
                    }
                    else -> Unit
                }
                return@execute
            }
            if (!hasFirstData) {
                hasFirstData = true
                callback(callback = { onEncodeStart() })
            }
            // MediaCodec may send out of order buffer
            if (info.presentationTimeUs <= lastPresentationTimeUs) {
                try {
                    Log.d(TAG, "Drop buffer by out of order buffer from MediaCodec.")
                    codec.releaseOutputBuffer(index, false)
                } catch (e: MediaCodec.CodecException) {
                    handleEncodeError(e)
                }
                return@execute
            }
            if (isSignalEnd) {
                try {
                    Log.d(TAG, "Drop buffer by not in start-stop range.")
                    codec.releaseOutputBuffer(index, false)
                } catch (e: MediaCodec.CodecException) {
                    handleEncodeError(e)
                }
                return@execute
            }
            lastPresentationTimeUs = info.presentationTimeUs
            val needEnd = state == Status.PENDING_STOP || state == Status.PENDING_RELEASE
            val data = EncodeDataImpl()
            data.codec = codec
            data.bufferIndex = index
            data.bufferInfo = info
            data.buffer = codec.getOutputBuffer(index)!!
            callback(
                callback = {
                    onEncodeData(data)
                    if (needEnd) {
                        // 要等最后一个数据写完才能停止，否则 releaseOutputBuffer 可能报状态异常
                        executor.execute(::signalEndOfInputStream)
                    }
                },
                onRejected = { data.close() }
            )
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            handleEncodeError(e)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            encoderCallback.onOutputConfigUpdate { format }
        }
    }

    private inner class SurfaceInput : Encoder.SurfaceInput {

        private var surface: Surface? = null

        override fun surface(): Surface {
            return checkNotNull(surface)
        }

        fun resetSurface() {
            surface?.release()
            surface = codec.createInputSurface()
        }

        fun release() {
            surface?.release()
            surface = null
        }
    }

    private class EncodeDataImpl : EncodeData {

        lateinit var codec: MediaCodec
        lateinit var bufferInfo: MediaCodec.BufferInfo
        var bufferIndex: Int = -1
        lateinit var buffer: ByteBuffer

        private val closed = AtomicBoolean(false)

        override fun buffer(): ByteBuffer {
            check(!closed.get()) { "encoded data is closed." }
            buffer.position(bufferInfo.offset)
            buffer.limit(bufferInfo.offset + bufferInfo.size)
            return buffer
        }

        override fun bufferInfo(): MediaCodec.BufferInfo = bufferInfo
        override fun presentationTimeUs() = bufferInfo.presentationTimeUs
        override fun isKeyFrame(): Boolean = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        override fun size() = bufferInfo.size

        override fun close() {
            if (closed.getAndSet(true)) {
                return
            }
            codec.releaseOutputBuffer(bufferIndex, false)
        }
    }
}