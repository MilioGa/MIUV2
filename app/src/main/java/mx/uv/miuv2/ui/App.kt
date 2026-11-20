package mx.uv.miuv2.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.uv.miuv2.data.Repo
import mx.uv.miuv2.data.Tarea

enum class Tab(val etiqueta: String, val icono: ImageVector) {
    INICIO("Inicio", Icons.Rounded.Home), TAREAS("Tareas", Icons.Rounded.Assignment), HORARIO("Horario", Icons.Rounded.Schedule),
    CALENDARIO("Calendario", Icons.Rounded.CalendarMonth), CREDENCIAL("Credencial", Icons.Rounded.Badge)
}

@Composable
fun App() {
    var sesion by remember { mutableStateOf(Repo.sesionIniciada) }
    AnimatedContent(sesion, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sesion") { s ->
        if (!s) Login(onListo = { sesion = true }) else Principal(onSalir = { sesion = false })
    }
}

@Composable
fun Principal(onSalir: () -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val tab = Tab.entries[pager.currentPage]
    fun irA(t: Tab) { scope.launch { pager.animateScrollToPage(t.ordinal) } }
    var detalle by remember { mutableStateOf<Tarea?>(null) }
    var miuv by remember { mutableStateOf(false) }
    var ayuda by remember { mutableStateOf(false) }
    var ajustes by remember { mutableStateOf(false) }
    val tareas by Repo.tareas.collectAsState()

    LaunchedEffect(Unit) {
        Repo.sincronizar()
        if (Repo.horario.value.isEmpty() && Repo.hayInternet()) miuv = true
    }
    BackHandler(tab != Tab.INICIO) { irA(Tab.INICIO) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    val badge = if (t == Tab.TAREAS) tareas.count { it.estado == mx.uv.miuv2.data.EstadoTarea.PENDIENTE } else 0
                    NavigationBarItem(selected = tab == t, onClick = { irA(t) }, label = { Text(t.etiqueta) },
                        icon = { BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) { Icon(t.icono, null) } })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            // Deslizar a izquierda/derecha cambia de sección (el horario consume el gesto mientras cambia de día).
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { p ->
                val abrir: (Tarea) -> Unit = { detalle = it }
                when (Tab.entries[p]) {
                    Tab.INICIO -> Inicio(abrir, irA = ::irA, onMiuv = { miuv = true }, onAyuda = { ayuda = true }, onAjustes = { ajustes = true })
                    Tab.TAREAS -> Tareas(abrir)
                    Tab.HORARIO -> Horario(onMiuv = { miuv = true })
                    Tab.CALENDARIO -> CalendarioPantalla()
                    Tab.CREDENCIAL -> CredencialPantalla(onMiuv = { miuv = true }, visible = pager.currentPage == p && pager.currentPageOffsetFraction == 0f)
                }
            }
        }
    }
    detalle?.let { t -> DetalleTarea(t, onCerrar = { detalle = null }) }
    if (miuv) MiUvSync(onCerrar = { miuv = false })
    if (ayuda) Ayuda(onCerrar = { ayuda = false })
    if (ajustes) Ajustes(onCerrar = { ajustes = false }, onSalir = { ajustes = false; Repo.cerrarSesion(); onSalir() }, onAyuda = { ajustes = false; ayuda = true })
}

@Composable
fun Login(onListo: () -> Unit) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var ver by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun entrar() {
        if (user.isBlank() || pass.isBlank()) { error = "Escribe tu matrícula y tu contraseña."; return }
        if (!Repo.hayInternet()) { error = "Necesitas internet solo para el primer inicio de sesión."; return }
        cargando = true; error = null
        scope.launch {
            runCatching { Repo.iniciarSesion(user, pass) }
                .onSuccess { onListo() }
                .onFailure { error = it.message ?: "No se pudo iniciar sesión. Revisa tu conexión e intenta de nuevo." }
            cargando = false
        }
    }
    Column(Modifier.fillMaxSize().background(GradienteHero).verticalScroll(rememberScrollState()).systemBarsPadding().imePadding().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("MIUV2", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
        Text("Tu UV en el bolsillo: tareas, clases, calendario y credencial, con o sin internet.",
            color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(36.dp))
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp)) {
                Text("Inicia sesión", style = MaterialTheme.typography.titleLarge)
                Text("Usa la misma cuenta de Eminus y MiUV.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(user, { user = it.trim() }, label = { Text("Matrícula o usuario") }, placeholder = { Text("zS24000000") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next), enabled = !cargando)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    trailingIcon = { IconButton({ ver = !ver }) { Icon(if (ver) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (ver) "Ocultar" else "Mostrar") } },
                    visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !cargando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { entrar() }))
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = ::entrar, enabled = !cargando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (cargando) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(10.dp)); Text("Conectando con Eminus…") }
                    else Text("Entrar")
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tu contraseña se guarda cifrada solo en este teléfono y se usa únicamente para conectarte con Eminus y MiUV.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
