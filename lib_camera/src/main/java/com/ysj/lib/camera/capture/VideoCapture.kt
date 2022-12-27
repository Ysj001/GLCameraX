package com.ysj.lib.camera.capture

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import com.ysj.lib.camera.capture.recorder.VideoOutput
import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.program.Common2DProgram
import com.ysj.lib.camera.gl.program.CommonOESProgram
import com.ysj.lib.camera.gl.texture.GLTexture
import com.ysj.lib.camera.gl.window.EGLWindow
import com.ysj.lib.camera.render.CameraInfo
import com.ysj.lib.camera.render.CameraRenderer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * 视频捕获。
 * - 注意：如果输入的是 2d 纹理，则需要确保该纹理已经经过矫正。
 *
 * @author Ysj
 * Create time: 2022/10/31
 */
class VideoCapture<T : VideoOutput> constructor(private val context: Context, val output: T) : CameraRenderer {

    companion object {
        private const val TAG = "VideoCapture"
    }

    @Volatile
    private var glExecutor: Executor? = null
    private var env: EGLEnv? = null

    private lateinit var programOES: CommonOESProgram
    private lateinit var program2D: Common2DProgram

    private var info: CameraInfo? = null

    private var request: SurfaceRequest? = null
    private var resolution: Size? = null

    private var degree = 0

    override fun onAttach(glExecutor: Executor, env: EGLEnv) {
        this.glExecutor = glExecutor
        this.env = env
        this.programOES = CommonOESProgram(context.assets)
        this.program2D = Common2DProgram(context.assets)
        this.output.onAttach()
    }

    override fun onDetach() {
        this.output.onDetach()
        this.request = null
        this.resolution = null
        this.info = null
        this.degree = 0
        this.glExecutor = null
        this.env = null
    }

    override fun onCameraInfo(info: CameraInfo) {
        this.info = info
        // 矫正相机的 oes 纹理
        Matrix.setIdentityM(programOES.matrix, 0)
        val lensFacing = info.lensFacing
        val degree = info.sensorRotationDegrees
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            // SurfaceView/TextureView automatically mirrors the Surface for front camera, which
            // needs to be compensated by mirroring the Surface around the upright direction of the
            // output image.
            if (degree == 90 || degree == 270) {
                Matrix.scaleM(programOES.matrix, 0, 1f, -1f, 1f)
            } else {
                Matrix.scaleM(programOES.matrix, 0, -1f, 1f, 1f)
            }
        }
        Matrix.rotateM(programOES.matrix, 0, degree.toFloat(), 0f, 0f, 1f)
        // 默认 CameraRender 处理后的 2d 纹理是正的了，但由于和屏幕坐标不一致要翻转一下
        Matrix.setIdentityM(program2D.matrix, 0)
        Matrix.scaleM(program2D.matrix, 0, 1f, -1f, 1f)
        // 请求编码的 surface
        val rotate = degree.let { it == 90 || it == 270 }
        resolution = if (rotate) Size(info.size.height, info.size.width) else info.size
        val surfaceRequest = SurfaceRequest(info)
        request = surfaceRequest
        output.onSurfaceRequest(surfaceRequest)
    }

    override fun onDraw(input: GLTexture, timestamp: Long): GLTexture {
        val window = this.request?.window ?: return input
        val env = this.env ?: return input
        val resolution = this.resolution ?: return input
        when (input.target) {
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES -> {
                window.selectCurrent(env)
                GLES20.glViewport(0, 0, resolution.width, resolution.height)
                programOES.texture = input
                programOES.run()
                window.swapBuffers(env)
            }
            GLES20.GL_TEXTURE_2D -> {
                window.selectCurrent(env)
                GLES20.glViewport(0, 0, resolution.width, resolution.height)
                program2D.texture = input
                program2D.run()
                window.swapBuffers(env)
            }
            else -> throw IllegalArgumentException("nonsupport texture type")
        }
        return input
    }

    /**
     * 设置输出视频的旋转角度。
     * @param degree 角度。注意只能是 90/180/270。
     */
    fun setRotation(degree: Int) {
        val executor = glExecutor
        if (executor == null) {
            this.degree = degree
            return
        }
        executor.execute {
            this.degree = degree
            val info = this.info
            if (info != null) {
                onCameraInfo(info)
            }
        }
    }

    private inner class SurfaceRequest(info: CameraInfo) : VideoOutput.SurfaceRequest {
        override val cameraId: String = info.cameraId
        override val size: Size = info.targetResolution
        override val degree: Int = this@VideoCapture.degree

        var window: EGLWindow<Surface>? = null
            private set

        override fun onRequest(surface: Surface) {
            glExecutor?.execute {
                window = EGLWindow(surface)
            }
            Log.d(TAG, "on surface requested. $glExecutor")
        }

        override fun onRelease(surface: Surface) {
            val executor = glExecutor
            if (executor != null) {
                val launch = CountDownLatch(1)
                try {
                    executor.execute {
                        try {
                            val glEnv = env
                            val glWindow = window
                            if (glEnv != null && glWindow != null) {
                                glWindow.release(glEnv)
                            }
                            window = null
                        } finally {
                            launch.countDown()
                        }
                    }
                    launch.await()
                } catch (e: RejectedExecutionException) {
                    Log.d(TAG, "window release rejected.")
                } catch (e: InterruptedException) {
                    Thread.interrupted()
                }
            }
            Log.d(TAG, "on surface released. $executor")
        }
    }

}