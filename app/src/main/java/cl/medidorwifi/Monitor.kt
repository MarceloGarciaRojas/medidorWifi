package cl.medidorwifi

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class Live(
    val current: Radio? = null,
    val nearby: List<Radio> = emptyList(),
    val message: String = "Preparando medición",
    val now: Long = 0,
    val notice: String? = null,
    val points: List<Radio> = emptyList(),
    val used: Set<String> = emptySet(),
)

class Monitor(app: Application) : AndroidViewModel(app) {
    val db = Room.databaseBuilder(app, LocalDatabase::class.java, "wifi.db").build()
    val records = db.records()
    val prefs = Preferences(app)
    val settings = prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val antennas = records.antennas().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val readings = records.readings().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val events = records.events().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val live = MutableStateFlow(Live())
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val tracker = RoamingTracker()
    private val late = LateRoaming()

    fun name(bssid: String) = antennas.value.firstOrNull { it.bssid == bssid }?.name ?: bssid

    fun setting(key: String, value: Boolean) {
        viewModelScope.launch { prefs.set(key, value) }
    }

    fun setting(key: String, value: String) {
        viewModelScope.launch { prefs.set(key, value) }
    }

    fun setting(key: String, value: Int) {
        viewModelScope.launch { prefs.set(key, value) }
    }

    fun register(a: Antenna) {
        viewModelScope.launch {
            db.withTransaction {
                val old = antennas.value.firstOrNull { it.bssid == a.bssid }
                if (old != null) records.renameGroup(old.name, a.name, a.zone, a.managementIp)
                records.register(a)
            }
        }
    }

    fun remove(a: Antenna) {
        viewModelScope.launch { records.remove(a) }
    }

    fun save(zone: String) {
        val r = live.value.current ?: return
        viewModelScope.launch {
            records.save(
                Reading(
                    time = System.currentTimeMillis(),
                    zone = zone.trim(),
                    name = name(r.bssid),
                    bssid = r.bssid,
                    ssid = r.ssid,
                    rssi = r.rssi,
                    frequency = r.frequency,
                    speed = r.speed,
                    ip = r.ip,
                )
            )
        }
    }

    fun clear() {
        viewModelScope.launch {
            db.withTransaction {
                records.clearReadings()
                records.clearEvents()
            }
        }
    }

