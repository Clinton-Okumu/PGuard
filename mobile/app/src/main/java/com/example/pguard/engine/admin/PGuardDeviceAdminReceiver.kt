package com.example.pguard.engine.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accountability.SecurityAlertReporter

class PGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "PGuard Device Admin enabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Uninstall Protection is active. Disabling this device admin requires passing your accountability check inside PGuard."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "PGuard Device Admin disabled")
        BlockingPreferences.loadSettings(context)
        if (BlockingPreferences.isUninstallProtectionEnabled) {
            SecurityAlertReporter.reportIfPartnerLinked(
                context = context,
                eventType = "permission_revoked",
                message = "PGuard Device Administrator was deactivated while Uninstall Protection was active."
            )
        }
    }

    companion object {
        private const val TAG = "PGuardDeviceAdmin"
    }
}
