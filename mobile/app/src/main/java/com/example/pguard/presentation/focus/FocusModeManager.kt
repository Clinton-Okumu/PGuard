package com.example.pguard.presentation.focus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Collections

object FocusModeManager {

    private const val TAG = "FocusModeManager"
    private const val PREFS_NAME = "pguard_focus_mode_prefs"
    private const val KEY_SCHEDULES = "focus_schedules_json"

    const val MAX_FREE_SCHEDULES = 1

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
        return getActiveSchedule(context, cal) != null
    }

    /**
     * Determines whether a package is permitted during an active focus schedule:
     * - PGuard itself is always permitted (to adjust settings, disable lock if authorized)
     * - System dialer & SMS apps are permitted by default for emergencies
     * - Home Launcher and System UI are permitted
     * - Any package explicitly in schedule.allowedApps is permitted
     */
    fun isPackageAllowed(context: Context, packageName: String, schedule: FocusSchedule): Boolean {
        val lower = packageName.lowercase()
        val appPkg = context.packageName.lowercase()

        // 1. PGuard itself
        if (lower == appPkg) return true

        // 2. Explicitly allowed apps
        if (schedule.allowedApps.any { it.equals(lower, ignoreCase = true) }) {
            return true
        }

        // 3. System UI & Launcher
        if (lower == "android" ||
            lower.startsWith("com.android.systemui") ||
            lower.contains("launcher") ||
            lower.contains("nexuslauncher") ||
            lower.contains("trebuchet") ||
            lower.contains("quickstep")
        ) {
            return true
        }

        // 4. Default Telephony & Phone Dialer (Emergency Calls)
        val defaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            telecomManager?.defaultDialerPackage
        } else null

        if (!defaultDialer.isNullOrBlank() && lower == defaultDialer.lowercase()) {
            return true
        }

        if (lower.contains("dialer") || lower.contains("incallui") || lower.contains("phone")) {
            return true
        }

        // 5. Default SMS Provider
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(context)
        if (!defaultSms.isNullOrBlank() && lower == defaultSms.lowercase()) {
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
