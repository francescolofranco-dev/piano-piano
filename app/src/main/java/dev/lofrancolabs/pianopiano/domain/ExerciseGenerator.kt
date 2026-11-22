package dev.lofrancolabs.pianopiano.domain

import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.NoteEvent
import kotlin.math.abs
import kotlin.random.Random

object ExerciseGenerator {

    private data class ScaleNote(val midi: Int, val finger: Int)

    private val beginnerPool = listOf(
        ScaleNote(60, 1), // C4
        ScaleNote(62, 2), // D4
        ScaleNote(64, 3)  // E4
    )

    private val intermediatePool = listOf(
        ScaleNote(60, 1), // C4
        ScaleNote(62, 2), // D4
        ScaleNote(64, 3), // E4
        ScaleNote(65, 4), // F4
        ScaleNote(67, 5)  // G4
    )

    private val advancedBeginnerPool = listOf(
        ScaleNote(60, 1), // C4
        ScaleNote(62, 2), // D4
        ScaleNote(64, 3), // E4
        ScaleNote(65, 1), // F4
        ScaleNote(67, 2), // G4
        ScaleNote(69, 3), // A4
        ScaleNote(71, 4), // B4
        ScaleNote(72, 5)  // C5
    )

    // Negative duration represents a musical rest of abs(duration) beats; each bar sums to 4.0f
    private val beginnerBars = listOf(
        listOf(1f, 1f, 1f, 1f),
        listOf(1f, 1f, 2f),
        listOf(2f, 1f, 1f),
        listOf(1f, 1f, -1f, 1f),
        listOf(2f, 2f)
    )
    private val beginnerFinalBars = listOf(
        listOf(1f, 1f, 2f),
        listOf(2f, 2f)
    )

    private val intermediateBars = listOf(
        listOf(1f, 1f, 1f, 1f),
        listOf(1f, 1f, 2f),
        listOf(2f, 1f, 1f),
        listOf(1f, -1f, 1f, 1f),
        listOf(2f, -1f, 1f)
    )
    private val intermediateFinalBars = listOf(
        listOf(1f, 1f, 2f),
        listOf(4f)
    )

    private val advancedBars = listOf(
        listOf(1f, 1f, 1f, 1f),
        listOf(1.5f, 0.5f, 1f, 1f),
        listOf(1f, 0.5f, 0.5f, 2f),
        listOf(1f, 1f, 1.5f, 0.5f),
        listOf(1f, -1f, 1f, 1f),
        listOf(2f, 1f, 1f)
    )
    private val advancedFinalBars = listOf(
        listOf(1.5f, 0.5f, 2f),
        listOf(1f, 1f, 2f)
    )
    private val phraseEndingBars = listOf(
        listOf(1f, 1f, 2f),
        listOf(2f, 2f),
        listOf(1f, 1f, 1f, -1f)
    )

