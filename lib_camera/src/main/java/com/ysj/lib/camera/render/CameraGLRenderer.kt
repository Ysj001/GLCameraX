package com.ysj.lib.camera.render

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import java.io.Closeable
import kotlin.math.roundToInt

/**
 * 提供 OpenGL 环境的相机渲染器。
 *
 * @author Ysj
 * Create time: 2022/10/26
 */
class CameraGLRenderer(context: Context) : Preview.SurfaceProvider, Closeable {

    companion object {
        private const val TAG = "CameraGLRender"
    }

    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private val cameraGLEnv = CameraGLEnv(context)

    private var preview: Preview? = null

    private var previewSurfaceProvider: SurfaceProvider? = null

    private var unbindWindowRunnable: Runnable? = null

    private var request: SurfaceRequest? = null

    override fun onSurfaceRequested(request: SurfaceRequest) {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            return mainExecutor.execute { onSurfaceRequested(request) }
        }
        this.request = request
        val provider = this.previewSurfaceProvider
        if (provider == null) {
            request.willNotProvideSurface()
            return
        }
        provider.onSurfaceRequested(request)
    }

    @MainThread
    override fun close() {
        unbindWindow()
        cameraGLEnv.close()
        Log.i(TAG, "closed.")
    }

    @MainThread
    fun bindWindow(surfaceView: SurfaceView) {
        unbindWindow()
        val callback = SurfaceCallback(surfaceView)
        surfaceView.holder.addCallback(callback)
        previewSurfaceProvider = callback
        unbindWindowRunnable = Runnable {
            surfaceView.holder.removeCallback(callback)
        }
        Log.i(TAG, "bind window.")
    }

    @SuppressLint("RestrictedApi")
    @MainThread
    fun unbindWindow() {
        unbindWindowRunnable?.run()
        unbindWindowRunnable = null
        previewSurfaceProvider = null
        cameraGLEnv.unbindWindow()
        request?.deferrableSurface?.close()
        request = null
        Log.i(TAG, "unbind window.")
    }

    @MainThread
    fun setPreview(preview: Preview?) {
        this.preview = preview
    }

    @MainThread
    fun setTargetAspectRatio(ratio: Rational?) {
        val surfaceProvider = checkNotNull(previewSurfaceProvider) {
            "not bind window"
        }
        surfaceProvider.setTargetAspectRatio(ratio)
    }

    fun setRenderManager(manager: CameraRenderManager?) {
        cameraGLEnv.setRenderManager(manager)
    }

    private inner class SurfaceCallback(private val surfaceView: SurfaceView) : SurfaceHolder.Callback, SurfaceProvider {

        private var aspectRatio: Rational? = null

        private var targetAspectRatio: Rational? = null

        private var targetResolution: Size? = null

        var request: SurfaceRequest? = null

        var surfaceSize: Size? = null

        var wasSurfaceProvided = false

        @MainThread
        override fun setTargetAspectRatio(ratio: Rational?) {
            if (ratio == aspectRatio) {
                return
            }
            aspectRatio = ratio
            targetAspectRatio = ratio
            Log.d(TAG, "surface view set target aspect ratio: $ratio")
            if (request != null) {
                preview?.setSurfaceProvider(this@CameraGLRenderer)
            }
        }

        @SuppressLint("RestrictedApi")
        @MainThread
        override fun onSurfaceRequested(request: SurfaceRequest) {
            val degrees = request.camera.cameraInfo.sensorRotationDegrees
            val maxPreviewWidth = (surfaceView.parent as ViewGroup).width
            val maxPreviewHeight = (surfaceView.parent as ViewGroup).height
            val resolution = when (degrees) {
                90, 270 -> Size(request.resolution.height, request.resolution.width)
                else -> request.resolution
            }
            var targetAspectRatio = this.aspectRatio
            if (targetAspectRatio == null) {
                targetAspectRatio = Rational(resolution.width, resolution.height)
                this.targetAspectRatio = targetAspectRatio
                Log.d(TAG, "surface view set default target aspect ratio: $targetAspectRatio")
            }
            val rw = targetAspectRatio.numerator * resolution.height
            val rh = targetAspectRatio.denominator * resolution.width
            var targetWidth = resolution.width
            var targetHeight = resolution.height
            when {
                rw < rh -> targetWidth = rw / targetAspectRatio.denominator
                rw > rh -> targetHeight = rh / targetAspectRatio.numerator
            }
            if (targetWidth % 2 > 0) {
                targetWidth++
            }
            if (targetHeight % 2 > 0) {
                targetHeight++
            }
            targetResolution = Size(targetWidth, targetHeight)
            val viewWidth: Int
            val viewHeight: Int
            val vw = targetAspectRatio.numerator * maxPreviewHeight
            val vh = targetAspectRatio.denominator * maxPreviewWidth
            if (vw < vh) {
                viewWidth = (vw.toFloat() / targetAspectRatio.denominator).roundToInt()
                viewHeight = maxPreviewHeight
            } else if (vw > vh) {
                viewWidth = maxPreviewWidth
                viewHeight = (vh.toFloat() / targetAspectRatio.numerator).roundToInt()
            } else {
                viewWidth = maxPreviewWidth
                viewHeight = maxPreviewHeight
            }
            Log.d(TAG, "surface view width=$viewWidth , height=$viewHeight")
            surfaceView.updateLayoutParams<ViewGroup.LayoutParams> {
                width = viewWidth
                height = viewHeight
            }
            setSurfaceRequest(request)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.i(TAG, "surfaceCreated")
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.i(TAG, "surfaceChanged: $format , $width , $height")
            surfaceSize = Size(width, height)
            tryToComplete()
        }

        @SuppressLint("RestrictedApi")
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (wasSurfaceProvided) {
                cameraGLEnv.unbindWindow()
                request?.deferrableSurface?.close()
                Log.i(TAG, "Surface invalidated $request")
            } else {
                request?.willNotProvideSurface()
            }
            wasSurfaceProvided = false
            surfaceSize = null
            request = null
        }

        @SuppressLint("RestrictedApi")
        private fun setSurfaceRequest(request: SurfaceRequest) {
            // 老的取消掉
            cameraGLEnv.unbindWindow()
            this.request?.willNotProvideSurface()
            this.request = request
            this.wasSurfaceProvided = false
            val targetResolution = checkNotNull(this.targetResolution)
            if (!tryToComplete()) {
                Log.i(TAG, "setSurfaceRequest: Wait for new Surface creation.")
                surfaceView.holder.setFixedSize(targetResolution.width, targetResolution.height)
            }
        }

        private fun tryToComplete(): Boolean {
            val targetAspectRatio = this.targetAspectRatio ?: return false
            val targetResolution = this.targetResolution ?: return false
            val request = this.request ?: return false
            val surfaceSize = this.surfaceSize ?: return false
            val surfaceLegal = targetResolution == surfaceSize
            Log.i(TAG, "tryToComplete: $surfaceSize , $targetResolution , $targetAspectRatio")
            if (targetResolution == surfaceSize) {
                Log.i(TAG, "Surface set on Preview. $request")
                cameraGLEnv.onSurfaceRequested(request.cameraInfo(), request, surfaceView.holder.surface)
                wasSurfaceProvided = true
            }
            return surfaceLegal
        }

        @SuppressLint("RestrictedApi")
        private fun SurfaceRequest.cameraInfo(): CameraInfo {
            return CameraInfo(
                cameraId = camera.cameraInfoInternal.cameraId,
                size = resolution,
                lensFacing = camera.cameraInfoInternal.lensFacing!!,
                sensorRotationDegrees = camera.cameraInfo.sensorRotationDegrees,
                targetResolution = targetResolution!!,
            )
        }
    }

    private interface SurfaceProvider : Preview.SurfaceProvider {

        fun setTargetAspectRatio(ratio: Rational?)

    }
}