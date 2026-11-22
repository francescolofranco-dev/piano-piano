package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MidiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class OnsetEnvelopeTrackerTest {

    private data class CapturedEvent(val isOn: Boolean, val midi: Int, val timestampMs: Long)

    @Test
    fun detectsHammerTransientPitchLockAndRelease() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )

        val hop = FloatArray(256)
        var sampleIndex = 0L

        // Feed 15 hops of silence (~80 ms)
        repeat(15) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // Feed C4 (60) for 45 hops (~240 ms) with an initial noisy thump on hop 0
        val f0 = MidiUtils.midiToFrequency(60)
        val noteStartMs = currentMs(sampleIndex)
        for (h in 0 until 45) {
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                val thump = if (h == 0) 0.12 * sin(2.0 * PI * 90.0 * t) else 0.0
                val tone = 0.25 * exp(-t * 2.0) * (sin(2.0 * PI * f0 * t) + 0.35 * sin(2.0 * PI * f0 * 2.0 * t))
                hop[i] = (tone + thump).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // Feed 15 hops of silence for damper release
        repeat(15) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onEvents = events.filter { it.isOn }
        val offEvents = events.filter { !it.isOn }
        assertEquals(1, onEvents.size)
        assertEquals(60, onEvents[0].midi)
        assertTrue("Onset timestamp should match strike time", kotlin.math.abs(onEvents[0].timestampMs - noteStartMs) <= 10L)
        assertEquals(1, offEvents.size)
        assertEquals(60, offEvents[0].midi)
        assertTrue(offEvents[0].timestampMs > onEvents[0].timestampMs + 180L)
    }

    @Test
    fun detectsReattackOnRepeatedKeyWhileTailIsDecaying() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )

        val hop = FloatArray(256)
        var sampleIndex = 0L

        repeat(10) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // Strike E4 (64) and let it decay down to low sustain
        val fE4 = MidiUtils.midiToFrequency(64)
        for (h in 0 until 40) {
            val amp = if (h < 12) 0.28 else 0.035
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (amp * sin(2.0 * PI * fE4 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // Strike G4 (67) strongly while previous tail was 0.035
        val fG4 = MidiUtils.midiToFrequency(67)
        for (h in 0 until 25) {
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (0.30 * sin(2.0 * PI * fG4 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onEvents = events.filter { it.isOn }
        assertEquals(2, onEvents.size)
        assertEquals(64, onEvents[0].midi)
        assertEquals(67, onEvents[1].midi)
    }

    private fun currentMs(samples: Long): Long = (samples * 1000L) / 48_000L
}
