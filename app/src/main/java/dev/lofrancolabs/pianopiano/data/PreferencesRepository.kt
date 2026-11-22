package dev.lofrancolabs.pianopiano.data

import android.content.Context
import androidx.core.content.edit
import dev.lofrancolabs.pianopiano.domain.PlayerProgress
import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem
import dev.lofrancolabs.pianopiano.model.PracticeMode

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

    fun loadPracticeMode(): PracticeMode {
        val raw = prefs.getString(KEY_PRACTICE_MODE, PracticeMode.GUIDED.name)
        return runCatching { PracticeMode.valueOf(raw ?: PracticeMode.GUIDED.name) }
            .getOrDefault(PracticeMode.GUIDED)
    }

    fun savePracticeMode(mode: PracticeMode) {
        prefs.edit { putString(KEY_PRACTICE_MODE, mode.name) }
    }

    fun loadRandomDifficulty(): BeginnerDifficulty {
        val raw = prefs.getString(KEY_RANDOM_DIFFICULTY, BeginnerDifficulty.BEGINNER.name)
        return runCatching { BeginnerDifficulty.valueOf(raw ?: BeginnerDifficulty.BEGINNER.name) }
            .getOrDefault(BeginnerDifficulty.BEGINNER)
    }

    fun saveRandomDifficulty(difficulty: BeginnerDifficulty) {
        prefs.edit { putString(KEY_RANDOM_DIFFICULTY, difficulty.name) }
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

    companion object {
        private const val PREFS_NAME = "piano_piano_prefs"
        private const val KEY_NOTE_NAMING = "note_naming"
        private const val KEY_PRACTICE_MODE = "practice_mode"
        private const val KEY_RANDOM_DIFFICULTY = "random_difficulty"
        private const val KEY_TOTAL_XP = "total_xp"
        private const val KEY_EXERCISES_COMPLETED = "exercises_completed"
        private const val KEY_RANDOM_SEQ = "random_seq"
    }
}
