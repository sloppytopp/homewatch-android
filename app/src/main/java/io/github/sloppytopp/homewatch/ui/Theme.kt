package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Low-glare palette: true black, muted desaturated status colors, never a bright broadcast of light. */
enum class Level(val word: String, val icon: String, val accent: Color, val container: Color) {
    OFF("OFF", "–", Color(0xFF7A7A7A), Color(0xFF141414)),
    OK("OK", "✓", Color(0xFF5E9A74), Color(0xFF0F1D15)),
    WATCH("WATCH", "◔", Color(0xFFB39A55), Color(0xFF211B0D)),
    ALERT("ALERT", "▲", Color(0xFFC06A6A), Color(0xFF2A1313)),
}

/** Night mode: dim red on black, for walking around outside. */
object NightPalette {
    val text = Color(0xFF8C3030)
    val border = Color(0xFF3A1414)
}

private val Dark = darkColorScheme(
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    onBackground = Color(0xFFC8C8C8),
    onSurface = Color(0xFFC8C8C8),
    primary = Color(0xFF5E9A74),
)

@Composable
fun HomewatchTheme(content: @Composable () -> Unit) {
    // Always dark: the app is meant to be used at night without lighting up the street.
    MaterialTheme(colorScheme = Dark, content = content)
}
