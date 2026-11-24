package com.example.pguard.presentation.focus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.Collections

object FocusModeManager {

    private const val TAG = "FocusModeManager"
    private const val PREFS_NAME = "pguard_focus_mode_prefs"
    private const val KEY_SCHEDULES = "focus_schedules_json"

    private const val KEY_URGE_LOCKDOWN_UNTIL = "urge_lockdown_until"
    private const val KEY_URGE_LOCKDOWN_ELAPSED_END = "urge_lockdown_elapsed_end"
    private const val KEY_URGE_LOCKDOWN_BOOT_COUNT = "urge_lockdown_boot_count"
    private const val KEY_URGE_LOCKDOWN_MAX_MS = "urge_lockdown_max_ms"

    private const val KEY_QUICK_FOCUS_WALL_END = "quick_focus_wall_end"
    private const val KEY_QUICK_FOCUS_ELAPSED_END = "quick_focus_elapsed_end"
    private const val KEY_QUICK_FOCUS_BOOT_COUNT = "quick_focus_boot_count"
    private const val KEY_QUICK_FOCUS_MAX_MS = "quick_focus_max_ms"
    private const val KEY_QUICK_FOCUS_ALLOWED_APPS = "quick_focus_allowed_apps"

    private const val SYSTEM_PKG_CACHE_TTL_MS = 30_000L

