package mx.uv.miuv2.data

import org.json.JSONArray
import org.json.JSONObject

data class Curso(val id: Long, val nombre: String)

enum class EstadoTarea { PENDIENTE, ENTREGADA, VENCIDA, POR_INICIAR }

data class Tarea(
    val id: Long, val cursoId: Long, val curso: String, val titulo: String, val descripcion: String,
    val inicio: Long?, val fin: Long?, val estado: EstadoTarea, val porEquipos: Boolean,
    val fechaEntrega: Long? = null,
) {
    fun toJson() = JSONObject().put("id", id).put("cursoId", cursoId).put("curso", curso).put("titulo", titulo)
        .put("descripcion", descripcion).put("inicio", inicio ?: -1).put("fin", fin ?: -1).put("estado", estado.name)
        .put("equipos", porEquipos).put("fechaEntrega", fechaEntrega ?: -1)
    companion object {
        fun from(o: JSONObject) = Tarea(o.getLong("id"), o.getLong("cursoId"), o.optString("curso"), o.optString("titulo"),
            o.optString("descripcion"), o.optLong("inicio").takeIf { it > 0 }, o.optLong("fin").takeIf { it > 0 },
            EstadoTarea.valueOf(o.optString("estado", "PENDIENTE")), o.optBoolean("equipos"),
            o.optLong("fechaEntrega").takeIf { it > 0 })
    }
}

/** dia: 0 = lunes … 5 = sábado. Horas como "07:00". */
data class Clase(val dia: Int, val inicio: String, val fin: String, val materia: String, val salon: String, val profesor: String = "") {
    fun toJson() = JSONObject().put("dia", dia).put("inicio", inicio).put("fin", fin).put("materia", materia).put("salon", salon).put("profesor", profesor)
    companion object { fun from(o: JSONObject) = Clase(o.getInt("dia"), o.optString("inicio"), o.optString("fin"), o.optString("materia"), o.optString("salon"), o.optString("profesor")) }
}

/** Fechas en formato yyyy-MM-dd. */
data class Evento(val inicio: String, val fin: String, val titulo: String, val tipo: String) {
    fun toJson() = JSONObject().put("inicio", inicio).put("fin", fin).put("titulo", titulo).put("tipo", tipo)
    companion object { fun from(o: JSONObject) = Evento(o.getString("inicio"), o.getString("fin"), o.optString("titulo"), o.optString("tipo")) }
}

data class Credencial(
    val nombre: String = "", val matricula: String = "", val programa: String = "", val facultad: String = "",
    val campus: String = "", val vigencia: String = "", val capturaPath: String? = null, val fotoPath: String? = null,
) {
    fun toJson() = JSONObject().put("nombre", nombre).put("matricula", matricula).put("programa", programa).put("facultad", facultad)
        .put("campus", campus).put("vigencia", vigencia).put("captura", capturaPath ?: "").put("foto", fotoPath ?: "")
    companion object {
        fun from(o: JSONObject) = Credencial(o.optString("nombre"), o.optString("matricula"), o.optString("programa"), o.optString("facultad"),
            o.optString("campus"), o.optString("vigencia"), o.optString("captura").ifBlank { null }, o.optString("foto").ifBlank { null })
    }
}

fun <T> List<T>.toJsonArray(f: (T) -> JSONObject) = JSONArray().also { a -> forEach { a.put(f(it)) } }
fun <T> JSONArray.mapObj(f: (JSONObject) -> T): List<T> = (0 until length()).mapNotNull { optJSONObject(it)?.let(f) }
