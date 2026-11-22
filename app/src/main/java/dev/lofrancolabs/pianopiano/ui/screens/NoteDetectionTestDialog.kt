package dev.lofrancolabs.pianopiano.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lofrancolabs.pianopiano.model.MicSensitivity
import dev.lofrancolabs.pianopiano.model.MicSourceMode
import dev.lofrancolabs.pianopiano.model.MidiUtils
import dev.lofrancolabs.pianopiano.model.SessionPhase
import dev.lofrancolabs.pianopiano.ui.PianoPianoUiState
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun NoteDetectionTestDialog(
    uiState: PianoPianoUiState,
    onRequestMicPermission: () -> Unit,
    onSelectSensitivity: (MicSensitivity) -> Unit,
    onSelectSourceMode: (MicSourceMode) -> Unit,
    onCalibrate: () -> Unit,
    onClearDetectedNotes: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monitor = uiState.audioMonitor
    val liveNoteText = monitor.liveMidi?.let { midi ->
        MidiUtils.midiToName(midi, uiState.noteNaming, includeOctave = true)
    } ?: "--"
    val frequencyText = monitor.liveFrequencyHz?.let { "${it.roundToInt()} Hz" }
        ?: if (uiState.hasMicPermission) "Listening..." else "Mic permission needed"
    val confidenceText = monitor.liveConfidence?.let { "${(it * 100f).roundToInt()}% match" }
        ?: monitor.activeAudioSourceLabel
    val isCalibrating = uiState.phase == SessionPhase.CALIBRATING

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PianoColors.Surface,
            border = BorderStroke(1.dp, PianoColors.Border),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Note Detection Test",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PianoColors.WarmGold
                )

                if (!uiState.hasMicPermission) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PianoColors.WarmGold.copy(alpha = 0.14f))
                            .border(1.dp, PianoColors.WarmGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Allow Microphone access to test live piano note detection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PianoColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestMicPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PianoColors.WarmGold,
                                contentColor = PianoColors.Background
                            )
                        ) {
                            Text("Allow Mic", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Live pitch & meter card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PianoColors.SurfaceElevated)
                        .border(1.dp, PianoColors.Border, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LIVE NOTE",
                                style = MaterialTheme.typography.labelSmall,
                                color = PianoColors.TextMuted
                            )
                            Text(
                                text = liveNoteText,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (monitor.liveMidi != null) PianoColors.Emerald else PianoColors.TextMuted
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = frequencyText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PianoColors.TextPrimary
                            )
                            Text(
                                text = confidenceText,
                                style = MaterialTheme.typography.labelSmall,
                                color = PianoColors.TextMuted
                            )
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(PianoColors.Background)
                            .border(1.dp, PianoColors.Border, RoundedCornerShape(9.dp))
                    ) {
                        val levelFraction = meterFraction(monitor.rms)
                        val thresholdFraction = meterFraction(monitor.onsetThreshold)
                        val barColor = when {
                            monitor.rms >= monitor.onsetThreshold -> PianoColors.Emerald
                            monitor.rms >= monitor.offsetThreshold -> PianoColors.TargetBlue
                            else -> PianoColors.TextMuted
                        }
                        if (levelFraction > 0f) {
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width * levelFraction, size.height),
                                cornerRadius = CornerRadius(9.dp.toPx())
                            )
                        }
                        val tickX = (size.width * thresholdFraction).coerceIn(2f, size.width - 2f)
                        drawLine(
                            color = PianoColors.WarmGold,
                            start = Offset(tickX, 0f),
                            end = Offset(tickX, size.height),
                            strokeWidth = 2.5.dp.toPx()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${monitor.dbFs.roundToInt()} dBFS • Floor ${(uiState.calibratedNoiseFloor * 1000f).roundToInt()} mV",
                            style = MaterialTheme.typography.labelSmall,
                            color = PianoColors.TextMuted
                        )
                        Text(
                            text = "Gold tick = trigger",
                            style = MaterialTheme.typography.labelSmall,
                            color = PianoColors.WarmGold
                        )
                    }
                }

                // Recent strike log
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DETECTED STRIKES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PianoColors.TextMuted
                        )
                        TextButton(
                            onClick = onClearDetectedNotes,
                            enabled = uiState.recentDetectedNotes.isNotEmpty()
                        ) {
                            Text("Clear", color = PianoColors.TextMuted)
                        }
                    }

                    if (uiState.recentDetectedNotes.isEmpty()) {
                        Text(
                            text = "Strike a key on your piano to verify note detection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PianoColors.TextMuted
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.recentDetectedNotes.forEachIndexed { idx, strike ->
                                val noteLabel = MidiUtils.midiToName(
                                    midi = strike.midi,
                                    naming = uiState.noteNaming,
                                    includeOctave = true
                                )
                                Text(
                                    text = noteLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (idx == 0) PianoColors.Emerald else PianoColors.TextPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (idx == 0) {
                                                PianoColors.Emerald.copy(alpha = 0.16f)
                                            } else {
                                                PianoColors.SurfaceElevated
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (idx == 0) PianoColors.Emerald.copy(alpha = 0.5f) else PianoColors.Border,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Sensitivity
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SENSITIVITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PianoColors.TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MicSensitivity.entries.forEach { sensitivity ->
                            FilterChip(
                                selected = uiState.micSensitivity == sensitivity,
                                onClick = { onSelectSensitivity(sensitivity) },
                                label = { Text(sensitivity.label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PianoColors.WarmGold,
                                    selectedLabelColor = PianoColors.Background
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Mic Input Source
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "MICROPHONE INPUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PianoColors.TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MicSourceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.micSourceMode == mode,
                                onClick = { onSelectSourceMode(mode) },
                                label = { Text(mode.label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PianoColors.TargetBlue,
                                    selectedLabelColor = PianoColors.Background
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!uiState.hasMicPermission) onRequestMicPermission() else onCalibrate()
                        },
                        enabled = !isCalibrating,
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Text(
                            text = if (isCalibrating) "Calibrating..." else "Calibrate Noise",
                            color = PianoColors.TextPrimary,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PianoColors.WarmGold,
                            contentColor = PianoColors.Background
                        ),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun meterFraction(rms: Float): Float {
    if (rms <= 0f) return 0f
    return sqrt((rms / 0.08f).coerceIn(0f, 1f))
}
