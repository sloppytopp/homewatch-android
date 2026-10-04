package io.github.sloppytopp.homewatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sloppytopp.homewatch.detect.DirectionFinder
import io.github.sloppytopp.homewatch.detect.Geo
import kotlin.math.cos
import kotlin.math.sin

/**
 * A real compass: the rose turns with the phone so N always points north, the notch at the top is where the phone is
 * facing. Signal wedges (from the turn-in-place sweep) are fixed to the compass, so the longest wedge points at the
 * strongest direction; [bearing] marks a target direction (strongest side, or a drone).
 */
@Composable
fun CompassDial(heading: Float, finder: DirectionFinder?, bearing: Double?, accuracyLow: Boolean) {
    val tm = rememberTextMeasurer()
    val txt = TextStyle(color = UiColors.text, fontSize = 15.sp)
    val north = TextStyle(color = UiColors.alert, fontSize = 17.sp)
    Column {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 - 22.dp.toPx()
            rotate(-heading, c) {
                drawCircle(UiColors.ring, r, c, style = Stroke(2.dp.toPx()))
                finder?.let { f ->
                    for (i in 0 until 8) {
                        val m = f.meanDbm(i) ?: continue
                        val len = r * ((m + 100) / 60.0).coerceIn(0.08, 1.0).toFloat()
                        drawArc(UiColors.watch.copy(alpha = 0.55f), i * 45f - 90f, 45f, true, Offset(c.x - len, c.y - len), Size(2 * len, 2 * len))
                    }
                }
                for (d in 0 until 360 step 15) {
                    val a = Math.toRadians(d - 90.0)
                    val len = when { d % 90 == 0 -> 14.dp.toPx(); d % 45 == 0 -> 10.dp.toPx(); else -> 5.dp.toPx() }
                    drawLine(UiColors.dim, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                        Offset(c.x + (r - len) * cos(a).toFloat(), c.y + (r - len) * sin(a).toFloat()), 1.5.dp.toPx())
                }
                listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (t, d) ->
                    val a = Math.toRadians(d - 90.0)
                    val m = tm.measure(t, if (t == "N") north else txt)
                    val pos = Offset(c.x + (r + 14.dp.toPx()) * cos(a).toFloat() - m.size.width / 2, c.y + (r + 14.dp.toPx()) * sin(a).toFloat() - m.size.height / 2)
                    drawText(m, topLeft = pos)
                }
                bearing?.let { b ->
                    val a = Math.toRadians(b - 90)
                    val tip = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                    drawLine(UiColors.alert, c, tip, 4.dp.toPx())
                    drawCircle(UiColors.alert, 8.dp.toPx(), tip)
                }
            }
            // fixed: where the phone is facing
            val p = Path().apply { moveTo(c.x, c.y - r - 10.dp.toPx()); lineTo(c.x + 8.dp.toPx(), c.y - r + 6.dp.toPx()); lineTo(c.x - 8.dp.toPx(), c.y - r + 6.dp.toPx()); close() }
            drawPath(p, UiColors.good)
            drawCircle(UiColors.good, 4.dp.toPx(), c)
        }
        Text("Facing ${Geo.compass(heading.toDouble())}  ${heading.toInt()}°", color = UiColors.text, fontSize = 14.sp)
        if (accuracyLow) Text("Compass needs calibrating: wave the phone in a slow figure-8, away from metal and magnets.", color = UiColors.warn, fontSize = 12.sp)
    }
}
