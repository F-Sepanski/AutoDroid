package io.github.sds100.keymapper.system.gemini

import io.github.sds100.keymapper.common.utils.KMResult

interface GeminiAdapter {
    suspend fun executePrompt(
        prompt: String,
        apiKey: String? = null,
        model: String = "gemini-2.0-flash",
        mode: GeminiExecutionMode = GeminiExecutionMode.API_FALLBACK_INTENT,
    ): KMResult<String>
}
