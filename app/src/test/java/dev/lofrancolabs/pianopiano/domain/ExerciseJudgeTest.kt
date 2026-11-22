package dev.lofrancolabs.pianopiano.domain

import dev.lofrancolabs.pianopiano.model.DurationVerdict
import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.NoteEvent
import dev.lofrancolabs.pianopiano.model.PitchVerdict
import dev.lofrancolabs.pianopiano.model.TimingVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseJudgeTest {

    // 60 BPM -> 1 beat = 1000 ms
    private val sampleExercise = Exercise(
        id = "test",
        title = "Test Drill",
        subtitle = "Unit test",
        focusTag = "Test",
        defaultBpm = 60,
        notes = listOf(
            NoteEvent(60, 0f, 1f), // expected 1000..2000
            NoteEvent(62, 1f, 1f), // expected 2000..3000
            NoteEvent(64, 2f, 1f), // expected 3000..4000
            NoteEvent(65, 3f, 1f), // expected 4000..5000
            NoteEvent(67, 4f, 1f)  // expected 5000..6000
        )
    )

    @Test
    fun evaluatesPitchTimingDurationAndSummaryAccurately() {
        val startMs = 1000L
        val judge = ExerciseJudge(sampleExercise, bpm = 60, exerciseStartMs = startMs)

        // Note 0 (C4 at 1000..2000): Perfect (+20ms) and Good Hold (950ms)
        judge.onNoteOn(60, 1020L)
        judge.onNoteOff(60, 1970L)

        // Note 1 (D4 at 2000..3000): Too soon (-110ms) and Too short (450ms)
        judge.onNoteOn(62, 1890L)
        judge.onNoteOff(62, 2340L)

        // Note 2 (E4 at 3000..4000): Wrong key (F4=65) and Too late (+130ms)
        judge.onNoteOn(65, 3130L)
        judge.onNoteOff(65, 4050L)

        // Note 3 (F4 at 4000..5000): Missed (skip past window via tick)
        judge.tick(4700L)

        // Note 4 (G4 at 5000..6000): Perfect timing, held Too long (1600ms), live flag via tick
        judge.onNoteOn(67, 5000L)
        judge.tick(6400L)
        assertEquals(DurationVerdict.TOO_LONG, judge.snapshot()[4].durationVerdict)
        judge.onNoteOff(67, 6600L)

        val summary = judge.finish(6700L)
        val evals = summary.evaluations

        assertEquals(PitchVerdict.CORRECT, evals[0].pitchVerdict)
        assertEquals(TimingVerdict.PERFECT, evals[0].timingVerdict)
        assertEquals(DurationVerdict.GOOD, evals[0].durationVerdict)
        assertEquals("Perfect!", evals[0].feedbackBadgeText())

        assertEquals(TimingVerdict.TOO_SOON, evals[1].timingVerdict)
        assertEquals(DurationVerdict.TOO_SHORT, evals[1].durationVerdict)
        assertTrue(evals[1].feedbackBadgeText()!!.startsWith("Too soon!"))

        assertEquals(PitchVerdict.WRONG_KEY, evals[2].pitchVerdict)
        assertEquals(TimingVerdict.TOO_LATE, evals[2].timingVerdict)
        assertEquals("Wrong: F4", evals[2].feedbackBadgeText())

        assertEquals(TimingVerdict.MISSED, evals[3].timingVerdict)
        assertEquals("Missed", evals[3].feedbackBadgeText())

        assertEquals(TimingVerdict.PERFECT, evals[4].timingVerdict)
        assertEquals(DurationVerdict.TOO_LONG, evals[4].durationVerdict)
        assertEquals("Too long!", evals[4].feedbackBadgeText())

        // 3 out of 5 correct pitch (0, 1, 4) -> 60%
        assertEquals(60, summary.pitchAccuracyPercent)
        // 2 out of 5 perfect timing (0, 4) -> 40%
        assertEquals(40, summary.timingAccuracyPercent)
        // 2 out of 5 good duration (0, 2) -> 40%
        assertEquals(40, summary.durationAccuracyPercent)
        // Mean onset over 4 played notes: (20 - 110 + 130 + 0) / 4 = 10ms
        assertEquals(10, summary.averageOnsetOffsetMs)
    }

    @Test
    fun acceptsLateStrikeOnHalfNote() {
        val halfNoteExercise = Exercise(
            id = "half",
            title = "Half Note Drill",
            subtitle = "Unit test",
            focusTag = "Test",
            defaultBpm = 68,
            notes = listOf(
                NoteEvent(60, 0f, 2f)
            )
        )
        val startMs = 1000L
        val judge = ExerciseJudge(halfNoteExercise, bpm = 68, exerciseStartMs = startMs)

        // +640ms past expected start on a 1765ms half note should still be pending (not missed)
        judge.tick(startMs + 640L)
        judge.onNoteOn(60, startMs + 650L)
        judge.onNoteOff(60, startMs + 1900L)

        val eval = judge.finish(startMs + 2200L).evaluations[0]
        assertEquals(PitchVerdict.CORRECT, eval.pitchVerdict)
        assertEquals(TimingVerdict.TOO_LATE, eval.timingVerdict)
    }
}
