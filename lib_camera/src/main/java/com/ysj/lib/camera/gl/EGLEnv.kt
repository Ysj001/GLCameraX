package com.ysj.lib.camera.gl

import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay

/**
 * 包装 OpenGL 环境
 *
 * @author Ysj
 * Create time: 2022/4/13
 */
data class EGLEnv(
    val display: EGLDisplay,
    val config: EGLConfig,
    val context: EGLContext,
)