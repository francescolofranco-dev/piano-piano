package dev.lofrancolabs.pianopiano.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PianoColors {
    val Background = Color(0xFF111318)
    val Surface = Color(0xFF191D26)
    val SurfaceElevated = Color(0xFF222834)
    val Border = Color(0xFF2E3545)
    val TextPrimary = Color(0xFFF5F1E8)
    val TextMuted = Color(0xFF9CA5B4)
    val WarmGold = Color(0xFFE8B84B)
    val TargetBlue = Color(0xFF58A6FF)
    val Emerald = Color(0xFF3DDC97)
    val Amber = Color(0xFFFFB347)
    val Coral = Color(0xFFFF6B6B)
    val IvoryKey = Color(0xFFF5F1E8)
    val IvoryKeyBorder = Color(0xFFD5CFC4)
    val BlackKey = Color(0xFF1C2029)
    val BlackKeyHighlight = Color(0xFF303747)
}

private val PianoColorScheme = darkColorScheme(
    primary = PianoColors.WarmGold,
    onPrimary = PianoColors.Background,
    secondary = PianoColors.TargetBlue,
    onSecondary = PianoColors.Background,
    tertiary = PianoColors.Emerald,
    background = PianoColors.Background,
    onBackground = PianoColors.TextPrimary,
    surface = PianoColors.Surface,
    onSurface = PianoColors.TextPrimary,
    surfaceVariant = PianoColors.SurfaceElevated,
    onSurfaceVariant = PianoColors.TextMuted,
    outline = PianoColors.Border,
    error = PianoColors.Coral,
    onError = PianoColors.Background
)

@Composable
fun PianoPianoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PianoColorScheme,
        content = content
    )
}
