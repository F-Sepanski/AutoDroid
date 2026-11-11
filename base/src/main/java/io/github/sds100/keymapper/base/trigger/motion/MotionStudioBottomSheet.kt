package io.github.sds100.keymapper.base.trigger.motion

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sds100.keymapper.base.R
import io.github.sds100.keymapper.base.trigger.TriggerSetupBottomSheet
import io.github.sds100.keymapper.base.trigger.TriggerSetupState
import io.github.sds100.keymapper.base.utils.ui.compose.icons.KeyMapperIcons
import io.github.sds100.keymapper.base.utils.ui.compose.icons.MobileSensorHi
import io.github.sds100.keymapper.system.motion.MotionDirection
import io.github.sds100.keymapper.system.motion.MotionTelemetry
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionStudioBottomSheet(
    sheetState: SheetState,
    state: TriggerSetupState.MotionCombo,
    telemetryFlow: StateFlow<MotionTelemetry>,
    onDismissRequest: () -> Unit,
    onEnableAccessibilityServiceClick: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClearSequence: () -> Unit,
    onSensitivityChanged: (Float, Float) -> Unit,
    onToggleTraining: () -> Unit,
    onToggleDebugger: () -> Unit,
    onSaveCombo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val telemetry by telemetryFlow.collectAsStateWithLifecycle()

    TriggerSetupBottomSheet(
        modifier = modifier,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.motion_studio_title),
        icon = KeyMapperIcons.MobileSensorHi,
        positiveButtonContent = {
            Button(onClick = onSaveCombo, enabled = state.currentSequence.isNotEmpty()) {
                Text(stringResource(R.string.motion_studio_save_button))
            }
        },
    ) {
        MotionVisualizer3D(telemetry = telemetry, modifier = Modifier.fillMaxWidth().height(160.dp))
        MotionSequenceBadges(sequence = state.currentSequence)
        MotionControlButtons(
            isRecording = state.isRecording,
            canClear = state.currentSequence.isNotEmpty(),
            canTrain = state.currentSequence.isNotEmpty(),
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onClearSequence = onClearSequence,
            onToggleTraining = onToggleTraining,
            onToggleDebugger = onToggleDebugger,
        )
        if (state.isTraining && state.currentSequence.isNotEmpty()) {
            MotionTrainingSection(
                sequence = state.currentSequence,
                currentStep = state.trainingCurrentStep,
            )
        }
        MotionSensitivityControls(
            threshold = state.threshold,
            noisePadding = state.noisePadding,
            onSensitivityChanged = onSensitivityChanged,
        )
        if (state.showDebugger) {
            MotionDebuggerSection(telemetry = telemetry)
        }
    }
}

@Composable
fun MotionVisualizer3D(telemetry: MotionTelemetry, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(
            color = surfaceColor.copy(alpha = 0.35f),
            radius = minOf(cx, cy) * 0.85f,
            center = Offset(cx, cy),
        )

        val w = 38f
        val h = 64f
        val d = 14f
        val rxAngle = (-telemetry.y * 0.08f).coerceIn(-1.2f, 1.2f)
        val ryAngle = (telemetry.x * 0.08f).coerceIn(-1.2f, 1.2f)
        val rzAngle = (telemetry.z * 0.04f).coerceIn(-1.0f, 1.0f)

        val local = listOf(
            Triple(-w, -h, -d),
            Triple(w, -h, -d),
            Triple(w, h, -d),
            Triple(-w, h, -d),
            Triple(-w, -h, d),
            Triple(w, -h, d),
            Triple(w, h, d),
            Triple(-w, h, d),
        )
        val projected = local.map { (vx, vy, vz) ->
            val x1 = vx * cos(ryAngle) + vz * sin(ryAngle)
            val z1 = -vx * sin(ryAngle) + vz * cos(ryAngle)
            val y2 = vy * cos(rxAngle) - z1 * sin(rxAngle)
            val z2 = vy * sin(rxAngle) + z1 * cos(rxAngle)
            val x3 = x1 * cos(rzAngle) - y2 * sin(rzAngle)
            val y3 = x1 * sin(rzAngle) + y2 * cos(rzAngle)
            val scale = 260f / (260f + z2)
            Offset(cx + x3 * scale, cy + y3 * scale)
        }

        val frontPath = Path().apply {
            moveTo(projected[4].x, projected[4].y)
            for (i in 5..7) lineTo(projected[i].x, projected[i].y)
            close()
        }
        drawPath(frontPath, color = primaryColor.copy(alpha = 0.22f))

        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7,
        )
        for ((s, e) in edges) {
            drawLine(
                color = primaryColor,
                start = projected[s],
                end = projected[e],
                strokeWidth = 3f,
            )
        }

        if (telemetry.magnitude > 0.4f) {
            val vecEnd = Offset(cx + telemetry.x * 12f, cy - telemetry.y * 12f)
            drawLine(color = secondaryColor, start = Offset(cx, cy), end = vecEnd, strokeWidth = 5f)
            drawCircle(color = secondaryColor, radius = 6f, center = vecEnd)
        }

        if (telemetry.lastDirection != null) {
            drawCircle(
                color = Color(0xFF00E676),
                radius = 12f,
                center = Offset(cx, cy - h - 14f),
                style = Stroke(width = 3.5f),
            )
        }
    }
}

