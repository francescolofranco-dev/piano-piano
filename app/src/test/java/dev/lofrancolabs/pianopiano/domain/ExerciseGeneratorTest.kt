package dev.lofrancolabs.pianopiano.domain

import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExerciseGeneratorTest {

    @Test
    fun generatedDrillsStayWithinBeginnerScopesAndStrictFourFourBars() {
        val allowedBeginner = setOf(60, 62, 64)
        val allowedIntermediate = setOf(60, 62, 64, 65, 67)
        val allowedAdvanced = setOf(60, 62, 64, 65, 67, 69, 71, 72)

        for (seed in 1..25) {
            val rng = Random(seed)

            val beg = ExerciseGenerator.generate(BeginnerDifficulty.BEGINNER, seed, rng)
            assertTrue(beg.isGenerated)
            assertEquals(BeginnerDifficulty.BEGINNER, beg.difficulty)
            assertTrue(beg.defaultBpm in 68..74)
            assertTrue(beg.totalBeats == 8f || beg.totalBeats == 12f)
            assertBarAlignment(beg.notes.map { it.startBeat to it.durationBeats })
            val begPitches = beg.notes.mapNotNull { it.midiNote }
            assertTrue(begPitches.size >= 4)
            assertTrue(begPitches.all { it in allowedBeginner })

            val inter = ExerciseGenerator.generate(BeginnerDifficulty.INTERMEDIATE, seed, rng)
            assertTrue(inter.isGenerated)
            assertEquals(BeginnerDifficulty.INTERMEDIATE, inter.difficulty)
            assertTrue(inter.defaultBpm in 74..82)
            assertTrue(inter.totalBeats == 12f || inter.totalBeats == 16f)
            assertBarAlignment(inter.notes.map { it.startBeat to it.durationBeats })
            val interPitches = inter.notes.mapNotNull { it.midiNote }
            assertTrue(interPitches.size >= 6)
            assertTrue(interPitches.all { it in allowedIntermediate })

            val adv = ExerciseGenerator.generate(BeginnerDifficulty.ADVANCED, seed, rng)
            assertTrue(adv.isGenerated)
            assertEquals(BeginnerDifficulty.ADVANCED, adv.difficulty)
            assertTrue(adv.defaultBpm in 80..90)
            assertEquals(16f, adv.totalBeats)
            assertBarAlignment(adv.notes.map { it.startBeat to it.durationBeats })
            val advPitches = adv.notes.mapNotNull { it.midiNote }
            assertTrue(advPitches.size >= 8)
            assertTrue(advPitches.all { it in allowedAdvanced })
        }
    }

    private fun assertBarAlignment(spans: List<Pair<Float, Float>>) {
        var cursor = 0f
        for ((start, duration) in spans) {
            assertEquals(cursor, start, 0.001f)
            val startBar = (start / 4f).toInt()
            val endBar = ((start + duration - 0.001f) / 4f).toInt()
            assertEquals("Note should not cross barline: $start..${start + duration}", startBar, endBar)
            cursor += duration
        }
        assertEquals(0f, cursor % 4f, 0.001f)
    }
}
