package dev.lofrancolabs.pianopiano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lofrancolabs.pianopiano.model.MidiUtils
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors

private data class WhiteKeySpec(val midi: Int)
private data class BlackKeySpec(val midi: Int, val leftWhiteIndex: Int)

@Composable
fun InteractivePianoKeyboard(
    expectedRightMidi: Int?,
    expectedLeftMidi: Int?,
    heardMidis: Set<Int>,
    previewActiveMidis: Set<Int>,
    isPlaying: Boolean,
    heldCorrectMidis: Set<Int>,
    heldWrongMidis: Set<Int>,
    noteNaming: NoteNamingSystem,
    showLeftOctaveLabels: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // 2 full octaves: C3 (48) to C5 (72)
    val whiteKeys = remember {
        val list = mutableListOf<WhiteKeySpec>()
        for (midi in 48..72) {
            if (!MidiUtils.isBlackKey(midi)) {
                list.add(WhiteKeySpec(midi))
            }
        }
        list.toList()
    }

    val blackKeys = remember {
        val list = mutableListOf<BlackKeySpec>()
        var whiteIdx = -1
        for (midi in 48..72) {
            if (!MidiUtils.isBlackKey(midi)) {
                whiteIdx++
            } else {
                list.add(BlackKeySpec(midi, leftWhiteIndex = whiteIdx))
            }
        }
        list.toList()
    }

    val keyLabelStyle = remember {
        TextStyle(color = Color(0xFF474E5D), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
    val compactKeyLabelStyle = remember {
        TextStyle(color = Color(0xFF474E5D), fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
    }
    val middleCLabelStyle = remember {
        TextStyle(color = PianoColors.TargetBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
    val compactMiddleCLabelStyle = remember {
        TextStyle(color = PianoColors.TargetBlue, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(PianoColors.Background)
            .border(1.dp, PianoColors.Border, RoundedCornerShape(14.dp))
    ) {
        val whiteWidth = size.width / whiteKeys.size.toFloat()
        val keyGap = 1.5.dp.toPx()
        val blackWidth = whiteWidth * 0.62f
        val blackHeight = size.height * 0.58f

        fun activeKeyColor(midi: Int): Color? {
            if (midi in previewActiveMidis) {
                return if (midi == expectedLeftMidi && midi != expectedRightMidi) {
                    PianoColors.LeftHandPurple
                } else {
                    PianoColors.TargetBlue
                }
            }
            if (midi in heardMidis) {
                return when {
                    !isPlaying -> PianoColors.WarmGold
                    midi in heldCorrectMidis -> PianoColors.Emerald
                    midi in heldWrongMidis -> PianoColors.Coral
                    expectedRightMidi == null && expectedLeftMidi == null -> PianoColors.WarmGold
                    midi == expectedRightMidi || midi == expectedLeftMidi -> PianoColors.Emerald
                    else -> PianoColors.Coral
                }
            }
            return null
        }

        // Draw White Keys
        whiteKeys.forEachIndexed { idx, key ->
            val left = idx * whiteWidth + keyGap / 2f
            val keyW = (whiteWidth - keyGap).coerceAtLeast(4f)
            val activeTint = activeKeyColor(key.midi)
            val isExpectedRight = key.midi == expectedRightMidi
            val isExpectedLeft = key.midi == expectedLeftMidi

            val fillColor = when {
                activeTint != null -> activeTint
                isExpectedRight -> Color(0xFFD7EBFF)
                isExpectedLeft -> Color(0xFFEDE0FF)
                else -> PianoColors.IvoryKey
            }

            drawRoundRect(
                color = fillColor,
                topLeft = Offset(left, 2.dp.toPx()),
                size = Size(keyW, size.height - 4.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            if ((isExpectedRight || isExpectedLeft) && activeTint == null) {
                val dotColor = if (isExpectedRight) PianoColors.TargetBlue else PianoColors.LeftHandPurple
                drawCircle(
                    color = dotColor,
                    radius = 4.dp.toPx(),
                    center = Offset(left + keyW / 2f, size.height - 32.dp.toPx())
                )
            }

            val showLabel = if (showLeftOctaveLabels) {
                key.midi in 48..55 || key.midi in 60..67 || key.midi == 72
            } else {
                key.midi == 48 || key.midi in 60..67 || key.midi == 72
            }
            if (showLabel) {
                val isMiddleC = key.midi == 60
                val isOctaveAnchor = key.midi == 48 || key.midi == 60 || key.midi == 72
                val fullText = MidiUtils.midiToName(key.midi, noteNaming, includeOctave = true)
                val regularStyle = if (isMiddleC) middleCLabelStyle else keyLabelStyle
                val compactStyle = if (isMiddleC) compactMiddleCLabelStyle else compactKeyLabelStyle

                var measured = textMeasurer.measure(fullText, regularStyle)
                if (measured.size.width > keyW - 2f) {
                    measured = if (isOctaveAnchor) {
                        textMeasurer.measure(fullText, compactStyle)
                    } else {
                        val shortText = MidiUtils.midiToName(key.midi, noteNaming, includeOctave = false)
                        val shortMeasured = textMeasurer.measure(shortText, regularStyle)
                        if (shortMeasured.size.width <= keyW - 2f) {
                            shortMeasured
                        } else {
                            textMeasurer.measure(shortText, compactStyle)
                        }
                    }
                }
                if (measured.size.width <= keyW - 1f) {
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            left + (keyW - measured.size.width) / 2f,
                            size.height - measured.size.height - 6.dp.toPx()
                        )
                    )
                }
            }
        }

        // Draw Black Keys
        blackKeys.forEach { bk ->
            val centerX = (bk.leftWhiteIndex + 1) * whiteWidth
            val left = centerX - blackWidth / 2f
            val activeTint = activeKeyColor(bk.midi)
            val isExpectedRight = bk.midi == expectedRightMidi
            val isExpectedLeft = bk.midi == expectedLeftMidi

            val fillColor = when {
                activeTint != null -> activeTint
                isExpectedRight -> PianoColors.TargetBlue
                isExpectedLeft -> PianoColors.LeftHandPurple
                else -> PianoColors.BlackKey
            }

            drawRoundRect(
                color = fillColor,
                topLeft = Offset(left, 2.dp.toPx()),
                size = Size(blackWidth, blackHeight),
                cornerRadius = CornerRadius(5.dp.toPx())
            )
            drawRoundRect(
                color = PianoColors.BlackKeyHighlight,
                topLeft = Offset(left + 2.dp.toPx(), 4.dp.toPx()),
                size = Size((blackWidth - 4.dp.toPx()).coerceAtLeast(2f), blackHeight * 0.78f),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            if (activeTint != null || isExpectedRight || isExpectedLeft) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(left + 1.dp.toPx(), 2.dp.toPx()),
                    size = Size((blackWidth - 2.dp.toPx()).coerceAtLeast(2f), blackHeight),
                    cornerRadius = CornerRadius(5.dp.toPx())
                )
            }
        }
    }
}
