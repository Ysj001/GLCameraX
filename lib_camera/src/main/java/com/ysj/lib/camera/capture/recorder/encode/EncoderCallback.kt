package com.ysj.lib.camera.capture.recorder.encode

import android.media.MediaFormat
import java.util.concurrent.Executor

/**
 * 编码器回调。
 *
 * @author Ysj
 * Create time: 2022/11/3
 */
interface EncoderCallback {

    fun onEncodeStart()

    fun onEncodeStop()

    fun onEncodePause()

    fun onEncodeError(e: Throwable)

    fun onEncodeData(data: EncodeData)

    fun onOutputConfigUpdate(config: OutputConfig)

    fun interface OutputConfig {

        fun mediaFormat(): MediaFormat
    }

    object EMPTY : EncoderCallback {
        override fun onEncodeStart() = Unit
        override fun onEncodeStop() = Unit
        override fun onEncodePause() = Unit
        override fun onEncodeError(e: Throwable) = Unit
        override fun onEncodeData(data: EncodeData) = Unit
        override fun onOutputConfigUpdate(config: OutputConfig) = Unit
    }
}