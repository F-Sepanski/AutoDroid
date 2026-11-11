package io.github.sds100.keymapper.system.gemini

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.sds100.keymapper.common.utils.KMError
import io.github.sds100.keymapper.common.utils.KMResult
import io.github.sds100.keymapper.common.utils.Success
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

@Singleton
class AndroidGeminiAdapter @Inject constructor(@ApplicationContext private val context: Context) :
    GeminiAdapter {

    internal var httpClient: OkHttpClient = OkHttpClient()

    override suspend fun executePrompt(
        prompt: String,
        apiKey: String?,
        model: String,
        mode: GeminiExecutionMode,
    ): KMResult<String> {
        if (mode == GeminiExecutionMode.INTENT_ONLY) {
            return dispatchAssistantIntent(prompt)
        }

        if (apiKey.isNullOrBlank()) {
            return if (mode == GeminiExecutionMode.API_FALLBACK_INTENT) {
                dispatchAssistantIntent(prompt)
            } else {
                KMError.UnknownIOError
            }
        }

        val apiResult = callGeminiApi(prompt, apiKey, model)
        return when (apiResult) {
            is Success -> apiResult
            is KMError -> {
                if (mode == GeminiExecutionMode.API_FALLBACK_INTENT) {
                    dispatchAssistantIntent(prompt)
                } else {
                    apiResult
                }
            }
        }
    }

    private suspend fun callGeminiApi(
        prompt: String,
        apiKey: String,
        model: String,
    ): KMResult<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "$model:generateContent?key=$apiKey"
        val jsonPayload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", JsonPrimitive(prompt))
                        }
                    }
                }
            }
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody(mediaType))
            .build()

        return try {
            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Timber.e("Gemini API call unsuccessful: HTTP ${resp.code}")
                    return KMError.UnknownIOError
                }
                val bodyString = resp.body?.string() ?: ""
                val text = extractTextFromResponse(bodyString)
                if (text != null) {
                    Success(text)
                } else {
                    Timber.e("Failed to parse Gemini response body")
                    KMError.UnknownIOError
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "I/O error during Gemini API call")
            KMError.UnknownIOError
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during Gemini API call")
            KMError.Exception(e)
        }
    }

    private fun extractTextFromResponse(bodyString: String): String? {
        return try {
            val root = Json.parseToJsonElement(bodyString).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
            val content = firstCandidate?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
        } catch (e: Exception) {
            Timber.e(e, "JSON parsing error for Gemini response")
            null
        }
    }

    private fun dispatchAssistantIntent(prompt: String): KMResult<String> {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, prompt)
            putExtra("query", prompt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Success("Dispatched Assistant Intent")
        } catch (e: Exception) {
            try {
                val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                    putExtra("query", prompt)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(assistIntent)
                Success("Dispatched Assistant Intent")
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to launch assistant intent")
                KMError.Exception(e2)
            }
        }
    }
}
