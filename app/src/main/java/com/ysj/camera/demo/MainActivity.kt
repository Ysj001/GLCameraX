package com.ysj.camera.demo

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService

/**
 * 入口。
 *
 * @author Ysj
 * Create time: 2022/11/28
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private var pendingPermission: RequestPermission? = null

    private var openGLMajorVersion = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val am: ActivityManager? = getSystemService()
        if (am != null) {
            openGLMajorVersion = am.deviceConfigurationInfo.reqGlEsVersion ushr 16
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) Result@{
            pendingPermission?.onResult(it)
            pendingPermission = null
        }
    }

    fun onDemo1Clicked(view: View) {
        if (openGLMajorVersion < 2) {
            // OpenGL 1 使用固定管线，2+ 才有可编程管线
            toast("openGL 版本过低. version=$openGLMajorVersion")
            return
        }
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        requestPermissions(RequestPermission(permissions) {
            startActivity(Intent(this, Demo1Activity::class.java))
        })
    }

    private fun requestPermissions(request: RequestPermission) {
        permissionLauncher.launch(request.permissions)
        pendingPermission = request
    }

    private fun toast(msg: String) {
        val toast = Toast.makeText(this, msg, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    private inner class RequestPermission(
        val permissions: Array<String>,
        private val onSuccess: () -> Unit,
    ) {

        fun onResult(result: Map<String, Boolean>) {
            for (index in permissions.indices) {
                if (result[permissions[index]] == true) {
                    continue
                }
                onFailure()
            }
            onSuccess()
        }

        private fun onFailure() {
            toast("授权失败")
        }
    }
}