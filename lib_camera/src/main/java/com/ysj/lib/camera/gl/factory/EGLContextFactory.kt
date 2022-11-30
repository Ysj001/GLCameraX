package com.ysj.lib.camera.gl.factory

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.os.Build
import com.ysj.lib.camera.gl.checkEGLError

/**
 * 用于创建和销毁 [EGLContext]。
 *
 * @author Ysj
 * Create time: 2022/4/2
 */
interface EGLContextFactory {

    fun createContext(display: EGLDisplay, config: EGLConfig): EGLContext

    fun destroyContext(display: EGLDisplay, context: EGLContext)

    open class Default : EGLContextFactory {

        override fun createContext(display: EGLDisplay, config: EGLConfig): EGLContext {
            val attribList = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) 3 else 2,
                EGL14.EGL_NONE
            )
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attribList, 0)
            if (context == null || context === EGL14.EGL_NO_CONTEXT) {
                checkEGLError("eglCreateContext")
            }
            return context
        }

        override fun destroyContext(display: EGLDisplay, context: EGLContext) {
            if (!EGL14.eglDestroyContext(display, context)) {
                checkEGLError("eglDestroyContext")
            }
        }

    }
}