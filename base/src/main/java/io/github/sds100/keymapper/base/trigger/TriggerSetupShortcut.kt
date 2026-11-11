package io.github.sds100.keymapper.base.trigger

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
enum class TriggerSetupShortcut {
    VOLUME,
    ASSISTANT,
    POWER,
    FINGERPRINT_GESTURE,
    MOTION_COMBO,
    KEYBOARD,
    MOUSE,
    GAMEPAD,
    OTHER,
    NOT_DETECTED,
}
