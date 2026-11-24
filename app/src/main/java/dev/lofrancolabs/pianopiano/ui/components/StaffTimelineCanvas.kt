package dev.lofrancolabs.pianopiano.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import dev.lofrancolabs.pianopiano.model.PianoHand
import dev.lofrancolabs.pianopiano.model.PitchVerdict
import dev.lofrancolabs.pianopiano.model.SessionPhase
import dev.lofrancolabs.pianopiano.model.TimingVerdict
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

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

    val isAnimating = phase == SessionPhase.PREVIEWING ||
        phase == SessionPhase.PLAYING ||
        (phase == SessionPhase.COUNT_IN && countInBeat >= 1)

    LaunchedEffect(isAnimating, exercise.id) {
        if (isAnimating) {
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
        TextStyle(color = PianoColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
    val badgeStyle = remember {
        TextStyle(color = PianoColors.Background, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
    val clefStyle = remember {
        TextStyle(color = PianoColors.TextMuted.copy(alpha = 0.7f), fontSize = 32.sp)
    }
    val grandClefStyle = remember {
        TextStyle(color = PianoColors.TextMuted.copy(alpha = 0.7f), fontSize = 26.sp)
    }
    val countInStyle = remember {
        TextStyle(color = PianoColors.WarmGold, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
    val restLabelStyle = remember {
        TextStyle(color = PianoColors.TextMuted.copy(alpha = 0.7f), fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
    }
    val beatNumberActiveStyle = remember {
        TextStyle(color = PianoColors.Background, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
    val beatNumberInactiveStyle = remember {
        TextStyle(color = PianoColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
    val gutterBadgeTextStyle = remember {
        TextStyle(color = PianoColors.Background, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
    }
    val handInSolidStyle = remember {
        TextStyle(color = PianoColors.Background, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
    }
    val handInHollowRightStyle = remember {
        TextStyle(color = PianoColors.TargetBlue, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
    }
    val handInHollowLeftStyle = remember {
        TextStyle(color = PianoColors.LeftHandPurple, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
    }

    val beatActiveLayouts = remember(beatNumberActiveStyle) {
        (1..6).map { textMeasurer.measure(it.toString(), beatNumberActiveStyle) }
    }
    val beatInactiveLayouts = remember(beatNumberInactiveStyle) {
        (1..6).map { textMeasurer.measure(it.toString(), beatNumberInactiveStyle) }
    }
    val trebleClefRegular = remember(clefStyle) { textMeasurer.measure("\uD834\uDD1E", clefStyle) }
    val trebleClefGrand = remember(grandClefStyle) { textMeasurer.measure("\uD834\uDD1E", grandClefStyle) }
    val gutterRightLayout = remember(gutterBadgeTextStyle) { textMeasurer.measure("R", gutterBadgeTextStyle) }
    val gutterLeftLayout = remember(gutterBadgeTextStyle) { textMeasurer.measure("L", gutterBadgeTextStyle) }
    val handSolidRight = remember(handInSolidStyle) { textMeasurer.measure("R", handInSolidStyle) }
    val handSolidLeft = remember(handInSolidStyle) { textMeasurer.measure("L", handInSolidStyle) }
    val handHollowRight = remember(handInHollowRightStyle) { textMeasurer.measure("R", handInHollowRightStyle) }
    val handHollowLeft = remember(handInHollowLeftStyle) { textMeasurer.measure("L", handInHollowLeftStyle) }

    val evalByEventIndex = remember(evaluations) {
        evaluations.associateBy { it.noteEventIndex }
    }
    val bassClefPath = remember { Path() }

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
        val pxPerBeat = max(58.dp.toPx(), width / 7.8f)

        val usesGrand = exercise.usesGrandStaff
        val showRightStaff = exercise.usesRightHand
        val showLeftStaff = exercise.usesLeftHand

        val trebleTopY = if (usesGrand) height * 0.21f else height * 0.34f
        val trebleSpan = if (usesGrand) height * 0.23f else height * 0.36f
        val bassTopY = if (usesGrand) height * 0.56f else height * 0.34f
        val bassSpan = if (usesGrand) height * 0.23f else height * 0.36f

        val stepSpacing = (if (showRightStaff) trebleSpan else bassSpan) / 8f

        fun trebleStepToY(stepFromE4: Int): Float = trebleTopY + (8 - stepFromE4) * (trebleSpan / 8f)
        fun bassStepToY(stepFromG2: Int): Float = bassTopY + (8 - stepFromG2) * (bassSpan / 8f)
        fun handMidiToY(hand: PianoHand, midi: Int): Float {
            return if (hand == PianoHand.LEFT && showLeftStaff) {
                bassStepToY(MidiUtils.bassStaffStepFromG2(midi))
            } else {
                trebleStepToY(MidiUtils.trebleStaffStepFromE4(midi))
            }
        }
        fun handStep(hand: PianoHand, midi: Int): Int {
            return if (hand == PianoHand.LEFT && showLeftStaff) {
                MidiUtils.bassStaffStepFromG2(midi)
            } else {
                MidiUtils.trebleStaffStepFromE4(midi)
            }
        }

        fun beatToX(beat: Float): Float = playheadX + (beat - smoothBeat) * pxPerBeat

        val msPerBeat = 60_000.0 / bpm
        val overallStaffTop = if (showRightStaff) trebleTopY else bassTopY
        val overallStaffBottom = if (showLeftStaff) bassTopY + bassSpan else trebleTopY + trebleSpan

        // Beat flash subtle glow along playhead strip
        if (beatFlashAlpha > 0.01f) {
            val flashColor = if (phase == SessionPhase.PREVIEWING) {
                PianoColors.TargetBlue
            } else {
                PianoColors.WarmGold
            }
            drawRect(
                color = flashColor.copy(alpha = 0.15f * beatFlashAlpha),
                topLeft = Offset(playheadX - 18.dp.toPx(), 0f),
                size = Size(36.dp.toPx(), height)
            )
        }

        // Measure barlines and beat grid ticks
        val totalBeatsInt = ceil(exercise.totalBeats.toDouble()).toInt().coerceAtLeast(4)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (b in 0..totalBeatsInt) {
            val x = beatToX(b.toFloat())
            if (x < -40f || x > width + 40f) continue
            val isBarline = (b % exercise.beatsPerBar) == 0
            if (isBarline) {
                drawLine(
                    color = PianoColors.Border.copy(alpha = 0.9f),
                    start = Offset(x, overallStaffTop - stepSpacing * 0.4f),
                    end = Offset(x, overallStaffBottom + stepSpacing * 0.4f),
                    strokeWidth = 1.6.dp.toPx()
                )
            } else {
                if (showRightStaff) {
                    drawLine(
                        color = PianoColors.Border.copy(alpha = 0.38f),
                        start = Offset(x, trebleTopY),
                        end = Offset(x, trebleTopY + trebleSpan),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
                if (showLeftStaff) {
                    drawLine(
                        color = PianoColors.Border.copy(alpha = 0.38f),
                        start = Offset(x, bassTopY),
                        end = Offset(x, bassTopY + bassSpan),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
            }
        }

        // Grand Staff vertical connector bar on far left
        if (usesGrand) {
            val braceX = 6.dp.toPx()
            drawLine(
                color = PianoColors.TextMuted.copy(alpha = 0.65f),
                start = Offset(braceX, trebleTopY),
                end = Offset(braceX, bassTopY + bassSpan),
                strokeWidth = 2.4.dp.toPx()
            )
        }

        // 5 Treble Clef Staff Lines (when RH is active)
        if (showRightStaff) {
            for (lineStep in 0..8 step 2) {
                val lineY = trebleStepToY(lineStep)
                drawLine(
                    color = Color(0xFF394256),
                    start = Offset(0f, lineY),
                    end = Offset(width, lineY),
                    strokeWidth = 1.3.dp.toPx()
                )
            }
            val clefLayout = if (usesGrand) trebleClefGrand else trebleClefRegular
            val clefX = 11.dp.toPx()
            drawText(
                textLayoutResult = clefLayout,
                topLeft = Offset(clefX, trebleTopY - stepSpacing * 1.15f)
            )
            // Explicit 'R' pill badge on treble clef gutter
            val pillSize = 15.dp.toPx()
            val badgeX = (clefX + clefLayout.size.width + 3.dp.toPx()).coerceAtMost(playheadX - pillSize - 4.dp.toPx())
            val badgeY = trebleTopY + trebleSpan / 2f - pillSize / 2f
            drawRoundRect(
                color = PianoColors.TargetBlue,
                topLeft = Offset(badgeX, badgeY),
                size = Size(pillSize, pillSize),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawText(
                textLayoutResult = gutterRightLayout,
                topLeft = Offset(
                    badgeX + (pillSize - gutterRightLayout.size.width) / 2f,
                    badgeY + (pillSize - gutterRightLayout.size.height) / 2f
                )
            )
        }

        // 5 Bass Clef Staff Lines (when LH is active)
        if (showLeftStaff) {
            for (lineStep in 0..8 step 2) {
                val lineY = bassStepToY(lineStep)
                drawLine(
                    color = Color(0xFF394256),
                    start = Offset(0f, lineY),
                    end = Offset(width, lineY),
                    strokeWidth = 1.3.dp.toPx()
                )
            }
            // Crisp vector F-clef anchored around F3 (step 6)
            val bassStep = bassSpan / 8f
            val f3Y = bassStepToY(6)
            val clefLeft = 12.dp.toPx()
            val arcRight = clefLeft + minOf(bassStep * 2.2f, 20.dp.toPx())
            bassClefPath.reset()
            bassClefPath.moveTo(clefLeft + bassStep * 0.4f, f3Y)
            bassClefPath.cubicTo(
                clefLeft + bassStep * 0.4f, f3Y - bassStep * 2.1f,
                arcRight, f3Y - bassStep * 2.1f,
                arcRight, f3Y + bassStep * 0.3f
            )
            bassClefPath.cubicTo(
                arcRight, f3Y + bassStep * 2.4f,
                clefLeft + bassStep * 0.6f, f3Y + bassStep * 3.4f,
                clefLeft + bassStep * 0.1f, f3Y + bassStep * 3.8f
            )
            val clefColor = PianoColors.TextMuted.copy(alpha = 0.78f)
            drawCircle(
                color = clefColor,
                radius = (bassStep * 0.55f).coerceAtLeast(2.5.dp.toPx()),
                center = Offset(clefLeft + bassStep * 0.55f, f3Y)
            )
            drawPath(
                path = bassClefPath,
                color = clefColor,
                style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
            )
            val dotX = arcRight + 3.dp.toPx()
            val dotRadius = (bassStep * 0.28f).coerceAtLeast(1.8.dp.toPx())
            drawCircle(color = clefColor, radius = dotRadius, center = Offset(dotX, bassStepToY(7)))
            drawCircle(color = clefColor, radius = dotRadius, center = Offset(dotX, bassStepToY(5)))

            // Explicit 'L' pill badge on bass clef gutter
            val pillSize = 15.dp.toPx()
            val badgeX = (dotX + 3.dp.toPx()).coerceAtMost(playheadX - pillSize - stepSpacing - 4.dp.toPx())
            val badgeY = bassTopY + bassSpan / 2f - pillSize / 2f
            drawRoundRect(
                color = PianoColors.LeftHandPurple,
                topLeft = Offset(badgeX, badgeY),
                size = Size(pillSize, pillSize),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawText(
                textLayoutResult = gutterLeftLayout,
                topLeft = Offset(
                    badgeX + (pillSize - gutterLeftLayout.size.width) / 2f,
                    badgeY + (pillSize - gutterLeftLayout.size.height) / 2f
                )
            )
        }

        val noteRadius = (stepSpacing * 0.90f).coerceIn(6.5.dp.toPx(), 12.dp.toPx())
        val barHeight = (stepSpacing * 1.55f).coerceIn(12.dp.toPx(), 22.dp.toPx())

        // Draw subtle vertical connectors between simultaneous LH & RH notes
        if (usesGrand) {
            val beatSlots = HashMap<Int, Pair<Float?, Float?>>()
            exercise.notes.forEach { note ->
                val midi = note.midiNote ?: return@forEach
                val xStart = beatToX(note.startBeat)
                if (xStart < -120f || xStart > width + 120f) return@forEach
                val slotKey = (note.startBeat * 100f).roundToInt()
                val noteY = handMidiToY(note.hand, midi)
                val existing = beatSlots[slotKey]
                beatSlots[slotKey] = if (note.hand == PianoHand.RIGHT) {
                    noteY to existing?.second
                } else {
                    existing?.first to noteY
                }
            }
            beatSlots.forEach { (slotKey, pair) ->
                val rhY = pair.first
                val lhY = pair.second
                if (rhY != null && lhY != null) {
                    val x = beatToX(slotKey / 100f)
                    drawLine(
                        color = PianoColors.TextMuted.copy(alpha = 0.40f),
                        start = Offset(x, minOf(rhY, lhY) + noteRadius),
                        end = Offset(x, maxOf(rhY, lhY) - noteRadius),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
            }
        }

        // Render Notes & Rests
        exercise.notes.forEachIndexed { eventIdx, note ->
            val xStart = beatToX(note.startBeat)
            val rawWidth = note.durationBeats * pxPerBeat
            val xEnd = xStart + rawWidth
            if (xEnd < -120f || xStart > width + 120f) return@forEachIndexed

            val barWidth = (rawWidth - 6.dp.toPx()).coerceAtLeast(18.dp.toPx())

            if (note.isRest) {
                val restY = if (note.hand == PianoHand.LEFT && showLeftStaff) {
                    bassStepToY(4) - barHeight * 0.42f
                } else {
                    trebleStepToY(4) - barHeight * 0.42f
                }
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
            val step = handStep(note.hand, midi)
            val noteY = handMidiToY(note.hand, midi)
            val eval = evalByEventIndex[eventIdx]
            val handColor = if (note.hand == PianoHand.LEFT) PianoColors.LeftHandPurple else PianoColors.TargetBlue

            // Ledger lines (Middle C on treble bottom/bass top, or extra ledger above/below)
            if (note.hand == PianoHand.RIGHT) {
                if (step <= -2) {
                    val ledgerY = trebleStepToY(-2)
                    drawLine(
                        color = Color(0xFF56627A),
                        start = Offset(xStart - noteRadius * 1.7f, ledgerY),
                        end = Offset(xStart + noteRadius * 1.7f, ledgerY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                } else if (step >= 10) {
                    val ledgerY = trebleStepToY(10)
                    drawLine(
                        color = Color(0xFF56627A),
                        start = Offset(xStart - noteRadius * 1.7f, ledgerY),
                        end = Offset(xStart + noteRadius * 1.7f, ledgerY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            } else if (showLeftStaff) {
                if (step >= 10) {
                    val ledgerY = bassStepToY(10)
                    drawLine(
                        color = Color(0xFF56627A),
                        start = Offset(xStart - noteRadius * 1.7f, ledgerY),
                        end = Offset(xStart + noteRadius * 1.7f, ledgerY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                } else if (step <= -2) {
                    val ledgerY = bassStepToY(-2)
                    drawLine(
                        color = Color(0xFF56627A),
                        start = Offset(xStart - noteRadius * 1.7f, ledgerY),
                        end = Offset(xStart + noteRadius * 1.7f, ledgerY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            val isCurrentPreviewNote =
                phase == SessionPhase.PREVIEWING &&
                    smoothBeat >= note.startBeat &&
                    smoothBeat < (note.startBeat + note.durationBeats)

            val targetBarTop = noteY - barHeight / 2f
            val baseFill = if (isCurrentPreviewNote) {
                handColor.copy(alpha = 0.48f)
            } else {
                handColor.copy(alpha = 0.17f)
            }
            val baseStroke = if (isCurrentPreviewNote) {
                handColor
            } else {
                handColor.copy(alpha = 0.52f)
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
                isCurrentPreviewNote -> handColor
                else -> PianoColors.TextPrimary
            }

            val isHollow = note.durationBeats >= 2f
            if (isHollow) {
                drawCircle(
                    color = PianoColors.Surface,
                    radius = noteRadius,
                    center = Offset(xStart, noteY)
                )
                drawCircle(
                    color = noteheadColor,
                    radius = noteRadius,
                    center = Offset(xStart, noteY),
                    style = Stroke(width = 2.4.dp.toPx())
                )
            } else {
                drawCircle(
                    color = noteheadColor,
                    radius = noteRadius,
                    center = Offset(xStart, noteY)
                )
            }

            // Explicit R / L hand marker inside notehead
            val handMarker = when {
                isHollow && note.hand == PianoHand.RIGHT -> handHollowRight
                isHollow && note.hand == PianoHand.LEFT -> handHollowLeft
                note.hand == PianoHand.RIGHT -> handSolidRight
                else -> handSolidLeft
            }
            drawText(
                textLayoutResult = handMarker,
                topLeft = Offset(
                    xStart - handMarker.size.width / 2f,
                    noteY - handMarker.size.height / 2f
                )
            )

            // Note pitch label
            val noteNameLayout = textMeasurer.measure(
                MidiUtils.midiToName(midi, noteNaming),
                noteLabelStyle
            )
            drawText(
                textLayoutResult = noteNameLayout,
                topLeft = Offset(
                    xStart - noteNameLayout.size.width / 2f,
                    noteY + noteRadius + 2.dp.toPx()
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
                val rowY = when {
                    usesGrand && note.hand == PianoHand.LEFT ->
                        if (eval.targetIndex % 2 == 0) height * 0.84f else height * 0.91f
                    usesGrand ->
                        if (eval.targetIndex % 2 == 0) height * 0.03f else height * 0.105f
                    else ->
                        if (eval.targetIndex % 2 == 0) height * 0.05f else height * 0.16f
                }
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

        // Compact 1 2 3 4 visual beat indicator pill during count-in, play, and listen preview
        if (isAnimating) {
            val barBeats = exercise.beatsPerBar.coerceIn(2, 6)
            val activeBeat = if (phase == SessionPhase.COUNT_IN) {
                countInBeat.coerceIn(1, barBeats)
            } else {
                (((floor(smoothBeat).toInt() % barBeats) + barBeats) % barBeats) + 1
            }
            val circleDiameter = 18.dp.toPx()
            val circleRadius = circleDiameter / 2f
            val circleGap = 5.dp.toPx()
            val pillPadX = 8.dp.toPx()
            val pillPadY = 5.dp.toPx()
            val pillW = pillPadX * 2f + barBeats * circleDiameter + (barBeats - 1) * circleGap
            val pillH = pillPadY * 2f + circleDiameter
            val pillLeft = (width - pillW - 10.dp.toPx()).coerceAtLeast(10.dp.toPx())
            val pillTop = 10.dp.toPx()
            val activePillColor = if (phase == SessionPhase.PREVIEWING) {
                PianoColors.TargetBlue
            } else {
                PianoColors.WarmGold
            }

            drawRoundRect(
                color = PianoColors.Background.copy(alpha = 0.80f),
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f)
            )
            drawRoundRect(
                color = PianoColors.Border,
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f),
                style = Stroke(width = 1.dp.toPx())
            )

            for (b in 1..barBeats) {
                val isCurrent = b == activeBeat
                val cx = pillLeft + pillPadX + (b - 1) * (circleDiameter + circleGap) + circleRadius
                val cy = pillTop + pillPadY + circleRadius
                drawCircle(
                    color = if (isCurrent) activePillColor else PianoColors.SurfaceElevated,
                    radius = circleRadius,
                    center = Offset(cx, cy)
                )
                val label = if (isCurrent) beatActiveLayouts[b - 1] else beatInactiveLayouts[b - 1]
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(cx - label.size.width / 2f, cy - label.size.height / 2f)
                )
            }
        }
    }
}
