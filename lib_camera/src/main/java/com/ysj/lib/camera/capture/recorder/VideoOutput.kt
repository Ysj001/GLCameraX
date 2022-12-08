package com.ysj.lib.camera.capture.recorder

import android.util.Size
import android.view.Surface
import java.util.concurrent.Executor

/**
 * 视频输出接口。
 *
 * @author Ysj
 * Create time: 2022/11/3
 */
interface VideoOutput {

    /**
     * 当该 output 被安装时回调。
     */
    fun onAttach()

    /**
     * 当该 output 被卸载时回调。
     */
    fun onDetach()

    /**
     * 当视频帧生产者准备好向 output 提供视频帧时回调。
     * - 当该 output 准备好 [Surface] 并要开始编码视频帧时，即会回调 [SurfaceRequest.onRequest]
     * 来通知生产者向该 [Surface] 提供数据。
     * - 当该 output 编码完成视频时，即会回调 [SurfaceRequest.onRelease]
     * 来通知生产者不需要在继续提供视频帧了。
     */
    fun onSurfaceRequest(request: SurfaceRequest)

    interface SurfaceRequest {
        /**
         * 当前的相机 id。
         */
        val cameraId: String

        /**
         * 输出视频的大小。
         */
        val size: Size

        /**
         * 输出视频是携带的旋转信息。
         */
        val degree: Int

        /**
         * 当 [Surface] 准备好时回调。
         */
        fun onRequest(surface: Surface)

        /**
         * 当 [Surface] 即将释放时回调。
         */
        fun onRelease(surface: Surface)
    }
}