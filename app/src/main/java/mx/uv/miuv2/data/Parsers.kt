package mx.uv.miuv2.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.nio.charset.Charset
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Calendario escolar oficial: https://www.uv.mx/escolar/calendarios/{año}/escolarizado.html */
object CalendarioUV {
    private val etiquetas = mapOf(
        "inicio_clases" to ("Inicio de clases" to "Clases"), "fin_clases" to ("Fin de clases" to "Clases"),
        "inicio_inter" to ("Inicio de intersemestral" to "Intersemestral"), "fin_intersemestral" to ("Fin de intersemestral" to "Intersemestral"),
        "festivos" to ("Día festivo" to "Sin clases"), "festivos_inter" to ("Día festivo" to "Sin clases"),
        "ordinarios" to ("Exámenes ordinarios" to "Exámenes"), "ordinarios_inter" to ("Ordinarios intersemestral" to "Exámenes"),
        "extraordinarios" to ("Exámenes extraordinarios" to "Exámenes"), "extraordinarios_inter" to ("Extraordinarios intersemestral" to "Exámenes"),
        "titulo" to ("Exámenes de título de suficiencia" to "Exámenes"), "titulo_inter" to ("Título de suficiencia intersemestral" to "Exámenes"),
        "vacaciones" to ("Vacaciones" to "Vacaciones"), "vacaciones_2" to ("Vacaciones" to "Vacaciones"),
        "filu" to ("Feria Internacional del Libro Universitario" to "Evento"),
        "inscripciones" to ("Inscripción en línea" to "Trámites"), "inscripciones_lugares" to ("Inscripción por lugares disponibles" to "Trámites"),
        "inscripciones_extemporanea" to ("Inscripción extemporánea" to "Trámites"), "induccion" to ("Inducción de nuevo ingreso" to "Evento"),
        "inhabil" to ("Día inhábil para académicos" to "Sin clases"), "tutorias" to ("Tutorías" to "Evento"),
    )

    fun descargar(): List<Evento> {
        val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
        val y = Calendar.getInstance().get(Calendar.YEAR)
        val out = mutableListOf<Evento>()
        for (anio in listOf(y - 1, y, y + 1)) runCatching {
            val r = http.newCall(Request.Builder().url("https://www.uv.mx/escolar/calendarios/$anio/escolarizado.html")
                .header("User-Agent", "Mozilla/5.0 MIUV2").build()).execute()
            r.use { if (it.isSuccessful) out += parse(String(it.body!!.bytes(), Charset.forName("ISO-8859-1"))) }
        }
        if (out.isEmpty()) throw java.io.IOException("No se pudo descargar el calendario oficial")
        return out.distinct().sortedBy { it.inicio }
    }

    fun parse(html: String): List<Evento> {
        val out = mutableListOf<Evento>()
        val bloque = Regex("""var\s+(\w+)\s*=\s*\{\s*dates\s*:\s*\[(.*?)]\s*,""", RegexOption.DOT_MATCHES_ALL)
        val rango = Regex("""start:\s*new Date\((\d+),\s*(\d+),\s*(\d+)\)\s*,\s*end:\s*new Date\((\d+),\s*(\d+),\s*(\d+)\)""")
        for (m in bloque.findAll(html)) {
            val clave = m.groupValues[1].lowercase(Locale.ROOT)
            if (clave.startsWith("descansos")) continue
            val (titulo, tipo) = etiquetas[clave] ?: etiquetas[clave.removeSuffix("_basico")]
                ?: (clave.replace('_', ' ').replaceFirstChar { it.uppercase() } to "Evento")
            val sufijo = if (clave.endsWith("_basico")) " (AFBG)" else ""
            for (r in rango.findAll(m.groupValues[2])) {
                val g = r.groupValues.drop(1).map { it.toInt() }
                fun f(a: Int, mm: Int, d: Int) = String.format(Locale.US, "%04d-%02d-%02d", a, mm + 1, d)
                out += Evento(f(g[0], g[1], g[2]), f(g[3], g[4], g[5]), titulo + sufijo, tipo)
            }
        }
        return out
    }
}

