package dev.lofrancolabs.pianopiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lofrancolabs.pianopiano.model.BeginnerDifficulty
import dev.lofrancolabs.pianopiano.model.HandMode
import dev.lofrancolabs.pianopiano.model.MicSensitivity
import dev.lofrancolabs.pianopiano.model.MicSourceMode
import dev.lofrancolabs.pianopiano.model.MidiUtils
import dev.lofrancolabs.pianopiano.model.NoteNamingSystem
import dev.lofrancolabs.pianopiano.model.PitchVerdict
import dev.lofrancolabs.pianopiano.model.SessionPhase
import dev.lofrancolabs.pianopiano.ui.PianoPianoUiState
import dev.lofrancolabs.pianopiano.ui.components.AppMenuDrawer
import dev.lofrancolabs.pianopiano.ui.components.InteractivePianoKeyboard
import dev.lofrancolabs.pianopiano.ui.components.StaffTimelineCanvas
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    uiState: PianoPianoUiState,
    onSelectDifficulty: (BeginnerDifficulty) -> Unit,
    onSelectHandMode: (HandMode) -> Unit,
    onNextExercise: () -> Unit,
    onSelectNoteNaming: (NoteNamingSystem) -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onSelectSensitivity: (MicSensitivity) -> Unit,
    onSelectMicSourceMode: (MicSourceMode) -> Unit,
    onRequestMicPermission: () -> Unit,
    onCalibrate: () -> Unit,
    onClearDetectedNotes: () -> Unit,
    onPreview: () -> Unit,
    onStartOrStopPractice: () -> Unit,
    onNextExerciseSummary: () -> Unit,
    onDismissSummary: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDetectionTestDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val isCompactHeight = with(LocalDensity.current) { containerHeightPx.toDp() } < 500.dp
    val isActivePractice = uiState.phase == SessionPhase.COUNT_IN || uiState.phase == SessionPhase.PLAYING
    val isPreviewing = uiState.phase == SessionPhase.PREVIEWING

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isActivePractice,
        drawerContent = {
            AppMenuDrawer(
                uiState = uiState,
                onSelectNoteNaming = onSelectNoteNaming,
                onSelectDifficulty = { diff ->
                    coroutineScope.launch { drawerState.close() }
                    onSelectDifficulty(diff)
                },
                onSelectHandMode = { mode ->
                    coroutineScope.launch { drawerState.close() }
                    onSelectHandMode(mode)
                },
                onAdjustBpm = onAdjustBpm,
                onOpenDetectionTest = {
                    coroutineScope.launch { drawerState.close() }
                    showDetectionTestDialog = true
                },
                onOpenAbout = {
                    coroutineScope.launch { drawerState.close() }
                    showAboutDialog = true
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PianoColors.Background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = if (isCompactHeight) 6.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else 10.dp)
        ) {
            // Row 1: Single Ordered Top Bar (Menu, Exercise Title + Quiet Status, Level Badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { coroutineScope.launch { drawerState.open() } },
                    enabled = !isActivePractice,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Open menu",
                        tint = PianoColors.TextPrimary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isActivePractice) {
                            coroutineScope.launch { drawerState.open() }
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = uiState.selectedExercise.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PianoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val heardText = if (uiState.heardMidis.isNotEmpty()) {
                        uiState.heardMidis.sorted().joinToString(" + ") { midi ->
                            MidiUtils.midiToName(midi, uiState.noteNaming)
                        }
                    } else {
                        uiState.heardMidi?.let { midi -> MidiUtils.midiToName(midi, uiState.noteNaming) }
                    }
                    val heardSuffix = heardText?.let { " • $it" }.orEmpty()
                    val subtitleText = uiState.statusMessage ?: "${uiState.bpm} BPM • ${uiState.handMode.shortLabel}$heardSuffix"
                    val subtitleColor = when {
                        uiState.statusMessage != null -> PianoColors.WarmGold
                        heardText != null -> PianoColors.Emerald
                        else -> PianoColors.TextMuted
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Lv ${uiState.playerProgress.level}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PianoColors.WarmGold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PianoColors.WarmGold.copy(alpha = 0.14f))
                        .border(1.dp, PianoColors.WarmGold.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable(enabled = !isActivePractice) {
                            coroutineScope.launch { drawerState.open() }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            // Permission rationale banner when Microphone lacks permission
            if (!uiState.hasMicPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PianoColors.WarmGold.copy(alpha = 0.14f))
                        .border(1.dp, PianoColors.WarmGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Allow Microphone access so Piano Piano can listen to your acoustic piano.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestMicPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PianoColors.WarmGold,
                            contentColor = PianoColors.Background
                        )
                    ) {
                        Text("Allow Mic", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Row 2: Clean Single Action Bar (Play, Listen, Next)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (!uiState.hasMicPermission) {
                            onRequestMicPermission()
                        } else {
                            onStartOrStopPractice()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActivePractice) PianoColors.Coral else PianoColors.Emerald,
                        contentColor = PianoColors.Background
                    ),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(
                        imageVector = if (isActivePractice) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isActivePractice) "Stop" else "Start",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isActivePractice) "Stop" else "Play",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onPreview,
                    enabled = !isActivePractice && uiState.phase != SessionPhase.CALIBRATING,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Rounded.Stop else Icons.Rounded.Headphones,
                        contentDescription = "Listen",
                        tint = PianoColors.TargetBlue,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPreviewing) "Stop" else "Listen",
                        color = PianoColors.TextPrimary,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onNextExercise,
                    enabled = uiState.phase != SessionPhase.CALIBRATING,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next exercise",
                        tint = PianoColors.WarmGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Next",
                        color = PianoColors.WarmGold,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Center Stage: Staff + Proportional Duration Bars
            StaffTimelineCanvas(
                exercise = uiState.selectedExercise,
                bpm = uiState.bpm,
                phase = uiState.phase,
                beatPosition = uiState.beatPosition,
                countInBeat = uiState.countInBeat,
                beatFlashAlpha = uiState.beatFlashAlpha,
                evaluations = uiState.evaluations,
                noteNaming = uiState.noteNaming,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.45f)
            )

            // Bottom Dock: Visual 2-Octave Piano Keyboard Guide (C3 - C5)
            val activeHolds = uiState.evaluations.filter {
                it.activeHoldStartMs != null && it.activeHoldEndMs == null
            }
            val heldCorrectMidis = activeHolds
                .filter { it.pitchVerdict == PitchVerdict.CORRECT }
                .mapNotNull { it.playedMidi }
                .toSet()
            val heldWrongMidis = activeHolds
                .filter { it.pitchVerdict == PitchVerdict.WRONG_KEY }
                .mapNotNull { it.playedMidi }
                .toSet()
            val effectiveHeardMidis = uiState.heardMidis.ifEmpty {
                uiState.heardMidi?.let(::setOf) ?: emptySet()
            }
            val effectivePreviewMidis = uiState.previewActiveMidis.ifEmpty {
                uiState.previewActiveMidi?.let(::setOf) ?: emptySet()
            }
            InteractivePianoKeyboard(
                expectedRightMidi = uiState.expectedRightMidi,
                expectedLeftMidi = uiState.expectedLeftMidi,
                heardMidis = effectiveHeardMidis,
                previewActiveMidis = effectivePreviewMidis,
                isPlaying = uiState.phase == SessionPhase.PLAYING,
                heldCorrectMidis = heldCorrectMidis,
                heldWrongMidis = heldWrongMidis,
                noteNaming = uiState.noteNaming,
                showLeftOctaveLabels = uiState.selectedExercise.usesLeftHand,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
            )
        }
    }

    uiState.summary?.let { summary ->
        ScorecardDialog(
            summary = summary,
            xpReward = uiState.lastXpReward,
            onRetry = {
                onDismissSummary()
                onStartOrStopPractice()
            },
            onNextExercise = onNextExerciseSummary,
            onDismiss = onDismissSummary
        )
    }

    if (showAboutDialog) {
        AboutPrivacyDialog(onDismiss = { showAboutDialog = false })
    }

    if (showDetectionTestDialog) {
        NoteDetectionTestDialog(
            uiState = uiState,
            onRequestMicPermission = onRequestMicPermission,
            onSelectSensitivity = onSelectSensitivity,
            onSelectSourceMode = onSelectMicSourceMode,
            onCalibrate = onCalibrate,
            onClearDetectedNotes = onClearDetectedNotes,
            onDismiss = { showDetectionTestDialog = false }
        )
    }
}
