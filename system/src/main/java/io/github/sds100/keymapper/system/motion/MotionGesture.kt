package io.github.sds100.keymapper.system.motion

data class MotionGesture(
    val direction: MotionDirection,
    val timestampMs: Long,
    val intensity: Float = 1.0f,
)
