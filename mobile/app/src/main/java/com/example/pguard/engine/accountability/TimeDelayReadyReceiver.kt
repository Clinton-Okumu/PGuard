package com.example.pguard.engine.accountability

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pguard.MainActivity
import com.example.pguard.R

class TimeDelayReadyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val targetTitle = intent.getStringExtra(TimeDelayNotificationScheduler.EXTRA_TARGET_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Protection Feature"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Accountability & Unlock Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies you when a 24-hour cooling-off unlock timer completes."
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pguard_globe_shield)
            .setContentTitle("24-Hour Unlock Ready")
            .setContentText("Cooling-off period completed for $targetTitle. Open PGuard to confirm or cancel.")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "pguard_accountability_alerts"
        private const val NOTIFICATION_ID = 4209
    }
}
