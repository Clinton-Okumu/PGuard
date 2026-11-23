package com.example.pguard.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

internal fun formatAppVersion(versionName: String?, versionCode: Long?): String {
    val cleanName = versionName?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0"
    val cleanCode = versionCode ?: 1L
    return "$cleanName (Build $cleanCode)"
}

internal fun buildSupportEmailBody(
    appVersion: String,
    androidRelease: String,
    sdkInt: Int,
    isSignedIn: Boolean,
    accessibilityRunning: Boolean,
    vpnRunning: Boolean,
    deviceAdminActive: Boolean,
    batteryUnrestricted: Boolean,
    notificationsEnabled: Boolean,
    accountabilityModeTitle: String
): String {
    val accountStatus = if (isSignedIn) "Signed In" else "Guest Mode"
    fun flagLabel(enabled: Boolean) = if (enabled) "Active" else "Off"

    return buildString {
        appendLine("Describe the issue you're experiencing:")
        appendLine()
        appendLine()
        appendLine("--- Diagnostics ---")
        appendLine("App Version: $appVersion")
        appendLine("Android: $androidRelease (SDK $sdkInt)")
        appendLine("Account Status: $accountStatus")
        appendLine("Accountability Mode: $accountabilityModeTitle")
        appendLine("Accessibility Service: ${flagLabel(accessibilityRunning)}")
        appendLine("DNS VPN Shield: ${flagLabel(vpnRunning)}")
        appendLine("Device Admin: ${flagLabel(deviceAdminActive)}")
        appendLine("Battery Optimization Ignored: ${if (batteryUnrestricted) "Yes" else "No"}")
        append("Notifications Enabled: ${if (notificationsEnabled) "Yes" else "No"}")
    }
}

internal fun isBatteryOptimizationIgnored(context: Context): Boolean {
    return try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        true
    }
}

internal fun areAppNotificationsEnabled(context: Context): Boolean {
    return try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (_: Exception) {
        true
    }
}

internal fun launchAppUninstall(context: Context): Boolean {
    val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(deleteIntent)
        true
    } catch (_: Exception) {
        try {
            val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(detailsIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

internal fun getAppVersionSummary(context: Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
        formatAppVersion(pInfo.versionName, versionCode)
    } catch (_: Exception) {
        formatAppVersion(null, null)
    }
}
