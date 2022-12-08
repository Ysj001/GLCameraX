package com.ysj.lib.camera.capture.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.CamcorderProfile
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresPermission
import com.ysj.lib.camera.capture.recorder.encode.*
import com.ysj.lib.camera.capture.recorder.encode.config.AudioEncoderConfig
import com.ysj.lib.camera.capture.recorder.encode.config.VideoEncoderConfig
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * [VideoOutput] 的默认实现。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
class Recorder private constructor(builder: Builder) : VideoOutput {

    companion object {
        private const val TAG = "Recorder"
    }

    enum class State {
        INITIALIZING,
        INITIALIZED,
        PENDING_START,
        STARTED,
        PAUSED,
    }

    private val videoEncoderFactory = builder.videoEncoderFactory
    private val audioEncoderFactory = builder.audioEncoderFactory

    // ============================== executed by executor ===============================

    private var state = State.INITIALIZING

    private var request: VideoOutput.SurfaceRequest? = null

    private var camcorderProfile: CamcorderProfile? = null

    private var surface: Surface? = null

    private var audioSource: AudioSource? = null

    private var muxer: MediaMuxer? = null
    private var videoEncoder: Encoder<Encoder.SurfaceInput>? = null
    private var audioEncoder: Encoder<Encoder.ByteBufferInput>? = null

    private var audioConfig: EncoderCallback.OutputConfig? = null
    private var videoConfig: EncoderCallback.OutputConfig? = null
    private var audioFirstData: EncodeData? = null
    private var videoFirstData: EncodeData? = null
    private var audioTrackIndex = -1
    private var videoTrackIndex = -1
    private var audioEncoderStopped = false
    private var videoEncoderStopped = false
    private var audioEncoderError: Throwable? = null
    private var videoEncoderError: Throwable? = null

    private var recordingBytes = 0L
    private var firstRecordingTimeUs = 0L
    private var recordingDurationUs = 0L

    // ====================================================================================

    // =============================== guarded by this ====================================

    @GuardedBy("this")
    private var recording: Recording? = null

    // ====================================================================================

    private val executor = Executors.newSingleThreadExecutor()
    private val audioEncoderExecutor = Executors.newSingleThreadExecutor()
    private val videoEncoderExecutor = Executors.newSingleThreadExecutor()

    override fun onAttach() = Unit

    override fun onDetach() = executor.execute {
        when (this.state) {
            State.INITIALIZING,
            State.INITIALIZED,
            State.PENDING_START -> Unit
            State.STARTED,
            State.PAUSED -> {
                stopInternal()
            }
        }
        this.state = State.INITIALIZING
        this.request = null
    }

    @SuppressLint("MissingPermission")
    override fun onSurfaceRequest(request: VideoOutput.SurfaceRequest) = executor.execute {
        when (this.state) {
            State.INITIALIZING,
            State.INITIALIZED -> {
                this.request = request
                this.state = State.INITIALIZED
            }
            State.PENDING_START -> {
                this.request = request
                this.state = State.INITIALIZED
                setupAndStartEncoder()
            }
            State.STARTED,
            State.PAUSED -> {
                stopInternal()
                this.request = request
                this.state = State.INITIALIZED
            }
        }
    }

    @Synchronized
    fun prepare(context: Context, output: File): Recording {
        check(this.recording == null) { "was prepared !" }
        val recording = Recording(this, context, output)
        this.recording = recording
        return recording
    }

    @SuppressLint("MissingPermission")
    private fun start() = executor.execute {
        when (this.state) {
            State.INITIALIZING -> this.state = State.PENDING_START
            State.INITIALIZED -> setupAndStartEncoder()
            else -> throw IllegalStateException("state error: ${this.state}")
        }
    }

    private fun stop() = executor.execute {
        when (this.state) {
            State.INITIALIZING,
            State.INITIALIZED -> Unit
            State.PENDING_START -> this.state = State.INITIALIZING
            State.STARTED,
            State.PAUSED -> {
                stopInternal()
                this.state = State.INITIALIZED
            }
        }
    }

    private fun stopInternal() {
        val request = checkNotNull(this.request)
        val surface = checkNotNull(this.surface)
        val audioSource = checkNotNull(this.audioSource)
        val audioEncoder = checkNotNull(this.audioEncoder)
        val videoEncoder = checkNotNull(this.videoEncoder)
        audioSource.stop()
        audioEncoder.stop()
        videoEncoder.stop()
        request.onRelease(surface)
        this.surface = null
        Log.d(TAG, "stopInternal")
    }

