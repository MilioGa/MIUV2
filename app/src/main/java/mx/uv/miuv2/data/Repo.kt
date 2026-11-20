package mx.uv.miuv2.data

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class EntregaEnCola(val id: String, val cursoId: Long, val tareaId: Long, val titulo: String, val comentario: String,
                         val archivos: List<String>, val estado: String = "En espera de internet", val error: String? = null) {
    fun toJson() = JSONObject().put("id", id).put("cursoId", cursoId).put("tareaId", tareaId).put("titulo", titulo)
        .put("comentario", comentario).put("archivos", JSONArray(archivos)).put("estado", estado).put("error", error ?: "")
    companion object {
        fun from(o: JSONObject) = EntregaEnCola(o.getString("id"), o.getLong("cursoId"), o.getLong("tareaId"), o.optString("titulo"),
            o.optString("comentario"), o.getJSONArray("archivos").let { a -> (0 until a.length()).map { a.getString(it) } },
            o.optString("estado"), o.optString("error").ifBlank { null })
    }
}

data class Sync(val cargando: Boolean = false, val mensaje: String? = null, val error: String? = null, val ultima: Long = 0)

/** Fuente única de datos. Todo se guarda en el teléfono para funcionar sin internet. */
object Repo {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eminus = Eminus()

    val nombre = MutableStateFlow("")
    val tareas = MutableStateFlow<List<Tarea>>(emptyList())
    val horario = MutableStateFlow<List<Clase>>(emptyList())
    val eventos = MutableStateFlow<List<Evento>>(emptyList())
    val credencial = MutableStateFlow(Credencial())
    val cola = MutableStateFlow<List<EntregaEnCola>>(emptyList())
    private val _sync = MutableStateFlow(Sync())
    val sync: StateFlow<Sync> = _sync.asStateFlow()
    val online = MutableStateFlow(true)

