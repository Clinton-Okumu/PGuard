package com.example.pguard.engine.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object DeviceAdminManager {
    private const val TAG = "DeviceAdminManager"

    fun getComponentName(context: Context): ComponentName {
        return ComponentName(context.applicationContext.packageName, PGuardDeviceAdminReceiver::class.java.name)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            dpm.isAdminActive(getComponentName(context))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device admin status", e)
            false
        }
    }

    fun requestEnableDeviceAdmin(context: Context) {
        try {
            val component = getComponentName(context)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Activate Device Administrator to prevent PGuard from being uninstalled without passing your accountability check."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch enable device admin intent, falling back to security settings", e)
            try {
                val fallbackIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }

    fun deactivateDeviceAdmin(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
            val component = getComponentName(context)
            if (dpm.isAdminActive(component)) {
                dpm.removeActiveAdmin(component)
                Log.i(TAG, "Device Admin removed successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing device admin", e)
        }
    }
}
