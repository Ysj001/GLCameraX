package com.ysj.camera.demo

import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import androidx.core.view.*
import com.ysj.camera.demo.databinding.ActivityDemo1Binding
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

    private val videoCapture by lazy(LazyThreadSafetyMode.NONE) {
        val recorder = Recorder.Builder().build()
        VideoCapture(this, recorder)
    }

    private var recording: Recorder.Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityDemo1Binding.inflate(layoutInflater)
        setContentView(vb.root)
        initViews()
        cameraViewModel.renderManager.addRenderer(videoCapture)
        onTurnClicked()
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

    private fun onTurnClicked() {
        val lensFacing: Int
        val resolution: Size
        when (cameraViewModel.cameraConfig.value?.lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> {
                lensFacing = CameraSelector.LENS_FACING_BACK
                resolution = Size(1080,1920)
                vb.btnTurn.text = "后"
            }
            else -> {
                lensFacing = CameraSelector.LENS_FACING_FRONT
                resolution = Size(720,1280)
                vb.btnTurn.text = "前"
            }
        }
        cameraViewModel.setCameraConfig(CameraViewModel.CameraConfig(
            lensFacing = lensFacing,
            preview = Preview.Builder()
                .setTargetResolution(resolution)
                .build(),
        ))
    }

    private fun initViews(): Unit = with(vb) {
        val systemBarColor = ContextCompat.getColor(spaceStatusBar.context, R.color.black)
        initStyle(spaceStatusBar, systemBarColor, systemBarColor)
        btnRecord.text = "开始"
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
}