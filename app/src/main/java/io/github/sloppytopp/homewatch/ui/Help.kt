package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HELP = listOf(
    "Stay calm." to "Most alerts turn out to be ordinary: a neighbor's device, your own phone, a passing car. A single amber or red line is a reason to look, not proof that someone is targeting you.",
    "Tracker:" to "a tracker that stays strong for many minutes is worth finding. Tap it on the Status or Nearby screen to use the finder and walk toward it. Don't move or destroy it yet: photograph it where it is, note the time, and contact local law enforcement. iPhone: Find My > Items > Identify Found Item. Android: Settings > Safety & emergency > Unknown tracker alerts.",
    "Drone:" to "a Remote ID broadcast only claims a drone and can be faked. Note the time and what you saw. Don't shoot at, jam or interfere with it (that is a federal crime). You can report it to local law enforcement or the FAA.",
    "Unknown device on your Wi-Fi:" to "look it up in your router's client list, block it, then change the Wi-Fi password and turn off WPS and any guest network you don't use.",
    "If you feel unsafe" to " (for example a stalker or abusive partner), contact local police or the National Domestic Violence Hotline (US: 1-800-799-7233). A quiet screen is not a guarantee of safety: this tool cannot see every kind of device.",
)

/** Same "what to do" guide as the web dashboard. Collapsed by default. */
@Composable
fun HelpCard() {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(UiColors.ring).clickable { open = !open }.padding(14.dp)) {
        Text((if (open) "▾ " else "▸ ") + "If something is flagged - what to do", color = UiColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (open) HELP.forEach { (h, b) ->
            Text("$h $b".replace("  ", " "), color = UiColors.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
