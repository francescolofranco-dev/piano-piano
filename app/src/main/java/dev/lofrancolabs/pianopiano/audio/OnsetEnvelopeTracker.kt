package dev.lofrancolabs.pianopiano.audio

import dev.lofrancolabs.pianopiano.model.MicSensitivity
import kotlin.math.max

/**
 * Tracks note onset (hammer strike), settles through the transient (~32–53 ms),
 * locks pitch via multi-frame YIN consensus voting (or FFT harmonic peeling in polyphonic mode),
 * and tracks damper release or repeated strikes.
 */
class OnsetEnvelopeTracker(
    private val sampleRate: Int = 48_000,
    val hopSize: Int = 256,
    private val pitchDetector: YinPitchDetector = YinPitchDetector(sampleRate = sampleRate),
    private val polyDetector: PolyphonicChordDetector = PolyphonicChordDetector(sampleRate = sampleRate),
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
    private val polyWindowSize = polyDetector.windowSize
    private val polyWindow = FloatArray(polyWindowSize)

    private var state = State.IDLE
    private var noiseFloorRms = 0.002f
    private var sensitivityMultiplier = MicSensitivity.NORMAL.thresholdMultiplier

    @Volatile
    private var requestedPolyphonicMode = false
    private var polyphonicEnabled = false

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

    // Polyphonic state
    private val activePolyMidis = mutableSetOf<Int>()
    private val polyConfirmCounts = IntArray(128)
    private val polyAbsentCounts = IntArray(128)
    private val polyAttackVotes = IntArray(128)
    private val polyAttackConfSums = FloatArray(128)
    private var polySampleCounter = 0
    private var polyLeadReachedHop = -1
    private var polyReattackScanRemaining = 0
    private var polyReattackNewNoteDetected = false

    var currentRms: Float = 0f
        private set
    var currentMidi: Int? = null
        private set
    var currentMidis: Set<Int> = emptySet()
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

    fun setPolyphonicMode(enabled: Boolean) {
        requestedPolyphonicMode = enabled
    }

    fun reset() {
        analysisWindow.fill(0f)
        polyWindow.fill(0f)
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
        activePolyMidis.clear()
        polyConfirmCounts.fill(0)
        polyAbsentCounts.fill(0)
        polyAttackVotes.fill(0)
        polyAttackConfSums.fill(0f)
        polySampleCounter = 0
        polyLeadReachedHop = -1
        polyReattackScanRemaining = 0
        polyReattackNewNoteDetected = false
        currentRms = 0f
        currentMidi = null
        currentMidis = emptySet()
        currentPitchEstimate = null
    }

    fun processHop(hopSamples: FloatArray, timestampMs: Long) {
        require(hopSamples.size == hopSize) { "Expected $hopSize hop samples, got ${hopSamples.size}" }

        if (polyphonicEnabled != requestedPolyphonicMode) {
            polyphonicEnabled = requestedPolyphonicMode
            reset()
        }

        // Shift sliding analysis buffers
        System.arraycopy(analysisWindow, hopSize, analysisWindow, 0, windowSize - hopSize)
        System.arraycopy(hopSamples, 0, analysisWindow, windowSize - hopSize, hopSize)
        System.arraycopy(polyWindow, hopSize, polyWindow, 0, polyWindowSize - hopSize)
        System.arraycopy(hopSamples, 0, polyWindow, polyWindowSize - hopSize, hopSize)

        val rms = YinPitchDetector.computeRms(hopSamples, hopSize)
        val minRecentRms = recentMinRms()
        currentRms = rms

        if (polyphonicEnabled) {
            processPolyphonicHop(rms, minRecentRms, timestampMs)
        } else {
            processMonophonicHop(rms, minRecentRms, timestampMs)
        }
    }

    private fun processMonophonicHop(rms: Float, minRecentRms: Float, timestampMs: Long) {
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
                    currentMidis = setOf(activeMidi)
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
                    currentMidis = emptySet()
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
                        currentMidis = setOf(activeMidi)
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
                        currentMidis = emptySet()
                        attackVoteRing.clear()
                    }
                } else if (settleHops >= MAX_SETTLE_HOPS) {
                    state = State.IDLE
                    waitForQuietAfterReject = true
                    currentMidi = null
                    currentMidis = emptySet()
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
                        currentMidis = emptySet()
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
                    currentMidis = emptySet()
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
                            currentMidis = setOf(newMidi)
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

    private fun processPolyphonicHop(rms: Float, minRecentRms: Float, timestampMs: Long) {
        when (state) {
            State.IDLE -> {
                if (rms >= currentOnsetThreshold) {
                    state = State.ATTACK_SETTLING
                    pendingOnsetMs = timestampMs
                    settleHops = 1
                    hopsSinceOnset = 1
                    quietHops = 0
                    polyLeadReachedHop = -1
                    polyAttackVotes.fill(0)
                    polyAttackConfSums.fill(0f)
                    resetRmsHistory()
                }
            }

            State.ATTACK_SETTLING -> {
                settleHops++
                hopsSinceOnset++
                if (rms < currentOffsetThreshold * 0.7f && settleHops >= 6) {
                    state = State.IDLE
                    polyAttackVotes.fill(0)
                    polyAttackConfSums.fill(0f)
                    currentMidi = null
                    currentMidis = emptySet()
                    currentPitchEstimate = null
                } else if (settleHops >= POLY_SETTLE_SKIP_HOPS &&
                    (settleHops - POLY_SETTLE_SKIP_HOPS) % ATTACK_SAMPLE_HOP_INTERVAL == 0
                ) {
                    val detected = polyDetector.detectChords(polyWindow, maxNotes = 2)
                    for (est in detected) {
                        val m = est.midiNote
                        if (m in POLY_MIN_MIDI..POLY_MAX_MIDI) {
                            polyAttackVotes[m]++
                            polyAttackConfSums[m] += est.confidence
                        }
                    }
                    if (detected.isNotEmpty()) {
                        currentPitchEstimate = detected.last()
                    }

                    var anyReached3 = false
                    var readyCount2 = 0
                    for (m in POLY_MIN_MIDI..POLY_MAX_MIDI) {
                        val v = polyAttackVotes[m]
                        if (v >= ATTACK_CONFIRM_VOTES) anyReached3 = true
                        if (v >= 2) readyCount2++
                    }
                    if (anyReached3 && polyLeadReachedHop < 0) {
                        polyLeadReachedHop = settleHops
                    }

                    val shouldCommit = (anyReached3 && readyCount2 >= 2) ||
                        (polyLeadReachedHop >= 0 && settleHops >= polyLeadReachedHop + POLY_STAGGER_WAIT_HOPS) ||
                        (settleHops >= MAX_SETTLE_HOPS && readyCount2 >= 1)

                    if (shouldCommit) {
                        commitPolyAttack(pendingOnsetMs)
                    } else if (settleHops >= MAX_SETTLE_HOPS) {
                        state = State.IDLE
                        polyAttackVotes.fill(0)
                        polyAttackConfSums.fill(0f)
                    }
                } else if (settleHops >= MAX_SETTLE_HOPS) {
                    var readyCount2 = 0
                    for (m in POLY_MIN_MIDI..POLY_MAX_MIDI) {
                        if (polyAttackVotes[m] >= 2) readyCount2++
                    }
                    if (readyCount2 >= 1) {
                        commitPolyAttack(pendingOnsetMs)
                    } else {
                        state = State.IDLE
                        polyAttackVotes.fill(0)
                        polyAttackConfSums.fill(0f)
                    }
                }
            }

            State.SUSTAINING -> {
                hopsSinceOnset++

                if (rms < currentOffsetThreshold) {
                    quietHops++
                    if (quietHops >= QUIET_HOPS_TO_RELEASE) {
                        val toRelease = activePolyMidis.sorted()
                        activePolyMidis.clear()
                        polyConfirmCounts.fill(0)
                        polyAbsentCounts.fill(0)
                        polyReattackScanRemaining = 0
                        currentMidi = null
                        currentMidis = emptySet()
                        currentPitchEstimate = null
                        state = State.IDLE
                        for (midi in toRelease) {
                            onNoteOff(midi, timestampMs)
                        }
                        prevRms = rms
                        recordRms(rms)
                        return
                    }
                } else {
                    quietHops = 0
                }

                // Trigger brisk scan on new strike over sustained bass (without dropping sustained bass note)
                if (hopsSinceOnset >= MIN_NOTE_LENGTH_HOPS &&
                    polyReattackScanRemaining == 0 &&
                    isMultiHopReattack(rms, minRecentRms)
                ) {
                    polyReattackScanRemaining = POLY_REATTACK_SCAN_HOPS
                    polyReattackNewNoteDetected = false
                    pendingOnsetMs = timestampMs
                    hopsSinceOnset = 0
                    resetRmsHistory()
                }

                val isReattackTick = polyReattackScanRemaining > 0 && (polyReattackScanRemaining % 2 == 0)
                if (polyReattackScanRemaining > 0) {
                    polyReattackScanRemaining--
                }

                polySampleCounter++
                val isRegularTick = polySampleCounter >= LIVE_PITCH_HOP_INTERVAL
                if (isRegularTick) {
                    polySampleCounter = 0
                }

                if ((isRegularTick || isReattackTick) && rms >= currentOffsetThreshold) {
                    val detected = polyDetector.detectChords(polyWindow, maxNotes = 2)
                    val requiredConfirm = if (polyReattackScanRemaining > 0 || isReattackTick) 2 else POLY_SUSTAIN_CONFIRM_COUNT
                    var updated = false

                    for (m in POLY_MIN_MIDI..POLY_MAX_MIDI) {
                        val isDetected = detected.any { it.midiNote == m }
                        if (isDetected) {
                            polyAbsentCounts[m] = 0
                            polyConfirmCounts[m]++
                            if (m !in activePolyMidis && polyConfirmCounts[m] >= requiredConfirm) {
                                activePolyMidis.add(m)
                                polyReattackNewNoteDetected = true
                                updated = true
                                val onsetTs = if (polyReattackScanRemaining > 0) pendingOnsetMs else max(0L, timestampMs - 30L)
                                onNoteOn(m, onsetTs)
                            }
                        } else {
                            polyConfirmCounts[m] = 0
                            if (m in activePolyMidis) {
                                polyAbsentCounts[m]++
                                if (polyAbsentCounts[m] >= POLY_SUSTAIN_ABSENT_COUNT) {
                                    activePolyMidis.remove(m)
                                    updated = true
                                    onNoteOff(m, timestampMs)
                                }
                            }
                        }
                    }

                    // If an amplitude re-attack finished scanning with no new pitch (same melody note re-struck),
                    // re-trigger the upper active voice without disturbing any held lower bass voice.
                    if (isReattackTick && polyReattackScanRemaining == 0 && !polyReattackNewNoteDetected) {
                        val repeatedUpperMidi = activePolyMidis.maxOrNull()
                        if (repeatedUpperMidi != null) {
                            onNoteOff(repeatedUpperMidi, timestampMs)
                            onNoteOn(repeatedUpperMidi, pendingOnsetMs)
                            updated = true
                        }
                    }

                    if (updated || detected.isNotEmpty()) {
                        currentMidis = activePolyMidis.toSet()
                        currentMidi = activePolyMidis.maxOrNull() ?: detected.lastOrNull()?.midiNote
                        if (detected.isNotEmpty()) {
                            currentPitchEstimate = detected.last()
                        }
                    }
                }
            }
        }

        prevRms = rms
        recordRms(rms)
    }

    private fun commitPolyAttack(onsetMs: Long) {
        var best1 = -1
        var best1Score = 0f
        var best2 = -1
        var best2Score = 0f

        for (m in POLY_MIN_MIDI..POLY_MAX_MIDI) {
            val v = polyAttackVotes[m]
            if (v >= 2) {
                val score = v * 10f + (polyAttackConfSums[m] / v)
                if (score > best1Score) {
                    best2 = best1
                    best2Score = best1Score
                    best1 = m
                    best1Score = score
                } else if (score > best2Score) {
                    best2 = m
                    best2Score = score
                }
            }
        }

        val committed = mutableListOf<Int>()
        if (best1 >= 0) committed.add(best1)
        if (best2 >= 0) committed.add(best2)
        committed.sort()

        activePolyMidis.clear()
        polyConfirmCounts.fill(0)
        polyAbsentCounts.fill(0)
        polyAttackVotes.fill(0)
        polyAttackConfSums.fill(0f)
        polySampleCounter = 0
        polyReattackScanRemaining = 0

        for (m in committed) {
            activePolyMidis.add(m)
            polyConfirmCounts[m] = POLY_SUSTAIN_CONFIRM_COUNT
        }
        currentMidis = activePolyMidis.toSet()
        currentMidi = committed.lastOrNull()
        currentPitchEstimate = committed.lastOrNull()?.let { midi ->
            PitchEstimate(
                frequencyHz = dev.lofrancolabs.pianopiano.model.MidiUtils.midiToFrequency(midi).toFloat(),
                midiNote = midi,
                confidence = 0.88f
            )
        }
        state = State.SUSTAINING
        hopsSinceOnset = 0
        quietHops = 0
        resetRmsHistory()

        for (m in committed) {
            onNoteOn(m, onsetMs)
        }
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

        private const val POLY_MIN_MIDI = 48
        private const val POLY_MAX_MIDI = 72
        private const val POLY_SETTLE_SKIP_HOPS = 10
        private const val POLY_STAGGER_WAIT_HOPS = 4
        private const val POLY_SUSTAIN_CONFIRM_COUNT = 3
        private const val POLY_SUSTAIN_ABSENT_COUNT = 4
        private const val POLY_REATTACK_SCAN_HOPS = 6
    }
}
