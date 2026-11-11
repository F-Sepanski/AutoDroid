package io.github.sds100.keymapper.base.actions

import io.github.sds100.keymapper.base.system.accessibility.IAccessibilityService
import io.github.sds100.keymapper.common.utils.Success
import io.github.sds100.keymapper.system.gemini.GeminiAdapter
import io.github.sds100.keymapper.system.gemini.GeminiExecutionMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class GeminiActionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testCoroutineScope = TestScope(testDispatcher)

    private lateinit var mockGeminiAdapter: GeminiAdapter
    private lateinit var mockAccessibilityService: IAccessibilityService
    private lateinit var useCase: PerformActionsUseCaseImpl

    @Before
    fun setup() {
        mockGeminiAdapter = mock()
        mockAccessibilityService = mock()

        useCase = PerformActionsUseCaseImpl(
            coroutineScope = testCoroutineScope,
            service = mockAccessibilityService,
            inputMethodAdapter = mock(),
            switchImeInterface = mock(),
            fileAdapter = mock(),
            suAdapter = mock(),
            shell = mock(),
            intentAdapter = mock(),
            getActionErrorUseCase = mock(),
            executeShellCommandUseCase = mock(),
            keyMapperImeMessenger = mock(),
            packageManagerAdapter = mock(),
            appShortcutAdapter = mock(),
            toastAdapter = mock(),
            devicesAdapter = mock(),
            phoneAdapter = mock(),
            audioAdapter = mock(),
            cameraAdapter = mock(),
            displayAdapter = mock(),
            lockScreenAdapter = mock(),
            mediaAdapter = mock(),
            airplaneModeAdapter = mock(),
            networkAdapter = mock(),
            bluetoothAdapter = mock(),
            nfcAdapter = mock(),
            openUrlAdapter = mock(),
            resourceProvider = mock(),
            soundsManager = mock(),
            notificationAdapter = mock(),
            ringtoneAdapter = mock(),
            settingsRepository = mock(),
            inputEventHub = mock(),
            systemBridgeConnectionManager = mock(),
            settingsAdapter = mock(),
            vibratorAdapter = mock(),
            variablesUseCase = mock(),
            buildConfigProvider = mock {
                on { packageName } doReturn "io.github.sds100.keymapper"
            },
            geminiAdapter = mockGeminiAdapter,
        )
    }

    @Test
    fun `ActionData GeminiPrompt has correct default values and ActionId`() {
        val action = ActionData.GeminiPrompt(prompt = "Hello Gemini")

        assertEquals(ActionId.GEMINI_PROMPT, action.id)
        assertEquals("Hello Gemini", action.prompt)
        assertNull(action.apiKey)
        assertEquals("gemini-2.0-flash", action.model)
        assertEquals(GeminiExecutionMode.API_FALLBACK_INTENT, action.mode)
    }

    @Test
    fun `ActionData GeminiPrompt serializes and deserializes correctly`() {
        val original = ActionData.GeminiPrompt(
            prompt = "Turn on lights",
            apiKey = "test-api-key",
            model = "gemini-1.5-pro",
            mode = GeminiExecutionMode.API_ONLY,
        )

        val jsonString = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<ActionData.GeminiPrompt>(jsonString)

        assertThat(deserialized, equalTo(original))
        assertEquals("Turn on lights", deserialized.prompt)
        assertEquals("test-api-key", deserialized.apiKey)
        assertEquals("gemini-1.5-pro", deserialized.model)
        assertEquals(GeminiExecutionMode.API_ONLY, deserialized.mode)
        assertEquals(ActionId.GEMINI_PROMPT, deserialized.id)
    }

    @Test
    fun `ActionData GeminiPrompt compareTo compares by prompt`() {
        val actionA = ActionData.GeminiPrompt(prompt = "Alpha")
        val actionB = ActionData.GeminiPrompt(prompt = "Beta")

        assertThat(actionA.compareTo(actionB) < 0, equalTo(true))
        assertThat(actionB.compareTo(actionA) > 0, equalTo(true))
        assertThat(actionA.compareTo(actionA) == 0, equalTo(true))
    }

    @Test
    fun `PerformActionsUseCase calls GeminiAdapter with correct parameters`() =
        runTest(testDispatcher) {
            whenever(
                mockGeminiAdapter.executePrompt(
                    any(),
                    anyOrNull(),
                    any(),
                    any(),
                ),
            ).doReturn(Success("Response text"))

            val action = ActionData.GeminiPrompt(
                prompt = "Explain quantum computing",
                apiKey = "test-secret-key",
                model = "gemini-2.0-flash",
                mode = GeminiExecutionMode.API_ONLY,
            )

            useCase.perform(action)

            verify(mockGeminiAdapter).executePrompt(
                prompt = "Explain quantum computing",
                apiKey = "test-secret-key",
                model = "gemini-2.0-flash",
                mode = GeminiExecutionMode.API_ONLY,
            )
        }

    @Test
    fun `PerformActionsUseCase calls GeminiAdapter with default parameters`() =
        runTest(testDispatcher) {
            whenever(
                mockGeminiAdapter.executePrompt(
                    any(),
                    anyOrNull(),
                    any(),
                    any(),
                ),
            ).doReturn(Success("Dispatched Assistant Intent"))

            val action = ActionData.GeminiPrompt(prompt = "Open Assistant")

            useCase.perform(action)

            verify(mockGeminiAdapter).executePrompt(
                prompt = "Open Assistant",
                apiKey = null,
                model = "gemini-2.0-flash",
                mode = GeminiExecutionMode.API_FALLBACK_INTENT,
            )
        }
}
