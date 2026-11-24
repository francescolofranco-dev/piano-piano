package dev.lofrancolabs.pianopiano.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lofrancolabs.pianopiano.audio.AudioCaptureEngine
import dev.lofrancolabs.pianopiano.audio.AudioMonitorSnapshot
import dev.lofrancolabs.pianopiano.audio.PianoSynthesizer
import dev.lofrancolabs.pianopiano.data.PreferencesRepository
import dev.lofrancolabs.pianopiano.domain.ExerciseGenerator
import dev.lofrancolabs.pianopiano.domain.ExerciseJudge
import dev.lofrancolabs.pianopiano.domain.PlayerProgress
import dev.lofrancolabs.pianopiano.domain.ProgressTracker
import dev.lofrancolabs.pianopiano.domain.XpReward
import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.ExerciseSummary
import dev.lofrancolabs.pianopiano.model.HandMode
import dev.lofrancolabs.pianopiano.model.MicSensitivity
import dev.lofrancolabs.pianopiano.model.MicSourceMode
import dev.lofrancolabs.pianopiano.model.MidiUtils
import dev.lofrancolabs.pianopiano.model.NoteEvaluation
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem
import dev.lofrancolabs.pianopiano.model.PianoHand
import dev.lofrancolabs.pianopiano.model.SessionPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToLong

data class DetectedNoteEvent(
    val midi: Int,
    val timestampMs: Long
)

data class PianoPianoUiState(
    val selectedExercise: Exercise,
    val bpm: Int = selectedExercise.defaultBpm,
    val noteNaming: NoteNamingSystem = NoteNamingSystem.ENGLISH,
    val randomDifficulty: BeginnerDifficulty = BeginnerDifficulty.BEGINNER,
    val handMode: HandMode = HandMode.RIGHT_HAND,
    val micSensitivity: MicSensitivity = MicSensitivity.NORMAL,
    val micSourceMode: MicSourceMode = MicSourceMode.RAW,
    val playerProgress: PlayerProgress = PlayerProgress(),
    val lastXpReward: XpReward? = null,
    val hasMicPermission: Boolean = false,
    val phase: SessionPhase = SessionPhase.IDLE,
    val beatPosition: Float = 0f,
    val countInBeat: Int = 0,
    val beatFlashAlpha: Float = 0f,
    val micLevel: Float = 0f,
    val heardMidi: Int? = null,
    val heardMidis: Set<Int> = emptySet(),
    val audioMonitor: AudioMonitorSnapshot = AudioMonitorSnapshot(),
    val recentDetectedNotes: List<DetectedNoteEvent> = emptyList(),
    val previewActiveMidi: Int? = null,
    val previewActiveMidis: Set<Int> = emptySet(),
    val expectedTargetMidi: Int? = selectedExercise.notes.firstOrNull { !it.isRest }?.midiNote,
    val expectedRightMidi: Int? = selectedExercise.notes.firstOrNull { !it.isRest && it.hand == PianoHand.RIGHT }?.midiNote,
    val expectedLeftMidi: Int? = selectedExercise.notes.firstOrNull { !it.isRest && it.hand == PianoHand.LEFT }?.midiNote,
    val calibratedNoiseFloor: Float = 0.002f,
    val statusMessage: String? = null,
    val evaluations: List<NoteEvaluation> = emptyList(),
    val summary: ExerciseSummary? = null
)

class PianoPianoViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application.applicationContext)
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<PianoPianoUiState> = _uiState.asStateFlow()

    private fun initialExpectedTargets(exercise: Exercise): Pair<Int?, Int?> {
        val firstPlayable = exercise.notes.firstOrNull { !it.isRest }
        val lookaheadEnd = (firstPlayable?.startBeat ?: 0f) + exercise.beatsPerBar
        val firstRight = exercise.notes.firstOrNull {
            !it.isRest && it.hand == PianoHand.RIGHT && it.startBeat < lookaheadEnd
        }?.midiNote
        val firstLeft = exercise.notes.firstOrNull {
            !it.isRest && it.hand == PianoHand.LEFT && it.startBeat < lookaheadEnd
        }?.midiNote
        return firstRight to firstLeft
    }

    private fun loadInitialState(): PianoPianoUiState {
        val naming = prefs.loadNoteNaming()
        val difficulty = prefs.loadRandomDifficulty()
        val handMode = prefs.loadHandMode()
        val sensitivity = prefs.loadMicSensitivity()
        val sourceMode = prefs.loadMicSourceMode()
        val progress = prefs.loadProgress()
        val initialExercise = ExerciseGenerator.generate(
            difficulty = difficulty,
            handMode = handMode,
            sequenceNumber = prefs.nextRandomSequence()
        )
        val (firstRight, firstLeft) = initialExpectedTargets(initialExercise)
        return PianoPianoUiState(
            selectedExercise = initialExercise,
            bpm = initialExercise.defaultBpm,
            noteNaming = naming,
            randomDifficulty = difficulty,
            handMode = handMode,
            micSensitivity = sensitivity,
            micSourceMode = sourceMode,
            playerProgress = progress,
            expectedTargetMidi = firstRight ?: firstLeft,
            expectedRightMidi = firstRight,
            expectedLeftMidi = firstLeft
        )
    }

    private val synthesizer = PianoSynthesizer()
    private val captureEngine = AudioCaptureEngine(
        context = application.applicationContext,
        onNoteOn = { midi, timestampMs -> handleMicNoteOn(midi, timestampMs) },
        onNoteOff = { midi, timestampMs -> handleMicNoteOff(midi, timestampMs) },
        onMonitorUpdate = { snapshot -> handleMicMonitor(snapshot) }
    )

    init {
        captureEngine.setSensitivity(_uiState.value.micSensitivity)
        captureEngine.setSourceMode(_uiState.value.micSourceMode)
        captureEngine.setPolyphonicMode(_uiState.value.selectedExercise.hasSimultaneousNotes)
    }

    @Volatile
    private var activeJudge: ExerciseJudge? = null

    @Volatile
    private var judgeAcceptWindowStartMs: Long = Long.MAX_VALUE

    private var sessionJob: Job? = null

    fun onPermissionChanged(granted: Boolean) {
        val current = _uiState.value
        _uiState.update { it.copy(hasMicPermission = granted) }
        if (granted && current.phase != SessionPhase.PREVIEWING) {
            captureEngine.start()
        } else if (!granted) {
            captureEngine.stop()
        }
    }

    fun onResume() {
        val state = _uiState.value
        if (state.hasMicPermission && state.phase != SessionPhase.PREVIEWING) {
            captureEngine.start()
        }
    }

    fun onPause() {
        stopActiveSession(keepSummary = false, restartCapture = false)
        captureEngine.stop()
        synthesizer.stop()
    }

    override fun onCleared() {
        sessionJob?.cancel()
        captureEngine.stop()
        synthesizer.release()
        super.onCleared()
    }

    fun setNoteNaming(naming: NoteNamingSystem) {
        prefs.saveNoteNaming(naming)
        _uiState.update { it.copy(noteNaming = naming) }
    }

    fun selectDifficulty(difficulty: BeginnerDifficulty, autoStart: Boolean = false) {
        val targetHandMode = if (difficulty == BeginnerDifficulty.ADVANCED &&
            _uiState.value.handMode == HandMode.RIGHT_HAND
        ) {
            prefs.saveHandMode(HandMode.BOTH_TOGETHER)
            HandMode.BOTH_TOGETHER
        } else {
            _uiState.value.handMode
        }
        prefs.saveRandomDifficulty(difficulty)
        generateAndApplyExercise(difficulty, targetHandMode, autoStart)
    }

    fun selectHandMode(mode: HandMode, autoStart: Boolean = false) {
        prefs.saveHandMode(mode)
        generateAndApplyExercise(_uiState.value.randomDifficulty, mode, autoStart)
    }

    private fun generateAndApplyExercise(
        difficulty: BeginnerDifficulty,
        handMode: HandMode,
        autoStart: Boolean
    ) {
        stopActiveSession(keepSummary = false)
        val generated = ExerciseGenerator.generate(
            difficulty = difficulty,
            handMode = handMode,
            sequenceNumber = prefs.nextRandomSequence()
        )
        captureEngine.setPolyphonicMode(generated.hasSimultaneousNotes)
        val (firstRight, firstLeft) = initialExpectedTargets(generated)
        _uiState.update {
            it.copy(
                randomDifficulty = difficulty,
                handMode = handMode,
                selectedExercise = generated,
                bpm = generated.defaultBpm,
                beatPosition = 0f,
                evaluations = emptyList(),
                expectedTargetMidi = firstRight ?: firstLeft,
                expectedRightMidi = firstRight,
                expectedLeftMidi = firstLeft,
                summary = null,
                lastXpReward = null,
                statusMessage = null
            )
        }
        if (autoStart) {
            startPracticeIfReady()
        }
    }

    fun nextExercise(autoStart: Boolean = true) {
        val state = _uiState.value
        generateAndApplyExercise(state.randomDifficulty, state.handMode, autoStart)
    }

    private fun startPracticeIfReady() {
        val state = _uiState.value
        if (state.hasMicPermission) {
            startPractice()
        } else {
            _uiState.update {
                it.copy(statusMessage = "Loaded ${state.selectedExercise.title}. Allow Mic to practice.")
            }
        }
    }

    fun adjustBpm(delta: Int) {
        if (_uiState.value.phase == SessionPhase.COUNT_IN || _uiState.value.phase == SessionPhase.PLAYING) {
            return
        }
        _uiState.update {
            it.copy(bpm = (it.bpm + delta).coerceIn(MIN_BPM, MAX_BPM))
        }
    }

    fun setMicSensitivity(sensitivity: MicSensitivity) {
        prefs.saveMicSensitivity(sensitivity)
        captureEngine.setSensitivity(sensitivity)
        _uiState.update { it.copy(micSensitivity = sensitivity) }
    }

    fun setMicSourceMode(mode: MicSourceMode) {
        prefs.saveMicSourceMode(mode)
        captureEngine.setSourceMode(mode)
        _uiState.update { it.copy(micSourceMode = mode) }
    }

    fun clearRecentDetectedNotes() {
        _uiState.update { it.copy(recentDetectedNotes = emptyList()) }
    }

    fun startCalibration() {
        if (!_uiState.value.hasMicPermission) {
            _uiState.update { it.copy(statusMessage = "Grant microphone permission to calibrate room noise.") }
            return
        }
        stopActiveSession(keepSummary = false)
        _uiState.update {
            it.copy(
                phase = SessionPhase.CALIBRATING,
                statusMessage = "Keep quiet for 1.5s to measure room noise..."
            )
        }
        captureEngine.startCalibration { floor ->
            _uiState.update { state ->
                val nextPhase = if (state.phase == SessionPhase.CALIBRATING) SessionPhase.IDLE else state.phase
                state.copy(
                    phase = nextPhase,
                    calibratedNoiseFloor = floor,
                    statusMessage = "Calibrated room noise floor (${(floor * 1000).toInt()} mV)."
                )
            }
        }
    }

    fun startPreview() {
        val state = _uiState.value
        if (state.phase == SessionPhase.PREVIEWING) {
            stopActiveSession(keepSummary = false)
            return
        }
        stopActiveSession(keepSummary = false, restartCapture = false)

        val exercise = state.selectedExercise
        val bpm = state.bpm
        val msPerBeat = 60_000.0 / bpm
        val leadInBeats = 2f

        _uiState.update {
            it.copy(
                phase = SessionPhase.PREVIEWING,
                beatPosition = -leadInBeats,
                countInBeat = 0,
                evaluations = emptyList(),
                summary = null,
                statusMessage = "Listening to ${exercise.title}..."
            )
        }

        sessionJob = viewModelScope.launch {
            val activeNoteIndices = mutableSetOf<Int>()
            var lastBeatFloor = Int.MIN_VALUE

            try {
                withContext(Dispatchers.IO) {
                    captureEngine.stop() // Avoid mic picking up the preview audio
                    synthesizer.ensureStarted()
                }
                delay(120L)
                if (!isActive) return@launch
                val startMs = nowMs() + (leadInBeats * msPerBeat).roundToLong()

                while (isActive) {
                    val now = nowMs()
                    val beatPos = ((now - startMs) / msPerBeat).toFloat()
                    val beatFloor = floor(beatPos).toInt()
                    if (beatFloor > lastBeatFloor && beatFloor < exercise.totalBeats.toInt()) {
                        lastBeatFloor = beatFloor
                        if (beatPos < 0f) {
                            synthesizer.triggerMetronome(accent = beatFloor == -2)
                        }
                    }

                    val activeMidisThisTick = mutableSetOf<Int>()
                    var previewRight: Int? = null
                    var previewLeft: Int? = null
                    exercise.notes.forEachIndexed { idx, note ->
                        val midi = note.midiNote ?: return@forEachIndexed
                        val noteEnd = note.startBeat + note.durationBeats
                        if (beatPos >= note.startBeat && beatPos < noteEnd) {
                            activeMidisThisTick.add(midi)
                            if (note.hand == PianoHand.RIGHT) previewRight = midi else previewLeft = midi
                            if (activeNoteIndices.add(idx)) {
                                synthesizer.noteOn(midi)
                            }
                        } else if (activeNoteIndices.remove(idx)) {
                            synthesizer.noteOff(midi)
                        }
                    }

                    val beatFraction = (beatPos - floor(beatPos)).coerceIn(0f, 1f)
                    val flash = if (beatFraction < 0.25f) 1f - (beatFraction / 0.25f) else 0f

                    _uiState.update {
                        it.copy(
                            beatPosition = beatPos,
                            beatFlashAlpha = flash,
                            previewActiveMidi = previewRight ?: previewLeft,
                            previewActiveMidis = activeMidisThisTick.toSet(),
                            expectedTargetMidi = previewRight ?: previewLeft,
                            expectedRightMidi = previewRight,
                            expectedLeftMidi = previewLeft
                        )
                    }

                    if (beatPos >= exercise.totalBeats + 0.4f) {
                        break
                    }
                    delay(16L)
                }
            } finally {
                synthesizer.stop()
                val (firstRight, firstLeft) = initialExpectedTargets(exercise)
                _uiState.update {
                    it.copy(
                        phase = SessionPhase.IDLE,
                        beatPosition = 0f,
                        beatFlashAlpha = 0f,
                        previewActiveMidi = null,
                        previewActiveMidis = emptySet(),
                        expectedTargetMidi = firstRight ?: firstLeft,
                        expectedRightMidi = firstRight,
                        expectedLeftMidi = firstLeft,
                        statusMessage = null
                    )
                }
                val updated = _uiState.value
                if (updated.hasMicPermission) {
                    captureEngine.start()
                }
            }
        }
    }

    fun startPractice() {
        val state = _uiState.value
        if (state.phase == SessionPhase.COUNT_IN || state.phase == SessionPhase.PLAYING) {
            stopActiveSession(keepSummary = false)
            return
        }

        if (!state.hasMicPermission) {
            _uiState.update { it.copy(statusMessage = "Please allow microphone access to listen to your piano.") }
            return
        }

        stopActiveSession(keepSummary = false, restartCapture = false)

        val exercise = state.selectedExercise
        val bpm = state.bpm
        val msPerBeat = 60_000.0 / bpm
        val countInBeats = 4
        val (firstRight, firstLeft) = initialExpectedTargets(exercise)
        val firstNoteLabel = (firstRight ?: firstLeft)
            ?.let { m -> MidiUtils.midiToName(m, state.noteNaming) } ?: "first note"
        captureEngine.setPolyphonicMode(exercise.hasSimultaneousNotes)
        _uiState.update {
            it.copy(
                phase = SessionPhase.COUNT_IN,
                beatPosition = -countInBeats.toFloat(),
                countInBeat = 0,
                evaluations = emptyList(),
                summary = null,
                lastXpReward = null,
                expectedTargetMidi = firstRight ?: firstLeft,
                expectedRightMidi = firstRight,
                expectedLeftMidi = firstLeft,
                statusMessage = "Count-in: get ready on $firstNoteLabel"
            )
        }

        sessionJob = viewModelScope.launch {
            var lastCountInBeatTriggered = 0
            var hasSwitchedToCapture = false

            try {
                withContext(Dispatchers.IO) {
                    captureEngine.stop()
                    synthesizer.ensureStarted()
                }
                delay(120L)
                if (!isActive) return@launch

                val countInStartMs = nowMs()
                val exerciseStartMs = countInStartMs + (countInBeats * msPerBeat).roundToLong()
                val beat4ClearanceMs = minOf(240L, (msPerBeat * 0.42).roundToLong())
                val beat4ClickDoneMs = countInStartMs + ((countInBeats - 1) * msPerBeat).roundToLong() + beat4ClearanceMs

                val judge = ExerciseJudge(exercise = exercise, bpm = bpm, exerciseStartMs = exerciseStartMs)
                activeJudge = judge
                val acceptWindowStart = exerciseStartMs - maxOf(250L, (0.45 * msPerBeat).roundToLong())
                judgeAcceptWindowStartMs = acceptWindowStart
                val exerciseEndMs = exerciseStartMs + (exercise.totalBeats * msPerBeat).roundToLong() + 450L

                _uiState.update {
                    it.copy(
                        countInBeat = 1,
                        evaluations = judge.snapshot()
                    )
                }

                while (isActive) {
                    val now = nowMs()
                    val beatPos = ((now - exerciseStartMs) / msPerBeat).toFloat()
                    val phase = if (now < exerciseStartMs) SessionPhase.COUNT_IN else SessionPhase.PLAYING

                    if (!hasSwitchedToCapture &&
                        ((lastCountInBeatTriggered >= countInBeats && now >= beat4ClickDoneMs) || phase == SessionPhase.PLAYING)
                    ) {
                        hasSwitchedToCapture = true
                        val micStarted = withContext(Dispatchers.IO) {
                            synthesizer.stop()
                            captureEngine.start()
                        }
                        if (!isActive) break
                        if (!micStarted) {
                            _uiState.update { it.copy(statusMessage = "Could not open microphone.") }
                            break
                        }
                    }

                    if (phase == SessionPhase.COUNT_IN) {
                        val elapsedCountInBeats = ((now - countInStartMs) / msPerBeat).toInt() + 1
                        val clampedCountIn = elapsedCountInBeats.coerceIn(1, countInBeats)
                        if (!hasSwitchedToCapture && clampedCountIn > lastCountInBeatTriggered) {
                            lastCountInBeatTriggered = clampedCountIn
                            synthesizer.triggerMetronome(accent = clampedCountIn == 1)
                        }
                        val beatFrac = (beatPos - floor(beatPos)).coerceIn(0f, 1f)
                        val flash = if (beatFrac < 0.25f) 1f - (beatFrac / 0.25f) else 0f
                        _uiState.update {
                            it.copy(
                                phase = SessionPhase.COUNT_IN,
                                beatPosition = beatPos,
                                countInBeat = clampedCountIn,
                                beatFlashAlpha = flash,
                                evaluations = judge.snapshot()
                            )
                        }
                    } else {
                        judge.tick(now)
                        val beatFrac = (beatPos - floor(beatPos)).coerceIn(0f, 1f)
                        val flash = if (beatFrac < 0.25f) 1f - (beatFrac / 0.25f) else 0f
                        val (rightTarget, leftTarget) = judge.currentTargetMidis(now)

                        _uiState.update {
                            it.copy(
                                phase = SessionPhase.PLAYING,
                                beatPosition = beatPos,
                                countInBeat = 0,
                                beatFlashAlpha = flash,
                                evaluations = judge.snapshot(),
                                expectedTargetMidi = rightTarget ?: leftTarget,
                                expectedRightMidi = rightTarget,
                                expectedLeftMidi = leftTarget,
                                statusMessage = null
                            )
                        }
                    }

                    if (now >= exerciseEndMs) {
                        val summary = judge.finish(now)
                        val reward = ProgressTracker.calculateAndApplyReward(
                            current = _uiState.value.playerProgress,
                            summary = summary,
                            difficulty = exercise.difficulty
                        )
                        if (reward.xpEarned > 0) {
                            prefs.saveProgress(reward.updatedProgress)
                        }
                        activeJudge = null
                        judgeAcceptWindowStartMs = Long.MAX_VALUE
                        _uiState.update {
                            it.copy(
                                phase = SessionPhase.COMPLETED,
                                beatPosition = exercise.totalBeats,
                                beatFlashAlpha = 0f,
                                evaluations = summary.evaluations,
                                summary = summary,
                                playerProgress = reward.updatedProgress,
                                lastXpReward = reward,
                                statusMessage = null
                            )
                        }
                        break
                    }
                    delay(16L)
                }
            } finally {
                synthesizer.stop()
                val phaseAtExit = _uiState.value.phase
                if (phaseAtExit == SessionPhase.COUNT_IN || phaseAtExit == SessionPhase.PLAYING) {
                    activeJudge = null
                    judgeAcceptWindowStartMs = Long.MAX_VALUE
                }
                if (_uiState.value.hasMicPermission && phaseAtExit != SessionPhase.PREVIEWING) {
                    captureEngine.start()
                }
            }
        }
    }

    fun stopActiveSession(keepSummary: Boolean = false, restartCapture: Boolean = true) {
        sessionJob?.cancel()
        sessionJob = null
        activeJudge = null
        judgeAcceptWindowStartMs = Long.MAX_VALUE
        synthesizer.stop()
        _uiState.update { state ->
            val (firstRight, firstLeft) = initialExpectedTargets(state.selectedExercise)
            state.copy(
                phase = SessionPhase.IDLE,
                beatPosition = 0f,
                countInBeat = 0,
                beatFlashAlpha = 0f,
                previewActiveMidi = null,
                previewActiveMidis = emptySet(),
                expectedTargetMidi = firstRight ?: firstLeft,
                expectedRightMidi = firstRight,
                expectedLeftMidi = firstLeft,
                summary = if (keepSummary) state.summary else null
            )
        }
        val updated = _uiState.value
        if (restartCapture && updated.hasMicPermission) {
            captureEngine.start()
        }
    }

    fun dismissSummary() {
        _uiState.update { state ->
            val (firstRight, firstLeft) = initialExpectedTargets(state.selectedExercise)
            state.copy(
                phase = SessionPhase.IDLE,
                beatPosition = 0f,
                summary = null,
                lastXpReward = null,
                evaluations = emptyList(),
                expectedTargetMidi = firstRight ?: firstLeft,
                expectedRightMidi = firstRight,
                expectedLeftMidi = firstLeft
            )
        }
    }

    fun selectNextExerciseAndCloseSummary() {
        nextExercise(autoStart = true)
    }

    private fun handleMicNoteOn(midi: Int, timestampMs: Long) {
        _uiState.update { state ->
            val updatedEvents = listOf(DetectedNoteEvent(midi = midi, timestampMs = timestampMs)) +
                state.recentDetectedNotes.take(11)
            state.copy(recentDetectedNotes = updatedEvents)
        }
        if (timestampMs >= judgeAcceptWindowStartMs) {
            activeJudge?.let { judge ->
                judge.onNoteOn(midi, timestampMs)
                _uiState.update { it.copy(evaluations = judge.snapshot()) }
            }
        }
    }

    private fun handleMicNoteOff(midi: Int, timestampMs: Long) {
        activeJudge?.let { judge ->
            judge.onNoteOff(midi, timestampMs)
            _uiState.update { it.copy(evaluations = judge.snapshot()) }
        }
    }

    private fun handleMicMonitor(snapshot: AudioMonitorSnapshot) {
        val normalizedLevel = (snapshot.rms * 15f).coerceIn(0f, 1f)
        _uiState.update {
            it.copy(
                micLevel = normalizedLevel,
                heardMidi = snapshot.liveMidi,
                heardMidis = snapshot.liveMidis,
                audioMonitor = snapshot
            )
        }
    }

    private fun nowMs(): Long = SystemClock.elapsedRealtimeNanos() / 1_000_000L

    companion object {
        const val MIN_BPM = 40
        const val MAX_BPM = 120
    }
}
