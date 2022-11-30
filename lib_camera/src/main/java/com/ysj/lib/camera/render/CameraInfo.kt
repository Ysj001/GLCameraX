package com.ysj.lib.camera.render

import android.util.Size

/**
 * 相机信息。
 *
 * @author Ysj
 * Create time: 2022/11/2
 */
data class CameraInfo(
    val cameraId: String,
    /**
     * 相机拍摄到的原始画面大小，也是原始 oes 纹理大小。
     */
    val size: Size,
    /**
     * 标识是前置摄像头还是后置摄像头。
     */
    val lensFacing: Int,
    /**
     * 相机画面相对于设备默认方向的旋转角度。
     */
    val sensorRotationDegrees: Int,
    /**
     * 目标分辨率。
     * - 默认情况下为 [size] 经过 [sensorRotationDegrees] 后的大小。
     * - 如果应用了裁剪（如：调整比例）后会发生改变。
     */
    val targetResolution: Size,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CameraInfo) return false

        if (cameraId != other.cameraId) return false

        return true
    }

    override fun hashCode(): Int {
        return cameraId.hashCode()
    }

    override fun toString(): String {
        return """
            |CameraInfo(
            |cameraId='$cameraId'
            |size=$size 
            |lensFacing=$lensFacing
            |sensorRotationDegrees=$sensorRotationDegrees
            |targetResolution=$targetResolution
            |)""".trimMargin()
    }

}