package com.ysj.lib.camera.render

import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.texture.GLTexture
import java.util.concurrent.Executor

/**
 * 相机渲染器。
 * - 接口所有方法调用都在 GL 线程中。
 *
 * @author Ysj
 * Create time: 2022/10/31
 */
interface CameraRenderer {

    /**
     * 当附加到 GL 环境中时回调。
     */
    fun onAttach(glExecutor: Executor, env: EGLEnv)

    /**
     * 当从 GL 环境中移除时回调。
     */
    fun onDetach()

    /**
     * 当摄像头信息改变时回调。
     */
    fun onCameraInfo(info: CameraInfo)

    /**
     * 渲染。
     * - 提供一个输入纹理 [input]，你可以对它进行处理后输出一个新的纹理。
     *
     * @param timestamp 这个时间戳以纳秒为单位，单调递增。
     */
    fun onDraw(input: GLTexture, timestamp: Long): GLTexture
}