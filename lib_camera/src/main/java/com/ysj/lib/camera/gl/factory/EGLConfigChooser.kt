package com.ysj.lib.camera.gl.factory

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay

/**
 * 用于初始化 [EGLConfig]
 *
 * @author Ysj
 * Create time: 2022/4/6
 */
interface EGLConfigChooser {

    fun chooseConfig(display: EGLDisplay): EGLConfig

    class Default : EGLConfigChooser {
        override fun chooseConfig(display: EGLDisplay): EGLConfig {
            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            require(
                EGL14.eglChooseConfig(
                    display,
                    attribList,
                    0,
                    configs,
                    0,
                    configs.size,
                    numConfigs,
                    0
                )
            ) { "eglChooseConfig failed" }
            return configs[0]!!
        }
    }
}