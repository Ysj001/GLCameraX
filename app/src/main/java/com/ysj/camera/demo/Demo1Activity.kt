package com.ysj.camera.demo

import android.annotation.SuppressLint
import android.hardware.camera2.CameraDevice
import android.os.Bundle
import android.util.Rational
import android.util.Size
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.impl.SessionConfig
import androidx.core.content.ContextCompat
import androidx.core.view.*
import com.ysj.camera.demo.databinding.ActivityDemo1Binding
import com.ysj.camera.demo.renders.Effects
import com.ysj.camera.demo.renders.FpsCapture
import com.ysj.lib.camera.CameraFragment
import com.ysj.lib.camera.CameraViewModel
import com.ysj.lib.camera.capture.VideoCapture
import com.ysj.lib.camera.capture.recorder.RecordEvent
import com.ysj.lib.camera.capture.recorder.Recorder
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * 演示使用 [CameraFragment]。
 *
 * @author Ysj
 * Create time: 2022/11/28
 */
class Demo1Activity : AppCompatActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()

    private lateinit var vb: ActivityDemo1Binding

    private val date by lazy(LazyThreadSafetyMode.NONE) { Date() }
    private val dateFormat: DateFormat by lazy(LazyThreadSafetyMode.NONE) {
        SimpleDateFormat("HH:mm:ss", Locale.ROOT).also {
            it.timeZone = TimeZone.getTimeZone("GMT+0")
        }
    }

    private val orientationListener by lazy(LazyThreadSafetyMode.NONE) {
        OrientationListener()
    }

    private val effects by lazy(LazyThreadSafetyMode.NONE) {
        Effects(this)
    }

    private val videoCapture by lazy(LazyThreadSafetyMode.NONE) {
        val recorder = Recorder.Builder().build()
        VideoCapture(this, recorder)
    }

    private val fpsCapture by lazy(LazyThreadSafetyMode.NONE) {
        FpsCapture(10, ::onFpsUpdate)
    }

    private val rationals = arrayOf(
        CameraParam(
            rationalName = "9:16",
            rational = null,
            resolution = Size(720, 1280),
        ),
        CameraParam(
            rationalName = "3:4",
            rational = null,
            resolution = Size(720, 960),
        ),
        CameraParam(
            rationalName = "1:1",
            rational = Rational(1, 1),
            resolution = Size(720, 960),
        ),
    )

    private var currentRationalIndex = 0

    private var currentSensorDegree = 0

    private var recording: Recorder.Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityDemo1Binding.inflate(layoutInflater)
        setContentView(vb.root)
        initViews()
        cameraViewModel.renderManager.addRenderer(effects)
        cameraViewModel.renderManager.addRenderer(videoCapture)
        cameraViewModel.renderManager.addRenderer(fpsCapture)
        initCamera()
        orientationListener.enable()
    }

    override fun onDestroy() {
        super.onDestroy()
        orientationListener.disable()
    }

    private fun onRecordEvent(event: RecordEvent) {
        val recording = checkNotNull(this.recording)
        when (event) {
            RecordEvent.Start -> {
                vb.btnRecord.text = "停止"
                vb.tvRecordTime.text = dateFormat.format(date.apply { time = 0 })
                vb.cardRecordTime.isVisible = true
            }
            is RecordEvent.Status -> {
                val durationMs = event.durationUs / 1000
                vb.tvRecordTime.text = dateFormat.format(date.apply { time = durationMs })
            }
            is RecordEvent.Finalize -> {
                vb.btnRecord.text = "开始"
                vb.cardRecordTime.isGone = true
                this.recording = null
                if (event.error == RecordEvent.ERROR_NO) {
                    Toast.makeText(this, "已保存:${recording.output}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onRecordClicked() {
        var recording = this.recording
        if (recording != null) {
            recording.stop()
            return
        }
        val name = "CameraX-record.mp4"
        val dir = File(cacheDir, "camera_cache")
        dir.mkdirs()
        val output = File(dir, name)
        output.delete()
        output.createNewFile()
        recording = videoCapture.output.prepare(this, output)
        this.recording = recording
        recording.start(ContextCompat.getMainExecutor(this), ::onRecordEvent)
    }

    private fun onFpsUpdate(fps: Double): Unit = with(vb.tvFps) {
        post {
            text = if (fps.isNaN() || fps.isInfinite()) "---" else String.format("%.1ffps", fps)
        }
    }

    private fun onTurnClicked() {
        val config = checkNotNull(cameraViewModel.cameraConfig.value)
        val lensFacing: Int
        val btnText: String
        when (config.lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> {
                lensFacing = CameraSelector.LENS_FACING_BACK
                btnText = "后"
            }
            else -> {
                lensFacing = CameraSelector.LENS_FACING_FRONT
                btnText = "前"
            }
        }
        vb.btnTurn.text = btnText
        cameraViewModel.setCameraConfig(config.copy(lensFacing = lensFacing))
    }

    @SuppressLint("RestrictedApi")
    private fun onRatioClicked() {
        val config = checkNotNull(cameraViewModel.cameraConfig.value)
        val oldIndex = currentRationalIndex
        currentRationalIndex++
        if (currentRationalIndex == rationals.size) {
            currentRationalIndex = 0
        }
        vb.btnRatio.text = rationals[currentRationalIndex].rationalName
        val oldCameraParam = rationals[oldIndex]
        val cameraParam = rationals[currentRationalIndex]
        cameraViewModel.setAspectRatio(cameraParam.rational)
        if (oldCameraParam.resolution == cameraParam.resolution) {
            return
        }
        cameraViewModel.setCameraConfig(config.copy(
            preview = Preview.Builder()
                .setTargetResolution(cameraParam.resolution)
                .setDefaultSessionConfig(SessionConfig.Builder()
                    .apply { setTemplateType(CameraDevice.TEMPLATE_RECORD) }
                    .build())
                .build(),
        ))
    }

    @SuppressLint("RestrictedApi")
    private fun initCamera() {
        val cameraParam = rationals[currentRationalIndex]
        cameraViewModel.setAspectRatio(cameraParam.rational)
        cameraViewModel.setCameraConfig(CameraViewModel.CameraConfig(
            lensFacing = CameraSelector.LENS_FACING_FRONT,
            preview = Preview.Builder()
                .setTargetResolution(cameraParam.resolution)
                .setDefaultSessionConfig(SessionConfig.Builder()
                    .apply { setTemplateType(CameraDevice.TEMPLATE_RECORD) }
                    .build())
                .build(),
        ))
    }

    private fun initViews(): Unit = with(vb) {
        val systemBarColor = ContextCompat.getColor(spaceStatusBar.context, R.color.black)
        initStyle(spaceStatusBar, systemBarColor, systemBarColor)
        btnRecord.text = "开始"
        btnTurn.text = "前"
        btnRatio.text = rationals[currentRationalIndex].rationalName
        btnRatio.setOnClickListener { onRatioClicked() }
        btnTurn.setOnClickListener { onTurnClicked() }
        btnRecord.setOnClickListener { onRecordClicked() }
    }

    private fun initStyle(spaceStatusBar: View, statusBarColor: Int, navigationBarColor: Int) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, spaceStatusBar).show(WindowInsetsCompat.Type.navigationBars())
        WindowInsetsControllerCompat(window, spaceStatusBar).show(WindowInsetsCompat.Type.statusBars())
        window.statusBarColor = statusBarColor
        window.navigationBarColor = navigationBarColor
        ViewCompat.setOnApplyWindowInsetsListener(spaceStatusBar) { view, windowInsets ->
            val statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updateLayoutParams<ViewGroup.LayoutParams> {
                height = statusBarHeight
            }
            windowInsets
        }
    }

    private inner class OrientationListener : OrientationEventListener(this) {

        override fun onOrientationChanged(orientation: Int) {
            val degree = when (orientation) {
                in 46..134 -> 90
                in 136..224 -> 180
                in 226..314 -> 270
                else -> 0
            }
            if (degree != currentSensorDegree) {
                currentSensorDegree = degree
                videoCapture.setRotation(degree)
            }
        }
    }

    private class CameraParam(
        val rationalName: String,
        val rational: Rational?,
        val resolution: Size,
    )
}