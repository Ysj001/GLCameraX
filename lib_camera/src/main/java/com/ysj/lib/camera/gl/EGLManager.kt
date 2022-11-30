package com.ysj.lib.camera.gl

import android.opengl.EGL14
import com.ysj.lib.camera.gl.factory.EGLConfigChooser
import com.ysj.lib.camera.gl.factory.EGLContextFactory
import com.ysj.lib.camera.gl.factory.EGLDisplayFactory

/**
 * OpenGL 环境管理器。
 *
 * @author Ysj
 * Create time: 2022/4/13
 */
class EGLManager private constructor(builder: Builder) {

    private val eglDisplayFactory = builder.eglDisplayFactory
    private val eglContextFactory = builder.eglContextFactory
    private val eglConfigChooser = builder.eglConfigChooser

    private var eglThread: Thread? = null

    // egl 环境
    var eglEnv: EGLEnv? = null
        private set
        get() {
            assert(eglThread?.let { it == Thread.currentThread() || Thread.holdsLock(it) } != null)
            return field
        }

    fun setupEGL() {
        eglThread = Thread.currentThread()
        val display = eglDisplayFactory.eglInitialize()
        val config = eglConfigChooser.chooseConfig(display)
        val context = eglContextFactory.createContext(display, config)
        if (context == EGL14.EGL_NO_CONTEXT) {
            checkEGLError("createContext")
        }
        eglEnv = EGLEnv(display, config, context)
    }

    fun stopEGLContext() {
        val eglEnv = this.eglEnv ?: return
        eglContextFactory.destroyContext(eglEnv.display, eglEnv.context)
        eglDisplayFactory.eglTerminate(eglEnv.display)
        this.eglEnv = null
        this.eglThread = null
    }

    class Builder {

        internal lateinit var eglDisplayFactory: EGLDisplayFactory

        internal lateinit var eglContextFactory: EGLContextFactory

        internal lateinit var eglConfigChooser: EGLConfigChooser

        fun setEGLConfigChooser(chooser: EGLConfigChooser) = apply {
            this.eglConfigChooser = chooser
        }

        fun setEGLContextFactory(factory: EGLContextFactory) = apply {
            this.eglContextFactory = factory
        }

        fun setEGLDisplayFactory(factory: EGLDisplayFactory) = apply {
            this.eglDisplayFactory = factory
        }

        fun build(): EGLManager {
            if (!::eglDisplayFactory.isInitialized) {
                eglDisplayFactory = EGLDisplayFactory.Default()
            }
            if (!::eglContextFactory.isInitialized) {
                eglContextFactory = EGLContextFactory.Default()
            }
            if (!::eglConfigChooser.isInitialized) {
                eglConfigChooser = EGLConfigChooser.Default()
            }
            return EGLManager(this)
        }
    }
}