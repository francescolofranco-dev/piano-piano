package dev.lofrancolabs.pianopiano.domain

import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.HandMode
import dev.lofrancolabs.pianopiano.model.PianoHand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

            val beg = ExerciseGenerator.generate(
                difficulty = BeginnerDifficulty.BEGINNER,
                sequenceNumber = seed,
                random = rng
            )
            assertEquals(BeginnerDifficulty.BEGINNER, beg.difficulty)
            assertTrue(beg.defaultBpm in 56..62)
            assertTrue("Expected 6–8 bars (24, 28, or 32 beats), got ${beg.totalBeats}", beg.totalBeats in listOf(24f, 28f, 32f))
            assertBarAlignment(beg.notes.map { it.startBeat to it.durationBeats })
            val begPitches = beg.notes.mapNotNull { it.midiNote }
            assertTrue(begPitches.size >= 12)
            assertTrue(begPitches.all { it in allowedBeginner })

            val inter = ExerciseGenerator.generate(
                difficulty = BeginnerDifficulty.INTERMEDIATE,
                sequenceNumber = seed,
                random = rng
            )
            assertEquals(BeginnerDifficulty.INTERMEDIATE, inter.difficulty)
            assertTrue(inter.defaultBpm in 64..70)
            assertTrue("Expected 10–12 bars (40, 44, or 48 beats), got ${inter.totalBeats}", inter.totalBeats in listOf(40f, 44f, 48f))
            assertBarAlignment(inter.notes.map { it.startBeat to it.durationBeats })
            val interPitches = inter.notes.mapNotNull { it.midiNote }
            assertTrue(interPitches.size >= 20)
            assertTrue(interPitches.all { it in allowedIntermediate })

            val adv = ExerciseGenerator.generate(
                difficulty = BeginnerDifficulty.ADVANCED,
                sequenceNumber = seed,
                random = rng
            )
            assertEquals(BeginnerDifficulty.ADVANCED, adv.difficulty)
            assertTrue(adv.defaultBpm in 72..78)
            assertTrue("Expected 16–20 bars (64..80 beats), got ${adv.totalBeats}", adv.totalBeats in listOf(64f, 68f, 72f, 76f, 80f))
            assertBarAlignment(adv.notes.map { it.startBeat to it.durationBeats })
            val advPitches = adv.notes.mapNotNull { it.midiNote }
            assertTrue(advPitches.size >= 36)
            assertTrue(advPitches.all { it in allowedAdvanced })
        }
    }

    @Test
    fun generatesLeftHandAlternatingAndSimultaneousTwoHandDrills() {
        val leftBeginner = setOf(48, 50, 52)
        val leftIntermediate = setOf(48, 50, 52, 53, 55)
        val leftAdvanced = setOf(48, 50, 52, 53, 55, 57, 59, 60)

        for (seed in 1..10) {
            val rng = Random(seed + 100)

            // Left Hand solo across difficulties
            val lhBeg = ExerciseGenerator.generate(BeginnerDifficulty.BEGINNER, HandMode.LEFT_HAND, seed, rng)
            assertEquals(HandMode.LEFT_HAND, lhBeg.handMode)
            assertTrue(lhBeg.usesLeftHand && !lhBeg.usesRightHand)
            assertFalse(lhBeg.usesGrandStaff)
            assertBarAlignment(lhBeg.notes.map { it.startBeat to it.durationBeats })
            assertTrue(lhBeg.notes.all { it.hand == PianoHand.LEFT })
            assertTrue(lhBeg.notes.mapNotNull { it.midiNote }.all { it in leftBeginner })

            val lhInter = ExerciseGenerator.generate(BeginnerDifficulty.INTERMEDIATE, HandMode.LEFT_HAND, seed, rng)
            assertTrue(lhInter.notes.mapNotNull { it.midiNote }.all { it in leftIntermediate })

            // Alternating Two Hands
            val alt = ExerciseGenerator.generate(BeginnerDifficulty.ADVANCED, HandMode.BOTH_ALTERNATING, seed, rng)
            assertTrue(alt.usesGrandStaff)
            assertFalse(alt.hasSimultaneousNotes)
            assertBarAlignment(alt.notes.map { it.startBeat to it.durationBeats })
            assertTrue(alt.notes.any { it.hand == PianoHand.RIGHT && !it.isRest })
            assertTrue(alt.notes.any { it.hand == PianoHand.LEFT && !it.isRest })

            // Simultaneous Two Hands Together
            val tog = ExerciseGenerator.generate(BeginnerDifficulty.ADVANCED, HandMode.BOTH_TOGETHER, seed, rng)
            assertTrue(tog.usesGrandStaff)
            assertTrue(tog.hasSimultaneousNotes)
            val rhNotes = tog.notes.filter { it.hand == PianoHand.RIGHT }
            val lhNotes = tog.notes.filter { it.hand == PianoHand.LEFT }
            assertBarAlignment(rhNotes.map { it.startBeat to it.durationBeats })
            assertBarAlignment(lhNotes.map { it.startBeat to it.durationBeats })
            assertTrue(lhNotes.mapNotNull { it.midiNote }.all { it in leftAdvanced })
            // Verify simultaneous RH & LH notes avoid harsh minor-2nd / major-7th clashes
            for (lh in lhNotes) {
                val lhMidi = lh.midiNote ?: continue
                val lhEnd = lh.startBeat + lh.durationBeats
                for (rh in rhNotes) {
                    val rhMidi = rh.midiNote ?: continue
                    val rhEnd = rh.startBeat + rh.durationBeats
                    if (lh.startBeat < rhEnd - 0.001f && rh.startBeat < lhEnd - 0.001f) {
                        val iv = ((rhMidi - lhMidi) % 12 + 12) % 12
                        assertFalse("Avoid dissonant clash between LH $lhMidi and RH $rhMidi", iv == 1 || iv == 11)
                    }
                }
            }
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
