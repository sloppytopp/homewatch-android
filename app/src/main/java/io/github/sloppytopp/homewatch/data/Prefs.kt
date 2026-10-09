package io.github.sloppytopp.homewatch.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest
import java.security.SecureRandom
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
    /** What the UI should treat as home: a fictional spot in sample-data mode, otherwise the user's saved home. */
    val displayHome: Home? get() = if (io.github.sloppytopp.homewatch.scan.Demo.active) io.github.sloppytopp.homewatch.scan.Demo.FAKE_HOME else home
    var mySsids by mutableStateOf<Set<String>>(emptySet()); private set
    var welcomed by mutableStateOf(false); private set
    var myDevices by mutableStateOf<Set<String>>(emptySet()); private set
    var discreet by mutableStateOf(false); private set
    var rooms by mutableStateOf<List<String>>(emptyList()); private set
    var hasPin by mutableStateOf(false); private set
    var launcher by mutableStateOf(0); private set   // 0 N0RMA, 1 Notes, 2 Weather
    /** Physical-inspection checklist: room -> ids of the items ticked off. */
    var inspected by mutableStateOf<Map<String, Set<String>>>(emptyMap()); private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("homewatch", Context.MODE_PRIVATE)
        night = sp.getBoolean("night", false)
        alertStyle = runCatching { AlertStyle.valueOf(sp.getString("alert", "CHIME")!!) }.getOrDefault(AlertStyle.CHIME)
        if (sp.contains("home_lat")) home = Home(Double.fromBits(sp.getLong("home_lat", 0)), Double.fromBits(sp.getLong("home_lon", 0)), sp.getString("home_note", "") ?: "")
        welcomed = sp.getBoolean("welcomed", false)
        myDevices = sp.getStringSet("my_devices", emptySet()) ?: emptySet()
        discreet = sp.getBoolean("discreet", false)
        rooms = (sp.getString("rooms", "") ?: "").split("|").filter { it.isNotBlank() }
        hasPin = sp.contains("pin_hash")
        launcher = sp.getInt("launcher", 0)
        mySsids = sp.getStringSet("my_ssids", emptySet()) ?: emptySet()
        inspected = sp.all.filterKeys { it.startsWith("insp_") }.mapKeys { it.key.removePrefix("insp_") }
            .mapValues { (it.value as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet() }
    }

    fun toggleInspection(room: String, id: String) {
        val cur = inspected[room] ?: emptySet()
        val next = if (id in cur) cur - id else cur + id
        inspected = inspected + (room to next); sp.edit().putStringSet("insp_$room", next).apply()
    }
    fun resetInspection(room: String) { inspected = inspected - room; sp.edit().remove("insp_$room").apply() }
    fun clearInspections() { sp.edit().also { e -> inspected.keys.forEach { e.remove("insp_$it") } }.apply(); inspected = emptyMap() }

    fun isMine(addr: String) = addr in myDevices
    fun markMine(addr: String) { myDevices = myDevices + addr; sp.edit().putStringSet("my_devices", myDevices).apply() }
    fun unmarkMine(addr: String) { myDevices = myDevices - addr; sp.edit().putStringSet("my_devices", myDevices).apply() }
    fun saveWelcomed() { welcomed = true; sp.edit().putBoolean("welcomed", true).apply() }
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

    fun addRoom(name: String) { val n = name.trim().take(24).replace("|", ""); if (n.isNotEmpty() && n !in rooms) { rooms = rooms + n; sp.edit().putString("rooms", rooms.joinToString("|")).apply() } }
    fun removeRoom(name: String) { resetInspection(name); rooms = rooms - name; sp.edit().putString("rooms", rooms.joinToString("|")).apply() }

    fun saveDiscreet(v: Boolean) { discreet = v; sp.edit().putBoolean("discreet", v).apply() }

    private fun hash(salt: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    /** PIN is stored only as a salted hash on this phone. There is no recovery: forgetting it means clearing app data. */
    fun savePin(pin: String?) {
        if (pin == null) { sp.edit().remove("pin_hash").remove("pin_salt").apply(); hasPin = false; return }
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        sp.edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply(); hasPin = true
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pin_salt", null) ?: return false
        val want = sp.getString("pin_hash", null) ?: return false
        return MessageDigest.isEqual(want.toByteArray(), hash(salt, pin).toByteArray())
    }

    /** Switch which launcher entry (name + icon) is shown. Only one alias is enabled at a time. */
    fun saveLauncher(ctx: Context, which: Int) {
        val names = listOf(".LauncherDefault", ".LauncherNotes", ".LauncherWeather")
        names.forEachIndexed { i, n ->
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx.packageName, ctx.packageName + n),
                if (i == which) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }
        launcher = which; sp.edit().putInt("launcher", which).apply()
    }
}
