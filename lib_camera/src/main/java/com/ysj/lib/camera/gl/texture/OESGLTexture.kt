package com.ysj.lib.camera.gl.texture

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import com.ysj.lib.camera.gl.checkEGLError

/**
 * OES 纹理。
 *
 * @author Ysj
 * Create time: 2022/4/2
 */
class OESGLTexture : GLTexture {

    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    override val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES

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
        surface?.release()
        surfaceTexture?.release()
        surfaceTexture?.setOnFrameAvailableListener(null)
        surface = null
        surfaceTexture = null
        GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }

    fun setTextureSize(width: Int, height: Int) {
        surfaceTexture?.setDefaultBufferSize(width, height)
    }

    fun getSurface(listener: SurfaceTexture.OnFrameAvailableListener): Surface {
        if (surfaceTexture == null) {
            surfaceTexture = SurfaceTexture(id)
        }
        if (surface == null) {
            surface = Surface(surfaceTexture)
        }
        surfaceTexture!!.setOnFrameAvailableListener(listener)
        return surface!!
    }

}