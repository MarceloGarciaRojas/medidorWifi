package cl.medidorwifi

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as SystemSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model by viewModels<Monitor>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { model.monitor() } }
        setContent { Analyzer(model) }
    }
}

data class AntennaSelection(val bssid: String, val ssid: String)

val Aqua = Color(0xFF00A99D)

fun date(value: Long) = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(value))

@Composable
fun Analyzer(vm: Monitor) {
    val settings by vm.settings.collectAsState()
    val live by vm.live.collectAsState()
    val antennas by vm.antennas.collectAsState()
    val readings by vm.readings.collectAsState()
    val events by vm.events.collectAsState()
    val dark =
        settings.theme == "Oscuro" || (settings.theme == "Automático" && isSystemInDarkTheme())
    val scheme =
        if (dark) darkColorScheme(primary = Aqua)
        else lightColorScheme(primary = Aqua, background = Color(0xFFF5F8F8), surface = Color.White)
    val context = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri
            ->
            if (uri != null)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(vm.csv())
                }
        }
    var selected by remember { mutableStateOf<AntennaSelection?>(null) }
    var save by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "Medidor"
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            bottomBar = {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    tonalElevation = 3.dp,
                    color = scheme.surface.copy(alpha = .95f),
                    modifier = Modifier.padding(12.dp).navigationBarsPadding(),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Medidor", "Antena fijada", "Historial", "Ajustes").forEach { title
                            ->
                            TextButton(
                                onClick = {
                                    nav.navigate(title) {
                                        launchSingleTop = true
                                        popUpTo("Medidor") { saveState = true }
                                        restoreState = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        when (title) {
                                            "Medidor" -> Icons.Default.Home
                                            "Antena fijada" -> Icons.Default.Star
                                            "Historial" -> Icons.Default.List
                                            else -> Icons.Default.Settings
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        title,
                                        fontSize = 11.sp,
                                        color =
                                            if (route == title) Aqua else scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Text(
                    "Wi-Fi Signal Analyzer",
                    modifier = Modifier.padding(start = 24.dp, top = 14.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Aqua,
                )
                Text(
                    route,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                NavHost(nav, startDestination = "Medidor", modifier = Modifier.weight(1f)) {
                    composable("Medidor") {
                        var search by remember { mutableStateOf("") }
                        var strongest by remember { mutableStateOf(true) }
                        LazyColumn(
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Gauge(live.current?.rssi, settings)
                                Text(
                                    live.current?.let { vm.name(it.bssid) }
                                        ?: "Sin conexión identificable",
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(live.message, style = MaterialTheme.typography.bodySmall)
                            }
                            live.current?.let { r ->
                                item {
                                    Panel {
                                        Text(r.ssid)
                                        Text(
                                            "${r.band} · Canal ${r.channel ?: "—"} · ${r.frequency} MHz"
                                        )
                                        Text(
                                            "Enlace: ${r.speed?.let { "$it Mbps" } ?: "No disponible"} · IP: ${r.ip ?: "No disponible"}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(r.bssid, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            live.notice?.let { item { Panel { Text(it, color = Aqua) } } }
                            item {
                                Button(
                                    onClick = { save = true },
                                    enabled = live.current != null,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                ) {
                                    Text("Guardar lectura")
                                }
                            }
                            item {
                                Row {
                                    TextButton(
                                        onClick = {
                                            permission.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                )
                                            )
                                        }
                                    ) {
                                        Text("Permisos")
                                    }
                                    TextButton(
                                        onClick = {
                                            context.startActivity(
                                                Intent(SystemSettings.ACTION_WIFI_SETTINGS)
                                            )
                                        }
                                    ) {
                                        Text("Wi-Fi")
                                    }
                                    TextButton(
                                        onClick = {
                                            context.startActivity(
                                                Intent(
                                                    SystemSettings.ACTION_LOCATION_SOURCE_SETTINGS
                                                )
                                            )
                                        }
                                    ) {
                                        Text("Ubicación")
                                    }
                                }
                            }
                            item {
                                Text(
                                    "Android requiere ubicación precisa para mostrar BSSID y escaneos. Los datos permanecen en este dispositivo.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            item {
                                OutlinedTextField(
                                    search,
                                    { search = it },
                                    label = { Text("Buscar nombre o SSID") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(onClick = { strongest = !strongest }) {
                                    Text(
                                        if (strongest) "Orden: mayor señal"
                                        else "Orden: menor señal"
                                    )
                                }
                            }
                            val radios =
                                live.nearby
                                    .filter {
                                        vm.name(it.bssid).contains(search, true) ||
                                            it.ssid.contains(search, true)
                                    }
                                    .let {
                                        if (strongest) it.sortedByDescending { r -> r.rssi }
                                        else it.sortedBy { r -> r.rssi }
                                    }
                            if (radios.isEmpty())
                                item { Text("Sin resultados de escaneo disponibles") }
                            items(radios, key = { it.bssid }) { r ->
                                Card(
                                    onClick = { selected = AntennaSelection(r.bssid, r.ssid) },
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(vm.name(r.bssid), fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${r.ssid.ifBlank { "SSID oculto" }} · ${r.rssi} dBm · ${quality(r.rssi,settings.weak,settings.good)}"
                                        )
                                        Text(
                                            "${r.band} · Canal ${r.channel ?: "—"} · ${r.bssid}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            "Actualizado hace ${((live.now-r.observed)/1000).coerceAtLeast(0)} segundos",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (r.bssid == live.current?.bssid)
                                            Text("CONECTADO", color = Aqua)
                                        if (r.bssid == live.nearby.maxByOrNull { it.rssi }?.bssid)
                                            Text("MEJOR SEÑAL", color = Aqua)
                                    }
                                }
                            }
                        }
                    }
                    composable("Antena fijada") {
                        val radio =
                            live.current?.takeIf { it.bssid == settings.pinned }
                                ?: live.nearby.firstOrNull { it.bssid == settings.pinned }
                                ?: live.points.lastOrNull { it.bssid == settings.pinned }
                        val fresh = radio?.takeIf { live.now - it.observed in 0..15000 }
                        LazyColumn(
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item {
                                Gauge(fresh?.rssi, settings)
                                Text(
                                    if (settings.pinned.isBlank())
                                        "Selecciona una antena en Medidor"
                                    else vm.name(settings.pinned)
                                )
                                Text(settings.pinned)
                                if (fresh == null && radio != null)
                                    Text(
                                        "Último valor conocido: ${radio.rssi} dBm · hace ${((live.now-radio.observed)/1000).coerceAtLeast(0)} s"
                                    )
                            }
                            item {
                                Text(
                                    if (fresh == null)
                                        "Sin lectura reciente. La ausencia en un escaneo no demuestra una falla."
                                    else
                                        "Última lectura hace ${(live.now-fresh.observed)/1000} segundos"
                                )
                                Text(
                                    if (settings.pinned == live.current?.bssid)
                                        "Conectado a esta antena"
                                    else "El seguimiento no cambia la conexión del teléfono"
                                )
                            }
                            item {
                                Trend(
                                    live.points
                                        .filter { it.bssid == settings.pinned }
                                        .map { it.rssi }
                                )
                                Text(
                                    "Últimas lecturas disponibles de esta sesión",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            item {
                                Button(
                                    onClick = { vm.setting("pinned", "") },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Liberar antena")
                                }
                                TextButton(onClick = { nav.navigate("Medidor") }) {
                                    Text("Cambiar antena")
                                }
                            }
                        }
                    }
                    composable("Historial") {
                        var tab by remember { mutableStateOf(false) }
                        var filter by remember { mutableStateOf("") }
                        LazyColumn(
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Row {
                                    FilterChip(!tab, { tab = false }, label = { Text("Lecturas") })
                                    Spacer(Modifier.width(12.dp))
                                    FilterChip(tab, { tab = true }, label = { Text("Roaming") })
                                }
                            }
                            if (tab) {
                                item {
                                    Text(
                                        "${events.count { it.source.isNotBlank() && it.kind!="Posible roaming tardío" }} cambios · ${events.count { it.kind=="Posible interrupción" }} posibles interrupciones · ${events.count { it.kind=="Posible roaming tardío" }} alertas"
                                    )
                                    Text(
                                        "Antenas usadas durante esta sesión: ${live.used.size}. Los intervalos observados no son la duración exacta del roaming.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                items(events, key = { it.id }) { e ->
                                    Panel {
                                        Text(date(e.time), color = Aqua)
                                        Text(e.kind, fontWeight = FontWeight.Bold)
                                        Text("${e.sourceName} → ${e.destinationName}")
                                        Text("${e.fromRssi ?: "—"} → ${e.toRssi ?: "—"} dBm")
                                        e.dwell?.let { Text("Permanencia observada: ${it/1000} s") }
                                        e.interval?.let { Text("Entre observaciones: $it ms") }
                                        e.gap?.let { Text("Ausencia observada: $it ms") }
                                    }
                                }
                            } else {
                                item {
                                    OutlinedTextField(
                                        filter,
                                        { filter = it },
                                        label = { Text("Filtrar zona o antena") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Trend(
                                        readings
                                            .filter {
                                                it.zone.contains(filter, true) ||
                                                    it.name.contains(filter, true)
                                            }
                                            .take(90)
                                            .reversed()
                                            .map { it.rssi }
                                    )
                                }
                                items(
                                    readings.filter {
                                        it.zone.contains(filter, true) ||
                                            it.name.contains(filter, true)
                                    },
                                    key = { it.id },
                                ) { r ->
                                    Panel {
                                        Text(
                                            "${r.zone} · ${r.rssi} dBm",
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text("${r.name} · ${r.ssid}")
                                        Text(
                                            "${date(r.time)} · ${r.bssid}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                            if ((tab && events.isEmpty()) || (!tab && readings.isEmpty()))
                                item { Text("Todavía no hay registros") }
                        }
                    }
                    composable("Ajustes") {
                        LazyColumn(
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item {
                                Text("Apariencia", style = MaterialTheme.typography.titleLarge)
                                Row {
                                    listOf("Automático", "Claro", "Oscuro").forEach { t ->
                                        TextButton(onClick = { vm.setting("theme", t) }) {
                                            Text((if (settings.theme == t) "✓ " else "") + t)
                                        }
                                    }
                                }
                            }
                            item {
                                Toggle("Beep", settings.beep) { vm.setting("beep", it) }
                                Toggle("Parpadeo", settings.blink) { vm.setting("blink", it) }
                                Toggle("Vibración de roaming", settings.vibration) {
                                    vm.setting("vibration", it)
                                }
                            }
                            item {
                                Text("Umbral débil: ${settings.weak} dBm")
                                Slider(
                                    settings.weak.toFloat(),
                                    { vm.setting("weak", it.toInt()) },
                                    valueRange = -90f..(settings.good - 1).toFloat(),
                                )
                                Text("Umbral bueno: ${settings.good} dBm")
                                Slider(
                                    settings.good.toFloat(),
                                    { vm.setting("good", it.toInt()) },
                                    valueRange = (settings.weak + 1).toFloat()..-40f,
                                )
                            }
                            item {
                                Text("Diferencia para alerta: ${settings.delta} dB")
                                Slider(
                                    settings.delta.toFloat(),
                                    { vm.setting("delta", it.toInt()) },
                                    valueRange = 8f..30f,
                                )
                                Text("Persistencia: ${settings.persistence} s")
                                Slider(
                                    settings.persistence.toFloat(),
                                    { vm.setting("persistence", it.toInt()) },
                                    valueRange = 3f..15f,
                                )
                            }
                            item {
                                Text(
                                    "Antenas registradas",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "Reutiliza el mismo nombre para agrupar varios BSSID de una antena física. La IP de administración es manual.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            items(antennas, key = { it.bssid }) { a ->
                                Panel {
                                    Text("${a.name} · ${a.zone}")
                                    Text(a.bssid)
                                    Row {
                                        TextButton(
                                            onClick = { selected = AntennaSelection(a.bssid, "") }
                                        ) {
                                            Text("Editar")
                                        }
                                        TextButton(onClick = { vm.remove(a) }) {
                                            Text("Eliminar registro")
                                        }
                                    }
                                }
                            }
                            item {
                                Button(
                                    onClick = { export.launch("mediciones-wifi.csv") },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Exportar lecturas CSV")
                                }
                                TextButton(onClick = { vm.prune() }) {
                                    Text("Conservar solo últimos 30 días")
                                }
                                TextButton(onClick = { confirmClear = true }) {
                                    Text("Borrar historial")
                                }
                            }
                            item {
                                Text("Wi-Fi Signal Analyzer · 0.1.0")
                                Text(
                                    "Datos locales, sin cuenta ni publicidad. Solo se mide mientras esta pantalla está activa. El estado de conexión Wi-Fi no demuestra conectividad con un servidor. No se realizan pruebas externas.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        selected?.let { radio ->
            Registration(
                radio,
                antennas.firstOrNull { it.bssid == radio.bssid },
                antennas,
                onClose = { selected = null },
                onSave = {
                    vm.register(it)
                    selected = null
                },
                onPin = {
                    vm.setting("pinned", radio.bssid)
                    selected = null
                    nav.navigate("Antena fijada")
                },
            )
        }
        if (save)
            SaveSheet(readings.map { it.zone }.distinct().take(3), { save = false }) {
                vm.save(it)
                save = false
            }
        if (confirmClear)
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("¿Borrar todo el historial?") },
                text = {
                    Text("Se eliminarán lecturas y eventos. Los nombres de antenas se conservan.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.clear()
                            confirmClear = false
                        }
                    ) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Cancelar") }
                },
            )
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(value, change)
    }
}

@Composable
fun Gauge(rssi: Int?, s: Settings) {
    val progress by
        animateFloatAsState(((rssi ?: -100) + 100).coerceIn(0, 70) / 70f, label = "signal")
    val color =
        when {
            rssi == null -> Color.Gray
            rssi < s.weak -> Color(0xFFEB625C)
            rssi < s.good -> Color(0xFFE9BA4F)
            else -> Aqua
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(155.dp)) {
            val width = min(size.width - 36, size.height * 2 - 30)
            val radius = width / 2
            val left = (size.width - width) / 2
            val top = 12f
            for (i in 0..69) drawArc(
                androidx.compose.ui.graphics.lerp(
                    if (i < 35) Color(0xFFEB625C) else Color(0xFFE9BA4F),
                    if (i < 35) Color(0xFFE9BA4F) else Aqua,
                    if (i < 35) i / 35f else (i - 35) / 35f,
                ),
                180f + i * 180f / 70f,
                2.7f,
                false,
                Offset(left, top),
                Size(width, width),
                style = Stroke(12.dp.toPx(), cap = StrokeCap.Round),
            )
            val angle = Math.PI * (1 + progress)
            drawCircle(
                color,
                10.dp.toPx(),
                Offset(
                    left + radius + cos(angle).toFloat() * radius,
                    top + radius + sin(angle).toFloat() * radius,
                ),
            )
        }
        Text(
            rssi?.toString() ?: "—",
            fontSize = 58.sp,
            fontWeight = FontWeight.Light,
            color = color,
        )
        Text("dBm · ${quality(rssi,s.weak,s.good)}")
        if (s.blink && rssi != null)
            Text("●", color = color.copy(alpha = if (rssi % 2 == 0) 1f else .35f))
    }
}

@Composable
fun Trend(values: List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        if (values.size > 1)
            values.zipWithNext().forEachIndexed { i, (a, b) ->
                drawLine(
                    Aqua,
                    Offset(
                        size.width * i / (values.size - 1),
                        size.height * (1 - (a + 100).coerceIn(0, 70) / 70f),
                    ),
                    Offset(
                        size.width * (i + 1) / (values.size - 1),
                        size.height * (1 - (b + 100).coerceIn(0, 70) / 70f),
                    ),
                    3.dp.toPx(),
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Registration(
    r: AntennaSelection,
    a: Antenna?,
    groups: List<Antenna>,
    onClose: () -> Unit,
    onSave: (Antenna) -> Unit,
    onPin: () -> Unit,
) {
    var name by remember { mutableStateOf(a?.name ?: "") }
    var zone by remember { mutableStateOf(a?.zone ?: "") }
    var ip by remember { mutableStateOf(a?.managementIp ?: "") }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.padding(24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Registrar antena", style = MaterialTheme.typography.titleLarge)
            Text("${r.ssid} · ${r.bssid}")
            Text(
                "Confirma físicamente la antena: una señal mayor no identifica por sí sola un equipo Cisco.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(name, { name = it }, label = { Text("Nombre / grupo físico") })
            if (a == null) {
                Text("Añadir a antena existente:", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.lazy.LazyRow {
                    items(groups.distinctBy { it.name }) { g ->
                        TextButton(
                            onClick = {
                                name = g.name
                                zone = g.zone
                                ip = g.managementIp
                            }
                        ) {
                            Text(g.name)
                        }
                    }
                }
            } else
                Text(
                    "Editar el nombre o ubicación actualiza todos los BSSID de este grupo.",
                    style = MaterialTheme.typography.bodySmall,
                )
            OutlinedTextField(zone, { zone = it }, label = { Text("Ubicación") })
            OutlinedTextField(ip, { ip = it }, label = { Text("IP de administración (opcional)") })
            Button(
                onClick = { onSave(Antenna(r.bssid, name.trim(), zone.trim(), ip.trim())) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Confirmar y guardar")
            }
            TextButton(onClick = onPin) { Text("Fijar este BSSID") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(zones: List<String>, close: () -> Unit, save: (String) -> Unit) {
    var zone by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = close) {
        Column(
            Modifier.padding(24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Guardar lectura", style = MaterialTheme.typography.titleLarge)
            zones.forEach { z -> TextButton(onClick = { save(z) }) { Text(z) } }
            OutlinedTextField(zone, { zone = it }, label = { Text("Seleccionar o crear zona") })
            Button(
                onClick = { save(zone) },
                enabled = zone.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
