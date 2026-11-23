package com.example.pguard.engine.accessibility

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.CreateBlocklistRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Evaluates whether a newly installed package should be automatically blocked until reviewed.
 *
 * Pure predicate helpers are Android-free so they can be verified directly on the JVM.
 */
object NewAppInstallGuard {
    private const val TAG = "NewAppInstallGuard"

    private val CORE_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.vending",
        "com.android.settings",
        "com.google.android.settings",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.providers.downloads",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    fun isCoreOrSystemPackageName(pkg: String, selfPackageName: String): Boolean {
        val normalized = pkg.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return true
        if (selfPackageName.isNotBlank() && normalized == selfPackageName.lowercase(Locale.ROOT)) {
            return true
        }
        if (normalized in CORE_SYSTEM_PACKAGES) return true

        return normalized.contains("launcher") ||
            normalized.contains("trebuchet") ||
            normalized.contains("quickstep") ||
            normalized.contains("packageinstaller") ||
            normalized.contains("permissioncontroller") ||
            normalized.contains("inputmethod")
    }

    fun shouldAutoBlockNewApp(
        packageName: String,
        selfPackageName: String,
        isFeatureEnabled: Boolean,
        isSystemApp: Boolean,
        hasLaunchIntent: Boolean,
        isInputMethod: Boolean,
        isWhitelisted: Boolean,
        isApprovedNewApp: Boolean,
        isAlreadyBlocked: Boolean = false
    ): Boolean {
        if (!isFeatureEnabled) return false
        if (packageName.isBlank()) return false
        if (isCoreOrSystemPackageName(packageName, selfPackageName)) return false
        if (isSystemApp || !hasLaunchIntent || isInputMethod) return false
        if (isWhitelisted || isApprovedNewApp || isAlreadyBlocked) return false
        return true
    }

    fun shouldAutoBlockInstalledApp(context: Context, packageName: String): Boolean {
        if (!BlockingPreferences.isNewlyInstalledAppsBlockingEnabled) return false
        val appContext = context.applicationContext
        val pm = appContext.packageManager

        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: Exception) {
            return false
        }

        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (isSystemApp) return false

        val hasLaunchIntent = pm.getLaunchIntentForPackage(packageName) != null ||
            pm.getLeanbackLaunchIntentForPackage(packageName) != null
        val isInputMethod = isRegisteredInputMethod(appContext, packageName)

        return shouldAutoBlockNewApp(
            packageName = packageName,
            selfPackageName = appContext.packageName,
            isFeatureEnabled = BlockingPreferences.isNewlyInstalledAppsBlockingEnabled,
            isSystemApp = isSystemApp,
            hasLaunchIntent = hasLaunchIntent,
            isInputMethod = isInputMethod,
            isWhitelisted = BlockingPreferences.whitelistedAppPackages.contains(packageName),
            isApprovedNewApp = BlockingPreferences.isNewAppApproved(packageName),
            isAlreadyBlocked = BlockingPreferences.blockedAppPackages.contains(packageName)
        )
    }

    private fun isRegisteredInputMethod(context: Context, packageName: String): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return false
            imm.inputMethodList.any { it.packageName == packageName }
        } catch (_: Exception) {
            false
        }
    }

    fun syncBlockedAppToBackendAsync(
        context: Context,
        packageName: String,
        appLabel: String,
        onFinished: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = AuthDataStore(appContext).accessTokenFlow.firstOrNull()
                if (token.isNullOrBlank()) {
                    Log.d(TAG, "Skipping backend blocklist sync for $packageName (not signed in)")
                    return@launch
                }
                withTimeoutOrNull(8_000L) {
                    val api = NetworkClient.getBlocklistApiService(appContext)
                    api.createItem(
                        CreateBlocklistRequest(
                            type = "app",
                            value = packageName,
                            label = appLabel,
                            isEnabled = true
                        )
                    )
                    Log.i(TAG, "Synced newly blocked app $packageName to backend blocklist")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync $packageName to backend blocklist: ${e.message}")
            } finally {
                onFinished?.invoke()
            }
        }
    }
}
