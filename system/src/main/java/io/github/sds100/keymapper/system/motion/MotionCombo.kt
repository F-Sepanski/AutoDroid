package io.github.sds100.keymapper.system.motion

data class MotionCombo(
    val id: String,
    val sequence: List<MotionDirection>,
    val windowTimeoutMs: Long = 2000L,
)
