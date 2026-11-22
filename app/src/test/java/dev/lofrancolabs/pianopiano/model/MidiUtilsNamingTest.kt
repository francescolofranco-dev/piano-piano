package dev.lofrancolabs.pianopiano.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiUtilsNamingTest {

    @Test
    fun formatsEnglishAndSolfegeNoteNamesWithAndWithoutOctaves() {
        assertEquals("C4", MidiUtils.midiToName(60, NoteNamingSystem.ENGLISH))
        assertEquals("F#4", MidiUtils.midiToName(66, NoteNamingSystem.ENGLISH))
        assertEquals("G4", MidiUtils.midiToName(67, NoteNamingSystem.ENGLISH))

        assertEquals("Do4", MidiUtils.midiToName(60, NoteNamingSystem.SOLFEGE))
        assertEquals("Re4", MidiUtils.midiToName(62, NoteNamingSystem.SOLFEGE))
        assertEquals("Mi4", MidiUtils.midiToName(64, NoteNamingSystem.SOLFEGE))
        assertEquals("Fa#4", MidiUtils.midiToName(66, NoteNamingSystem.SOLFEGE))
        assertEquals("Sol4", MidiUtils.midiToName(67, NoteNamingSystem.SOLFEGE))
        assertEquals("La4", MidiUtils.midiToName(69, NoteNamingSystem.SOLFEGE))
        assertEquals("Si4", MidiUtils.midiToName(71, NoteNamingSystem.SOLFEGE))

        assertEquals("Sol", MidiUtils.midiToName(67, NoteNamingSystem.SOLFEGE, includeOctave = false))
        assertEquals("Do", MidiUtils.midiToName(60, NoteNamingSystem.SOLFEGE, includeOctave = false))
    }

    @Test
    fun formatsWrongKeyBadgeInSelectedNamingSystem() {
        val eval = NoteEvaluation(
            targetIndex = 0,
            noteEventIndex = 0,
            expectedMidi = 64,
            expectedStartMs = 1000L,
            expectedEndMs = 2000L,
            playedMidi = 67,
            onsetDeltaMs = 10L,
            pitchVerdict = PitchVerdict.WRONG_KEY,
            timingVerdict = TimingVerdict.PERFECT
        )

        assertEquals("Wrong: G4", eval.feedbackBadgeText(NoteNamingSystem.ENGLISH))
        assertEquals("Wrong: Sol4", eval.feedbackBadgeText(NoteNamingSystem.SOLFEGE))
    }
}
