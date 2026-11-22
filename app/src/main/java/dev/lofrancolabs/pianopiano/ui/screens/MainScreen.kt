package dev.lofrancolabs.pianopiano.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import dev.lofrancolabs.pianopiano.model.Exercise
import dev.lofrancolabs.pianopiano.model.InputSource
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    uiState: PianoPianoUiState,
    onSelectGuidedExercise: (Exercise) -> Unit,
    onStartRandomMode: (BeginnerDifficulty) -> Unit,
    onNextExercise: () -> Unit,
    onSelectNoteNaming: (NoteNamingSystem) -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onSetInputSource: (InputSource) -> Unit,
    onRequestMicPermission: () -> Unit,
    onCalibrate: () -> Unit,
    onPreview: () -> Unit,
    onStartOrStopPractice: () -> Unit,
    onTouchKeyDown: (Int) -> Unit,
    onTouchKeyUp: (Int) -> Unit,
    onNextExerciseSummary: () -> Unit,
    onDismissSummary: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }
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
                onSelectGuidedExercise = { exercise ->
                    coroutineScope.launch { drawerState.close() }
                    onSelectGuidedExercise(exercise)
                },
                onSelectRandomDifficulty = { diff ->
                    coroutineScope.launch { drawerState.close() }
                    onStartRandomMode(diff)
                },
                onSelectInputSource = { src ->
                    onSetInputSource(src)
                    if (src == InputSource.MICROPHONE && !uiState.hasMicPermission) {
                        onRequestMicPermission()
                    }
                },
                onCalibrate = {
                    coroutineScope.launch { drawerState.close() }
                    if (!uiState.hasMicPermission) onRequestMicPermission() else onCalibrate()
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
            // Top Header: Menu Button, App Title, Level/XP Pill, Active Exercise Pill, Input Mode
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { coroutineScope.launch { drawerState.open() } },
                    enabled = !isActivePractice,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Open menu",
                        tint = PianoColors.TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.beatFlashAlpha > 0.1f) PianoColors.WarmGold
                                else PianoColors.WarmGold.copy(alpha = 0.35f)
                            )
                    )
                    Text(
                        text = "Piano Piano",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PianoColors.WarmGold
                    )
                }

                // Level / XP Badge (opens menu drawer)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PianoColors.WarmGold.copy(alpha = 0.14f))
                        .border(1.dp, PianoColors.WarmGold.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable(enabled = !isActivePractice) {
                            coroutineScope.launch { drawerState.open() }
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Player level",
                        tint = PianoColors.WarmGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Lv ${uiState.playerProgress.level} • ${uiState.playerProgress.xpIntoCurrentLevel}/${uiState.playerProgress.xpNeededForNextLevel} XP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PianoColors.WarmGold
                    )
                }

                // Active Exercise Pill (opens drawer to pick drill / random difficulty)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PianoColors.SurfaceElevated)
                        .border(1.dp, PianoColors.Border, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isActivePractice) {
                            coroutineScope.launch { drawerState.open() }
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = uiState.selectedExercise.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = PianoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "• ${uiState.selectedExercise.focusTag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PianoColors.TargetBlue
                    )
                }

                FilterChip(
                    selected = uiState.inputSource == InputSource.MICROPHONE,
                    onClick = {
                        onSetInputSource(InputSource.MICROPHONE)
                        if (!uiState.hasMicPermission) onRequestMicPermission()
                    },
                    label = { Text("Mic") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Microphone mode",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PianoColors.WarmGold,
                        selectedLabelColor = PianoColors.Background,
                        selectedLeadingIconColor = PianoColors.Background
                    )
                )

                FilterChip(
                    selected = uiState.inputSource == InputSource.TOUCH_PIANO,
                    onClick = { onSetInputSource(InputSource.TOUCH_PIANO) },
                    label = { Text("Touch") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Piano,
                            contentDescription = "Touch piano mode",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PianoColors.TargetBlue,
                        selectedLabelColor = PianoColors.Background,
                        selectedLeadingIconColor = PianoColors.Background
                    )
                )

                if (uiState.inputSource == InputSource.MICROPHONE) {
                    OutlinedButton(
                        onClick = {
                            if (!uiState.hasMicPermission) onRequestMicPermission() else onCalibrate()
                        },
                        enabled = !isActivePractice && uiState.phase != SessionPhase.CALIBRATING
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Calibrate mic",
                            modifier = Modifier.size(16.dp),
                            tint = PianoColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.phase == SessionPhase.CALIBRATING) "Calibrating..." else "Calibrate",
                            color = PianoColors.TextPrimary
                        )
                    }
                }
            }

            // Permission rationale banner if Mic mode lacks permission
            if (uiState.inputSource == InputSource.MICROPHONE && !uiState.hasMicPermission) {
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
                        text = "Allow Microphone access so Piano Piano can listen to your piano, or switch to Touch mode.",
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

            // Transport & Mic Monitor Bar
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (uiState.inputSource == InputSource.MICROPHONE && !uiState.hasMicPermission) {
                            onRequestMicPermission()
                        } else {
                            onStartOrStopPractice()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActivePractice) PianoColors.Coral else PianoColors.Emerald,
                        contentColor = PianoColors.Background
                    )
                ) {
                    Icon(
                        imageVector = if (isActivePractice) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isActivePractice) "Stop" else "Start"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isActivePractice) "Stop" else "Play",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onPreview,
                    enabled = !isActivePractice && uiState.phase != SessionPhase.CALIBRATING
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Rounded.Stop else Icons.Rounded.Headphones,
                        contentDescription = "Listen preview",
                        tint = PianoColors.TargetBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPreviewing) "Stop Listen" else "Listen",
                        color = PianoColors.TextPrimary
                    )
                }

                // One-tap Next Exercise button
                OutlinedButton(
                    onClick = onNextExercise,
                    enabled = uiState.phase != SessionPhase.CALIBRATING
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next exercise",
                        tint = PianoColors.WarmGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Next",
                        color = PianoColors.WarmGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // BPM Control
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PianoColors.Surface)
                        .border(1.dp, PianoColors.Border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { onAdjustBpm(-4) },
                        enabled = !isActivePractice && !isPreviewing,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Slower", tint = PianoColors.TextPrimary)
                    }
                    Text(
                        text = "${uiState.bpm} BPM",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PianoColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onAdjustBpm(4) },
                        enabled = !isActivePractice && !isPreviewing,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Faster", tint = PianoColors.TextPrimary)
                    }
                }

                // Live Mic / Heard Note Status Pill
                if (uiState.inputSource == InputSource.MICROPHONE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(PianoColors.Surface)
                            .border(1.dp, PianoColors.Border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Audio level",
                            tint = if (uiState.micLevel > 0.15f) PianoColors.Emerald else PianoColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        LinearProgressIndicator(
                            progress = { uiState.micLevel },
                            modifier = Modifier
                                .width(56.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PianoColors.Emerald,
                            trackColor = PianoColors.SurfaceElevated
                        )
                        val heardLabel = uiState.heardMidi?.let {
                            MidiUtils.midiToName(it, uiState.noteNaming)
                        } ?: "--"
                        Text(
                            text = "Heard: $heardLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.heardMidi != null) PianoColors.Emerald else PianoColors.TextMuted
                        )
                    }
                }

                uiState.statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = PianoColors.WarmGold,
                        maxLines = 1
                    )
                }
            }

            // Center Stage: Hybrid Staff + Proportional Duration Bars
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
                    .weight(1.35f)
            )

            // Bottom Dock: Interactive 2-Octave Piano Keyboard (C3 - C5)
            val activeHoldEval = uiState.evaluations.lastOrNull {
                it.activeHoldStartMs != null && it.activeHoldEndMs == null
            }
            InteractivePianoKeyboard(
                expectedMidi = uiState.expectedTargetMidi,
                heardMidi = if (uiState.inputSource == InputSource.MICROPHONE) uiState.heardMidi else null,
                activeTouchMidis = uiState.activeTouchMidis,
                previewActiveMidi = uiState.previewActiveMidi,
                isPlaying = uiState.phase == SessionPhase.PLAYING,
                heldCorrectMidi = activeHoldEval?.takeIf { it.pitchVerdict == PitchVerdict.CORRECT }?.playedMidi,
                heldWrongMidi = activeHoldEval?.takeIf { it.pitchVerdict == PitchVerdict.WRONG_KEY }?.playedMidi,
                noteNaming = uiState.noteNaming,
                onKeyDown = onTouchKeyDown,
                onKeyUp = onTouchKeyUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
            )
        }
    }

    uiState.summary?.let { summary ->
        ScorecardDialog(
            summary = summary,
            noteNaming = uiState.noteNaming,
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
}
