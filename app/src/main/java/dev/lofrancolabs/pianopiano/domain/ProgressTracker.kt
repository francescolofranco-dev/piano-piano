package dev.lofrancolabs.pianopiano.domain

import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.ExerciseSummary
import kotlin.math.roundToInt

data class PlayerProgress(
    val totalXp: Int = 0,
    val exercisesCompleted: Int = 0
) {
    val level: Int
        get() = computeLevelBreakdown(totalXp).level

    val xpIntoCurrentLevel: Int
        get() = computeLevelBreakdown(totalXp).xpIntoLevel

    val xpNeededForNextLevel: Int
        get() = computeLevelBreakdown(totalXp).xpNeeded

    val levelProgressFraction: Float
        get() = (xpIntoCurrentLevel.toFloat() / xpNeededForNextLevel.coerceAtLeast(1)).coerceIn(0f, 1f)

    val rankTitle: String
        get() = when (level) {
            1, 2 -> "First Keys"
            3, 4 -> "Steady Pulse"
            in 5..7 -> "C-Position Explorer"
            in 8..10 -> "Rhythm Keeper"
            else -> "Piano Piano Apprentice"
        }

    private data class LevelBreakdown(val level: Int, val xpIntoLevel: Int, val xpNeeded: Int)

    private fun computeLevelBreakdown(xp: Int): LevelBreakdown {
        var remaining = xp.coerceAtLeast(0)
        var lvl = 1
        while (true) {
            val needed = 100 + (lvl - 1) * 50
            if (remaining < needed) {
                return LevelBreakdown(lvl, remaining, needed)
            }
            remaining -= needed
            lvl++
        }
    }
}

data class XpReward(
    val xpEarned: Int,
    val previousLevel: Int,
    val newLevel: Int,
    val leveledUp: Boolean,
    val updatedProgress: PlayerProgress
)

object ProgressTracker {

    fun calculateAndApplyReward(
        current: PlayerProgress,
        summary: ExerciseSummary,
        difficulty: BeginnerDifficulty
    ): XpReward {
        val attemptedAnyNote = summary.evaluations.any { it.playedMidi != null }
        if (!attemptedAnyNote) {
            return XpReward(
                xpEarned = 0,
                previousLevel = current.level,
                newLevel = current.level,
                leveledUp = false,
                updatedProgress = current
            )
        }

        val avgScore = (
            summary.pitchAccuracyPercent +
                summary.timingAccuracyPercent +
                summary.durationAccuracyPercent
            ) / 3.0
        val accuracyBonus = (avgScore * 0.40).roundToInt()
        val excellenceBonus = if (avgScore >= 90.0) 15 else 0
        val xpEarned = maxOf(15, difficulty.baseXp + accuracyBonus + excellenceBonus)

        val updated = PlayerProgress(
            totalXp = current.totalXp + xpEarned,
            exercisesCompleted = current.exercisesCompleted + 1
        )
        val prevLevel = current.level
        val newLevel = updated.level
        return XpReward(
            xpEarned = xpEarned,
            previousLevel = prevLevel,
            newLevel = newLevel,
            leveledUp = newLevel > prevLevel,
            updatedProgress = updated
        )
    }
}
