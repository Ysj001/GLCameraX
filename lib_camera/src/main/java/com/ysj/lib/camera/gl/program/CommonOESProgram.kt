package com.ysj.lib.camera.gl.program

import android.content.res.AssetManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.ysj.lib.camera.gl.checkEGLError
import com.ysj.lib.camera.gl.createBuffer
import com.ysj.lib.camera.gl.texture.GLTexture

/**
 * 通用 OES 纹理程序。
 *
 * 包含：
 * - common_2d.vert
 * - common_oes.frag
 *
 * @author Ysj
 * Create time: 2022/4/7
 */
open class CommonOESProgram(asset: AssetManager) : AbsGLProgram() {

    protected open val vertexCoordinates by lazy(LazyThreadSafetyMode.NONE) {
        floatArrayOf(
            -1.0f, -1.0f, // 0 bottom left
            1.0f, -1.0f,  // 1 bottom right
            -1.0f, 1.0f,  // 2 top left
            1.0f, 1.0f    // 3 top right
        ).createBuffer()
    }

    protected open val textureCoordinates by lazy(LazyThreadSafetyMode.NONE) {
        floatArrayOf(
            0.0f, 1.0f,  // 0 top left
            1.0f, 1.0f,  // 1 top right
            0.0f, 0.0f,  // 2 bottom left
            1.0f, 0.0f   // 3 bottom right
        ).createBuffer()
    }

    open var texture: GLTexture? = null

    open val matrix = floatArrayOf(
        1f, 0f, 0f, 0f, // x
        0f, 1f, 0f, 0f, // y
        0f, 0f, 1f, 0f, // z
        0f, 0f, 0f, 1f, // w
    )

    override val program: Int by lazy(LazyThreadSafetyMode.NONE) {
        val vertSource = asset.open("glsl/common_2d.vert").use {
            it.reader().readText()
        }
        val fragSource = asset.open("glsl/common_oes.frag").use {
            it.reader().readText()
        }
        createProgram(
            GLES20.GL_VERTEX_SHADER to vertSource,
            GLES20.GL_FRAGMENT_SHADER to fragSource,
        )
    }

    protected val uMatrix by lazy(LazyThreadSafetyMode.NONE) {
        GLES20.glGetUniformLocation(program, "uMatrix").also {
            checkEGLError("glGetAttribLocation -- uMatrix")
        }
    }

    protected val aPosition by lazy(LazyThreadSafetyMode.NONE) {
        GLES20.glGetAttribLocation(program, "aPosition").also {
            checkEGLError("glGetAttribLocation -- aPosition")
        }
    }

    protected val aTextureXY by lazy(LazyThreadSafetyMode.NONE) {
        GLES20.glGetAttribLocation(program, "aTextureXY").also {
            checkEGLError("glGetAttribLocation -- aTextureXY")
        }
    }

    protected val vTextureXY by lazy(LazyThreadSafetyMode.NONE) {
        GLES20.glGetUniformLocation(program, "vTextureXY").also {
            checkEGLError("glGetAttribLocation -- vTextureXY")
        }
    }

    protected val uOESTexture by lazy(LazyThreadSafetyMode.NONE) {
        GLES20.glGetUniformLocation(program, "uOESTexture").also {
            checkEGLError("glGetAttribLocation -- uOESTexture")
        }
    }

    override fun run() {
        val texture = this.texture ?: return
        require(texture.target == GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        // 使用该程序
        GLES20.glUseProgram(program)
        checkEGLError("glUseProgram")
        updateMatrix(matrix)
        draw(texture)
        // 程序执行结束
        GLES20.glFinish()
    }

    /**
     * 应用该矩阵。
     * - 注意该方法虚在 GL 线程调用。
     */
    open fun updateMatrix(matrix: FloatArray, transpose: Boolean = false) {
        GLES20.glUniformMatrix4fv(uMatrix, 1, transpose, matrix, 0)
        checkEGLError("glUniformMatrix4fv")
    }

    open fun draw(texture: GLTexture) {
        val vcSize = vertexCoordinates.capacity()
        val tcSize = textureCoordinates.capacity()
        // 设置顶点坐标，绘制时会依次取点传给着色器
        vertexCoordinates.clear()
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(
            aPosition, vcSize / 4, GLES20.GL_FLOAT,
            false, 0, vertexCoordinates
        )
        // 设置纹理坐标，绘制时会依次取点传给着色器
        textureCoordinates.clear()
        GLES20.glEnableVertexAttribArray(aTextureXY)
        GLES20.glVertexAttribPointer(
            aTextureXY, tcSize / 4, GLES20.GL_FLOAT,
            false, 0, textureCoordinates
        )
        // 激活并设置纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        checkEGLError("glActiveTexture")
        GLES20.glBindTexture(texture.target, texture.id)
        checkEGLError("glBindTexture")
        GLES20.glUniform1i(uOESTexture, 0)
        checkEGLError("glUniform1i -- uOESTexture")
        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vcSize / 2)
        checkEGLError("glDrawArrays")
    }
}