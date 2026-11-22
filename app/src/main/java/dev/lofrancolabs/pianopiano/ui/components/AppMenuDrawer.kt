package dev.lofrancolabs.pianopiano.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.InputSource
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem
import dev.lofrancolabs.pianopiano.model.PracticeMode
import dev.lofrancolabs.pianopiano.ui.PianoPianoUiState
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors

@Composable
fun AppMenuDrawer(
    uiState: PianoPianoUiState,
    onSelectNoteNaming: (NoteNamingSystem) -> Unit,
    onSelectGuidedExercise: (Exercise) -> Unit,
    onSelectRandomDifficulty: (BeginnerDifficulty) -> Unit,
    onSelectInputSource: (InputSource) -> Unit,
    onCalibrate: () -> Unit,
    onOpenAbout: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = PianoColors.Surface,
        drawerContentColor = PianoColors.TextPrimary,
        modifier = Modifier.width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Brand + Player Level / XP Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PianoColors.SurfaceElevated)
                    .border(1.dp, PianoColors.Border, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Piano Piano",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = PianoColors.WarmGold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PianoColors.WarmGold.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Level",
                            tint = PianoColors.WarmGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Lv ${uiState.playerProgress.level}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PianoColors.WarmGold
                        )
                    }
                }

                Text(
                    text = "${uiState.playerProgress.rankTitle} • ${uiState.playerProgress.exercisesCompleted} drills completed",
                    style = MaterialTheme.typography.labelMedium,
                    color = PianoColors.TextMuted
                )

                LinearProgressIndicator(
                    progress = { uiState.playerProgress.levelProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PianoColors.WarmGold,
                    trackColor = PianoColors.Background
                )

                Text(
                    text = "${uiState.playerProgress.xpIntoCurrentLevel} / ${uiState.playerProgress.xpNeededForNextLevel} XP to Level ${uiState.playerProgress.level + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PianoColors.TextMuted
                )
            }

            // Note Naming Preference (English vs Italian/Spanish Solfege)
            SectionHeader(title = "Note Names")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteNamingChip(
                    selected = uiState.noteNaming == NoteNamingSystem.ENGLISH,
                    title = "C, D, E",
                    subtitle = "English",
                    onClick = { onSelectNoteNaming(NoteNamingSystem.ENGLISH) },
                    modifier = Modifier.weight(1f)
                )
                NoteNamingChip(
                    selected = uiState.noteNaming == NoteNamingSystem.SOLFEGE,
                    title = "Do, Re, Mi",
                    subtitle = "IT / ES",
                    onClick = { onSelectNoteNaming(NoteNamingSystem.SOLFEGE) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = PianoColors.Border)

            // Random Practice Generator (3 Beginner Difficulties)
            SectionHeader(title = "Random Drills (Beginner Tiers)")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BeginnerDifficulty.entries.forEach { diff ->
                    val isSelected =
                        uiState.practiceMode == PracticeMode.RANDOM && uiState.randomDifficulty == diff
                    val borderColor = if (isSelected) PianoColors.WarmGold else PianoColors.Border
                    val bgColor = if (isSelected) {
                        PianoColors.WarmGold.copy(alpha = 0.14f)
                    } else {
                        PianoColors.SurfaceElevated
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable { onSelectRandomDifficulty(diff) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = diff.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) PianoColors.WarmGold else PianoColors.TextPrimary
                            )
                            Text(
                                text = difficultySubtitle(diff, uiState.noteNaming),
                                style = MaterialTheme.typography.labelSmall,
                                color = PianoColors.TextMuted
                            )
                        }
                        Text(
                            text = "+${diff.baseXp} XP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PianoColors.Emerald
                        )
                    }
                }
            }

            Button(
                onClick = { onSelectRandomDifficulty(uiState.randomDifficulty) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PianoColors.WarmGold,
                    contentColor = PianoColors.Background
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "New ${uiState.randomDifficulty.label} Drill",
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = PianoColors.Border)

            // 5 Guided Exercises
            SectionHeader(title = "Guided Exercises (5)")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.exercises.forEach { exercise ->
                    val isSelected =
                        uiState.practiceMode == PracticeMode.GUIDED &&
                            uiState.selectedExercise.id == exercise.id
                    val borderColor = if (isSelected) PianoColors.TargetBlue else PianoColors.Border
                    val bgColor = if (isSelected) {
                        PianoColors.TargetBlue.copy(alpha = 0.14f)
                    } else {
                        PianoColors.SurfaceElevated
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable { onSelectGuidedExercise(exercise) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = if (isSelected) PianoColors.TargetBlue else PianoColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exercise.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = PianoColors.TextPrimary
                            )
                            Text(
                                text = "${exercise.focusTag} • ${exercise.subtitle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PianoColors.TextMuted
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = PianoColors.Border)

            // Input & Utility Actions
            SectionHeader(title = "Input & Calibration")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.inputSource == InputSource.MICROPHONE,
                    onClick = { onSelectInputSource(InputSource.MICROPHONE) },
                    label = { Text("Mic") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PianoColors.WarmGold,
                        selectedLabelColor = PianoColors.Background,
                        selectedLeadingIconColor = PianoColors.Background
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState.inputSource == InputSource.TOUCH_PIANO,
                    onClick = { onSelectInputSource(InputSource.TOUCH_PIANO) },
                    label = { Text("Touch") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Piano, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PianoColors.TargetBlue,
                        selectedLabelColor = PianoColors.Background,
                        selectedLeadingIconColor = PianoColors.Background
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (uiState.inputSource == InputSource.MICROPHONE) {
                OutlinedButton(
                    onClick = onCalibrate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = PianoColors.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Calibrate Room Noise", color = PianoColors.TextPrimary)
                }
            }

            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = PianoColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("About & Privacy Policy", color = PianoColors.TextMuted)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = PianoColors.TextMuted
    )
}

@Composable
private fun NoteNamingChip(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) PianoColors.TargetBlue else PianoColors.Border
    val bg = if (selected) PianoColors.TargetBlue.copy(alpha = 0.16f) else PianoColors.SurfaceElevated
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) PianoColors.TargetBlue else PianoColors.TextPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = PianoColors.TextMuted
        )
    }
}

private fun difficultySubtitle(diff: BeginnerDifficulty, naming: NoteNamingSystem): String {
    return if (naming == NoteNamingSystem.ENGLISH) {
        diff.description
    } else {
        when (diff) {
            BeginnerDifficulty.BEGINNER -> "Do4–Mi4 • steady quarter & half notes"
            BeginnerDifficulty.INTERMEDIATE -> "Do4–Sol4 5-finger position • holds & rests"
            BeginnerDifficulty.ADVANCED -> "Do4–Do5 • gentle skips & dotted rhythms"
        }
    }
}
