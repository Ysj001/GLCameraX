package com.ysj.camera.demo.renders

import androidx.annotation.IntRange
import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.texture.GLTexture
import com.ysj.lib.camera.render.CameraInfo
import com.ysj.lib.camera.render.CameraRenderer
import java.util.concurrent.Executor
import kotlin.math.min

/**
 * 用于计算渲染的 Fps。
 *
 * @author Ysj
 * Create time: 2022/12/5
 */
class FpsCapture(@IntRange(from = 1) bufferSize: Int, private val fpsCallback: (Double) -> Unit) : CameraRenderer {

    companion object {
        private const val NANOS_IN_SECOND = 1000000000.0
    }

    private var timestamps = LongArray(bufferSize)

    private var index = 0

    private var numSamples = 0


    override fun onAttach(glExecutor: Executor, env: EGLEnv) = reset()
    override fun onDetach() = reset()
    override fun onCameraInfo(info: CameraInfo) = reset()

    override fun onDraw(input: GLTexture, timestamp: Long): GLTexture {
        fpsCallback(recordTimestamp(timestamp))
        return input
    }

    private fun recordTimestamp(timestampNs: Long): Double {
        val nextIndex: Int = (index + 1) % timestamps.size
        val duration: Long = timestampNs - timestamps[index]
        timestamps[index] = timestampNs
        index = nextIndex
        numSamples = min(numSamples + 1, timestamps.size + 1)
        return if (numSamples == timestamps.size + 1) NANOS_IN_SECOND * timestamps.size / duration else Double.NaN
    }

    private fun reset() {
        numSamples = 0
        index = 0
    }
}