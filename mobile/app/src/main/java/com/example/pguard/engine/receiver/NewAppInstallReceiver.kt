package com.example.pguard.engine.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.BrowserInspector
import com.example.pguard.engine.accessibility.NewAppInstallGuard
import com.example.pguard.engine.accessibility.PGuardAccessibilityService

class NewAppInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REMOVED) return

        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (isReplacing) {
            // App update / replacement, not a fresh install or full uninstall
            return
        }

        val data = intent.data ?: return
        val packageName = data.schemeSpecificPart ?: return
        if (packageName.isBlank() || packageName == context.packageName) return

        // Invalidate stale package/browser caches on any real install or uninstall
        BrowserInspector.invalidateBrowserCache()
        PGuardAccessibilityService.invalidateInstallTimeCache(packageName)
        BlockingPreferences.revokeNewAppApproval(context, packageName)

        if (action == Intent.ACTION_PACKAGE_REMOVED) {
            Log.i(TAG, "App removed on device: $packageName")
            return
        }

        Log.i(TAG, "New app installed on device: $packageName")

        // Reload accessibility settings to inspect current protection & whitelist state
        PGuardAccessibilityService.loadSettings(context)
        if (!NewAppInstallGuard.shouldAutoBlockInstalledApp(context, packageName)) {
            Log.d(TAG, "Skipping auto-block for $packageName (disabled, system/IME/non-launchable, or whitelisted)")
            return
        }

        val appLabel = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

        Log.w(TAG, "Auto-blocking newly installed app: $appLabel ($packageName)")

        // 1. Add to local accessibility service blocked apps set and persist
        PGuardAccessibilityService.addBlockedApp(context, packageName, appLabel)

        // 2. Notify user via Toast on main thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                "New app '$appLabel' was automatically added to your Blocklist.",
                Toast.LENGTH_LONG
            ).show()
        }

        // 3. Sync to backend blocklist API asynchronously when signed in and network is available
        val pendingResult = goAsync()
        NewAppInstallGuard.syncBlockedAppToBackendAsync(context, packageName, appLabel) {
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "NewAppInstallReceiver"
    }
}
