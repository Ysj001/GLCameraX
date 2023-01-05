package com.ysj.camera.demo.renders.effects

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.program.AbsGLProgram
import com.ysj.lib.camera.gl.program.Common2DProgram
import com.ysj.lib.camera.gl.program.CommonOESProgram
import com.ysj.lib.camera.gl.texture.GLTexture
import com.ysj.lib.camera.render.CameraInfo
import com.ysj.lib.camera.render.CameraRenderer
import java.util.concurrent.Executor

/**
 * 处理切换相机的过渡效果。
 *
 * @author Ysj
 * Create time: 2022/12/27
 */
class CameraTransition(private val context: Context) : CameraRenderer {

    private var glExecutor: Executor? = null
    private var env: EGLEnv? = null

    private var info: CameraInfo? = null

    private var programOES: CommonOESProgram? = null
    private var program2D: Common2DProgram? = null

    private var texture: GLTexture? = null

    override fun onAttach(glExecutor: Executor, env: EGLEnv) {
        this.glExecutor = glExecutor
        this.env = env
        this.programOES = CommonOESProgram(context.assets)
        this.program2D = Common2DProgram(context.assets)
    }

    override fun onDetach() {
        program2D = null
        programOES = null
        texture = null
        info = null
        glExecutor = null
        env = null
    }

    override fun onCameraInfo(info: CameraInfo) {
        val oldInfo = this.info
        this.info = info
        oldInfo ?: return
        val eglSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW) ?: return
        val env = this.env ?: return
        val texture = this.texture ?: return
        val programOes = this.programOES ?: return
        val program2d = this.program2D ?: return
        if (eglSurface.nativeHandle == 0L) {
            return
        }
        val lensFacing = oldInfo.lensFacing
        val degree = oldInfo.sensorRotationDegrees
        val program: AbsGLProgram
        when (texture.target) {
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES -> {
                // 矫正相机的 oes 纹理
                Matrix.setIdentityM(programOes.matrix, 0)
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    if (degree == 90 || degree == 270) {
                        Matrix.scaleM(programOes.matrix, 0, 1f, -1f, 1f)
                    } else {
                        Matrix.scaleM(programOes.matrix, 0, -1f, 1f, 1f)
                    }
                }
                Matrix.rotateM(programOes.matrix, 0, degree.toFloat(), 0f, 0f, 1f)
                programOes.texture = texture
                program = programOes
            }
            GLES20.GL_TEXTURE_2D -> {
                // 默认 CameraRender 处理后的 2d 纹理是正的了，但由于和屏幕坐标不一致要翻转一下
                Matrix.setIdentityM(program2d.matrix, 0)
                Matrix.scaleM(program2d.matrix, 0, 1f, -1f, 1f)
                program2d.texture = texture
                program = program2d
            }
            else -> return
        }
        val resolution = when (degree) {
            90, 270 -> Size(oldInfo.size.height, oldInfo.size.width)
            else -> oldInfo.size
        }
        val resolutionNew = when (info.sensorRotationDegrees) {
            90, 270 -> Size(info.size.height, info.size.width)
            else -> info.size
        }
        val x = (resolutionNew.width - resolution.width) / 2
        val y = (resolutionNew.height - resolution.height) / 2
        EGL14.eglMakeCurrent(env.display, eglSurface, eglSurface, env.context)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(x, y, resolution.width, resolution.height)
        program.run()
        EGL14.eglSwapBuffers(env.display, eglSurface)
    }

    override fun onDraw(input: GLTexture, timestamp: Long): GLTexture {
        this.texture = input
        return input
    }

}