package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MidiUtils
import kotlin.math.abs
import kotlin.math.sqrt

data class PitchEstimate(
    val frequencyHz: Float,
    val midiNote: Int,
    val confidence: Float
)

/**
 * Real-time YIN monophonic pitch detector tuned for piano frequencies (~55 Hz to ~1850 Hz).
 * Scratch buffers are pre-allocated so per-hop detection avoids heap allocations.
 */
class YinPitchDetector(
    private val sampleRate: Int = 48_000,
    val windowSize: Int = 1024,
    private val yinThreshold: Float = 0.16f,
    private val minRms: Float = 0.0015f
) {
    private val halfWindow = windowSize / 2
    private val minTau = (sampleRate / 1850f).toInt().coerceAtLeast(2)
    private val maxTau = (sampleRate / 85f).toInt().coerceAtMost(halfWindow - 2)

    private val normalizedBuffer = FloatArray(windowSize)
    private val diffBuffer = FloatArray(maxTau + 1)
    private val cmndBuffer = FloatArray(maxTau + 1)

    fun detect(samples: FloatArray): PitchEstimate? {
        require(samples.size >= windowSize) { "Expected at least $windowSize samples, got ${samples.size}" }

        var sumSq = 0.0
        var maxAbs = 0f
        for (i in 0 until windowSize) {
            val sample = samples[i]
            val absSample = kotlin.math.abs(sample)
            if (absSample > maxAbs) maxAbs = absSample
            val s = sample.toDouble()
            sumSq += s * s
        }
        val rms = sqrt(sumSq / windowSize).toFloat()
        if (rms < minRms || maxAbs < 1e-6f) return null

        val invPeak = 1f / maxAbs
        for (i in 0 until windowSize) {
            normalizedBuffer[i] = samples[i] * invPeak
        }

        // Step 1: Difference function on peak-normalized samples for numeric stability on quiet mic input
        for (tau in 1..maxTau) {
            var sum = 0f
            for (i in 0 until halfWindow) {
                val delta = normalizedBuffer[i] - normalizedBuffer[i + tau]
                sum += delta * delta
            }
            diffBuffer[tau] = sum
        }

        // Step 2: Cumulative mean normalized difference (CMND)
        cmndBuffer[0] = 1f
        var runningSum = 0f
        for (tau in 1..maxTau) {
            runningSum += diffBuffer[tau]
            cmndBuffer[tau] = if (runningSum > 1e-12f) {
                diffBuffer[tau] * tau / runningSum
            } else {
                1f
            }
        }

        // Step 3: Absolute threshold search for first local minimum
        var tauEstimate = -1
        var tau = minTau
        while (tau <= maxTau) {
            if (cmndBuffer[tau] < yinThreshold) {
                while (tau + 1 <= maxTau && cmndBuffer[tau + 1] < cmndBuffer[tau]) {
                    tau++
                }
                tauEstimate = tau
                break
            }
            tau++
        }

        // Fallback to strong global minimum below 0.30 if slightly inharmonic acoustic piano string
        if (tauEstimate == -1) {
            var bestTau = -1
            var bestVal = 0.30f
            for (t in minTau..maxTau) {
                if (cmndBuffer[t] < bestVal) {
                    bestVal = cmndBuffer[t]
                    bestTau = t
                }
            }
            tauEstimate = bestTau
        }

        if (tauEstimate <= 0) return null

        // Guard against 2nd-harmonic octave-up lock when phone mics attenuate the fundamental
        if (cmndBuffer[tauEstimate] > 0.04f) {
            val doubleTau = tauEstimate * 2
            if (doubleTau in (minTau + 2)..(maxTau - 2)) {
                var subTau = -1
                var subVal = Float.MAX_VALUE
                for (t in (doubleTau - 2)..(doubleTau + 2)) {
                    if (cmndBuffer[t] < subVal) {
                        subVal = cmndBuffer[t]
                        subTau = t
                    }
                }
                if (subTau > 0 && subVal < cmndBuffer[tauEstimate] * 0.80f && subVal < 0.25f) {
                    tauEstimate = subTau
                }
            }
        }

        // Step 4: Parabolic interpolation around tauEstimate
        val betterTau = if (tauEstimate in (minTau + 1) until maxTau) {
            val s0 = cmndBuffer[tauEstimate - 1]
            val s1 = cmndBuffer[tauEstimate]
            val s2 = cmndBuffer[tauEstimate + 1]
            val denom = 2f * (s0 - 2f * s1 + s2)
            if (abs(denom) > 1e-6f) {
                (tauEstimate + (s0 - s2) / denom).coerceIn(tauEstimate - 1f, tauEstimate + 1f)
            } else {
                tauEstimate.toFloat()
            }
        } else {
            tauEstimate.toFloat()
        }

        val freqHz = sampleRate.toFloat() / betterTau
        if (freqHz < 50f || freqHz > 2000f) return null

        val midi = MidiUtils.frequencyToMidi(freqHz.toDouble())
        val confidence = (1f - cmndBuffer[tauEstimate]).coerceIn(0f, 1f)
        return PitchEstimate(frequencyHz = freqHz, midiNote = midi, confidence = confidence)
    }

    companion object {
        fun computeRms(samples: FloatArray, count: Int = samples.size): Float {
            if (count <= 0) return 0f
            var sumSq = 0.0
            for (i in 0 until count) {
                val s = samples[i].toDouble()
                sumSq += s * s
            }
            return sqrt(sumSq / count).toFloat()
        }
    }
}
