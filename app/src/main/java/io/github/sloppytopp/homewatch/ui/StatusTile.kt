package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.background
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
fun StatusTile(title: String, level: Level, message: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(level.containerC())
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(level.accentC()))
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${level.icon}  ${level.word}  ·  $title",
                color = level.accentC(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(message, color = UiColors.dim, fontSize = 13.sp)
        }
    }
}
