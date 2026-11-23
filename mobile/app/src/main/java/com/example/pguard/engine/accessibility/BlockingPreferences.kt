package com.example.pguard.engine.accessibility

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized thread-safe storage for blocking preferences, blocklists/whitelists,
 * sync dirty state, and tamper-resistant time-delay timers.
 */
object BlockingPreferences {
    private const val TAG = "BlockingPreferences"
    const val PREFS_NAME = "pguard_accessibility_prefs"

    const val KEY_ADULT = "block_adult_content"
    const val KEY_SHORTS = "block_shorts"
    const val KEY_WHATSAPP = "block_whatsapp_channels"
    const val KEY_INSTAGRAM_REELS = "block_instagram_reels"
    const val KEY_TIKTOK = "block_tiktok"
    const val KEY_UNINSTALL = "uninstall_protection"
    const val KEY_UNSUPPORTED_BROWSERS = "block_unsupported_browsers"
    const val KEY_NEW_INSTALLED_APPS = "block_newly_installed_apps"
    const val KEY_NEW_APPS_ENABLED_AT = "new_apps_enabled_at"
    const val KEY_BLOCKED_PACKAGES = "blocked_app_packages"
    const val KEY_APPROVED_NEW_APP_PACKAGES = "approved_new_app_packages"
    const val KEY_CUSTOM_BLOCKLIST = "custom_blocklist"
    const val KEY_PENDING_DELETED_APPS = "pending_deleted_blocklist_apps"
    const val KEY_PENDING_DELETED_KEYWORDS = "pending_deleted_blocklist_keywords"
    const val KEY_WHITELISTED_PACKAGES = "whitelisted_app_packages"
    const val KEY_WHITELISTED_KEYWORDS = "whitelisted_keywords"
    const val KEY_MESSAGE = "blocked_screen_message"
    const val KEY_COUNTDOWN = "blocked_screen_countdown"
    const val KEY_REDIRECT_URL = "redirect_url"
    const val KEY_ACCOUNTABILITY_MODE = "accountability_mode"
    const val KEY_TIME_DELAY_UNLOCK_AT = "time_delay_unlock_at"
    const val KEY_TIME_DELAY_TARGET = "time_delay_target"
    const val KEY_TIME_DELAY_STARTED_ELAPSED = "time_delay_started_elapsed_ms"
    const val KEY_TIME_DELAY_DURATION = "time_delay_duration_ms"
    const val KEY_TIME_DELAY_BOOT_COUNT = "time_delay_boot_count"
    const val KEY_LOCAL_DIRTY = "local_settings_dirty"
    const val KEY_DNS_VPN_ENABLED = "dns_vpn_enabled"
    const val KEY_PARTNER_LINKED = "accountability_partner_linked"
    const val KEY_PARTNER_EMAIL = "accountability_partner_email"
    const val KEY_PARTNER_NAME = "accountability_partner_name"
    const val KEY_TIME_DELAY_SERVER_BACKED = "time_delay_server_backed"
    const val KEY_TIME_DELAY_LAST_WALL_MS = "time_delay_last_wall_ms"

    const val DEFAULT_TIME_DELAY_MS = 24L * 60L * 60L * 1000L

    @Volatile var isAdultBlockingEnabled: Boolean = true
    @Volatile var isShortsBlockingEnabled: Boolean = false
    @Volatile var isWhatsAppChannelsBlockingEnabled: Boolean = false
    @Volatile var isInstagramReelsBlockingEnabled: Boolean = false
    @Volatile var isTikTokBlockingEnabled: Boolean = false
    @Volatile var isUninstallProtectionEnabled: Boolean = false
    @Volatile var isUnsupportedBrowsersBlockingEnabled: Boolean = false
    @Volatile var isNewlyInstalledAppsBlockingEnabled: Boolean = false
    @Volatile var isDnsVpnEnabled: Boolean = false
    @Volatile var newAppsEnabledAt: Long = 0L
    @Volatile var blockedScreenMessage: String = "This page is blocked."
    @Volatile var blockedScreenCountdown: Int = 3
    @Volatile var redirectUrl: String = ""
    @Volatile var accountabilityMode: String = "none"
    @Volatile var timeDelayUnlockAt: Long = 0L
    @Volatile var timeDelayTarget: String = ""
    @Volatile var isTimeDelayServerBacked: Boolean = false
    @Volatile var isPartnerLinkedCached: Boolean = false
    @Volatile var cachedPartnerEmail: String = ""
    @Volatile var cachedPartnerName: String = ""
    @Volatile var isLocalDirty: Boolean = false

