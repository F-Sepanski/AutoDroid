package io.github.sds100.keymapper.base.actions

import io.github.sds100.keymapper.base.variables.VariablesUseCase
import io.github.sds100.keymapper.base.vibration.VibrateConfigDelegate
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ChooseActionBottomSheetTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var delegateJob: CompletableJob
    private lateinit var delegate: CreateActionDelegate

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
    }

    @After
    fun tearDown() {
        delegateJob.cancel()
    }

    @Test
    fun `setting geminiPromptBottomSheetState and calling onDoneGeminiPromptClick emits ActionData GeminiPrompt to actionResult`() =
        testScope.runTest {
            delegate.geminiPromptBottomSheetState =
                GeminiPromptBottomSheetState("Explain this code")

            delegate.onDoneGeminiPromptClick()

            val result = delegate.actionResult.value as? ActionData.GeminiPrompt
            assertNotNull(result)
            assertEquals("Explain this code", result?.prompt)
            assertNull(delegate.geminiPromptBottomSheetState)
        }

    @Test
    fun `onDoneGeminiPromptClick with blank prompt emits fallback prompt`() = testScope.runTest {
        delegate.geminiPromptBottomSheetState = GeminiPromptBottomSheetState("   ")

        delegate.onDoneGeminiPromptClick()

        val result = delegate.actionResult.value as? ActionData.GeminiPrompt
        assertNotNull(result)
        assertEquals("Ligue as luzes do meu quarto", result?.prompt)
        assertNull(delegate.geminiPromptBottomSheetState)
    }

    @Test
    fun `onGeminiPromptChange modifies the current prompt in bottom sheet state`() =
        testScope.runTest {
            delegate.geminiPromptBottomSheetState = GeminiPromptBottomSheetState("Initial")
            delegate.onGeminiPromptChange("Updated prompt")

            assertEquals("Updated prompt", delegate.geminiPromptBottomSheetState?.prompt)
        }
}
