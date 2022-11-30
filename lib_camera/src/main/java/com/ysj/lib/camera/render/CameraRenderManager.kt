package com.ysj.lib.camera.render

import androidx.annotation.GuardedBy
import androidx.core.os.ExecutorCompat
import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.GLHandlerThread
import com.ysj.lib.camera.gl.texture.GLTexture
import java.util.concurrent.Executor

/**
 * 相机渲染管理器，用于管理 [CameraRenderer]。
 *
 * @author Ysj
 * Create time: 2022/10/31
 */
class CameraRenderManager {

    @GuardedBy("this")
    private val pendingExecutes = ArrayList<(EGLEnv) -> Unit>()

    @GuardedBy("this")
    private var glExecutor: Executor? = null

    private var glThread: GLHandlerThread? = null
    private var env: EGLEnv? = null
    private var info: CameraInfo? = null

    private val renderers = ArrayList<CameraRenderer>()

    private val pendingSurface = ArrayList<CameraRenderer>()

    fun attach(env: EGLEnv, glThread: GLHandlerThread) {
        this.env = env
        this.glThread = glThread
        synchronized(this) {
            this.glExecutor = ExecutorCompat.create(glThread.handler)
            val iterator = this.pendingExecutes.iterator()
            while (iterator.hasNext()) {
                iterator.next().invoke(env)
                iterator.remove()
            }
        }
    }

    fun detach() {
        assert(Thread.currentThread() == glThread)
        glThread = null
        env = null
        info = null
        pendingSurface.clear()
        val iterator = renderers.iterator()
        while (iterator.hasNext()) {
            iterator.next().onDetach()
            iterator.remove()
        }
        synchronized(this) {
            glExecutor = null
        }
    }

    fun onSurfaceRequest(cameraInfo: CameraInfo) {
        assert(Thread.currentThread() == glThread)
        this.info = cameraInfo
        for (index in renderers.indices) {
            renderers[index].onCameraInfo(cameraInfo)
        }
        val iterator = pendingSurface.iterator()
        while (iterator.hasNext()) {
            val renderer = iterator.next()
            renderer.onCameraInfo(cameraInfo)
            renderers.add(renderer)
            iterator.remove()
        }
    }

    fun render(input: GLTexture, timestamp: Long): GLTexture {
        assert(Thread.currentThread() == glThread)
        var nextInput: GLTexture = input
        for (index in renderers.indices) {
            nextInput = renderers[index].onDraw(nextInput, timestamp)
        }
        return nextInput
    }

    fun addRenderer(renderer: CameraRenderer) = execute {
        if (renderer in pendingSurface || renderer in renderers) {
            return@execute
        }
        renderer.onAttach(glExecutor!!, it)
        val info = this.info
        if (info != null) {
            renderer.onCameraInfo(info)
            renderers.add(renderer)
        } else {
            pendingSurface.add(renderer)
        }
    }

    fun removeRenderer(transform: CameraRenderer) = execute {
        if (pendingSurface.remove(transform)) {
            return@execute
        }
        if (renderers.remove(transform)) {
            transform.onDetach()
        }
    }

    /**
     * 执行在 GL 线程中。
     */
    fun execute(block: (EGLEnv) -> Unit) {
        val executor = this.glExecutor
        if (executor == null) {
            pendingExecutes.add(block)
            return
        }
        executor.execute {
            block(checkNotNull(env))
        }
    }
}