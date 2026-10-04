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
import io.github.sloppytopp.homewatch.data.AlertStyle
import io.github.sloppytopp.homewatch.data.Prefs
import io.github.sloppytopp.homewatch.detect.Level

/** Foreground service (Android requires a visible notification for background scanning). Soft chime on alerts, never a voice. */
class ScanService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BleScanner? = null
    private var wifi: WifiScanner? = null
    private var wifiN = 0
    private val tick = object : Runnable {
        override fun run() {
            if (wifiN++ % (if (Monitor.hunt != null) 5 else 10) == 0) wifi?.poke()   // ~30 s, or ~15 s while hunting
            Monitor.refresh()
            val s = Monitor.snapshot
            nm().notify(ONGOING_ID, ongoing(statusLine(s.drone.level, s.tracker.level)))
            handler.postDelayed(this, 3000)
        }
    }

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun statusLine(d: Level, t: Level) =
        if (d == Level.ALERT || t == Level.ALERT) "Alert - open Homewatch"
        else if (d == Level.WATCH || t == Level.WATCH) "Watching something - open Homewatch" else "Scanning - nothing flagged"

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
            description = "Shows that Homewatch is scanning"; setShowBadge(false)
        })
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

    private fun ongoing(text: String): Notification = builder(CH_ONGOING)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Homewatch").setContentText(text)
        .setOngoing(true).setContentIntent(openApp())
        .addAction(Notification.Action.Builder(null, "Stop", PendingIntent.getService(
            this, 1, Intent(this, ScanService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)).build())
        .build()

    private fun postAlert(domain: String, text: String) {
        val ch = when (Prefs.alertStyle) { AlertStyle.CHIME -> CH_ALERTS; AlertStyle.VIBRATE -> CH_VIBRATE; AlertStyle.SILENT -> CH_SILENT }
        nm().notify(ALERT_ID, builder(ch).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Homewatch").setContentText(text + " Tap to find it.").setAutoCancel(true).setContentIntent(huntIntent(domain)).build())
    }

    companion object {
        const val ACTION_STOP = "io.github.sloppytopp.homewatch.STOP"
        private const val CH_ONGOING = "ongoing"
        private const val CH_ALERTS = "alerts"
        private const val CH_VIBRATE = "alerts_vibrate"
        private const val CH_SILENT = "alerts_silent"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2
    }
}
