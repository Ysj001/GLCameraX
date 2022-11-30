package com.ysj.lib.camera

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.ysj.lib.camera.databinding.LibCameraFragmentCameraBinding
import com.ysj.lib.camera.render.CameraGLRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * 通用封装的相机 Fragment。
 *
 * @author Ysj
 * Create time: 2022/11/28
 */
class CameraFragment : Fragment(R.layout.lib_camera_fragment_camera) {

    companion object {
        private const val TAG = "CameraFragment"
    }

    private lateinit var cameraGlRender: CameraGLRenderer

    private val cameraViewModel: CameraViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraGlRender = CameraGLRenderer(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vb = LibCameraFragmentCameraBinding.bind(view)
        cameraGlRender.bindWindow(vb.surfaceView)
        cameraGlRender.setRenderManager(cameraViewModel.renderManager)
        cameraViewModel.cameraConfig.observe(viewLifecycleOwner, ::setupCamera)
        cameraViewModel.aspectRatio.observe(viewLifecycleOwner, cameraGlRender::setTargetAspectRatio)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraGlRender.unbindWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraGlRender.close()
    }

    private fun setupCamera(config: CameraViewModel.CameraConfig) {
        viewLifecycleOwner.lifecycleScope.launch(SetupExceptionHandler() + Dispatchers.Main) {
            val provider = ProcessCameraProvider.getInstance(requireContext()).await()
            val (lensFacing, filters, preview, cases) = config
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .apply {
                    for (index in filters.indices) {
                        addCameraFilter(filters[index])
                    }
                }
                .build()
            check(provider != null && provider.hasCamera(selector)) { "Not found camera!" }
            provider.unbindAll()
            preview.setSurfaceProvider(cameraGlRender)
            cameraViewModel.setupCamera(provider.bindToLifecycle(viewLifecycleOwner, selector, preview, *cases))
        }.invokeOnCompletion {
            if (it is CancellationException) {
                return@invokeOnCompletion
            }
            if (it != null) {
                Toast.makeText(requireContext(), "相机初始化失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class SetupExceptionHandler : CoroutineExceptionHandler {

        override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler

        override fun handleException(context: CoroutineContext, exception: Throwable) {
            if (exception is CancellationException) {
                return
            }
            Log.e(TAG, "setup camera exception.", exception)
        }
    }
}