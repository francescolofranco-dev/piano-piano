package dev.lofrancolabs.pianopiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lofrancolabs.pianopiano.model.DurationVerdict
import dev.lofrancolabs.pianopiano.model.ExerciseSummary
import dev.lofrancolabs.pianopiano.model.MidiUtils
import dev.lofrancolabs.pianopiano.model.PitchVerdict
import dev.lofrancolabs.pianopiano.model.TimingVerdict
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScorecardDialog(
    summary: ExerciseSummary,
    onRetry: () -> Unit,
    onNextExercise: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PianoColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, PianoColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Exercise Complete",
                            style = MaterialTheme.typography.labelMedium,
                            color = PianoColors.WarmGold
                        )
                        Text(
                            text = summary.exerciseTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PianoColors.TextPrimary
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = PianoColors.TextMuted)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        label = "Pitch",
                        value = "${summary.pitchAccuracyPercent}%",
                        color = metricColor(summary.pitchAccuracyPercent),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Timing",
                        value = "${summary.timingAccuracyPercent}%",
                        color = metricColor(summary.timingAccuracyPercent),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Hold Length",
                        value = "${summary.durationAccuracyPercent}%",
                        color = metricColor(summary.durationAccuracyPercent),
                        modifier = Modifier.weight(1f)
                    )
                }

                val tendencyText = when {
                    abs(summary.averageOnsetOffsetMs) <= 20 ->
                        "Timing tendency: steady right on the beat (${summary.averageOnsetOffsetMs}ms avg)."
                    summary.averageOnsetOffsetMs < 0 ->
                        "Timing tendency: rushing slightly (${summary.averageOnsetOffsetMs}ms avg)."
                    else ->
                        "Timing tendency: trailing slightly (+${summary.averageOnsetOffsetMs}ms avg)."
                }
                Text(
                    text = tendencyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PianoColors.TextMuted
                )

                Text(
                    text = "Note Breakdown (${summary.totalNotes} notes)",
                    style = MaterialTheme.typography.labelLarge,
                    color = PianoColors.TextPrimary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.evaluations.forEach { eval ->
                        val noteName = MidiUtils.midiToName(eval.expectedMidi)
                        val badge = eval.feedbackBadgeText() ?: "OK"
                        val chipColor = when {
                            eval.timingVerdict == TimingVerdict.MISSED ||
                                eval.pitchVerdict == PitchVerdict.WRONG_KEY ||
                                eval.durationVerdict == DurationVerdict.TOO_LONG -> PianoColors.Coral
                            eval.timingVerdict == TimingVerdict.TOO_SOON ||
                                eval.timingVerdict == TimingVerdict.TOO_LATE ||
                                eval.durationVerdict == DurationVerdict.TOO_SHORT -> PianoColors.Amber
                            else -> PianoColors.Emerald
                        }
                        Column(
                            modifier = Modifier
                                .background(chipColor.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .border(1.dp, chipColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "#${eval.targetIndex + 1} $noteName",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PianoColors.TextPrimary
                            )
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = chipColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Try Again", color = PianoColors.TextPrimary)
                    }
                    Button(
                        onClick = onNextExercise,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PianoColors.WarmGold,
                            contentColor = PianoColors.Background
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next Exercise", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(PianoColors.SurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, PianoColors.Border, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = PianoColors.TextMuted
        )
    }
}

private fun metricColor(percent: Int): Color = when {
    percent >= 80 -> PianoColors.Emerald
    percent >= 55 -> PianoColors.Amber
    else -> PianoColors.Coral
}
