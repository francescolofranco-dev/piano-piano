package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MidiUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Allocation-free 4096-point FFT + iterative harmonic peeling detector for
 * recognizing up to [maxNotes] simultaneous piano keys across C3 (48) .. C5 (72).
 */
class PolyphonicChordDetector(
    private val sampleRate: Int = 48_000,
    val windowSize: Int = 4096
) {
    private val log2N: Int = Integer.numberOfTrailingZeros(windowSize)
    private val halfWindow = windowSize / 2
    private val binHz = sampleRate.toFloat() / windowSize.toFloat()
    private val maxBin = min(240, halfWindow)
    private val floorEndBin = min(180, maxBin)

    private val hannWindow = FloatArray(windowSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (windowSize - 1)))).toFloat()
    }
    private val bitRev = IntArray(windowSize) { i ->
        Integer.reverse(i) ushr (32 - log2N)
    }
    private val cosTable = FloatArray(halfWindow) { k ->
        cos(2.0 * PI * k / windowSize).toFloat()
    }
    private val sinTable = FloatArray(halfWindow) { k ->
        sin(2.0 * PI * k / windowSize).toFloat()
    }

    private val real = FloatArray(windowSize)
    private val imag = FloatArray(windowSize)
    private val mag = FloatArray(maxBin + 1)
    private val residualMag = FloatArray(maxBin + 1)
    private val floorScratch = FloatArray((floorEndBin - FLOOR_START_BIN + 1).coerceAtLeast(1))
    private val pickedMidis = IntArray(4)

    init {
        require(windowSize > 0 && (windowSize and (windowSize - 1)) == 0) {
            "FFT windowSize must be a power of 2, got $windowSize"
        }
    }

    fun detectChords(
        samples: FloatArray,
        maxNotes: Int = 2,
        minRms: Float = 0.002f
    ): List<PitchEstimate> {
        require(samples.size >= windowSize) {
            "Expected at least $windowSize samples, got ${samples.size}"
        }
        val cappedMaxNotes = maxNotes.coerceIn(1, pickedMidis.size)

        var sumSq = 0.0
        var maxAbs = 0f
        for (i in 0 until windowSize) {
            val s = samples[i]
            val a = kotlin.math.abs(s)
            if (a > maxAbs) maxAbs = a
            sumSq += s.toDouble() * s.toDouble()
        }
        val rms = sqrt(sumSq / windowSize).toFloat()
        if (rms < minRms || maxAbs < 1e-6f) return emptyList()

        val invPeak = 1f / maxAbs
        for (i in 0 until windowSize) {
            val rev = bitRev[i]
            real[i] = samples[rev] * invPeak * hannWindow[rev]
            imag[i] = 0f
        }

        // Iterative in-place Radix-2 Cooley-Tukey FFT
        var halfSize = 1
        while (halfSize < windowSize) {
            val step = halfSize shl 1
            val tableStep = halfWindow / halfSize
            var base = 0
            while (base < windowSize) {
                var tableIdx = 0
                for (j in 0 until halfSize) {
                    val idx1 = base + j
                    val idx2 = idx1 + halfSize
                    val c = cosTable[tableIdx]
                    val s = sinTable[tableIdx]
                    val r2 = real[idx2]
                    val i2 = imag[idx2]
                    val tr = r2 * c + i2 * s
                    val ti = i2 * c - r2 * s
                    real[idx2] = real[idx1] - tr
                    imag[idx2] = imag[idx1] - ti
                    real[idx1] += tr
                    imag[idx1] += ti
                    tableIdx += tableStep
                }
                base += step
            }
            halfSize = step
        }

        var peakMag = 0f
        for (b in 0..maxBin) {
            val re = real[b]
            val im = imag[b]
            val m = sqrt(re * re + im * im)
            mag[b] = m
            if (b >= FLOOR_START_BIN && m > peakMag) {
                peakMag = m
            }
        }
        if (peakMag <= 1e-5f) return emptyList()

        val floorCount = (floorEndBin - FLOOR_START_BIN + 1).coerceAtLeast(1)
        for (i in 0 until floorCount) {
            floorScratch[i] = mag[FLOOR_START_BIN + i]
        }
        floorScratch.sort(0, floorCount)
        val noiseFloor = max(floorScratch[(floorCount * 40) / 100], 1e-5f)

        System.arraycopy(mag, 0, residualMag, 0, maxBin + 1)

        val result = ArrayList<PitchEstimate>(cappedMaxNotes)
        var pickedCount = 0
        var firstScore = 0f

        for (pass in 0 until cappedMaxNotes) {
            var bestMidi = -1
            var bestScore = 0f
            var bestM1 = 0f

            for (midi in MIN_MIDI..MAX_MIDI) {
                var tooClose = false
                for (p in 0 until pickedCount) {
                    if (kotlin.math.abs(pickedMidis[p] - midi) <= 1) {
                        tooClose = true
                        break
                    }
                }
                if (tooClose) continue

                val f0 = MidiUtils.midiToFrequency(midi).toFloat()
                val m1 = trueHarmonicPeak(residualMag, f0 / binHz)
                // Fundamental presence guard prevents phantom subharmonic octave ghosts (e.g. C3 from C4)
                if (m1 < max(noiseFloor * 3.5f, peakMag * 0.06f)) continue

                val m2 = trueHarmonicPeak(residualMag, (2f * f0) / binHz)
                val m3 = trueHarmonicPeak(residualMag, (3f * f0) / binHz)
                val m4 = trueHarmonicPeak(residualMag, (4f * f0) / binHz)
                val score = m1 + 0.65f * m2 + 0.40f * m3 + 0.20f * m4

                if (score > bestScore) {
                    bestScore = score
                    bestMidi = midi
                    bestM1 = m1
                }
            }

            if (bestMidi < 0) break
            if (bestScore < noiseFloor * 6.0f) break
            if (pass > 0 && bestScore < firstScore * 0.26f) break

            if (pass == 0) {
                firstScore = bestScore
            }
            pickedMidis[pickedCount++] = bestMidi

            val f0 = MidiUtils.midiToFrequency(bestMidi).toFloat()
            val conf = (1f - noiseFloor / max(bestM1, noiseFloor)).coerceIn(0.5f, 0.99f)
            result.add(PitchEstimate(frequencyHz = f0, midiNote = bestMidi, confidence = conf))

            // Peel harmonics from residual spectrum so remaining chord voices stand out
            for (h in 1..6) {
                val centerBin = ((h * f0) / binHz).roundToInt()
                val peelAmount = bestM1 * EXPECTED_HARMONIC_DECAY[h]
                val radius = if (h == 1) 2 else 1
                val startB = max(1, centerBin - radius)
                val endB = min(maxBin, centerBin + radius)
                for (b in startB..endB) {
                    val weight = if (kotlin.math.abs(b - centerBin) == 2) 0.70f else 1.0f
                    residualMag[b] = (residualMag[b] - peelAmount * weight).coerceAtLeast(0f)
                }
            }
        }

        result.sortBy { it.midiNote }
        return result
    }

    private fun trueHarmonicPeak(spectrum: FloatArray, exactBin: Float): Float {
        val centerBin = exactBin.roundToInt()
        if (centerBin < 1 || centerBin > maxBin) return 0f
        var bestBin = centerBin
        var bestVal = spectrum[centerBin]
        if (centerBin - 1 >= 1 && spectrum[centerBin - 1] > bestVal) {
            bestBin = centerBin - 1
            bestVal = spectrum[bestBin]
        }
        if (centerBin + 1 <= maxBin && spectrum[centerBin + 1] > bestVal) {
            bestBin = centerBin + 1
            bestVal = spectrum[bestBin]
        }
        // Reject spectral shoulders rising monotonically into an adjacent note's bin
        if (kotlin.math.abs(bestBin - exactBin) > 0.72f) return 0f
        if (bestBin == centerBin - 1 && bestBin - 1 >= 1 && spectrum[bestBin - 1] > bestVal) {
            return 0f
        }
        if (bestBin == centerBin + 1 && bestBin + 1 <= maxBin && spectrum[bestBin + 1] > bestVal) {
            return 0f
        }
        return bestVal
    }

    companion object {
        private const val MIN_MIDI = 48 // C3
        private const val MAX_MIDI = 72 // C5
        private const val FLOOR_START_BIN = 7 // ~82 Hz
        private val EXPECTED_HARMONIC_DECAY = floatArrayOf(0f, 0.90f, 0.55f, 0.35f, 0.22f, 0.15f, 0.10f)
    }
}
