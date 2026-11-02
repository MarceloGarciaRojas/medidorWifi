package cl.medidorwifi

data class Radio(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequency: Int,
    val observed: Long,
    val speed: Int? = null,
    val ip: String? = null,
) {
    val band
        get() =
            when {
                frequency >= 5925 -> "6 GHz"
                frequency >= 4900 -> "5 GHz"
                frequency > 0 -> "2.4 GHz"
                else -> "No disponible"
            }

    val channel
        get() =
            when {
                frequency == 2484 -> 14
                frequency in 2412..2472 -> (frequency - 2407) / 5
                frequency == 5935 -> 2
                frequency >= 5955 -> (frequency - 5950) / 5
                frequency in 5000..5895 -> (frequency - 5000) / 5
                else -> null
            }
}

fun validBssid(value: String?) =
    value != null &&
        value != "02:00:00:00:00:00" &&
        value != "00:00:00:00:00:00" &&
        Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(value)

fun quality(rssi: Int?, weak: Int = -75, good: Int = -65) =
    when {
        rssi == null -> "Sin lectura reciente"
        rssi < weak -> "Débil"
        rssi < good -> "Intermedia"
        else -> "Buena"
    }

data class Transition(
    val from: Radio,
    val to: Radio,
    val dwell: Long,
    val interval: Long,
    val gap: Long?,
)

class RoamingTracker {
    private var previous: Radio? = null
    private var joined = 0L
    private var last = 0L
    private var lost: Long? = null

    fun reset() {
        previous = null
        lost = null
        joined = 0
        last = 0
    }

    fun observe(radio: Radio?, now: Long): Transition? {
        if (radio == null) {
            if (previous != null && lost == null) lost = now
            return null
        }
        val old = previous
        val result =
            if (old != null && old.bssid != radio.bssid)
                Transition(old, radio, now - joined, now - last, lost?.let { now - it })
            else null
        if (old == null || old.bssid != radio.bssid) joined = now
        previous = radio
        last = now
        lost = null
        return result
    }
}

class LateRoaming {
    private var candidate: String? = null
    private var since = 0L
    private var alerted = false

    fun reset() {
        candidate = null
        alerted = false
    }

    fun observe(
        current: Radio?,
        nearby: List<Radio>,
        now: Long,
        weak: Int,
        delta: Int,
        duration: Long,
    ): Radio? {
        val better =
            current
                ?.takeIf { it.rssi < weak && now - it.observed <= 15000 }
                ?.let { c ->
                    nearby
                        .filter {
                            it.ssid == c.ssid &&
                                it.ssid.isNotBlank() &&
                                it.bssid != c.bssid &&
                                now - it.observed in 0..15000 &&
                                it.rssi - c.rssi >= delta
                        }
                        .maxByOrNull { it.rssi }
                }
        if (better == null) {
            reset()
            return null
        }
        val key = "${current?.bssid}/${better.bssid}"
        if (candidate != key) {
            candidate = key
            since = now
            alerted = false
        }
        if (!alerted && now - since >= duration) {
            alerted = true
            return better
        }
        return null
    }
}

fun csvCell(s: String): String =
    "\"" +
        (if (s.firstOrNull() in listOf('=', '+', '-', '@')) "'" else "") +
        s.replace("\"", "\"\"") +
        "\""