@Composable
private fun MotionSequenceBadges(sequence: List<MotionDirection>) {
    if (sequence.isEmpty()) {
        Text(
            text = stringResource(R.string.motion_studio_empty_sequence_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sequence.forEachIndexed { index, dir ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${index + 1}. ${dir.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionControlButtons(
    isRecording: Boolean,
    canClear: Boolean,
    canTrain: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClearSequence: () -> Unit,
    onToggleTraining: () -> Unit,
    onToggleDebugger: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isRecording) {
            Button(onClick = onStartRecording, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.motion_studio_record_button))
            }
        } else {
            val errColor = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onStopRecording, colors = errColor, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.motion_studio_stop_button))
            }
        }
        OutlinedButton(onClick = onClearSequence, enabled = canClear) {
            Text(stringResource(R.string.motion_studio_clear_button))
        }
        FilledTonalButton(onClick = onToggleTraining, enabled = canTrain) {
            Text(stringResource(R.string.motion_studio_train_button))
        }
        OutlinedButton(onClick = onToggleDebugger) {
            Text(stringResource(R.string.motion_studio_debugger_button))
        }
    }
}

@Composable
private fun MotionTrainingSection(sequence: List<MotionDirection>, currentStep: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val isSuccess = currentStep >= sequence.size
            val statusText = if (isSuccess) {
                stringResource(R.string.motion_studio_training_success)
            } else {
                stringResource(
                    R.string.motion_studio_training_progress,
                    currentStep + 1,
                    sequence.size,
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSuccess) {
                    Color(
                        0xFF00C853,
                    )
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sequence.forEachIndexed { index, _ ->
                    val isDone = index < currentStep
                    val isCurrent = index == currentStep
                    val bg = when {
                        isDone -> Color(0xFF00C853)
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    Box(
                        modifier = Modifier.size(32.dp).background(bg, CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isDone) "✓" else "${index + 1}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MotionSensitivityControls(
    threshold: Float,
    noisePadding: Float,
    onSensitivityChanged: (Float, Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.motion_studio_sensitivity_label, threshold),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = threshold,
            onValueChange = {
                onSensitivityChanged(it, noisePadding)
            },
            valueRange =
            1.5f..8.0f,
            steps = 13,
        )
        Text(
            text = stringResource(R.string.motion_studio_padding_label, noisePadding),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = noisePadding,
            onValueChange = {
                onSensitivityChanged(threshold, it)
            },
            valueRange =
            0.1f..2.0f,
            steps = 19,
        )
    }
}

@Composable
private fun MotionDebuggerSection(telemetry: MotionTelemetry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.motion_studio_debugger_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.motion_studio_debugger_telemetry,
                    telemetry.x,
                    telemetry.y,
                    telemetry.z,
                    telemetry.magnitude,
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = stringResource(
                    R.string.motion_studio_debugger_last_gesture,
                    telemetry.lastDirection?.name ?: "NONE",
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
