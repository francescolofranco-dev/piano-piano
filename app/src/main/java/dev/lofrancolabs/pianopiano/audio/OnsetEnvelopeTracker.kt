package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MicSensitivity
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
    private var noiseFloorRms = 0.002f
    private var sensitivityMultiplier = MicSensitivity.NORMAL.thresholdMultiplier

    var currentOnsetThreshold: Float = 0.006f
        private set
    var currentOffsetThreshold: Float = 0.003f
        private set

    private var prevRms = 0f
    private var settleHops = 0
    private var hopsSinceOnset = 0
    private var quietHops = 0
    private var pendingOnsetMs = 0L
    private var activeMidi = -1
    private var candidateLegatoMidi = -1
    private var candidateLegatoHops = 0
    private var waitForQuietAfterReject = false
    private var livePitchHopCounter = 0
    private var livePitchQuietHops = 0
    private var consecutiveLivePitchMidi = -1
    private var consecutiveLivePitchCount = 0
    private val rmsHistory = FloatArray(REATTACK_HISTORY_HOPS)
    private var rmsHistoryIndex = 0
    private var rmsHistoryCount = 0

    var currentRms: Float = 0f
        private set
    var currentMidi: Int? = null
        private set
    var currentPitchEstimate: PitchEstimate? = null
        private set

    init {
        recomputeThresholds()
    }

    fun setSensitivity(sensitivity: MicSensitivity) {
        sensitivityMultiplier = sensitivity.thresholdMultiplier
        recomputeThresholds()
    }

    fun setNoiseFloor(measuredNoiseRms: Float) {
        noiseFloorRms = measuredNoiseRms.coerceIn(0.001f, 0.05f)
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
        waitForQuietAfterReject = false
        livePitchHopCounter = 0
        livePitchQuietHops = 0
        consecutiveLivePitchMidi = -1
        consecutiveLivePitchCount = 0
        resetRmsHistory()
        currentRms = 0f
        currentMidi = null
        currentPitchEstimate = null
    }

    fun processHop(hopSamples: FloatArray, timestampMs: Long) {
        require(hopSamples.size == hopSize) { "Expected $hopSize hop samples, got ${hopSamples.size}" }

        // Shift sliding analysis buffer
        System.arraycopy(analysisWindow, hopSize, analysisWindow, 0, windowSize - hopSize)
        System.arraycopy(hopSamples, 0, analysisWindow, windowSize - hopSize, hopSize)

        val rms = YinPitchDetector.computeRms(hopSamples, hopSize)
        val minRecentRms = recentMinRms()
        currentRms = rms

        val liveGate = max(currentOffsetThreshold * 0.65f, 0.002f)
        if (rms >= liveGate) {
            livePitchQuietHops = 0
            livePitchHopCounter++
            if (livePitchHopCounter >= LIVE_PITCH_HOP_INTERVAL) {
                livePitchHopCounter = 0
                val liveEstimate = pitchDetector.detect(analysisWindow)
                if (liveEstimate != null) {
                    currentPitchEstimate = liveEstimate
                    if (liveEstimate.confidence >= LIVE_LOCK_CONFIDENCE) {
                        if (liveEstimate.midiNote == consecutiveLivePitchMidi) {
                            consecutiveLivePitchCount++
                        } else {
                            consecutiveLivePitchMidi = liveEstimate.midiNote
                            consecutiveLivePitchCount = 1
                        }
                    }
                }
            }
        } else {
            livePitchHopCounter = 0
            consecutiveLivePitchMidi = -1
            consecutiveLivePitchCount = 0
            livePitchQuietHops++
            if (livePitchQuietHops >= QUIET_HOPS_TO_RELEASE) {
                currentPitchEstimate = null
            }
        }

        when (state) {
            State.IDLE -> {
                if (rms < currentOffsetThreshold) {
                    waitForQuietAfterReject = false
                }
                val lockedEstimate = currentPitchEstimate
                if (rms >= currentOnsetThreshold &&
                    (!waitForQuietAfterReject || isMultiHopReattack(rms, minRecentRms))
                ) {
                    state = State.ATTACK_SETTLING
                    pendingOnsetMs = timestampMs
                    settleHops = 1
                    hopsSinceOnset = 1
                    quietHops = 0
                    resetRmsHistory()
                } else if (waitForQuietAfterReject &&
                    rms >= currentOnsetThreshold &&
                    consecutiveLivePitchCount >= 2 &&
                    lockedEstimate != null
                ) {
                    activeMidi = lockedEstimate.midiNote
                    currentMidi = lockedEstimate.midiNote
                    state = State.SUSTAINING
                    waitForQuietAfterReject = false
                    hopsSinceOnset = 0
                    quietHops = 0
                    candidateLegatoMidi = -1
                    candidateLegatoHops = 0
                    resetRmsHistory()
                    onNoteOn(activeMidi, timestampMs)
                }
            }

            State.ATTACK_SETTLING -> {
                settleHops++
                hopsSinceOnset++
                if (rms < currentOffsetThreshold * 0.7f && settleHops >= 4) {
                    state = State.IDLE
                    waitForQuietAfterReject = false
                    currentMidi = null
                } else if (settleHops >= SETTLE_HOPS_REQUIRED) {
                    val estimate = pitchDetector.detect(analysisWindow)
                    if (estimate != null) {
                        currentPitchEstimate = estimate
                        activeMidi = estimate.midiNote
                        currentMidi = estimate.midiNote
                        state = State.SUSTAINING
                        waitForQuietAfterReject = false
                        quietHops = 0
                        candidateLegatoMidi = -1
                        candidateLegatoHops = 0
                        resetRmsHistory()
                        onNoteOn(estimate.midiNote, pendingOnsetMs)
                    } else if (settleHops >= MAX_SETTLE_HOPS) {
                        state = State.IDLE
                        waitForQuietAfterReject = true
                        currentMidi = null
                    }
                }
            }

            State.SUSTAINING -> {
                hopsSinceOnset++

                // 1) Check energy decay (damper release)
                if (rms < currentOffsetThreshold) {
                    quietHops++
                    if (quietHops >= QUIET_HOPS_TO_RELEASE) {
                        val releasedMidi = activeMidi
                        activeMidi = -1
                        currentMidi = null
                        state = State.IDLE
                        waitForQuietAfterReject = false
                        onNoteOff(releasedMidi, timestampMs)
                        prevRms = rms
                        recordRms(rms)
                        return
                    }
                } else {
                    quietHops = 0
                }

                // 2) Check multi-hop re-attack swell (same key struck again or sharper louder note)
                if (hopsSinceOnset >= MIN_NOTE_LENGTH_HOPS && isMultiHopReattack(rms, minRecentRms)) {
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
                    waitForQuietAfterReject = false
                    resetRmsHistory()
                    prevRms = rms
                    recordRms(rms)
                    return
                }

                // 3) Check stable legato pitch change
                if (hopsSinceOnset >= MIN_LEGATO_HOPS && rms >= currentOffsetThreshold) {
                    val estimate = pitchDetector.detect(analysisWindow)
                    if (estimate != null && estimate.confidence >= LEGATO_MIN_CONFIDENCE) {
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
                                currentPitchEstimate = estimate
                                hopsSinceOnset = 0
                                candidateLegatoMidi = -1
                                candidateLegatoHops = 0
                                resetRmsHistory()
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
        recordRms(rms)
    }

    private fun recentMinRms(): Float {
        if (rmsHistoryCount == 0) return prevRms
        var minVal = Float.MAX_VALUE
        for (i in 0 until rmsHistoryCount) {
            if (rmsHistory[i] < minVal) {
                minVal = rmsHistory[i]
            }
        }
        return minVal
    }

    private fun recordRms(rms: Float) {
        rmsHistory[rmsHistoryIndex] = rms
        rmsHistoryIndex = (rmsHistoryIndex + 1) % REATTACK_HISTORY_HOPS
        if (rmsHistoryCount < REATTACK_HISTORY_HOPS) {
            rmsHistoryCount++
        }
    }

    private fun resetRmsHistory() {
        rmsHistory.fill(0f)
        rmsHistoryIndex = 0
        rmsHistoryCount = 0
    }

    private fun isMultiHopReattack(rms: Float, minRecentRms: Float): Boolean {
        return rms >= currentOnsetThreshold &&
            rms > prevRms &&
            (minRecentRms <= 1e-6f || rms >= minRecentRms * MULTI_HOP_REATTACK_RATIO)
    }

    private fun recomputeThresholds() {
        currentOnsetThreshold = max(noiseFloorRms * 3.0f, 0.006f) * sensitivityMultiplier
        currentOffsetThreshold = max(noiseFloorRms * 1.5f, 0.003f) * sensitivityMultiplier
    }

    companion object {
        private const val SETTLE_HOPS_REQUIRED = 4   // 1024 samples (~21.3 ms) so YIN sees only post-onset audio
        private const val MAX_SETTLE_HOPS = 14       // ~75 ms max wait for pitch lock
        private const val QUIET_HOPS_TO_RELEASE = 4  // ~21 ms below sustain floor
        private const val LIVE_PITCH_HOP_INTERVAL = 4 // ~21 ms continuous pitch refresh for diagnostics
        private const val LIVE_LOCK_CONFIDENCE = 0.68f
        private const val MIN_NOTE_LENGTH_HOPS = 12  // ~64 ms refractory period
        private const val MIN_LEGATO_HOPS = 7
        private const val LEGATO_CONFIRM_HOPS = 3
        private const val LEGATO_MIN_CONFIDENCE = 0.65f
        private const val REATTACK_HISTORY_HOPS = 6
        private const val MULTI_HOP_REATTACK_RATIO = 1.55f
    }
}
