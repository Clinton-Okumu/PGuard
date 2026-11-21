package com.example.pguard.presentation.focus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import android.view.inputmethod.InputMethodManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Collections

object FocusModeManager {

    private const val TAG = "FocusModeManager"
    private const val PREFS_NAME = "pguard_focus_mode_prefs"
    private const val KEY_SCHEDULES = "focus_schedules_json"
    private const val KEY_URGE_LOCKDOWN_UNTIL = "urge_lockdown_until"

    const val MAX_FREE_SCHEDULES = Int.MAX_VALUE

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cachedSchedules = Collections.synchronizedList(mutableListOf<FocusSchedule>())
    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            loadSchedulesFromDisk(context)
            isInitialized = true
        }
    }

    fun getSchedules(context: Context): List<FocusSchedule> {
        if (!isInitialized) {
            init(context)
        }
        return synchronized(cachedSchedules) {
            cachedSchedules.toList()
        }
    }

    fun saveSchedule(context: Context, schedule: FocusSchedule): Boolean {
        synchronized(cachedSchedules) {
            val existingIndex = cachedSchedules.indexOfFirst { it.id == schedule.id }
            if (existingIndex >= 0) {
                cachedSchedules[existingIndex] = schedule
            } else {
                cachedSchedules.add(schedule)
            }
        }
        persistToDisk(context)
        Log.i(TAG, "Saved schedule: ${schedule.formattedTimeRange}")
        return true
    }

    fun toggleSchedule(context: Context, id: String, isEnabled: Boolean) {
        synchronized(cachedSchedules) {
            val index = cachedSchedules.indexOfFirst { it.id == id }
            if (index >= 0) {
                val current = cachedSchedules[index]
                cachedSchedules[index] = current.copy(isEnabled = isEnabled)
            }
        }
        persistToDisk(context)
    }

    fun deleteSchedule(context: Context, id: String) {
        synchronized(cachedSchedules) {
            cachedSchedules.removeAll { it.id == id }
        }
        persistToDisk(context)
        Log.i(TAG, "Deleted schedule: $id")
    }

    fun getActiveSchedule(context: Context, cal: Calendar = Calendar.getInstance()): FocusSchedule? {
        val schedules = getSchedules(context)
        return schedules.firstOrNull { it.isCurrentlyActive(cal) }
    }

    fun isFocusModeActiveNow(context: Context, cal: Calendar = Calendar.getInstance()): Boolean {
        return getActiveSchedule(context, cal) != null || isUrgeLockdownActive(context)
    }

    /**
     * Determines whether a package is permitted during the 2-minute emergency urge lockdown.
     * To enforce full phone lockdown from use, only PGuard (the urge breathing protocol)
     * and emergency calls are permitted. All launchers and other apps are locked.
     */
    fun isPackageAllowedDuringUrgeLockdown(context: Context, packageName: String): Boolean {
        val lower = packageName.lowercase()
        val appPkg = context.packageName.lowercase()

        // 1. PGuard itself is always permitted
        if (lower == appPkg) return true

        // 2. Emergency phone dialer / in-call UI is permitted for critical safety
        val defaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            telecomManager?.defaultDialerPackage
        } else null

        if (!defaultDialer.isNullOrBlank() && lower == defaultDialer.lowercase()) {
            return true
        }
        if (lower.contains("dialer") || lower.contains("incallui") || lower.contains("telecom")) {
            return true
        }

        // 3. Keyboards / Input methods
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val imes = imm?.enabledInputMethodList?.map { it.packageName.lowercase() } ?: emptyList()
            if (imes.contains(lower)) return true
        } catch (_: Exception) {}

        // Everything else (Launchers, browsers, social media, games, etc.) is strictly locked down!
        return false
    }

    fun startUrgeLockdown(context: Context, durationSeconds: Int = 120) {
        val until = System.currentTimeMillis() + (durationSeconds * 1000L)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_URGE_LOCKDOWN_UNTIL, until).apply()
        Log.i(TAG, "Started 2-min urge lockdown until $until")
    }

    fun stopUrgeLockdown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_URGE_LOCKDOWN_UNTIL).apply()
        Log.i(TAG, "Stopped urge lockdown")
    }

    fun isUrgeLockdownActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_URGE_LOCKDOWN_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun getUrgeLockdownRemainingSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_URGE_LOCKDOWN_UNTIL, 0L)
        val remainingMs = until - System.currentTimeMillis()
        return if (remainingMs > 0) ((remainingMs + 999L) / 1000L).toInt() else 0
    }

    /**
     * Determines whether a package is permitted during an active focus schedule:
     * - PGuard itself is always permitted
     * - Standard Defaults: Messages, Calls (Dialer), Contacts are permitted
     * - Home Launcher and System UI are permitted
     * - Input Methods (keyboards) are permitted
     * - Explicit user-selected allowedApps in the schedule are permitted
     * - All other applications are strictly blocked
     */
    fun isPackageAllowed(context: Context, packageName: String, schedule: FocusSchedule): Boolean {
        val lower = packageName.lowercase()
        val appPkg = context.packageName.lowercase()

        // 1. PGuard itself
        if (lower == appPkg) return true

        // 2. Explicitly allowed apps selected during schedule creation
        if (schedule.allowedApps.any { it.equals(lower, ignoreCase = true) }) {
            return true
        }

        // 3. System UI & Home Launcher
        if (lower == "android" ||
            lower.startsWith("com.android.systemui") ||
            lower.contains("launcher") ||
            lower.contains("nexuslauncher") ||
            lower.contains("trebuchet") ||
            lower.contains("quickstep")
        ) {
            return true
        }
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val homeResolve = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val homePkg = homeResolve?.activityInfo?.packageName?.lowercase()
            if (!homePkg.isNullOrBlank() && homePkg == lower) {
                return true
            }
        } catch (_: Exception) {}

        // 4. Calls / Phone Dialer (Default & Emergency Calls)
        val defaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            telecomManager?.defaultDialerPackage
        } else null

        if (!defaultDialer.isNullOrBlank() && lower == defaultDialer.lowercase()) {
            return true
        }
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val dialResolve = context.packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val dialPkg = dialResolve?.activityInfo?.packageName?.lowercase()
            if (!dialPkg.isNullOrBlank() && dialPkg == lower) {
                return true
            }
        } catch (_: Exception) {}
        if (lower.contains("dialer") || lower.contains("incallui") || lower.contains("phone") || lower.contains("telecom")) {
            return true
        }

        // 5. Messages / SMS
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(context)
        if (!defaultSms.isNullOrBlank() && lower == defaultSms.lowercase()) {
            return true
        }
        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            val smsResolve = context.packageManager.resolveActivity(smsIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val smsPkg = smsResolve?.activityInfo?.packageName?.lowercase()
            if (!smsPkg.isNullOrBlank() && smsPkg == lower) {
                return true
            }
        } catch (_: Exception) {}
        if (lower.contains("messaging") || lower.contains("mms") || lower == "com.google.android.apps.messaging") {
            return true
        }

        // 6. Contacts (Calls, Messages, Contacts are default allowed)
        try {
            val contactsIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            val contactsResolve = context.packageManager.resolveActivity(contactsIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val contactsPkg = contactsResolve?.activityInfo?.packageName?.lowercase()
            if (!contactsPkg.isNullOrBlank() && contactsPkg == lower) {
                return true
            }
        } catch (_: Exception) {}
        if (lower == "com.google.android.contacts" ||
            lower == "com.android.contacts" ||
            lower == "com.samsung.android.app.contacts" ||
            lower.contains("contacts")
        ) {
            return true
        }

        // 7. Input Method Keyboards (Gboard, Swiftkey, Samsung Keyboard, etc.)
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val imes = imm?.enabledInputMethodList?.map { it.packageName.lowercase() } ?: emptyList()
            if (imes.contains(lower)) return true
        } catch (_: Exception) {}
        if (lower.contains("inputmethod") || lower.contains("gboard") || lower.contains("latinime")) {
            return true
        }

        return false
    }

    private fun loadSchedulesFromDisk(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_SCHEDULES, null)
            if (!jsonStr.isNullOrBlank()) {
                val list = json.decodeFromString<List<FocusSchedule>>(jsonStr)
                synchronized(cachedSchedules) {
                    cachedSchedules.clear()
                    cachedSchedules.addAll(list)
                }
                Log.d(TAG, "Loaded ${list.size} focus schedules from disk.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load focus schedules: ${e.message}")
        }
    }

    private fun persistToDisk(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val list = synchronized(cachedSchedules) { cachedSchedules.toList() }
            val jsonStr = json.encodeToString(list)
            prefs.edit().putString(KEY_SCHEDULES, jsonStr).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist focus schedules: ${e.message}")
        }
    }
}
