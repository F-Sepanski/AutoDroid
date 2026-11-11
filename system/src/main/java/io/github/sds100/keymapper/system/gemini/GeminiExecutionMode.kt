package io.github.sds100.keymapper.system.gemini

import kotlinx.serialization.Serializable

@Serializable
enum class GeminiExecutionMode {
    API_ONLY,
    INTENT_ONLY,
    API_FALLBACK_INTENT,
}