    private fun f(n: String) = File(app.filesDir, n)
    private fun leer(n: String) = f(n).takeIf { it.exists() }?.readText()
    private fun escribir(n: String, s: String) = f(n).writeText(s)
    private val prefs get() = app.getSharedPreferences("miuv2", Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        nombre.value = prefs.getString("nombre", "") ?: ""
        leer("tareas.json")?.let { tareas.value = JSONArray(it).mapObj(Tarea::from) }
        leer("horario.json")?.let { horario.value = JSONArray(it).mapObj(Clase::from) }
        leer("eventos.json")?.let { eventos.value = JSONArray(it).mapObj(Evento::from) }
        leer("credencial.json")?.let { credencial.value = Credencial.from(JSONObject(it)) }
        leer("cola.json")?.let { cola.value = JSONArray(it).mapObj(EntregaEnCola::from) }
        _sync.value = Sync(ultima = prefs.getLong("ultima", 0))
        val cm = app.getSystemService(ConnectivityManager::class.java)
        online.value = hayInternet()
        if (sesionIniciada) programarAvisos()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: android.net.Network) { online.value = true }
            override fun onLost(n: android.net.Network) { online.value = hayInternet() }
        })
    }

    fun hayInternet(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    val sesionIniciada get() = Vault.user(app) != null

    suspend fun iniciarSesion(user: String, pass: String) = withContext(Dispatchers.IO) {
        val e = Eminus()
        val n = e.login(user, pass).ifBlank { e.nombreUsuario() }
        eminus = e
        Vault.save(app, user.trim(), pass.trim())
        nombre.value = n.toTitle()
        prefs.edit().putString("nombre", nombre.value).apply()
        programarAvisos()
    }

    fun cerrarSesion() {
        WorkManager.getInstance(app).cancelAllWork()
        Vault.clear(app); eminus = Eminus()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        listOf("tareas.json", "horario.json", "eventos.json", "credencial.json", "cola.json").forEach { f(it).delete() }
        File(app.filesDir, "cola").deleteRecursively(); f("credencial.png").delete()
        prefs.edit().clear().apply()
        nombre.value = ""; tareas.value = emptyList(); horario.value = emptyList(); eventos.value = emptyList()
        credencial.value = Credencial(); cola.value = emptyList(); _sync.value = Sync()
    }

    private fun <T> conSesion(bloque: (Eminus) -> T): T {
        if (eminus.accessToken == null) eminus.login(Vault.user(app) ?: throw EminusError("Inicia sesión", true), Vault.pass(app) ?: "")
        return try { bloque(eminus) } catch (e: EminusError) {
            if (!e.credenciales) throw e
            eminus = Eminus(); eminus.login(Vault.user(app)!!, Vault.pass(app) ?: ""); bloque(eminus)
        }
    }

    /** Sincroniza Eminus y el calendario oficial. Si no hay internet, se quedan los datos guardados. */
    fun sincronizar() {
        if (_sync.value.cargando) return
        scope.launch {
            if (!hayInternet()) { _sync.value = _sync.value.copy(error = "Sin internet. Mostrando lo último que guardaste."); return@launch }
            _sync.value = _sync.value.copy(cargando = true, error = null, mensaje = "Conectando con Eminus…")
            val errores = mutableListOf<String>()
            runCatching {
                descargarTareas { i, n, c -> _sync.value = _sync.value.copy(mensaje = "Revisando ${c.take(28)} ($i/$n)") }
                if (nombre.value.isBlank()) conSesion { it.nombreUsuario() }.takeIf { it.isNotBlank() }?.let {
                    nombre.value = it.toTitle(); prefs.edit().putString("nombre", nombre.value).apply() }
            }.onFailure { errores += "Eminus: ${it.message}" }
            _sync.value = _sync.value.copy(mensaje = "Descargando calendario escolar…")
            runCatching { guardarEventos(CalendarioUV.descargar()) }.onFailure { errores += "Calendario: ${it.message}" }
            val ahora = System.currentTimeMillis()
            prefs.edit().putLong("ultima", ahora).apply()
            _sync.value = Sync(false, null, errores.joinToString("\n").ifBlank { null }, ahora)
        }
    }

    @Synchronized private fun descargarTareas(progreso: (Int, Int, String) -> Unit = { _, _, _ -> }) {
        val cursos = conSesion { it.cursos() }
        val lista = mutableListOf<Tarea>()
        cursos.forEachIndexed { i, c ->
            progreso(i + 1, cursos.size, c.nombre)
            runCatching { conSesion { it.tareas(c) } }.onSuccess { lista += it }
        }
        tareas.value = lista.sortedBy { it.fin ?: Long.MAX_VALUE }
        escribir("tareas.json", tareas.value.toJsonArray { it.toJson() }.toString())
        revisarAvisos()
    }

    // ---- Notificaciones: recordatorios antes del cierre y tareas nuevas ----
    var avisarCierre: Boolean
        get() = prefs.getBoolean("notif_cierre", true)
        set(v) { prefs.edit().putBoolean("notif_cierre", v).apply() }
    var avisarNuevas: Boolean
        get() = prefs.getBoolean("notif_nuevas", true)
        set(v) { prefs.edit().putBoolean("notif_nuevas", v).apply() }

    fun programarAvisos() {
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("avisos", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AvisosWorker>(1, java.util.concurrent.TimeUnit.HOURS).build())
    }

    /** Desde segundo plano: actualiza tareas si hay internet y revisa qué avisar. */
    internal fun revisarEnSegundoPlano() {
        if (!sesionIniciada) return
        if (hayInternet()) runCatching { descargarTareas() }.onFailure { revisarAvisos() } else revisarAvisos()
    }

    @Synchronized private fun revisarAvisos() {
        val ahora = System.currentTimeMillis()
        val l = tareas.value
        // Tareas nuevas: la primera vez solo se memorizan, para no avisar de las que ya existían.
        val conocidas = prefs.getStringSet("conocidas", null)
        val ids = l.map { "${it.cursoId}:${it.id}" }.toSet()
        if (conocidas != null && avisarNuevas) l.filter { "${it.cursoId}:${it.id}" !in conocidas &&
            (it.estado == EstadoTarea.PENDIENTE || it.estado == EstadoTarea.POR_INICIAR) }.take(5).forEach { Notificar.nueva(app, it) }
        prefs.edit().putStringSet("conocidas", ids + (conocidas ?: emptySet())).apply()
        // Recordatorios: 24 h y 2 h antes del cierre, una sola vez cada uno.
        if (!avisarCierre) return
        val avisadas = prefs.getStringSet("avisadas", emptySet())!!.toMutableSet()
        for (t in l) {
            if (t.estado != EstadoTarea.PENDIENTE) continue
            val fin = t.fin ?: continue
            val falta = fin - ahora
            if (falta <= 0) continue
            val clave = when { falta <= 2 * 3_600_000L -> "2h"; falta <= 24 * 3_600_000L -> "24h"; else -> continue }
            val k = "${t.cursoId}:${t.id}:$clave"
            if (k in avisadas) continue
            avisadas += k; avisadas += "${t.cursoId}:${t.id}:24h"
            Notificar.cierre(app, t, falta)
        }
        prefs.edit().putStringSet("avisadas", avisadas).apply()
    }

    fun guardarHorario(l: List<Clase>) { horario.value = l; escribir("horario.json", l.toJsonArray { it.toJson() }.toString()) }
    private fun guardarEventos(l: List<Evento>) { eventos.value = l; escribir("eventos.json", l.toJsonArray { it.toJson() }.toString()) }
    fun guardarCredencial(c: Credencial) { credencial.value = c; escribir("credencial.json", c.toJson().toString()) }
    fun archivoCaptura() = f("credencial.png")
    fun usuario() = Vault.user(app) ?: ""
    fun contrasena() = Vault.pass(app) ?: ""

    // ---------- Entregas ----------
    fun copiarArchivo(uri: Uri, dir: File): File {
        var nombre = "archivo_${System.currentTimeMillis()}"
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> nombre = n } }
        dir.mkdirs()
        val dest = File(dir, nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        app.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { i.copyTo(it) } }
        return dest
    }

    /** Pone la entrega en la cola; se envía en cuanto haya internet (incluso con la app cerrada). */
    fun encolarEntrega(t: Tarea, comentario: String, archivos: List<Uri>): EntregaEnCola {
        val id = "e" + System.currentTimeMillis()
        val dir = File(app.filesDir, "cola/$id")
        val copias = archivos.map { copiarArchivo(it, dir).absolutePath }
        val e = EntregaEnCola(id, t.cursoId, t.id, t.titulo, comentario, copias, if (hayInternet()) "Enviando…" else "En espera de internet")
        actualizarCola { it + e }
        WorkManager.getInstance(app).enqueueUniqueWork(id, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<EntregaWorker>().setInputData(workDataOf("id" to id))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build())
        return e
    }

    fun cancelarEntrega(id: String) {
        WorkManager.getInstance(app).cancelUniqueWork(id)
        File(app.filesDir, "cola/$id").deleteRecursively()
        actualizarCola { l -> l.filterNot { it.id == id } }
    }

    @Synchronized fun actualizarCola(f: (List<EntregaEnCola>) -> List<EntregaEnCola>) {
        cola.value = f(cola.value); escribir("cola.json", cola.value.toJsonArray { it.toJson() }.toString())
    }

    internal fun enviar(id: String): Boolean {
        val e = cola.value.firstOrNull { it.id == id } ?: return true
        return try {
            actualizarCola { l -> l.map { if (it.id == id) it.copy(estado = "Enviando…", error = null) else it } }
            conSesion { em -> em.entregar(e.cursoId, e.tareaId, e.comentario, e.archivos.map(::File)) { p ->
                actualizarCola { l -> l.map { if (it.id == id) it.copy(estado = p) else it } } } }
            File(app.filesDir, "cola/$id").deleteRecursively()
            actualizarCola { l -> l.filterNot { it.id == id } }
            tareas.value = tareas.value.map { if (it.id == e.tareaId) it.copy(estado = EstadoTarea.ENTREGADA, fechaEntrega = System.currentTimeMillis()) else it }
            escribir("tareas.json", tareas.value.toJsonArray { it.toJson() }.toString())
            Notificar.ok(app, e.titulo); true
        } catch (ex: Exception) {
            val red = ex is java.net.UnknownHostException || ex is java.net.SocketTimeoutException || ex is java.net.ConnectException
            actualizarCola { l -> l.map { if (it.id == id) it.copy(estado = if (red) "En espera de internet" else "No se pudo entregar", error = if (red) null else ex.message) else it } }
            if (!red) Notificar.error(app, e.titulo, ex.message ?: "Error")
            !red
        }
    }

    fun reintentar(id: String) {
        WorkManager.getInstance(app).enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<EntregaWorker>().setInputData(workDataOf("id" to id))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build())
    }
}

class AvisosWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        Repo.revisarEnSegundoPlano()
        return Result.success()
    }
}

class EntregaWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        val terminado = withContext(Dispatchers.IO) { Repo.enviar(inputData.getString("id") ?: return@withContext true) }
        return if (terminado) Result.success() else Result.retry()
    }
}

fun String.toTitle() = lowercase().split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
