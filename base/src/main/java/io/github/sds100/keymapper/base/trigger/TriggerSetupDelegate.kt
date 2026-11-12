package io.github.sds100.keymapper.base.trigger

import android.os.Build
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.sds100.keymapper.base.keymaps.ConfigKeyMapState
import io.github.sds100.keymapper.base.onboarding.SetupAccessibilityServiceDelegate
import io.github.sds100.keymapper.base.system.accessibility.FingerprintGestureType
import io.github.sds100.keymapper.base.utils.ExpertModeStatus
import io.github.sds100.keymapper.base.utils.navigation.NavDestination
import io.github.sds100.keymapper.base.utils.navigation.NavigationProvider
import io.github.sds100.keymapper.base.utils.navigation.navigate
import io.github.sds100.keymapper.base.utils.ui.DialogProvider
import io.github.sds100.keymapper.base.utils.ui.ResourceProvider
import io.github.sds100.keymapper.common.utils.AccessibilityServiceError
import io.github.sds100.keymapper.common.utils.KMResult
import io.github.sds100.keymapper.common.utils.onFailure
import io.github.sds100.keymapper.common.utils.onSuccess
import io.github.sds100.keymapper.sysbridge.manager.SystemBridgeConnectionManager
import io.github.sds100.keymapper.sysbridge.manager.SystemBridgeConnectionState
import io.github.sds100.keymapper.system.accessibility.AccessibilityServiceState
import io.github.sds100.keymapper.system.motion.AndroidMotionAdapter
import io.github.sds100.keymapper.system.motion.MotionAdapter
import io.github.sds100.keymapper.system.motion.MotionDirection
import io.github.sds100.keymapper.system.motion.MotionTelemetry
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelScoped
class TriggerSetupDelegateImpl @Inject constructor(
    @Named("viewmodel")
    val viewModelScope: CoroutineScope,
    val setupAccessibilityServiceDelegate: SetupAccessibilityServiceDelegate,
    val recordTriggerController: RecordTriggerController,
    val systemBridgeConnectionManager: SystemBridgeConnectionManager,
    val configTriggerUseCase: ConfigTriggerUseCase,
    val setupInputMethodUseCase: SetupInputMethodUseCase,
    val configKeyMapState: ConfigKeyMapState,
    resourceProvider: ResourceProvider,
    dialogProvider: DialogProvider,
    navigationProvider: NavigationProvider,
    val motionAdapter: MotionAdapter,
) : TriggerSetupDelegate,
    ResourceProvider by resourceProvider,
    DialogProvider by dialogProvider,
    NavigationProvider by navigationProvider,
    SetupAccessibilityServiceDelegate by setupAccessibilityServiceDelegate {

    private val configTriggerDelegate: ConfigTriggerDelegate = ConfigTriggerDelegate()

    private val currentSetupShortcut: MutableStateFlow<TriggerSetupShortcut?> =
        MutableStateFlow(null)

    private val isScreenOffChecked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isExpertModeLocked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val forceExpertMode: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val editingMotionKey = MutableStateFlow<MotionTriggerKey?>(null)
    private val motionCurrentSequence = MutableStateFlow<List<MotionDirection>>(emptyList())
    private val motionThreshold = MutableStateFlow(5.0f)
    private val motionNoisePadding = MutableStateFlow(0.5f)
    private val isMotionRecording = MutableStateFlow(false)
    private val isMotionTraining = MutableStateFlow(false)
    private val motionTrainingCurrentStep = MutableStateFlow(0)
    private val isMotionDebuggerVisible = MutableStateFlow(false)

    override val motionTelemetry: StateFlow<MotionTelemetry> = motionAdapter.telemetry

    init {
        (motionAdapter as? AndroidMotionAdapter)?.let { adapter ->
            motionThreshold.value = adapter.savedThreshold
            motionNoisePadding.value = adapter.savedNoisePadding
        }
        viewModelScope.launch {
            motionAdapter.gestures.collect { gesture ->
                if (currentSetupShortcut.value == TriggerSetupShortcut.MOTION_COMBO) {
                    if (isMotionRecording.value) {
                        motionCurrentSequence.value =
                            motionCurrentSequence.value + gesture.direction
                    } else if (isMotionTraining.value) {
                        val seq = motionCurrentSequence.value
                        val step = motionTrainingCurrentStep.value
                        if (seq.isNotEmpty()) {
                            if (step < seq.size && gesture.direction == seq[step]) {
                                val nextStep = step + 1
                                if (nextStep >= seq.size) {
                                    motionTrainingCurrentStep.value = 0
                                } else {
                                    motionTrainingCurrentStep.value = nextStep
                                }
                            } else {
                                motionTrainingCurrentStep.value = 0
                            }
                        }
                    }
                }
            }
        }
    }

    private val selectedFingerprintGestureType: MutableStateFlow<FingerprintGestureType> =
        MutableStateFlow(FingerprintGestureType.SWIPE_DOWN)

    private val selectedGamePadType: MutableStateFlow<TriggerSetupState.Gamepad.Type> =
        MutableStateFlow(TriggerSetupState.Gamepad.Type.DPAD)

    private val expertModeStatus: Flow<ExpertModeStatus> =
        systemBridgeConnectionManager.connectionState.map { state ->
            when (state) {
                is SystemBridgeConnectionState.Connected -> ExpertModeStatus.ENABLED
                is SystemBridgeConnectionState.Disconnected -> ExpertModeStatus.DISABLED
            }
        }

    override val triggerSetupState: StateFlow<TriggerSetupState?> =
        currentSetupShortcut.flatMapLatest { shortcut ->
            if (shortcut == null) {
                flowOf(null)
            } else {
                when (shortcut) {
                    TriggerSetupShortcut.VOLUME -> buildSetupVolumeTriggerFlow()
                    TriggerSetupShortcut.POWER -> buildSetupPowerTriggerFlow()
                    TriggerSetupShortcut.FINGERPRINT_GESTURE -> buildSetupFingerprintGestureFlow()
                    TriggerSetupShortcut.MOTION_COMBO -> buildSetupMotionComboFlow()
                    TriggerSetupShortcut.KEYBOARD -> buildSetupKeyboardTriggerFlow()
                    TriggerSetupShortcut.MOUSE -> buildSetupMouseTriggerFlow()
                    TriggerSetupShortcut.GAMEPAD -> buildSetupGamepadTriggerFlow()
                    TriggerSetupShortcut.OTHER -> buildSetupOtherTriggerFlow()
                    TriggerSetupShortcut.NOT_DETECTED -> buildSetupNotDetectedFlow()
                    else -> throw UnsupportedOperationException("Unhandled shortcut: $shortcut")
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    override fun showTriggerSetup(shortcut: TriggerSetupShortcut, forceExpertMode: Boolean) {
        if (shortcut == TriggerSetupShortcut.MOTION_COMBO) {
            editingMotionKey.value = null
            currentSetupShortcut.value = shortcut
            motionCurrentSequence.value = emptyList()
            isMotionRecording.value = false
            isMotionTraining.value = false
            motionTrainingCurrentStep.value = 0
            (motionAdapter as? AndroidMotionAdapter)?.let { adapter ->
                motionThreshold.value = adapter.savedThreshold
                motionNoisePadding.value = adapter.savedNoisePadding
            }
            motionAdapter.setSensitivity(motionThreshold.value, motionNoisePadding.value)
            motionAdapter.startListening()
            return
        }
        isExpertModeLocked.value = true
        this.forceExpertMode.value = forceExpertMode
        // If force pro mode is enabled, automatically check the screen off option
        if (forceExpertMode) {
            isScreenOffChecked.value = true
        }
        currentSetupShortcut.value = shortcut
    }

    override fun onMotionComboSelected(directions: List<MotionDirection>) {
        configKeyMapState.update { keyMap ->
            val newTrigger = configTriggerDelegate.addMotionTriggerKey(keyMap.trigger, directions)
            keyMap.copy(trigger = newTrigger)
        }
    }

    private fun buildSetupMotionComboFlow(): Flow<TriggerSetupState> {
        return combine(
            accessibilityServiceState,
            motionCurrentSequence,
            motionThreshold,
            motionNoisePadding,
            isMotionRecording,
            isMotionTraining,
            motionTrainingCurrentStep,
            isMotionDebuggerVisible,
        ) { args: Array<Any?> ->
            val serviceState = args[0] as AccessibilityServiceState

            @Suppress("UNCHECKED_CAST")
            val sequence = args[1] as List<MotionDirection>
            val threshold = args[2] as Float
            val padding = args[3] as Float
            val recording = args[4] as Boolean
            val training = args[5] as Boolean
            val step = args[6] as Int
            val showDebugger = args[7] as Boolean

            val isServiceEnabled = serviceState == AccessibilityServiceState.ENABLED
            TriggerSetupState.MotionCombo(
                isAccessibilityServiceEnabled = isServiceEnabled,
                areRequirementsMet = isServiceEnabled,
                currentSequence = sequence,
                threshold = threshold,
                noisePadding = padding,
                isRecording = recording,
                isTraining = training,
                trainingCurrentStep = step,
                showDebugger = showDebugger,
            )
        }
    }

    private fun buildSetupVolumeTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            accessibilityServiceState,
            isScreenOffChecked,
            recordTriggerController.state,
            expertModeStatus,
            forceExpertMode,
        ) {
                serviceState,
                isScreenOffChecked,
                recordTriggerState,
                expertModeStatus,
                forceExpertMode,
            ->
            val areRequirementsMet = if (isScreenOffChecked) {
                serviceState == AccessibilityServiceState.ENABLED &&
                    expertModeStatus == ExpertModeStatus.ENABLED
            } else {
                serviceState == AccessibilityServiceState.ENABLED
            }

            TriggerSetupState.Volume(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                isUseExpertModeChecked = isScreenOffChecked,
                expertModeStatus = expertModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
                forceExpertMode = forceExpertMode,
            )
        }
    }

    private fun buildSetupGamepadTriggerFlow(): Flow<TriggerSetupState> {
        return selectedGamePadType.flatMapLatest { selectedGamepadType ->
            when (selectedGamepadType) {
                TriggerSetupState.Gamepad.Type.DPAD -> {
                    combine(
                        accessibilityServiceState,
                        setupInputMethodUseCase.isEnabled,
                        setupInputMethodUseCase.isChosen,
                        recordTriggerController.state,
                    ) { serviceState, isImeEnabled, isImeChosen, recordTriggerState ->
                        val areRequirementsMet =
                            serviceState == AccessibilityServiceState.ENABLED &&
                                isImeEnabled &&
                                isImeChosen

                        TriggerSetupState.Gamepad.Dpad(
                            isAccessibilityServiceEnabled =
                            serviceState == AccessibilityServiceState.ENABLED,
                            isImeEnabled = isImeEnabled,
                            isImeChosen = isImeChosen,
                            areRequirementsMet = areRequirementsMet,
                            recordTriggerState = recordTriggerState,
                            enablingRequiresUserInput =
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
                        )
                    }
                }

                TriggerSetupState.Gamepad.Type.SIMPLE_BUTTONS -> {
                    combine(
                        accessibilityServiceState,
                        isScreenOffChecked,
                        recordTriggerController.state,
                        expertModeStatus,
                        forceExpertMode,
                    ) {
                            serviceState,
                            isScreenOffChecked,
                            recordTriggerState,
                            expertModeStatus,
                            forceExpertMode,
                        ->
                        val areRequirementsMet = if (isScreenOffChecked) {
                            serviceState == AccessibilityServiceState.ENABLED &&
                                expertModeStatus == ExpertModeStatus.ENABLED
                        } else {
                            serviceState == AccessibilityServiceState.ENABLED
                        }

                        TriggerSetupState.Gamepad.SimpleButtons(
                            isAccessibilityServiceEnabled =
                            serviceState == AccessibilityServiceState.ENABLED,
                            isUseExpertModeChecked = isScreenOffChecked,
                            expertModeStatus = expertModeStatus,
                            areRequirementsMet = areRequirementsMet,
                            recordTriggerState = recordTriggerState,
                            forceExpertMode = forceExpertMode,
                        )
                    }
                }
            }
        }
    }

    private fun buildSetupOtherTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            accessibilityServiceState,
            isScreenOffChecked,
            recordTriggerController.state,
            expertModeStatus,
            forceExpertMode,
        ) {
                serviceState,
                isScreenOffChecked,
                recordTriggerState,
                expertModeStatus,
                forceExpertMode,
            ->
            val areRequirementsMet = if (isScreenOffChecked) {
                serviceState == AccessibilityServiceState.ENABLED &&
                    expertModeStatus == ExpertModeStatus.ENABLED
            } else {
                serviceState == AccessibilityServiceState.ENABLED
            }

            TriggerSetupState.Other(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                isUseExpertModeChecked = isScreenOffChecked,
                expertModeStatus = expertModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
                forceExpertMode = forceExpertMode,
            )
        }
    }

    private fun buildSetupNotDetectedFlow(): Flow<TriggerSetupState> {
        return combine(
            accessibilityServiceState,
            recordTriggerController.state,
            expertModeStatus,
        ) { serviceState, recordTriggerState, expertModeStatus ->
            val areRequirementsMet =
                serviceState == AccessibilityServiceState.ENABLED &&
                    expertModeStatus == ExpertModeStatus.ENABLED

            TriggerSetupState.NotDetected(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                expertModeStatus = expertModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
            )
        }
    }

    private fun buildSetupKeyboardTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            accessibilityServiceState,
            isScreenOffChecked,
            recordTriggerController.state,
            expertModeStatus,
            forceExpertMode,
        ) {
                serviceState,
                isScreenOffChecked,
                recordTriggerState,
                expertModeStatus,
                forceExpertMode,
            ->
            val areRequirementsMet = if (isScreenOffChecked) {
                serviceState == AccessibilityServiceState.ENABLED &&
                    expertModeStatus == ExpertModeStatus.ENABLED
            } else {
                serviceState == AccessibilityServiceState.ENABLED
            }

            TriggerSetupState.Keyboard(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                isUseExpertModeChecked = isScreenOffChecked,
                expertModeStatus = expertModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
                forceExpertMode = forceExpertMode,
            )
        }
    }

    private fun buildSetupFingerprintGestureFlow(): Flow<TriggerSetupState> {
        return combine(
            accessibilityServiceState,
            selectedFingerprintGestureType,
        ) { serviceState, gestureType ->
            val areRequirementsMet = serviceState == AccessibilityServiceState.ENABLED

            TriggerSetupState.FingerprintGesture(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                areRequirementsMet = areRequirementsMet,
                selectedType = gestureType,
            )
        }
    }

    private fun buildSetupPowerTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            accessibilityServiceState,
            recordTriggerController.state,
            expertModeStatus,
        ) { serviceState, recordTriggerState, expertModeStatus ->
            val areRequirementsMet =
                serviceState == AccessibilityServiceState.ENABLED &&
                    expertModeStatus == ExpertModeStatus.ENABLED

            val remapStatus = if (areRequirementsMet) {
                RemapStatus.SUPPORTED
            } else {
                RemapStatus.UNCERTAIN
            }

            TriggerSetupState.Power(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                expertModeStatus = expertModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
                remapStatus = remapStatus,
            )
        }
    }

    private fun buildSetupMouseTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            accessibilityServiceState,
            recordTriggerController.state,
            expertModeStatus,
        ) { serviceState, recordTriggerState, expertModeStatus ->
            val areRequirementsMet =
                serviceState == AccessibilityServiceState.ENABLED &&
                    expertModeStatus == ExpertModeStatus.ENABLED

            val remapStatus = if (areRequirementsMet) {
                RemapStatus.SUPPORTED
            } else {
                RemapStatus.UNCERTAIN
            }

            TriggerSetupState.Mouse(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                expertModeStatus = expertModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
                remapStatus = remapStatus,
            )
        }
    }

    override fun onEnableAccessibilityServiceClick() {
        viewModelScope.launch {
            showEnableAccessibilityServiceDialog()
        }
    }

    override fun onEnableExpertModeClick() {
        viewModelScope.launch {
            navigate("trigger_setup_enable_expert_mode", NavDestination.ExpertMode)
        }
    }

    override fun onScreenOffTriggerSetupCheckedChange(isChecked: Boolean) {
        isScreenOffChecked.value = isChecked
    }

    override fun onUseExpertModeCheckedChange(isChecked: Boolean) {
        isScreenOffChecked.value = isChecked
    }

    override fun editMotionTriggerKey(key: MotionTriggerKey) {
        editingMotionKey.value = key
        motionCurrentSequence.value = key.directions
        isMotionRecording.value = false
        isMotionTraining.value = false
        motionTrainingCurrentStep.value = 0
        currentSetupShortcut.value = TriggerSetupShortcut.MOTION_COMBO
        (motionAdapter as? AndroidMotionAdapter)?.let { adapter ->
            motionThreshold.value = adapter.savedThreshold
            motionNoisePadding.value = adapter.savedNoisePadding
        }
        motionAdapter.setSensitivity(motionThreshold.value, motionNoisePadding.value)
        motionAdapter.startListening()
    }

    override fun onDismissTriggerSetup() {
        if (currentSetupShortcut.value == TriggerSetupShortcut.MOTION_COMBO) {
            motionAdapter.stopListening()
            isMotionRecording.value = false
            isMotionTraining.value = false
            motionTrainingCurrentStep.value = 0
            editingMotionKey.value = null
        }
        currentSetupShortcut.value = null
    }

    override fun onStartMotionRecording() {
        motionCurrentSequence.value = emptyList()
        isMotionRecording.value = true
        isMotionTraining.value = false
        motionTrainingCurrentStep.value = 0
        motionAdapter.startListening()
    }

    override fun onStopMotionRecording() {
        isMotionRecording.value = false
    }

    override fun onClearMotionSequence() {
        motionCurrentSequence.value = emptyList()
        motionTrainingCurrentStep.value = 0
    }

    override fun onMotionSensitivityChanged(threshold: Float, noisePadding: Float) {
        motionThreshold.value = threshold
        motionNoisePadding.value = noisePadding
        motionAdapter.setSensitivity(threshold, noisePadding)
    }

    override fun onToggleMotionTraining() {
        val next = !isMotionTraining.value
        isMotionTraining.value = next
        if (next) {
            isMotionRecording.value = false
            motionTrainingCurrentStep.value = 0
            motionAdapter.startListening()
        }
    }

    override fun onToggleMotionDebugger() {
        isMotionDebuggerVisible.value = !isMotionDebuggerVisible.value
    }

    override fun onSaveMotionCombo() {
        motionAdapter.setSensitivity(motionThreshold.value, motionNoisePadding.value)
        val sequence = motionCurrentSequence.value
        if (sequence.isNotEmpty()) {
            val editing = editingMotionKey.value
            if (editing != null) {
                configKeyMapState.update { keyMap ->
                    val newKeys = keyMap.trigger.keys.map { k ->
                        if (k.uid == editing.uid && k is MotionTriggerKey) {
                            k.copy(directions = sequence)
                        } else {
                            k
                        }
                    }
                    keyMap.copy(trigger = keyMap.trigger.copy(keys = newKeys))
                }
            } else {
                onMotionComboSelected(sequence)
            }
        }
        onDismissTriggerSetup()
    }

    override fun onTriggerSetupRecordClick() {
        val setupState = triggerSetupState.value ?: return

        val enableEvdevRecording = when (setupState) {
            is TriggerSetupState.Volume -> setupState.isUseExpertModeChecked

            is TriggerSetupState.Keyboard -> setupState.isUseExpertModeChecked

            is TriggerSetupState.Power -> true

            is TriggerSetupState.FingerprintGesture -> false

            is TriggerSetupState.MotionCombo -> false

            is TriggerSetupState.Mouse -> true

            is TriggerSetupState.Other -> setupState.isUseExpertModeChecked

            is TriggerSetupState.Gamepad.Dpad -> false

            is TriggerSetupState.Gamepad.SimpleButtons -> setupState.isUseExpertModeChecked

            // Always enable pro mode recording to increase the chances of detecting
            // the key
            is TriggerSetupState.NotDetected -> true
        }

        recordTriggerController.setEvdevRecordingEnabled(enableEvdevRecording)

        viewModelScope.launch {
            val recordTriggerState = recordTriggerController.state.firstOrNull() ?: return@launch

            val result: KMResult<*> = when (recordTriggerState) {
                is RecordTriggerState.CountingDown -> {
                    recordTriggerController.stopRecording()
                }

                is RecordTriggerState.Completed,
                RecordTriggerState.Idle,
                    -> recordTriggerController.startRecording()
            }

            result.onSuccess {
                currentSetupShortcut.value = null
            }

            // Show dialog if the accessibility service is disabled or crashed
            if (result is AccessibilityServiceError) {
                showFixAccessibilityServiceDialog(result)
            }
        }
    }

    override fun onFingerprintGestureTypeSelected(type: FingerprintGestureType) {
        selectedFingerprintGestureType.value = type
    }

    override fun onAddFingerprintGestureClick() {
        configTriggerUseCase.addFingerprintGesture(selectedFingerprintGestureType.value)
        currentSetupShortcut.value = null
    }

    override fun onGamepadButtonTypeSelected(type: TriggerSetupState.Gamepad.Type) {
        selectedGamePadType.value = type
    }

    override fun onEnableImeClick() {
        viewModelScope.launch {
            setupInputMethodUseCase.enableInputMethod()
        }
    }

    override fun onChooseImeClick() {
        viewModelScope.launch {
            setupInputMethodUseCase.chooseInputMethod().onFailure {
                Timber.e("Failed to choose input method when setting up trigger. Error: $it")
            }
        }
    }
}

