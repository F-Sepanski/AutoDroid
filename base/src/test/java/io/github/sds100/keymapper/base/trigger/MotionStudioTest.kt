package io.github.sds100.keymapper.base.trigger

import io.github.sds100.keymapper.base.keymaps.ConfigKeyMapState
import io.github.sds100.keymapper.base.keymaps.KeyMap
import io.github.sds100.keymapper.base.onboarding.SetupAccessibilityServiceDelegate
import io.github.sds100.keymapper.common.utils.State
import io.github.sds100.keymapper.sysbridge.manager.SystemBridgeConnectionManager
import io.github.sds100.keymapper.sysbridge.manager.SystemBridgeConnectionState
import io.github.sds100.keymapper.system.accessibility.AccessibilityServiceState
import io.github.sds100.keymapper.system.motion.MotionAdapter
import io.github.sds100.keymapper.system.motion.MotionComboDetector
import io.github.sds100.keymapper.system.motion.MotionDirection
import io.github.sds100.keymapper.system.motion.MotionGesture
import io.github.sds100.keymapper.system.motion.MotionTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class MotionStudioTest {

    private fun withDelegate(
        motionAdapter: MotionAdapter,
        configKeyMapState: ConfigKeyMapState = mock(),
        block: suspend (TriggerSetupDelegateImpl, TestScope) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val testJob = Job()
        val scope = CoroutineScope(coroutineContext + testJob)
        try {
            val setupAccessibility: SetupAccessibilityServiceDelegate = mock {
                on { accessibilityServiceState } doReturn
                    MutableStateFlow(AccessibilityServiceState.ENABLED)
            }
            val systemBridge: SystemBridgeConnectionManager = mock {
                on { connectionState } doReturn
                    MutableStateFlow(SystemBridgeConnectionState.Connected(0L))
            }
            val delegate = TriggerSetupDelegateImpl(
                viewModelScope = scope,
                setupAccessibilityServiceDelegate = setupAccessibility,
                recordTriggerController = mock {
                    on { state } doReturn
                        MutableStateFlow(RecordTriggerState.Idle)
                },
                systemBridgeConnectionManager = systemBridge,
                configTriggerUseCase = mock(),
                setupInputMethodUseCase = mock(),
                configKeyMapState = configKeyMapState,
                resourceProvider = mock(),
                dialogProvider = mock(),
                navigationProvider = mock(),
                motionAdapter = motionAdapter,
            )
            block(delegate, this)
        } finally {
            testJob.cancel()
        }
    }

    @Test
    fun `MotionComboDetector adjusts sensitivity and noise padding`() {
        val detector =
            MotionComboDetector(threshold = 3.5f, debounceMs = 250L, shakeWindowMs = 150L)
        assertEquals(MotionDirection.UP, detector.processSample(0f, 4.0f, 0f, 1000L))

        detector.setSensitivity(threshold = 5.0f, noisePadding = 1.0f)
        assertNull(detector.processSample(0f, 5.5f, 0f, 1300L))
        assertEquals(MotionDirection.UP, detector.processSample(0f, 6.5f, 0f, 1600L))
    }

    @Test
    fun `MotionStudio free recording captures arbitrary uncapped motion sequence`() {
        val gesturesFlow = MutableSharedFlow<MotionGesture>(extraBufferCapacity = 64)
        val motionAdapter: MotionAdapter = mock {
            on { gestures } doReturn gesturesFlow
            on { telemetry } doReturn MutableStateFlow(MotionTelemetry())
        }

        withDelegate(motionAdapter) { delegate, testScope ->
            delegate.showTriggerSetup(TriggerSetupShortcut.MOTION_COMBO)
            val stateJob = testScope.launch { delegate.triggerSetupState.collect {} }

            var state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertNotNull(state)
            assertTrue(state!!.currentSequence.isEmpty())
            assertFalse(state.isRecording)

            delegate.onStartMotionRecording()
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertTrue(state!!.isRecording)

            val arbitrarySequence = listOf(
                MotionDirection.UP,
                MotionDirection.DOWN,
                MotionDirection.LEFT,
                MotionDirection.RIGHT,
                MotionDirection.FORWARD,
                MotionDirection.BACKWARD,
                MotionDirection.SHAKE,
            )
            arbitrarySequence.forEachIndexed { i, dir ->
                gesturesFlow.tryEmit(MotionGesture(dir, 1000L + i * 300L))
            }

            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertThat(state!!.currentSequence, `is`(arbitrarySequence))

            delegate.onStopMotionRecording()
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertFalse(state!!.isRecording)
            assertThat(state.currentSequence, `is`(arbitrarySequence))

            delegate.onClearMotionSequence()
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertTrue(state!!.currentSequence.isEmpty())

            stateJob.cancel()
        }
    }

    @Test
    fun `MotionStudio sensitivity slider updates adapter and delegate state`() {
        val motionAdapter: MotionAdapter = mock {
            on { gestures } doReturn MutableSharedFlow()
            on { telemetry } doReturn MutableStateFlow(MotionTelemetry())
        }

        withDelegate(motionAdapter) { delegate, testScope ->
            delegate.showTriggerSetup(TriggerSetupShortcut.MOTION_COMBO)
            val stateJob = testScope.launch { delegate.triggerSetupState.collect {} }

            delegate.onMotionSensitivityChanged(threshold = 6.0f, noisePadding = 1.2f)
            val state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertNotNull(state)
            assertEquals(6.0f, state!!.threshold, 0.001f)
            assertEquals(1.2f, state.noisePadding, 0.001f)
            verify(motionAdapter).setSensitivity(6.0f, 1.2f)

            stateJob.cancel()
        }
    }

    @Test
    fun `MotionStudio training mode auto-resets on incorrect gesture and combo completion`() {
        val gesturesFlow = MutableSharedFlow<MotionGesture>(extraBufferCapacity = 64)
        val motionAdapter: MotionAdapter = mock {
            on { gestures } doReturn gesturesFlow
            on { telemetry } doReturn MutableStateFlow(MotionTelemetry())
        }

        withDelegate(motionAdapter) { delegate, testScope ->
            delegate.showTriggerSetup(TriggerSetupShortcut.MOTION_COMBO)
            val stateJob = testScope.launch { delegate.triggerSetupState.collect {} }

            delegate.onStartMotionRecording()
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.UP, 1000L))
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.RIGHT, 1300L))
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.SHAKE, 1600L))
            delegate.onStopMotionRecording()

            delegate.onToggleMotionTraining()
            var state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertNotNull(state)
            assertTrue(state!!.isTraining)
            assertEquals(0, state.trainingCurrentStep)

            // Step 0: correct gesture UP -> advances to step 1
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.UP, 2000L))
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertEquals(1, state!!.trainingCurrentStep)

            // Step 1: incorrect gesture DOWN -> auto-resets back to step 0
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.DOWN, 2300L))
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertEquals(0, state!!.trainingCurrentStep)

            // Restart from step 0: correct gesture UP -> step 1
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.UP, 2600L))
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertEquals(1, state!!.trainingCurrentStep)

            // Step 1: correct gesture RIGHT -> step 2
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.RIGHT, 2900L))
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertEquals(2, state!!.trainingCurrentStep)

            // Step 2: final gesture SHAKE completes combo -> auto-resets back to step 0
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.SHAKE, 3200L))
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertEquals(0, state!!.trainingCurrentStep)

            stateJob.cancel()
        }
    }

    @Test
    fun `MotionStudio debugger toggle and live sensor telemetry`() {
        val telemetryFlow =
            MutableStateFlow(MotionTelemetry(x = 1.2f, y = 3.4f, z = -0.5f, magnitude = 3.6f))
        val motionAdapter: MotionAdapter = mock {
            on { gestures } doReturn MutableSharedFlow()
            on { telemetry } doReturn telemetryFlow
        }

        withDelegate(motionAdapter) { delegate, testScope ->
            delegate.showTriggerSetup(TriggerSetupShortcut.MOTION_COMBO)
            val stateJob = testScope.launch { delegate.triggerSetupState.collect {} }

            var state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertNotNull(state)
            assertFalse(state!!.showDebugger)

            delegate.onToggleMotionDebugger()
            state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertTrue(state!!.showDebugger)

            val currentTelemetry = delegate.motionTelemetry.value
            assertEquals(1.2f, currentTelemetry.x, 0.001f)
            assertEquals(3.4f, currentTelemetry.y, 0.001f)
            assertEquals(-0.5f, currentTelemetry.z, 0.001f)
            assertEquals(3.6f, currentTelemetry.magnitude, 0.001f)

            stateJob.cancel()
        }
    }

    @Test
    fun `MotionStudio save motion combo adds MotionTriggerKey to keymap and dismisses`() {
        val gesturesFlow = MutableSharedFlow<MotionGesture>(extraBufferCapacity = 64)
        val motionAdapter: MotionAdapter = mock {
            on { gestures } doReturn gesturesFlow
            on { telemetry } doReturn MutableStateFlow(MotionTelemetry())
        }
        var savedKeyMap = KeyMap()
        val configKeyMapState: ConfigKeyMapState = mock {
            on { update(org.mockito.kotlin.any()) } doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.arguments[0] as (KeyMap) -> KeyMap
                savedKeyMap = block(savedKeyMap)
                State.Data(savedKeyMap)
            }
        }

        withDelegate(motionAdapter, configKeyMapState) { delegate, testScope ->
            delegate.showTriggerSetup(TriggerSetupShortcut.MOTION_COMBO)
            val stateJob = testScope.launch { delegate.triggerSetupState.collect {} }

            delegate.onStartMotionRecording()
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.UP, 1000L))
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.BACKWARD, 1300L))

            delegate.onSaveMotionCombo()

            val motionKeys = savedKeyMap.trigger.keys.filterIsInstance<MotionTriggerKey>()
            assertEquals(1, motionKeys.size)
            assertEquals(
                listOf(MotionDirection.UP, MotionDirection.BACKWARD),
                motionKeys[0].directions,
            )

            assertNull(delegate.triggerSetupState.value)
            verify(motionAdapter).stopListening()

            stateJob.cancel()
        }
    }

    @Test
    fun `MotionStudio sensitivity slider supports expanded range up to 50 m per s2`() {
        val motionAdapter: MotionAdapter = mock {
            on { gestures } doReturn MutableSharedFlow()
            on { telemetry } doReturn MutableStateFlow(MotionTelemetry())
        }

        withDelegate(motionAdapter) { delegate, testScope ->
            delegate.showTriggerSetup(TriggerSetupShortcut.MOTION_COMBO)
            val stateJob = testScope.launch { delegate.triggerSetupState.collect {} }

            delegate.onMotionSensitivityChanged(threshold = 50.0f, noisePadding = 2.0f)
            val state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertNotNull(state)
            assertEquals(50.0f, state!!.threshold, 0.001f)
            assertEquals(2.0f, state.noisePadding, 0.001f)
            verify(motionAdapter).setSensitivity(50.0f, 2.0f)

            stateJob.cancel()
        }
    }

    @Test
    fun `MotionComboDetector calibrates with expanded threshold up to 50 m per s2`() {
        val detector =
            MotionComboDetector(threshold = 3.5f, debounceMs = 250L, shakeWindowMs = 150L)
        detector.setSensitivity(threshold = 50.0f, noisePadding = 2.0f)

        // Acceleration below threshold (e.g. 40.0 m/s2) is ignored
        assertNull(detector.processSample(0f, 40.0f, 0f, 1000L))
        // Acceleration exceeding 50.0 + 2.0 m/s2 is detected
        assertEquals(MotionDirection.UP, detector.processSample(0f, 53.0f, 0f, 1300L))
    }

    @Test
    fun `MotionStudio edit existing MotionTriggerKey loads sequence and updates keymap on save`() {
        val gesturesFlow = MutableSharedFlow<MotionGesture>(extraBufferCapacity = 64)
        val motionAdapter: MotionAdapter = mock {
            on { gestures } doReturn gesturesFlow
            on { telemetry } doReturn MutableStateFlow(MotionTelemetry())
        }

        val existingKey = MotionTriggerKey(
            directions = listOf(MotionDirection.UP, MotionDirection.DOWN),
            uid = "existing-motion-uid",
        )
        var savedKeyMap = KeyMap(
            trigger = Trigger(
                keys = listOf(existingKey),
            ),
        )
        val configKeyMapState: ConfigKeyMapState = mock {
            on { update(org.mockito.kotlin.any()) } doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.arguments[0] as (KeyMap) -> KeyMap
                savedKeyMap = block(savedKeyMap)
                State.Data(savedKeyMap)
            }
        }

        withDelegate(motionAdapter, configKeyMapState) { delegate, testScope ->
            delegate.editMotionTriggerKey(existingKey)
            val stateJob = testScope.launch { delegate.triggerSetupState.collect {} }

            val state = delegate.triggerSetupState.value as? TriggerSetupState.MotionCombo
            assertNotNull(state)
            assertEquals(listOf(MotionDirection.UP, MotionDirection.DOWN), state!!.currentSequence)

            // Start recording to overwrite with new sequence
            delegate.onStartMotionRecording()
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.LEFT, 1000L))
            gesturesFlow.tryEmit(MotionGesture(MotionDirection.RIGHT, 1300L))
            delegate.onStopMotionRecording()

            delegate.onSaveMotionCombo()

            val motionKeys = savedKeyMap.trigger.keys.filterIsInstance<MotionTriggerKey>()
            assertEquals(1, motionKeys.size)
            assertEquals("existing-motion-uid", motionKeys[0].uid)
            assertEquals(
                listOf(MotionDirection.LEFT, MotionDirection.RIGHT),
                motionKeys[0].directions,
            )

            assertNull(delegate.triggerSetupState.value)
            verify(motionAdapter).stopListening()

            stateJob.cancel()
        }
    }
}
