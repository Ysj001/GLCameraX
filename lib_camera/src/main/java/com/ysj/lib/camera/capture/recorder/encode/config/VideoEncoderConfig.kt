package com.ysj.lib.camera.capture.recorder.encode.config

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Rational
import android.util.Size

/**
 * 视频编码器参数。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
class VideoEncoderConfig private constructor(builder: Builder) : EncoderConfig {

    companion object {

        // 基于 (720p H264 高质量) 的基础配置。
        private const val VIDEO_BITRATE_BASE = 14_000_000
        private val VIDEO_SIZE_BASE = Size(1280, 720)
        private const val VIDEO_FRAME_RATE_BASE = 30

        fun builder(): Builder = Builder()
            .setMimeType(MediaFormat.MIMETYPE_VIDEO_AVC)
            .setColorFormat(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            .setIFrameInterval(1)

        fun default(size: Size, frameRate: Int): VideoEncoderConfig {
            val resolvedBitrate = scaleAndClampBitrate(size, frameRate)
            return builder()
                .setSize(size)
                .setBitrate(resolvedBitrate)
                .setFrameRate(frameRate)
                .build()
        }

        private fun scaleAndClampBitrate(size: Size, frameRate: Int): Int {
            // Scale bitrate to match current frame rate
            val frameRateRatio = Rational(frameRate, VIDEO_FRAME_RATE_BASE)
            // Scale bitrate depending on number of actual pixels relative to profile's number of pixels.
            val widthRatio = Rational(size.width, VIDEO_SIZE_BASE.width)
            val heightRatio = Rational(size.height, VIDEO_SIZE_BASE.height)
            val resolvedBitrate = VIDEO_BITRATE_BASE *
                frameRateRatio.toDouble() *
                widthRatio.toDouble() *
                heightRatio.toDouble()
            return resolvedBitrate.toInt()
        }
    }

    override val mimeType: String = builder.mimeType!!

    val size: Size = builder.size!!
    val colorFormat: Int = builder.colorFormat!!
    val bitrate: Int = builder.bitrate!!
    val frameRate: Int = builder.frameRate!!
    val iFrameInterval: Int = builder.iFrameInterval!!

    override fun toMediaFormat(): MediaFormat {
        val format = MediaFormat.createVideoFormat(mimeType, size.width, size.height)
        format.colorFormat = colorFormat
        format.bitrate = bitrate
        format.frameRate = frameRate
        format.iFrameInterval = iFrameInterval
        return format
    }

    class Builder {
        internal var mimeType: String? = null
        internal var size: Size? = null
        internal var colorFormat: Int? = null
        internal var bitrate: Int? = null
        internal var frameRate: Int? = null
        internal var iFrameInterval: Int? = null

        fun setMimeType(mimeType: String) = apply {
            this.mimeType = mimeType
        }

        fun setSize(size: Size) = apply {
            this.size = size
        }

        fun setColorFormat(format: Int) = apply {
            this.colorFormat = format
        }

        fun setFrameRate(frameRate: Int) = apply {
            this.frameRate = frameRate
        }

        fun setBitrate(bitrate: Int) = apply {
            this.bitrate = bitrate
        }

        fun setIFrameInterval(interval: Int) = apply {
            this.iFrameInterval = interval
        }

        fun build(): VideoEncoderConfig {
            val sb = StringBuilder()
            if (mimeType == null) {
                sb.append(" mimeType")
            }
            if (size == null) {
                sb.append(" size")
            }
            if (colorFormat == null) {
                sb.append(" colorFormat")
            }
            if (bitrate == null) {
                sb.append(" bitrate")
            }
            if (iFrameInterval == null) {
                sb.append(" iFrameInterval")
            }
            if (sb.isNotEmpty()) {
                throw IllegalArgumentException("Missing required properties:$sb")
            }
            return VideoEncoderConfig(this)
        }
    }
}