package com.ysj.lib.camera.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.core.os.ExecutorCompat
import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.GLHandlerThread
import com.ysj.lib.camera.gl.checkEGLError
import com.ysj.lib.camera.gl.program.Common2DProgram
import com.ysj.lib.camera.gl.program.CommonOESProgram
import com.ysj.lib.camera.gl.texture.OESGLTexture
import com.ysj.lib.camera.gl.window.EGLWindow
import java.io.Closeable

/**
 * 提供相机的 OpenGL 环境。
 *
 * @author Ysj
 * Create time: 2022/10/28
 */
class CameraGLEnv(private val context: Context) : SurfaceTexture.OnFrameAvailableListener, Closeable {

    companion object {
        private const val TAG = "GLSurfaceProvider"
    }

    private val glThread = GLHandlerThread(
        "CameraXGLThread",
        onExited = ::onGLThreadExited,
    ).also {
        it.start()
    }

    private val glExecutor = ExecutorCompat.create(glThread.handler)

    private val program = CommonOESProgram(context.assets)
    private var program2D = Common2DProgram(context.assets)
    private var glWindow: EGLWindow<Any>? = null

    private var resolution: Size? = null

    private var renderManager: CameraRenderManager? = null

    fun onSurfaceRequested(cameraInfo: CameraInfo, request: SurfaceRequest, window: Any) {
        if (glThread.ending) {
            request.willNotProvideSurface()
            return
        }
        if (Thread.currentThread() != glThread) {
            glExecutor.execute {
                onSurfaceRequested(cameraInfo, request, window)
            }
            return
        }
        val lensFacing = cameraInfo.lensFacing
        val degree = cameraInfo.sensorRotationDegrees
        resolution = when (degree) {
            90, 270 -> Size(cameraInfo.size.height, cameraInfo.size.width)
            else -> cameraInfo.size
        }
        // 矫正相机的 oes 纹理
        Matrix.setIdentityM(program.matrix, 0)
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            // SurfaceView/TextureView automatically mirrors the Surface for front camera, which
            // needs to be compensated by mirroring the Surface around the upright direction of the
            // output image.
            if (degree == 90 || degree == 270) {
                Matrix.scaleM(program.matrix, 0, 1f, -1f, 1f)
            } else {
                Matrix.scaleM(program.matrix, 0, -1f, 1f, 1f)
            }
        }
        Matrix.rotateM(program.matrix, 0, degree.toFloat(), 0f, 0f, 1f)
        // 默认 CameraRender 处理后的 2d 纹理是正的了，但由于和屏幕坐标不一致要翻转一下
        Matrix.setIdentityM(program2D.matrix, 0)
        Matrix.scaleM(program2D.matrix, 0, 1f, -1f, 1f)
        bindWindow(glThread.eglEnv, window)
        renderManager?.onSurfaceRequest(cameraInfo)
        request.provideSurface(output(cameraInfo.size), glExecutor) {
            Log.i(TAG, "Safe to release surface.")
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        val window = this.glWindow
        if (window == null) {
            surfaceTexture.release()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && surfaceTexture.isReleased) {
            Log.w(TAG, "onFrameAvailable: skip frame")
            return
        }
        val env = glThread.eglEnv
        window.selectCurrent(env)
        try {
            surfaceTexture.updateTexImage()
        } catch (e: Exception) {
            Log.w(TAG, "onFrameAvailable: skip frame.", e)
            return
        }
        val resolution = this.resolution ?: return
        val texture = program.texture
        val renderManager = this.renderManager
        if (renderManager == null) {
            window.selectCurrent(env)
            GLES20.glViewport(0, 0, resolution.width, resolution.height)
            program.run()
        } else if (texture != null) {
            val outputTexture = renderManager.render(texture, surfaceTexture.timestamp)
            window.selectCurrent(env)
            GLES20.glViewport(0, 0, resolution.width, resolution.height)
            if (outputTexture == texture) {
                program.run()
            } else {
                program2D.texture = outputTexture
                program2D.run()
            }
        }
        if (!window.swapBuffers(env)) {
            checkEGLError("$TAG eglSwapBuffers")
        }
    }

    override fun close() {
        glThread.quitSafely()
    }

    fun unbindWindow() = glThread.exec {
        glWindow?.release(it)
        glWindow = null
    }

    fun setRenderManager(manager: CameraRenderManager?) = glThread.exec {
        renderManager?.detach()
        renderManager = manager
        manager?.attach(it, glThread)
    }

    private fun onGLThreadExited(env: EGLEnv?) {
        resolution = null
        renderManager?.detach()
        renderManager = null
        if (env != null) {
            glWindow?.release(env)
            glWindow = null
            program.texture?.release()
        }
    }

    private fun output(size: Size): Surface {
        program.texture?.release()
        val texture = OESGLTexture()
        program.texture = texture
        val surface = texture.getSurface(this)
        texture.setTextureSize(size.width, size.height)
        return surface
    }

    private fun bindWindow(env: EGLEnv, window: Any) {
        glWindow?.release(env)
        glWindow = EGLWindow(window)
        glWindow?.selectCurrent(env)
    }

}