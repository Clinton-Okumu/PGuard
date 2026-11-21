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
    private const val SYSTEM_PKG_CACHE_TTL_MS = 30_000L

    const val MAX_FREE_SCHEDULES = Int.MAX_VALUE

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cachedSchedules = Collections.synchronizedList(mutableListOf<FocusSchedule>())
    @Volatile
    private var isInitialized = false
    @Volatile
    private var cachedUrgeLockdownUntil: Long = -1L

    private data class SystemPackagesSnapshot(
        val dialerPackages: Set<String>,
        val allowedFocusPackages: Set<String>,
        val inputMethodPackages: Set<String>,
        val capturedAtMs: Long
    )

    @Volatile
    private var cachedSystemPackages: SystemPackagesSnapshot? = null

    fun init(context: Context) {
        if (!isInitialized) {
            loadSchedulesFromDisk(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cachedUrgeLockdownUntil = prefs.getLong(KEY_URGE_LOCKDOWN_UNTIL, 0L)
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

    private fun getSystemPackages(context: Context): SystemPackagesSnapshot {
        val now = System.currentTimeMillis()
        val existing = cachedSystemPackages
        if (existing != null && (now - existing.capturedAtMs) < SYSTEM_PKG_CACHE_TTL_MS) {
            return existing
        }

        val dialerPkgs = mutableSetOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.server.telecom",
            "com.android.phone"
        )
        val focusPkgs = mutableSetOf(
            "android",
            "com.android.systemui",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.google.android.contacts",
            "com.android.contacts",
            "com.samsung.android.app.contacts"
        )
        val imePkgs = mutableSetOf<String>()

        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val homeResolve = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            homeResolve?.activityInfo?.packageName?.lowercase()?.let { focusPkgs.add(it) }
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                telecomManager?.defaultDialerPackage?.lowercase()?.let {
                    dialerPkgs.add(it)
                    focusPkgs.add(it)
                }
            }
        } catch (_: Exception) {}

        try {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val dialResolve = context.packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
            dialResolve?.activityInfo?.packageName?.lowercase()?.let {
                dialerPkgs.add(it)
                focusPkgs.add(it)
            }
        } catch (_: Exception) {}

        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.lowercase()?.let { focusPkgs.add(it) }
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            val smsResolve = context.packageManager.resolveActivity(smsIntent, PackageManager.MATCH_DEFAULT_ONLY)
            smsResolve?.activityInfo?.packageName?.lowercase()?.let { focusPkgs.add(it) }
        } catch (_: Exception) {}

        try {
            val contactsIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            val contactsResolve = context.packageManager.resolveActivity(contactsIntent, PackageManager.MATCH_DEFAULT_ONLY)
            contactsResolve?.activityInfo?.packageName?.lowercase()?.let { focusPkgs.add(it) }
        } catch (_: Exception) {}

        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.forEach {
                imePkgs.add(it.packageName.lowercase())
            }
        } catch (_: Exception) {}

        focusPkgs.addAll(dialerPkgs)
        focusPkgs.addAll(imePkgs)

        val snapshot = SystemPackagesSnapshot(
            dialerPackages = dialerPkgs,
            allowedFocusPackages = focusPkgs,
            inputMethodPackages = imePkgs,
            capturedAtMs = now
        )
        cachedSystemPackages = snapshot
        return snapshot
    }

    /**
     * Determines whether a package is permitted during the 2-minute emergency urge lockdown.
     * To enforce full phone lockdown from use, only PGuard (the urge breathing protocol),
     * emergency calls, and input methods are permitted. All launchers and other apps are locked.
     */
    fun isPackageAllowedDuringUrgeLockdown(context: Context, packageName: String): Boolean {
        val lower = packageName.lowercase()
        val appPkg = context.packageName.lowercase()

        if (lower == appPkg) return true

        val snapshot = getSystemPackages(context)
        if (lower in snapshot.dialerPackages) return true
        if (lower in snapshot.inputMethodPackages) return true

        return false
    }

    fun startUrgeLockdown(context: Context, durationSeconds: Int = 120) {
        val until = System.currentTimeMillis() + (durationSeconds * 1000L)
        cachedUrgeLockdownUntil = until
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_URGE_LOCKDOWN_UNTIL, until).apply()
        Log.i(TAG, "Started 2-min urge lockdown until $until")
    }

    fun stopUrgeLockdown(context: Context) {
        cachedUrgeLockdownUntil = 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_URGE_LOCKDOWN_UNTIL).apply()
        Log.i(TAG, "Stopped urge lockdown")
    }

    private fun readUrgeLockdownUntil(context: Context): Long {
        val cached = cachedUrgeLockdownUntil
        if (cached >= 0L) return cached
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = prefs.getLong(KEY_URGE_LOCKDOWN_UNTIL, 0L)
        cachedUrgeLockdownUntil = loaded
        return loaded
    }

    fun isUrgeLockdownActive(context: Context): Boolean {
        return System.currentTimeMillis() < readUrgeLockdownUntil(context)
    }

    fun getUrgeLockdownRemainingSeconds(context: Context): Int {
        val until = readUrgeLockdownUntil(context)
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

        if (lower == appPkg) return true

        if (schedule.allowedApps.any { it.equals(lower, ignoreCase = true) }) {
            return true
        }

        if (lower.startsWith("com.android.systemui") ||
            lower.contains("launcher") ||
            lower.contains("trebuchet") ||
            lower.contains("quickstep")
        ) {
            return true
        }

        val snapshot = getSystemPackages(context)
        if (lower in snapshot.allowedFocusPackages) {
            return true
        }

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
