package dev.lofrancolabs.pianopiano.data

import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.NoteEvent

object BeginnerExercises {
    private const val C4 = 60
    private const val D4 = 62
    private const val E4 = 64
    private const val F4 = 65
    private const val G4 = 67

    val all: List<Exercise> = listOf(
        Exercise(
            id = "middle-c-pulse",
            title = "Middle C Pulse",
            subtitle = "Quarter notes & rests on C4",
            focusTag = "Rhythm",
            defaultBpm = 72,
            notes = listOf(
                NoteEvent(C4, 0f, 1f, 1),
                NoteEvent(C4, 1f, 1f, 1),
                NoteEvent(null, 2f, 1f),
                NoteEvent(C4, 3f, 1f, 1),
                NoteEvent(C4, 4f, 1f, 1),
                NoteEvent(null, 5f, 1f),
                NoteEvent(C4, 6f, 1f, 1),
                NoteEvent(C4, 7f, 1f, 1)
            )
        ),
        Exercise(
            id = "hold-it-right",
            title = "Hold It Right",
            subtitle = "Quarter, half & whole notes on C4–E4",
            focusTag = "Duration",
            defaultBpm = 72,
            notes = listOf(
                NoteEvent(C4, 0f, 1f, 1),
                NoteEvent(D4, 1f, 1f, 2),
                NoteEvent(E4, 2f, 2f, 3),
                NoteEvent(null, 4f, 1f),
                NoteEvent(E4, 5f, 1f, 3),
                NoteEvent(D4, 6f, 2f, 2),
                NoteEvent(C4, 8f, 4f, 1)
            )
        ),
        Exercise(
            id = "five-finger-escalator",
            title = "Five-Finger Escalator",
            subtitle = "Step up & down C4 to G4",
            focusTag = "C Position",
            defaultBpm = 80,
            notes = listOf(
                NoteEvent(C4, 0f, 1f, 1),
                NoteEvent(D4, 1f, 1f, 2),
                NoteEvent(E4, 2f, 1f, 3),
                NoteEvent(F4, 3f, 1f, 4),
                NoteEvent(G4, 4f, 1f, 5),
                NoteEvent(F4, 5f, 1f, 4),
                NoteEvent(E4, 6f, 1f, 3),
                NoteEvent(D4, 7f, 1f, 2),
                NoteEvent(C4, 8f, 2f, 1),
                NoteEvent(null, 10f, 2f)
            )
        ),
        Exercise(
            id = "skipping-stones",
            title = "Skipping Stones",
            subtitle = "Broken C major triad with holds",
            focusTag = "Intervals",
            defaultBpm = 76,
            notes = listOf(
                NoteEvent(C4, 0f, 1f, 1),
                NoteEvent(E4, 1f, 1f, 3),
                NoteEvent(G4, 2f, 2f, 5),
                NoteEvent(E4, 4f, 1f, 3),
                NoteEvent(C4, 5f, 1f, 1),
                NoteEvent(G4, 6f, 2f, 5),
                NoteEvent(E4, 8f, 1f, 3),
                NoteEvent(null, 9f, 1f),
                NoteEvent(C4, 10f, 2f, 1)
            )
        ),
        Exercise(
            id = "ode-to-joy",
            title = "Ode to Joy",
            subtitle = "8-bar beginner phrase with dotted holds",
            focusTag = "Melody",
            defaultBpm = 84,
            notes = listOf(
                // Bar 1
                NoteEvent(E4, 0f, 1f, 3),
                NoteEvent(E4, 1f, 1f, 3),
                NoteEvent(F4, 2f, 1f, 4),
                NoteEvent(G4, 3f, 1f, 5),
                // Bar 2
                NoteEvent(G4, 4f, 1f, 5),
                NoteEvent(F4, 5f, 1f, 4),
                NoteEvent(E4, 6f, 1f, 3),
                NoteEvent(D4, 7f, 1f, 2),
                // Bar 3
                NoteEvent(C4, 8f, 1f, 1),
                NoteEvent(C4, 9f, 1f, 1),
                NoteEvent(D4, 10f, 1f, 2),
                NoteEvent(E4, 11f, 1f, 3),
                // Bar 4
                NoteEvent(E4, 12f, 1.5f, 3),
                NoteEvent(D4, 13.5f, 0.5f, 2),
                NoteEvent(D4, 14f, 2f, 2),
                // Bar 5
                NoteEvent(E4, 16f, 1f, 3),
                NoteEvent(E4, 17f, 1f, 3),
                NoteEvent(F4, 18f, 1f, 4),
                NoteEvent(G4, 19f, 1f, 5),
                // Bar 6
                NoteEvent(G4, 20f, 1f, 5),
                NoteEvent(F4, 21f, 1f, 4),
                NoteEvent(E4, 22f, 1f, 3),
                NoteEvent(D4, 23f, 1f, 2),
                // Bar 7
                NoteEvent(C4, 24f, 1f, 1),
                NoteEvent(C4, 25f, 1f, 1),
                NoteEvent(D4, 26f, 1f, 2),
                NoteEvent(E4, 27f, 1f, 3),
                // Bar 8
                NoteEvent(D4, 28f, 1.5f, 2),
                NoteEvent(C4, 29.5f, 0.5f, 1),
                NoteEvent(C4, 30f, 2f, 1)
            )
        )
    )
}
