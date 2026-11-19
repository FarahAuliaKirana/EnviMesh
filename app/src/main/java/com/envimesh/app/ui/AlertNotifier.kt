package com.envimesh.app.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.envimesh.app.MainActivity
import com.envimesh.app.R
import com.envimesh.app.data.IncidentAlert

/** Shows a phone notification for a new alert from the server. */
object AlertNotifier {

    private const val CHANNEL_ID = "envimesh_alerts"

    fun show(context: Context, alert: IncidentAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "EnviMesh alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Water level, sensor and fire alerts from EnviMesh nodes"
                }
            )
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = when (alert.level) {
            "danger" -> "Danger: ${alert.nodeId}"
            "possible_fire" -> "Possible fire: ${alert.nodeId}"
            "sensor_error" -> "Sensor error: ${alert.nodeId}"
            else -> "Warning: ${alert.nodeId}"
        }
        val text = alert.message ?: "Water table ${alert.waterLevelCm} cm below surface"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify((alert.remoteId ?: alert.hashCode().toLong()).toInt(), notification)
        } catch (e: SecurityException) {
            // Permission was revoked while the app was running; nothing to show.
        }
    }
}
