package com.ysj.lib.camera.capture

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
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
import java.util.concurrent.Executor

/**
 * 视频捕获。
 * - 注意：如果输入的是 2d 纹理，则需要确保该纹理已经经过矫正。
 *
 * @author Ysj
 * Create time: 2022/10/31
 */
class VideoCapture<T : VideoOutput> constructor(private val context: Context, val output: T) : CameraRenderer {

    private var glExecutor: Executor? = null
    private var env: EGLEnv? = null

    private lateinit var programOES: CommonOESProgram
    private lateinit var program2D: Common2DProgram

    private var window: EGLWindow<Surface>? = null

    private var resolution: Size? = null

    override fun onAttach(glExecutor: Executor, env: EGLEnv) {
        this.glExecutor = glExecutor
        this.env = env
        this.programOES = CommonOESProgram(context.assets)
        this.program2D = Common2DProgram(context.assets)
        this.output.onAttach(glExecutor)
    }

    override fun onDetach() {
        resolution = null
        output.onDetach()
        window?.release(env!!)
        window = null
        env = null
    }

    override fun onCameraInfo(info: CameraInfo) {
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
        output.onSurfaceRequest(object : VideoOutput.SurfaceRequest {
            override val cameraId: String = info.cameraId
            override val size: Size = info.targetResolution
            override val degree: Int = 0
            override val onRequest: (Surface) -> Unit = {
                window = EGLWindow(it)
            }
            override val onRelease: (Surface) -> Unit = {
                window?.release(checkNotNull(env))
                window = null
            }
        })
    }

    override fun onDraw(input: GLTexture, timestamp: Long): GLTexture {
        val window = this.window ?: return input
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

}