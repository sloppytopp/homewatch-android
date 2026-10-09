package io.github.sloppytopp.homewatch.scan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.sloppytopp.homewatch.MainActivity
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import io.github.sloppytopp.homewatch.data.AlertStyle
import io.github.sloppytopp.homewatch.data.Store
import io.github.sloppytopp.homewatch.detect.SurveyPoint
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.detect.Level

/** Foreground service (Android requires a visible notification for background scanning). Soft chime on alerts, never a voice. */
class ScanService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BleScanner? = null
    private var wifi: WifiScanner? = null
    private var wifiN = 0
    private var locListener: LocationListener? = null
    private var lastWifiLogged = 0L
    private val surveyGate = io.github.sloppytopp.homewatch.detect.SurveyGate()

    @android.annotation.SuppressLint("MissingPermission")
    private fun syncSurvey() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Monitor.surveying && locListener == null) {
            val l = LocationListener { loc -> Monitor.lastLoc = loc }
            try {
                val p = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                lm.requestLocationUpdates(p, 1000L, 0f, l); locListener = l; Monitor.surveyNote = "Waiting for a GPS fix..."
            } catch (e: Exception) { Monitor.surveying = false; Monitor.surveyNote = "Couldn't use location (permission or Location switch off)." }
        } else if (!Monitor.surveying && locListener != null) {
            lm.removeUpdates(locListener!!); locListener = null
        }
        if (locListener == null) return
        val loc: Location = Monitor.lastLoc ?: return
        val now = System.currentTimeMillis()
        if (now - loc.time > 15_000) { Monitor.surveyNote = "GPS position is stale - step outside or near a window."; return }
        Monitor.surveyNote = null
        val s = Monitor.snapshot
        val pts = ArrayList<SurveyPoint>()
        if (s.wifiAt != 0L && s.wifiAt != lastWifiLogged) {
            lastWifiLogged = s.wifiAt
            s.wifi.forEach { pts += SurveyPoint(now, "wifi", "wifi:${it.bssid}", it.ssid, it.level, loc.latitude, loc.longitude, loc.accuracy, it.caps, it.freq, it.vendor, it.klass) }
        }
        s.trackers.forEach { pts += SurveyPoint(now, "tracker", "ble:${it.addr}", it.label, it.rssi, loc.latitude, loc.longitude, loc.accuracy) }
        if (Monitor.inspecting) s.inspect.filter { it.name.isNotEmpty() || it.company.isNotEmpty() }.forEach {
            pts += SurveyPoint(now, "ble", "ble:${it.addr}", it.name.ifEmpty { it.company }, it.rssi, loc.latitude, loc.longitude, loc.accuracy)
        }
        val keep = pts.filter { surveyGate.keep(it) }
        if (keep.isNotEmpty()) { Store.addSurvey(keep); Monitor.surveyCount += keep.size }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (wifiN++ % (if (Monitor.hunt != null || Monitor.fastWifi) 5 else 10) == 0) wifi?.poke()   // ~30 s, or ~15 s while hunting
            Monitor.refresh()
            runCatching { syncSurvey() }
            val s = Monitor.snapshot
            nm().notify(ONGOING_ID, ongoing(statusLine(s.drone.level, s.tracker.level)))
            handler.postDelayed(this, 3000)
        }
    }

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun statusLine(d: Level, t: Level) =
        if (d == Level.ALERT || t == Level.ALERT) "Alert - open N0RMA"
        else if (d == Level.WATCH || t == Level.WATCH) "Watching something - open N0RMA" else "Scanning - nothing flagged"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopScanning(); return START_NOT_STICKY }
        makeChannels()
        val n = ongoing("Starting...")
        if (Build.VERSION.SDK_INT >= 29) startForeground(ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(ONGOING_ID, n)
        Monitor.onAlert = { domain, text -> postAlert(domain, text) }
        Monitor.engine.reset()
        scanner = BleScanner(this)
        val ok = scanner!!.start()
        wifi = WifiScanner(this).also { it.start() }
        Monitor.engine.running = ok
        if (!ok) { Monitor.refresh(); stopSelf(); return START_NOT_STICKY }
        handler.removeCallbacks(tick); handler.post(tick)
        return START_STICKY
    }

    private fun stopScanning() {
        handler.removeCallbacks(tick)
        scanner?.stop(); scanner = null
        wifi?.stop(); wifi = null
        Monitor.surveying = false; runCatching { syncSurvey() }
        Monitor.engine.running = false
        Monitor.refresh()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        scanner?.stop()
        wifi?.stop()
        Monitor.engine.running = false
        Monitor.refresh()
        super.onDestroy()
    }

    private fun makeChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        nm().createNotificationChannel(NotificationChannel(CH_ONGOING, "Scanning status", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows that N0RMA is scanning"; setShowBadge(false)
        })
        // Generic channel names for discreet mode (channel names are visible in Android's notification settings).
        nm().createNotificationChannel(NotificationChannel(CH_D_ONGOING, "Background", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        nm().createNotificationChannel(NotificationChannel(CH_D_CHIME, "Updates", NotificationManager.IMPORTANCE_DEFAULT))
        nm().createNotificationChannel(NotificationChannel(CH_D_VIBRATE, "Updates (vibrate)", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(true) })
        nm().createNotificationChannel(NotificationChannel(CH_D_SILENT, "Updates (quiet)", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
        // Three alert styles; the user picks one in Settings. Never a voice.
        nm().createNotificationChannel(NotificationChannel(CH_ALERTS, "Alerts (soft chime)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The system's soft notification sound when something is flagged"
        })
        nm().createNotificationChannel(NotificationChannel(CH_VIBRATE, "Alerts (vibrate only)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Vibration only - no sound"; setSound(null, null); enableVibration(true)
        })
        nm().createNotificationChannel(NotificationChannel(CH_SILENT, "Alerts (silent)", NotificationManager.IMPORTANCE_LOW).apply {
            description = "No sound or vibration - check the screen"; setSound(null, null); enableVibration(false)
        })
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun huntIntent(domain: String) = PendingIntent.getActivity(
        this, 100 + domain.hashCode().and(0xFF), Intent(this, MainActivity::class.java).putExtra("hunt", domain).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun builder(channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else @Suppress("DEPRECATION") Notification.Builder(this)

    private fun ongoing(text: String): Notification = builder(if (Prefs.discreet) CH_D_ONGOING else CH_ONGOING)
        .setSmallIcon(if (Prefs.discreet) android.R.drawable.stat_notify_sync_noanim else android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle(if (Prefs.discreet) "Background service" else "N0RMA")
        .setContentText(if (Prefs.discreet) "Running" else text)
        .setVisibility(if (Prefs.discreet) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PRIVATE)
        .setOngoing(true).setContentIntent(openApp())
        .addAction(Notification.Action.Builder(null, "Stop", PendingIntent.getService(
            this, 1, Intent(this, ScanService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)).build())
        .build()

    private fun postAlert(domain: String, text: String) {
        val d = Prefs.discreet
        val ch = when (Prefs.alertStyle) {
            AlertStyle.CHIME -> if (d) CH_D_CHIME else CH_ALERTS
            AlertStyle.VIBRATE -> if (d) CH_D_VIBRATE else CH_VIBRATE
            AlertStyle.SILENT -> if (d) CH_D_SILENT else CH_SILENT
        }
        nm().notify(ALERT_ID, builder(ch)
            .setSmallIcon(if (d) android.R.drawable.stat_notify_sync_noanim else android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(if (d) "Update" else "N0RMA").setContentText(if (d) "Tap to open" else "$text Tap to find it.")
            .setVisibility(if (d) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setContentIntent(huntIntent(domain)).build())
    }

    companion object {
        const val ACTION_STOP = "io.github.sloppytopp.homewatch.STOP"
        private const val CH_ONGOING = "ongoing"
        private const val CH_D_ONGOING = "d_ongoing"
        private const val CH_D_CHIME = "d_chime"
        private const val CH_D_VIBRATE = "d_vibrate"
        private const val CH_D_SILENT = "d_silent"
        private const val CH_ALERTS = "alerts"
        private const val CH_VIBRATE = "alerts_vibrate"
        private const val CH_SILENT = "alerts_silent"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2
    }
}
