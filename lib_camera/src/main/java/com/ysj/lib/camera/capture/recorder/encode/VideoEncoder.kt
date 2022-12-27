package com.ysj.lib.camera.capture.recorder.encode

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.annotation.GuardedBy
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.core.util.Pools
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
class VideoEncoder constructor(executor: Executor, config: VideoEncoderConfig) : Encoder<Encoder.SurfaceInput> {

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

    @SuppressLint("RestrictedApi")
    private val executor = CameraXExecutors.newSequentialExecutor(executor)

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
        if (state == Status.INITIALIZED || state == Status.RELEASED || isSignalEnd) {
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
                onRelease()
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

        private val dataPool = Pools.SimplePool<EncodeDataImpl>(5)

        private var hasFirstData = false
        private var hasEndData = false

        private var lastPresentationTimeUs = 0L

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) = executor.execute {
            val data = dataPool.acquire() ?: EncodeDataImpl(dataPool)
            data.reset()
            data.codec = codec
            data.bufferIndex = index
            data.bufferInfo = info
            data.buffer = codec.getOutputBuffer(index)!!
            if (hasEndData) {
                Log.d(TAG, "Drop buffer by already reach end of stream.")
                data.close()
                return@execute
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                Log.d(TAG, "Drop buffer by codec config.")
                data.close()
                return@execute
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
                if (info.size <= 0) {
                    Log.d(TAG, "Drop buffer by invalid buffer size.")
                    data.close()
                    return@execute
                }
                if (info.presentationTimeUs <= lastPresentationTimeUs) {
                    try {
                        Log.d(TAG, "Drop buffer by out of order buffer from MediaCodec.")
                        data.close()
                    } catch (e: MediaCodec.CodecException) {
                        handleEncodeError(e)
                    }
                    return@execute
                }
                if (isSignalEnd) {
                    Log.d(TAG, "Drop buffer by not in start-stop range.")
                    data.close()
                    return@execute
                }
                if (!hasFirstData) {
                    hasFirstData = true
                    callback(callback = { onEncodeStart() })
                }
                lastPresentationTimeUs = info.presentationTimeUs
                val needEnd = state == Status.PENDING_STOP || state == Status.PENDING_RELEASE
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
            } else {
                hasEndData = true
                val needEncode = info.size > 0 && info.presentationTimeUs > lastPresentationTimeUs
                when (state) {
                    Status.PENDING_STOP -> {
                        if (needEncode) {
                            callback(
                                callback = {
                                    onEncodeData(data)
                                    executor.execute(::onStop)
                                },
                                onRejected = {
                                    data.close()
                                    onStop()
                                }
                            )
                        } else {
                            data.close()
                            onStop()
                        }
                    }
                    Status.PENDING_RELEASE -> {
                        if (needEncode) {
                            callback(
                                callback = {
                                    onEncodeData(data)
                                    executor.execute {
                                        onStop()
                                        onRelease()
                                    }
                                },
                                onRejected = {
                                    data.close()
                                    onStop()
                                    onRelease()
                                }
                            )
                        } else {
                            data.close()
                            onStop()
                            onRelease()
                        }
                    }
                    else -> data.close()
                }
            }
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

    private class EncodeDataImpl(private val pool: Pools.Pool<EncodeDataImpl>) : EncodeData {

        var codec: MediaCodec? = null
        var bufferInfo: MediaCodec.BufferInfo? = null
        var bufferIndex: Int = -1
        var buffer: ByteBuffer? = null

        private val closed = AtomicBoolean(false)

        override fun buffer(): ByteBuffer {
            check(!this.closed.get()) { "encoded data is closed." }
            val buffer = checkNotNull(this.buffer)
            val bufferInfo = checkNotNull(this.bufferInfo)
            buffer.position(bufferInfo.offset)
            buffer.limit(bufferInfo.offset + bufferInfo.size)
            return buffer
        }

        override fun bufferInfo(): MediaCodec.BufferInfo = checkNotNull(bufferInfo)
        override fun presentationTimeUs() = checkNotNull(bufferInfo).presentationTimeUs
        override fun isKeyFrame(): Boolean = checkNotNull(bufferInfo).flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        override fun size() = checkNotNull(bufferInfo).size

        override fun close() {
            if (closed.getAndSet(true)) {
                return
            }
            checkNotNull(codec).releaseOutputBuffer(bufferIndex, false)
            pool.release(this)
        }

        fun reset() {
            closed.set(false)
            codec = null
            bufferInfo = null
            bufferIndex = -1
            buffer = null
        }
    }
}