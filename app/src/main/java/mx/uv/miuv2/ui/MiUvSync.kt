package mx.uv.miuv2.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import mx.uv.miuv2.data.*
import org.json.JSONTokener
import kotlin.coroutines.resume

private enum class Paso { LOGIN, HORARIO, CREDENCIAL, LISTO }

private suspend fun WebView.js(code: String): String = suspendCancellableCoroutine { c ->
    evaluateJavascript(code) { r -> if (c.isActive) c.resume(runCatching { JSONTokener(r).nextValue()?.toString() ?: "" }.getOrDefault(r ?: "")) }
}

private suspend fun WebView.esperarCarga(cargas: () -> Int, desde: Int, maxMs: Long = 15_000) {
    var t = 0L
    while (t < maxMs && cargas() == desde) { delay(250); t += 250 }
    delay(1200) // da tiempo a que el portal pinte su contenido dinámico
}

/**
 * Ventana que conecta con MiUV. La automatización es visible (el usuario ve qué pasa y puede tomar el control):
 * inicia sesión con el formulario oficial, abre "Horario" y "Credencial" y guarda lo que encuentra.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiUvSync(onCerrar: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var cargas by remember { mutableIntStateOf(0) }
    var paso by remember { mutableStateOf(Paso.LOGIN) }
    var mensaje by remember { mutableStateOf("Abriendo MiUV…") }
    var manual by remember { mutableStateOf(false) }
    var horarioOk by remember { mutableStateOf(Repo.horario.value.isNotEmpty()) }
    var credOk by remember { mutableStateOf(Repo.credencial.value.capturaPath != null) }
    var snack by remember { mutableStateOf<String?>(null) }
    var ver by remember { mutableStateOf(false) }  // la ventana de MiUV solo se muestra si el usuario la pide

    suspend fun guardarHorario(w: WebView): Boolean {
        // Cada tarjeta muestra un día a la vez: se lee cada una POR SEPARADO (lo visible tras cada toque
        // y su texto oculto), para que los días de una materia nunca se mezclen con los de otra.
        val url = w.url
        val visible0 = w.js(MiUV.JS_TEXTO)
        val pareceHorario = Regex("""(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado)\s+\d{1,2}:\d{2}""", RegexOption.IGNORE_CASE).containsMatchIn(visible0)
        if (!pareceHorario) return false
        // Empezamos de cero: el horario anterior se borra para que nada viejo se mezcle con lo nuevo.
        Repo.guardarHorario(emptyList()); horarioOk = false
        // Lectura directa: MiUV ya trae TODOS los días en la página (los paneles ocultos), se leen sin tocar nada.
        val directas = runCatching { MiUV.clasesDom(w.js(MiUV.JS_HORARIO_DOM)) }.getOrDefault(emptyList())
        if (directas.isNotEmpty()) {
            val clases = HorarioParser.limpiar(directas)
            Repo.guardarHorario(clases); horarioOk = true
            snack = "Horario guardado: ${clases.size} clases de ${clases.map { it.materia }.distinct().size} materias"
            return true
        }
        val n = w.js(MiUV.JS_MARCAR_TARJETAS).toDoubleOrNull()?.toInt() ?: 0
        val crudas = mutableListOf<Clase>()
        // Lectura de respaldo: lo que se ve en la página completa (cada tarjeta muestra su propio título).
        crudas += runCatching { HorarioParser.parseTexto(visible0, unirBloques = false) }.getOrDefault(emptyList())
        for (i in 0 until n.coerceAtMost(25)) {
            snack = "Leyendo materia ${i + 1} de $n…"
            val vistos = linkedSetOf(w.js(MiUV.jsTextoTarjeta(i)))
            repeat(6) {
                w.js(MiUV.jsTocarTarjeta(i)); delay(450)
                if (w.url != url) { w.goBack(); delay(1500); w.js(MiUV.JS_MARCAR_TARJETAS); return@repeat }
                w.js(MiUV.jsTextoTarjeta(i)).takeIf { it.isNotBlank() }?.let { vistos += it }
            }
            vistos += w.js(MiUV.jsTextoOcultoTarjeta(i))  // último: solo aporta días que nunca se vieron
            vistos.forEach { t -> crudas += runCatching { HorarioParser.parseTexto(t, unirBloques = false) }.getOrDefault(emptyList()) }
        }
        // Tras todos los toques las tarjetas pueden mostrar otro día: una última lectura de la página completa.
        if (n > 0) crudas += runCatching { HorarioParser.parseTexto(w.js(MiUV.JS_TEXTO), unirBloques = false) }.getOrDefault(emptyList())
        val clases = HorarioParser.limpiar(crudas)
            .ifEmpty { runCatching { HorarioParser.parse(w.js(MiUV.JS_TABLAS)) }.getOrDefault(emptyList()) }
        if (clases.isNotEmpty()) { Repo.guardarHorario(clases); horarioOk = true; snack = "Horario guardado: ${clases.size} clases de ${clases.map { it.materia }.distinct().size} materias" }
        else snack = "Vi el horario pero no pude leer las clases. Puedes agregarlas a mano con el botón +."
        return clases.isNotEmpty()
    }
    suspend fun guardarCredencial(w: WebView): Boolean {
        val texto = w.js(MiUV.JS_TEXTO)
        if (!Regex("matr[ií]cula", RegexOption.IGNORE_CASE).containsMatchIn(texto) ||
            !Regex("vigencia", RegexOption.IGNORE_CASE).containsMatchIn(texto)) return false
        val rect = runCatching { org.json.JSONObject(w.js(MiUV.JS_ENFOCAR_CREDENCIAL)) }.getOrNull()
        delay(700)
        // Captura toda la página y recorta la credencial completa.
        @Suppress("DEPRECATION") val escala = w.scale
        val total = (w.contentHeight * escala).toInt().coerceAtLeast(w.height).coerceAtMost(12000)
        val pagina = Bitmap.createBitmap(w.width.coerceAtLeast(1), total, Bitmap.Config.ARGB_8888)
        w.draw(Canvas(pagina))
        val bmp = if (rect != null) {
            val pad = (16 * escala).toInt()
            val y = (w.scrollY + rect.optDouble("top") * escala).toInt() - pad
            val h = (rect.optDouble("height") * escala).toInt() + 2 * pad
            val y0 = y.coerceIn(0, total - 1)
            Bitmap.createBitmap(pagina, 0, y0, pagina.width, h.coerceAtMost(total - y0).coerceAtLeast(1))
        } else pagina
        val f = Repo.archivoCaptura(); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Repo.guardarCredencial(MiUV.datosCredencial(texto, Repo.credencial.value).copy(capturaPath = f.absolutePath))
        credOk = true; snack = "Credencial guardada"; return true
    }

    // Automatización paso a paso.
    LaunchedEffect(web) {
        val w = web ?: return@LaunchedEffect
        if (!Repo.hayInternet()) { mensaje = "Necesitas internet para conectar con MiUV."; manual = true; return@LaunchedEffect }
        w.esperarCarga({ cargas }, 0)
        if (w.js(MiUV.JS_ES_LOGIN) == "true") {
            mensaje = "Iniciando sesión en MiUV…"
            val c0 = cargas
            w.js(MiUV.jsLogin(Repo.usuario(), Repo.contrasena()))
            w.esperarCarga({ cargas }, c0, 20_000)
            if (w.js(MiUV.JS_ES_LOGIN) == "true") {
                mensaje = "MiUV no aceptó la sesión. Revisa la ventana: puedes iniciar sesión tú mismo y luego usar los botones de abajo."
                manual = true; return@LaunchedEffect
            }
        }
        val inicio = w.url
        paso = Paso.HORARIO; mensaje = "Buscando tu horario…"
        var hecho = false
        for (patron in listOf("^horario", "horario", "escolaridad", "^horario")) {
            val c0 = cargas
            if (w.js(MiUV.jsIrA(patron)) == "none") continue
            w.esperarCarga({ cargas }, c0, 8_000)
            if (guardarHorario(w)) { hecho = true; break }
        }
        if (!hecho) mensaje = "No encontré el horario automáticamente."
        paso = Paso.CREDENCIAL; mensaje = if (hecho) "Buscando tu credencial…" else "$mensaje Buscando tu credencial…"
        if (inicio != null) { val c0 = cargas; w.loadUrl(inicio); w.esperarCarga({ cargas }, c0) }
        var cred = false
        for (patron in listOf("credencial", "credencial electr", "mi credencial")) {
            val c0 = cargas
            if (w.js(MiUV.jsIrA(patron)) == "none") continue
            w.esperarCarga({ cargas }, c0, 8_000)
            delay(1500)
            if (guardarCredencial(w)) { cred = true; break }
        }
        paso = Paso.LISTO
        mensaje = when {
            hecho && cred -> "¡Listo! Horario y credencial guardados para usarlos sin internet."
            hecho || cred -> "Guardé ${if (hecho) "tu horario" else "tu credencial"}. Para lo que falta, ábrelo en la ventana y pulsa el botón correspondiente."
            else -> "No pude encontrarlos solo. Navega en la ventana hasta tu horario o credencial y pulsa \"Guardar\"."
        }
        manual = !(hecho && cred)
        if (!manual && !ver) { delay(2500); onCerrar() }
    }

    androidx.activity.compose.BackHandler(ver) { if (web?.canGoBack() == true) web?.goBack() else onCerrar() }
    // Capa a pantalla completa dentro de la actividad (respeta las barras del sistema, a diferencia de un Dialog).
    // En segundo plano la ventana sigue funcionando, pero colocada fuera de la pantalla (no bloquea toques).
    Box(Modifier.fillMaxSize().then(if (ver) Modifier else Modifier.offset(x = 4000.dp))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Conectar con MiUV", style = MaterialTheme.typography.titleLarge)
                        Text("Solo leemos tu horario y credencial", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ ver = false }) { Text("Ocultar") }
                    IconButton(onCerrar) { Icon(Icons.Rounded.Close, "Cerrar") }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PasoChip("Sesión", paso.ordinal > 0, paso == Paso.LOGIN, Icons.Rounded.Login)
                    PasoChip("Horario", horarioOk, paso == Paso.HORARIO, Icons.Rounded.Schedule)
                    PasoChip("Credencial", credOk, paso == Paso.CREDENCIAL, Icons.Rounded.Badge)
                }
                Tarjeta(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (paso != Paso.LISTO && !manual) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                        Text(mensaje, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                AndroidView(factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true; settings.useWideViewPort = true; settings.builtInZoomControls = true; settings.displayZoomControls = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = object : WebViewClient() { override fun onPageFinished(v: WebView, url: String) { cargas++ } }
                        loadUrl(MiUV.LOGIN); web = this
                    }
                }, modifier = Modifier.weight(1f).fillMaxWidth())
                run {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var ocupado by remember { mutableStateOf(false) }
                            val scope = rememberCoroutineScope()
                            OutlinedButton({ web?.let { w -> scope.launch2 { ocupado = true; if (!guardarHorario(w)) snack = "En esta página no hay un horario que pueda leer."; ocupado = false } } },
                                Modifier.weight(1f), enabled = !ocupado) { Text("Guardar horario") }
                            OutlinedButton({ web?.let { w -> scope.launch2 { ocupado = true; if (!guardarCredencial(w)) snack = "Abre la página de tu credencial primero."; ocupado = false } } },
                                Modifier.weight(1f), enabled = !ocupado) { Text("Guardar credencial") }
                        }
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val scopeDiag = rememberCoroutineScope()
                        TextButton({ web?.let { w -> scopeDiag.launch {
                            w.js(MiUV.JS_MARCAR_TARJETAS)
                            val d = w.js(MiUV.JS_DIAGNOSTICO)
                            ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, d), "Compartir diagnóstico del horario"))
                        } } }, Modifier.fillMaxWidth()) { Text("Compartir diagnóstico del horario") }
                        Button(onCerrar, Modifier.fillMaxWidth()) { Text("Terminar") }
                    }
                }
            }
        }
    }
    if (!ver) Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, end = 16.dp, bottom = 92.dp), contentAlignment = Alignment.BottomCenter) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface)) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (paso != Paso.LISTO && !manual) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.inverseOnSurface); Spacer(Modifier.width(10.dp)) }
                else Icon(if (manual) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, null, Modifier.size(18.dp).padding(end = 4.dp))
                Text(mensaje, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton({ ver = true }) { Text(if (manual) "Revisar" else "Ver", color = MaterialTheme.colorScheme.inversePrimary) }
                if (manual || paso == Paso.LISTO) IconButton(onCerrar) { Icon(Icons.Rounded.Close, "Cerrar") }
            }
        }
    }
    if (ver) snack?.let { s -> LaunchedEffect(s) { delay(3000); snack = null }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
                Snackbar { Text(s) } } }
}

private fun kotlinx.coroutines.CoroutineScope.launch2(b: suspend () -> Unit) { launch { b() } }

@Composable
private fun PasoChip(t: String, ok: Boolean, activo: Boolean, icono: ImageVector) {
    val cs = MaterialTheme.colorScheme
    Etiqueta(t, if (ok) cs.secondaryContainer else if (activo) cs.primaryContainer else cs.surfaceContainerHigh,
        if (ok) cs.onSecondaryContainer else if (activo) cs.onPrimaryContainer else cs.onSurfaceVariant, if (ok) Icons.Rounded.CheckCircle else icono)
}
