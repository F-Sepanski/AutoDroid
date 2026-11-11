package io.github.sds100.keymapper.base.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assistant
import io.github.sds100.keymapper.base.R
import io.github.sds100.keymapper.base.trigger.ConfigTriggerDelegate
import io.github.sds100.keymapper.base.trigger.MotionTriggerKey
import io.github.sds100.keymapper.base.trigger.Trigger
import io.github.sds100.keymapper.base.variables.VariablesUseCase
import io.github.sds100.keymapper.base.vibration.VibrateConfigDelegate
import io.github.sds100.keymapper.system.motion.MotionDirection
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class CreateGeminiActionDelegateTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var delegateJob: CompletableJob
    private lateinit var delegate: CreateActionDelegate
    private lateinit var configTriggerDelegate: ConfigTriggerDelegate

    @Before
    fun setup() {
        delegateJob = Job()
        val delegateScope = CoroutineScope(testDispatcher + delegateJob)

        val useCase: CreateActionUseCase = mock()
        whenever(useCase.isFlashlightEnabled()).thenReturn(emptyFlow())

        val vibrateConfigDelegate: VibrateConfigDelegate = mock()
        val variablesUseCase: VariablesUseCase = mock()

        delegate = CreateActionDelegate(
            coroutineScope = delegateScope,
            useCase = useCase,
            dialogProvider = mock(),
            navigationProvider = mock(),
            resourceProvider = mock(),
            vibrateConfigDelegate = vibrateConfigDelegate,
            variablesUseCase = variablesUseCase,
        )

        configTriggerDelegate = ConfigTriggerDelegate()
    }

    @After
    fun tearDown() {
        delegateJob.cancel()
    }

    @Test
    fun `createAction with GEMINI_PROMPT opens bottom sheet with empty prompt`() =
        testScope.runTest {
            delegate.createAction(ActionId.GEMINI_PROMPT)

            val state = delegate.geminiPromptBottomSheetState
            assertNotNull(state)
            assertEquals("", state?.prompt)
            assertNull(delegate.actionResult.value)
        }

    @Test
    fun `onDoneGeminiPromptClick confirms custom prompt and saves ActionData GeminiPrompt`() =
        testScope.runTest {
            delegate.createAction(ActionId.GEMINI_PROMPT)
            delegate.onGeminiPromptChange("Turn on the living room lights")

            assertEquals(
                "Turn on the living room lights",
                delegate.geminiPromptBottomSheetState?.prompt,
            )

            delegate.onDoneGeminiPromptClick()

            assertNull(delegate.geminiPromptBottomSheetState)
            val result = delegate.actionResult.value as? ActionData.GeminiPrompt
            assertNotNull(result)
            assertEquals("Turn on the living room lights", result?.prompt)
        }

    @Test
    fun `onDoneGeminiPromptClick with blank prompt falls back to suggested default`() =
        testScope.runTest {
            delegate.createAction(ActionId.GEMINI_PROMPT)
            delegate.onGeminiPromptChange("   ")
            delegate.onDoneGeminiPromptClick()

            assertNull(delegate.geminiPromptBottomSheetState)
            val result = delegate.actionResult.value as? ActionData.GeminiPrompt
            assertNotNull(result)
            assertEquals("Ligue as luzes do meu quarto", result?.prompt)
        }

    @Test
    fun `editAction with existing GeminiPrompt opens bottom sheet with current prompt`() =
        testScope.runTest {
            val existingAction = ActionData.GeminiPrompt(prompt = "Take a picture and describe it")
            delegate.editAction(existingAction)

            val state = delegate.geminiPromptBottomSheetState
            assertNotNull(state)
            assertEquals("Take a picture and describe it", state?.prompt)

            delegate.onGeminiPromptChange("Take a panoramic picture")
            delegate.onDoneGeminiPromptClick()

            val result = delegate.actionResult.value as? ActionData.GeminiPrompt
            assertNotNull(result)
            assertEquals("Take a panoramic picture", result?.prompt)
        }

    @Test
    fun `dismissing Gemini bottom sheet clears state`() = testScope.runTest {
        delegate.createAction(ActionId.GEMINI_PROMPT)
        assertNotNull(delegate.geminiPromptBottomSheetState)

        delegate.geminiPromptBottomSheetState = null
        assertNull(delegate.geminiPromptBottomSheetState)
        assertNull(delegate.actionResult.value)
    }

    @Test
    fun `GeminiPrompt action is editable`() {
        val action = ActionData.GeminiPrompt(prompt = "Sample")
        assertTrue(action.isEditable())
    }

    @Test
    fun `ActionUtils mappings for GEMINI_PROMPT`() {
        assertEquals(R.string.action_gemini_prompt, ActionUtils.getTitle(ActionId.GEMINI_PROMPT))
        assertEquals(Icons.Outlined.Assistant, ActionUtils.getComposeIcon(ActionId.GEMINI_PROMPT))
        assertEquals(ActionCategory.APPS, ActionUtils.getCategory(ActionId.GEMINI_PROMPT))
    }

    @Test
    fun `ConfigTriggerDelegate addMotionTriggerKey adds preset combo correctly`() {
        val initialTrigger = Trigger()
        val directions = listOf(MotionDirection.UP, MotionDirection.UP, MotionDirection.BACKWARD)

        val updatedTrigger = configTriggerDelegate.addMotionTriggerKey(initialTrigger, directions)

        assertEquals(1, updatedTrigger.keys.size)
        val triggerKey = updatedTrigger.keys.first() as? MotionTriggerKey
        assertNotNull(triggerKey)
        assertEquals(directions, triggerKey?.directions)
    }

    @Test
    fun `ConfigTriggerDelegate addMotionTriggerKey adds shake gesture correctly`() {
        val initialTrigger = Trigger()
        val directions = listOf(MotionDirection.SHAKE)

        val updatedTrigger = configTriggerDelegate.addMotionTriggerKey(initialTrigger, directions)

        assertEquals(1, updatedTrigger.keys.size)
        val triggerKey = updatedTrigger.keys.first() as? MotionTriggerKey
        assertNotNull(triggerKey)
        assertEquals(listOf(MotionDirection.SHAKE), triggerKey?.directions)
    }
}