    fun generate(
        difficulty: BeginnerDifficulty,
        sequenceNumber: Int = 1,
        random: Random = Random.Default
    ): Exercise {
        val pool: List<ScaleNote>
        val barCount: Int
        val bpm: Int
        val barTemplates: List<List<Float>>
        val finalTemplates: List<List<Float>>
        val maxStep: Int
        val titlePrefix: String

        when (difficulty) {
            BeginnerDifficulty.BEGINNER -> {
                pool = beginnerPool
                barCount = random.nextInt(6, 9) // 6–8 bars (24–32 beats)
                bpm = random.nextInt(68, 75)
                barTemplates = beginnerBars
                finalTemplates = beginnerFinalBars
                maxStep = 1
                titlePrefix = "Beginner Drill"
            }

            BeginnerDifficulty.INTERMEDIATE -> {
                pool = intermediatePool
                barCount = random.nextInt(10, 13) // 10–12 bars (40–48 beats)
                bpm = random.nextInt(74, 83)
                barTemplates = intermediateBars
                finalTemplates = intermediateFinalBars
                maxStep = 2
                titlePrefix = "Intermediate Drill"
            }

            BeginnerDifficulty.ADVANCED -> {
                pool = advancedBeginnerPool
                barCount = random.nextInt(16, 21) // 16–20 bars (64–80 beats)
                bpm = random.nextInt(80, 91)
                barTemplates = advancedBars
                finalTemplates = advancedFinalBars
                maxStep = 4
                titlePrefix = "Advanced Drill"
            }
        }

        val notes = mutableListOf<NoteEvent>()
        var currentBeat = 0f
        var currentIndex = 0 // Start near tonic C4

        for (barIdx in 0 until barCount) {
            val isFinalBar = barIdx == barCount - 1
            val isPhraseEndingBar = ((barIdx + 1) % 4 == 0) && !isFinalBar
            val pattern = when {
                isFinalBar -> finalTemplates[random.nextInt(finalTemplates.size)]
                isPhraseEndingBar -> phraseEndingBars[random.nextInt(phraseEndingBars.size)]
                else -> barTemplates[random.nextInt(barTemplates.size)]
            }

            pattern.forEachIndexed { slotIdx, rawDuration ->
                val isLastNoteOfPiece = isFinalBar && slotIdx == pattern.lastIndex
                if (rawDuration < 0f) {
                    val restDuration = abs(rawDuration)
                    notes.add(NoteEvent(midiNote = null, startBeat = currentBeat, durationBeats = restDuration))
                    currentBeat += restDuration
                } else {
                    if (isLastNoteOfPiece) {
                        // Resolve final cadence cleanly on C4 (index 0) or C5 (lastIndex if close in Adv. Beginner)
                        currentIndex = if (difficulty == BeginnerDifficulty.ADVANCED && currentIndex >= pool.size - 3) {
                            pool.lastIndex
                        } else {
                            0
                        }
                    } else if (notes.none { !it.isRest }) {
                        // First playable note starts cleanly on C4 or E4/G4
                        currentIndex = if (difficulty == BeginnerDifficulty.BEGINNER) 0 else random.nextInt(minOf(3, pool.size))
                    } else {
                        currentIndex = pickNextScaleIndex(
                            currentIndex = currentIndex,
                            poolSize = pool.size,
                            maxStep = maxStep,
                            difficulty = difficulty,
                            random = random
                        )
                    }
                    val chosen = pool[currentIndex]
                    notes.add(
                        NoteEvent(
                            midiNote = chosen.midi,
                            startBeat = currentBeat,
                            durationBeats = rawDuration,
                            fingerHint = chosen.finger
                        )
                    )
                    currentBeat += rawDuration
                }
            }
        }

        return Exercise(
            id = "gen-${difficulty.name.lowercase()}-$sequenceNumber-${random.nextInt(100_000)}",
            title = "$titlePrefix #$sequenceNumber",
            subtitle = difficulty.description,
            focusTag = difficulty.shortTag,
            defaultBpm = bpm,
            beatsPerBar = 4,
            difficulty = difficulty,
            notes = notes
        )
    }

    private fun pickNextScaleIndex(
        currentIndex: Int,
        poolSize: Int,
        maxStep: Int,
        difficulty: BeginnerDifficulty,
        random: Random
    ): Int {
        val delta = when (difficulty) {
            BeginnerDifficulty.BEGINNER -> {
                // Stepwise neighbor or occasional repeat
                when (random.nextInt(5)) {
                    0 -> 0
                    1, 2 -> 1
                    else -> -1
                }
            }

            BeginnerDifficulty.INTERMEDIATE -> {
                when (random.nextInt(8)) {
                    0 -> 0
                    1, 2 -> 1
                    3, 4 -> -1
                    5 -> 2
                    6 -> -2
                    else -> if (currentIndex == 0) 1 else -1
                }
            }

            BeginnerDifficulty.ADVANCED -> {
                when (random.nextInt(10)) {
                    0 -> 0
                    1, 2 -> 1
                    3, 4 -> -1
                    5, 6 -> 2
                    7 -> -2
                    8 -> random.nextInt(-maxStep, maxStep + 1)
                    else -> if (currentIndex > poolSize / 2) -1 else 1
                }
            }
        }
        return (currentIndex + delta).coerceIn(0, poolSize - 1)
    }
}
