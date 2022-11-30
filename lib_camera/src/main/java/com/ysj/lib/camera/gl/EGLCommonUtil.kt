package com.ysj.lib.camera.gl

import android.opengl.EGL14
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL11

/*
 * EGL 通用工具
 *
 * @author Ysj
 * Create time: 2022/4/6
 */

fun FloatArray.createBuffer(): FloatBuffer {
    val buffer = ByteBuffer.allocateDirect(size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    buffer.put(this, 0, size).position(0)
    return buffer
}

fun checkEGLError(name: String) {
    val error = EGL14.eglGetError()
    if (error == EGL14.EGL_SUCCESS) return
    throw RuntimeException(formatEglError(name, error))
}

fun formatEglError(name: String, error: Int): String {
    return "$name failed: ${eglErrorString(error)}"
}

fun eglErrorString(error: Int): String = when (error) {
    EGL14.EGL_SUCCESS -> "EGL_SUCCESS"
    EGL14.EGL_NOT_INITIALIZED -> "EGL_NOT_INITIALIZED"
    EGL14.EGL_BAD_ACCESS -> "EGL_BAD_ACCESS"
    EGL14.EGL_BAD_ALLOC -> "EGL_BAD_ALLOC"
    EGL14.EGL_BAD_ATTRIBUTE -> "EGL_BAD_ATTRIBUTE"
    EGL14.EGL_BAD_CONFIG -> "EGL_BAD_CONFIG"
    EGL14.EGL_BAD_CONTEXT -> "EGL_BAD_CONTEXT"
    EGL14.EGL_BAD_CURRENT_SURFACE -> "EGL_BAD_CURRENT_SURFACE"
    EGL14.EGL_BAD_DISPLAY -> "EGL_BAD_DISPLAY"
    EGL14.EGL_BAD_MATCH -> "EGL_BAD_MATCH"
    EGL14.EGL_BAD_NATIVE_PIXMAP -> "EGL_BAD_NATIVE_PIXMAP"
    EGL14.EGL_BAD_NATIVE_WINDOW -> "EGL_BAD_NATIVE_WINDOW"
    EGL14.EGL_BAD_PARAMETER -> "EGL_BAD_PARAMETER"
    EGL14.EGL_BAD_SURFACE -> "EGL_BAD_SURFACE"
    EGL14.EGL_CONTEXT_LOST -> "EGL_CONTEXT_LOST"
    else -> "0x" + Integer.toHexString(error)
}