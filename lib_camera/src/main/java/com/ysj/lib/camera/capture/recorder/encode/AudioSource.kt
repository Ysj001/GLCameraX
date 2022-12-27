package com.ysj.lib.camera.capture.recorder.encode

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import com.ysj.lib.camera.capture.recorder.encode.config.AudioEncoderConfig
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 封装音频源，作为音频数据的生产者。
 *
 * @author Ysj
 * Create time: 2022/11/20
 */
class AudioSource @RequiresPermission(Manifest.permission.RECORD_AUDIO) constructor(
    config: AudioEncoderConfig,
    context: Context,
    executor: Executor,
) {

    companion object {
        private const val TAG = "AudioSource"
    }

    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val source = MediaRecorder.AudioSource.CAMCORDER

    private enum class State {
        INITIALIZED,
        STARTED,
        DESTROYED
    }

    class AccessException(message: String, cause: Throwable? = null) : Exception(message, cause)

    @SuppressLint("RestrictedApi")
    private val executor = CameraXExecutors.newSequentialExecutor(executor)

    private var state: State = State.INITIALIZED

    private val bufferSize: Int
    private val recorder: AudioRecord
    private val recordingCallback: AudioManager.AudioRecordingCallback?

    private var bufferInput: Encoder.ByteBufferInput? = null

    private var callback: Callback? = null
    private var callbackExecutor: Executor? = null

    init {
        if (!isSupported(config.channelCount, config.sampleRate, encoding)) {
            throw UnsupportedOperationException(
                "not supported config: sampleRate=${config.sampleRate} , channelCount=${config.channelCount} , format=${encoding}"
            )
        }
        bufferSize = minBufferSize(config.channelCount, config.sampleRate, encoding) * 2
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(config.sampleRate)
                    .setChannelMask(channelCountToMask(config.channelCount))
                    .setEncoding(encoding)
                    .build()
                val builder = AudioRecord.Builder()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setContext(context)
                }
                recorder = builder.setAudioSource(source)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } else {
                recorder = AudioRecord(
                    source,
                    config.sampleRate,
                    channelCountToConfig(config.channelCount),
                    encoding,
                    bufferSize
                )
            }
        } catch (e: IllegalArgumentException) {
            throw AccessException("Unable to create AudioRecord", e)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw AccessException("Unable to initialize AudioRecord")
        }
        recordingCallback = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) null else RecordingCallback().also {
            recorder.registerAudioRecordingCallback(executor, it)
        }
    }

    fun setCallback(callback: Callback, executor: Executor) = executor.execute {
        when (this.state) {
            State.INITIALIZED -> {
                this.callback = callback
                this.callbackExecutor = executor
            }
            else -> throw IllegalStateException(
                "The audio recording callback must be registered before the audio source is started."
            )
        }
    }

    fun setInputBuffer(bufferInput: Encoder.ByteBufferInput) = executor.execute {
        when (this.state) {
            State.INITIALIZED,
            State.STARTED -> {
                if (this.bufferInput != bufferInput) {
                    resetInputBuffer(bufferInput)
                }
            }
            State.DESTROYED -> throw IllegalStateException("$TAG is destroyed")
        }
    }

    fun start() = executor.execute {
        when (state) {
            State.INITIALIZED -> startSendingAudio()
            State.STARTED -> Unit
            State.DESTROYED -> throw IllegalStateException("$TAG is destroyed")
        }
    }

    fun stop() = executor.execute {
        when (state) {
            State.INITIALIZED -> Unit
            State.STARTED -> stopSendingAudio()
            State.DESTROYED -> throw IllegalStateException("$TAG is destroyed")
        }
    }

    fun destroy() = executor.execute {
        when (state) {
            State.INITIALIZED,
            State.STARTED -> {
                bufferInput = null
                callback = null
                callbackExecutor = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recordingCallback != null) {
                    recorder.unregisterAudioRecordingCallback(recordingCallback)
                }
                stopSendingAudio()
                recorder.release()
                state = State.DESTROYED
            }
            State.DESTROYED -> Unit
        }
    }

    private fun notifyError(throwable: Throwable) {
        val callback = this.callback ?: return
        val callbackExecutor = this.callbackExecutor ?: return
        callbackExecutor.execute {
            callback.onError(throwable)
        }
    }

    private fun sendNextAudio() {
        if (state != State.STARTED) {
            return
        }
        val input = this.bufferInput ?: return
        val inputBuffer = input.acquireBuffer()
        if (inputBuffer == null) {
            executor.execute(::sendNextAudio)
            return
        }
        val presentationTimeUs = generatePresentationTimeUs()
        val buffer = inputBuffer.buffer()
        val length = recorder.read(buffer, bufferSize)
        if (length > 0) {
            buffer.limit(length)
            inputBuffer.setPresentationTimeUs(presentationTimeUs)
            inputBuffer.submit()
        } else {
            Log.w(TAG, "Unable to read data from AudioRecord.")
            inputBuffer.cancel()
        }
        executor.execute(::sendNextAudio)
    }

    private fun startSendingAudio() {
        if (state == State.STARTED) {
            return
        }
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Unable to start AudioRecord with state: ${recorder.recordingState}"
            }
        } catch (e: IllegalStateException) {
            state = State.INITIALIZED
            notifyError(AccessException("Unable to start the audio record.", e))
            return
        }
        state = State.STARTED
        sendNextAudio()
    }

    private fun stopSendingAudio() {
        if (state != State.STARTED) {
            return
        }
        state = State.INITIALIZED
        try {
            recorder.stop()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_STOPPED) {
                "Unable to stop AudioRecord with state: ${recorder.recordingState}"
            }
        } catch (e: IllegalStateException) {
            notifyError(AccessException("Failed to stop AudioRecord", e))
        }
    }

    private fun resetInputBuffer(bufferInput: Encoder.ByteBufferInput) {
        this.bufferInput = bufferInput
        if (state == State.STARTED) {
            startSendingAudio()
        } else {
            stopSendingAudio()
        }
    }

    private fun generatePresentationTimeUs(): Long {
        var presentationTimeUs: Long = -1L
        if (Build.VERSION.SDK_INT >= 24) {
            val audioTimestamp = AudioTimestamp()
            if (recorder.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                presentationTimeUs = TimeUnit.NANOSECONDS.toMicros(audioTimestamp.nanoTime)
            } else {
                Log.w(TAG, "Unable to get audio timestamp")
            }
        }
        if (presentationTimeUs == -1L) {
            presentationTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime())
        }
        return presentationTimeUs
    }

    private fun isSupported(channelCount: Int, sampleRate: Int, encoding: Int): Boolean {
        return channelCount > 0 && sampleRate > 0 && minBufferSize(channelCount, sampleRate, encoding) > 0
    }

    private fun channelCountToConfig(channelCount: Int): Int {
        return if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
    }

    /**
     * mask 和 config 实际上表示不同的东西，所以还是区分成两个方法
     * @see AudioFormat.Builder.setChannelMask
     */
    private fun channelCountToMask(channelCount: Int): Int {
        return if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
    }

    private fun minBufferSize(channelCount: Int, sampleRate: Int, encoding: Int): Int {
        return AudioRecord.getMinBufferSize(sampleRate, channelCountToConfig(channelCount), encoding)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private inner class RecordingCallback : AudioManager.AudioRecordingCallback() {

        private val sourceSilence = AtomicBoolean(false)

        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            super.onRecordingConfigChanged(configs)
            val callback = this@AudioSource.callback ?: return
            val callbackExecutor = this@AudioSource.callbackExecutor ?: return
            for (config in configs) {
                if (config.clientAudioSessionId == recorder.audioSessionId) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val silenced = config.isClientSilenced
                        if (sourceSilence.getAndSet(silenced) != silenced) {
                            callbackExecutor.execute {
                                callback.onSilenced(silenced)
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    interface Callback {

        /**
         * 当音频源被静音时回调。此时音频源将继续提供被静音的音频数据。
         */
        fun onSilenced(silenced: Boolean)

        /**
         * 当音频源遇到错误时调用。
         */
        fun onError(throwable: Throwable)
    }
}