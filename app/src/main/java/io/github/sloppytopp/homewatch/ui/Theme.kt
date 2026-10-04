package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.sloppytopp.homewatch.data.Prefs

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

/** Night mode = dim red on black (keeps night vision, shows no bright light). Status is still word + icon. */
fun Level.accentC(): Color = if (!Prefs.night) accent else when (this) {
    Level.OFF -> Color(0xFF4A1C1C); Level.OK -> Color(0xFF6E2A2A); Level.WATCH -> Color(0xFF8C3434); Level.ALERT -> Color(0xFFAA4040)
}
fun Level.containerC(): Color = if (Prefs.night) Color(0xFF0C0404) else container

object UiColors {
    val text get() = if (Prefs.night) Color(0xFF8C3030) else Color(0xFFC8C8C8)
    val dim get() = if (Prefs.night) Color(0xFF722B2B) else Color(0xFF8A8A8A)
    val faint get() = if (Prefs.night) Color(0xFF5A2222) else Color(0xFF6A6A6A)
    val warn get() = if (Prefs.night) Color(0xFF8C3030) else Color(0xFFB39A55)
    val ring get() = if (Prefs.night) Color(0xFF2A0D0D) else Color(0xFF2A2A2A)
    val good get() = if (Prefs.night) Color(0xFF6E2A2A) else Color(0xFF5E9A74)
    val neutral get() = if (Prefs.night) Color(0xFF4A1C1C) else Color(0xFF7A7A7A)
    val alert get() = if (Prefs.night) Color(0xFFAA4040) else Color(0xFFC06A6A)
    val watch get() = if (Prefs.night) Color(0xFF8C3434) else Color(0xFFB39A55)
    val dialogBg get() = if (Prefs.night) Color(0xFF0A0303) else Color(0xFF101010)
    val buttonBg get() = if (Prefs.night) Color(0xFF1A0707) else Color(0xFF1C2A22)
    val buttonFg get() = if (Prefs.night) Color(0xFF8C3030) else Color(0xFF9CC7AB)
}
