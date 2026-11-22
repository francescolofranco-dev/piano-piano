package dev.lofrancolabs.pianopiano.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lofrancolabs.pianopiano.domain.XpReward
import dev.lofrancolabs.pianopiano.model.DurationVerdict
import dev.lofrancolabs.pianopiano.model.ExerciseSummary
import dev.lofrancolabs.pianopiano.model.PitchVerdict
import dev.lofrancolabs.pianopiano.model.TimingVerdict
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors

private data class FeedbackCountBadge(val label: String, val count: Int, val color: Color)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScorecardDialog(
    summary: ExerciseSummary,
    xpReward: XpReward?,
    onRetry: () -> Unit,
    onNextExercise: () -> Unit,
    onDismiss: () -> Unit
) {
    val evals = summary.evaluations
    val summaryBadges = buildList {
        val perfect = evals.count {
            it.pitchVerdict == PitchVerdict.CORRECT &&
                it.timingVerdict == TimingVerdict.PERFECT &&
                it.durationVerdict == DurationVerdict.GOOD
        }
        val tooSoon = evals.count { it.timingVerdict == TimingVerdict.TOO_SOON }
        val tooLate = evals.count { it.timingVerdict == TimingVerdict.TOO_LATE }
        val tooShort = evals.count { it.durationVerdict == DurationVerdict.TOO_SHORT }
        val tooLong = evals.count { it.durationVerdict == DurationVerdict.TOO_LONG }
        val wrongKey = evals.count { it.pitchVerdict == PitchVerdict.WRONG_KEY }
        val missed = evals.count { it.timingVerdict == TimingVerdict.MISSED }

        if (perfect > 0) add(FeedbackCountBadge("Perfect", perfect, PianoColors.Emerald))
        if (tooSoon > 0) add(FeedbackCountBadge("Too soon", tooSoon, PianoColors.Amber))
        if (tooLate > 0) add(FeedbackCountBadge("Too late", tooLate, PianoColors.Amber))
        if (tooShort > 0) add(FeedbackCountBadge("Too short", tooShort, PianoColors.Amber))
        if (tooLong > 0) add(FeedbackCountBadge("Too long", tooLong, PianoColors.Coral))
        if (wrongKey > 0) add(FeedbackCountBadge("Wrong key", wrongKey, PianoColors.Coral))
        if (missed > 0) add(FeedbackCountBadge("Missed", missed, PianoColors.Coral))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PianoColors.Surface,
            border = BorderStroke(1.dp, PianoColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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

                if (xpReward != null) {
                    val progress = xpReward.updatedProgress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (xpReward.leveledUp) PianoColors.WarmGold.copy(alpha = 0.16f)
                                else PianoColors.SurfaceElevated
                            )
                            .border(
                                1.dp,
                                if (xpReward.leveledUp) PianoColors.WarmGold else PianoColors.Border,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (xpReward.leveledUp) {
                                    "Level Up! \u2192 Level ${progress.level}"
                                } else {
                                    "Level ${progress.level}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (xpReward.leveledUp) PianoColors.WarmGold else PianoColors.TextPrimary
                            )
                            Text(
                                text = "+${xpReward.xpEarned} XP",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (xpReward.xpEarned > 0) PianoColors.Emerald else PianoColors.TextMuted
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress.levelProgressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PianoColors.WarmGold,
                            trackColor = PianoColors.Background
                        )
                        Text(
                            text = "${progress.xpIntoCurrentLevel} / ${progress.xpNeededForNextLevel} XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = PianoColors.TextMuted
                        )
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
                        label = "Hold",
                        value = "${summary.durationAccuracyPercent}%",
                        color = metricColor(summary.durationAccuracyPercent),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "${summary.totalNotes} notes",
                    style = MaterialTheme.typography.labelMedium,
                    color = PianoColors.TextMuted
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summaryBadges.forEach { badge ->
                        Text(
                            text = "${badge.count} ${badge.label}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = badge.color,
                            modifier = Modifier
                                .background(badge.color.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .border(1.dp, badge.color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
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
                        Text("Play Next \u2192", fontWeight = FontWeight.Bold)
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
