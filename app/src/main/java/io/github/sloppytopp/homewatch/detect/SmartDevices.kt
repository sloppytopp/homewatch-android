package io.github.sloppytopp.homewatch.detect

enum class SmartGroup(val title: String, val blurb: String) {
    AMAZON("Amazon (Echo, Ring, Fire TV, Sidewalk)", "Amazon devices can share a slice of your internet connection with neighbours' devices through Sidewalk unless you turn it off."),
    GOOGLE("Google (Nest, Home, Chromecast)", "Speakers, displays and cameras with microphones or cameras built in."),
    SAMSUNG("Samsung (SmartThings, TVs, SmartTag)", "TVs, hubs and tags that report to Samsung's cloud."),
    CAMERA("Cameras and doorbells", "Anything that looks like a camera or video doorbell by its network name or maker."),
    IOT("Smart plugs, bulbs and other gadgets", "Cheap internet-connected gadgets: often unmanaged, rarely updated."),
    MEDIA("TVs and streaming boxes", "Roku, Sonos, Apple TV and similar."),
}

data class SmartItem(val group: SmartGroup, val title: String, val sub: String, val rssi: Int, val addr: String)

/**
 * Sorts what is around you into broad maker groups, using only network names, Bluetooth names and the company id in the advertisement.
 * It is a hint, not an inventory: a device that hides its name or uses a generic one will not appear here, and a name match is not proof of what it is.
 */
object SmartDevices {
    private val AMAZON = Regex("(^|[^a-z])(echo|alexa|ring[-_ ]|amazon|fire[-_ ]?(tv|stick)|kindle|blink)", RegexOption.IGNORE_CASE)
    private val GOOGLE = Regex("(nest|google[-_ ]?home|chromecast|google ?(mini|hub)|^GHome)", RegexOption.IGNORE_CASE)
    private val SAMSUNG = Regex("(smartthings|^\\[?(TV|AV)\\]? ?samsung|samsung|galaxy ?(tag|smart)|smarttag)", RegexOption.IGNORE_CASE)
    private val CAMERA = Regex("(wyze|arlo|eufy|reolink|tapo[-_ ]?cam|doorbell|cam(era)?[-_ ]|ipcam|hikvision|dahua|yi[-_ ]|ezviz|blink)", RegexOption.IGNORE_CASE)
    private val IOT = Regex("(tuya|smartlife|shelly|kasa|tp-?link_smart|esp[-_ ]?\\d|^esp_|tasmota|govee|hue|lifx|meross|sonoff|switchbot|wiz_|bulb|plug)", RegexOption.IGNORE_CASE)
    private val MEDIA = Regex("(roku|sonos|apple ?tv|bravia|vizio|lg ?webos|\\[lg\\]|firetv)", RegexOption.IGNORE_CASE)

    fun classify(name: String, company: String = "", klass: String = ""): SmartGroup? {
        val n = name.trim()
        return when {
            klass == "camera" || CAMERA.containsMatchIn(n) -> SmartGroup.CAMERA
            AMAZON.containsMatchIn(n) || company.startsWith("Amazon") -> SmartGroup.AMAZON
            GOOGLE.containsMatchIn(n) -> SmartGroup.GOOGLE
            SAMSUNG.containsMatchIn(n) || company.startsWith("Samsung") && n.isNotEmpty() -> SmartGroup.SAMSUNG
            MEDIA.containsMatchIn(n) -> SmartGroup.MEDIA
            IOT.containsMatchIn(n) || company.contains("Espressif") || company.contains("IoT") -> SmartGroup.IOT
            else -> null
        }
    }

    fun fromSnapshot(s: Snapshot): List<SmartItem> {
        val out = ArrayList<SmartItem>()
        s.wifi.forEach { w ->
            classify(w.ssid, "", w.klass)?.let { out += SmartItem(it, w.ssid, "Wi-Fi · ${w.bssid}${if (w.vendor.isNotEmpty()) " · ${w.vendor}" else ""}", w.level, "wifi:${w.bssid}") }
        }
        s.inspect.forEach { r ->
            classify(r.name, r.company)?.let { out += SmartItem(it, r.name.ifEmpty { r.company }, "Bluetooth · ${r.addr}${if (r.company.isNotEmpty()) " · ${r.company}" else ""}", r.rssi, r.addr) }
        }
        return out.sortedByDescending { it.rssi }
    }
}