    fun prune() {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - 30L * 86400000
            records.pruneReadings(cutoff)
            records.pruneEvents(cutoff)
        }
    }

    fun csv(): String =
        "fecha,zona,antena,bssid,ssid,rssi,frecuencia,velocidad,ip\r\n" +
            readings.value.joinToString("\r\n") { r ->
                listOf(
                        r.time,
                        r.zone,
                        r.name,
                        r.bssid,
                        r.ssid,
                        r.rssi,
                        r.frequency,
                        r.speed ?: "",
                        r.ip ?: "",
                    )
                    .joinToString(",") { csvCell(it.toString()) }
            }

    @Suppress("DEPRECATION", "MissingPermission")
    suspend fun monitor() {
        tracker.reset()
        late.reset()
        var scanAt = -30000L
        var toneAt = 0L
        var hadWifi: Boolean? = null
        var lostAt: Long? = null
        var tone: ToneGenerator? = null
        var points = emptyList<Radio>()
        val used = linkedSetOf<String>()
        try {
            while (currentCoroutineContext().isActive) {
                val now = SystemClock.elapsedRealtime()
                val allowed =
                    getApplication<Application>()
                        .checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                val location =
                    getApplication<Application>()
                        .getSystemService(LocationManager::class.java)
                        .isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                        getApplication<Application>()
                            .getSystemService(LocationManager::class.java)
                            .isProviderEnabled(LocationManager.GPS_PROVIDER)
                var current: Radio? = null
                var nearby = emptyList<Radio>()
                var message = "Las lecturas cercanas pueden estar limitadas por Android"
                if (!allowed) message = "Permite ubicación precisa para consultar las redes Wi-Fi."
                else if (!wifi.isWifiEnabled)
                    message = "Wi-Fi desactivado. Actívalo en los ajustes del sistema."
                else if (!location)
                    message = "Activa los servicios de ubicación para consultar BSSID y escaneos."
                else
                    try {
                        val info = wifi.connectionInfo
                        if (validBssid(info.bssid) && info.rssi in -126..-1) {
                            val ip =
                                connectivity.allNetworks
                                    .firstOrNull {
                                        connectivity
                                            .getNetworkCapabilities(it)
                                            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ==
                                            true
                                    }
                                    ?.let {
                                        connectivity
                                            .getLinkProperties(it)
                                            ?.linkAddresses
                                            ?.firstOrNull { a ->
                                                a.address is java.net.Inet4Address
                                            }
                                            ?.address
                                            ?.hostAddress
                                    }
                            current =
                                Radio(
                                    info.bssid.lowercase(),
                                    info.ssid.removeSurrounding("\""),
                                    info.rssi,
                                    info.frequency,
                                    now,
                                    info.linkSpeed.takeIf { it > 0 },
                                    ip,
                                )
                        } else message = "Sin conexión Wi-Fi o BSSID no disponible"
                        if (now - scanAt >= 30000) {
                            scanAt = now
                            if (!wifi.startScan())
                                message =
                                    "Escaneo restringido por Android; se muestran las últimas lecturas disponibles"
                        }
                        nearby =
                            wifi.scanResults
                                .filter { validBssid(it.BSSID) }
                                .map {
                                    Radio(
                                        it.BSSID.lowercase(),
                                        it.SSID,
                                        it.level,
                                        it.frequency,
                                        it.timestamp / 1000,
                                    )
                                }
                                .sortedByDescending { it.rssi }
                    } catch (_: SecurityException) {
                        message =
                            "Android no permite acceder a estas lecturas. Revisa los permisos."
                    }
                if (allowed && location && wifi.isWifiEnabled) {
                    val connected =
                        connectivity.allNetworks.any {
                            connectivity
                                .getNetworkCapabilities(it)
                                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                        }
                    if (connected && current == null) tracker.reset()
                    if (hadWifi == true && !connected) {
                        lostAt = now
                        records.event(
                            Event(
                                time = System.currentTimeMillis(),
                                kind = "Pérdida de conexión Wi-Fi observada",
                                source = "",
                                destination = "",
                                fromRssi = null,
                                toRssi = null,
                                dwell = null,
                                interval = null,
                                gap = null,
                            )
                        )
                    }
                    if (hadWifi == false && connected && lostAt != null) {
                        records.event(
                            Event(
                                time = System.currentTimeMillis(),
                                kind = "Conexión Wi-Fi recuperada (intervalo observado)",
                                source = "",
                                destination = current?.bssid ?: "No disponible",
                                fromRssi = null,
                                toRssi = current?.rssi,
                                dwell = null,
                                interval = null,
                                gap = now - lostAt!!,
                            )
                        )
                        lostAt = null
                    }
                    hadWifi = connected
                } else {
                    tracker.reset()
                    late.reset()
                    hadWifi = null
                    lostAt = null
                }
                var notice: String? = null
                tracker.observe(current, now)?.let { t ->
                    records.event(
                        Event(
                            time = System.currentTimeMillis(),
                            kind =
                                if (t.gap != null) "Posible interrupción"
                                else "Información insuficiente",
                            source = t.from.bssid,
                            destination = t.to.bssid,
                            fromRssi = t.from.rssi,
                            toRssi = t.to.rssi,
                            dwell = t.dwell,
                            interval = t.interval,
                            gap = t.gap,
                            sourceName = name(t.from.bssid),
                            destinationName = name(t.to.bssid),
                        )
                    )
                    notice = "Cambio de antena: ${name(t.from.bssid)} → ${name(t.to.bssid)}"
                    if (settings.value.vibration)
                        getApplication<Application>()
                            .getSystemService(Vibrator::class.java)
                            .vibrate(
                                VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                }
                val s = settings.value
                late.observe(current, nearby, now, s.weak, s.delta, s.persistence * 1000L)?.let {
                    better ->
                    notice =
                        "Posible roaming tardío: ${current?.rssi} dBm → ${better.rssi} dBm. La asociación también depende de banda, dispositivo y políticas."
                    records.event(
                        Event(
                            time = System.currentTimeMillis(),
                            kind = "Posible roaming tardío",
                            source = current!!.bssid,
                            destination = better.bssid,
                            fromRssi = current!!.rssi,
                            toRssi = better.rssi,
                            dwell = null,
                            interval = null,
                            gap = null,
                        )
                    )
                }
                val target =
                    if (s.pinned.isBlank()) current
                    else
                        current?.takeIf { it.bssid == s.pinned }
                            ?: nearby.firstOrNull {
                                it.bssid == s.pinned && now - it.observed in 0..15000
                            }
                if (
                    target != null &&
                        points.lastOrNull()?.let {
                            it.bssid == target.bssid && it.observed == target.observed
                        } != true
                )
                    points = (points.filter { it.bssid == target.bssid } + target).takeLast(90)
                if (
                    s.beep &&
                        target != null &&
                        now - toneAt >= (2200 - (target.rssi + 100).coerceIn(0, 70) * 22)
                ) {
                    if (tone == null)
                        tone =
                            runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 25) }.getOrNull()
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
                    toneAt = now
                }
                if (!s.beep) {
                    tone?.release()
                    tone = null
                }
                current?.let { used.add(it.bssid) }
                live.value = Live(current, nearby, message, now, notice, points, used.toSet())
                delay(1000)
            }
        } finally {
            tone?.stopTone()
            tone?.release()
            tracker.reset()
            late.reset()
            live.value =
                live.value.copy(current = null, notice = null, message = "Monitoreo suspendido")
        }
    }

    override fun onCleared() {
        db.close()
    }
}
