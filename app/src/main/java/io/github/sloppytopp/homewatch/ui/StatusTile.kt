package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Status is always word + icon + color, never color alone (color-blind safe). */
@Composable
fun StatusTile(title: String, level: Level, message: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(level.containerC())
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(level.accentC()))
        val off = level == Level.OFF   // an unavailable sensor takes less room than a live answer
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (off) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (off) 1.dp else 3.dp)) {
            Text(
                "${level.icon}  ${level.word}  ·  $title",
                color = level.accentC(), fontSize = if (off) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(message, color = UiColors.dim, fontSize = if (off) 12.sp else 13.sp)
            if (onClick != null) Text("Tap to find it  ▸", color = level.accentC(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
