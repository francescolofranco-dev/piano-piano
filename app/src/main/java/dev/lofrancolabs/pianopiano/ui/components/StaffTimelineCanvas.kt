package dev.lofrancolabs.pianopiano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lofrancolabs.pianopiano.model.DurationVerdict
import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.MidiUtils
import dev.lofrancolabs.pianopiano.model.NoteEvaluation
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem
import dev.lofrancolabs.pianopiano.model.PitchVerdict
import dev.lofrancolabs.pianopiano.model.SessionPhase
import dev.lofrancolabs.pianopiano.model.TimingVerdict
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun StaffTimelineCanvas(
    exercise: Exercise,
    bpm: Int,
    phase: SessionPhase,
    beatPosition: Float,
    countInBeat: Int,
    beatFlashAlpha: Float,
    evaluations: List<NoteEvaluation>,
    noteNaming: NoteNamingSystem,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val latestBeat by rememberUpdatedState(beatPosition)
    val latestBpm by rememberUpdatedState(bpm)
    var smoothBeat by remember(exercise.id) { mutableFloatStateOf(beatPosition) }

    LaunchedEffect(phase, exercise.id) {
        if (phase == SessionPhase.PREVIEWING || phase == SessionPhase.COUNT_IN || phase == SessionPhase.PLAYING) {
            smoothBeat = latestBeat
            var prevFrameNanos = 0L
            while (true) {
                withInfiniteAnimationFrameNanos { frameNanos ->
                    if (prevFrameNanos != 0L) {
                        val dtSec = ((frameNanos - prevFrameNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
                        val predicted = smoothBeat + dtSec * (latestBpm / 60f)
                        val target = latestBeat
                        smoothBeat = if (abs(predicted - target) > 0.14f) {
                            target
                        } else {
                            predicted + (target - predicted) * 0.25f
                        }
                    } else {
                        smoothBeat = latestBeat
                    }
                    prevFrameNanos = frameNanos
                }
            }
        } else {
            smoothBeat = latestBeat
        }
    }

    val noteLabelStyle = remember {
        TextStyle(color = PianoColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
    val badgeStyle = remember {
        TextStyle(color = PianoColors.Background, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
    val clefStyle = remember {
        TextStyle(color = PianoColors.TextMuted.copy(alpha = 0.65f), fontSize = 38.sp)
    }
    val countInStyle = remember {
        TextStyle(color = PianoColors.WarmGold, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
    val restLabelStyle = remember {
        TextStyle(color = PianoColors.TextMuted.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }

    val evalByEventIndex = remember(evaluations) {
        evaluations.associateBy { it.noteEventIndex }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(PianoColors.Surface)
            .border(1.dp, PianoColors.Border, RoundedCornerShape(16.dp))
    ) {
        val width = size.width
        val height = size.height
        val playheadX = width * 0.22f
        val pxPerBeat = max(72.dp.toPx(), width / 7.8f)

        val f5Y = height * 0.34f
        val staffSpan = height * 0.36f
        val stepSpacing = staffSpan / 8f

        fun stepToY(stepFromE4: Int): Float = f5Y + (8 - stepFromE4) * stepSpacing
        fun beatToX(beat: Float): Float = playheadX + (beat - smoothBeat) * pxPerBeat

        val msPerBeat = 60_000.0 / bpm

        // Beat flash subtle glow along playhead strip
        if (beatFlashAlpha > 0.01f) {
            val flashColor = if (phase == SessionPhase.PREVIEWING) {
                PianoColors.TargetBlue
            } else {
                PianoColors.WarmGold
            }
            drawRect(
                color = flashColor.copy(alpha = 0.09f * beatFlashAlpha),
                topLeft = Offset(playheadX - 18.dp.toPx(), 0f),
                size = Size(36.dp.toPx(), height)
            )
        }

        // Measure barlines and beat grid ticks
        val totalBeatsInt = ceil(exercise.totalBeats.toDouble()).toInt().coerceAtLeast(4)
        for (b in 0..totalBeatsInt) {
            val x = beatToX(b.toFloat())
            if (x < -40f || x > width + 40f) continue
            val isBarline = (b % exercise.beatsPerBar) == 0
            if (isBarline) {
                drawLine(
                    color = PianoColors.Border.copy(alpha = 0.9f),
                    start = Offset(x, f5Y - stepSpacing * 0.4f),
                    end = Offset(x, stepToY(0) + stepSpacing * 0.4f),
                    strokeWidth = 1.6.dp.toPx()
                )
            } else {
                drawLine(
                    color = PianoColors.Border.copy(alpha = 0.38f),
                    start = Offset(x, f5Y),
                    end = Offset(x, stepToY(0)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
        }

        // 5 Treble Clef Staff Lines (steps 0, 2, 4, 6, 8)
        for (lineStep in 0..8 step 2) {
            val lineY = stepToY(lineStep)
            drawLine(
                color = Color(0xFF394256),
                start = Offset(0f, lineY),
                end = Offset(width, lineY),
                strokeWidth = 1.3.dp.toPx()
            )
        }

        // Treble Clef emblem on left gutter
        val clefLayout = textMeasurer.measure("\uD834\uDD1E", clefStyle)
        drawText(
            textLayoutResult = clefLayout,
            topLeft = Offset(12.dp.toPx(), f5Y - stepSpacing * 1.1f)
        )

        val noteRadius = (stepSpacing * 0.90f).coerceIn(7.dp.toPx(), 13.dp.toPx())
        val barHeight = (stepSpacing * 1.55f).coerceIn(13.dp.toPx(), 24.dp.toPx())

        // Render Notes & Rests
        exercise.notes.forEachIndexed { eventIdx, note ->
            val xStart = beatToX(note.startBeat)
            val rawWidth = note.durationBeats * pxPerBeat
            val xEnd = xStart + rawWidth
            if (xEnd < -120f || xStart > width + 120f) return@forEachIndexed

            val barWidth = (rawWidth - 6.dp.toPx()).coerceAtLeast(18.dp.toPx())

            if (note.isRest) {
                val restY = stepToY(4) - barHeight * 0.42f
                drawRoundRect(
                    color = PianoColors.SurfaceElevated.copy(alpha = 0.85f),
                    topLeft = Offset(xStart, restY),
                    size = Size(barWidth, barHeight * 0.84f),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                drawRoundRect(
                    color = PianoColors.TextMuted.copy(alpha = 0.35f),
                    topLeft = Offset(xStart, restY),
                    size = Size(barWidth, barHeight * 0.84f),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
                val restText = textMeasurer.measure("rest", restLabelStyle)
                drawText(
                    textLayoutResult = restText,
                    topLeft = Offset(
                        xStart + (barWidth - restText.size.width) / 2f,
                        restY + (barHeight * 0.84f - restText.size.height) / 2f
                    )
                )
                return@forEachIndexed
            }

            val midi = note.midiNote ?: return@forEachIndexed
            val step = MidiUtils.trebleStaffStepFromE4(midi)
            val noteY = stepToY(step)
            val eval = evalByEventIndex[eventIdx]

            // Ledger line for Middle C (C4) or lower
            if (step <= -2) {
                val c4Y = stepToY(-2)
                drawLine(
                    color = Color(0xFF56627A),
                    start = Offset(xStart - noteRadius * 1.7f, c4Y),
                    end = Offset(xStart + noteRadius * 1.7f, c4Y),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            val isCurrentPreviewNote =
                phase == SessionPhase.PREVIEWING &&
                    smoothBeat >= note.startBeat &&
                    smoothBeat < (note.startBeat + note.durationBeats)

            val targetBarTop = noteY - barHeight / 2f
            val baseFill = if (isCurrentPreviewNote) {
                PianoColors.TargetBlue.copy(alpha = 0.48f)
            } else {
                PianoColors.TargetBlue.copy(alpha = 0.17f)
            }
            val baseStroke = if (isCurrentPreviewNote) {
                PianoColors.TargetBlue
            } else {
                PianoColors.TargetBlue.copy(alpha = 0.52f)
            }

            // Target Proportional Duration Bar
            drawRoundRect(
                color = baseFill,
                topLeft = Offset(xStart, targetBarTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f)
            )
            drawRoundRect(
                color = baseStroke,
                topLeft = Offset(xStart, targetBarTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f),
                style = Stroke(width = 1.3.dp.toPx())
            )

            // Live Hold Fill & Overflow Indicator
            if (eval != null && eval.activeHoldStartMs != null) {
                val actualStartBeat =
                    note.startBeat + ((eval.activeHoldStartMs - eval.expectedStartMs) / msPerBeat).toFloat()
                val actualEndBeat = if (eval.activeHoldEndMs != null) {
                    note.startBeat + ((eval.activeHoldEndMs - eval.expectedStartMs) / msPerBeat).toFloat()
                } else {
                    max(actualStartBeat, smoothBeat)
                }
                val holdStartX = beatToX(actualStartBeat)
                val holdEndX = beatToX(actualEndBeat)
                val barRight = xStart + barWidth

                val fillColor = when {
                    eval.pitchVerdict == PitchVerdict.WRONG_KEY ||
                        eval.durationVerdict == DurationVerdict.TOO_LONG -> PianoColors.Coral
                    eval.durationVerdict == DurationVerdict.TOO_SHORT -> PianoColors.Amber
                    else -> PianoColors.Emerald
                }

                val clampedWidth = (minOf(holdEndX, barRight) - holdStartX).coerceAtLeast(6.dp.toPx())
                drawRoundRect(
                    color = fillColor.copy(alpha = 0.78f),
                    topLeft = Offset(holdStartX, targetBarTop + 2.dp.toPx()),
                    size = Size(clampedWidth, (barHeight - 4.dp.toPx()).coerceAtLeast(4.dp.toPx())),
                    cornerRadius = CornerRadius(barHeight / 2f)
                )

                // Overflow tail when held past the end of the target duration bar
                if (holdEndX > barRight + 2.dp.toPx()) {
                    drawRoundRect(
                        color = PianoColors.Coral.copy(alpha = 0.85f),
                        topLeft = Offset(barRight, targetBarTop + 3.dp.toPx()),
                        size = Size(holdEndX - barRight, (barHeight - 6.dp.toPx()).coerceAtLeast(3.dp.toPx())),
                        cornerRadius = CornerRadius(barHeight / 2f)
                    )
                }
            }

            // Notehead
            val noteheadColor = when {
                eval?.timingVerdict == TimingVerdict.MISSED -> PianoColors.Coral.copy(alpha = 0.6f)
                eval?.pitchVerdict == PitchVerdict.WRONG_KEY -> PianoColors.Coral
                eval?.timingVerdict == TimingVerdict.PERFECT -> PianoColors.Emerald
                eval?.timingVerdict == TimingVerdict.TOO_SOON ||
                    eval?.timingVerdict == TimingVerdict.TOO_LATE -> PianoColors.Amber
                isCurrentPreviewNote -> PianoColors.TargetBlue
                else -> PianoColors.TextPrimary
            }

            if (note.durationBeats >= 2f) {
                // Hollow half/whole notehead
                drawCircle(
                    color = PianoColors.Surface,
                    radius = noteRadius,
                    center = Offset(xStart, noteY)
                )
                drawCircle(
                    color = noteheadColor,
                    radius = noteRadius,
                    center = Offset(xStart, noteY),
                    style = Stroke(width = 2.6.dp.toPx())
                )
            } else {
                // Solid quarter/eighth notehead
                drawCircle(
                    color = noteheadColor,
                    radius = noteRadius,
                    center = Offset(xStart, noteY)
                )
            }

            // Note pitch label
            val noteNameLayout = textMeasurer.measure(
                MidiUtils.midiToName(midi, noteNaming),
                noteLabelStyle
            )
            drawText(
                textLayoutResult = noteNameLayout,
                topLeft = Offset(
                    xStart - noteNameLayout.size.width / 2f,
                    noteY + noteRadius + 3.dp.toPx()
                )
            )

            // Floating Feedback Badge
            val badgeText = eval?.feedbackBadgeText(noteNaming)
            if (eval != null && badgeText != null) {
                val badgeBg = when {
                    eval.timingVerdict == TimingVerdict.MISSED ||
                        eval.pitchVerdict == PitchVerdict.WRONG_KEY ||
                        eval.durationVerdict == DurationVerdict.TOO_LONG -> PianoColors.Coral
                    eval.timingVerdict == TimingVerdict.TOO_SOON ||
                        eval.timingVerdict == TimingVerdict.TOO_LATE ||
                        eval.durationVerdict == DurationVerdict.TOO_SHORT -> PianoColors.Amber
                    else -> PianoColors.Emerald
                }
                val badgeMeasured = textMeasurer.measure(badgeText, badgeStyle)
                val padX = 8.dp.toPx()
                val padY = 4.dp.toPx()
                val pillW = badgeMeasured.size.width + padX * 2f
                val pillH = badgeMeasured.size.height + padY * 2f
                val rowY = if (eval.targetIndex % 2 == 0) height * 0.05f else height * 0.16f
                val pillX = (xStart + minOf(barWidth * 0.35f, 30.dp.toPx()) - pillW / 2f)
                    .coerceAtMost((width - pillW - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))

                if (pillX + pillW > 0f && pillX < width) {
                    drawRoundRect(
                        color = badgeBg,
                        topLeft = Offset(pillX, rowY),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(pillH / 2f)
                    )
                    drawText(
                        textLayoutResult = badgeMeasured,
                        topLeft = Offset(pillX + padX, rowY + padY)
                    )
                }
            }
        }

        // Playhead Line ("NOW")
        val playheadColor = if (phase == SessionPhase.PREVIEWING) {
            PianoColors.TargetBlue
        } else {
            PianoColors.WarmGold
        }
        drawLine(
            color = playheadColor,
            start = Offset(playheadX, 6.dp.toPx()),
            end = Offset(playheadX, height - 6.dp.toPx()),
            strokeWidth = 2.4.dp.toPx()
        )
        drawCircle(
            color = playheadColor,
            radius = 5.dp.toPx(),
            center = Offset(playheadX, 10.dp.toPx())
        )

        // Count-In Overlay Number
        if (phase == SessionPhase.COUNT_IN && countInBeat in 1..4) {
            val countText = textMeasurer.measure("Count-in: $countInBeat", countInStyle)
            val boxPadX = 16.dp.toPx()
            val boxPadY = 8.dp.toPx()
            val boxW = countText.size.width + boxPadX * 2f
            val boxH = countText.size.height + boxPadY * 2f
            val boxX = (width - boxW) / 2f
            val boxY = (height - boxH) / 2f
            drawRoundRect(
                color = PianoColors.Background.copy(alpha = 0.88f),
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(14.dp.toPx())
            )
            drawRoundRect(
                color = PianoColors.WarmGold,
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(width = 1.6.dp.toPx())
            )
            drawText(
                textLayoutResult = countText,
                topLeft = Offset(boxX + boxPadX, boxY + boxPadY)
            )
        }
    }
}
