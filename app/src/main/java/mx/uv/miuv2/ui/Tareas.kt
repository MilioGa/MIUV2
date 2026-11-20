package mx.uv.miuv2.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.uv.miuv2.data.*

enum class Filtro(val etiqueta: String) { PENDIENTES("Pendientes"), ENTREGADAS("Entregadas"), CERRADAS("Cerradas"), TODAS("Todas") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tareas(abrir: (Tarea) -> Unit) {
    val tareas by Repo.tareas.collectAsState()
    val sync by Repo.sync.collectAsState()
    var filtro by rememberSaveable { mutableStateOf(Filtro.PENDIENTES) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    val lista = tareas.filter {
        when (filtro) {
            Filtro.PENDIENTES -> it.estado == EstadoTarea.PENDIENTE || it.estado == EstadoTarea.POR_INICIAR
            Filtro.ENTREGADAS -> it.estado == EstadoTarea.ENTREGADA
            Filtro.CERRADAS -> it.estado == EstadoTarea.VENCIDA
            Filtro.TODAS -> true
        } && (busqueda.isBlank() || it.titulo.contains(busqueda, true) || it.curso.contains(busqueda, true))
    }.let { l -> if (filtro == Filtro.PENDIENTES) l.sortedBy { it.fin ?: Long.MAX_VALUE } else l.sortedByDescending { it.fin ?: 0 } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text("Tareas", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp)); EstadoSync()
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(busqueda, { busqueda = it }, placeholder = { Text("Buscar por tarea o curso") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (busqueda.isNotEmpty()) IconButton({ busqueda = "" }) { Icon(Icons.Rounded.Close, "Borrar búsqueda") } },
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Filtro.entries) { f ->
                val n = tareas.count { t -> when (f) { Filtro.PENDIENTES -> t.estado == EstadoTarea.PENDIENTE || t.estado == EstadoTarea.POR_INICIAR
                    Filtro.ENTREGADAS -> t.estado == EstadoTarea.ENTREGADA; Filtro.CERRADAS -> t.estado == EstadoTarea.VENCIDA; Filtro.TODAS -> true } }
                FilterChip(filtro == f, { filtro = f }, label = { Text("${f.etiqueta} · $n") },
                    leadingIcon = if (filtro == f) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null)
            }
        }
        PullToRefreshBox(isRefreshing = sync.cargando, onRefresh = { Repo.sincronizar() }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AvisoError() }
                if (lista.isEmpty()) item {
                    Vacio(Icons.Rounded.TaskAlt, when { busqueda.isNotBlank() -> "Sin resultados"; filtro == Filtro.PENDIENTES -> "Nada pendiente"; else -> "No hay tareas aquí" },
                        if (busqueda.isNotBlank()) "Prueba con otra palabra." else "Desliza hacia abajo para actualizar desde Eminus.")
                }
                items(lista, key = { it.id }) { t -> FilaTarea(t) { abrir(t) } }
            }
        }
    }
}