    val customBlocklist: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val blockedAppPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val approvedNewAppPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val pendingDeletedBlocklistApps: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val pendingDeletedBlocklistKeywords: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val whitelistedAppPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val whitelistedKeywords: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _blocklistRevision = MutableStateFlow(0L)
    val blocklistRevision: StateFlow<Long> = _blocklistRevision.asStateFlow()

    @Volatile
    var appContext: Context? = null

    fun ensureContext(context: Context?) {
        if (context != null) {
            appContext = context.applicationContext
        }
    }

    @Synchronized
    fun loadSettings(context: Context) {
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isAdultBlockingEnabled = prefs.getBoolean(KEY_ADULT, true)
        isShortsBlockingEnabled = prefs.getBoolean(KEY_SHORTS, false)
        isWhatsAppChannelsBlockingEnabled = prefs.getBoolean(KEY_WHATSAPP, false)
        isInstagramReelsBlockingEnabled = prefs.getBoolean(KEY_INSTAGRAM_REELS, false)
        isTikTokBlockingEnabled = prefs.getBoolean(KEY_TIKTOK, false)
        isUninstallProtectionEnabled = prefs.getBoolean(KEY_UNINSTALL, false)
        isUnsupportedBrowsersBlockingEnabled = prefs.getBoolean(KEY_UNSUPPORTED_BROWSERS, false)
        isNewlyInstalledAppsBlockingEnabled = prefs.getBoolean(KEY_NEW_INSTALLED_APPS, false)
        isDnsVpnEnabled = prefs.getBoolean(KEY_DNS_VPN_ENABLED, false)
        newAppsEnabledAt = prefs.getLong(KEY_NEW_APPS_ENABLED_AT, 0L)
        blockedScreenMessage = prefs.getString(KEY_MESSAGE, "This page is blocked.") ?: "This page is blocked."
        blockedScreenCountdown = prefs.getInt(KEY_COUNTDOWN, 3)
        redirectUrl = prefs.getString(KEY_REDIRECT_URL, "") ?: ""
        accountabilityMode = prefs.getString(KEY_ACCOUNTABILITY_MODE, "none") ?: "none"
        timeDelayUnlockAt = prefs.getLong(KEY_TIME_DELAY_UNLOCK_AT, 0L)
        timeDelayTarget = prefs.getString(KEY_TIME_DELAY_TARGET, "") ?: ""
        isTimeDelayServerBacked = prefs.getBoolean(KEY_TIME_DELAY_SERVER_BACKED, false)
        isPartnerLinkedCached = prefs.getBoolean(KEY_PARTNER_LINKED, false)
        cachedPartnerEmail = prefs.getString(KEY_PARTNER_EMAIL, "") ?: ""
        cachedPartnerName = prefs.getString(KEY_PARTNER_NAME, "") ?: ""
        isLocalDirty = prefs.getBoolean(KEY_LOCAL_DIRTY, false)

        val savedBlocked = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        blockedAppPackages.clear()
        blockedAppPackages.addAll(savedBlocked)

        val savedApprovedNewApps = prefs.getStringSet(KEY_APPROVED_NEW_APP_PACKAGES, emptySet()) ?: emptySet()
        approvedNewAppPackages.clear()
        approvedNewAppPackages.addAll(savedApprovedNewApps)

        val savedCustomBlocklist = prefs.getStringSet(KEY_CUSTOM_BLOCKLIST, emptySet()) ?: emptySet()
        customBlocklist.clear()
        customBlocklist.addAll(savedCustomBlocklist)

        val savedPendingDeletedApps = prefs.getStringSet(KEY_PENDING_DELETED_APPS, emptySet()) ?: emptySet()
        pendingDeletedBlocklistApps.clear()
        pendingDeletedBlocklistApps.addAll(savedPendingDeletedApps)

        val savedPendingDeletedKeywords = prefs.getStringSet(KEY_PENDING_DELETED_KEYWORDS, emptySet()) ?: emptySet()
        pendingDeletedBlocklistKeywords.clear()
        pendingDeletedBlocklistKeywords.addAll(savedPendingDeletedKeywords)

        val savedWhitelistedApps = prefs.getStringSet(KEY_WHITELISTED_PACKAGES, emptySet()) ?: emptySet()
        whitelistedAppPackages.clear()
        whitelistedAppPackages.addAll(savedWhitelistedApps)

        val savedWhitelistedKeywords = prefs.getStringSet(KEY_WHITELISTED_KEYWORDS, emptySet()) ?: emptySet()
        whitelistedKeywords.clear()
        whitelistedKeywords.addAll(savedWhitelistedKeywords)

        Log.d(
            TAG,
            "Loaded settings: adult=$isAdultBlockingEnabled, shorts=$isShortsBlockingEnabled, " +
                "whatsapp=$isWhatsAppChannelsBlockingEnabled, reels=$isInstagramReelsBlockingEnabled, " +
                "tiktok=$isTikTokBlockingEnabled, unsupportedBrowsers=$isUnsupportedBrowsersBlockingEnabled, " +
                "newApps=$isNewlyInstalledAppsBlockingEnabled, mode=$accountabilityMode"
        )
    }

