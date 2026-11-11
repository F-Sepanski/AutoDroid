package io.github.sds100.keymapper.system.motion

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class MotionTelemetry(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val magnitude: Float = 0f,
    val lastDirection: MotionDirection? = null,
    val timestampMs: Long = 0L,
)

interface MotionAdapter {
    val gestures: SharedFlow<MotionGesture>
    val triggeredCombos: SharedFlow<MotionCombo>
    val telemetry: StateFlow<MotionTelemetry>
    fun registerCombos(combos: List<MotionCombo>)
    fun setSensitivity(threshold: Float, noisePadding: Float)
    fun startListening()
    fun stopListening()
    val isListening: Boolean
}