    private fun finalizeRecording() {
        Log.d(TAG, "waiting stopped. audio=${audioEncoderStopped} , video=${videoEncoderStopped}")
        if (!audioEncoderStopped || !videoEncoderStopped) {
            return
        }
        this.muxer?.stop()
        val recording: Recording
        synchronized(this) {
            recording = checkNotNull(this.recording) { "not prepare !" }
            this.recording = null
        }
        val audioEncoderError = this.audioEncoderError
        val videoEncoderError = this.videoEncoderError
        val event = when {
            audioEncoderError != null -> RecordEvent.Finalize(
                error = RecordEvent.ERROR_ENCODER,
                case = audioEncoderError,
                bytes = this.recordingBytes,
                durationUs = this.recordingDurationUs,
            )
            videoEncoderError != null -> RecordEvent.Finalize(
                error = RecordEvent.ERROR_ENCODER,
                case = videoEncoderError,
                bytes = this.recordingBytes,
                durationUs = this.recordingDurationUs,
            )
            else -> RecordEvent.Finalize(
                error = RecordEvent.ERROR_NO,
                case = null,
                bytes = this.recordingBytes,
                durationUs = this.recordingDurationUs,
            )
        }
        recording.sendEvent(event)

        this.audioEncoderStopped = false
        this.videoEncoderStopped = false
        this.audioConfig = null
        this.videoConfig = null
        this.audioTrackIndex = -1
        this.videoTrackIndex = -1
        this.audioEncoderError = null
        this.videoEncoderError = null

        this.recordingBytes = 0
        this.firstRecordingTimeUs = 0L
        this.recordingDurationUs = 0L

        reset()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun setupAndStartEncoder() {
        val recording: Recording = synchronized(this) {
            checkNotNull(this.recording) { "not prepare !" }
        }
        this.state = State.STARTED
        recording.sendEvent(RecordEvent.Start)
        val request = checkNotNull(this.request)
        val config = VideoEncoderConfig.default(request.size, 30)
        val videoEncoder = videoEncoderFactory.create(videoEncoderExecutor, config)
        val surface = videoEncoder.input.surface()
        val audioConfig = AudioEncoderConfig.default(2, 44100)
        val audioEncoder = audioEncoderFactory.create(audioEncoderExecutor, audioConfig)
        val audioSource = AudioSource(audioConfig, recording.context.applicationContext, audioEncoderExecutor)
        audioSource.setInputBuffer(audioEncoder.input)
        this.audioEncoder = audioEncoder
        this.audioSource = audioSource
        this.videoEncoder = videoEncoder
        this.surface = surface
        audioEncoder.setEncoderCallback(AudioEncoderCallback(), executor)
        videoEncoder.setEncoderCallback(VideoEncoderCallback(), executor)
        audioSource.start()
        audioEncoder.start()
        videoEncoder.start()
        request.onRequest(surface)
        Log.d(TAG, "setupAndStartEncoder")
    }

    private fun setupAndStartMediaMuxer() {
        val recording: Recording = synchronized(this) {
            checkNotNull(this.recording) { "not prepare !" }
        }
        check(this.muxer == null) { "Unable to set up media muxer when one already exists." }
        val audioFirstData = this.audioFirstData
        val audioConfig = this.audioConfig
        if (audioFirstData == null || audioConfig == null) {
            throw IllegalStateException("Audio is enabled but no audio sample is ready. Cannot start media muxer.")
        }
        val videoFirstData = this.videoFirstData
        val videoConfig = this.videoConfig
        if (videoFirstData == null || videoConfig == null) {
            throw IllegalStateException("Media muxer cannot be started without an encoded video frame.")
        }
        val request = checkNotNull(this.request)
        val muxer = MediaMuxer(recording.output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.setOrientationHint(request.degree)
        this.videoTrackIndex = muxer.addTrack(videoConfig.mediaFormat())
        this.audioTrackIndex = muxer.addTrack(audioConfig.mediaFormat())
        this.muxer = muxer
        muxer.start()
        // 写入首个数据
        writeVideoData(videoFirstData)
        writeAudioData(audioFirstData)
        videoFirstData.close()
        audioFirstData.close()
        this.audioFirstData = null
        this.videoFirstData = null
        Log.d(TAG, "setupAndStartMediaMuxer")
    }

    private fun writeAudioData(data: EncodeData) {
        val muxer = checkNotNull(this.muxer)
        recordingBytes += data.size()
        muxer.writeSampleData(audioTrackIndex, data.buffer(), data.bufferInfo())
    }

    private fun writeVideoData(data: EncodeData) {
        val muxer = checkNotNull(this.muxer)
        muxer.writeSampleData(videoTrackIndex, data.buffer(), data.bufferInfo())
        recordingBytes += data.size()
        if (firstRecordingTimeUs == 0L) {
            firstRecordingTimeUs = data.presentationTimeUs()
        }
        recordingDurationUs = data.presentationTimeUs() - firstRecordingTimeUs
        val recording: Recording = synchronized(this) {
            checkNotNull(this.recording) { "not prepare !" }
        }
        recording.sendEvent(RecordEvent.Status(
            bytes = recordingBytes,
            durationUs = recordingDurationUs,
        ))
    }

    private fun reset() {
        val audioSource = this.audioSource
        if (audioSource != null) {
            audioSource.destroy()
            this.audioSource = null
        }
        val audioEncoder = this.audioEncoder
        if (audioEncoder != null) {
            audioEncoder.release()
            this.audioEncoder = null
        }
        val videoEncoder = this.videoEncoder
        if (videoEncoder != null) {
            videoEncoder.release()
            this.videoEncoder = null
        }
        val muxer = this.muxer
        if (muxer != null) {
            muxer.runCatching {
                release()
            }.onFailure {
                Log.w(TAG, "release muxer failure. os-level=${Build.VERSION.SDK_INT} , brand=${Build.BRAND} , model=${Build.MODEL}", it)
            }
            this.muxer = null
        }
    }

    private inner class AudioEncoderCallback : EncoderCallback {

        override fun onEncodeStart() {
            Log.d(TAG, "Audio encoder start.")
        }

        override fun onEncodeStop() {
            audioEncoderStopped = true
            finalizeRecording()
        }

        override fun onEncodePause() {
            Log.d(TAG, "Audio encoder pause.")
        }

        override fun onEncodeError(e: Throwable) {
            audioEncoderStopped = true
            audioEncoderError = e
            finalizeRecording()
        }

        override fun onEncodeData(data: EncodeData) {
            if (this@Recorder.muxer == null) {
                this@Recorder.audioFirstData?.close()
                this@Recorder.audioFirstData = data
                if (this@Recorder.videoFirstData == null) {
                    Log.d(TAG, "Audio onEncodeData: wait video data...")
                } else {
                    Log.d(TAG, "Audio onEncodeData: wait muxer...")
                    setupAndStartMediaMuxer()
                }
                return
            }
            data.use {
                writeAudioData(data)
            }
        }

        override fun onOutputConfigUpdate(config: EncoderCallback.OutputConfig) {
            audioConfig = config
        }
    }

    private inner class VideoEncoderCallback : EncoderCallback {

        override fun onEncodeStart() {
            Log.d(TAG, "Video encoder start.")
        }

        override fun onEncodeStop() {
            videoEncoderStopped = true
            finalizeRecording()
        }

        override fun onEncodePause() {
            Log.d(TAG, "Video encoder pause.")
        }

        override fun onEncodeError(e: Throwable) {
            videoEncoderStopped = true
            videoEncoderError = e
            finalizeRecording()
        }

        override fun onEncodeData(data: EncodeData) {
            if (this@Recorder.muxer == null) {
                val videoFirstData = this@Recorder.videoFirstData
                if (videoFirstData != null) {
                    videoFirstData.close()
                    this@Recorder.videoFirstData = null
                }
                if (data.isKeyFrame()) {
                    this@Recorder.videoFirstData = data
                    if (this@Recorder.audioFirstData == null) {
                        Log.d(TAG, "Video onEncodeData: wait audio data...")
                    } else {
                        Log.d(TAG, "Video onEncodeData: wait muxer...")
                        setupAndStartMediaMuxer()
                    }
                } else {
                    checkNotNull(this@Recorder.videoEncoder).requestKeyFrame()
                    data.close()
                    Log.d(TAG, "Video onEncodeData: wait key frame...")
                }
                return
            }
            data.use {
                writeVideoData(it)
            }
        }

        override fun onOutputConfigUpdate(config: EncoderCallback.OutputConfig) {
            videoConfig = config
        }
    }

    class Recording internal constructor(
        private val recorder: Recorder,
        internal var context: Context,
        val output: File,
    ) {

        private var listenerExecutor: Executor? = null
        private var listener: EventListener? = null

        private var rejected = false

        fun start(listenerExecutor: Executor, listener: EventListener) {
            this.listenerExecutor = listenerExecutor
            this.listener = listener
            this.rejected = false
            this.recorder.start()
        }

        fun stop() {
            recorder.stop()
        }

        fun sendEvent(event: RecordEvent) {
            if (rejected) {
                return
            }
            val eventListener = checkNotNull(listener)
            val executor = checkNotNull(listenerExecutor)
            try {
                executor.execute {
                    eventListener.onEvent(event)
                }
            } catch (e: RejectedExecutionException) {
                Log.d(TAG, "send event rejected. ${e.message}")
                listener = null
                listenerExecutor = null
            }
        }

        fun interface EventListener {
            fun onEvent(recordEvent: RecordEvent)
        }
    }

    class Builder {

        internal var videoEncoderFactory = EncoderFactory(::VideoEncoder)

        internal var audioEncoderFactory = EncoderFactory(::AudioEncoder)

        fun setVideoEncoderFactory(factory: EncoderFactory<VideoEncoderConfig, Encoder.SurfaceInput>) = apply {
            this.videoEncoderFactory = factory
        }

        fun setAudioEncoderFactory(factory: EncoderFactory<AudioEncoderConfig, Encoder.ByteBufferInput>) = apply {
            this.audioEncoderFactory = factory
        }

        fun build(): Recorder {
            return Recorder(this)
        }
    }

}