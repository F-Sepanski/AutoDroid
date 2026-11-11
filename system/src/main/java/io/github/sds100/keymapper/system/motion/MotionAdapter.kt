package io.github.sds100.keymapper.system.motion

import kotlinx.coroutines.flow.SharedFlow

interface MotionAdapter {
    val gestures: SharedFlow<MotionGesture>
    val triggeredCombos: SharedFlow<MotionCombo>
    fun registerCombos(combos: List<MotionCombo>)
    fun startListening()
    fun stopListening()
    val isListening: Boolean
}
