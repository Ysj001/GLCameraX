package com.ysj.lib.camera.gl.factory

import android.opengl.*

/**
 * 用于创建和销毁 [EGLSurface]。
 *
 * @author Ysj
 * Create time: 2022/4/6
 */
interface EGLSurfaceFactory {

    fun createSurface(display: EGLDisplay, config: EGLConfig, context: EGLContext, window: Any): EGLSurface?

    fun destroySurface(display: EGLDisplay, surface: EGLSurface)

    open class Default : EGLSurfaceFactory {

        override fun createSurface(display: EGLDisplay, config: EGLConfig, context: EGLContext, window: Any): EGLSurface? {
            return EGL14.eglCreateWindowSurface(display, config, window, intArrayOf(EGL14.EGL_NONE), 0)
        }

        override fun destroySurface(display: EGLDisplay, surface: EGLSurface) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
        }
    }
}