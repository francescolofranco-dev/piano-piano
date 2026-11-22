package dev.lofrancolabs.pianopiano.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lofrancolabs.pianopiano.BuildConfig
import dev.lofrancolabs.pianopiano.ui.theme.PianoColors

@Composable
fun AboutPrivacyDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PianoColors.Surface,
            border = BorderStroke(1.dp, PianoColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Piano Piano",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PianoColors.WarmGold
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} • Lofranco Labs",
                    style = MaterialTheme.typography.labelMedium,
                    color = PianoColors.TextMuted
                )
                Text(
                    text = "Microphone audio is processed strictly on-device in real time to detect piano pitch, onset timing, and note hold length. Nothing is recorded, stored, or transmitted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PianoColors.TextPrimary
                )
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://lofrancolabs.dev/privacy.html") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Privacy Policy", color = PianoColors.TargetBlue)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PianoColors.WarmGold,
                        contentColor = PianoColors.Background
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
