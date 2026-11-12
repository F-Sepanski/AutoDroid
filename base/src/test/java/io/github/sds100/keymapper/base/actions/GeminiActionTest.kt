package io.github.sds100.keymapper.base.actions

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.sds100.keymapper.base.system.accessibility.IAccessibilityService
import io.github.sds100.keymapper.common.utils.Success
import io.github.sds100.keymapper.system.gemini.AndroidGeminiAdapter
import io.github.sds100.keymapper.system.gemini.GeminiAdapter
import io.github.sds100.keymapper.system.gemini.GeminiExecutionMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class GeminiActionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testCoroutineScope = TestScope(testDispatcher)

    private lateinit var mockGeminiAdapter: GeminiAdapter
    private lateinit var mockAccessibilityService: IAccessibilityService
    private lateinit var useCase: PerformActionsUseCaseImpl
    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager

    @Before
    fun setup() {
        mockGeminiAdapter = mock()
        mockAccessibilityService = mock()
        mockContext = mock()
        mockPackageManager = mock()
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)

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

    @Test
    fun `AndroidGeminiAdapter dispatches ACTION_VOICE_COMMAND in overlay mode without opening full-screen ACTION_SEND`() =
        runTest(testDispatcher) {
            whenever(
                mockPackageManager.getPackageInfo(AndroidGeminiAdapter.GEMINI_PACKAGE, 0),
            ).thenReturn(mock())

            val adapter = AndroidGeminiAdapter(mockContext)
            val result = adapter.executePrompt(
                "Explain quantum computing",
                mode = GeminiExecutionMode.INTENT_ONLY,
            )

            assertThat(result is Success, equalTo(true))
            val captor = argumentCaptor<Intent>()
            verify(mockContext).startActivity(captor.capture())
            val intent = captor.firstValue

            assertNotEquals(Intent.ACTION_WEB_SEARCH, intent.action)
            assertNotEquals(Intent.ACTION_SEND, intent.action)
            assertEquals(Intent.ACTION_VOICE_COMMAND, intent.action)
            assertNull(intent.`package`)
            assertEquals("Explain quantum computing", intent.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals("Explain quantum computing", intent.getStringExtra(SearchManager.QUERY))
            assertEquals("Explain quantum computing", intent.getStringExtra("query"))
            assertThat(
                intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
                equalTo(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

    @Test
    fun `AndroidGeminiAdapter dispatches ACTION_VOICE_COMMAND when bard is not installed`() =
        runTest(testDispatcher) {
            whenever(
                mockPackageManager.getPackageInfo(AndroidGeminiAdapter.GEMINI_PACKAGE, 0),
            ).thenThrow(PackageManager.NameNotFoundException())

            val adapter = AndroidGeminiAdapter(mockContext)
            val result = adapter.executePrompt("Play music", mode = GeminiExecutionMode.INTENT_ONLY)

            assertThat(result is Success, equalTo(true))
            val captor = argumentCaptor<Intent>()
            verify(mockContext).startActivity(captor.capture())
            val intent = captor.firstValue

            assertNotEquals(Intent.ACTION_WEB_SEARCH, intent.action)
            assertNotEquals(Intent.ACTION_SEND, intent.action)
            assertEquals(Intent.ACTION_VOICE_COMMAND, intent.action)
            assertNull(intent.`package`)
            assertEquals("Play music", intent.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals("Play music", intent.getStringExtra(SearchManager.QUERY))
            assertEquals("Play music", intent.getStringExtra("query"))
            assertThat(
                intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
                equalTo(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

    @Test
    fun `AndroidGeminiAdapter falls back to ACTION_ASSIST when voice command fails`() =
        runTest(testDispatcher) {
            whenever(
                mockPackageManager.getPackageInfo(AndroidGeminiAdapter.GEMINI_PACKAGE, 0),
            ).thenThrow(PackageManager.NameNotFoundException())

            whenever(mockContext.startActivity(any())).thenAnswer { invocation ->
                val intent = invocation.getArgument<Intent>(0)
                if (intent.action == Intent.ACTION_VOICE_COMMAND) {
                    throw ActivityNotFoundException("Voice command not supported")
                }
                null
            }

            val adapter = AndroidGeminiAdapter(mockContext)
            val result = adapter.executePrompt("Set alarm", mode = GeminiExecutionMode.INTENT_ONLY)

            assertThat(result is Success, equalTo(true))
            val captor = argumentCaptor<Intent>()
            verify(mockContext, times(2)).startActivity(captor.capture())

            assertEquals(Intent.ACTION_VOICE_COMMAND, captor.firstValue.action)
            assertNotEquals(Intent.ACTION_WEB_SEARCH, captor.secondValue.action)
            assertNotEquals(Intent.ACTION_SEND, captor.secondValue.action)
            assertEquals(Intent.ACTION_ASSIST, captor.secondValue.action)
            assertNull(captor.secondValue.`package`)
            assertEquals("Set alarm", captor.secondValue.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals("Set alarm", captor.secondValue.getStringExtra(SearchManager.QUERY))
            assertEquals("Set alarm", captor.secondValue.getStringExtra("query"))
            assertThat(
                captor.secondValue.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
                equalTo(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

    @Test
    fun `AndroidGeminiAdapter handles API response successfully with feedback`() =
        runTest(testDispatcher) {
            val adapter = AndroidGeminiAdapter(mockContext)
            val mockCall: Call = mock()
            val mockResponse: Response = mock()
            val mockResponseBody: ResponseBody = mock()
            val json =
                """{"candidates":[{"content":{"parts":[{"text":"Generated response"}]}}]}"""

            whenever(mockResponseBody.string()).thenReturn(json)
            whenever(mockResponse.isSuccessful).thenReturn(true)
            whenever(mockResponse.body).thenReturn(mockResponseBody)
            whenever(mockCall.execute()).thenReturn(mockResponse)

            val mockHttpClient: OkHttpClient = mock {
                on { newCall(any()) } doReturn mockCall
            }
            adapter.httpClient = mockHttpClient

            val result = adapter.executePrompt(
                prompt = "Tell a joke",
                apiKey = "fake-key",
                model = "gemini-2.0-flash",
                mode = GeminiExecutionMode.API_ONLY,
            )

            assertThat(result is Success, equalTo(true))
            assertEquals("Generated response", (result as Success).value)
        }
}
