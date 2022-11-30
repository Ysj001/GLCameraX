package com.ysj.lib.camera.gl.texture

/**
 * 定义 GL 纹理。
 * - 注意：为确保安全，内部所有调用都应在 GL 线程中。
 *
 * @author Ysj
 * Create time: 2022/4/8
 */
interface GLTexture {
    /**
     * 纹理目标，表示是哪种纹理。
     */
    val target: Int

    /**
     * 纹理 id。注意要在 GL 环境的线程创建。
     */
    val id: Int

    /**
     * 释放该纹理资源。
     */
    fun release()
}