/** Convierte tablas extraídas de MiUV (lista de filas → celdas) en clases. Soporta tablas por día-columna. */
object HorarioParser {
    private val dias = listOf("lun", "mar", "mi", "jue", "vie", "s")
    private val rangoHora = Regex("""(\d{1,2}):(\d{2})\s*(?:-|–|a)\s*(\d{1,2}):(\d{2})""")

    private fun diaDe(h: String): Int? {
        val t = h.trim().lowercase(Locale.ROOT)
        if (t.length < 2) return null
        return when {
            t.startsWith("lun") -> 0; t.startsWith("mar") -> 1; t.startsWith("mi") -> 2
            t.startsWith("jue") -> 3; t.startsWith("vie") -> 4; t.startsWith("sá") || t.startsWith("sab") -> 5
            else -> null
        }
    }
    private fun hh(h: String, m: String) = String.format(Locale.US, "%02d:%s", h.toInt(), m)

    /**
     * Formato de tarjetas de MiUV:
     *   MATERIA / Académico. NOMBRE / Lunes 09:00 / Jueves 08:00 / Escuela … / Edificio: FEI / AULA TC
     * Cada "Día HH:MM" es un bloque de una hora; las horas seguidas se unen.
     */
    /** Une lecturas repetidas: una clase por (día, hora, materia), prefiriendo la que trae salón real. */
    fun limpiar(crudas: List<Clase>): List<Clase> = unir(
        crudas.groupBy { Triple(it.dia, it.inicio, it.materia.lowercase()) }.values
            .map { g -> g.firstOrNull { it.salon.isNotBlank() } ?: g.first() })

    fun parseTexto(texto: String, unirBloques: Boolean = true): List<Clase> {
        val l = texto.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }
        val slot = Regex("""\b(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|lun|mar|mi[eé]|jue|vie|s[aá]b)\.?\s*(\d{1,2})[:.](\d{2})(?:\s*(?:hrs?\.?)?\s*(?:-|–|a)\s*(\d{1,2})[:.](\d{2}))?""", RegexOption.IGNORE_CASE)
        val noMateria = Regex("""^(acad[eé]mico|edificio|aula|sal[oó]n|escuela|facultad|horario|agosto|febrero|periodo|nrc|cr[eé]ditos|secci[oó]n)""", RegexOption.IGNORE_CASE)
        fun mayus(s: String): Boolean { val letras = s.filter { it.isLetter() }; return letras.length >= 4 && letras.count { it.isUpperCase() } >= letras.length * 0.8 }

        class Bloque(val materia: String) { var prof = ""; var edificio = ""; var aula = ""; val slots = mutableListOf<MatchResult>() }
        val bloques = mutableListOf<Bloque>()
        var actual: Bloque? = null
        l.forEachIndexed { i, s ->
            val sig = l.getOrNull(i + 1).orEmpty()
            val esEncabezado = mayus(s) && slot.find(s) == null && !noMateria.containsMatchIn(s) &&
                (sig.startsWith("Acad", true) || slot.find(sig) != null)
            when {
                esEncabezado -> { actual = Bloque(s); bloques += actual!! }
                s.startsWith("Acad", true) -> actual?.prof = s.substringAfter('.').substringAfter(':').trim().toTitle()
                s.startsWith("Edificio", true) -> s.substringAfter(':', "").trim().takeIf { it.isNotBlank() }?.let { actual?.edificio = it }
                s.startsWith("Aula", true) || s.startsWith("Salón", true) || s.startsWith("Salon", true) ->
                    Regex("""^(?:aula|sal[oó]n)\s*:?\s*(.+)$""", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.trim()
                        ?.takeIf { it.isNotBlank() && !it.equals("salón", true) && !it.equals("salon", true) && !it.equals("aula", true) }
                        ?.let { actual?.aula = it }
                else -> actual?.slots?.addAll(slot.findAll(s))
            }
        }
        val out = mutableListOf<Clase>()
        for (b in bloques) {
            val salon = listOf(b.aula, b.edificio).filter { it.isNotBlank() }.joinToString(" · ")
            for (m in b.slots) {
                val d = diaDe(m.groupValues[1]) ?: continue
                val h = m.groupValues[2].toInt(); val min = m.groupValues[3]
                val fin = if (m.groupValues[4].isNotBlank()) hh(m.groupValues[4], m.groupValues[5]) else hh((h + 1).toString(), min)
                out += Clase(d, hh(h.toString(), min), fin, b.materia.toTitle(), salon, b.prof)
            }
        }
        return if (unirBloques) limpiar(out) else out
    }

