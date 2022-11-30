package com.ysj.lib.camera.capture.recorder.encode

import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.Executor

/**
 * 统一音频和视频的编码器接口。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
interface Encoder<I : Encoder.Input> {

    val input: I

    fun start()

    fun stop()

    fun pause()

    fun release()

    fun setEncoderCallback(callback: EncoderCallback, executor: Executor)

    fun requestKeyFrame()

    interface Input

    interface SurfaceInput : Input {
        fun surface(): Surface
    }

    interface ByteBufferInput : Input {

        fun acquireBuffer(): InputBuffer?

    }

    interface InputBuffer {

        fun buffer(): ByteBuffer

        fun setPresentationTimeUs(presentationTimeUs: Long)

        fun setEndOfStream(isEnd: Boolean)

        fun submit(): Boolean

        fun cancel(): Boolean

    }
}