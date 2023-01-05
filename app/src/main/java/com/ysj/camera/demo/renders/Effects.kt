package com.ysj.camera.demo.renders

import android.content.Context
import com.ysj.camera.demo.renders.effects.CameraTransition
import com.ysj.camera.demo.renders.effects.OesTo2d
import com.ysj.lib.camera.gl.EGLEnv
import com.ysj.lib.camera.gl.texture.GLTexture
import com.ysj.lib.camera.render.CameraInfo
import com.ysj.lib.camera.render.CameraRenderer
import java.util.concurrent.Executor

/**
 *
 *
 * @author Ysj
 * Create time: 2022/12/28
 */
class Effects(private val context: Context) : CameraRenderer {

    private val oesTo2D by lazy(LazyThreadSafetyMode.NONE) {
        OesTo2d(context)
    }

    private val cameraTransition by lazy(LazyThreadSafetyMode.NONE) {
        CameraTransition(context)
    }

    override fun onAttach(glExecutor: Executor, env: EGLEnv) {
        oesTo2D.onAttach(glExecutor, env)
        cameraTransition.onAttach(glExecutor, env)
    }

    override fun onDetach() {
        oesTo2D.onDetach()
        cameraTransition.onDetach()
    }

    override fun onCameraInfo(info: CameraInfo) {
        cameraTransition.onCameraInfo(info)
        oesTo2D.onCameraInfo(info)
    }

    override fun onDraw(input: GLTexture, timestamp: Long): GLTexture {
        var result = oesTo2D.onDraw(input, timestamp)
        result = cameraTransition.onDraw(result, timestamp)
        return result
    }
}