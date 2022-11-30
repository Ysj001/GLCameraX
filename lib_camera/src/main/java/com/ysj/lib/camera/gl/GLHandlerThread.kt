package com.ysj.lib.camera.gl

import android.os.Handler
import android.os.HandlerThread

/**
 * 通过 [HandlerThread] 管理 [EGLEnv]。
 *
 * @author Ysj
 * Create time: 2022/4/13
 */
class GLHandlerThread(
    name: String = "GLHandlerThread",
    private val onExited: ((EGLEnv?) -> Unit)? = null,
    private val onError: ((Exception) -> Unit)? = null,
    private val eglManager: EGLManager = EGLManager.Builder().build(),
) : HandlerThread(name) {

    val handler by lazy { Handler(looper) }

    val eglEnv: EGLEnv get() = checkNotNull(eglManager.eglEnv)

    private var exited = false

    @Volatile
    var ending = false
        private set

    override fun run() {
        try {
            eglManager.setupEGL()
            super.run()
        } catch (e: InterruptedException) {
            // 不处理
        } catch (e: Exception) {
            onError(e)
        } finally {
            onExited()
        }
    }

    override fun quit(): Boolean {
        val quit = super.quit()
        if (!quit) return false
        interrupt()
        while (true) {
            if (ending) {
                break
            }
        }
        return quit
    }

    override fun quitSafely(): Boolean {
        val quitSafely = super.quitSafely()
        if (!quitSafely) return false
        interrupt()
        while (true) {
            if (ending) {
                break
            }
        }
        return quitSafely
    }

    /**
     * 同步执行。
     */
    fun exec(block: (EGLEnv) -> Unit) {
        if (ending) return
        if (currentThread() == this) {
            block(eglEnv)
            return
        }
        var finished = false
        val posted = handler.post {
            synchronized(this) {
                try {
                    block(eglEnv)
                } finally {
                    finished = true
                    notifyAll()
                }
            }
        }
        synchronized(this) {
            while (posted && !ending && !finished) {
                try {
                    wait()
                } catch (e: InterruptedException) {
                    interrupted()
                }
            }
        }
    }

    private fun onError(e: Exception) {
        ending = true
        onError?.invoke(e) ?: throw e
    }

    private fun onExited() {
        ending = true
        onExited?.invoke(eglManager.eglEnv)
        eglManager.stopEGLContext()
        synchronized(this) {
            exited = true
            notifyAll()
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Any.notifyAll() = (this as Object).notifyAll()

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Any.wait(timeout: Long = 0, nanos: Int = 0) = (this as Object).wait(timeout, nanos)

}