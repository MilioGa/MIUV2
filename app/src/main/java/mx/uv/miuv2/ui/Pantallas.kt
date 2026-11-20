package mx.uv.miuv2.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import mx.uv.miuv2.data.*
import java.io.File
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
private fun Encabezado(titulo: String, sub: String? = null, accion: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.headlineMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        accion?.invoke()
    }
}

// ------------------------------- HORARIO -------------------------------
@Composable
fun Horario(onMiuv: () -> Unit) {
    val horario by Repo.horario.collectAsState()
    val diasPager = androidx.compose.foundation.pager.rememberPagerState(initialPage = hoyIndice().coerceAtMost(5)) { DIAS.size }
    val scopeDias = rememberCoroutineScope()
    val dia = diasPager.currentPage
    val hoy = hoyIndice()
    val ahora = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    var editando by remember { mutableStateOf<Clase?>(null) }
    var nueva by remember { mutableStateOf(false) }
    editando?.let { c -> EditorClase(c, nueva, onCerrar = { editando = null }) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Encabezado("Horario", "Toca una clase para corregirla") {
            IconButton({ nueva = true; editando = Clase(dia, "07:00", "09:00", "", "") }) { Icon(Icons.Rounded.Add, "Agregar clase") }
            IconButton(onMiuv) { Icon(Icons.Rounded.Sync, "Actualizar desde MiUV") }
        }
        if (horario.isEmpty()) {
            Vacio(Icons.Rounded.Schedule, "Aún no tenemos tu horario", "Conéctate una vez a MiUV y lo guardamos en tu teléfono. También puedes agregar clases con el botón +.", "Conectar MiUV", onMiuv)
            return@Column
        }
        val chips = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(dia) { chips.animateScrollToItem((dia - 1).coerceAtLeast(0)) }
        LazyRow(state = chips, contentPadding = PaddingValues(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DIAS.indices.toList()) { i ->
                val n = horario.count { it.dia == i }
                FilterChip(dia == i, { scopeDias.launch { diasPager.animateScrollToPage(i) } }, label = { Text(DIAS[i].take(3) + if (i == hoy) " · hoy" else "") },
                    leadingIcon = if (n > 0) ({ Text("$n", fontWeight = FontWeight.Bold) }) else null, enabled = true)
            }
        }
        // Desliza a izquierda/derecha para cambiar de día; en lunes o sábado el gesto pasa a la sección vecina.
        androidx.compose.foundation.pager.HorizontalPager(diasPager, Modifier.weight(1f)) { d ->
            val clases = horario.filter { it.dia == d }.sortedBy { it.inicio }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(DIAS[d], style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp)) }
                if (clases.isEmpty()) item { Tarjeta { Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WbSunny, null, tint = MaterialTheme.colorScheme.tertiary); Spacer(Modifier.width(12.dp)); Text("Día libre") } } }
                items(clases) { c ->
                    val estado = if (d != hoy) 0 else when { ahora in minutos(c.inicio) until minutos(c.fin) -> 1; ahora < minutos(c.inicio) -> 0; else -> -1 }
                    Box(Modifier.clip(MaterialTheme.shapes.medium).clickable { nueva = false; editando = c }) { FilaClase(c, estado) }
                }
            }
        }
    }
}

