package io.github.sds100.keymapper.base.actions

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.github.sds100.keymapper.base.repositories.FakePreferenceRepository
import io.github.sds100.keymapper.base.system.accessibility.AccessibilityNodeAction
import io.github.sds100.keymapper.base.system.accessibility.AccessibilityNodeModel
import io.github.sds100.keymapper.base.system.accessibility.BaseAccessibilityService
import io.github.sds100.keymapper.common.utils.KMError
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
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    private lateinit var mockAccessibilityService: BaseAccessibilityService
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

        useCase = createUseCase(testCoroutineScope)
    }

    private fun createUseCase(scope: kotlinx.coroutines.CoroutineScope): PerformActionsUseCaseImpl =
        PerformActionsUseCaseImpl(
            coroutineScope = scope,
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
            settingsRepository = FakePreferenceRepository(),
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

    @Test
    fun `AndroidGeminiAdapter copies prompt to clipboard when dispatching assistant intent`() =
        runTest(testDispatcher) {
            val mockClipboardManager: ClipboardManager = mock()
            whenever(
                mockContext.getSystemService(Context.CLIPBOARD_SERVICE),
            ).thenReturn(mockClipboardManager)

            val adapter = AndroidGeminiAdapter(mockContext)
            val result = adapter.executePrompt(
                "Explain quantum computing",
                mode = GeminiExecutionMode.INTENT_ONLY,
            )

            assertThat(result is Success, equalTo(true))
            val captor = argumentCaptor<ClipData>()
            verify(mockClipboardManager).setPrimaryClip(captor.capture())
            assertEquals(
                "Explain quantum computing",
                captor.firstValue.getItemAt(0).text.toString(),
            )
        }

    @Test
    fun `PerformActionsUseCase injects prompt text and clicks send button via AccessibilityService rootInActiveWindow`() =
        runTest(testDispatcher) {
            useCase = createUseCase(backgroundScope)
            whenever(
                mockGeminiAdapter.executePrompt(any(), anyOrNull(), any(), any()),
            ).doReturn(Success("Dispatched Assistant Intent"))

            val mockRootNode: AccessibilityNodeInfo = mock()
            val mockEditableNode: AccessibilityNodeInfo = mock()
            val mockSendNode: AccessibilityNodeInfo = mock()

            whenever(mockAccessibilityService.rootInActiveWindow).thenReturn(mockRootNode)
            whenever(mockRootNode.childCount).thenReturn(2)
            whenever(mockRootNode.getChild(0)).thenReturn(mockEditableNode)
            whenever(mockRootNode.getChild(1)).thenReturn(mockSendNode)

            whenever(mockEditableNode.isEditable).thenReturn(true)
            whenever(mockSendNode.isClickable).thenReturn(true)
            whenever(mockSendNode.contentDescription).thenReturn("Send")

            val action = ActionData.GeminiPrompt(prompt = "Hello Gemini")
            useCase.perform(action)
            testScheduler.advanceUntilIdle()

            val bundleCaptor = argumentCaptor<Bundle>()
            verify(mockEditableNode).performAction(
                eq(AccessibilityNodeInfo.ACTION_SET_TEXT),
                bundleCaptor.capture(),
            )
            assertEquals(
                "Hello Gemini",
                bundleCaptor.firstValue.getCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                )?.toString(),
            )
            verify(mockSendNode).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

    @Test
    fun `PerformActionsUseCase recognizes localized send buttons and view ids`() =
        runTest(testDispatcher) {
            useCase = createUseCase(backgroundScope)
            whenever(
                mockGeminiAdapter.executePrompt(any(), anyOrNull(), any(), any()),
            ).doReturn(Success("Dispatched Assistant Intent"))

            val mockRootNode: AccessibilityNodeInfo = mock()
            val mockEditableNode: AccessibilityNodeInfo = mock()
            val mockSendNode: AccessibilityNodeInfo = mock()

            whenever(mockAccessibilityService.rootInActiveWindow).thenReturn(mockRootNode)
            whenever(mockRootNode.childCount).thenReturn(2)
            whenever(mockRootNode.getChild(0)).thenReturn(mockEditableNode)
            whenever(mockRootNode.getChild(1)).thenReturn(mockSendNode)

            whenever(mockEditableNode.isEditable).thenReturn(true)
            whenever(mockSendNode.isClickable).thenReturn(true)
            whenever(
                mockSendNode.viewIdResourceName,
            ).thenReturn("com.google.android.apps.bard:id/send_button")

            val action = ActionData.GeminiPrompt(prompt = "Traduza para português")
            useCase.perform(action)
            testScheduler.advanceUntilIdle()

            verify(mockEditableNode).performAction(
                eq(AccessibilityNodeInfo.ACTION_SET_TEXT),
                any(),
            )
            verify(mockSendNode).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

    @Test
    fun `PerformActionsUseCase does not trigger accessibility auto-type when mode is API_ONLY with valid key`() =
        runTest(testDispatcher) {
            useCase = createUseCase(backgroundScope)
            whenever(
                mockGeminiAdapter.executePrompt(any(), anyOrNull(), any(), any()),
            ).doReturn(Success("API result text"))

            val action = ActionData.GeminiPrompt(
                prompt = "Hello",
                apiKey = "test-key",
                mode = GeminiExecutionMode.API_ONLY,
            )
            useCase.perform(action)
            testScheduler.advanceUntilIdle()

            verify(mockAccessibilityService, never()).rootInActiveWindow
        }

    @Test
    fun `PerformActionsUseCase falls back to performActionOnNode when rootInActiveWindow is null`() =
        runTest(testDispatcher) {
            useCase = createUseCase(backgroundScope)
            whenever(
                mockGeminiAdapter.executePrompt(any(), anyOrNull(), any(), any()),
            ).doReturn(Success("Dispatched Assistant Intent"))

            whenever(mockAccessibilityService.rootInActiveWindow).thenReturn(null)

            whenever(
                mockAccessibilityService.performActionOnNode(any(), any()),
            ).thenAnswer { invocation ->
                val findNode = invocation.getArgument<(AccessibilityNodeModel) -> Boolean>(0)
                val performAction = invocation.getArgument<
                    (
                        AccessibilityNodeModel,
                    ) -> AccessibilityNodeAction?,
                    >(1)

                val testNode = AccessibilityNodeModel(
                    packageName = "com.google.android.apps.bard",
                    contentDescription = null,
                    isFocused = true,
                    text = null,
                    textSelectionStart = 0,
                    textSelectionEnd = 0,
                    isEditable = true,
                    className = "android.widget.EditText",
                    uniqueId = null,
                    viewResourceId = null,
                    actions = listOf(AccessibilityNodeInfo.ACTION_SET_TEXT),
                )

                if (findNode(testNode)) {
                    performAction(testNode)
                    Success(Unit)
                } else {
                    val sendNode = AccessibilityNodeModel(
                        packageName = "com.google.android.apps.bard",
                        contentDescription = "Send",
                        isFocused = false,
                        text = null,
                        textSelectionStart = 0,
                        textSelectionEnd = 0,
                        isEditable = false,
                        className = "android.widget.Button",
                        uniqueId = null,
                        viewResourceId = "com.google.android.apps.bard:id/send_button",
                        actions = listOf(AccessibilityNodeInfo.ACTION_CLICK),
                    )
                    if (findNode(sendNode)) {
                        performAction(sendNode)
                        Success(Unit)
                    } else {
                        KMError.FailedToFindAccessibilityNode
                    }
                }
            }

            val action = ActionData.GeminiPrompt(prompt = "Fallback test")
            useCase.perform(action)
            testScheduler.advanceUntilIdle()

            verify(mockAccessibilityService, atLeastOnce()).performActionOnNode(any(), any())
        }

    @Test
    fun `PerformActionsUseCase triggers GLOBAL_ACTION_VOICE_ASSIST when executing GeminiPrompt in assistant mode`() =
        runTest(testDispatcher) {
            useCase = createUseCase(backgroundScope)
            whenever(
                mockGeminiAdapter.executePrompt(any(), anyOrNull(), any(), any()),
            ).doReturn(Success("Dispatched Assistant Intent"))

            val action = ActionData.GeminiPrompt(
                prompt = "Open Assistant",
                mode = GeminiExecutionMode.INTENT_ONLY,
            )
            useCase.perform(action)

            verify(mockAccessibilityService).doGlobalAction(
                PerformActionsUseCaseImpl.GLOBAL_ACTION_VOICE_ASSIST,
            )
        }

    @Test
    fun `PerformActionsUseCase scans overlay windows from AccessibilityService windows and injects text into editable node`() =
        runTest(testDispatcher) {
            useCase = createUseCase(backgroundScope)
            whenever(
                mockGeminiAdapter.executePrompt(any(), anyOrNull(), any(), any()),
            ).doReturn(Success("Dispatched Assistant Intent"))

            val mockWindow1: AccessibilityWindowInfo = mock()
            val mockWindow2: AccessibilityWindowInfo = mock()
            val mockNonEditableRoot: AccessibilityNodeInfo = mock()
            val mockOverlayRoot: AccessibilityNodeInfo = mock()
            val mockEditableNode: AccessibilityNodeInfo = mock()
            val mockSendNode: AccessibilityNodeInfo = mock()

            whenever(mockAccessibilityService.windows).thenReturn(listOf(mockWindow1, mockWindow2))
            whenever(mockWindow1.root).thenReturn(mockNonEditableRoot)
            whenever(mockWindow2.root).thenReturn(mockOverlayRoot)

            whenever(mockNonEditableRoot.childCount).thenReturn(0)
            whenever(mockOverlayRoot.childCount).thenReturn(2)
            whenever(mockOverlayRoot.getChild(0)).thenReturn(mockEditableNode)
            whenever(mockOverlayRoot.getChild(1)).thenReturn(mockSendNode)

            whenever(mockEditableNode.isEditable).thenReturn(true)
            whenever(mockSendNode.isClickable).thenReturn(true)
            whenever(mockSendNode.contentDescription).thenReturn("Submit")

            val action = ActionData.GeminiPrompt(prompt = "Hello from overlay")
            useCase.perform(action)
            testScheduler.advanceUntilIdle()

            val bundleCaptor = argumentCaptor<Bundle>()
            verify(mockEditableNode).performAction(
                eq(AccessibilityNodeInfo.ACTION_SET_TEXT),
                bundleCaptor.capture(),
            )
            assertEquals(
                "Hello from overlay",
                bundleCaptor.firstValue.getCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                )?.toString(),
            )
            verify(mockSendNode).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

    @Test
    fun `AndroidGeminiAdapter does not fail or throw when background startActivity is blocked`() =
        runTest(testDispatcher) {
            whenever(mockContext.startActivity(any())).thenThrow(
                SecurityException("Background activity start blocked"),
            )

            val adapter = AndroidGeminiAdapter(mockContext)
            val result = adapter.executePrompt(
                "Explain quantum computing",
                mode = GeminiExecutionMode.INTENT_ONLY,
            )

            assertThat(result is Success, equalTo(true))
            assertEquals("Dispatched Assistant Intent", (result as Success).value)
        }
}
