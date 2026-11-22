package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MidiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class YinPitchDetectorTest {

    private val sampleRate = 48_000
    private val detector = YinPitchDetector(sampleRate = sampleRate, windowSize = 2048)

    @Test
    fun detectsPianoHarmonicWaveformsAcrossC3ToC5() {
        val testMidis = listOf(48, 60, 62, 64, 65, 67, 69, 72)
        for (midi in testMidis) {
            val f0 = MidiUtils.midiToFrequency(midi)
            val buffer = FloatArray(detector.windowSize) { idx ->
                val t = idx.toDouble() / sampleRate
                val env = exp(-t * 2.5)
                val harmonic1 = 1.0 * sin(2.0 * PI * f0 * t)
                val harmonic2 = 0.45 * sin(2.0 * PI * f0 * 2.0 * t)
                val harmonic3 = 0.22 * sin(2.0 * PI * f0 * 3.0 * t)
                val harmonic4 = 0.10 * sin(2.0 * PI * f0 * 4.0 * t)
                (0.25 * env * (harmonic1 + harmonic2 + harmonic3 + harmonic4)).toFloat()
            }

            val result = detector.detect(buffer)
            assertNotNull("Expected pitch for MIDI $midi", result)
            assertEquals("Expected MIDI $midi", midi, result!!.midiNote)
            assertTrue("Expected high confidence for MIDI $midi, got ${result.confidence}", result.confidence > 0.7f)
        }
    }

    @Test
    fun silenceReturnsNull() {
        val silence = FloatArray(detector.windowSize) { 0f }
        assertNull(detector.detect(silence))
    }
}
