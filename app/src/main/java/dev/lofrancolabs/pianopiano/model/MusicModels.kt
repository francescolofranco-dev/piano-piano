package dev.lofrancolabs.pianopiano.model

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object MidiUtils {
    private val noteNames = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )
    private val diatonicInOctave = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)

    fun midiToName(midi: Int): String {
        val semitone = ((midi % 12) + 12) % 12
        val octave = Math.floorDiv(midi, 12) - 1
        return "${noteNames[semitone]}$octave"
    }

    fun midiToFrequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun frequencyToMidi(freqHz: Double): Int {
        require(freqHz > 0.0) { "Frequency must be positive: $freqHz" }
        return (69.0 + 12.0 * ln(freqHz / 440.0) / ln(2.0)).roundToInt()
    }

    fun isBlackKey(midi: Int): Boolean {
        return when (((midi % 12) + 12) % 12) {
            1, 3, 6, 8, 10 -> true
            else -> false
        }
    }

    /**
     * Diatonic step relative to E4 (bottom line of treble clef = 0).
     * C4 (Middle C) = -2, D4 = -1, E4 = 0, G4 = 2, C5 = 5, G5 = 9.
     */
    fun trebleStaffStepFromE4(midi: Int): Int {
        val octave = Math.floorDiv(midi, 12) - 1
        val semitone = ((midi % 12) + 12) % 12
        val absoluteDiatonic = octave * 7 + diatonicInOctave[semitone]
        return absoluteDiatonic - 30 // 4 * 7 + 2 (E4)
    }
}

data class NoteEvent(
    val midiNote: Int?, // null represents a musical rest
    val startBeat: Float,
    val durationBeats: Float,
    val fingerHint: Int? = null
) {
    val isRest: Boolean get() = midiNote == null
}

data class Exercise(
    val id: String,
    val title: String,
    val subtitle: String,
    val focusTag: String,
    val defaultBpm: Int,
    val beatsPerBar: Int = 4,
    val notes: List<NoteEvent>
) {
    val totalBeats: Float
        get() = notes.maxOfOrNull { it.startBeat + it.durationBeats } ?: 0f
}

enum class InputSource {
    MICROPHONE,
    TOUCH_PIANO
}

enum class SessionPhase {
    IDLE,
    CALIBRATING,
    PREVIEWING,
    COUNT_IN,
    PLAYING,
    COMPLETED
}

enum class PitchVerdict {
    PENDING,
    CORRECT,
    WRONG_KEY
}

enum class TimingVerdict {
    PENDING,
    PERFECT,
    TOO_SOON,
    TOO_LATE,
    MISSED
}

enum class DurationVerdict {
    PENDING,
    GOOD,
    TOO_SHORT,
    TOO_LONG
}

data class NoteEvaluation(
    val targetIndex: Int,
    val noteEventIndex: Int,
    val expectedMidi: Int,
    val expectedStartMs: Long,
    val expectedEndMs: Long,
    val playedMidi: Int? = null,
    val onsetDeltaMs: Long? = null,
    val actualDurationMs: Long? = null,
    val activeHoldStartMs: Long? = null,
    val activeHoldEndMs: Long? = null,
    val pitchVerdict: PitchVerdict = PitchVerdict.PENDING,
    val timingVerdict: TimingVerdict = TimingVerdict.PENDING,
    val durationVerdict: DurationVerdict = DurationVerdict.PENDING
) {
    val expectedDurationMs: Long get() = (expectedEndMs - expectedStartMs).coerceAtLeast(1L)

    val isEvaluated: Boolean
        get() = timingVerdict != TimingVerdict.PENDING || pitchVerdict != PitchVerdict.PENDING

    fun feedbackBadgeText(): String? {
        if (timingVerdict == TimingVerdict.MISSED) return "Missed"
        if (pitchVerdict == PitchVerdict.WRONG_KEY && playedMidi != null) {
            return "Wrong: ${MidiUtils.midiToName(playedMidi)}"
        }
        if (timingVerdict == TimingVerdict.TOO_SOON && onsetDeltaMs != null) {
            return "Too soon! (${onsetDeltaMs}ms)"
        }
        if (timingVerdict == TimingVerdict.TOO_LATE && onsetDeltaMs != null) {
            return "Too late! (+${onsetDeltaMs}ms)"
        }
        if (durationVerdict == DurationVerdict.TOO_SHORT) return "Too short!"
        if (durationVerdict == DurationVerdict.TOO_LONG) return "Too long!"
        if (timingVerdict == TimingVerdict.PERFECT &&
            (durationVerdict == DurationVerdict.GOOD || durationVerdict == DurationVerdict.PENDING)
        ) {
            return "Perfect!"
        }
        return null
    }
}

data class ExerciseSummary(
    val exerciseId: String,
    val exerciseTitle: String,
    val pitchAccuracyPercent: Int,
    val timingAccuracyPercent: Int,
    val durationAccuracyPercent: Int,
    val averageOnsetOffsetMs: Int,
    val totalNotes: Int,
    val evaluations: List<NoteEvaluation>
)
