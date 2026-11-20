package mx.uv.miuv2.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object Notificar {
    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel("entregas", "Entregas de tareas", NotificationManager.IMPORTANCE_DEFAULT))
        it.createNotificationChannel(NotificationChannel("cierre", "Tareas por cerrar", NotificationManager.IMPORTANCE_HIGH))
        it.createNotificationChannel(NotificationChannel("nuevas", "Tareas nuevas", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun abrir(c: Context) = android.app.PendingIntent.getActivity(c, 0,
        android.content.Intent(c, mx.uv.miuv2.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
        android.app.PendingIntent.FLAG_IMMUTABLE)
    private fun show(c: Context, t: String, m: String, canal: String = "entregas", icono: Int = android.R.drawable.stat_sys_upload_done, id: Int = t.hashCode()) = runCatching {
        nm(c).notify(id, NotificationCompat.Builder(c, canal).setSmallIcon(icono).setContentIntent(abrir(c))
            .setContentTitle(t).setContentText(m).setStyle(NotificationCompat.BigTextStyle().bigText(m)).setAutoCancel(true).build())
    }
    fun ok(c: Context, tarea: String) = show(c, "Tarea entregada", "\"$tarea\" se entregó en Eminus.")
    fun cierre(c: Context, t: Tarea, faltaMs: Long) {
        val h = faltaMs / 3_600_000L; val m = (faltaMs / 60_000L) % 60
        val cuanto = if (h >= 1) "$h h" + (if (m > 0 && h < 3) " $m min" else "") else "$m min"
        show(c, "Cierra en $cuanto: ${t.titulo}", "${t.curso}\nAún no la entregas. Toca para abrir MIUV2.", "cierre",
            android.R.drawable.ic_lock_idle_alarm, ("c" + t.cursoId + t.id).hashCode())
    }
    fun nueva(c: Context, t: Tarea) = show(c, "Nueva tarea: ${t.titulo}", t.curso, "nuevas",
        android.R.drawable.ic_menu_agenda, ("n" + t.cursoId + t.id).hashCode())
    fun error(c: Context, tarea: String, msg: String) = show(c, "No se pudo entregar \"$tarea\"", msg)
}
