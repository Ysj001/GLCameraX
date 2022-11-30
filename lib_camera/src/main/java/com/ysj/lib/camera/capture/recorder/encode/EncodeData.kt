package com.ysj.lib.camera.capture.recorder.encode

import android.media.MediaCodec.BufferInfo
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * 封装由编码器生成的编码数据。
 *
 * @author Ysj
 * Create time: 2022/11/21
 */
interface EncodeData : Closeable {

    /**
     * 获取编码数据。
     */
    fun buffer(): ByteBuffer

    /**
     * 编码数据的信息。
     */
    fun bufferInfo(): BufferInfo

    /**
     * 获取编码数据的时间戳（us）
     */
    fun presentationTimeUs(): Long

    /**
     * 判断当前编码数据是否是关键帧。
     */
    fun isKeyFrame(): Boolean

    /**
     * 获取编码数据的大小
     */
    fun size(): Int

}