    private fun unir(l: List<Clase>) = l.sortedWith(compareBy({ it.dia }, { it.inicio })).fold(mutableListOf<Clase>()) { acc, c ->
        val last = acc.lastOrNull()
        if (last != null && last.dia == c.dia && last.materia == c.materia && min(c.inicio) - min(last.fin) in 0..1) acc[acc.lastIndex] = last.copy(fin = maxOf(last.fin, c.fin)) else acc += c
        acc
    }
    private fun min(h: String) = h.substringBefore(':').toIntOrNull()?.times(60)?.plus(h.substringAfter(':').toIntOrNull() ?: 0) ?: 0

    fun parse(tablasJson: String): List<Clase> {
        val tablas = JSONArray(tablasJson)
        val out = mutableListOf<Clase>()
        for (t in 0 until tablas.length()) {
            val filas = tablas.getJSONArray(t).let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { r -> (0 until r.length()).map { r.optString(it).trim() } } } }
            val hi = filas.indexOfFirst { f -> f.count { diaDe(it) != null } >= 3 }
            if (hi < 0) continue
            val cab = filas[hi]
            val colDia = cab.mapIndexedNotNull { j, h -> diaDe(h)?.let { j to it } }.toMap()
            val colProf = cab.indexOfFirst { it.lowercase().contains("acad") || it.lowercase().contains("prof") || it.lowercase().contains("docente") }
            val colSalon = cab.indexOfFirst { it.lowercase().contains("salón") || it.lowercase().contains("salon") || it.lowercase().contains("aula") }
            for (f in filas.drop(hi + 1)) {
                val horaFila = f.firstOrNull()?.let { rangoHora.find(it) }
                val nombre = f.withIndex().firstOrNull { (j, v) -> j !in colDia && j != colProf && j != colSalon && v.length > 3 && rangoHora.find(v) == null && v.any { it.isLetter() } }?.value ?: ""
                for ((j, d) in colDia) {
                    val celda = f.getOrNull(j)?.takeIf { it.isNotBlank() } ?: continue
                    val r = rangoHora.find(celda) ?: horaFila ?: continue
                    val (h1, m1, h2, m2) = r.destructured
                    val resto = celda.replace(r.value, "").trim().trim('-', ',', '/').trim()
                    val materia = if (horaFila != null && rangoHora.find(celda) == null) resto.lines().firstOrNull().orEmpty() else nombre
                    val salon = f.getOrNull(colSalon)?.takeIf { colSalon >= 0 } ?: if (horaFila != null) resto.lines().drop(1).joinToString(" ") else resto
                    if (materia.isBlank()) continue
                    out += Clase(d, hh(h1, m1), hh(h2, m2), materia, salon, f.getOrNull(colProf)?.takeIf { colProf >= 0 } ?: "")
                }
            }
        }
        // Une bloques consecutivos de la misma materia (tablas por hora).
        return out.sortedWith(compareBy({ it.dia }, { it.inicio })).fold(mutableListOf()) { acc, c ->
            val last = acc.lastOrNull()
            if (last != null && last.dia == c.dia && last.materia == c.materia && last.fin == c.inicio) acc[acc.lastIndex] = last.copy(fin = c.fin) else acc += c
            acc
        }
    }
}
