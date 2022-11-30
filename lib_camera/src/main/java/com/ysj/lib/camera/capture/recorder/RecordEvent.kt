package com.ysj.lib.camera.capture.recorder

/**
 * 录制相关事件。
 *
 * @author Ysj
 * Create time: 2022/11/3
 */
sealed class RecordEvent {

    companion object {
        const val ERROR_NO = 0
        const val ERROR_ENCODER = 1
    }

    object Start : RecordEvent()

    data class Status(
        val bytes: Long,
        val durationUs: Long,
    ) : RecordEvent()

    data class Finalize(
        val error: Int,
        val case: Throwable?,
        val bytes: Long,
        val durationUs: Long,
    ) : RecordEvent()
}