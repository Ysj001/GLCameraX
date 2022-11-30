package com.ysj.lib.camera.gl.window

import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.EGLSurface
import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.factory.EGLSurfaceFactory


/**
 * 封装 OpenGL 用于显示的窗口。
 * - 注意：内部所有调用都应在 GL 环境的线程。
 *
 * @author Ysj
 * Create time: 2022/10/31
 */
open class EGLWindow<W>(var window: W?) {

    open val factory: EGLSurfaceFactory = EGLSurfaceFactory.Default()

    protected open var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    open fun selectCurrent(env: EGLEnv) {
        val window = checkNotNull(this.window)
        val (display, config, context) = env
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            eglSurface = checkNotNull(factory.createSurface(display, config, context, window))
        }
        check(eglSurface != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context))
    }

    open fun selectedNothing(env: EGLEnv) {
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            return
        }
        EGL14.eglMakeCurrent(env.display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    open fun release(env: EGLEnv) {
        factory.destroySurface(env.display, eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
        window = null
    }

    open fun swapBuffers(env: EGLEnv): Boolean {
        return EGL14.eglSwapBuffers(env.display, eglSurface)
    }

    open fun setPresentationTime(env: EGLEnv, time: Long) {
        EGLExt.eglPresentationTimeANDROID(env.display, eglSurface, time)
    }

    open fun isCurrent(env: EGLEnv, surface: EGLSurface): Boolean {
        return env.context == EGL14.eglGetCurrentContext()
            && surface == EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
    }
}