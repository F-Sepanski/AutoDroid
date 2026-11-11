package io.github.sds100.keymapper.base.trigger

import com.google.gson.GsonBuilder
import io.github.sds100.keymapper.base.actions.Action
import io.github.sds100.keymapper.base.actions.ActionData
import io.github.sds100.keymapper.base.actions.ActionErrorSnapshot
import io.github.sds100.keymapper.base.actions.PerformActionsUseCase
import io.github.sds100.keymapper.base.constraints.DetectConstraintsUseCase
import io.github.sds100.keymapper.base.detection.DetectKeyMapModel
import io.github.sds100.keymapper.base.detection.DetectKeyMapsUseCase
import io.github.sds100.keymapper.base.detection.KeyMapDetectionController
import io.github.sds100.keymapper.base.input.InputEventHub
import io.github.sds100.keymapper.base.keymaps.ClickType
import io.github.sds100.keymapper.base.keymaps.KeyMap
import io.github.sds100.keymapper.base.keymaps.PauseKeyMapsUseCase
import io.github.sds100.keymapper.base.utils.ui.ResourceProvider
import io.github.sds100.keymapper.data.entities.MotionTriggerKeyEntity
import io.github.sds100.keymapper.data.entities.TriggerKeyEntity
import io.github.sds100.keymapper.data.repositories.PreferenceRepository
import io.github.sds100.keymapper.system.motion.MotionAdapter
import io.github.sds100.keymapper.system.motion.MotionDirection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class MotionTriggerTest {

    private val gson = GsonBuilder()
        .registerTypeHierarchyAdapter(TriggerKeyEntity::class.java, TriggerKeyEntity.SERIALIZER)
        .registerTypeHierarchyAdapter(TriggerKeyEntity::class.java, TriggerKeyEntity.DESERIALIZER)
        .create()

    @Test
    fun `motion trigger key default properties and uid`() {
        val directions = listOf(MotionDirection.UP, MotionDirection.UP, MotionDirection.BACKWARD)
        val key = MotionTriggerKey(directions = directions)

        assertThat(key.allowedLongPress, `is`(false))
        assertThat(key.allowedDoublePress, `is`(false))
        assertThat(key.clickType, `is`(ClickType.SHORT_PRESS))
        assertThat(key.uid, `is`("UP-UP-BACKWARD"))
    }

    @Test
    fun `motion trigger key setClickType creates updated copy`() {
        val key = MotionTriggerKey(directions = listOf(MotionDirection.SHAKE))
        val updated = key.setClickType(ClickType.LONG_PRESS)

        assertThat(updated.clickType, `is`(ClickType.LONG_PRESS))
        assertThat(updated is MotionTriggerKey, `is`(true))
        assertThat((updated as MotionTriggerKey).directions, `is`(listOf(MotionDirection.SHAKE)))
    }

    @Test
    fun `isLogicallyEqual compares direction sequences`() {
        val key1 = MotionTriggerKey(directions = listOf(MotionDirection.UP, MotionDirection.DOWN))
        val key2 = MotionTriggerKey(directions = listOf(MotionDirection.UP, MotionDirection.DOWN))
        val key3 = MotionTriggerKey(directions = listOf(MotionDirection.DOWN, MotionDirection.UP))

        assertThat(key1.isLogicallyEqual(key2), `is`(true))
        assertThat(key1.isLogicallyEqual(key3), `is`(false))
    }

    @Test
    fun `bidirectional mapping between MotionTriggerKey and MotionTriggerKeyEntity`() {
        val originalKey = MotionTriggerKey(
            directions = listOf(MotionDirection.LEFT, MotionDirection.RIGHT),
            clickType = ClickType.SHORT_PRESS,
            uid = "custom-motion-uid",
        )

        val entity = MotionTriggerKey.toEntity(originalKey)
        assertThat(entity.directions, `is`(listOf("LEFT", "RIGHT")))
        assertThat(entity.clickType, `is`(TriggerKeyEntity.SHORT_PRESS))
        assertThat(entity.uid, `is`("custom-motion-uid"))

        val mappedBack = MotionTriggerKey.fromEntity(entity)
        assertThat(mappedBack.directions, `is`(listOf(MotionDirection.LEFT, MotionDirection.RIGHT)))
        assertThat(mappedBack.clickType, `is`(ClickType.SHORT_PRESS))
        assertThat(mappedBack.uid, `is`("custom-motion-uid"))
    }

    @Test
    fun `bidirectional mapping through TriggerEntityMapper`() {
        val trigger = Trigger(
            keys = listOf(
                MotionTriggerKey(
                    directions = listOf(MotionDirection.FORWARD, MotionDirection.BACKWARD),
                    clickType = ClickType.SHORT_PRESS,
                    uid = "test-combo-uid",
                ),
            ),
        )

        val entity = TriggerEntityMapper.toEntity(trigger)
        assertThat(entity.keys.size, `is`(1))
        assertThat(entity.keys[0] is MotionTriggerKeyEntity, `is`(true))

        val loadedTrigger = TriggerEntityMapper.fromEntity(entity, floatingButtons = emptyList())
        assertThat(loadedTrigger.keys.size, `is`(1))
        assertThat(loadedTrigger.keys[0] is MotionTriggerKey, `is`(true))

        val loadedKey = loadedTrigger.keys[0] as MotionTriggerKey
        assertThat(
            loadedKey.directions,
            `is`(listOf(MotionDirection.FORWARD, MotionDirection.BACKWARD)),
        )
        assertThat(loadedKey.uid, `is`("test-combo-uid"))
    }

    @Test
    fun `json serialization and deserialization of MotionTriggerKeyEntity`() {
        val originalEntity: TriggerKeyEntity = MotionTriggerKeyEntity(
            directions = listOf("UP", "DOWN", "SHAKE"),
            clickType = TriggerKeyEntity.SHORT_PRESS,
            uid = "json-test-uid",
        )

        val json = gson.toJson(originalEntity, TriggerKeyEntity::class.java)
        val deserialized = gson.fromJson(json, TriggerKeyEntity::class.java)

        assertThat(deserialized is MotionTriggerKeyEntity, `is`(true))
        val motionEntity = deserialized as MotionTriggerKeyEntity
        assertThat(motionEntity.directions, `is`(listOf("UP", "DOWN", "SHAKE")))
        assertThat(motionEntity.clickType, `is`(TriggerKeyEntity.SHORT_PRESS))
        assertThat(motionEntity.uid, `is`("json-test-uid"))
    }

    @Test
    fun `TriggerUiHelper formats motion trigger key names`() {
        val resourceProvider: ResourceProvider = mock {
            on { getString(any()) } doReturn "dummy"
        }
        val helper = TriggerUiHelper(resourceProvider)
        val trigger = Trigger(
            keys = listOf(
                MotionTriggerKey(
                    directions = listOf(
                        MotionDirection.UP,
                        MotionDirection.UP,
                        MotionDirection.BACKWARD,
                    ),
                ),
            ),
        )

        val names = helper.getKeyNames(trigger, showDeviceDescriptors = false)
        assertThat(names.size, `is`(1))
        assertThat(names[0], `is`("Motion: UP UP BACKWARD"))
    }

    @Test
    fun `KeyMapDetectionController triggers actions when motion combo occurs`() {
        val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        val controllerJob = kotlinx.coroutines.Job()
        val controllerScope = kotlinx.coroutines.CoroutineScope(testDispatcher + controllerJob)

        kotlinx.coroutines.test.runTest(testDispatcher) {
            val actionData = ActionData.OpenCamera
            val action = Action(data = actionData)
            val motionKey = MotionTriggerKey(
                directions = listOf(MotionDirection.UP, MotionDirection.DOWN),
                uid = "combo-123",
            )
            val keyMap = KeyMap(
                uid = "km-456",
                trigger = Trigger(keys = listOf(motionKey)),
                actionList = listOf(action),
            )
            val detectModel = DetectKeyMapModel(keyMap)

            val detectUseCase: DetectKeyMapsUseCase = mock {
                on { allKeyMapList } doReturn MutableStateFlow(listOf(detectModel))
                on { defaultVibrateDuration } doReturn MutableStateFlow(100L)
                on { forceVibrate } doReturn MutableStateFlow(false)
                on { defaultSequenceTriggerTimeout } doReturn MutableStateFlow(2000L)
                on { defaultLongPressDelay } doReturn MutableStateFlow(500L)
                on { defaultDoublePressDelay } doReturn MutableStateFlow(300L)
            }

            val performActionsUseCase: PerformActionsUseCase = mock {
                on { defaultRepeatRate } doReturn MutableStateFlow(50L)
                on { defaultRepeatDelay } doReturn MutableStateFlow(400L)
                on { defaultHoldDownDuration } doReturn MutableStateFlow(500L)
                on { getErrorSnapshot() } doReturn object : ActionErrorSnapshot {
                    override fun getError(action: ActionData) = null
                    override fun getErrors(actions: List<ActionData>) =
                        emptyMap<ActionData, Nothing?>()
                }
            }

            val detectConstraints: DetectConstraintsUseCase = mock {
                on { getSnapshot() } doReturn mock()
            }
            val inputEventHub: InputEventHub = mock()
            val pauseKeyMapsUseCase: PauseKeyMapsUseCase = mock {
                on { isPaused } doReturn MutableStateFlow(false)
            }
            val recordTriggerController: RecordTriggerController = mock {
                on { state } doReturn MutableStateFlow(RecordTriggerState.Idle)
            }
            val preferences: PreferenceRepository = mock {
                on {
                    get(io.github.sds100.keymapper.data.Keys.keyEventActionsUseSystemBridge)
                } doReturn
                    emptyFlow()
            }
            val motionAdapter: MotionAdapter = mock {
                on { triggeredCombos } doReturn MutableSharedFlow(extraBufferCapacity = 1)
            }

            val controller = KeyMapDetectionController(
                coroutineScope = controllerScope,
                detectUseCase = detectUseCase,
                performActionsUseCase = performActionsUseCase,
                detectConstraints = detectConstraints,
                inputEventHub = inputEventHub,
                pauseKeyMapsUseCase = pauseKeyMapsUseCase,
                recordTriggerController = recordTriggerController,
                preferences = preferences,
                motionAdapter = motionAdapter,
            )

            try {
                kotlinx.coroutines.delay(100)
                controller.onMotionCombo("km-456")
                advanceUntilIdle()
                verify(performActionsUseCase).perform(actionData)
            } finally {
                controllerJob.cancel()
            }
        }
    }
}
