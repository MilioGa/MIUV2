package mx.uv.miuv2.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object Notificar {
    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java).also {
        it.createNotificationChannel(NotificationChannel("entregas", "Entregas de tareas", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun show(c: Context, t: String, m: String) = runCatching {
        nm(c).notify(t.hashCode(), NotificationCompat.Builder(c, "entregas").setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(t).setContentText(m).setStyle(NotificationCompat.BigTextStyle().bigText(m)).setAutoCancel(true).build())
    }
    fun ok(c: Context, tarea: String) = show(c, "Tarea entregada ✓", "\"$tarea\" se entregó en Eminus.")
    fun error(c: Context, tarea: String, msg: String) = show(c, "No se pudo entregar \"$tarea\"", msg)
}
