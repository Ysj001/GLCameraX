package com.ysj.lib.camera.gl.program

import android.opengl.GLES20
import com.ysj.lib.camera.gl.checkEGLError

/**
 * 抽象的 GL 程序。
 *
 * @author Ysj
 * Create time: 2022/4/8
 */
abstract class AbsGLProgram : Runnable {

    /**
     * [createProgram] 所返回的。
     */
    abstract val program: Int

    /**
     * 释放。
     */
    open fun release() {
        GLES20.glDeleteProgram(program)
    }

    /**
     * 创建程序。
     *
     * @param typeAndSource 该程序所需的着色器的类型和源码。
     * @return 返回程序指针，0 代表创建失败。
     */
    protected fun createProgram(vararg typeAndSource: Pair<Int, String>): Int {
        // 创建 program
        var program = GLES20.glCreateProgram()
        checkEGLError("glCreateProgram")
        if (program == 0) return 0
        // 创建 shader
        for (i in typeAndSource.indices) {
            val (type, source) = typeAndSource[i]
            val shader = createShader(type, source)
            if (shader == 0) {
                GLES20.glDeleteProgram(program)
                return 0
            }
            GLES20.glAttachShader(program, shader)
            checkEGLError("glAttachShader")
        }
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        return program
    }

    /**
     * 创建着色器。
     *
     * @param type 着色器类型
     * @param source 着色器源码
     * @return 返回着色器指针，0 代表创建失败。
     */
    protected fun createShader(type: Int, source: String): Int {
        var shader = GLES20.glCreateShader(type)
        checkEGLError("glCreateShader")
        GLES20.glShaderSource(shader, source)
        checkEGLError("glShaderSource")
        GLES20.glCompileShader(shader)
        checkEGLError("glCompileShader")
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == GLES20.GL_FALSE) {
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }

}