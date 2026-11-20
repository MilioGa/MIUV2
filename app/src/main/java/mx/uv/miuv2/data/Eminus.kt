package mx.uv.miuv2.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class EminusError(msg: String, val credenciales: Boolean = false) : IOException(msg)

/**
 * Cliente nativo de la API de Eminus 4 (la misma que usa eminus.uv.mx/eminus4).
 * Solo usa llamadas de lectura, excepto [entregar], que replica el flujo de "Entregar" del estudiante.
 * Nunca llama a endpoints de borrar ni modificar entregas existentes.
 */
class Eminus(private var token: String? = null) {
    private val api = "https://eminus.uv.mx/eminusapi/api"
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val accessToken get() = token

    private fun req(path: String) = Request.Builder().url(api + path).header("Accept", "application/json")
        .apply { token?.let { header("Authorization", "Bearer $it") } }

    private fun exec(r: Request): Any? {
        http.newCall(r).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) throw EminusError("Tu sesión de Eminus expiró", credenciales = true)
            if (!resp.isSuccessful) throw EminusError("Eminus respondió con error ${resp.code}")
            val t = body.trim()
            return when { t.startsWith("{") -> JSONObject(t); t.startsWith("[") -> JSONArray(t); t.isEmpty() -> null; else -> t }
        }
    }
    private fun get(path: String) = exec(req(path).get().build())
    private fun post(path: String, o: JSONObject) = exec(req(path).post(o.toString().toRequestBody(jsonType)).build())

    /** Inicia sesión. Devuelve el nombre del estudiante si Eminus lo envía. */
    fun login(user: String, pass: String): String {
        val body = JSONObject().put("username", user.trim()).put("password", EminusCipher.encrypt(pass.trim()))
        val r = try { post("/auth", body) } catch (e: EminusError) {
            throw EminusError("Matrícula o contraseña incorrectas", credenciales = true)
        }
        val o = (if (r is JSONArray) r.optJSONObject(r.length() - 1) else r as? JSONObject)
            ?: throw EminusError("Respuesta inesperada de Eminus")
        val data = o.optJSONObject("body") ?: o
        token = data.optString("accessToken").ifBlank { o.optString("accessToken") }.ifBlank { null }
            ?: throw EminusError("Matrícula o contraseña incorrectas", credenciales = true)
        return listOf(data.optString("nombre"), data.optString("paterno"), data.optString("materno"))
            .filter { it.isNotBlank() && it != "null" }.joinToString(" ")
    }

    fun nombreUsuario(): String = runCatching {
        val o = get("/Auth/get-user").let { if (it is JSONArray) it.optJSONObject(0) else it as? JSONObject } ?: return ""
        val d = o.optJSONObject("body") ?: o
        listOf(d.optString("nombre"), d.optString("paterno"), d.optString("materno")).filter { it.isNotBlank() && it != "null" }.joinToString(" ")
    }.getOrDefault("")

    fun cursos(): List<Curso> {
        fun pedir(validez: Int) = findObjects(get("/Cursos/%22%22/$validez/0/0/%22%22/0")) { it.has("idCurso") }
            .map { Curso(it.optLong("idCurso"), it.optString("nombre").ifBlank { it.optString("nombreCurso") }.trim()) }
        return pedir(2).ifEmpty { pedir(0) }.distinctBy { it.id }
    }

    fun tareas(c: Curso, now: Long = System.currentTimeMillis()): List<Tarea> {
        val acts = findObjects(get("/Actividad/getActividadesEstudiante/${c.id}")) { it.has("idActividad") }
        return acts.mapNotNull { a ->
            val id = a.optLong("idActividad")
            val fin = fecha(a, "fechaTermino")
            val prorroga = a.optLong("fechaProrrogaEpoch").takeIf { it > 0 }?.let { if (it < 10_000_000_000) it * 1000 else it }
            val limite = maxOf(fin ?: 0, prorroga ?: 0).takeIf { it > 0 }
            if (limite != null && limite < now - 21L * 86_400_000) return@mapNotNull null // muy antiguas
            val estadoAct = a.optInt("estadoAct")
            val equipos = a.optInt("porEquipos") == 1
            val equipo = if (equipos) runCatching { idEquipo(c.id, id) }.getOrDefault(0L) else 0L
            val entrega = runCatching { entregaActual(c.id, id, equipo) }.getOrNull()
            val fEntrega = entrega?.let { fecha(it, "fechaEntrega") }
            val estado = when {
                fEntrega != null -> EstadoTarea.ENTREGADA
                estadoAct == 3 -> EstadoTarea.POR_INICIAR
                limite != null && limite < now -> EstadoTarea.VENCIDA
                else -> EstadoTarea.PENDIENTE
            }
            Tarea(id, c.id, c.nombre, a.optString("titulo").trim(), limpiarHtml(a.optString("descripcion")),
                fecha(a, "fechaInicio"), limite, estado, equipos, fEntrega)
        }
    }

    /** Entrega actual del estudiante (null si nunca ha guardado nada). */
    /** Equipo del estudiante en una actividad por equipos (Eminus lo manda en los integrantes). */
    fun idEquipo(curso: Long, act: Long): Long {
        val r = get("/Actividad/getActividadEstudiante/$curso/$act")
        val o = (if (r is JSONArray) r.optJSONObject(0) else r as? JSONObject) ?: return 0
        o.optLong("idEquipo").takeIf { it > 0 }?.let { return it }
        return findObjects(o.opt("integrantes")) { it.has("idEquipo") }.map { it.optLong("idEquipo") }.firstOrNull { it > 0 } ?: 0
    }

    fun entregaActual(curso: Long, act: Long, equipo: Long = 0): JSONObject? {
        val r = get("/Actividad/getActUsuarioMiRevision/$curso/$act/$equipo")
        return (if (r is JSONArray) r.optJSONObject(0) else r as? JSONObject)?.takeIf { it.length() > 0 }
    }

    /**
     * Entrega una tarea igual que el botón "Entregar" de Eminus:
     * 1) crea el borrador si no existe, 2) adjunta los archivos, 3) envía la entrega (visible = 1).
     * Se niega si la tarea ya tiene una entrega enviada, para no sobrescribir nada.
     */
    fun entregar(curso: Long, act: Long, comentario: String, archivos: List<File>, progreso: (String) -> Unit = {}) {
        progreso("Revisando la tarea…")
        val actual = entregaActual(curso, act)
        if (actual != null && fecha(actual, "fechaEntrega") != null)
            throw EminusError("Esta tarea ya fue entregada. MIUV2 no modifica entregas existentes; si necesitas cambiarla hazlo en Eminus web.")
        var idEntrega = actual?.optLong("idActividadEntrega") ?: 0L
        if (idEntrega <= 0) {
            progreso("Preparando la entrega…")
            val r = post("/Actividad/InsertaActividadEntrega", JSONObject().put("idActividadEntrega", 0).put("texto", "")
                .put("idActividad", act).put("idCurso", curso).put("visible", 0).put("tieneAdjuntos", 0).put("idEquipo", 0)
                .put("fechaEntrega", iso.format(System.currentTimeMillis())))
            val o = if (r is JSONArray) r.optJSONObject(0) else r as? JSONObject
            idEntrega = o?.optString("salida")?.toLongOrNull() ?: 0
            if (idEntrega <= 0) idEntrega = entregaActual(curso, act)?.optLong("idActividadEntrega") ?: 0
            if (idEntrega <= 0) throw EminusError("Eminus no creó la entrega. No se envió nada; intenta de nuevo.")
        }
        archivos.forEachIndexed { i, f ->
            progreso("Subiendo ${f.name} (${i + 1}/${archivos.size})…")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", f.name, f.asRequestBody("application/octet-stream".toMediaType())).build()
            exec(req("/actividad/adjuntarEntrega?idCurso=$curso&idActividad=$act&idActividadEntregas=$idEntrega").post(body).build())
        }
        progreso("Enviando entrega…")
        post("/Actividad/InsertaActividadEntrega", JSONObject().put("idActividadEntrega", idEntrega)
            .put("texto", comentario.ifBlank { "Entrega enviada desde MIUV2" }).put("idActividad", act).put("idCurso", curso)
            .put("visible", 1).put("tieneAdjuntos", if (archivos.isEmpty()) 0 else 1).put("idEquipo", 0).put("fechaEntrega", JSONObject.NULL))
        progreso("Confirmando…")
        val fin = entregaActual(curso, act)
        if (fin == null || fecha(fin, "fechaEntrega") == null)
            throw EminusError("Eminus recibió los archivos pero no confirmó la entrega. Revísala en Eminus web antes de reintentar.")
    }

    companion object {
        private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        private val formatos = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm")

        fun fecha(o: JSONObject, campo: String): Long? {
            o.optLong(campo + "Epoch").takeIf { it > 0 }?.let { return if (it < 10_000_000_000) it * 1000 else it }
            val s = o.optString(campo).takeIf { it.isNotBlank() && it != "null" } ?: return null
            s.toLongOrNull()?.let { return if (it < 10_000_000_000) it * 1000 else it }
            val limpio = s.replace(Regex("Z$|[+-]\\d{2}:\\d{2}$"), "")
            for (f in formatos) runCatching {
                return SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Mexico_City") }.parse(limpio)!!.time
            }
            return null
        }

        fun limpiarHtml(h: String) = if (h == "null") "" else android.text.Html.fromHtml(h, android.text.Html.FROM_HTML_MODE_COMPACT)
            .toString().replace(Regex("\n{3,}"), "\n\n").trim()

        /** Busca recursivamente objetos que cumplan [ok] (la API a veces envuelve listas en objetos). */
        fun findObjects(any: Any?, ok: (JSONObject) -> Boolean): List<JSONObject> {
            val out = mutableListOf<JSONObject>()
            fun walk(x: Any?) {
                when (x) {
                    is JSONArray -> for (i in 0 until x.length()) walk(x.opt(i))
                    is JSONObject -> if (ok(x)) out += x else x.keys().forEach { walk(x.opt(it)) }
                }
            }
            walk(any); return out
        }
    }
}
