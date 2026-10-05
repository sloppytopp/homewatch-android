package io.github.sloppytopp.homewatch.scan

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import io.github.sloppytopp.homewatch.detect.ReviewApp
import io.github.sloppytopp.homewatch.detect.Stalkerware
import io.github.sloppytopp.homewatch.detect.StalkerResult

/** Reads the installed-app list once, on tap. Nothing is saved or sent anywhere. */
object StalkerScan {
    fun run(ctx: Context): StalkerResult {
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(0)
        val pkgs = apps.map { it.packageName }
        val hits = Stalkerware.match(pkgs)

        val powers = HashMap<String, MutableList<String>>()
        fun add(pkg: String, what: String) { powers.getOrPut(pkg) { ArrayList() }.let { if (what !in it) it += what } }
        fun enabled(key: String): List<String> =
            (Settings.Secure.getString(ctx.contentResolver, key) ?: "").split(":").mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
        enabled("enabled_accessibility_services").forEach { add(it, "accessibility (can read the screen and what you type)") }
        enabled("enabled_notification_listeners").forEach { add(it, "notification access (can read your messages)") }
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager)?.activeAdmins
            ?.forEach { add(it.packageName, "device admin (hard to uninstall)") }

        val review = powers.mapNotNull { (pkg, p) ->
            val ai = apps.firstOrNull { it.packageName == pkg } ?: return@mapNotNull null
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) return@mapNotNull null
            if (hits.any { it.pkg == pkg }) return@mapNotNull null
            ReviewApp(pkg, runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg), p, pm.getLaunchIntentForPackage(pkg) == null)
        }
        return StalkerResult(apps.size, hits, Stalkerware.rank(review), Stalkerware.version)
    }
}
