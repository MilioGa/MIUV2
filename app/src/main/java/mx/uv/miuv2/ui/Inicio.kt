package mx.uv.miuv2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.uv.miuv2.data.*
import java.util.Calendar

/** Barra de estado de sincronización (visibilidad del estado del sistema). */
@Composable
fun EstadoSync(oscuro: Boolean = false) {
    val s by Repo.sync.collectAsState()
    val online by Repo.online.collectAsState()
    val cola by Repo.cola.collectAsState()
    val fg = if (oscuro) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val fondo = if (oscuro) Color.White.copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceContainerHigh
    Row(Modifier.clip(CircleShape).background(fondo).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            s.cargando -> { CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = fg); Spacer(Modifier.width(8.dp))
                Text(s.mensaje ?: "Actualizando…", color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            !online -> { Icon(Icons.Rounded.CloudOff, null, tint = fg, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                Text("Sin internet · datos de ${haceCuanto(s.ultima)}" + if (cola.isNotEmpty()) " · ${cola.size} por enviar" else "",
                    color = fg, style = MaterialTheme.typography.labelMedium) }
            else -> { Icon(Icons.Rounded.CloudDone, null, tint = fg, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                Text("Actualizado ${haceCuanto(s.ultima)}", color = fg, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
fun AvisoError() {
    val s by Repo.sync.collectAsState()
    val e = s.error ?: return
    Tarjeta(color = MaterialTheme.colorScheme.error.copy(alpha = .10f), modifier = Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("No se pudo actualizar todo", style = MaterialTheme.typography.titleSmall)
                Text(e, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { Repo.sincronizar() }) { Text("Reintentar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Inicio(abrir: (Tarea) -> Unit, irA: (Tab) -> Unit, onMiuv: () -> Unit, onAyuda: () -> Unit, onAjustes: () -> Unit) {
    val nombre by Repo.nombre.collectAsState()
    val tareas by Repo.tareas.collectAsState()
    val horario by Repo.horario.collectAsState()
    val eventos by Repo.eventos.collectAsState()
    val cola by Repo.cola.collectAsState()
    val sync by Repo.sync.collectAsState()
    val pendientes = tareas.filter { it.estado == EstadoTarea.PENDIENTE }
    val hoy = hoyIndice()
    val clasesHoy = horario.filter { it.dia == hoy }.sortedBy { it.inicio }
    val ahoraMin = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val saludo = when (hora) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
    val hoyIso = fmt(System.currentTimeMillis(), "yyyy-MM-dd")
    val proximo = eventos.firstOrNull { it.fin >= hoyIso }

    PullToRefreshBox(isRefreshing = sync.cargando, onRefresh = { Repo.sincronizar() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(GradienteHero).statusBarsPadding().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("MIUV2", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onAyuda) { Icon(Icons.Rounded.HelpOutline, "Ayuda", tint = Color.White) }
                        IconButton(onAjustes) { Icon(Icons.Rounded.Settings, "Ajustes", tint = Color.White) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("$saludo${nombre.split(" ").firstOrNull()?.let { if (it.isNotBlank()) ", $it" else "" } ?: ""}",
                        style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(fmt(System.currentTimeMillis(), "EEEE d 'de' MMMM").replaceFirstChar { it.uppercase() },
                        color = Color.White.copy(alpha = .8f))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Resumen("${pendientes.size}", if (pendientes.size == 1) "tarea pendiente" else "tareas pendientes", Modifier.weight(1f)) { irA(Tab.TAREAS) }
                        Resumen("${clasesHoy.size}", if (clasesHoy.size == 1) "clase hoy" else "clases hoy", Modifier.weight(1f)) { irA(Tab.HORARIO) }
                    }
                    Spacer(Modifier.height(14.dp))
                    EstadoSync(oscuro = true)
                }
            }
            item { Column(Modifier.padding(horizontal = 16.dp)) { AvisoError() } }

            if (cola.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Seccion("Entregas en camino")
                    cola.forEach { FilaCola(it); Spacer(Modifier.height(8.dp)) }
                }
            }

            item { Column(Modifier.padding(horizontal = 16.dp)) { Seccion("Clases de hoy", "Ver horario") { irA(Tab.HORARIO) } } }
            when {
                horario.isEmpty() -> item { Column(Modifier.padding(horizontal = 16.dp)) {
                    Tarjeta { Vacio(Icons.Rounded.Schedule, "Trae tu horario de MiUV", "Lo descargamos una vez y queda guardado para verlo sin internet.", "Conectar MiUV", onMiuv) } } }
                clasesHoy.isEmpty() -> item { Column(Modifier.padding(horizontal = 16.dp)) {
                    Tarjeta { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.WbSunny, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp)); Text("Hoy no tienes clases. ¡Aprovecha para adelantar tareas!") } } } }
                else -> items(clasesHoy) { c ->
                    val estado = when { ahoraMin in minutos(c.inicio) until minutos(c.fin) -> 1; ahoraMin < minutos(c.inicio) -> 0; else -> -1 }
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { FilaClase(c, estado) }
                }
            }

            item { Column(Modifier.padding(horizontal = 16.dp)) { Seccion("Tareas pendientes", if (pendientes.isNotEmpty()) "Ver todas" else null) { irA(Tab.TAREAS) } } }
            if (pendientes.isEmpty()) item { Column(Modifier.padding(horizontal = 16.dp)) {
                Tarjeta { Vacio(Icons.Rounded.TaskAlt, if (tareas.isEmpty() && sync.ultima == 0L) "Cargando tus tareas…" else "¡Estás al día!",
                    if (tareas.isEmpty() && sync.ultima == 0L) "Estamos leyendo tus cursos de Eminus." else "No tienes tareas pendientes en Eminus.") } } }
            items(pendientes.take(5)) { t -> Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { FilaTarea(t) { abrir(t) } } }

            proximo?.let { e -> item { Column(Modifier.padding(horizontal = 16.dp)) {
                Seccion("Próximo en el calendario", "Ver calendario") { irA(Tab.CALENDARIO) }
                FilaEvento(e)
            } } }
        }
    }
}

@Composable
private fun Resumen(n: String, texto: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .14f))) {
        Column(Modifier.padding(14.dp)) {
            Text(n, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(texto, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** estado: 1 = en curso, 0 = más tarde, -1 = ya pasó */
@Composable
fun FilaClase(c: Clase, estado: Int = 0) {
    val cs = MaterialTheme.colorScheme
    val color = when (estado) { 1 -> cs.secondary; 0 -> cs.primary; else -> cs.outlineVariant }
    Tarjeta(color = if (estado == 1) cs.secondaryContainer else cs.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp)) {
                Text(c.inicio, style = MaterialTheme.typography.titleMedium, color = if (estado < 0) cs.onSurfaceVariant else cs.onSurface)
                Text(c.fin, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Box(Modifier.width(4.dp).height(44.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.materia, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (estado < 0) cs.onSurfaceVariant else cs.onSurface)
                val extra = listOf(c.salon.takeIf { it.isNotBlank() }?.let { "Salón $it" }, c.profesor.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
                if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (estado == 1) Etiqueta("Ahora", cs.secondary, Color.White)
        }
    }
}

@Composable
fun FilaTarea(t: Tarea, onClick: () -> Unit) {
    Tarjeta(onClick = onClick) {
        Text(t.curso, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(t.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtiquetaEstado(t.estado, t.fin)
            Spacer(Modifier.weight(1f))
            t.fin?.let { Text(fmt(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun FilaCola(e: EntregaEnCola) {
    val cs = MaterialTheme.colorScheme
    Tarjeta(color = if (e.error != null) cs.error.copy(alpha = .08f) else cs.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (e.error == null && e.estado != "En espera de internet") CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(if (e.error != null) Icons.Rounded.ErrorOutline else Icons.Rounded.CloudUpload, null, tint = if (e.error != null) cs.error else cs.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.error ?: e.estado, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (e.error != null) TextButton({ Repo.reintentar(e.id) }) { Text("Reintentar") }
            if (e.error != null || e.estado == "En espera de internet") TextButton({ Repo.cancelarEntrega(e.id) }) { Text("Cancelar") }
        }
    }
}
