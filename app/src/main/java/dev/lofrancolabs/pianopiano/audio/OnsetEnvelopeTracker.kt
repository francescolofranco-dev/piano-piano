package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MicSensitivity
import kotlin.math.max

/**
 * Tracks note onset (hammer strike), settles through the transient (~32–53 ms),
 * locks pitch via multi-frame YIN consensus voting, and tracks damper release or repeated strikes.
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

    private data class ConsensusResult(
        val estimate: PitchEstimate,
        val voteCount: Int,
        val weightShare: Float
    )

    private class PitchVoteRing(private val capacity: Int) {
        private val midis = IntArray(capacity) { -1 }
        private val freqs = FloatArray(capacity)
        private val confs = FloatArray(capacity)
        private var writeIndex = 0
        private var count = 0

        fun clear() {
            midis.fill(-1)
            writeIndex = 0
            count = 0
        }

        fun add(estimate: PitchEstimate?) {
            midis[writeIndex] = estimate?.midiNote ?: -1
            freqs[writeIndex] = estimate?.frequencyHz ?: 0f
            confs[writeIndex] = estimate?.confidence ?: 0f
            writeIndex = (writeIndex + 1) % capacity
            if (count < capacity) {
                count++
            }
        }

        fun seed(estimate: PitchEstimate, repeats: Int) {
            clear()
            val times = repeats.coerceIn(0, capacity)
            repeat(times) {
                add(estimate)
            }
        }

        fun validCount(): Int {
            var valid = 0
            for (i in 0 until count) {
                if (midis[i] >= 0) valid++
            }
            return valid
        }

        fun consensus(minVotes: Int, minWeightShare: Float): ConsensusResult? {
            if (count < minVotes) return null
            var totalWeight = 0f
            for (i in 0 until count) {
                if (midis[i] >= 0) {
                    totalWeight += confs[i]
                }
            }
            if (totalWeight <= 1e-6f) return null

            var bestMidi = -1
            var bestWeight = 0f
            var bestVotes = 0
            var bestFreqWeightedSum = 0f

            for (i in 0 until count) {
                val candidateMidi = midis[i]
                if (candidateMidi < 0) continue

                var weightSum = 0f
                var voteCount = 0
                var freqWeightedSum = 0f
                for (j in 0 until count) {
                    if (midis[j] == candidateMidi) {
                        val w = confs[j]
                        weightSum += w
                        voteCount++
                        freqWeightedSum += freqs[j] * w
                    }
                }
                if (weightSum > bestWeight) {
                    bestWeight = weightSum
                    bestMidi = candidateMidi
                    bestVotes = voteCount
                    bestFreqWeightedSum = freqWeightedSum
                }
            }

            if (bestMidi < 0 || bestVotes < minVotes) return null
            val share = bestWeight / totalWeight
            if (share < minWeightShare) return null

            return ConsensusResult(
                estimate = PitchEstimate(
                    frequencyHz = bestFreqWeightedSum / bestWeight,
                    midiNote = bestMidi,
                    confidence = (bestWeight / bestVotes).coerceIn(0f, 1f)
                ),
                voteCount = bestVotes,
                weightShare = share
            )
        }
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
    private var activePeakRms = 0f
    private var settleHops = 0
    private var hopsSinceOnset = 0
    private var quietHops = 0
    private var pendingOnsetMs = 0L
    private var activeMidi = -1
    private var waitForQuietAfterReject = false
    private var livePitchHopCounter = 0
    private var livePitchQuietHops = 0
    private var latestStableConsensus: ConsensusResult? = null
    private val liveVoteRing = PitchVoteRing(LIVE_VOTE_WINDOW_SIZE)
    private val attackVoteRing = PitchVoteRing(ATTACK_VOTE_WINDOW_SIZE)
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
        activePeakRms = 0f
        settleHops = 0
        hopsSinceOnset = 0
        quietHops = 0
        pendingOnsetMs = 0L
        activeMidi = -1
        waitForQuietAfterReject = false
        livePitchHopCounter = 0
        livePitchQuietHops = 0
        latestStableConsensus = null
        liveVoteRing.clear()
        attackVoteRing.clear()
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
        var didSampleLiveThisHop = false
        if (rms >= liveGate) {
            livePitchQuietHops = 0
            livePitchHopCounter++
            if (livePitchHopCounter >= LIVE_PITCH_HOP_INTERVAL) {
                livePitchHopCounter = 0
                didSampleLiveThisHop = true
                val rawEstimate = pitchDetector.detect(analysisWindow)
                liveVoteRing.add(rawEstimate)
                val liveConsensus = liveVoteRing.consensus(
                    minVotes = LIVE_CONSENSUS_MIN_VOTES,
                    minWeightShare = LIVE_CONSENSUS_MIN_SHARE
                )
                if (liveConsensus != null) {
                    currentPitchEstimate = liveConsensus.estimate
                    latestStableConsensus = if (liveConsensus.estimate.confidence >= LIVE_LOCK_CONFIDENCE) {
                        liveConsensus
                    } else {
                        null
                    }
                } else {
                    latestStableConsensus = null
                    if (liveVoteRing.validCount() == 0) {
                        currentPitchEstimate = null
                    }
                }
            }
        } else {
            livePitchHopCounter = 0
            livePitchQuietHops++
            if (livePitchQuietHops >= QUIET_HOPS_TO_RELEASE) {
                liveVoteRing.clear()
                currentPitchEstimate = null
                latestStableConsensus = null
            }
        }

        when (state) {
            State.IDLE -> {
                if (rms < currentOffsetThreshold) {
                    waitForQuietAfterReject = false
                }
                val stable = latestStableConsensus
                if (rms >= currentOnsetThreshold &&
                    (!waitForQuietAfterReject || isMultiHopReattack(rms, minRecentRms))
                ) {
                    state = State.ATTACK_SETTLING
                    pendingOnsetMs = timestampMs
                    activePeakRms = rms
                    settleHops = 1
                    hopsSinceOnset = 1
                    quietHops = 0
                    attackVoteRing.clear()
                    resetRmsHistory()
                } else if (waitForQuietAfterReject &&
                    rms >= currentOnsetThreshold &&
                    stable != null &&
                    stable.voteCount >= RECOVERY_MIN_VOTES &&
                    stable.weightShare >= RECOVERY_MIN_SHARE
                ) {
                    activeMidi = stable.estimate.midiNote
                    currentMidi = stable.estimate.midiNote
                    currentPitchEstimate = stable.estimate
                    activePeakRms = rms
                    state = State.SUSTAINING
                    waitForQuietAfterReject = false
                    hopsSinceOnset = 0
                    quietHops = 0
                    resetRmsHistory()
                    onNoteOn(activeMidi, timestampMs)
                }
            }

            State.ATTACK_SETTLING -> {
                settleHops++
                hopsSinceOnset++
                activePeakRms = max(activePeakRms, rms)
                if (rms < currentOffsetThreshold * 0.7f && settleHops >= 6) {
                    state = State.IDLE
                    waitForQuietAfterReject = false
                    currentMidi = null
                    attackVoteRing.clear()
                } else if (settleHops >= SETTLE_SKIP_HOPS &&
                    (settleHops - SETTLE_SKIP_HOPS) % ATTACK_SAMPLE_HOP_INTERVAL == 0
                ) {
                    val est = pitchDetector.detect(analysisWindow)
                    attackVoteRing.add(est)
                    var locked = attackVoteRing.consensus(
                        minVotes = ATTACK_CONFIRM_VOTES,
                        minWeightShare = ATTACK_CONFIRM_SHARE
                    )
                    if (locked == null && settleHops >= 14) {
                        locked = attackVoteRing.consensus(
                            minVotes = 2,
                            minWeightShare = ATTACK_FALLBACK_SHARE
                        )
                    }
                    if (locked != null) {
                        currentPitchEstimate = locked.estimate
                        liveVoteRing.seed(locked.estimate, ATTACK_CONFIRM_VOTES)
                        activeMidi = locked.estimate.midiNote
                        currentMidi = locked.estimate.midiNote
                        state = State.SUSTAINING
                        waitForQuietAfterReject = false
                        quietHops = 0
                        attackVoteRing.clear()
                        resetRmsHistory()
                        onNoteOn(locked.estimate.midiNote, pendingOnsetMs)
                    } else if (settleHops >= MAX_SETTLE_HOPS) {
                        state = State.IDLE
                        waitForQuietAfterReject = true
                        currentMidi = null
                        attackVoteRing.clear()
                    }
                } else if (settleHops >= MAX_SETTLE_HOPS) {
                    state = State.IDLE
                    waitForQuietAfterReject = true
                    currentMidi = null
                    attackVoteRing.clear()
                }
            }

            State.SUSTAINING -> {
                hopsSinceOnset++
                activePeakRms = max(activePeakRms, rms)

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
                    activePeakRms = rms
                    settleHops = 1
                    hopsSinceOnset = 1
                    quietHops = 0
                    waitForQuietAfterReject = false
                    liveVoteRing.clear()
                    attackVoteRing.clear()
                    resetRmsHistory()
                    prevRms = rms
                    recordRms(rms)
                    return
                }

                // 3) Check debounced consensus legato pitch change
                if (hopsSinceOnset >= MIN_LEGATO_HOPS &&
                    rms >= currentOffsetThreshold &&
                    didSampleLiveThisHop
                ) {
                    val legatoConsensus = liveVoteRing.consensus(
                        minVotes = LEGATO_CONFIRM_VOTES,
                        minWeightShare = LEGATO_CONFIRM_SHARE
                    )
                    if (legatoConsensus != null &&
                        legatoConsensus.estimate.confidence >= LEGATO_MIN_CONFIDENCE &&
                        legatoConsensus.estimate.midiNote != activeMidi
                    ) {
                        val isOctaveUpDecay =
                            legatoConsensus.estimate.midiNote == activeMidi + 12 &&
                                rms < activePeakRms * OCTAVE_DECAY_RMS_RATIO
                        if (!isOctaveUpDecay) {
                            val prevMidi = activeMidi
                            val newMidi = legatoConsensus.estimate.midiNote
                            onNoteOff(prevMidi, timestampMs)
                            activeMidi = newMidi
                            currentMidi = newMidi
                            currentPitchEstimate = legatoConsensus.estimate
                            activePeakRms = rms
                            hopsSinceOnset = 0
                            resetRmsHistory()
                            onNoteOn(newMidi, max(0L, timestampMs - 40L))
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
        private const val SETTLE_SKIP_HOPS = 6          // ~32 ms transient skip before sampling attack
        private const val ATTACK_SAMPLE_HOP_INTERVAL = 2 // ~10.7 ms per attack vote
        private const val ATTACK_VOTE_WINDOW_SIZE = 7
        private const val ATTACK_CONFIRM_VOTES = 3
        private const val ATTACK_CONFIRM_SHARE = 0.55f
        private const val ATTACK_FALLBACK_SHARE = 0.60f
        private const val MAX_SETTLE_HOPS = 18           // ~96 ms max wait for attack consensus
        private const val QUIET_HOPS_TO_RELEASE = 6      // ~32 ms below sustain floor
        private const val LIVE_PITCH_HOP_INTERVAL = 4    // ~21.3 ms per live vote sample
        private const val LIVE_VOTE_WINDOW_SIZE = 7      // ~150 ms rolling consensus window
        private const val LIVE_CONSENSUS_MIN_VOTES = 3
        private const val LIVE_CONSENSUS_MIN_SHARE = 0.55f
        private const val LIVE_LOCK_CONFIDENCE = 0.72f
        private const val RECOVERY_MIN_VOTES = 4
        private const val RECOVERY_MIN_SHARE = 0.65f
        private const val MIN_NOTE_LENGTH_HOPS = 20      // ~107 ms refractory period
        private const val MIN_LEGATO_HOPS = 20           // ~107 ms before legato pitch change
        private const val LEGATO_CONFIRM_VOTES = 5
        private const val LEGATO_CONFIRM_SHARE = 0.70f
        private const val LEGATO_MIN_CONFIDENCE = 0.78f
        private const val OCTAVE_DECAY_RMS_RATIO = 0.45f
        private const val REATTACK_HISTORY_HOPS = 8
        private const val MULTI_HOP_REATTACK_RATIO = 1.65f
    }
}
