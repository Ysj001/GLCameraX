package com.ysj.lib.camera.gl.factory

import android.opengl.EGL14
import android.opengl.EGLDisplay

/**
 * 用于创建 [EGLDisplay] 并初始化 EGL。销毁 EGL。
 *
 * @author Ysj
 * Create time: 2022/4/6
 */
interface EGLDisplayFactory {

    /**
     * 创建 [EGLDisplay] 并初始化 EGL。
     */
    fun eglInitialize(): EGLDisplay

    /**
     * 销毁 EGL。
     */
    fun eglTerminate(display: EGLDisplay)

    open class Default : EGLDisplayFactory {

        override fun eglInitialize(): EGLDisplay {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display === EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("eglGetDisplay failed")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                throw RuntimeException("eglInitialize failed")
            }
            return display
        }

        override fun eglTerminate(display: EGLDisplay) {
            EGL14.eglTerminate(display)
        }
    }
}