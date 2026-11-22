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
import dev.lofrancolabs.pianopiano.model.MicSensitivity
import dev.lofrancolabs.pianopiano.model.MicSourceMode
import dev.lofrancolabs.pianopiano.model.MidiUtils
import dev.lofrancolabs.pianopiano.model.NoteEvaluation
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem
import dev.lofrancolabs.pianopiano.model.SessionPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val audioMonitor: AudioMonitorSnapshot = AudioMonitorSnapshot(),
    val recentDetectedNotes: List<DetectedNoteEvent> = emptyList(),
    val previewActiveMidi: Int? = null,
    val expectedTargetMidi: Int? = selectedExercise.notes.firstOrNull { !it.isRest }?.midiNote,
    val calibratedNoiseFloor: Float = 0.002f,
    val statusMessage: String? = null,
    val evaluations: List<NoteEvaluation> = emptyList(),
    val summary: ExerciseSummary? = null
)

class PianoPianoViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application.applicationContext)
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<PianoPianoUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): PianoPianoUiState {
        val naming = prefs.loadNoteNaming()
        val difficulty = prefs.loadRandomDifficulty()
        val sensitivity = prefs.loadMicSensitivity()
        val sourceMode = prefs.loadMicSourceMode()
        val progress = prefs.loadProgress()
        val initialExercise = ExerciseGenerator.generate(
            difficulty = difficulty,
            sequenceNumber = prefs.nextRandomSequence()
        )
        return PianoPianoUiState(
            selectedExercise = initialExercise,
            bpm = initialExercise.defaultBpm,
            noteNaming = naming,
            randomDifficulty = difficulty,
            micSensitivity = sensitivity,
            micSourceMode = sourceMode,
            playerProgress = progress,
            expectedTargetMidi = initialExercise.notes.firstOrNull { !it.isRest }?.midiNote
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
        stopActiveSession(keepSummary = false)
        captureEngine.stop()
        synthesizer.allNotesOff()
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
        stopActiveSession(keepSummary = false)
        prefs.saveRandomDifficulty(difficulty)
        val generated = ExerciseGenerator.generate(
            difficulty = difficulty,
            sequenceNumber = prefs.nextRandomSequence()
        )
        _uiState.update {
            it.copy(
                randomDifficulty = difficulty,
                selectedExercise = generated,
                bpm = generated.defaultBpm,
                beatPosition = 0f,
                evaluations = emptyList(),
                expectedTargetMidi = generated.notes.firstOrNull { n -> !n.isRest }?.midiNote,
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
        selectDifficulty(_uiState.value.randomDifficulty, autoStart = autoStart)
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
        stopActiveSession(keepSummary = false)
        captureEngine.stop() // Avoid mic picking up the preview audio

        val exercise = state.selectedExercise
        val bpm = state.bpm
        val msPerBeat = 60_000.0 / bpm
        val leadInBeats = 2f
        val startMs = nowMs() + (leadInBeats * msPerBeat).roundToLong()

        _uiState.update {
            it.copy(
                phase = SessionPhase.PREVIEWING,
                beatPosition = -leadInBeats,
                evaluations = emptyList(),
                summary = null,
                statusMessage = "Listening to ${exercise.title}..."
            )
        }

        sessionJob = viewModelScope.launch {
            val activeNoteIndices = mutableSetOf<Int>()
            var lastBeatFloor = Int.MIN_VALUE

            try {
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

                    var currentTarget: Int? = null
                    exercise.notes.forEachIndexed { idx, note ->
                        val midi = note.midiNote ?: return@forEachIndexed
                        val noteEnd = note.startBeat + note.durationBeats
                        if (beatPos >= note.startBeat && beatPos < noteEnd) {
                            currentTarget = midi
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
                            previewActiveMidi = currentTarget,
                            expectedTargetMidi = currentTarget
                        )
                    }

                    if (beatPos >= exercise.totalBeats + 0.4f) {
                        break
                    }
                    delay(16L)
                }
            } finally {
                synthesizer.allNotesOff()
                _uiState.update {
                    it.copy(
                        phase = SessionPhase.IDLE,
                        beatPosition = 0f,
                        beatFlashAlpha = 0f,
                        previewActiveMidi = null,
                        expectedTargetMidi = exercise.notes.firstOrNull { n -> !n.isRest }?.midiNote,
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
        if (!captureEngine.start()) {
            _uiState.update { it.copy(statusMessage = "Could not open microphone.") }
            return
        }

        stopActiveSession(keepSummary = false)

        val exercise = state.selectedExercise
        val bpm = state.bpm
        val msPerBeat = 60_000.0 / bpm
        val countInBeats = 4
        val countInStartMs = nowMs()
        val exerciseStartMs = countInStartMs + (countInBeats * msPerBeat).roundToLong()

        val judge = ExerciseJudge(exercise = exercise, bpm = bpm, exerciseStartMs = exerciseStartMs)
        activeJudge = judge
        val acceptWindowStart = exerciseStartMs - maxOf(250L, (0.45 * msPerBeat).roundToLong())
        judgeAcceptWindowStartMs = acceptWindowStart

        val firstNoteLabel = exercise.notes.firstOrNull { n -> !n.isRest }?.midiNote
            ?.let { m -> MidiUtils.midiToName(m, state.noteNaming) } ?: "first note"
        _uiState.update {
            it.copy(
                phase = SessionPhase.COUNT_IN,
                beatPosition = -countInBeats.toFloat(),
                countInBeat = 1,
                evaluations = judge.snapshot(),
                summary = null,
                lastXpReward = null,
                expectedTargetMidi = exercise.notes.firstOrNull { n -> !n.isRest }?.midiNote,
                statusMessage = "Count-in: get ready on $firstNoteLabel"
            )
        }

        sessionJob = viewModelScope.launch {
            var lastCountInBeatTriggered = 0
            var hasResetBeforeAcceptWindow = false
            val exerciseEndMs = exerciseStartMs + (exercise.totalBeats * msPerBeat).roundToLong() + 450L

            try {
                while (isActive) {
                    val now = nowMs()
                    if (!hasResetBeforeAcceptWindow && now >= acceptWindowStart - 60L) {
                        hasResetBeforeAcceptWindow = true
                        captureEngine.resetTracker()
                    }
                    val beatPos = ((now - exerciseStartMs) / msPerBeat).toFloat()
                    val phase = if (now < exerciseStartMs) SessionPhase.COUNT_IN else SessionPhase.PLAYING

                    if (phase == SessionPhase.COUNT_IN) {
                        val elapsedCountInBeats = ((now - countInStartMs) / msPerBeat).toInt() + 1
                        val clampedCountIn = elapsedCountInBeats.coerceIn(1, countInBeats)
                        if (clampedCountIn > lastCountInBeatTriggered) {
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

                        _uiState.update {
                            it.copy(
                                phase = SessionPhase.PLAYING,
                                beatPosition = beatPos,
                                countInBeat = 0,
                                beatFlashAlpha = flash,
                                evaluations = judge.snapshot(),
                                expectedTargetMidi = judge.currentTargetMidi(now),
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
                if (_uiState.value.phase == SessionPhase.COUNT_IN || _uiState.value.phase == SessionPhase.PLAYING) {
                    activeJudge = null
                    judgeAcceptWindowStartMs = Long.MAX_VALUE
                }
            }
        }
    }

    fun stopActiveSession(keepSummary: Boolean = false) {
        sessionJob?.cancel()
        sessionJob = null
        activeJudge = null
        judgeAcceptWindowStartMs = Long.MAX_VALUE
        synthesizer.allNotesOff()
        _uiState.update { state ->
            state.copy(
                phase = SessionPhase.IDLE,
                beatPosition = 0f,
                countInBeat = 0,
                beatFlashAlpha = 0f,
                previewActiveMidi = null,
                expectedTargetMidi = state.selectedExercise.notes.firstOrNull { n -> !n.isRest }?.midiNote,
                summary = if (keepSummary) state.summary else null
            )
        }
        val updated = _uiState.value
        if (updated.hasMicPermission) {
            captureEngine.start()
        }
    }

    fun dismissSummary() {
        _uiState.update {
            it.copy(
                phase = SessionPhase.IDLE,
                beatPosition = 0f,
                summary = null,
                lastXpReward = null,
                evaluations = emptyList(),
                expectedTargetMidi = it.selectedExercise.notes.firstOrNull { n -> !n.isRest }?.midiNote
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
                audioMonitor = snapshot
            )
        }
    }

    private fun nowMs(): Long = SystemClock.elapsedRealtimeNanos() / 1_000_000L

    companion object {
        const val MIN_BPM = 50
        const val MAX_BPM = 120
    }
}
