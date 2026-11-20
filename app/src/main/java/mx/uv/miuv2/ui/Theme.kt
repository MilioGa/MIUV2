package mx.uv.miuv2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.uv.miuv2.data.EstadoTarea
import java.text.SimpleDateFormat
import java.util.*

// Personalidad MIUV2: azul profundo de la UV, verde vivo para lo logrado, ámbar para lo urgente.
val AzulNoche = Color(0xFF0B2E63)
val AzulUV = Color(0xFF1B4DA0)
val AzulCielo = Color(0xFF4F86E8)
val VerdeUV = Color(0xFF1FA463)
val Ambar = Color(0xFFFF8A3D)
val Coral = Color(0xFFE5484D)

private val Claro = lightColorScheme(
    primary = AzulUV, onPrimary = Color.White, primaryContainer = Color(0xFFDDE7FA), onPrimaryContainer = AzulNoche,
    secondary = VerdeUV, onSecondary = Color.White, secondaryContainer = Color(0xFFD5F3E3), onSecondaryContainer = Color(0xFF0B4A2C),
    tertiary = Ambar, tertiaryContainer = Color(0xFFFFE6D5), onTertiaryContainer = Color(0xFF6B2D00),
    error = Coral, background = Color(0xFFF4F6FB), surface = Color(0xFFF4F6FB), surfaceContainer = Color.White,
    surfaceContainerLow = Color.White, surfaceContainerHigh = Color(0xFFEDF1F8), onSurfaceVariant = Color(0xFF5B6475),
    outlineVariant = Color(0xFFE1E6EF),
)
private val Oscuro = darkColorScheme(
    primary = AzulCielo, onPrimary = Color(0xFF001A42), primaryContainer = Color(0xFF14336B), onPrimaryContainer = Color(0xFFDDE7FA),
    secondary = Color(0xFF4CD08A), secondaryContainer = Color(0xFF0F3D27), onSecondaryContainer = Color(0xFFD5F3E3),
    tertiary = Color(0xFFFFA669), tertiaryContainer = Color(0xFF5A2A08), onTertiaryContainer = Color(0xFFFFE6D5),
    error = Color(0xFFFF6B6E), background = Color(0xFF0D1320), surface = Color(0xFF0D1320), surfaceContainer = Color(0xFF161E2E),
    surfaceContainerLow = Color(0xFF161E2E), surfaceContainerHigh = Color(0xFF1E2839), outlineVariant = Color(0xFF283349),
)

@Composable
fun MiuvTheme(content: @Composable () -> Unit) {
    val t = Typography()
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        typography = t.copy(
            headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
            headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)),
        content = content,
    )
}

val GradienteHero = Brush.linearGradient(listOf(AzulNoche, AzulUV, Color(0xFF2E6BD1)))

// ---------- Componentes compartidos (consistencia: mismos bloques en todas las pantallas) ----------

@Composable
fun Tarjeta(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
    val shape = MaterialTheme.shapes.medium
    if (onClick != null) Card(onClick = onClick, modifier = m, shape = shape, colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(0.dp)) { Column(Modifier.padding(16.dp), content = content) }
    else Card(modifier = m, shape = shape, colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(0.dp)) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun Seccion(titulo: String, accion: String? = null, onAccion: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (accion != null) TextButton(onClick = onAccion) { Text(accion) }
    }
}

@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color, icono: ImageVector? = null) {
    Row(Modifier.clip(CircleShape).background(fondo).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icono != null) { Icon(icono, null, tint = color, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(texto, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun EtiquetaEstado(e: EstadoTarea, fin: Long?) {
    val c = MaterialTheme.colorScheme
    when (e) {
        EstadoTarea.ENTREGADA -> Etiqueta("Entregada", c.secondaryContainer, c.onSecondaryContainer, Icons.Rounded.CheckCircle)
        EstadoTarea.VENCIDA -> Etiqueta("Cerrada sin entregar", c.error.copy(alpha = .14f), c.error, Icons.Rounded.ErrorOutline)
        EstadoTarea.POR_INICIAR -> Etiqueta("Aún no abre", c.surfaceContainerHigh, c.onSurfaceVariant, Icons.Rounded.Schedule)
        EstadoTarea.PENDIENTE -> {
            val h = fin?.let { (it - System.currentTimeMillis()) / 3_600_000 }
            if (h != null && h < 48) Etiqueta(restante(fin), c.tertiaryContainer, c.onTertiaryContainer, Icons.Rounded.LocalFireDepartment)
            else Etiqueta(fin?.let { restante(it) } ?: "Pendiente", c.primaryContainer, c.onPrimaryContainer, Icons.Rounded.HourglassTop)
        }
    }
}

@Composable
fun Vacio(icono: ImageVector, titulo: String, texto: String, accion: String? = null, onAccion: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icono, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (accion != null) { Spacer(Modifier.height(14.dp)); FilledTonalButton(onClick = onAccion) { Text(accion) } }
    }
}

// ---------- Fechas en lenguaje natural ----------
val es = Locale("es", "MX")
fun fmt(ms: Long, p: String = "EEE d MMM, HH:mm") = SimpleDateFormat(p, es).format(Date(ms)).replace(".", "")
fun restante(fin: Long): String {
    val d = fin - System.currentTimeMillis()
    if (d < 0) return "Cerró ${fmt(fin, "d MMM")}"
    val h = d / 3_600_000
    return when { h < 1 -> "¡Menos de 1 hora!"; h < 24 -> "Quedan ${h} h"; h < 48 -> "Vence mañana"; else -> "Quedan ${h / 24} días" }
}
fun haceCuanto(ms: Long): String {
    if (ms <= 0) return "nunca"
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when { m < 1 -> "justo ahora"; m < 60 -> "hace $m min"; m < 1440 -> "hace ${m / 60} h"; else -> fmt(ms, "d MMM, HH:mm") }
}
val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
fun hoyIndice() = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
fun minutos(h: String) = h.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
val TextoChico = TextStyle(fontSize = 12.sp)
