package com.ysj.lib.camera.capture.recorder.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import androidx.annotation.GuardedBy
import com.ysj.lib.camera.capture.recorder.encode.config.AudioEncoderConfig
import com.ysj.lib.camera.capture.recorder.encode.config.mime
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 音频编码器。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
class AudioEncoder constructor(private val executor: Executor, config: AudioEncoderConfig) : Encoder<Encoder.ByteBufferInput> {

    companion object {
        private const val TAG = "AudioEncoder"

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

    private val bufferInput = BufferInput()

    override val input: Encoder.ByteBufferInput = bufferInput

    init {
        Log.i(TAG, "init: $format , ${codec.name}")
        reset()
    }

    override fun start() = executor.execute {
        when (state) {
            Status.STARTED,
            Status.PENDING_STOP,
            Status.PENDING_RELEASE -> throw IllegalStateException("Audio encoder is started")
            Status.RELEASED -> throw IllegalStateException("Audio encoder is released")
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
            Status.RELEASED -> throw IllegalStateException("Audio encoder is released")
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
        encoderCallback = callback
        encoderCallbackExecutor = executor
    }

    override fun requestKeyFrame() = Unit

    private fun signalEndOfInputStream() {
        if (isSignalEnd) {
            return
        }
        val buffer = input.acquireBuffer()
        if (buffer == null) {
            executor.execute(::signalEndOfInputStream)
            return
        }
        buffer.setPresentationTimeUs(TimeUnit.NANOSECONDS.toMicros(System.nanoTime()))
        buffer.setEndOfStream(true)
        buffer.submit()
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
        bufferInput.release()
        this.state = Status.RELEASED
        Log.d(TAG, "onRelease")
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
                release()
            }
            Status.RELEASED -> Unit
        }
    }

    private fun reset() {
        codec.reset()
        codec.setCallback(MediaCodecCallback())
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        state = Status.INITIALIZED
        isSignalEnd = false
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

        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = executor.execute {
            bufferInput.offerBuffer(index)
        }

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

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) = executor.execute {
            handleEncodeError(e)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = executor.execute {
            callback(callback = { onOutputConfigUpdate { format } })
        }

    }

    private inner class BufferInput : Encoder.ByteBufferInput {

        private val freeBufferIndexQueue = ArrayDeque<Int>()

        override fun acquireBuffer(): Encoder.InputBuffer? {
            if (!isSignalEnd && state != Status.INITIALIZED && state != Status.RELEASED) {
                return InputBuffer(freeBufferIndexQueue.poll() ?: return null)
            }
            return null
        }

        fun release() {
            freeBufferIndexQueue.clear()
        }

        fun offerBuffer(bufferIndex: Int) {
            freeBufferIndexQueue.offer(bufferIndex)
        }
    }

    private inner class InputBuffer(val bufferIndex: Int) : Encoder.InputBuffer {

        private val terminated = AtomicBoolean(false)

        private val buffer = codec.getInputBuffer(bufferIndex)!!

        private var presentationTimeUs = 0L

        private var isEndOfStream = false

        override fun buffer(): ByteBuffer {
            checkTerminated()
            return buffer
        }

        override fun setPresentationTimeUs(presentationTimeUs: Long) {
            checkTerminated()
            require(presentationTimeUs >= 0L)
            this.presentationTimeUs = presentationTimeUs
        }

        override fun setEndOfStream(isEnd: Boolean) {
            checkTerminated()
            this.isEndOfStream = isEnd
        }

        override fun submit(): Boolean {
            if (terminated.getAndSet(true)) {
                return false
            }
            codec.queueInputBuffer(
                bufferIndex,
                buffer.position(),
                buffer.limit(),
                presentationTimeUs,
                if (isEndOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            )
            return true
        }

        override fun cancel(): Boolean {
            if (terminated.getAndSet(true)) {
                return false
            }
            codec.queueInputBuffer(bufferIndex, 0, 0, 0, 0)
            return true
        }

        private fun checkTerminated() = check(!terminated.get()) {
            "The buffer is submitted or canceled."
        }
    }

    private inner class EncodeDataImpl : EncodeData {

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
        override fun isKeyFrame(): Boolean = true
        override fun size() = bufferInfo.size

        override fun close() {
            if (closed.getAndSet(true)) {
                return
            }
            try {
                codec.releaseOutputBuffer(bufferIndex, false)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "release output buffer failure. ${presentationTimeUs()} , $bufferIndex")
                throw e
            }
        }
    }
}