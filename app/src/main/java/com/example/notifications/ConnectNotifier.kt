package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Cuando el usuario autoriza en el navegador, Helix completa la conexión en segundo plano. Android no deja
 * que una app en segundo plano se abra sola, así que se intenta traerla al frente y, si el sistema lo
 * impide, se avisa con una notificación que vuelve a Helix de un toque.
 */
object ConnectNotifier {
    private const val CHANNEL = "connectors"
    private const val ID = 4201

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun mainIntent(context: Context) =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    fun bringToFront(context: Context) {
        runCatching { context.startActivity(mainIntent(context)) } // el sistema puede bloquearlo en segundo plano
    }

    fun notifyConnected(context: Context, service: String, login: String) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Conectores", NotificationManager.IMPORTANCE_HIGH))
        }
        val tap = PendingIntent.getActivity(context, 0, mainIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_helix)
            .setContentTitle("$service conectado")
            .setContentText("Sesión iniciada como @$login. Toca para volver a Helix.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun cancel(context: Context) {
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID) }
    }
}
