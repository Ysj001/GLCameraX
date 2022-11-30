package com.ysj.lib.camera.capture.recorder.encode.config

import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer

/*
 * 编解码相关扩展，便于使用。
 *
 * @author Ysj
 * Create time: 2022/11/28
 */

internal fun MediaFormat.value(key: String, default: Int): Int =
    if (containsKey(key)) getInteger(key) else default

internal fun MediaFormat.value(key: String, default: Long): Long =
    if (containsKey(key)) getLong(key) else default

internal fun MediaFormat.value(key: String, default: Float): Float =
    if (containsKey(key)) getFloat(key) else default

internal fun MediaFormat.value(key: String, default: String): String =
    if (containsKey(key)) getString(key) ?: default else default

internal fun MediaFormat.value(key: String, default: ByteBuffer): ByteBuffer =
    if (containsKey(key)) getByteBuffer(key) ?: default else default

internal val MediaFormat.mime get() = getString(MediaFormat.KEY_MIME)!!
internal val MediaFormat.width get() = getInteger(MediaFormat.KEY_WIDTH)
internal val MediaFormat.height get() = getInteger(MediaFormat.KEY_HEIGHT)
internal val MediaFormat.durationUs get() = getLong(MediaFormat.KEY_DURATION)

internal var MediaFormat.rotation
    set(value) = setInteger("rotation-degrees", value)
    get() = value("rotation-degrees", 0)

internal var MediaFormat.bitrate
    set(value) = setInteger(MediaFormat.KEY_BIT_RATE, value)
    get() = value(MediaFormat.KEY_BIT_RATE, -1)

internal var MediaFormat.frameRate
    set(value) = setInteger(MediaFormat.KEY_FRAME_RATE, value)
    get() = value(MediaFormat.KEY_FRAME_RATE, -1)

internal var MediaFormat.iFrameInterval
    set(value) = setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, value)
    get() = value(MediaFormat.KEY_I_FRAME_INTERVAL, -1)

/** Constants are declared in [MediaCodecInfo.CodecCapabilities] */
internal var MediaFormat.colorFormat
    set(value) = setInteger(MediaFormat.KEY_COLOR_FORMAT, value)
    get() = value(MediaFormat.KEY_COLOR_FORMAT, -1)

internal val MediaFormat.maxInputSize get() = getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)