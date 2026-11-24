package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MidiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PolyphonicChordDetectorTest {

    private val sampleRate = 48_000
    private val detector = PolyphonicChordDetector(sampleRate = sampleRate)

    private fun synthPianoNote(midi: Int, t: Double, amp: Double): Double {
        val f0 = MidiUtils.midiToFrequency(midi)
        return amp * (
            1.0 * sin(2.0 * PI * f0 * t) +
                0.42 * sin(2.0 * PI * f0 * 2.0 * t) +
                0.20 * sin(2.0 * PI * f0 * 3.0 * t)
            )
    }

    @Test
    fun detectsSingleNoteWithoutPhantomOctaveGhosts() {
        val buffer = FloatArray(detector.windowSize) { idx ->
            val t = idx.toDouble() / sampleRate
            synthPianoNote(60, t, 0.20).toFloat()
        }
        val detected = detector.detectChords(buffer, maxNotes = 2)
        assertEquals(1, detected.size)
        assertEquals(60, detected[0].midiNote)
    }

    @Test
    fun detectsSimultaneousThirdsFifthsAndOctaveDyads() {
        val dyads = listOf(
            48 to 64, // C3 + E4
            53 to 69, // F3 + A4
            55 to 62, // G3 + D4
            48 to 60  // C3 + C4 octave dyad
        )
        for ((lowMidi, highMidi) in dyads) {
            val buffer = FloatArray(detector.windowSize) { idx ->
                val t = idx.toDouble() / sampleRate
                (synthPianoNote(lowMidi, t, 0.14) + synthPianoNote(highMidi, t, 0.14)).toFloat()
            }
            val detected = detector.detectChords(buffer, maxNotes = 2)
            assertEquals(
                "Expected dyad ($lowMidi, $highMidi), got ${detected.map { it.midiNote }}",
                listOf(lowMidi, highMidi),
                detected.map { it.midiNote }
            )
        }
    }

    @Test
    fun silenceReturnsEmptyList() {
        val silence = FloatArray(detector.windowSize)
        assertTrue(detector.detectChords(silence).isEmpty())
    }
}
