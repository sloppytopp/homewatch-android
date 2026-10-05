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
fun HelpCard() = Guide("If something is flagged - what to do", HELP)

@Composable
private fun Guide(title: String, items: List<Pair<String, String>>) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(UiColors.ring).clickable { open = !open }.padding(14.dp)) {
        Text((if (open) "▾ " else "▸ ") + title, color = UiColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (open) items.forEach { (h, b) ->
            Text("$h $b".replace("  ", " "), color = UiColors.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private val DIGITAL = listOf(
    "First, safety:" to "if someone controls or watches your devices, removing their access can alert them. If you may be in danger, talk to an advocate before changing anything (US: National Domestic Violence Hotline 1-800-799-7233, or text START to 88788; tech-safety help at techsafety.org), and use a device they have never touched for sensitive steps.",
    "Google account:" to "on a trusted device open myaccount.google.com > Security > Your devices and Recent security activity, and sign out anything you don't know. In Google Maps > Location sharing, stop sharing with anyone you don't recognize.",
    "Apple account:" to "Settings > [your name] lists every signed-in device. In Find My > People, stop sharing locations you don't want shared. On iPhone (iOS 16+), Settings > Privacy & Security > Safety Check can reset sharing and access in one go.",
    "Family and carrier sharing:" to "check Family Sharing, Google Family Link, and your phone carrier's family locator service (for example Smart Family or Family Locator) for anyone who can see where you are.",
    "Passwords and email:" to "change passwords from a trusted device, turn on 2-step verification, and check your email for forwarding rules and for recovery phone numbers or emails you didn't add.",
    "On this phone:" to "Android Settings > Apps > Special app access: look at Device admin apps, Accessibility, Notification access and Usage access for anything you don't recognize. Run Play Protect (Play Store > profile > Play Protect). Be suspicious of apps installed from outside the Play Store or with no icon.",
    "More help:" to "the Coalition Against Stalkerware (stopstalkerware.org) explains the signs and what to do.",
)

/** Checklist for the far more common kind of stalking: shared accounts, location sharing and stalkerware. No permissions needed. */
@Composable
fun DigitalSafetyCard() = Guide("Digital safety checklist (accounts, location sharing, stalkerware)", DIGITAL)