private fun nombreArchivo(ctx: android.content.Context, u: Uri): Pair<String, Long> {
    var n = u.lastPathSegment ?: "archivo"; var s = 0L
    ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst()) { n = it.getString(0) ?: n; s = it.getLong(1) } }
    return n to s
}
private fun peso(b: Long) = when { b <= 0 -> ""; b < 1024 * 1024 -> "${b / 1024} KB"; else -> String.format(es, "%.1f MB", b / 1048576.0) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleTarea(t0: Tarea, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val tareas by Repo.tareas.collectAsState()
    val cola by Repo.cola.collectAsState()
    val t = tareas.firstOrNull { it.id == t0.id } ?: t0
    val enCola = cola.firstOrNull { it.tareaId == t.id }
    var archivos by remember { mutableStateOf(listOf<Uri>()) }
    var comentario by rememberSaveable { mutableStateOf("") }
    var confirmar by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { l -> archivos = (archivos + l).distinct() }
    val puedeEntregar = t.estado == EstadoTarea.PENDIENTE && !t.porEquipos && enCola == null
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(t.curso, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(t.titulo, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            EtiquetaEstado(t.estado, t.fin)
            Spacer(Modifier.height(14.dp))
            Tarjeta {
                Dato(Icons.Rounded.PlayCircleOutline, "Abre", t.inicio?.let { fmt(it, "EEEE d MMM, HH:mm") } ?: "—")
                Dato(Icons.Rounded.Flag, "Cierra", t.fin?.let { fmt(it, "EEEE d MMM, HH:mm") } ?: "Sin fecha límite")
                t.fechaEntrega?.let { Dato(Icons.Rounded.CheckCircle, "Entregaste", fmt(it, "EEEE d MMM, HH:mm")) }
                if (t.porEquipos) Dato(Icons.Rounded.Groups, "Modalidad", "Por equipos")
            }
            if (t.descripcion.isNotBlank()) {
                Seccion("Instrucciones")
                Text(t.descripcion, style = MaterialTheme.typography.bodyLarge)
            }

            enCola?.let { Seccion("Tu entrega"); FilaCola(it) }

            if (puedeEntregar) {
                Seccion("Entregar")
                archivos.forEach { u ->
                    val (n, s) = remember(u) { nombreArchivo(ctx, u) }
                    Tarjeta(modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(n, maxLines = 1, overflow = TextOverflow.Ellipsis); if (s > 0) Text(peso(s), style = MaterialTheme.typography.bodySmall) }
                            IconButton({ archivos = archivos - u }) { Icon(Icons.Rounded.Close, "Quitar archivo") }
                        }
                    }
                }
                OutlinedButton({ picker.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AttachFile, null); Spacer(Modifier.width(6.dp)); Text(if (archivos.isEmpty()) "Elegir archivos" else "Agregar otro archivo")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(comentario, { comentario = it }, label = { Text("Comentario para tu profesor (opcional)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(14.dp))
                Button({ confirmar = true }, Modifier.fillMaxWidth().height(52.dp), enabled = archivos.isNotEmpty()) {
                    Icon(Icons.Rounded.Send, null); Spacer(Modifier.width(8.dp)); Text("Revisar y entregar")
                }
                if (!Repo.hayInternet()) Text("Sin internet: tu entrega se guardará y se enviará sola cuando vuelva la conexión.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else if (t.estado == EstadoTarea.PENDIENTE && t.porEquipos) {
                Spacer(Modifier.height(16.dp))
                Text("Las tareas por equipo se entregan desde Eminus web para no afectar la entrega de tus compañeros.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            TextButton({ uri.openUri("https://eminus.uv.mx/eminus4/") }) { Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text("Abrir Eminus web") }
        }
    }

    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        icon = { Icon(Icons.Rounded.Send, null) },
        title = { Text("¿Entregar esta tarea?") },
        text = {
            Column {
                Text("\"${t.titulo}\"", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("Se enviarán ${archivos.size} archivo(s):")
                archivos.forEach { Text("• " + nombreArchivo(ctx, it).first, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                Text("Una vez enviada, para cambiarla tendrás que usar Eminus web.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button({
            confirmar = false
            runCatching { Repo.encolarEntrega(t, comentario, archivos) }
                .onSuccess { archivos = emptyList(); aviso = if (Repo.hayInternet()) "Enviando tu entrega… te avisaremos al terminar." else "Guardada. Se enviará al reconectar." }
                .onFailure { aviso = "No se pudieron leer los archivos: ${it.message}" }
        }) { Text("Sí, entregar") } },
        dismissButton = { TextButton({ confirmar = false }) { Text("Revisar de nuevo") } },
    )
    aviso?.let { AlertDialog(onDismissRequest = { aviso = null }, confirmButton = { TextButton({ aviso = null }) { Text("Entendido") } }, text = { Text(it) }) }
}

@Composable
fun Dato(icono: androidx.compose.ui.graphics.vector.ImageVector, etiqueta: String, valor: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(86.dp))
        Text(valor.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
    }
}
