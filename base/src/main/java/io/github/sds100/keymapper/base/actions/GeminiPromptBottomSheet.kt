package io.github.sds100.keymapper.base.actions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.sds100.keymapper.base.R
import io.github.sds100.keymapper.base.utils.ui.compose.KMBottomSheet
import kotlinx.coroutines.launch

data class GeminiPromptBottomSheetState(val prompt: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiPromptBottomSheet(delegate: CreateActionDelegate) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (delegate.geminiPromptBottomSheetState != null) {
        GeminiPromptBottomSheet(
            sheetState = sheetState,
            state = delegate.geminiPromptBottomSheetState!!,
            onDismissRequest = { delegate.geminiPromptBottomSheetState = null },
            onPromptChange = delegate::onGeminiPromptChange,
            onDoneClick = {
                scope.launch {
                    sheetState.hide()
                    delegate.onDoneGeminiPromptClick()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiPromptBottomSheet(
    sheetState: SheetState,
    state: GeminiPromptBottomSheetState,
    onDismissRequest: () -> Unit = {},
    onPromptChange: (String) -> Unit = {},
    onDoneClick: () -> Unit = {},
) {
    KMBottomSheet(
        title = stringResource(R.string.action_gemini_prompt),
        negButtonText = stringResource(R.string.neg_cancel),
        posButtonText = stringResource(R.string.pos_done),
        onPosButtonClick = onDoneClick,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.prompt,
            onValueChange = onPromptChange,
            label = { Text(stringResource(R.string.action_gemini_prompt)) },
            placeholder = { Text(stringResource(R.string.gemini_prompt_hint)) },
            singleLine = false,
            maxLines = 4,
        )
    }
}
