package io.github.sloppytopp.homewatch.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AlertStyle { CHIME, VIBRATE, SILENT }
data class Home(val lat: Double, val lon: Double, val note: String = "")

/** On-device settings. Nothing here is ever sent anywhere. */
object Prefs {
    private lateinit var sp: SharedPreferences

    var night by mutableStateOf(false); private set
    var alertStyle by mutableStateOf(AlertStyle.CHIME); private set
    var home by mutableStateOf<Home?>(null); private set
    var mySsids by mutableStateOf<Set<String>>(emptySet()); private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("homewatch", Context.MODE_PRIVATE)
        night = sp.getBoolean("night", false)
        alertStyle = runCatching { AlertStyle.valueOf(sp.getString("alert", "CHIME")!!) }.getOrDefault(AlertStyle.CHIME)
        if (sp.contains("home_lat")) home = Home(Double.fromBits(sp.getLong("home_lat", 0)), Double.fromBits(sp.getLong("home_lon", 0)), sp.getString("home_note", "") ?: "")
        mySsids = sp.getStringSet("my_ssids", emptySet()) ?: emptySet()
    }

    fun saveNight(v: Boolean) { night = v; sp.edit().putBoolean("night", v).apply() }
    fun saveAlertStyle(v: AlertStyle) { alertStyle = v; sp.edit().putString("alert", v.name).apply() }
    fun saveHome(h: Home?) {
        home = h
        sp.edit().apply {
            if (h == null) { remove("home_lat"); remove("home_lon"); remove("home_note") }
            else { putLong("home_lat", h.lat.toBits()); putLong("home_lon", h.lon.toBits()); putString("home_note", h.note) }
        }.apply()
    }
    fun learnSsid(ssid: String) {
        if (ssid.isBlank() || ssid == "<unknown ssid>" || ssid in mySsids) return
        mySsids = mySsids + ssid; sp.edit().putStringSet("my_ssids", mySsids).apply()
    }
    fun forgetSsid(ssid: String) { mySsids = mySsids - ssid; sp.edit().putStringSet("my_ssids", mySsids).apply() }
}
