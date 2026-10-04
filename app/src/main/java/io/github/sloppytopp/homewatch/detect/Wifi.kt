package io.github.sloppytopp.homewatch.detect

/** One Wi-Fi network as seen by a scan. [ridData] = vendor-IE payloads (after the FA:0B:BC OUI) carrying Remote ID. */
data class WifiObs(val bssid: String, val ssid: String, val level: Int, val ridData: List<ByteArray> = emptyList(), val caps: String = "", val freq: Int = 0)
data class WifiRow(val ssid: String, val bssid: String, val level: Int, val vendor: String, val klass: String, val caps: String = "", val freq: Int = 0)
data class OuiHit(val vendor: String, val klass: String)

object WifiClassifier {
    val DRONE_SSID = Regex(
        "^(DJI|TELLO|MAVIC|PHANTOM|SPARK|PARROT|ANAFI|BEBOP|SKYDIO|AUTEL|HOLYSTONE|POTENSIC|HUBSAN|FIMI|YUNEEC|RYZE|SYMA|WLT|RC-?UFO|DRONE|FPV)[-_ ]?",
        RegexOption.IGNORE_CASE,
    )
    val CAMERA_SSID = Regex(
        "(\\bIPC\\b|IPCAM|IP-?CAM|HDCAM|HD-?WIFI|MINI.?CAM|SPY|^A9[-_]|^MV[-_]|V380|YOOSEE|ICSEE|EYE4|CLOUDEDGE|^YI-|^WYZE|^ARLO|" +
            "REOLINK|HIKVISION|DAHUA|^TAPO_?CAM|^CAMS?\\b|^CAM[-_]|^CAMERA|^P2P|^GOOLINK|^HDWIFI|^BC[-_]|^IPC-|^SHD[-_]|^NVR)",
        RegexOption.IGNORE_CASE,
    )

    /** AABBCC -> hit. Loaded from assets/oui_watch.csv at startup (camera + drone makers only). */
    @Volatile var oui: Map<String, OuiHit> = emptyMap()

    fun loadCsv(lines: Sequence<String>) {
        val m = HashMap<String, OuiHit>()
        for (l in lines) {
            val p = l.split(",")
            if (p.size >= 3) m[p[0].uppercase()] = OuiHit(p[1].trim(), p[2].trim())
        }
        oui = m
    }

    /** BSSIDs of routers/APs often set the locally-administered bit for virtual APs: retry with it cleared. */
    fun lookup(bssid: String): OuiHit? {
        val raw = bssid.uppercase().replace(":", "").replace("-", "")
        if (raw.length < 6) return null
        oui[raw.substring(0, 6)]?.let { return it }
        val first = raw.substring(0, 2).toIntOrNull(16) ?: return null
        if (first and 0x02 != 0) return oui["%02X%s".format(first and 0x02.inv(), raw.substring(2, 6))]
        return null
    }

    fun klassOf(o: WifiObs): String {
        val hit = lookup(o.bssid)
        return when {
            hit?.klass == "drone" || DRONE_SSID.containsMatchIn(o.ssid) -> "drone"
            hit?.klass == "camera" || CAMERA_SSID.containsMatchIn(o.ssid) -> "camera"
            else -> "other"
        }
    }
}