    private val KNOWN_SYSTEM_LAUNCHERS = setOf(
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher",
        "com.nothing.launcher",
        "com.motorola.launcher3",
        "com.bbk.launcher2",
        "com.transsion.hilauncher",
        "com.transsion.xoslauncher"
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cachedSchedules = Collections.synchronizedList(mutableListOf<FocusSchedule>())
    @Volatile
    private var isInitialized = false

    private val _focusRevision = MutableStateFlow(0L)
    val focusRevision: StateFlow<Long> = _focusRevision.asStateFlow()

    private data class SystemPackagesSnapshot(
        val dialerPackages: Set<String>,
        val allowedFocusPackages: Set<String>,
        val inputMethodPackages: Set<String>,
        val capturedAtMs: Long
    )

    @Volatile
    private var cachedSystemPackages: SystemPackagesSnapshot? = null

    @Volatile
    private var cachedBootCount: Int? = null

    @Synchronized
    fun init(context: Context) {
        if (!isInitialized) {
            loadSchedulesFromDisk(context.applicationContext)
            isInitialized = true
        }
    }

    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            init(context)
        }
    }

    fun getSchedules(context: Context): List<FocusSchedule> {
        ensureInitialized(context)
        return synchronized(cachedSchedules) {
            cachedSchedules.toList()
        }
    }

    fun saveSchedule(context: Context, schedule: FocusSchedule): Boolean {
        if (!schedule.isValidDuration) {
            Log.w(TAG, "Ignoring zero-duration schedule")
            return false
        }
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        synchronized(cachedSchedules) {
            val existingIndex = cachedSchedules.indexOfFirst { it.id == schedule.id }
            if (existingIndex >= 0) {
                cachedSchedules[existingIndex] = schedule
            } else {
                cachedSchedules.add(schedule)
            }
        }
        persistToDisk(appContext)
        _focusRevision.value += 1
        Log.i(TAG, "Saved schedule: ${schedule.displayName} ${schedule.formattedTimeRange}")
        return true
    }

    fun toggleSchedule(context: Context, id: String, isEnabled: Boolean) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        synchronized(cachedSchedules) {
            val index = cachedSchedules.indexOfFirst { it.id == id }
            if (index >= 0) {
                val current = cachedSchedules[index]
                cachedSchedules[index] = current.copy(isEnabled = isEnabled)
            }
        }
        persistToDisk(appContext)
        _focusRevision.value += 1
    }

    fun deleteSchedule(context: Context, id: String) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        synchronized(cachedSchedules) {
            cachedSchedules.removeAll { it.id == id }
        }
        persistToDisk(appContext)
        _focusRevision.value += 1
        Log.i(TAG, "Deleted schedule: $id")
    }

    fun getActiveSchedules(context: Context, cal: Calendar = Calendar.getInstance()): List<FocusSchedule> {
        return FocusSchedule.activeSchedules(getSchedules(context), cal)
    }

    fun getActiveSchedule(context: Context, cal: Calendar = Calendar.getInstance()): FocusSchedule? {
        return getActiveSchedules(context, cal).minByOrNull { it.allowedApps.size }
    }

    fun isFocusModeActiveNow(context: Context, cal: Calendar = Calendar.getInstance()): Boolean {
        return isQuickFocusActive(context) ||
            getActiveSchedules(context, cal).isNotEmpty() ||
            isUrgeLockdownActive(context)
    }

    internal fun computeMonotonicRemainingMs(
        elapsedEndMs: Long,
        nowElapsedMs: Long,
        wallEndMs: Long,
        nowWallMs: Long,
        savedBootCount: Int,
        currentBootCount: Int,
        maxDurationMs: Long
    ): Long {
        if (savedBootCount >= 0 && currentBootCount == savedBootCount && elapsedEndMs > 0L) {
            return (elapsedEndMs - nowElapsedMs).coerceAtLeast(0L)
        }
        if (wallEndMs <= 0L) return 0L
        return (wallEndMs - nowWallMs).coerceIn(0L, maxDurationMs.coerceAtLeast(0L))
    }

    private fun getBootCount(context: Context): Int {
        cachedBootCount?.let { return it }
        val count = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }
        if (count >= 0) {
            cachedBootCount = count
        }
        return count
    }

    private fun readMonotonicRemainingSeconds(
        context: Context,
        wallKey: String,
        elapsedKey: String,
        bootKey: String,
        maxMsKey: String,
        defaultMaxMs: Long
    ): Int {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val elapsedEndMs = prefs.getLong(elapsedKey, 0L)
        val wallEndMs = prefs.getLong(wallKey, 0L)
        if (elapsedEndMs <= 0L && wallEndMs <= 0L) return 0

        val remainingMs = computeMonotonicRemainingMs(
            elapsedEndMs = elapsedEndMs,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            wallEndMs = wallEndMs,
            nowWallMs = System.currentTimeMillis(),
            savedBootCount = prefs.getInt(bootKey, -1),
            currentBootCount = getBootCount(appContext),
            maxDurationMs = prefs.getLong(maxMsKey, defaultMaxMs)
        )
        return if (remainingMs > 0L) ((remainingMs + 999L) / 1000L).toInt() else 0
    }

    fun startUrgeLockdown(context: Context, durationSeconds: Int = 120) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val clampedSecs = durationSeconds.coerceAtLeast(1)
        val durationMs = clampedSecs * 1000L
        val wallEnd = System.currentTimeMillis() + durationMs
        val elapsedEnd = SystemClock.elapsedRealtime() + durationMs
        val bootCount = getBootCount(appContext)

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_URGE_LOCKDOWN_UNTIL, wallEnd)
            .putLong(KEY_URGE_LOCKDOWN_ELAPSED_END, elapsedEnd)
            .putInt(KEY_URGE_LOCKDOWN_BOOT_COUNT, bootCount)
            .putLong(KEY_URGE_LOCKDOWN_MAX_MS, durationMs)
            .apply()
        _focusRevision.value += 1
        Log.i(TAG, "Started urge lockdown for ${clampedSecs}s")
    }

    fun stopUrgeLockdown(context: Context) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URGE_LOCKDOWN_UNTIL)
            .remove(KEY_URGE_LOCKDOWN_ELAPSED_END)
            .remove(KEY_URGE_LOCKDOWN_BOOT_COUNT)
            .remove(KEY_URGE_LOCKDOWN_MAX_MS)
            .apply()
        _focusRevision.value += 1
        Log.i(TAG, "Stopped urge lockdown")
    }

    fun getUrgeLockdownRemainingSeconds(context: Context): Int {
        return readMonotonicRemainingSeconds(
            context = context,
            wallKey = KEY_URGE_LOCKDOWN_UNTIL,
            elapsedKey = KEY_URGE_LOCKDOWN_ELAPSED_END,
            bootKey = KEY_URGE_LOCKDOWN_BOOT_COUNT,
            maxMsKey = KEY_URGE_LOCKDOWN_MAX_MS,
            defaultMaxMs = 120_000L
        )
    }

    fun isUrgeLockdownActive(context: Context): Boolean {
        return getUrgeLockdownRemainingSeconds(context) > 0
    }

    fun startQuickFocus(context: Context, durationMinutes: Int, allowedApps: Set<String> = emptySet()) {
        if (durationMinutes <= 0) return
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val durationMs = durationMinutes * 60_000L
        val wallEnd = System.currentTimeMillis() + durationMs
        val elapsedEnd = SystemClock.elapsedRealtime() + durationMs
        val bootCount = getBootCount(appContext)

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_QUICK_FOCUS_WALL_END, wallEnd)
            .putLong(KEY_QUICK_FOCUS_ELAPSED_END, elapsedEnd)
            .putInt(KEY_QUICK_FOCUS_BOOT_COUNT, bootCount)
            .putLong(KEY_QUICK_FOCUS_MAX_MS, durationMs)
            .putStringSet(KEY_QUICK_FOCUS_ALLOWED_APPS, allowedApps.toSet())
            .apply()
        _focusRevision.value += 1
        Log.i(TAG, "Started Quick Focus for ${durationMinutes}m (allowed=${allowedApps.size})")
    }

    fun stopQuickFocus(context: Context) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_QUICK_FOCUS_WALL_END)
            .remove(KEY_QUICK_FOCUS_ELAPSED_END)
            .remove(KEY_QUICK_FOCUS_BOOT_COUNT)
            .remove(KEY_QUICK_FOCUS_MAX_MS)
            .remove(KEY_QUICK_FOCUS_ALLOWED_APPS)
            .apply()
        _focusRevision.value += 1
        Log.i(TAG, "Stopped Quick Focus session")
    }

    fun getQuickFocusRemainingSeconds(context: Context): Int {
        return readMonotonicRemainingSeconds(
            context = context,
            wallKey = KEY_QUICK_FOCUS_WALL_END,
            elapsedKey = KEY_QUICK_FOCUS_ELAPSED_END,
            bootKey = KEY_QUICK_FOCUS_BOOT_COUNT,
            maxMsKey = KEY_QUICK_FOCUS_MAX_MS,
            defaultMaxMs = 24L * 60L * 60_000L
        )
    }

    fun isQuickFocusActive(context: Context): Boolean {
        return getQuickFocusRemainingSeconds(context) > 0
    }

    fun getQuickFocusAllowedApps(context: Context): Set<String> {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_QUICK_FOCUS_ALLOWED_APPS, emptySet())?.toSet() ?: emptySet()
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

    private fun isEssentialFocusPackage(context: Context, packageName: String): Boolean {
        val lower = packageName.lowercase()
        val appPkg = context.packageName.lowercase()
        if (lower == appPkg) return true

        if (lower.startsWith("com.android.systemui") ||
            lower in KNOWN_SYSTEM_LAUNCHERS ||
            lower.contains("trebuchet") ||
            lower.contains("quickstep")
        ) {
            return true
        }

        val snapshot = getSystemPackages(context)
        if (lower in snapshot.allowedFocusPackages) {
            return true
        }

        return lower.contains("inputmethod") || lower.contains("gboard") || lower.contains("latinime")
    }

    fun isPackageAllowed(context: Context, packageName: String, schedule: FocusSchedule): Boolean {
        if (isEssentialFocusPackage(context, packageName)) return true
        return schedule.allowedApps.any { it.equals(packageName, ignoreCase = true) }
    }

    private fun hasAnyEnabledSchedules(context: Context): Boolean {
        ensureInitialized(context)
        return synchronized(cachedSchedules) {
            cachedSchedules.any { it.isEnabled }
        }
    }

    /**
     * Evaluates Quick Focus and all overlapping active Focus Schedules together.
     * Returns null when allowed, or a concise description of the blocking session.
     */
    fun getBlockingFocusReason(
        context: Context,
        packageName: String,
        cal: Calendar? = null
    ): String? {
        val quickRemaining = getQuickFocusRemainingSeconds(context)
        val hasEnabledSchedules = hasAnyEnabledSchedules(context)
        if (quickRemaining <= 0 && !hasEnabledSchedules) return null

        if (isEssentialFocusPackage(context, packageName)) return null

        if (quickRemaining > 0) {
            val quickAllowed = getQuickFocusAllowedApps(context)
            if (quickAllowed.none { it.equals(packageName, ignoreCase = true) }) {
                val minsLeft = (quickRemaining + 59) / 60
                val label = if (minsLeft <= 1 && quickRemaining < 60) "<1 min left" else "$minsLeft min left"
                return "Quick Focus ($label)"
            }
        }

        if (hasEnabledSchedules) {
            val active = getActiveSchedules(context, cal ?: Calendar.getInstance())
            if (active.isNotEmpty() &&
                !FocusSchedule.isAllowedAcrossSchedules(packageName, active, isEssentialSystemPackage = false)
            ) {
                val blocking = active.first { schedule ->
                    schedule.allowedApps.none { it.equals(packageName, ignoreCase = true) }
                }
                return "${blocking.displayName} (${blocking.formattedWindowOnly})"
            }
        }

        return null
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