    @Synchronized
    fun saveSettings(
        context: Context? = appContext,
        adult: Boolean = isAdultBlockingEnabled,
        shorts: Boolean = isShortsBlockingEnabled,
        whatsapp: Boolean = isWhatsAppChannelsBlockingEnabled,
        instagramReels: Boolean = isInstagramReelsBlockingEnabled,
        tiktok: Boolean = isTikTokBlockingEnabled,
        uninstall: Boolean = isUninstallProtectionEnabled,
        unsupportedBrowsers: Boolean = isUnsupportedBrowsersBlockingEnabled,
        newInstalledApps: Boolean = isNewlyInstalledAppsBlockingEnabled,
        message: String = blockedScreenMessage,
        countdown: Int = blockedScreenCountdown,
        redirect: String = redirectUrl,
        markDirty: Boolean = true
    ) {
        ensureContext(context)
        val targetContext = context?.applicationContext ?: appContext

        isAdultBlockingEnabled = adult
        isShortsBlockingEnabled = shorts
        isWhatsAppChannelsBlockingEnabled = whatsapp
        isInstagramReelsBlockingEnabled = instagramReels
        isTikTokBlockingEnabled = tiktok
        isUninstallProtectionEnabled = uninstall
        isUnsupportedBrowsersBlockingEnabled = unsupportedBrowsers
        isNewlyInstalledAppsBlockingEnabled = newInstalledApps
        if (newInstalledApps && newAppsEnabledAt == 0L) {
            newAppsEnabledAt = System.currentTimeMillis()
        } else if (!newInstalledApps) {
            newAppsEnabledAt = 0L
        }
        blockedScreenMessage = message
        blockedScreenCountdown = countdown
        redirectUrl = redirect
        if (markDirty) {
            isLocalDirty = true
        }

        targetContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_ADULT, adult)
            ?.putBoolean(KEY_SHORTS, shorts)
            ?.putBoolean(KEY_WHATSAPP, whatsapp)
            ?.putBoolean(KEY_INSTAGRAM_REELS, instagramReels)
            ?.putBoolean(KEY_TIKTOK, tiktok)
            ?.putBoolean(KEY_UNINSTALL, uninstall)
            ?.putBoolean(KEY_UNSUPPORTED_BROWSERS, unsupportedBrowsers)
            ?.putBoolean(KEY_NEW_INSTALLED_APPS, newInstalledApps)
            ?.putLong(KEY_NEW_APPS_ENABLED_AT, newAppsEnabledAt)
            ?.putString(KEY_MESSAGE, message)
            ?.putInt(KEY_COUNTDOWN, countdown)
            ?.putString(KEY_REDIRECT_URL, redirect)
            ?.apply {
                if (markDirty) putBoolean(KEY_LOCAL_DIRTY, true)
            }
            ?.apply()
    }

    fun markLocalDirty(context: Context? = appContext, dirty: Boolean) {
        ensureContext(context)
        isLocalDirty = dirty
        val targetContext = context?.applicationContext ?: appContext
        targetContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_LOCAL_DIRTY, dirty)
            ?.apply()
    }

    fun saveDnsVpnEnabled(context: Context? = appContext, enabled: Boolean) {
        ensureContext(context)
        isDnsVpnEnabled = enabled
        val targetContext = context?.applicationContext ?: appContext
        targetContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_DNS_VPN_ENABLED, enabled)
            ?.apply()
        Log.i(TAG, "Saved DNS VPN Shield enabled=$enabled")
    }

    fun addBlockedApp(context: Context, packageName: String, label: String = packageName) {
        blockedAppPackages.add(packageName)
        revokeNewAppApproval(context, packageName)
        clearPendingDeletedApp(context, packageName)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(packageName)
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, set).apply()
        _blocklistRevision.value += 1
        Log.i(TAG, "Added blocked app package: $packageName ($label)")
    }

    fun removeBlockedApp(context: Context, packageName: String, recordTombstone: Boolean = true) {
        blockedAppPackages.remove(packageName)
        approveNewApp(context, packageName)
        if (recordTombstone) {
            markPendingDeletedApp(context, packageName)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(packageName)
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, set).apply()
        _blocklistRevision.value += 1
        Log.i(TAG, "Removed blocked app package: $packageName")
    }

    fun markPendingDeletedApp(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        pendingDeletedBlocklistApps.add(packageName)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_PENDING_DELETED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (set.add(packageName)) {
            prefs.edit().putStringSet(KEY_PENDING_DELETED_APPS, set).apply()
        }
    }

    fun clearPendingDeletedApp(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        pendingDeletedBlocklistApps.remove(packageName)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_PENDING_DELETED_APPS, emptySet())
        if (existing.isNullOrEmpty() || !existing.contains(packageName)) return
        val updated = existing.toMutableSet().apply { remove(packageName) }
        prefs.edit().putStringSet(KEY_PENDING_DELETED_APPS, updated).apply()
    }

    fun markPendingDeletedKeyword(context: Context, keyword: String) {
        val normalized = BlocklistRuleValidator.normalizeWebsiteOrKeyword(keyword).first
        if (normalized.isBlank()) return
        pendingDeletedBlocklistKeywords.add(normalized)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_PENDING_DELETED_KEYWORDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (set.add(normalized)) {
            prefs.edit().putStringSet(KEY_PENDING_DELETED_KEYWORDS, set).apply()
        }
    }

    fun clearPendingDeletedKeyword(context: Context, keyword: String) {
        val normalized = BlocklistRuleValidator.normalizeWebsiteOrKeyword(keyword).first
        if (normalized.isBlank()) return
        pendingDeletedBlocklistKeywords.remove(normalized)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_PENDING_DELETED_KEYWORDS, emptySet())
        if (existing.isNullOrEmpty() || !existing.contains(normalized)) return
        val updated = existing.toMutableSet().apply { remove(normalized) }
        prefs.edit().putStringSet(KEY_PENDING_DELETED_KEYWORDS, updated).apply()
    }

    fun clearPendingBlocklistDeletes(context: Context) {
        pendingDeletedBlocklistApps.clear()
        pendingDeletedBlocklistKeywords.clear()
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_DELETED_APPS)
            .remove(KEY_PENDING_DELETED_KEYWORDS)
            .apply()
    }

    fun approveNewApp(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        approvedNewAppPackages.add(packageName)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_APPROVED_NEW_APP_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (set.add(packageName)) {
            prefs.edit().putStringSet(KEY_APPROVED_NEW_APP_PACKAGES, set).apply()
            Log.i(TAG, "Approved new app package: $packageName")
        }
    }

    fun revokeNewAppApproval(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        approvedNewAppPackages.remove(packageName)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_APPROVED_NEW_APP_PACKAGES, emptySet())
        if (existing.isNullOrEmpty() || !existing.contains(packageName)) return
        val updated = existing.toMutableSet().apply { remove(packageName) }
        prefs.edit().putStringSet(KEY_APPROVED_NEW_APP_PACKAGES, updated).apply()
        Log.i(TAG, "Revoked new app approval: $packageName")
    }

    fun isNewAppApproved(packageName: String): Boolean = approvedNewAppPackages.contains(packageName)

    fun getBlockedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        blockedAppPackages.clear()
        blockedAppPackages.addAll(set)
        return set
    }

    fun addCustomBlockedKeyword(context: Context, keyword: String) {
        val normalized = BlocklistRuleValidator.normalizeWebsiteOrKeyword(keyword).first
        if (normalized.isBlank()) return
        customBlocklist.add(normalized)
        clearPendingDeletedKeyword(context, normalized)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_CUSTOM_BLOCKLIST, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(normalized)
        prefs.edit().putStringSet(KEY_CUSTOM_BLOCKLIST, set).apply()
        _blocklistRevision.value += 1
        Log.i(TAG, "Added custom blocked keyword: $normalized")
    }

    fun removeCustomBlockedKeyword(context: Context, keyword: String, recordTombstone: Boolean = true) {
        val rawLower = keyword.trim().lowercase()
        val normalized = BlocklistRuleValidator.normalizeWebsiteOrKeyword(keyword).first
        customBlocklist.remove(rawLower)
        customBlocklist.remove(normalized)
        if (recordTombstone && normalized.isNotBlank()) {
            markPendingDeletedKeyword(context, normalized)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_CUSTOM_BLOCKLIST, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(rawLower)
        set.remove(normalized)
        prefs.edit().putStringSet(KEY_CUSTOM_BLOCKLIST, set).apply()
        _blocklistRevision.value += 1
        Log.i(TAG, "Removed custom blocked keyword: $normalized")
    }

    fun getCustomBlockedKeywords(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_CUSTOM_BLOCKLIST, emptySet()) ?: emptySet()
        customBlocklist.clear()
        customBlocklist.addAll(set)
        return set
    }

    fun addWhitelistedApp(context: Context, packageName: String) {
        whitelistedAppPackages.add(packageName)
        approveNewApp(context, packageName)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_WHITELISTED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(packageName)
        prefs.edit().putStringSet(KEY_WHITELISTED_PACKAGES, set).apply()
        _blocklistRevision.value += 1
        Log.i(TAG, "Added whitelisted app: $packageName")
    }

    fun removeWhitelistedApp(context: Context, packageName: String) {
        whitelistedAppPackages.remove(packageName)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_WHITELISTED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(packageName)
        prefs.edit().putStringSet(KEY_WHITELISTED_PACKAGES, set).apply()
        _blocklistRevision.value += 1
        Log.i(TAG, "Removed whitelisted app: $packageName")
    }

    fun getWhitelistedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_WHITELISTED_PACKAGES, emptySet()) ?: emptySet()
        whitelistedAppPackages.clear()
        whitelistedAppPackages.addAll(set)
        return set
    }

    fun addWhitelistedKeyword(context: Context, keyword: String) {
        val normalized = BlocklistRuleValidator.normalizeWebsiteOrKeyword(keyword).first
        if (normalized.isBlank()) return
        whitelistedKeywords.add(normalized)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_WHITELISTED_KEYWORDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(normalized)
        prefs.edit().putStringSet(KEY_WHITELISTED_KEYWORDS, set).apply()
        _blocklistRevision.value += 1
        Log.i(TAG, "Added whitelisted keyword: $normalized")
    }

    fun removeWhitelistedKeyword(context: Context, keyword: String) {
        val rawLower = keyword.trim().lowercase()
        val normalized = BlocklistRuleValidator.normalizeWebsiteOrKeyword(keyword).first
        whitelistedKeywords.remove(rawLower)
        whitelistedKeywords.remove(normalized)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_WHITELISTED_KEYWORDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(rawLower)
        set.remove(normalized)
        prefs.edit().putStringSet(KEY_WHITELISTED_KEYWORDS, set).apply()
        _blocklistRevision.value += 1
        Log.i(TAG, "Removed whitelisted keyword: $normalized")
    }

    fun getWhitelistedKeywords(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_WHITELISTED_KEYWORDS, emptySet()) ?: emptySet()
        whitelistedKeywords.clear()
        whitelistedKeywords.addAll(set)
        return set
    }

    fun saveAccountabilityMode(context: Context, mode: String) {
        accountabilityMode = mode
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNTABILITY_MODE, mode)
            .apply()
    }

    fun savePartnerCache(context: Context, isLinked: Boolean, email: String = "", name: String = "") {
        ensureContext(context)
        isPartnerLinkedCached = isLinked
        cachedPartnerEmail = email
        cachedPartnerName = name
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PARTNER_LINKED, isLinked)
            .putString(KEY_PARTNER_EMAIL, email)
            .putString(KEY_PARTNER_NAME, name)
            .apply()
    }

    fun clearPartnerCache(context: Context) {
        savePartnerCache(context, isLinked = false, email = "", name = "")
    }

    private fun getBootCount(context: Context): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }
    }

    fun setTimeDelay(
        context: Context,
        unlockAtWallMs: Long,
        target: String,
        durationMs: Long = (unlockAtWallMs - System.currentTimeMillis()).coerceAtLeast(0L),
        serverBacked: Boolean = false
    ) {
        timeDelayUnlockAt = unlockAtWallMs
        timeDelayTarget = target
        isTimeDelayServerBacked = serverBacked
        val nowWall = System.currentTimeMillis()
        val startedElapsed = SystemClock.elapsedRealtime()
        val bootCount = getBootCount(context)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TIME_DELAY_UNLOCK_AT, unlockAtWallMs)
            .putString(KEY_TIME_DELAY_TARGET, target)
            .putLong(KEY_TIME_DELAY_STARTED_ELAPSED, startedElapsed)
            .putLong(KEY_TIME_DELAY_DURATION, durationMs)
            .putInt(KEY_TIME_DELAY_BOOT_COUNT, bootCount)
            .putBoolean(KEY_TIME_DELAY_SERVER_BACKED, serverBacked)
            .putLong(KEY_TIME_DELAY_LAST_WALL_MS, nowWall)
            .apply()
    }

    fun clearTimeDelay(context: Context) {
        timeDelayUnlockAt = 0L
        timeDelayTarget = ""
        isTimeDelayServerBacked = false
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TIME_DELAY_UNLOCK_AT)
            .remove(KEY_TIME_DELAY_TARGET)
            .remove(KEY_TIME_DELAY_STARTED_ELAPSED)
            .remove(KEY_TIME_DELAY_DURATION)
            .remove(KEY_TIME_DELAY_BOOT_COUNT)
            .remove(KEY_TIME_DELAY_SERVER_BACKED)
            .remove(KEY_TIME_DELAY_LAST_WALL_MS)
            .apply()
    }

    fun getTimeDelayRemainingMs(context: Context, serverConfirmedReady: Boolean = false): Long {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unlockAt = prefs.getLong(KEY_TIME_DELAY_UNLOCK_AT, timeDelayUnlockAt)
        val target = prefs.getString(KEY_TIME_DELAY_TARGET, timeDelayTarget) ?: timeDelayTarget
        if (unlockAt <= 0L || target.isBlank()) return 0L

        val nowWall = System.currentTimeMillis()
        val wallRemaining = (unlockAt - nowWall).coerceAtLeast(0L)
        val startedElapsed = prefs.getLong(KEY_TIME_DELAY_STARTED_ELAPSED, 0L)
        val durationMs = prefs.getLong(KEY_TIME_DELAY_DURATION, 0L)
        val savedBootCount = prefs.getInt(KEY_TIME_DELAY_BOOT_COUNT, -1)
        val currentBootCount = getBootCount(context)
        val serverBacked = prefs.getBoolean(KEY_TIME_DELAY_SERVER_BACKED, isTimeDelayServerBacked)
        val lastWall = prefs.getLong(KEY_TIME_DELAY_LAST_WALL_MS, 0L)

        if (savedBootCount >= 0 && currentBootCount == savedBootCount && startedElapsed > 0L && durationMs > 0L) {
            val elapsed = (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
            val monotonicRemaining = (durationMs - elapsed).coerceAtLeast(0L)
            if (nowWall > lastWall) {
                prefs.edit().putLong(KEY_TIME_DELAY_LAST_WALL_MS, nowWall).apply()
            }
            return maxOf(wallRemaining, monotonicRemaining)
        }

        // After reboot: server-backed timers require server UTC confirmation before unlocking
        if (serverBacked && !serverConfirmedReady) {
            return maxOf(wallRemaining, 1_000L)
        }

        if (!serverConfirmedReady && lastWall > 0L && nowWall < lastWall) {
            return maxOf(wallRemaining, durationMs)
        }

        if (nowWall > lastWall) {
            prefs.edit().putLong(KEY_TIME_DELAY_LAST_WALL_MS, nowWall).apply()
        }
        return wallRemaining
    }

    fun isTimeDelayElapsed(
        context: Context,
        target: String,
        serverConfirmedReady: Boolean = false
    ): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTarget = prefs.getString(KEY_TIME_DELAY_TARGET, timeDelayTarget) ?: timeDelayTarget
        val unlockAt = prefs.getLong(KEY_TIME_DELAY_UNLOCK_AT, timeDelayUnlockAt)
        return savedTarget == target && unlockAt > 0L && getTimeDelayRemainingMs(context, serverConfirmedReady) <= 0L
    }
}
