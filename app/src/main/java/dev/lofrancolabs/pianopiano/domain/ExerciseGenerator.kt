package dev.lofrancolabs.pianopiano.domain

import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.HandMode
import dev.lofrancolabs.pianopiano.model.NoteEvent
import dev.lofrancolabs.pianopiano.model.PianoHand
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

    private val beginnerLeftPool = listOf(
        ScaleNote(48, 5), // C3
        ScaleNote(50, 4), // D3
        ScaleNote(52, 3)  // E3
    )

    private val intermediateLeftPool = listOf(
        ScaleNote(48, 5), // C3
        ScaleNote(50, 4), // D3
        ScaleNote(52, 3), // E3
        ScaleNote(53, 2), // F3
        ScaleNote(55, 1)  // G3
    )

    private val advancedLeftPool = listOf(
        ScaleNote(48, 5), // C3
        ScaleNote(50, 4), // D3
        ScaleNote(52, 3), // E3
        ScaleNote(53, 2), // F3
        ScaleNote(55, 1), // G3
        ScaleNote(57, 3), // A3
        ScaleNote(59, 2), // B3
        ScaleNote(60, 1)  // C4
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

    private val beginnerTogetherLeftBars = listOf(
        listOf(4f),
        listOf(2f, 2f)
    )
    private val intermediateTogetherLeftBars = listOf(
        listOf(2f, 2f),
        listOf(4f),
        listOf(2f, 1f, 1f)
    )
    private val advancedTogetherLeftBars = listOf(
        listOf(2f, 2f),
        listOf(2f, 1f, 1f),
        listOf(1f, 1f, 2f),
        listOf(1f, 1f, 1f, 1f)
    )
    private val togetherLeftFinalBars = listOf(
        listOf(2f, 2f)
    )

    private val dissonantIntervalsMod12 = setOf(1, 11)
    private val consonantIntervalsMod12 = setOf(0, 3, 4, 7, 8, 9)

    fun generate(
        difficulty: BeginnerDifficulty,
        handMode: HandMode = HandMode.RIGHT_HAND,
        sequenceNumber: Int = 1,
        random: Random = Random.Default
    ): Exercise {
        val rightPool: List<ScaleNote>
        val leftPool: List<ScaleNote>
        val barCount: Int
        val bpm: Int
        val barTemplates: List<List<Float>>
        val finalTemplates: List<List<Float>>
        val togetherLeftTemplates: List<List<Float>>
        val maxStep: Int
        val titlePrefix: String

        when (difficulty) {
            BeginnerDifficulty.BEGINNER -> {
                rightPool = beginnerPool
                leftPool = beginnerLeftPool
                barCount = random.nextInt(6, 9) // 6–8 bars (24–32 beats)
                bpm = random.nextInt(56, 63)
                barTemplates = beginnerBars
                finalTemplates = beginnerFinalBars
                togetherLeftTemplates = beginnerTogetherLeftBars
                maxStep = 1
                titlePrefix = "Beginner Drill"
            }

            BeginnerDifficulty.INTERMEDIATE -> {
                rightPool = intermediatePool
                leftPool = intermediateLeftPool
                barCount = random.nextInt(10, 13) // 10–12 bars (40–48 beats)
                bpm = random.nextInt(64, 71)
                barTemplates = intermediateBars
                finalTemplates = intermediateFinalBars
                togetherLeftTemplates = intermediateTogetherLeftBars
                maxStep = 2
                titlePrefix = "Intermediate Drill"
            }

            BeginnerDifficulty.ADVANCED -> {
                rightPool = advancedBeginnerPool
                leftPool = advancedLeftPool
                barCount = random.nextInt(16, 21) // 16–20 bars (64–80 beats)
                bpm = random.nextInt(72, 79)
                barTemplates = advancedBars
                finalTemplates = advancedFinalBars
                togetherLeftTemplates = advancedTogetherLeftBars
                maxStep = 4
                titlePrefix = "Advanced Drill"
            }
        }

        val notes = when (handMode) {
            HandMode.RIGHT_HAND -> generateSingleTrack(
                pool = rightPool,
                hand = PianoHand.RIGHT,
                barCount = barCount,
                barTemplates = barTemplates,
                finalTemplates = finalTemplates,
                maxStep = maxStep,
                difficulty = difficulty,
                random = random
            )

            HandMode.LEFT_HAND -> generateSingleTrack(
                pool = leftPool,
                hand = PianoHand.LEFT,
                barCount = barCount,
                barTemplates = barTemplates,
                finalTemplates = finalTemplates,
                maxStep = maxStep,
                difficulty = difficulty,
                random = random
            )

            HandMode.BOTH_ALTERNATING -> generateAlternatingTrack(
                rightPool = rightPool,
                leftPool = leftPool,
                barCount = barCount,
                barTemplates = barTemplates,
                finalTemplates = finalTemplates,
                maxStep = maxStep,
                difficulty = difficulty,
                random = random
            )

            HandMode.BOTH_TOGETHER -> {
                val rhNotes = generateSingleTrack(
                    pool = rightPool,
                    hand = PianoHand.RIGHT,
                    barCount = barCount,
                    barTemplates = barTemplates,
                    finalTemplates = finalTemplates,
                    maxStep = maxStep,
                    difficulty = difficulty,
                    random = random
                )
                val lhNotes = generateHarmonizedLeftTrack(
                    rhNotes = rhNotes,
                    leftPool = leftPool,
                    barCount = barCount,
                    barTemplates = togetherLeftTemplates,
                    maxStep = maxStep,
                    difficulty = difficulty,
                    random = random
                )
                (rhNotes + lhNotes).sortedWith(
                    compareBy<NoteEvent>({ it.startBeat }, { if (it.hand == PianoHand.LEFT) 0 else 1 })
                )
            }
        }

        return Exercise(
            id = "gen-${difficulty.name.lowercase()}-${handMode.name.lowercase()}-$sequenceNumber-${random.nextInt(100_000)}",
            title = "$titlePrefix #$sequenceNumber",
            subtitle = "${difficulty.shortTag} • ${handMode.label}",
            focusTag = difficulty.shortTag,
            defaultBpm = bpm,
            beatsPerBar = 4,
            difficulty = difficulty,
            handMode = handMode,
            notes = notes
        )
    }

    private fun generateSingleTrack(
        pool: List<ScaleNote>,
        hand: PianoHand,
        barCount: Int,
        barTemplates: List<List<Float>>,
        finalTemplates: List<List<Float>>,
        maxStep: Int,
        difficulty: BeginnerDifficulty,
        random: Random
    ): List<NoteEvent> {
        val notes = mutableListOf<NoteEvent>()
        var currentBeat = 0f
        var currentIndex = 0 // Start near tonic C4 (RH) or C3 (LH)

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
                    notes.add(NoteEvent(midiNote = null, startBeat = currentBeat, durationBeats = restDuration, hand = hand))
                    currentBeat += restDuration
                } else {
                    currentIndex = when {
                        isLastNoteOfPiece -> {
                            if (difficulty == BeginnerDifficulty.ADVANCED && currentIndex >= pool.size - 3) {
                                pool.lastIndex
                            } else {
                                0
                            }
                        }
                        notes.none { !it.isRest } -> {
                            if (difficulty == BeginnerDifficulty.BEGINNER) 0 else random.nextInt(minOf(3, pool.size))
                        }
                        else -> pickNextScaleIndex(currentIndex, pool.size, maxStep, difficulty, random)
                    }
                    val chosen = pool[currentIndex]
                    notes.add(
                        NoteEvent(
                            midiNote = chosen.midi,
                            startBeat = currentBeat,
                            durationBeats = rawDuration,
                            fingerHint = chosen.finger,
                            hand = hand
                        )
                    )
                    currentBeat += rawDuration
                }
            }
        }
        return notes
    }

    private fun generateAlternatingTrack(
        rightPool: List<ScaleNote>,
        leftPool: List<ScaleNote>,
        barCount: Int,
        barTemplates: List<List<Float>>,
        finalTemplates: List<List<Float>>,
        maxStep: Int,
        difficulty: BeginnerDifficulty,
        random: Random
    ): List<NoteEvent> {
        val notes = mutableListOf<NoteEvent>()
        var currentBeat = 0f
        var rhIndex = 0
        var lhIndex = 0
        var hasRightNote = false
        var hasLeftNote = false

        for (barIdx in 0 until barCount) {
            val hand = if (barIdx % 2 == 0) PianoHand.RIGHT else PianoHand.LEFT
            val pool = if (hand == PianoHand.RIGHT) rightPool else leftPool
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
                    notes.add(NoteEvent(midiNote = null, startBeat = currentBeat, durationBeats = restDuration, hand = hand))
                    currentBeat += restDuration
                } else {
                    var cursor = if (hand == PianoHand.RIGHT) rhIndex else lhIndex
                    val hasHandStarted = if (hand == PianoHand.RIGHT) hasRightNote else hasLeftNote
                    cursor = when {
                        isLastNoteOfPiece -> {
                            if (difficulty == BeginnerDifficulty.ADVANCED && cursor >= pool.size - 3) {
                                pool.lastIndex
                            } else {
                                0
                            }
                        }
                        !hasHandStarted -> {
                            if (difficulty == BeginnerDifficulty.BEGINNER) 0 else random.nextInt(minOf(3, pool.size))
                        }
                        else -> pickNextScaleIndex(cursor, pool.size, maxStep, difficulty, random)
                    }
                    if (hand == PianoHand.RIGHT) {
                        rhIndex = cursor
                        hasRightNote = true
                    } else {
                        lhIndex = cursor
                        hasLeftNote = true
                    }
                    val chosen = pool[cursor]
                    notes.add(
                        NoteEvent(
                            midiNote = chosen.midi,
                            startBeat = currentBeat,
                            durationBeats = rawDuration,
                            fingerHint = chosen.finger,
                            hand = hand
                        )
                    )
                    currentBeat += rawDuration
                }
            }
        }
        return notes
    }

    private fun generateHarmonizedLeftTrack(
        rhNotes: List<NoteEvent>,
        leftPool: List<ScaleNote>,
        barCount: Int,
        barTemplates: List<List<Float>>,
        maxStep: Int,
        difficulty: BeginnerDifficulty,
        random: Random
    ): List<NoteEvent> {
        val lhNotes = mutableListOf<NoteEvent>()
        var currentBeat = 0f
        var lhIndex = 0

        for (barIdx in 0 until barCount) {
            val isFinalBar = barIdx == barCount - 1
            val pattern = if (isFinalBar) {
                togetherLeftFinalBars[random.nextInt(togetherLeftFinalBars.size)]
            } else {
                barTemplates[random.nextInt(barTemplates.size)]
            }

            pattern.forEachIndexed { slotIdx, duration ->
                val isLastNote = isFinalBar && slotIdx == pattern.lastIndex
                val slotEnd = currentBeat + duration
                val overlappingRhMidis = rhNotes.filter { note ->
                    !note.isRest &&
                        note.startBeat < slotEnd - 0.001f &&
                        note.startBeat + note.durationBeats > currentBeat + 0.001f
                }.mapNotNull { it.midiNote }

                lhIndex = when {
                    isLastNote -> pickConsonantBassIndex(
                        currentIndex = lhIndex,
                        leftPool = leftPool,
                        overlappingRhMidis = overlappingRhMidis,
                        maxStep = leftPool.size,
                        preferIndexZero = true,
                        random = random
                    )
                    barIdx == 0 && slotIdx == 0 -> pickConsonantBassIndex(
                        currentIndex = 0,
                        leftPool = leftPool,
                        overlappingRhMidis = overlappingRhMidis,
                        maxStep = maxStep,
                        preferIndexZero = true,
                        random = random
                    )
                    else -> pickConsonantBassIndex(
                        currentIndex = lhIndex,
                        leftPool = leftPool,
                        overlappingRhMidis = overlappingRhMidis,
                        maxStep = maxStep,
                        preferIndexZero = difficulty == BeginnerDifficulty.BEGINNER && slotIdx == 0,
                        random = random
                    )
                }

                val chosen = leftPool[lhIndex]
                lhNotes.add(
                    NoteEvent(
                        midiNote = chosen.midi,
                        startBeat = currentBeat,
                        durationBeats = duration,
                        fingerHint = chosen.finger,
                        hand = PianoHand.LEFT
                    )
                )
                currentBeat += duration
            }
        }
        return lhNotes
    }

    private fun pickConsonantBassIndex(
        currentIndex: Int,
        leftPool: List<ScaleNote>,
        overlappingRhMidis: List<Int>,
        maxStep: Int,
        preferIndexZero: Boolean,
        random: Random
    ): Int {
        val nearbyIndices = leftPool.indices.filter { abs(it - currentIndex) <= maxStep }
            .ifEmpty { leftPool.indices.toList() }

        fun hasClash(candidateMidi: Int): Boolean {
            return overlappingRhMidis.any { rhMidi ->
                val interval = ((rhMidi - candidateMidi) % 12 + 12) % 12
                interval in dissonantIntervalsMod12
            }
        }

        fun isConsonant(candidateMidi: Int): Boolean {
            if (overlappingRhMidis.isEmpty()) return true
            return overlappingRhMidis.all { rhMidi ->
                val interval = ((rhMidi - candidateMidi) % 12 + 12) % 12
                interval in consonantIntervalsMod12
            }
        }

        if (preferIndexZero && !hasClash(leftPool[0].midi) && isConsonant(leftPool[0].midi)) {
            return 0
        }

        val ideal = nearbyIndices.filter { idx ->
            val midi = leftPool[idx].midi
            !hasClash(midi) && isConsonant(midi)
        }
        if (ideal.isNotEmpty()) {
            return ideal[random.nextInt(ideal.size)]
        }

        val noClash = nearbyIndices.filter { idx -> !hasClash(leftPool[idx].midi) }
        if (noClash.isNotEmpty()) {
            return noClash[random.nextInt(noClash.size)]
        }

        val globalSafe = leftPool.indices.filter { idx -> !hasClash(leftPool[idx].midi) }
        if (globalSafe.isNotEmpty()) {
            return globalSafe[random.nextInt(globalSafe.size)]
        }

        return currentIndex
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
