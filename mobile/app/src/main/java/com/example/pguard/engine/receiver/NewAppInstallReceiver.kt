package com.example.pguard.engine.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.CreateBlocklistRequest
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class NewAppInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return

        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (isReplacing) {
            // App update, not a newly installed app
            return
        }

        val data = intent.data ?: return
        val packageName = data.schemeSpecificPart ?: return
        if (packageName.isBlank() || packageName == context.packageName) return

        Log.i(TAG, "New app installed on device: $packageName")

        // Reload accessibility settings to check if "block newly installed apps" is enabled
        PGuardAccessibilityService.loadSettings(context)
        if (!PGuardAccessibilityService.isNewlyInstalledAppsBlockingEnabled) {
            Log.d(TAG, "Block newly installed apps is OFF; skipping $packageName")
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

        // 3. Sync to backend blocklist API asynchronously if network/auth is available
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    val api = NetworkClient.getBlocklistApiService(context)
                    api.createItem(
                        CreateBlocklistRequest(
                            type = "app",
                            value = packageName,
                            label = appLabel,
                            isEnabled = true
                        )
                    )
                    Log.i(TAG, "Successfully synced $packageName to backend blocklist")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync $packageName to backend blocklist (offline or unauthorized): ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NewAppInstallReceiver"
    }
}
