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
import io.github.sloppytopp.homewatch.detect.Level

/** Foreground service (Android requires a visible notification for background scanning). Soft chime on alerts, never a voice. */
class ScanService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BleScanner? = null
    private val tick = object : Runnable {
        override fun run() {
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
        Monitor.onAlert = { _, text -> postAlert(text) }
        Monitor.engine.reset()
        scanner = BleScanner(this)
        val ok = scanner!!.start()
        Monitor.engine.running = ok
        if (!ok) { Monitor.refresh(); stopSelf(); return START_NOT_STICKY }
        handler.removeCallbacks(tick); handler.post(tick)
        return START_STICKY
    }

    private fun stopScanning() {
        handler.removeCallbacks(tick)
        scanner?.stop(); scanner = null
        Monitor.engine.running = false
        Monitor.refresh()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        scanner?.stop()
        Monitor.engine.running = false
        Monitor.refresh()
        super.onDestroy()
    }

    private fun makeChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        nm().createNotificationChannel(NotificationChannel(CH_ONGOING, "Scanning status", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows that Homewatch is scanning"; setShowBadge(false)
        })
        // IMPORTANCE_DEFAULT = the system's normal soft notification sound (no voice) + vibration. User can change it in Settings.
        nm().createNotificationChannel(NotificationChannel(CH_ALERTS, "Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A soft chime when something is flagged"
        })
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun builder(channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else @Suppress("DEPRECATION") Notification.Builder(this)

    private fun ongoing(text: String): Notification = builder(CH_ONGOING)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Homewatch").setContentText(text)
        .setOngoing(true).setContentIntent(openApp())
        .addAction(Notification.Action.Builder(null, "Stop", PendingIntent.getService(
            this, 1, Intent(this, ScanService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)).build())
        .build()

    private fun postAlert(text: String) {
        nm().notify(ALERT_ID, builder(CH_ALERTS).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Homewatch").setContentText(text).setAutoCancel(true).setContentIntent(openApp()).build())
    }

    companion object {
        const val ACTION_STOP = "io.github.sloppytopp.homewatch.STOP"
        private const val CH_ONGOING = "ongoing"
        private const val CH_ALERTS = "alerts"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2
    }
}
