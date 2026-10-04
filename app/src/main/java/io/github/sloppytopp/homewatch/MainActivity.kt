package io.github.sloppytopp.homewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.ui.HomewatchTheme
import io.github.sloppytopp.homewatch.ui.Level
import io.github.sloppytopp.homewatch.ui.StatusTile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark system bars, regardless of the phone's light/dark setting (no bright strip at night).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        setContent {
            HomewatchTheme {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Homewatch", color = Color(0xFFC8C8C8), fontSize = 22.sp)
                    // Milestone 1: static layout only - no scanning yet, so every tile says so honestly.
                    StatusTile("Drone near the house?", Level.OFF, "Not scanning yet (demo screen)")
                    StatusTile("Active tracker present?", Level.OFF, "Not scanning yet (demo screen)")
                    StatusTile("Camera-like Wi-Fi source?", Level.OFF, "Not scanning yet (demo screen)")
                    StatusTile("Unusual RF activity?", Level.OFF, "Needs optional RTL-SDR hardware")
                    Text(
                        "Detect-only. A quiet screen is not a guarantee of safety.",
                        color = Color(0xFF6A6A6A), fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
