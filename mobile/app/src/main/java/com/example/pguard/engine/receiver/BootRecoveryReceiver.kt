package com.example.pguard.engine.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accountability.TimeDelayNotificationScheduler
import com.example.pguard.engine.vpn.PGuardVpnService
import com.example.pguard.presentation.dashboard.targetDisplayName
import com.example.pguard.presentation.focus.FocusModeManager

/**
 * Restores PGuard's persisted settings, Focus schedules, Time Delay notifications,
 * and local DNS Shield VPN after device reboot or app update.
 */
class BootRecoveryReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootRecoveryReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val appContext = context?.applicationContext ?: return
        try {
            BlockingPreferences.loadSettings(appContext)
            FocusModeManager.init(appContext)

            if (BlockingPreferences.isDnsVpnEnabled &&
                !PGuardVpnService.isRunning.value &&
                VpnService.prepare(appContext) == null
            ) {
                try {
                    PGuardVpnService.start(appContext)
                    Log.i(TAG, "Recovered DNS Shield VPN after $action")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start DNS VPN on $action: ${e.message}")
                }
            }

            val unlockAt = BlockingPreferences.timeDelayUnlockAt
            val target = BlockingPreferences.timeDelayTarget
            if (unlockAt > System.currentTimeMillis() && target.isNotBlank()) {
                TimeDelayNotificationScheduler.schedule(
                    context = appContext,
                    unlockAtWallMs = unlockAt,
                    targetTitle = targetDisplayName(target)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Boot recovery skipped safely: ${e.message}")
        }
    }
}
