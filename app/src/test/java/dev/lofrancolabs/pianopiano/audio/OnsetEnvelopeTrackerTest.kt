package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MicSensitivity
import dev.lofrancolabs.pianopiano.model.MidiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun detectsSmoothMultiHopAcousticAttack() {
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

        // Gradual acoustic swell where RMS crosses offsetThreshold before slowly crossing onsetThreshold
        val fG4 = MidiUtils.midiToFrequency(67)
        val attackAmps = floatArrayOf(0.005f, 0.007f, 0.009f, 0.011f, 0.012f)
        for (h in 0 until 15) {
            val amp = if (h < attackAmps.size) attackAmps[h] else 0.012f
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (amp * sin(2.0 * PI * fG4 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onEvents = events.filter { it.isOn }
        assertEquals(1, onEvents.size)
        assertEquals(67, onEvents[0].midi)
    }

    @Test
    fun updatesLivePitchContinuously() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )
        tracker.setSensitivity(MicSensitivity.LOW)

        val hop = FloatArray(256)
        var sampleIndex = 0L
        val fD4 = MidiUtils.midiToFrequency(62)

        // RMS ~0.005 is above liveGate (0.00351) but below LOW onsetThreshold (0.0108)
        for (h in 0 until 16) {
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (0.007 * sin(2.0 * PI * fD4 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        assertTrue("Gentle sustain below LOW onset threshold should not trigger strike", events.isEmpty())
        assertNotNull("Expected live pitch estimate while sustained note sounds", tracker.currentPitchEstimate)
        assertEquals(62, tracker.currentPitchEstimate!!.midiNote)

        repeat(8) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }
        assertNull("Expected live pitch to clear after silence", tracker.currentPitchEstimate)
    }

    @Test
    fun detectsSmoothRepeatedKeyReattackOverReverbTail() {
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

        val fC4 = MidiUtils.midiToFrequency(60)
        // Initial C4 strike decaying to steady reverb tail (amp = 0.020)
        for (h in 0 until 24) {
            val amp = if (h < 10) 0.20 else 0.020
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (amp * sin(2.0 * PI * fC4 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // Gradual multi-hop re-strike of same C4 key (each hop ~1.3x, cumulative >1.65x over tail)
        val reattackAmps = floatArrayOf(0.025f, 0.032f, 0.042f, 0.055f)
        for (h in 0 until 16) {
            val amp = if (h < reattackAmps.size) reattackAmps[h] else 0.055f
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (amp * sin(2.0 * PI * fC4 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onEvents = events.filter { it.isOn }
        assertEquals(2, onEvents.size)
        assertEquals(60, onEvents[0].midi)
        assertEquals(60, onEvents[1].midi)
    }

    @Test
    fun recoversFromUnpitchedNoiseRejectWhenPitchEmerges() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )

        val hop = FloatArray(256)
        var sampleIndex = 0L

        repeat(8) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // 22 hops of unpitched white noise (~0.026 RMS) exceeds MAX_SETTLE_HOPS and triggers lockout
        var seed = 1234567
        repeat(22) {
            for (i in 0 until 256) {
                seed = seed * 1664525 + 1013904223
                val normalized = ((seed ushr 16) and 0xFFFF) / 32768.0 - 1.0
                hop[i] = (0.045 * normalized).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // Immediately transition into steady C4 at equal RMS (~0.026) without dropping to silence
        val fC4 = MidiUtils.midiToFrequency(60)
        for (h in 0 until 24) {
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (0.0368 * sin(2.0 * PI * fC4 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onEvents = events.filter { it.isOn }
        assertEquals(1, onEvents.size)
        assertEquals(60, onEvents[0].midi)
    }

    @Test
    fun ignoresTransientWrongNoteGlitchDuringSingleHeldNote() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )

        val hop = FloatArray(256)
        var sampleIndex = 0L
        repeat(8) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val fC4 = MidiUtils.midiToFrequency(60)
        val fG5 = MidiUtils.midiToFrequency(79)
        for (h in 0 until 45) {
            val isGlitch = h in 22..23
            val f = if (isGlitch) fG5 else fC4
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (0.20 * sin(2.0 * PI * f * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onEvents = events.filter { it.isOn }
        assertEquals(1, onEvents.size)
        assertEquals(60, onEvents[0].midi)
        assertNotNull(tracker.currentPitchEstimate)
        assertEquals(60, tracker.currentPitchEstimate!!.midiNote)
    }

    @Test
    fun ignoresRapidPitchGlideLikeSpeech() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )

        val hop = FloatArray(256)
        var sampleIndex = 0L
        repeat(8) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // Rapid continuous glide from 150 Hz -> 320 Hz across 32 hops (~170 ms) like vocal inflection
        var phase = 0.0
        val totalSamples = 32 * 256
        for (h in 0 until 32) {
            for (i in 0 until 256) {
                val progress = (h * 256 + i).toDouble() / totalSamples.toDouble()
                val freq = 150.0 + progress * 170.0
                phase += (2.0 * PI * freq) / 48_000.0
                hop[i] = (0.08 * sin(phase)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onEvents = events.filter { it.isOn }
        assertEquals("Rapid pitch glide should not generate spurious strike trains", 0, onEvents.size)
    }

    @Test
    fun ignoresOctaveUpHarmonicFadeDuringDecay() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )

        val hop = FloatArray(256)
        var sampleIndex = 0L
        repeat(8) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val fC4 = MidiUtils.midiToFrequency(60)
        val fC5 = MidiUtils.midiToFrequency(72)
        // Loud C4 fundamental attack & sustain
        for (h in 0 until 25) {
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (0.25 * sin(2.0 * PI * fC4 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        // Decaying tail (< 45% peak) dominated by 2nd harmonic C5 (72)
        for (h in 0 until 28) {
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                hop[i] = (0.04 * sin(2.0 * PI * fC5 * t)).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onEvents = events.filter { it.isOn }
        assertEquals("Decaying 2nd-harmonic shift should not re-trigger an octave-up note", 1, onEvents.size)
        assertEquals(60, onEvents[0].midi)
    }

    @Test
    fun detectsSimultaneousTwoHandChordInPolyphonicMode() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )
        tracker.setPolyphonicMode(true)

        val hop = FloatArray(256)
        var sampleIndex = 0L
        repeat(10) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val fC3 = MidiUtils.midiToFrequency(48)
        val fE4 = MidiUtils.midiToFrequency(64)
        for (h in 0 until 30) {
            for (i in 0 until 256) {
                val t = (h * 256 + i).toDouble() / 48_000.0
                val low = 0.14 * (sin(2.0 * PI * fC3 * t) + 0.4 * sin(2.0 * PI * fC3 * 2.0 * t))
                val high = 0.14 * (sin(2.0 * PI * fE4 * t) + 0.4 * sin(2.0 * PI * fE4 * 2.0 * t))
                hop[i] = (low + high).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        repeat(10) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onMidis = events.filter { it.isOn }.map { it.midi }.toSet()
        val offMidis = events.filter { !it.isOn }.map { it.midi }.toSet()
        assertEquals(setOf(48, 64), onMidis)
        assertEquals(setOf(48, 64), offMidis)
    }

    @Test
    fun detectsMovingRightHandMelodyOverHeldLeftHandBass() {
        val events = mutableListOf<CapturedEvent>()
        val tracker = OnsetEnvelopeTracker(
            sampleRate = 48_000,
            hopSize = 256,
            onNoteOn = { midi, ts -> events.add(CapturedEvent(true, midi, ts)) },
            onNoteOff = { midi, ts -> events.add(CapturedEvent(false, midi, ts)) }
        )
        tracker.setPolyphonicMode(true)

        val hop = FloatArray(256)
        var sampleIndex = 0L
        repeat(10) {
            hop.fill(0f)
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val fC3 = MidiUtils.midiToFrequency(48)
        val fE4 = MidiUtils.midiToFrequency(64)
        val fG4 = MidiUtils.midiToFrequency(67)

        // Hold C3 + E4 for 32 hops, then keep C3 continuously sounding while RH switches E4 -> G4
        for (h in 0 until 64) {
            val fRight = if (h < 32) fE4 else fG4
            for (i in 0 until 256) {
                val t = sampleIndex.toDouble() / 48_000.0 + i.toDouble() / 48_000.0
                val low = 0.14 * (sin(2.0 * PI * fC3 * t) + 0.4 * sin(2.0 * PI * fC3 * 2.0 * t))
                val high = 0.14 * (sin(2.0 * PI * fRight * t) + 0.4 * sin(2.0 * PI * fRight * 2.0 * t))
                hop[i] = (low + high).toFloat()
            }
            tracker.processHop(hop, currentMs(sampleIndex))
            sampleIndex += 256
        }

        val onMidis = events.filter { it.isOn }.map { it.midi }
        val offMidis = events.filter { !it.isOn }.map { it.midi }
        assertTrue("Expected initial C3 onset", 48 in onMidis)
        assertTrue("Expected initial E4 onset", 64 in onMidis)
        assertTrue("Expected moving G4 onset", 67 in onMidis)
        assertTrue("Expected E4 release when switching to G4", 64 in offMidis)
        assertEquals("Held C3 bass should not be prematurely released", 0, offMidis.count { it == 48 })
    }

    private fun currentMs(samples: Long): Long = (samples * 1000L) / 48_000L
}
