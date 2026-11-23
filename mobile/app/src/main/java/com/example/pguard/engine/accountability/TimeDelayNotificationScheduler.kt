package com.example.pguard.engine.accountability

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Schedules a non-exact local notification broadcast when a 24-hour cooling-off unlock completes.
 */
object TimeDelayNotificationScheduler {
    private const val TAG = "TimeDelayNotifScheduler"
    private const val REQUEST_CODE = 4208

    const val EXTRA_TARGET_TITLE = "extra_target_title"

    fun schedule(context: Context, unlockAtWallMs: Long, targetTitle: String) {
        if (unlockAtWallMs <= System.currentTimeMillis()) return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(appContext, targetTitle)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unlockAtWallMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, unlockAtWallMs, pendingIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule time-delay ready notification: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, TimeDelayReadyReceiver::class.java)
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val existing = PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags) ?: return
        try {
            alarmManager.cancel(existing)
            existing.cancel()
        } catch (_: Exception) {}
    }

    private fun buildPendingIntent(context: Context, targetTitle: String): PendingIntent {
        val intent = Intent(context, TimeDelayReadyReceiver::class.java).apply {
            putExtra(EXTRA_TARGET_TITLE, targetTitle)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
