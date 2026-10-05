package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.detect.FollowHit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Timeline of a tracker that turned up at several different places. Shown only when there is one. */
@Composable
fun FollowCard(hits: List<FollowHit>) {
    if (hits.isEmpty()) return
    val f = SimpleDateFormat("EEE HH:mm", Locale.US)
    hits.forEach { h ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Level.ALERT.containerC()).padding(14.dp)) {
            Text("▲ ${h.label} may be following you", color = Level.ALERT.accentC(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Heard at ${h.visits.size} different places over ${h.spanMs / 60_000} min (${h.key}):", color = UiColors.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            h.visits.forEach { v -> Text("   place ${v.placeNo}: ${f.format(Date(v.first))} - ${f.format(Date(v.last))}  (${v.n} sightings)", color = UiColors.text, fontSize = 12.sp) }
            Text("Don't remove or destroy it yet. Photograph it where it is, note the time, then see \"what to do\" below.", color = UiColors.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
