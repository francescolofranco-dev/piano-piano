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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.HandMode
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem
import dev.lofrancolabs.pianopiano.ui.PianoPianoUiState
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors

@Composable
fun AppMenuDrawer(
    uiState: PianoPianoUiState,
    onSelectNoteNaming: (NoteNamingSystem) -> Unit,
    onSelectDifficulty: (BeginnerDifficulty) -> Unit,
    onSelectHandMode: (HandMode) -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onOpenDetectionTest: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        drawerContainerColor = PianoColors.Surface,
        drawerContentColor = PianoColors.TextPrimary,
        modifier = modifier.width(310.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Progress Header
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
                    Text(
                        text = "Level ${uiState.playerProgress.level}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PianoColors.WarmGold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PianoColors.WarmGold.copy(alpha = 0.16f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                LinearProgressIndicator(
                    progress = { uiState.playerProgress.levelProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PianoColors.WarmGold,
                    trackColor = PianoColors.Background
                )

                Text(
                    text = "${uiState.playerProgress.xpIntoCurrentLevel} / ${uiState.playerProgress.xpNeededForNextLevel} XP • ${uiState.playerProgress.exercisesCompleted} completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = PianoColors.TextMuted
                )
            }

            // 2. Difficulty Selection (Generates a new drill on tap)
            SectionHeader(title = "Difficulty")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BeginnerDifficulty.entries.forEach { diff ->
                    val isSelected = uiState.randomDifficulty == diff
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
                            .clickable { onSelectDifficulty(diff) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = if (isSelected) PianoColors.WarmGold else PianoColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = diff.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) PianoColors.WarmGold else PianoColors.TextPrimary
                            )
                            Text(
                                text = difficultySubtitle(diff, uiState.handMode, uiState.noteNaming),
                                style = MaterialTheme.typography.labelSmall,
                                color = PianoColors.TextMuted
                            )
                        }
                    }
                }
            }

            // 3. Hand Mode (Right Hand, Left Hand, Two Hands Alternating, Two Hands Together)
            SectionHeader(title = "Hands")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HandModeChip(
                        mode = HandMode.RIGHT_HAND,
                        selected = uiState.handMode == HandMode.RIGHT_HAND,
                        onClick = { onSelectHandMode(HandMode.RIGHT_HAND) },
                        modifier = Modifier.weight(1f)
                    )
                    HandModeChip(
                        mode = HandMode.LEFT_HAND,
                        selected = uiState.handMode == HandMode.LEFT_HAND,
                        onClick = { onSelectHandMode(HandMode.LEFT_HAND) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HandModeChip(
                        mode = HandMode.BOTH_ALTERNATING,
                        selected = uiState.handMode == HandMode.BOTH_ALTERNATING,
                        onClick = { onSelectHandMode(HandMode.BOTH_ALTERNATING) },
                        modifier = Modifier.weight(1f)
                    )
                    HandModeChip(
                        mode = HandMode.BOTH_TOGETHER,
                        selected = uiState.handMode == HandMode.BOTH_TOGETHER,
                        onClick = { onSelectHandMode(HandMode.BOTH_TOGETHER) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = PianoColors.Border)

            // 3. Settings
            SectionHeader(title = "Settings")

            // Note Names
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

            // Tempo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PianoColors.SurfaceElevated)
                    .border(1.dp, PianoColors.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tempo",
                    style = MaterialTheme.typography.labelLarge,
                    color = PianoColors.TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAdjustBpm(-4) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Slower", tint = PianoColors.TextPrimary)
                    }
                    Text(
                        text = "${uiState.bpm} BPM",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PianoColors.WarmGold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onAdjustBpm(4) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Faster", tint = PianoColors.TextPrimary)
                    }
                }
            }

            // Note Detection Test
            OutlinedButton(
                onClick = onOpenDetectionTest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = PianoColors.WarmGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Note Detection", color = PianoColors.WarmGold, fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = onOpenAbout,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("About & Privacy", color = PianoColors.TextMuted)
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

@Composable
private fun HandModeChip(
    mode: HandMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (mode) {
        HandMode.RIGHT_HAND -> PianoColors.TargetBlue
        HandMode.LEFT_HAND -> PianoColors.LeftHandPurple
        HandMode.BOTH_ALTERNATING, HandMode.BOTH_TOGETHER -> PianoColors.WarmGold
    }
    val border = if (selected) accent else PianoColors.Border
    val bg = if (selected) accent.copy(alpha = 0.16f) else PianoColors.SurfaceElevated
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = mode.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) accent else PianoColors.TextPrimary
        )
        Text(
            text = mode.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = PianoColors.TextMuted
        )
    }
}

private fun difficultySubtitle(
    diff: BeginnerDifficulty,
    handMode: HandMode,
    naming: NoteNamingSystem
): String {
    val eng = naming == NoteNamingSystem.ENGLISH
    val range = when (handMode) {
        HandMode.RIGHT_HAND -> when (diff) {
            BeginnerDifficulty.BEGINNER -> if (eng) "C4–E4" else "Do4–Mi4"
            BeginnerDifficulty.INTERMEDIATE -> if (eng) "C4–G4" else "Do4–Sol4"
            BeginnerDifficulty.ADVANCED -> if (eng) "C4–C5" else "Do4–Do5"
        }
        HandMode.LEFT_HAND -> when (diff) {
            BeginnerDifficulty.BEGINNER -> if (eng) "C3–E3" else "Do3–Mi3"
            BeginnerDifficulty.INTERMEDIATE -> if (eng) "C3–G3" else "Do3–Sol3"
            BeginnerDifficulty.ADVANCED -> if (eng) "C3–C4" else "Do3–Do4"
        }
        HandMode.BOTH_ALTERNATING, HandMode.BOTH_TOGETHER -> when (diff) {
            BeginnerDifficulty.BEGINNER -> if (eng) "C3–E4" else "Do3–Mi4"
            BeginnerDifficulty.INTERMEDIATE -> if (eng) "C3–G4" else "Do3–Sol4"
            BeginnerDifficulty.ADVANCED -> if (eng) "C3–C5" else "Do3–Do5"
        }
    }
    val bars = when (diff) {
        BeginnerDifficulty.BEGINNER -> "6–8 bars"
        BeginnerDifficulty.INTERMEDIATE -> "10–12 bars"
        BeginnerDifficulty.ADVANCED -> "16–20 bars"
    }
    return "$range • $bars"
}
