package io.github.sds100.keymapper.base.detection

import io.github.sds100.keymapper.base.actions.ActionData
import io.github.sds100.keymapper.base.actions.PerformActionsUseCase
import io.github.sds100.keymapper.base.constraints.DetectConstraintsUseCase
import io.github.sds100.keymapper.base.input.InputEventDetectionSource
import io.github.sds100.keymapper.base.input.InputEventHub
import io.github.sds100.keymapper.base.input.InputEventHubCallback
import io.github.sds100.keymapper.base.keymaps.KeyMap
import io.github.sds100.keymapper.base.keymaps.PauseKeyMapsUseCase
import io.github.sds100.keymapper.base.system.accessibility.FingerprintGestureType
import io.github.sds100.keymapper.base.trigger.AssistantTriggerType
import io.github.sds100.keymapper.base.trigger.EvdevTriggerKey
import io.github.sds100.keymapper.base.trigger.MotionTriggerKey
import io.github.sds100.keymapper.base.trigger.RecordTriggerController
import io.github.sds100.keymapper.base.trigger.RecordTriggerState
import io.github.sds100.keymapper.common.models.EvdevDeviceInfo
import io.github.sds100.keymapper.common.models.GrabTargetKeyCode
import io.github.sds100.keymapper.data.Keys
import io.github.sds100.keymapper.data.PreferenceDefaults
import io.github.sds100.keymapper.data.repositories.PreferenceRepository
import io.github.sds100.keymapper.system.inputevents.KMEvdevEvent
import io.github.sds100.keymapper.system.inputevents.KMInputEvent
import io.github.sds100.keymapper.system.motion.MotionAdapter
import io.github.sds100.keymapper.system.motion.MotionCombo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class KeyMapDetectionController(
    private val coroutineScope: CoroutineScope,
    private val detectUseCase: DetectKeyMapsUseCase,
    private val performActionsUseCase: PerformActionsUseCase,
    private val detectConstraints: DetectConstraintsUseCase,
    private val inputEventHub: InputEventHub,
    private val pauseKeyMapsUseCase: PauseKeyMapsUseCase,
    private val recordTriggerController: RecordTriggerController,
    private val preferences: PreferenceRepository,
    val motionAdapter: MotionAdapter? = null,
) : InputEventHubCallback {
    companion object {
        private const val INPUT_EVENT_HUB_ID = "key_map_controller"

        fun getEvdevGrabRequests(
            algorithm: KeyMapAlgorithm,
            injectKeyEventActionsWithSystemBridge: Boolean = true,
        ): List<GrabTargetKeyCode> {
            val deviceKeyEventMap = mutableMapOf<EvdevDeviceInfo, MutableSet<Int>>()

            for ((index, trigger) in algorithm.triggers.withIndex()) {
                val evdevDevices = trigger.keys.filterIsInstance<EvdevTriggerKey>()
                    .map { it.device }
                    .distinct()
                    .toList()

                if (evdevDevices.isEmpty()) {
                    continue
                }

                val actions: List<ActionData> = algorithm.triggerActions[index]
                    .map { actionIndex -> algorithm.actionMap[actionIndex]?.data }
                    .filterNotNull()

                val extraKeyCodes = if (injectKeyEventActionsWithSystemBridge) {
                    actions
                        .filterIsInstance<ActionData.InputKeyEvent>()
                        .map { it.keyCode }
                } else {
                    emptyList()
                }

                for (device in evdevDevices) {
                    deviceKeyEventMap.getOrPut(device) { mutableSetOf() }.addAll(extraKeyCodes)
                }
            }

            return deviceKeyEventMap.map { (device, keyEvents) ->
                GrabTargetKeyCode(
                    name = device.name,
                    bus = device.bus,
                    vendor = device.vendor,
                    product = device.product,
                    extraKeyCodes = keyEvents.toIntArray(),
                )
            }
        }
    }

    private val injectKeyEventsWithSystemBridge: StateFlow<Boolean> =
        preferences.get(Keys.keyEventActionsUseSystemBridge)
            .map { it ?: PreferenceDefaults.KEY_EVENT_ACTIONS_USE_SYSTEM_BRIDGE }
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private val algorithm: KeyMapAlgorithm =
        KeyMapAlgorithm(coroutineScope, detectUseCase, performActionsUseCase, detectConstraints)

    private val isPaused: StateFlow<Boolean> =
        pauseKeyMapsUseCase.isPaused.stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private var activeKeyMaps: List<DetectKeyMapModel> = emptyList()

    private val simpleMappingController = object : SimpleMappingController(
        coroutineScope,
        detectUseCase,
        performActionsUseCase,
        detectConstraints,
    ) {}

    init {
        // Must first register before collecting anything that may change the grabbed
        // evdev devices.
        inputEventHub.registerClient(INPUT_EVENT_HUB_ID, this, listOf(KMEvdevEvent.TYPE_KEY_EVENT))

        coroutineScope.launch {
            combine(detectUseCase.allKeyMapList, isPaused) { keyMapList, isPaused ->
                invalidateState(keyMapList, isPaused)
            }.launchIn(coroutineScope)
        }

        if (motionAdapter != null) {
            coroutineScope.launch {
                motionAdapter.triggeredCombos.collect { combo ->
                    onMotionCombo(combo.id)
                }
            }
        }
    }

    private fun invalidateState(keyMapList: List<DetectKeyMapModel>, isPaused: Boolean) {
        activeKeyMaps = keyMapList
        algorithm.reset()
        simpleMappingController.reset()

        if (isPaused) {
            algorithm.loadKeyMaps(emptyList())
            inputEventHub.setGrabTargets(INPUT_EVENT_HUB_ID, emptyList())
            motionAdapter?.registerCombos(emptyList())
            motionAdapter?.stopListening()
        } else {
            algorithm.loadKeyMaps(keyMapList)
            // Determine which evdev devices need to be grabbed depending on the state
            // of the algorithm.
            val grabRequests =
                getEvdevGrabRequests(algorithm, injectKeyEventsWithSystemBridge.value)

            Timber.i(
                "Grab evdev devices for key map detection: ${grabRequests.joinToString()}",
            )

            inputEventHub.setGrabTargets(
                INPUT_EVENT_HUB_ID,
                grabRequests,
            )

            val motionCombos = keyMapList.asSequence()
                .filter { it.keyMap.isEnabled }
                .flatMap { model ->
                    model.keyMap.trigger.keys.filterIsInstance<MotionTriggerKey>().map { key ->
                        MotionCombo(
                            id = model.keyMap.uid,
                            sequence = key.directions,
                        )
                    }
                }
                .filter { it.sequence.isNotEmpty() }
                .toList()

            motionAdapter?.registerCombos(motionCombos)
            if (motionCombos.isNotEmpty()) {
                motionAdapter?.startListening()
            } else {
                motionAdapter?.stopListening()
            }
        }
    }

    override fun onInputEvent(
        event: KMInputEvent,
        detectionSource: InputEventDetectionSource,
    ): Boolean {
        if (isPaused.value) {
            return false
        }

        if (recordTriggerController.state.value is RecordTriggerState.CountingDown) {
            return false
        }

        return algorithm.onInputEvent(event)
    }

    fun onFingerprintGesture(type: FingerprintGestureType) {
        algorithm.onFingerprintGesture(type)
    }

    fun onFloatingButtonDown(button: String) {
        algorithm.onFloatingButtonDown(button)
    }

    fun onFloatingButtonUp(button: String) {
        algorithm.onFloatingButtonUp(button)
    }

    fun onAssistantEvent(event: AssistantTriggerType) {
        algorithm.onAssistantEvent(event)
    }

    fun onMotionCombo(comboId: String) {
        if (isPaused.value) {
            return
        }

        if (recordTriggerController.state.value is RecordTriggerState.CountingDown) {
            return
        }

        val matchingKeyMaps = activeKeyMaps.map { it.keyMap }.filter { keyMap ->
            keyMap.uid == comboId ||
                keyMap.trigger.keys.filterIsInstance<MotionTriggerKey>().any { it.uid == comboId }
        }

        for (keyMap in matchingKeyMaps) {
            onKeyMapTriggered(keyMap)
        }
    }

    fun onKeyMapTriggered(keyMap: KeyMap) {
        simpleMappingController.onDetected(keyMap)
    }

    fun teardown() {
        algorithm.reset()
        simpleMappingController.reset()
        motionAdapter?.stopListening()
        motionAdapter?.registerCombos(emptyList())
        inputEventHub.setGrabTargets(INPUT_EVENT_HUB_ID, emptyList())
        inputEventHub.unregisterClient(INPUT_EVENT_HUB_ID)
    }
}
