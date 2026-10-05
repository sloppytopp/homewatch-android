package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.detect.StalkerResult
import io.github.sloppytopp.homewatch.detect.Stalkerware
import io.github.sloppytopp.homewatch.scan.StalkerScan

/** On-demand check of this phone's installed apps against the public stalkerware list. */
@Composable
fun StalkerCard() {
    val ctx = LocalContext.current
    var r by remember { mutableStateOf<StalkerResult?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Check this phone for stalkerware", color = UiColors.text, fontSize = 16.sp)
        Text("Compares your installed apps to the public Coalition Against Stalkerware list (bundled in the app, CC BY 4.0, nothing is uploaded) and lists non-system apps with accessibility, notification or device-admin power. " +
            "A clean result is not a guarantee: renamed or custom spyware won't match.", color = UiColors.dim, fontSize = 12.sp)
        Button(
            onClick = { r = StalkerScan.run(ctx) }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = UiColors.buttonBg, contentColor = UiColors.buttonFg),
        ) { Text("Scan installed apps") }
        r?.let { res ->
            Text("Checked ${res.scanned} apps against ${Stalkerware.indicators.size} known stalkerware packages (list ${res.listVersion}).", color = UiColors.dim, fontSize = 12.sp)
            if (res.hits.isEmpty()) Text("✓ No known stalkerware package found.", color = UiColors.good, fontSize = 13.sp)
            else {
                Text("▲ Possible stalkerware found:", color = Level.ALERT.accentC(), fontSize = 14.sp)
                res.hits.forEach { Text("   ${it.family}  (${it.pkg})", color = UiColors.text, fontSize = 13.sp) }
                Text("Don't uninstall it yet: that can alert whoever installed it. Read \"Digital safety checklist\" below and talk to an advocate first (US: 1-800-799-7233).", color = UiColors.warn, fontSize = 12.sp)
            }
            if (res.review.isNotEmpty()) {
                Text("Apps with powers stalkerware often uses (usually legitimate - review each):", color = UiColors.text, fontSize = 13.sp)
                res.review.forEach { a ->
                    Text("   ${a.label}  (${a.pkg})${if (a.noIcon) "  - NO launcher icon" else ""}", color = UiColors.text, fontSize = 12.sp)
                    a.powers.forEach { Text("      • $it", color = UiColors.dim, fontSize = 11.sp) }
                }
            }
        }
        Text("Indicator list: Coalition Against Stalkerware / Echap, github.com/AssoEchap/stalkerware-indicators (CC BY 4.0).", color = UiColors.faint, fontSize = 10.sp)
    }
}
