package dev.lofrancolabs.pianopiano.domain

import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.ExerciseSummary
import dev.lofrancolabs.pianopiano.model.NoteEvaluation
import dev.lofrancolabs.pianopiano.model.PitchVerdict
import dev.lofrancolabs.pianopiano.model.TimingVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTrackerTest {

    @Test
    fun levelThresholdsScaleAsExpected() {
        val lv1 = PlayerProgress(totalXp = 0)
        assertEquals(1, lv1.level)
        assertEquals(0, lv1.xpIntoCurrentLevel)
        assertEquals(100, lv1.xpNeededForNextLevel)

        val lv2 = PlayerProgress(totalXp = 100)
        assertEquals(2, lv2.level)
        assertEquals(0, lv2.xpIntoCurrentLevel)
        assertEquals(150, lv2.xpNeededForNextLevel)

        val lv3 = PlayerProgress(totalXp = 250)
        assertEquals(3, lv3.level)
        assertEquals(0, lv3.xpIntoCurrentLevel)
        assertEquals(200, lv3.xpNeededForNextLevel)
    }

    @Test
    fun awardsZeroXpWhenNoNoteAttemptedAndTriggersLevelUpWhenThresholdCrossed() {
        val unattemptedSummary = ExerciseSummary(
            exerciseId = "test",
            exerciseTitle = "Test",
            pitchAccuracyPercent = 0,
            timingAccuracyPercent = 0,
            durationAccuracyPercent = 0,
            averageOnsetOffsetMs = 0,
            totalNotes = 1,
            evaluations = listOf(
                NoteEvaluation(
                    targetIndex = 0,
                    noteEventIndex = 0,
                    expectedMidi = 60,
                    expectedStartMs = 0L,
                    expectedEndMs = 500L,
                    playedMidi = null,
                    timingVerdict = TimingVerdict.MISSED
                )
            )
        )
        val noXp = ProgressTracker.calculateAndApplyReward(
            current = PlayerProgress(totalXp = 90, exercisesCompleted = 2),
            summary = unattemptedSummary,
            difficulty = BeginnerDifficulty.BEGINNER
        )
        assertEquals(0, noXp.xpEarned)
        assertFalse(noXp.leveledUp)
        assertEquals(2, noXp.updatedProgress.exercisesCompleted)

        val goodSummary = ExerciseSummary(
            exerciseId = "test",
            exerciseTitle = "Test",
            pitchAccuracyPercent = 100,
            timingAccuracyPercent = 90,
            durationAccuracyPercent = 95,
            averageOnsetOffsetMs = 5,
            totalNotes = 1,
            evaluations = listOf(
                NoteEvaluation(
                    targetIndex = 0,
                    noteEventIndex = 0,
                    expectedMidi = 60,
                    expectedStartMs = 0L,
                    expectedEndMs = 500L,
                    playedMidi = 60,
                    pitchVerdict = PitchVerdict.CORRECT,
                    timingVerdict = TimingVerdict.PERFECT
                )
            )
        )
        // Advanced baseXp = 75, avgScore = 95 -> accuracyBonus = 38, excellenceBonus = 15 => 128 XP!
        val reward = ProgressTracker.calculateAndApplyReward(
            current = PlayerProgress(totalXp = 60, exercisesCompleted = 2),
            summary = goodSummary,
            difficulty = BeginnerDifficulty.ADVANCED
        )
        assertEquals(128, reward.xpEarned)
        assertTrue(reward.leveledUp)
        assertEquals(1, reward.previousLevel)
        assertEquals(2, reward.newLevel)
        assertEquals(188, reward.updatedProgress.totalXp)
        assertEquals(3, reward.updatedProgress.exercisesCompleted)
    }
}
