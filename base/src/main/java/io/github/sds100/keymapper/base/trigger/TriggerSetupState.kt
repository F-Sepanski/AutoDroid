package io.github.sds100.keymapper.base.trigger

import io.github.sds100.keymapper.base.system.accessibility.FingerprintGestureType
import io.github.sds100.keymapper.base.utils.ExpertModeStatus
import io.github.sds100.keymapper.system.motion.MotionDirection

sealed class TriggerSetupState {
    data class Volume(
        val isAccessibilityServiceEnabled: Boolean,
        val isUseExpertModeChecked: Boolean,
        val expertModeStatus: ExpertModeStatus,
        val areRequirementsMet: Boolean,
        val recordTriggerState: RecordTriggerState,
        val forceExpertMode: Boolean = false,
    ) : TriggerSetupState()

    data class Keyboard(
        val isAccessibilityServiceEnabled: Boolean,
        val isUseExpertModeChecked: Boolean,
        val expertModeStatus: ExpertModeStatus,
        val areRequirementsMet: Boolean,
        val recordTriggerState: RecordTriggerState,
        val forceExpertMode: Boolean = false,
    ) : TriggerSetupState()

    data class Power(
        val isAccessibilityServiceEnabled: Boolean,
        val expertModeStatus: ExpertModeStatus,
        val areRequirementsMet: Boolean,
        val recordTriggerState: RecordTriggerState,
        val remapStatus: RemapStatus,
    ) : TriggerSetupState()

    data class Mouse(
        val isAccessibilityServiceEnabled: Boolean,
        val expertModeStatus: ExpertModeStatus,
        val areRequirementsMet: Boolean,
        val recordTriggerState: RecordTriggerState,
        val remapStatus: RemapStatus,
    ) : TriggerSetupState()

    data class Other(
        val isAccessibilityServiceEnabled: Boolean,
        val isUseExpertModeChecked: Boolean,
        val expertModeStatus: ExpertModeStatus,
        val areRequirementsMet: Boolean,
        val recordTriggerState: RecordTriggerState,
        val forceExpertMode: Boolean = false,
    ) : TriggerSetupState()

    data class NotDetected(
        val isAccessibilityServiceEnabled: Boolean,
        val expertModeStatus: ExpertModeStatus,
        val areRequirementsMet: Boolean,
        val recordTriggerState: RecordTriggerState,
    ) : TriggerSetupState()

    sealed class Gamepad : TriggerSetupState() {
        abstract val isAccessibilityServiceEnabled: Boolean
        abstract val areRequirementsMet: Boolean
        abstract val recordTriggerState: RecordTriggerState

        enum class Type {
            DPAD,
            SIMPLE_BUTTONS,
        }

        data class Dpad(
            override val isAccessibilityServiceEnabled: Boolean,
            val isImeEnabled: Boolean,
            val enablingRequiresUserInput: Boolean,
            val isImeChosen: Boolean,
            override val areRequirementsMet: Boolean,
            override val recordTriggerState: RecordTriggerState,
        ) : Gamepad()

        data class SimpleButtons(
            override val isAccessibilityServiceEnabled: Boolean,
            val isUseExpertModeChecked: Boolean,
            val expertModeStatus: ExpertModeStatus,
            override val areRequirementsMet: Boolean,
            override val recordTriggerState: RecordTriggerState,
            val forceExpertMode: Boolean = false,
        ) : Gamepad()
    }

    data class FingerprintGesture(
        val isAccessibilityServiceEnabled: Boolean,
        val areRequirementsMet: Boolean,
        val selectedType: FingerprintGestureType,
    ) : TriggerSetupState()

    data class MotionCombo(
        val isAccessibilityServiceEnabled: Boolean,
        val areRequirementsMet: Boolean,
        val currentSequence: List<MotionDirection> = emptyList(),
        val threshold: Float = 3.5f,
        val noisePadding: Float = 0.5f,
        val isRecording: Boolean = false,
        val isTraining: Boolean = false,
        val trainingCurrentStep: Int = 0,
        val showDebugger: Boolean = false,
    ) : TriggerSetupState()
}
