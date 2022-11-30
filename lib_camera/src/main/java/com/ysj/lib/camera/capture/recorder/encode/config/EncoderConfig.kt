package com.ysj.lib.camera.capture.recorder.encode.config

import android.media.MediaFormat

/**
 * 配置编码器所需的参数。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
interface EncoderConfig {

    val mimeType: String

    fun toMediaFormat(): MediaFormat
}