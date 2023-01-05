package com.ysj.camera.demo.renders.effects

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.camera.core.CameraSelector
import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.checkEGLError
import com.ysj.lib.camera.gl.program.CommonOESProgram
import com.ysj.lib.camera.gl.texture.GL2DTexture
import com.ysj.lib.camera.gl.texture.GLTexture
import com.ysj.lib.camera.render.CameraInfo
import com.ysj.lib.camera.render.CameraRenderer
import java.util.concurrent.Executor

/**
 * [GLES11Ext.GL_TEXTURE_EXTERNAL_OES] 纹理转 [GLES20.GL_TEXTURE_2D]。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
class OesTo2d(private val context: Context) : CameraRenderer {

    private var program: CommonOESProgram? = null
    private var fbo: IntArray? = null
    private var output: GLTexture? = null

    override fun onAttach(glExecutor: Executor, env: EGLEnv) {
        program = CommonOESProgram(context.assets)
    }

    override fun onDetach() {
        program?.release()
        output?.release()
        fbo?.also {
            GLES20.glDeleteFramebuffers(1, it, 0)
            fbo = null
        }
    }

    override fun onCameraInfo(info: CameraInfo) {
        transform(info, checkNotNull(this.program).matrix)
        val fob = createFBO()
        this.fbo?.also { GLES20.glDeleteFramebuffers(1, it, 0) }
        this.fbo = fob
        this.output?.release()
        this.output = createOutput(info).bindFBO(fob)
    }

    override fun onDraw(input: GLTexture, timestamp: Long): GLTexture {
        require(input.target == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) { "input not oes texture" }
        val program = checkNotNull(this.program)
        val output = checkNotNull(this.output)
        val fbo = checkNotNull(fbo)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        program.texture = input
        program.run()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        return output
    }

    private fun GLTexture.bindFBO(fbo: IntArray) = apply {
        GLES20.glBindTexture(target, id)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            target,
            id,
            0
        )
        GLES20.glBindTexture(target, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun createFBO(): IntArray {
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        checkEGLError("glGenFramebuffers")
        return fbo
    }

    private fun createOutput(info: CameraInfo): GLTexture {
        val degrees = info.sensorRotationDegrees
        val texture = GL2DTexture()
        GLES20.glBindTexture(texture.target, texture.id)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            if (degrees == 90 || degrees == 270) info.size.height else info.size.width,
            if (degrees == 90 || degrees == 270) info.size.width else info.size.height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )
        GLES20.glBindTexture(texture.target, 0)
        return texture
    }

    private fun transform(info: CameraInfo, matrix: FloatArray) {
        val degree = info.sensorRotationDegrees
        Matrix.setIdentityM(matrix, 0)
        if (info.lensFacing == CameraSelector.LENS_FACING_FRONT) {
            // SurfaceView/TextureView automatically mirrors the Surface for front camera, which
            // needs to be compensated by mirroring the Surface around the upright direction of the
            // output image.
            if (degree == 90 || degree == 270) {
                Matrix.scaleM(matrix, 0, 1f, -1f, 1f)
            } else {
                Matrix.scaleM(matrix, 0, -1f, 1f, 1f)
            }
        }
        Matrix.rotateM(matrix, 0, degree.toFloat(), 0f, 0f, 1f)
        // oes 转完 2d 要翻转一下
        Matrix.scaleM(matrix, 0, 1f, -1f, 1f)
    }
}