interface TriggerSetupDelegate {
    val triggerSetupState: StateFlow<TriggerSetupState?>
    val motionTelemetry: StateFlow<MotionTelemetry>
    fun showTriggerSetup(shortcut: TriggerSetupShortcut, forceExpertMode: Boolean = false)
    fun onDismissTriggerSetup()
    fun onEnableAccessibilityServiceClick()
    fun onEnableExpertModeClick()
    fun onScreenOffTriggerSetupCheckedChange(isChecked: Boolean)
    fun onUseExpertModeCheckedChange(isChecked: Boolean)
    fun onTriggerSetupRecordClick()
    fun onFingerprintGestureTypeSelected(type: FingerprintGestureType)
    fun onAddFingerprintGestureClick()
    fun onGamepadButtonTypeSelected(type: TriggerSetupState.Gamepad.Type)
    fun onEnableImeClick()
    fun onChooseImeClick()
    fun onMotionComboSelected(directions: List<MotionDirection>)
    fun onStartMotionRecording()
    fun onStopMotionRecording()
    fun onClearMotionSequence()
    fun onMotionSensitivityChanged(threshold: Float, noisePadding: Float)
    fun onToggleMotionTraining()
    fun onToggleMotionDebugger()
    fun onSaveMotionCombo()
    fun editMotionTriggerKey(key: MotionTriggerKey)
}
