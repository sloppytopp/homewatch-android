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
import io.github.sloppytopp.homewatch.detect.AngleSmoother

class CompassState(val heading: State<Float>, val available: Boolean, val accuracy: State<Int>)

/** Smoothed phone heading in degrees (0 = north) from the rotation-vector sensor. Hold the phone flat. No permission needed. */
@Composable
fun rememberCompass(): CompassState {
    val ctx = LocalContext.current
    val h = remember { mutableStateOf(0f) }
    val acc = remember { mutableStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rot = remember { sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    DisposableEffect(Unit) {
        val smooth = AngleSmoother()
        val l = object : SensorEventListener {
            val r = FloatArray(9); val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values); SensorManager.getOrientation(r, o)
                h.value = smooth.update((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) { acc.value = a }
        }
        if (rot != null) sm.registerListener(l, rot, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(l) }
    }
    return remember { CompassState(h, rot != null, acc) }
}
