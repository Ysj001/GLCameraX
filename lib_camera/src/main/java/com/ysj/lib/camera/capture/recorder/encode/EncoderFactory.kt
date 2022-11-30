package com.ysj.lib.camera.capture.recorder.encode

import com.ysj.lib.camera.capture.recorder.encode.config.EncoderConfig
import java.util.concurrent.Executor

/**
 * 编码器工厂。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
fun interface EncoderFactory<C : EncoderConfig, I : Encoder.Input> {

    fun create(executor: Executor, config: C): Encoder<I>
}