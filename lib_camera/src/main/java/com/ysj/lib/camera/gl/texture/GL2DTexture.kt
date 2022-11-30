package com.ysj.lib.camera.gl.texture

import android.opengl.GLES20
import com.ysj.lib.camera.gl.checkEGLError

/**
 * 普通的 2D 纹理。
 *
 * @author Ysj
 * Create time: 2022/11/1
 */
class GL2DTexture : GLTexture {

    override val target = GLES20.GL_TEXTURE_2D

    override val id: Int by lazy(LazyThreadSafetyMode.NONE) {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        val textureHandle = textures[0]
        GLES20.glBindTexture(target, textureHandle)
        checkEGLError("glBindTexture")

        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkEGLError("glTexParameter")

        GLES20.glBindTexture(target, 0)

        textureHandle
    }

    override fun release() {
        GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }

}