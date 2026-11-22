package dev.lofrancolabs.pianopiano.audio

import kotlin.math.abs
import kotlin.math.max

/**
 * Tracks note onset (hammer strike), settles through the transient (~16 ms),
 * locks pitch via YIN, and tracks damper release or repeated strikes.
 */
class OnsetEnvelopeTracker(
    private val sampleRate: Int = 48_000,
    val hopSize: Int = 256,
    private val pitchDetector: YinPitchDetector = YinPitchDetector(sampleRate = sampleRate),
    private val onNoteOn: (midi: Int, timestampMs: Long) -> Unit,
    private val onNoteOff: (midi: Int, timestampMs: Long) -> Unit
) {
    private enum class State {
        IDLE,
        ATTACK_SETTLING,
        SUSTAINING
    }

    private val windowSize = pitchDetector.windowSize
    private val analysisWindow = FloatArray(windowSize)

    private var state = State.IDLE
    private var noiseFloorRms = 0.004f
    private var onsetThreshold = 0.018f
    private var offsetThreshold = 0.008f

    private var prevRms = 0f
    private var settleHops = 0
    private var hopsSinceOnset = 0
    private var quietHops = 0
    private var pendingOnsetMs = 0L
    private var activeMidi = -1
    private var candidateLegatoMidi = -1
    private var candidateLegatoHops = 0

    var currentRms: Float = 0f
        private set
    var currentMidi: Int? = null
        private set

    init {
        recomputeThresholds()
    }

    fun setNoiseFloor(measuredNoiseRms: Float) {
        noiseFloorRms = measuredNoiseRms.coerceIn(0.003f, 0.08f)
        recomputeThresholds()
    }

    fun getNoiseFloor(): Float = noiseFloorRms

    fun reset() {
        analysisWindow.fill(0f)
        state = State.IDLE
        prevRms = 0f
        settleHops = 0
        hopsSinceOnset = 0
        quietHops = 0
        pendingOnsetMs = 0L
        activeMidi = -1
        candidateLegatoMidi = -1
        candidateLegatoHops = 0
        currentRms = 0f
        currentMidi = null
    }

    fun processHop(hopSamples: FloatArray, timestampMs: Long) {
        require(hopSamples.size == hopSize) { "Expected $hopSize hop samples, got ${hopSamples.size}" }

        // Shift sliding analysis buffer
        System.arraycopy(analysisWindow, hopSize, analysisWindow, 0, windowSize - hopSize)
        System.arraycopy(hopSamples, 0, analysisWindow, windowSize - hopSize, hopSize)

        val rms = YinPitchDetector.computeRms(hopSamples, hopSize)
        currentRms = rms

        when (state) {
            State.IDLE -> {
                if (rms >= onsetThreshold && (prevRms <= offsetThreshold || rms >= prevRms * 1.35f)) {
                    state = State.ATTACK_SETTLING
                    pendingOnsetMs = timestampMs
                    settleHops = 1
                    hopsSinceOnset = 1
                    quietHops = 0
                }
            }

            State.ATTACK_SETTLING -> {
                settleHops++
                hopsSinceOnset++
                if (rms < offsetThreshold * 0.7f && settleHops >= 4) {
                    state = State.IDLE
                    currentMidi = null
                } else if (settleHops >= SETTLE_HOPS_REQUIRED) {
                    val estimate = pitchDetector.detect(analysisWindow)
                    if (estimate != null) {
                        activeMidi = estimate.midiNote
                        currentMidi = estimate.midiNote
                        state = State.SUSTAINING
                        quietHops = 0
                        candidateLegatoMidi = -1
                        candidateLegatoHops = 0
                        onNoteOn(estimate.midiNote, pendingOnsetMs)
                    } else if (settleHops >= MAX_SETTLE_HOPS) {
                        state = State.IDLE
                        currentMidi = null
                    }
                }
            }

            State.SUSTAINING -> {
                hopsSinceOnset++

                // 1) Check energy decay (damper release)
                if (rms < offsetThreshold) {
                    quietHops++
                    if (quietHops >= QUIET_HOPS_TO_RELEASE) {
                        val releasedMidi = activeMidi
                        activeMidi = -1
                        currentMidi = null
                        state = State.IDLE
                        onNoteOff(releasedMidi, timestampMs)
                        prevRms = rms
                        return
                    }
                } else {
                    quietHops = 0
                }

                // 2) Check re-attack spike (same key struck again or sharper louder note)
                if (hopsSinceOnset >= MIN_NOTE_LENGTH_HOPS &&
                    rms >= onsetThreshold &&
                    rms >= prevRms * REATTACK_RATIO
                ) {
                    val prevMidi = activeMidi
                    activeMidi = -1
                    currentMidi = null
                    onNoteOff(prevMidi, timestampMs)
                    state = State.ATTACK_SETTLING
                    pendingOnsetMs = timestampMs
                    settleHops = 1
                    hopsSinceOnset = 1
                    quietHops = 0
                    candidateLegatoMidi = -1
                    candidateLegatoHops = 0
                    prevRms = rms
                    return
                }

                // 3) Check stable legato pitch change
                if (hopsSinceOnset >= MIN_LEGATO_HOPS && rms >= offsetThreshold) {
                    val estimate = pitchDetector.detect(analysisWindow)
                    if (estimate != null && estimate.confidence >= 0.72f) {
                        if (abs(estimate.midiNote - activeMidi) >= 1) {
                            if (estimate.midiNote == candidateLegatoMidi) {
                                candidateLegatoHops++
                            } else {
                                candidateLegatoMidi = estimate.midiNote
                                candidateLegatoHops = 1
                            }
                            if (candidateLegatoHops >= LEGATO_CONFIRM_HOPS) {
                                val prevMidi = activeMidi
                                val newMidi = candidateLegatoMidi
                                onNoteOff(prevMidi, timestampMs)
                                activeMidi = newMidi
                                currentMidi = newMidi
                                hopsSinceOnset = 0
                                candidateLegatoMidi = -1
                                candidateLegatoHops = 0
                                onNoteOn(newMidi, max(0L, timestampMs - 10L))
                            }
                        } else {
                            candidateLegatoMidi = -1
                            candidateLegatoHops = 0
                        }
                    }
                }
            }
        }

        prevRms = rms
    }

    private fun recomputeThresholds() {
        onsetThreshold = max(noiseFloorRms * 4.5f, 0.018f)
        offsetThreshold = max(noiseFloorRms * 1.9f, 0.008f)
    }

    companion object {
        private const val SETTLE_HOPS_REQUIRED = 4   // 1024 samples (~21.3 ms) so YIN sees only post-onset audio
        private const val MAX_SETTLE_HOPS = 8        // ~43 ms max wait for pitch lock
        private const val QUIET_HOPS_TO_RELEASE = 4  // ~21 ms below sustain floor
        private const val MIN_NOTE_LENGTH_HOPS = 8   // ~43 ms refractory period
        private const val MIN_LEGATO_HOPS = 7
        private const val LEGATO_CONFIRM_HOPS = 3
        private const val REATTACK_RATIO = 1.85f
    }
}
