package io.github.sloppytopp.homewatch.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class CompassState(val heading: State<Float>, val available: Boolean)

/** Phone heading in degrees (0 = north), from the rotation-vector sensor. Hold the phone flat. No permission needed. */
@Composable
fun rememberCompass(): CompassState {
    val ctx = LocalContext.current
    val h = remember { mutableStateOf(0f) }
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rot = remember { sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    DisposableEffect(Unit) {
        val l = object : SensorEventListener {
            val r = FloatArray(9); val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values); SensorManager.getOrientation(r, o)
                h.value = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (rot != null) sm.registerListener(l, rot, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    return remember { CompassState(h, rot != null) }
}
