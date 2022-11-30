package com.ysj.lib.camera

import android.app.Application
import android.util.Rational
import androidx.camera.core.Camera
import androidx.camera.core.CameraFilter
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ysj.lib.camera.render.CameraRenderManager

/**
 * 配合 [CameraFragment] 的 [ViewModel]。
 *
 * @author Ysj
 * Create time: 2022/11/28
 */
class CameraViewModel(app: Application) : AndroidViewModel(app) {

    val cameraConfig: LiveData<CameraConfig> = MutableLiveData()

    val camera: LiveData<Camera> = MutableLiveData()

    val renderManager = CameraRenderManager()

    val aspectRatio: LiveData<Rational?> = MutableLiveData()

    internal fun setupCamera(camera: Camera) {
        this.camera as MutableLiveData
        this.camera.value = camera
    }

    /**
     * 设置用处初始化相机的配置。
     */
    fun setCameraConfig(config: CameraConfig) {
        this.cameraConfig as MutableLiveData
        this.cameraConfig.value = config
    }

    /**
     * 默认情况下会将设置的宽高对其到 9/16 或 3/4（相对正方向），通过该参数可以调整输出的渲染窗口的比例。
     *
     * @param rational 比例，为 null 时还原默认。
     */
    fun setAspectRatio(rational: Rational?) {
        this.aspectRatio as MutableLiveData
        this.aspectRatio.value = rational
    }

    data class CameraConfig(
        val lensFacing: Int,
        val filters: Array<CameraFilter> = emptyArray(),
        val preview: Preview,
        val cases: Array<UseCase> = emptyArray(),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CameraConfig) return false

            if (lensFacing != other.lensFacing) return false
            if (!filters.contentEquals(other.filters)) return false
            if (preview != other.preview) return false
            if (!cases.contentEquals(other.cases)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = lensFacing
            result = 31 * result + filters.contentHashCode()
            result = 31 * result + preview.hashCode()
            result = 31 * result + cases.contentHashCode()
            return result
        }
    }
}