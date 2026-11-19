package com.envimesh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colours taken from the App Inventor version.
val EnviGreen = Color(0xFF0F6E56)      // buttons, navigation (15, 110, 86)
val EnviGreenDark = Color(0xFF085441)  // pressed (8, 84, 65)
val EnviAccent = Color(0xFF11D1A1)
val EnviBackground = Color(0xFF151515) // (21, 21, 21)
val EnviSurface = Color(0xFF1F1F1E)
val EnviMuted = Color(0xFF9A9A9A)

val StatusGood = Color(0xFF22C55E)
val StatusBelow = Color(0xFFEF4444)
val StatusError = Color(0xFFF59E0B)
val StatusOffline = Color(0xFF8A8A8A)

private val EnviColors = darkColorScheme(
    primary = EnviGreen,
    onPrimary = Color.White,
    primaryContainer = EnviGreenDark,
    onPrimaryContainer = Color.White,
    secondary = EnviAccent,
    onSecondary = Color.Black,
    background = EnviBackground,
    onBackground = Color.White,
    surface = EnviSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF262625),
    onSurfaceVariant = EnviMuted,
    outline = Color(0xFF3A3A39),
)

@Composable
fun EnviMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EnviColors, content = content)
}
