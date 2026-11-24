package dev.lofrancolabs.pianopiano.data

import android.content.Context
import androidx.core.content.edit
import dev.lofrancolabs.pianopiano.domain.PlayerProgress
import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.HandMode
import dev.lofrancolabs.pianopiano.model.MicSensitivity
import dev.lofrancolabs.pianopiano.model.MicSourceMode
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem

class PreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadNoteNaming(): NoteNamingSystem {
        val raw = prefs.getString(KEY_NOTE_NAMING, NoteNamingSystem.ENGLISH.name)
        return runCatching { NoteNamingSystem.valueOf(raw ?: NoteNamingSystem.ENGLISH.name) }
            .getOrDefault(NoteNamingSystem.ENGLISH)
    }

    fun saveNoteNaming(naming: NoteNamingSystem) {
        prefs.edit { putString(KEY_NOTE_NAMING, naming.name) }
    }

    fun loadRandomDifficulty(): BeginnerDifficulty {
        val raw = prefs.getString(KEY_RANDOM_DIFFICULTY, BeginnerDifficulty.BEGINNER.name)
        return runCatching { BeginnerDifficulty.valueOf(raw ?: BeginnerDifficulty.BEGINNER.name) }
            .getOrDefault(BeginnerDifficulty.BEGINNER)
    }

    fun saveRandomDifficulty(difficulty: BeginnerDifficulty) {
        prefs.edit { putString(KEY_RANDOM_DIFFICULTY, difficulty.name) }
    }

    fun loadHandMode(): HandMode {
        val raw = prefs.getString(KEY_HAND_MODE, HandMode.RIGHT_HAND.name)
        return runCatching { HandMode.valueOf(raw ?: HandMode.RIGHT_HAND.name) }
            .getOrDefault(HandMode.RIGHT_HAND)
    }

    fun saveHandMode(mode: HandMode) {
        prefs.edit { putString(KEY_HAND_MODE, mode.name) }
    }

    fun loadProgress(): PlayerProgress {
        return PlayerProgress(
            totalXp = prefs.getInt(KEY_TOTAL_XP, 0).coerceAtLeast(0),
            exercisesCompleted = prefs.getInt(KEY_EXERCISES_COMPLETED, 0).coerceAtLeast(0)
        )
    }

    fun saveProgress(progress: PlayerProgress) {
        prefs.edit {
            putInt(KEY_TOTAL_XP, progress.totalXp)
            putInt(KEY_EXERCISES_COMPLETED, progress.exercisesCompleted)
        }
    }

    fun nextRandomSequence(): Int {
        val current = prefs.getInt(KEY_RANDOM_SEQ, 1).coerceAtLeast(1)
        prefs.edit { putInt(KEY_RANDOM_SEQ, current + 1) }
        return current
    }

    fun loadMicSensitivity(): MicSensitivity {
        val raw = prefs.getString(KEY_MIC_SENSITIVITY, MicSensitivity.NORMAL.name)
        return runCatching { MicSensitivity.valueOf(raw ?: MicSensitivity.NORMAL.name) }
            .getOrDefault(MicSensitivity.NORMAL)
    }

    fun saveMicSensitivity(sensitivity: MicSensitivity) {
        prefs.edit { putString(KEY_MIC_SENSITIVITY, sensitivity.name) }
    }

    fun loadMicSourceMode(): MicSourceMode {
        val raw = prefs.getString(KEY_MIC_SOURCE_MODE, MicSourceMode.RAW.name)
        return runCatching { MicSourceMode.valueOf(raw ?: MicSourceMode.RAW.name) }
            .getOrDefault(MicSourceMode.RAW)
    }

    fun saveMicSourceMode(mode: MicSourceMode) {
        prefs.edit { putString(KEY_MIC_SOURCE_MODE, mode.name) }
    }

    companion object {
        private const val PREFS_NAME = "piano_piano_prefs"
        private const val KEY_NOTE_NAMING = "note_naming"
        private const val KEY_RANDOM_DIFFICULTY = "random_difficulty"
        private const val KEY_HAND_MODE = "hand_mode"
        private const val KEY_TOTAL_XP = "total_xp"
        private const val KEY_EXERCISES_COMPLETED = "exercises_completed"
        private const val KEY_RANDOM_SEQ = "random_seq"
        private const val KEY_MIC_SENSITIVITY = "mic_sensitivity"
        private const val KEY_MIC_SOURCE_MODE = "mic_source_mode"
    }
}
