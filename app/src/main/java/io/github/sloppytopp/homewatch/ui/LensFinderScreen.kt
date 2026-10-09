package io.github.sloppytopp.homewatch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.github.sloppytopp.homewatch.detect.LensResult
import io.github.sloppytopp.homewatch.scan.LensCamera

/** Full-screen lens finder. Dim the room, sweep slowly: rings mark small bright points that might be lenses. */
@Composable
fun LensFinder(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(UiColors.dialogBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lens finder", color = UiColors.text, fontSize = 20.sp)
            if (!granted) {
                Text("This uses the rear camera and its flash as a torch to look for tiny bright reflections from lenses. Pictures are analysed on the phone and thrown away: nothing is saved or sent.", color = UiColors.dim, fontSize = 13.sp)
                TextButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow the camera") }
            } else LensBody()
            TextButton(onClick = onClose) { Text("Close (turns the light off)") }
        }
    }
}

@Composable
private fun LensBody() {
    val ctx = LocalContext.current
    var result by remember { mutableStateOf(LensResult(emptyList(), 0)) }
    var error by remember { mutableStateOf<String?>(null) }
    var cam by remember { mutableStateOf<LensCamera?>(null) }
    DisposableEffect(Unit) { onDispose { cam?.stop() } }

    Text("1. Dim the room (close curtains, lights off). 2. Hold the phone at eye level and sweep slowly across shelves, walls, clocks, detectors and vents. " +
        "3. Rings mark small bright points on a dark background. A real lens usually flares as a steady point from certain angles, so move a little and see if it stays.",
        color = UiColors.dim, fontSize = 12.sp)
    if (result.tooBright) Text("The room is too bright: ordinary reflections will drown out lens glints. Dim the lights.", color = UiColors.warn, fontSize = 13.sp)
    error?.let { Text(it, color = UiColors.warn, fontSize = 13.sp) }

    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
            TextureView(c).also { tv ->
                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val lc = LensCamera(ctx, { result = it }, { error = it }); cam = lc
                        lc.start(st)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { cam?.stop(); cam = null; return true }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        })
        Canvas(Modifier.fillMaxSize()) {
            val rot = cam?.sensorRotation ?: 90
            result.spots.forEachIndexed { i, s ->
                // sensor image (landscape) -> upright portrait on screen
                val (nx, ny) = when (rot) { 90 -> (1f - s.y) to s.x; 270 -> s.y to (1f - s.x); 180 -> (1f - s.x) to (1f - s.y); else -> s.x to s.y }
                val c = Offset(nx * size.width, ny * size.height)
                val r = (s.radius * size.height).coerceIn(10.dp.toPx(), 40.dp.toPx())
                drawCircle(if (i == 0) UiColors.alert else UiColors.watch, r, c, style = Stroke(3.dp.toPx()))
            }
        }
    }
    Text(when {
        result.spots.isEmpty() -> "No bright points found yet. Keep sweeping slowly."
        else -> "${result.spots.size} bright point${if (result.spots.size > 1) "s" else ""} found. Check the strongest (red ring) by eye: lenses, but also screws, jewellery and glass, glint."
    }, color = UiColors.text, fontSize = 13.sp)
    Text("A phone can't see lenses that aren't lit or are shielded, and finding nothing doesn't mean nothing is there. If you find something you don't own: leave it, photograph it, and talk to an advocate or police first.", color = UiColors.faint, fontSize = 11.sp)
}
