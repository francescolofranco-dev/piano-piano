package dev.lofrancolabs.pianopiano

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lofrancolabs.pianopiano.ui.PianoPianoViewModel
import dev.lofrancolabs.pianopiano.ui.screens.MainScreen
import dev.lofrancolabs.pianopiano.ui.theme.PianoPianoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PianoPianoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.onPermissionChanged(granted)
            }

            LaunchedEffect(Unit) {
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onPermissionChanged(granted)
            }

            PianoPianoTheme {
                MainScreen(
                    uiState = uiState,
                    onSelectDifficulty = { difficulty ->
                        viewModel.selectDifficulty(difficulty, autoStart = false)
                    },
                    onNextExercise = { viewModel.nextExercise(autoStart = true) },
                    onSelectNoteNaming = viewModel::setNoteNaming,
                    onAdjustBpm = viewModel::adjustBpm,
                    onSetInputSource = viewModel::setInputSource,
                    onRequestMicPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onCalibrate = viewModel::startCalibration,
                    onPreview = viewModel::startPreview,
                    onStartOrStopPractice = viewModel::startPractice,
                    onTouchKeyDown = viewModel::onTouchKeyDown,
                    onTouchKeyUp = viewModel::onTouchKeyUp,
                    onNextExerciseSummary = viewModel::selectNextExerciseAndCloseSummary,
                    onDismissSummary = viewModel::dismissSummary
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChanged(granted)
        viewModel.onResume()
    }

    override fun onPause() {
        viewModel.onPause()
        super.onPause()
    }
}
