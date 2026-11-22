package dev.lofrancolabs.pianopiano.domain

import dev.lofrancolabs.pianopiano.model.DurationVerdict
import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.ExerciseSummary
import dev.lofrancolabs.pianopiano.model.NoteEvaluation
import dev.lofrancolabs.pianopiano.model.PitchVerdict
import dev.lofrancolabs.pianopiano.model.TimingVerdict
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class ExerciseJudge(
    private val exercise: Exercise,
    bpm: Int,
    private val exerciseStartMs: Long,
    private val timingToleranceMs: Long = DEFAULT_TIMING_TOLERANCE_MS
) {
    private val msPerBeat: Double = 60_000.0 / bpm
    private val earlyWindowMs: Long = maxOf(220L, (0.45 * msPerBeat).roundToLong())
    private val lateGraceBaseMs: Long = maxOf(350L, (0.55 * msPerBeat).roundToLong())

    private val evaluations: MutableList<NoteEvaluation> = mutableListOf()

    init {
        var targetIndex = 0
        exercise.notes.forEachIndexed { eventIndex, note ->
            val midi = note.midiNote
            if (midi != null) {
                val startMs = exerciseStartMs + (note.startBeat * msPerBeat).roundToLong()
                val endMs = exerciseStartMs + ((note.startBeat + note.durationBeats) * msPerBeat).roundToLong()
                evaluations.add(
                    NoteEvaluation(
                        targetIndex = targetIndex++,
                        noteEventIndex = eventIndex,
                        expectedMidi = midi,
                        expectedStartMs = startMs,
                        expectedEndMs = endMs
                    )
                )
            }
        }
    }

    @Synchronized
    fun snapshot(): List<NoteEvaluation> = evaluations.toList()

    @Synchronized
    fun currentTargetMidi(nowMs: Long): Int? {
        val nextPending = evaluations.firstOrNull { it.timingVerdict == TimingVerdict.PENDING }
        if (nextPending != null && nowMs <= nextPending.expectedEndMs) {
            return nextPending.expectedMidi
        }
        return evaluations.lastOrNull { it.activeHoldStartMs != null && it.activeHoldEndMs == null }?.expectedMidi
    }

    @Synchronized
    fun onNoteOn(midi: Int, timestampMs: Long) {
        // Close any still-held previous note when a new note starts
        for (i in evaluations.indices) {
            val active = evaluations[i]
            if (active.activeHoldStartMs != null && active.activeHoldEndMs == null) {
                evaluations[i] = closeHold(active, timestampMs)
            }
        }

        // Find the closest pending target note whose window contains timestampMs
        var bestIndex = -1
        var bestAbsDelta = Long.MAX_VALUE
        for (i in evaluations.indices) {
            val eval = evaluations[i]
            if (eval.timingVerdict != TimingVerdict.PENDING) continue
            val windowStart = eval.expectedStartMs - earlyWindowMs
            val windowEnd = eval.expectedEndMs
            if (timestampMs in windowStart..windowEnd) {
                val dist = abs(timestampMs - eval.expectedStartMs)
                if (dist < bestAbsDelta) {
                    bestAbsDelta = dist
                    bestIndex = i
                }
            }
        }

        if (bestIndex == -1) return

        // Any earlier pending targets that were skipped become MISSED
        for (i in 0 until bestIndex) {
            val earlier = evaluations[i]
            if (earlier.timingVerdict == TimingVerdict.PENDING) {
                evaluations[i] = earlier.copy(timingVerdict = TimingVerdict.MISSED)
            }
        }

        val target = evaluations[bestIndex]
        val deltaMs = timestampMs - target.expectedStartMs
        val pitchVerdict = if (midi == target.expectedMidi) PitchVerdict.CORRECT else PitchVerdict.WRONG_KEY
        val timingVerdict = when {
            abs(deltaMs) <= timingToleranceMs -> TimingVerdict.PERFECT
            deltaMs < -timingToleranceMs -> TimingVerdict.TOO_SOON
            else -> TimingVerdict.TOO_LATE
        }

        evaluations[bestIndex] = target.copy(
            playedMidi = midi,
            onsetDeltaMs = deltaMs,
            activeHoldStartMs = timestampMs,
            pitchVerdict = pitchVerdict,
            timingVerdict = timingVerdict
        )
    }

    @Synchronized
    fun onNoteOff(midi: Int, timestampMs: Long) {
        val activeIndex = evaluations.indexOfLast {
            it.activeHoldStartMs != null && it.activeHoldEndMs == null && it.playedMidi == midi
        }
        if (activeIndex == -1) return
        evaluations[activeIndex] = closeHold(evaluations[activeIndex], timestampMs)
    }

    @Synchronized
    fun tick(nowMs: Long) {
        for (i in evaluations.indices) {
            val eval = evaluations[i]
            if (eval.timingVerdict == TimingVerdict.PENDING) {
                val missCutoff = eval.expectedStartMs + minOf(
                    (eval.expectedDurationMs * 0.85).roundToLong(),
                    lateGraceBaseMs
                )
                if (nowMs > missCutoff) {
                    evaluations[i] = eval.copy(timingVerdict = TimingVerdict.MISSED)
                }
            } else if (eval.activeHoldStartMs != null && eval.activeHoldEndMs == null) {
                val heldSoFar = (nowMs - eval.activeHoldStartMs).coerceAtLeast(0L)
                val maxAllowed = (eval.expectedDurationMs * MAX_DURATION_RATIO).roundToLong()
                if (heldSoFar > maxAllowed && eval.durationVerdict != DurationVerdict.TOO_LONG) {
                    evaluations[i] = eval.copy(durationVerdict = DurationVerdict.TOO_LONG)
                }
            }
        }
    }

    @Synchronized
    fun finish(nowMs: Long): ExerciseSummary {
        for (i in evaluations.indices) {
            val eval = evaluations[i]
            if (eval.timingVerdict == TimingVerdict.PENDING) {
                evaluations[i] = eval.copy(timingVerdict = TimingVerdict.MISSED)
            } else if (eval.activeHoldStartMs != null && eval.activeHoldEndMs == null) {
                evaluations[i] = closeHold(eval, nowMs)
            }
        }

        val total = evaluations.size.coerceAtLeast(1)
        val pitchCorrect = evaluations.count { it.pitchVerdict == PitchVerdict.CORRECT }
        val timingPerfect = evaluations.count { it.timingVerdict == TimingVerdict.PERFECT }
        val durationGood = evaluations.count { it.durationVerdict == DurationVerdict.GOOD }
        val onsetDeltas = evaluations.mapNotNull { it.onsetDeltaMs }
        val meanOnset = if (onsetDeltas.isNotEmpty()) onsetDeltas.average().roundToInt() else 0

        return ExerciseSummary(
            exerciseId = exercise.id,
            exerciseTitle = exercise.title,
            pitchAccuracyPercent = (pitchCorrect * 100.0 / total).roundToInt(),
            timingAccuracyPercent = (timingPerfect * 100.0 / total).roundToInt(),
            durationAccuracyPercent = (durationGood * 100.0 / total).roundToInt(),
            averageOnsetOffsetMs = meanOnset,
            totalNotes = evaluations.size,
            evaluations = evaluations.toList()
        )
    }

    private fun closeHold(eval: NoteEvaluation, releaseMs: Long): NoteEvaluation {
        val holdStart = eval.activeHoldStartMs ?: return eval
        val clampedEnd = releaseMs.coerceAtLeast(holdStart + 1L)
        val durationMs = clampedEnd - holdStart
        val ratio = durationMs.toDouble() / eval.expectedDurationMs.toDouble()
        val verdict = when {
            ratio < MIN_DURATION_RATIO -> DurationVerdict.TOO_SHORT
            ratio > MAX_DURATION_RATIO -> DurationVerdict.TOO_LONG
            else -> DurationVerdict.GOOD
        }
        return eval.copy(
            activeHoldEndMs = clampedEnd,
            actualDurationMs = durationMs,
            durationVerdict = verdict
        )
    }

    companion object {
        const val DEFAULT_TIMING_TOLERANCE_MS = 65L
        const val MIN_DURATION_RATIO = 0.70
        const val MAX_DURATION_RATIO = 1.30
    }
}