@Composable
private fun EditorClase(original: Clase, nueva: Boolean, onCerrar: () -> Unit) {
    var dia by remember { mutableIntStateOf(original.dia) }
    var materia by remember { mutableStateOf(original.materia) }
    var inicio by remember { mutableStateOf(original.inicio) }
    var fin by remember { mutableStateOf(original.fin) }
    var salon by remember { mutableStateOf(original.salon) }
    var prof by remember { mutableStateOf(original.profesor) }
    var borrar by remember { mutableStateOf(false) }
    val horaOk = Regex("^\\d{1,2}:\\d{2}$")
    val valido = materia.isNotBlank() && horaOk.matches(inicio) && horaOk.matches(fin) && minutos(fin) > minutos(inicio)
    AlertDialog(onDismissRequest = onCerrar,
        title = { Text(if (nueva) "Nueva clase" else "Editar clase") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DIAS.indices.toList()) { i -> FilterChip(dia == i, { dia = i }, label = { Text(DIAS[i].take(3)) }) }
                }
                OutlinedTextField(materia, { materia = it }, label = { Text("Experiencia educativa") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(inicio, { inicio = it }, label = { Text("Inicio") }, placeholder = { Text("07:00") }, singleLine = true,
                        isError = !horaOk.matches(inicio), modifier = Modifier.weight(1f))
                    OutlinedTextField(fin, { fin = it }, label = { Text("Fin") }, placeholder = { Text("09:00") }, singleLine = true,
                        isError = !horaOk.matches(fin) || minutos(fin) <= minutos(inicio), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(salon, { salon = it }, label = { Text("Salón") }, singleLine = true)
                OutlinedTextField(prof, { prof = it }, label = { Text("Académico (opcional)") }, singleLine = true)
                if (!valido) Text("Escribe la materia y horas como 07:00; el fin debe ser después del inicio.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(enabled = valido, onClick = {
            val c = Clase(dia, inicio.padStart(5, '0'), fin.padStart(5, '0'), materia.trim(), salon.trim(), prof.trim())
            Repo.guardarHorario(Repo.horario.value.filterNot { !nueva && it == original } + c); onCerrar()
        }) { Text("Guardar") } },
        dismissButton = {
            Row {
                if (!nueva) TextButton({ borrar = true }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                TextButton(onCerrar) { Text("Cancelar") }
            }
        })
    if (borrar) AlertDialog(onDismissRequest = { borrar = false }, title = { Text("¿Eliminar esta clase?") },
        text = { Text("${original.materia} · ${DIAS[original.dia]} ${original.inicio}. Puedes recuperarla volviendo a conectar MiUV.") },
        confirmButton = { Button({ Repo.guardarHorario(Repo.horario.value - original); borrar = false; onCerrar() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Eliminar") } },
        dismissButton = { TextButton({ borrar = false }) { Text("Cancelar") } })
}

// ------------------------------- CALENDARIO -------------------------------
private val coloresTipo = mapOf("Clases" to AzulUV, "Exámenes" to Coral, "Vacaciones" to VerdeUV, "Sin clases" to Ambar,
    "Trámites" to Color(0xFF7C5CFF), "Intersemestral" to Color(0xFF00A3B4), "Evento" to Color(0xFF8A94A6))

@Composable
fun FilaEvento(e: Evento) {
    val hoy = fmt(System.currentTimeMillis(), "yyyy-MM-dd")
    val iso = java.text.SimpleDateFormat("yyyy-MM-dd", es)
    val ini = iso.parse(e.inicio)!!.time; val fin = iso.parse(e.fin)!!.time
    val color = coloresTipo[e.tipo] ?: AzulUV
    val enCurso = e.inicio <= hoy && e.fin >= hoy
    val dias = ((ini - iso.parse(hoy)!!.time) / 86_400_000L).toInt()
    Tarjeta(color = if (enCurso) color.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(54.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .14f)).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt(ini, "d"), style = MaterialTheme.typography.titleLarge, color = color)
                Text(fmt(ini, "MMM").uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.titulo, style = MaterialTheme.typography.titleMedium)
                Text(if (e.inicio == e.fin) fmt(ini, "EEEE d 'de' MMMM") else "${fmt(ini, "d MMM")} – ${fmt(fin, "d MMM yyyy")}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Etiqueta(e.tipo, color.copy(alpha = .14f), color)
                    when { enCurso -> Etiqueta("En curso", color, Color.White); dias in 1..30 -> Etiqueta(if (dias == 1) "Mañana" else "En $dias días",
                        MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun CalendarioPantalla() {
    val eventos by Repo.eventos.collectAsState()
    val sync by Repo.sync.collectAsState()
    var tipo by rememberSaveable { mutableStateOf<String?>(null) }
    var pasados by rememberSaveable { mutableStateOf(false) }
    val hoy = fmt(System.currentTimeMillis(), "yyyy-MM-dd")
    val iso = remember { java.text.SimpleDateFormat("yyyy-MM-dd", es) }
    val lista = eventos.filter { (tipo == null || it.tipo == tipo) && (pasados || it.fin >= hoy) }
        .let { if (pasados) it.sortedByDescending { e -> e.inicio } else it }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Encabezado("Calendario escolar", "Oficial UV · sistema escolarizado") {
            IconButton({ Repo.sincronizar() }, enabled = !sync.cargando) { Icon(Icons.Rounded.Sync, "Actualizar calendario") }
        }
        LazyRow(contentPadding = PaddingValues(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(tipo == null, { tipo = null }, label = { Text("Todo") }) }
            items(eventos.map { it.tipo }.distinct().sorted()) { t ->
                FilterChip(tipo == t, { tipo = if (tipo == t) null else t }, label = { Text(t) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(coloresTipo[t] ?: AzulUV)) })
            }
            item { FilterChip(pasados, { pasados = !pasados }, label = { Text("Ver pasados") },
                leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) }) }
        }
        if (eventos.isEmpty()) {
            Vacio(Icons.Rounded.CalendarMonth, if (sync.cargando) "Descargando calendario…" else "Calendario no descargado",
                "Se descarga de uv.mx y queda guardado para consultarlo sin internet.", if (sync.cargando) null else "Descargar ahora") { Repo.sincronizar() }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var mesActual = ""
            lista.forEach { e ->
                val mes = fmt(iso.parse(e.inicio)!!.time, "MMMM yyyy").replaceFirstChar { it.uppercase() }
                if (mes != mesActual) { mesActual = mes; item(key = "m$mes$pasados") { Text(mes, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp)) } }
                item(key = e.inicio + e.titulo + e.fin) { FilaEvento(e) }
            }
            item { Text("Fuente: uv.mx/escolar/calendarios. Consulta siempre los avisos oficiales de tu facultad.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

// ------------------------------- CREDENCIAL -------------------------------
private fun qr(s: String): Bitmap {
    val m = QRCodeWriter().encode(s, BarcodeFormat.QR_CODE, 360, 360)
    return Bitmap.createBitmap(360, 360, Bitmap.Config.RGB_565).apply {
        for (x in 0 until 360) for (y in 0 until 360) setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}

@Composable
fun CredencialPantalla(onMiuv: () -> Unit, visible: Boolean = true) {
    val c by Repo.credencial.collectAsState()
    val nombre by Repo.nombre.collectAsState()
    var original by remember { mutableStateOf(false) }
    val act = LocalContext.current as? Activity
    // Brillo al máximo mientras se muestra la credencial (más fácil de escanear).
    DisposableEffect(visible) {
        if (!visible) return@DisposableEffect onDispose { }
        val w = act?.window; val prev = w?.attributes?.screenBrightness
        w?.attributes = w?.attributes?.apply { screenBrightness = 1f }
        onDispose { w?.attributes = w?.attributes?.apply { screenBrightness = prev ?: -1f } }
    }
    val matricula = c.matricula.ifBlank { Repo.usuario() }
    val qrBmp = remember(matricula) { qr(matricula.uppercase().ifBlank { "UV" }).asImageBitmap() }
    val captura = remember(c.capturaPath) { c.capturaPath?.let { File(it).takeIf(File::exists) }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Encabezado("Credencial digital", if (captura != null) "Guardada desde MiUV · sin internet" else "Conecta MiUV para traer la oficial") {
            IconButton(onMiuv) { Icon(Icons.Rounded.Sync, "Actualizar desde MiUV") }
        }
        Box(Modifier.padding(16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large).background(GradienteHero).padding(22.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("UNIVERSIDAD VERACRUZANA", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Etiqueta("Estudiante", VerdeUV, Color.White)
                }
                Spacer(Modifier.height(18.dp))
                Text(c.nombre.ifBlank { nombre }.ifBlank { "Tu nombre" }, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(matricula, color = Color(0xFF8FF0B8), style = MaterialTheme.typography.titleLarge)
                listOf(c.programa, c.facultad, c.campus).filter { it.isNotBlank() }.forEach {
                    Text(it, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Image(qrBmp, "Código QR con tu matrícula", Modifier.size(170.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp))
                if (c.vigencia.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text("Vigencia: ${c.vigencia}", color = Color.White.copy(alpha = .85f)) }
            }
        }
        if (captura != null) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Seccion("Credencial oficial de MiUV")
                Tarjeta(onClick = { original = true }) {
                    Image(captura, "Captura de la credencial oficial", Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Fit)
                    Spacer(Modifier.height(8.dp))
                    Text("Toca para verla en pantalla completa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else Column(Modifier.padding(horizontal = 16.dp)) {
            Tarjeta { Vacio(Icons.Rounded.Badge, "Trae tu credencial oficial", "La copiamos de MiUV para que la muestres aunque no tengas datos.", "Conectar MiUV", onMiuv) }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (original && captura != null) Dialog({ original = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { original = false }, contentAlignment = Alignment.Center) {
            Image(captura, "Credencial oficial", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            IconButton({ original = false }, Modifier.align(Alignment.TopEnd).statusBarsPadding()) { Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White) }
        }
    }
}

// ------------------------------- AYUDA Y AJUSTES -------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ayuda(onCerrar: () -> Unit) {
    val preguntas = listOf(
        "¿Funciona sin internet?" to "Sí. Tareas, horario, calendario y credencial se guardan en tu teléfono. Lo que veas sin internet es la última versión que se descargó (la hora aparece arriba en Inicio).",
        "¿Cómo entrego una tarea?" to "Abre la tarea, elige tus archivos, revisa el resumen y confirma. Si no hay internet, la entrega queda \"en camino\" y se envía sola al reconectar; te llegará una notificación.",
        "¿Puedo cancelar una entrega?" to "Mientras espera internet o si falló, sí: botón Cancelar en \"Entregas en camino\". Una vez enviada a Eminus, cualquier cambio se hace en Eminus web.",
        "¿MIUV2 puede borrar mis entregas?" to "No. La app nunca borra ni reemplaza entregas. Si una tarea ya fue entregada, no te deja volver a enviarla.",
        "¿Por qué no puedo entregar una tarea por equipo?" to "Para no modificar la entrega de tus compañeros, las tareas por equipo se entregan desde Eminus web.",
        "Mi horario o credencial no aparece" to "Toca el botón de sincronizar en Horario o Credencial. Si MiUV cambió de diseño, navega dentro de la ventana hasta tu horario o credencial y pulsa \"Guardar esta página\".",
        "¿Dónde queda mi contraseña?" to "Cifrada con el almacén seguro de Android, solo en este teléfono. Cerrar sesión la borra junto con todos tus datos.",
        "Desliza para actualizar" to "En Inicio y Tareas, desliza hacia abajo para traer lo más nuevo de Eminus.",
    )
    ModalBottomSheet(onCerrar) {
        LazyColumn(contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 32.dp)) {
            item { Text("Ayuda", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)) }
            items(preguntas) { (p, r) ->
                var abierto by remember { mutableStateOf(false) }
                Tarjeta(onClick = { abierto = !abierto }, modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(if (abierto) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    if (abierto) { Spacer(Modifier.height(6.dp)); Text(r, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ajustes(onCerrar: () -> Unit, onSalir: () -> Unit, onAyuda: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }
    val nombre by Repo.nombre.collectAsState()
    ModalBottomSheet(onCerrar) {
        Column(Modifier.padding(20.dp, 0.dp, 20.dp, 32.dp)) {
            Text("Cuenta", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Tarjeta {
                Dato(Icons.Rounded.Person, "Nombre", nombre.ifBlank { "—" })
                Dato(Icons.Rounded.Badge, "Usuario", Repo.usuario())
            }
            Spacer(Modifier.height(12.dp))
            Text("Notificaciones", style = MaterialTheme.typography.titleMedium)
            var cierre by remember { mutableStateOf(Repo.avisarCierre) }
            var nuevas by remember { mutableStateOf(Repo.avisarNuevas) }
            ListItem(headlineContent = { Text("Recordatorio antes del cierre") },
                supportingContent = { Text("24 horas y 2 horas antes, si aún no la entregas") },
                leadingContent = { Icon(Icons.Rounded.Alarm, null) },
                trailingContent = { Switch(cierre, { cierre = it; Repo.avisarCierre = it }) })
            ListItem(headlineContent = { Text("Tareas nuevas") },
                supportingContent = { Text("Avisa cuando un profesor publica una tarea") },
                leadingContent = { Icon(Icons.Rounded.NotificationsActive, null) },
                trailingContent = { Switch(nuevas, { nuevas = it; Repo.avisarNuevas = it }) })
            ListItem(headlineContent = { Text("Ayuda y preguntas frecuentes") }, leadingContent = { Icon(Icons.Rounded.HelpOutline, null) },
                modifier = Modifier.clickable(onClick = onAyuda))
            ListItem(headlineContent = { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Borra tu cuenta y los datos guardados de este teléfono") },
                leadingContent = { Icon(Icons.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmar = true })
            Spacer(Modifier.height(8.dp))
            Text("MIUV2 2.1 · App independiente, no oficial de la Universidad Veracruzana.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmar) {
        val pendientes = Repo.cola.value.size
        AlertDialog(onDismissRequest = { confirmar = false }, icon = { Icon(Icons.Rounded.Logout, null) },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Se borrarán tu horario, credencial y tareas guardadas en este teléfono." +
                if (pendientes > 0) "\n\nTienes $pendientes entrega(s) sin enviar que también se cancelarán." else "") },
            confirmButton = { Button(onSalir, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Cerrar sesión") } },
            dismissButton = { TextButton({ confirmar = false }) { Text("Cancelar") } })
    }
}
