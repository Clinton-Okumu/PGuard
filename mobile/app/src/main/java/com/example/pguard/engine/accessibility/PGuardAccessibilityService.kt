package com.example.pguard.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.pguard.engine.filter.ContentFilter
import com.example.pguard.presentation.focus.FocusModeManager
import com.example.pguard.presentation.habits.HabitsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class PGuardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PGuardAccessibility"
        private const val BLOCK_COOLDOWN_MS = 1500L
        private const val SHORTS_COOLDOWN_MS = 1200L
        private const val WHATSAPP_COOLDOWN_MS = 1200L
        private const val INSTAGRAM_COOLDOWN_MS = 1200L
        private const val TIKTOK_COOLDOWN_MS = 1500L
        private const val DEEP_SCAN_THROTTLE_MS = 250L
        private const val MAX_INSTALL_TIME_CACHE_SIZE = 512

        const val PREFS_NAME = BlockingPreferences.PREFS_NAME
        const val KEY_ADULT = BlockingPreferences.KEY_ADULT
        const val KEY_SHORTS = BlockingPreferences.KEY_SHORTS
        const val KEY_WHATSAPP = BlockingPreferences.KEY_WHATSAPP
        const val KEY_INSTAGRAM_REELS = BlockingPreferences.KEY_INSTAGRAM_REELS
        const val KEY_TIKTOK = BlockingPreferences.KEY_TIKTOK
        const val KEY_UNINSTALL = BlockingPreferences.KEY_UNINSTALL
        const val KEY_UNSUPPORTED_BROWSERS = BlockingPreferences.KEY_UNSUPPORTED_BROWSERS
        const val KEY_NEW_INSTALLED_APPS = BlockingPreferences.KEY_NEW_INSTALLED_APPS
        const val KEY_NEW_APPS_ENABLED_AT = BlockingPreferences.KEY_NEW_APPS_ENABLED_AT
        const val KEY_BLOCKED_PACKAGES = BlockingPreferences.KEY_BLOCKED_PACKAGES
        const val KEY_CUSTOM_BLOCKLIST = BlockingPreferences.KEY_CUSTOM_BLOCKLIST
        const val KEY_WHITELISTED_PACKAGES = BlockingPreferences.KEY_WHITELISTED_PACKAGES
        const val KEY_WHITELISTED_KEYWORDS = BlockingPreferences.KEY_WHITELISTED_KEYWORDS
        const val KEY_MESSAGE = BlockingPreferences.KEY_MESSAGE
        const val KEY_COUNTDOWN = BlockingPreferences.KEY_COUNTDOWN
        const val KEY_REDIRECT_URL = BlockingPreferences.KEY_REDIRECT_URL
        const val KEY_ACCOUNTABILITY_MODE = BlockingPreferences.KEY_ACCOUNTABILITY_MODE
        const val KEY_TIME_DELAY_UNLOCK_AT = BlockingPreferences.KEY_TIME_DELAY_UNLOCK_AT
        const val KEY_TIME_DELAY_TARGET = BlockingPreferences.KEY_TIME_DELAY_TARGET

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        val isAdultBlockingEnabled: Boolean get() = BlockingPreferences.isAdultBlockingEnabled
        val isShortsBlockingEnabled: Boolean get() = BlockingPreferences.isShortsBlockingEnabled
        val isWhatsAppChannelsBlockingEnabled: Boolean get() = BlockingPreferences.isWhatsAppChannelsBlockingEnabled
        val isInstagramReelsBlockingEnabled: Boolean get() = BlockingPreferences.isInstagramReelsBlockingEnabled
        val isTikTokBlockingEnabled: Boolean get() = BlockingPreferences.isTikTokBlockingEnabled
        val isUninstallProtectionEnabled: Boolean get() = BlockingPreferences.isUninstallProtectionEnabled
        val isUnsupportedBrowsersBlockingEnabled: Boolean get() = BlockingPreferences.isUnsupportedBrowsersBlockingEnabled
        val isNewlyInstalledAppsBlockingEnabled: Boolean get() = BlockingPreferences.isNewlyInstalledAppsBlockingEnabled
        val newAppsEnabledAt: Long get() = BlockingPreferences.newAppsEnabledAt
        val blockedScreenMessage: String get() = BlockingPreferences.blockedScreenMessage
        val blockedScreenCountdown: Int get() = BlockingPreferences.blockedScreenCountdown
        val redirectUrl: String get() = BlockingPreferences.redirectUrl
        val accountabilityMode: String get() = BlockingPreferences.accountabilityMode
        val timeDelayUnlockAt: Long get() = BlockingPreferences.timeDelayUnlockAt
        val timeDelayTarget: String get() = BlockingPreferences.timeDelayTarget

        val customBlocklist: MutableSet<String> get() = BlockingPreferences.customBlocklist
        val blockedAppPackages: MutableSet<String> get() = BlockingPreferences.blockedAppPackages
        val whitelistedAppPackages: MutableSet<String> get() = BlockingPreferences.whitelistedAppPackages
        val whitelistedKeywords: MutableSet<String> get() = BlockingPreferences.whitelistedKeywords

        var appContext: Context?
            get() = BlockingPreferences.appContext
            set(value) { BlockingPreferences.appContext = value }

        @Volatile
        var blockSuppressionUntil: Long = 0L

        fun suppressBlockingFor(durationMs: Long = 3000L) {
            blockSuppressionUntil = System.currentTimeMillis() + durationMs
        }

        fun loadSettings(context: Context) = BlockingPreferences.loadSettings(context)
        fun addBlockedApp(context: Context, packageName: String, label: String = packageName) =
            BlockingPreferences.addBlockedApp(context, packageName, label)
        fun removeBlockedApp(context: Context, packageName: String) =
            BlockingPreferences.removeBlockedApp(context, packageName)
        fun getBlockedApps(context: Context): Set<String> =
            BlockingPreferences.getBlockedApps(context)
        fun addCustomBlockedKeyword(context: Context, keyword: String) =
            BlockingPreferences.addCustomBlockedKeyword(context, keyword)
        fun removeCustomBlockedKeyword(context: Context, keyword: String) =
            BlockingPreferences.removeCustomBlockedKeyword(context, keyword)
        fun getCustomBlockedKeywords(context: Context): Set<String> =
            BlockingPreferences.getCustomBlockedKeywords(context)
        fun addWhitelistedApp(context: Context, packageName: String) =
            BlockingPreferences.addWhitelistedApp(context, packageName)
        fun removeWhitelistedApp(context: Context, packageName: String) =
            BlockingPreferences.removeWhitelistedApp(context, packageName)
        fun getWhitelistedApps(context: Context): Set<String> =
            BlockingPreferences.getWhitelistedApps(context)
        fun addWhitelistedKeyword(context: Context, keyword: String) =
            BlockingPreferences.addWhitelistedKeyword(context, keyword)
        fun removeWhitelistedKeyword(context: Context, keyword: String) =
            BlockingPreferences.removeWhitelistedKeyword(context, keyword)
        fun getWhitelistedKeywords(context: Context): Set<String> =
            BlockingPreferences.getWhitelistedKeywords(context)
        fun saveAccountabilityMode(context: Context, mode: String) =
            BlockingPreferences.saveAccountabilityMode(context, mode)
        fun setTimeDelay(context: Context, unlockAt: Long, target: String) =
            BlockingPreferences.setTimeDelay(context, unlockAt, target)
        fun clearTimeDelay(context: Context) = BlockingPreferences.clearTimeDelay(context)

        fun saveSettings(
            context: Context? = BlockingPreferences.appContext,
            adult: Boolean = BlockingPreferences.isAdultBlockingEnabled,
            shorts: Boolean = BlockingPreferences.isShortsBlockingEnabled,
            whatsapp: Boolean = BlockingPreferences.isWhatsAppChannelsBlockingEnabled,
            instagramReels: Boolean = BlockingPreferences.isInstagramReelsBlockingEnabled,
            tiktok: Boolean = BlockingPreferences.isTikTokBlockingEnabled,
            uninstall: Boolean = BlockingPreferences.isUninstallProtectionEnabled,
            unsupportedBrowsers: Boolean = BlockingPreferences.isUnsupportedBrowsersBlockingEnabled,
            newInstalledApps: Boolean = BlockingPreferences.isNewlyInstalledAppsBlockingEnabled,
            message: String = BlockingPreferences.blockedScreenMessage,
            countdown: Int = BlockingPreferences.blockedScreenCountdown,
            redirect: String = BlockingPreferences.redirectUrl
        ) = BlockingPreferences.saveSettings(
            context = context,
            adult = adult,
            shorts = shorts,
            whatsapp = whatsapp,
            instagramReels = instagramReels,
            tiktok = tiktok,
            uninstall = uninstall,
            unsupportedBrowsers = unsupportedBrowsers,
            newInstalledApps = newInstalledApps,
            message = message,
            countdown = countdown,
            redirect = redirect,
            markDirty = true
        )

        /**
         * Checks whether PGuard's accessibility service has been enabled in Android Settings.
         */
        fun isEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${PGuardAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val service = colonSplitter.next()
                if (service.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    private val contentFilter = ContentFilter.Default
    private val installTimeCache = ConcurrentHashMap<String, Long>()

    private var lastBlockedUrl: String? = null
    private var lastBlockedTimestamp: Long = 0L
    private var lastShortsBlockedTimestamp: Long = 0L
    private var lastWhatsAppBlockedTimestamp: Long = 0L
    private var lastInstagramBlockedTimestamp: Long = 0L
    private var lastTikTokBlockedTimestamp: Long = 0L
    private var lastAntiTamperBlockedTimestamp: Long = 0L
    private var lastUnsupportedBrowserBlockedTimestamp: Long = 0L
    private var lastAppBlockedTimestamp: Long = 0L

    private var lastBrowserDeepScanMs: Long = 0L
    private var lastSocialHierarchyScanMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    val overlayManager by lazy { AccessibilityOverlayManager(this) }
    private val dynamicInstallReceiver = com.example.pguard.engine.receiver.NewAppInstallReceiver()

    @Suppress("DEPRECATION")
    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        loadSettings(this)
        FocusModeManager.init(this)

        val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
        info.flags = info.flags or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info

        try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_PACKAGE_ADDED).apply {
                addDataScheme("package")
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dynamicInstallReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(dynamicInstallReceiver, filter)
            }
            Log.i(TAG, "Registered dynamic NewAppInstallReceiver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register dynamic install receiver: ${e.message}")
        }

        Log.i(TAG, "PGuard Accessibility Service connected and active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (overlayManager.isFullScreenShowing || System.currentTimeMillis() < blockSuppressionUntil) {
            return
        }

        // 0. Emergency Urge Lockdown (2-minute focus lockdown during peak urges: locks phone from use)
        if (FocusModeManager.isUrgeLockdownActive(this)) {
            if (!FocusModeManager.isPackageAllowedDuringUrgeLockdown(this, packageName)) {
                triggerUrgeLockdownBlock(packageName)
                return
            }
        }

        // 1. Focus Mode Scheduled App Blocking
        val activeFocusSchedule = FocusModeManager.getActiveSchedule(this)
        if (activeFocusSchedule != null && !FocusModeManager.isPackageAllowed(this, packageName, activeFocusSchedule)) {
            triggerFocusBlock(packageName, activeFocusSchedule)
            return
        }

        // 2. App Blocking (Blocked apps from blocklist / auto-blocked newly installed apps)
        val isAppWhitelisted = whitelistedAppPackages.contains(packageName)
        if (!isAppWhitelisted) {
            if (blockedAppPackages.contains(packageName) && packageName != applicationContext.packageName) {
                triggerAppBlock(packageName)
                return
            }

            if (isNewlyInstalledAppsBlockingEnabled && newAppsEnabledAt > 0L &&
                packageName != applicationContext.packageName && !isSystemOrLauncherPackage(packageName)
            ) {
                val firstInstall = getCachedFirstInstallTime(packageName)
                if (firstInstall >= newAppsEnabledAt) {
                    val appName = getAppLabelSafe(packageName)
                    addBlockedApp(applicationContext, packageName, appName)
                    triggerAppBlock(packageName)
                    return
                }
            }
        }

        // 3. Unsupported Browsers Blocking
        if (isUnsupportedBrowsersBlockingEnabled && BrowserInspector.isUnsupportedBrowser(packageName, applicationContext)) {
            triggerUnsupportedBrowserBlock(packageName)
            return
        }

        // 4. Browser URL and Content Scanning
        if (BrowserInspector.isBrowser(packageName, applicationContext)) {
            inspectBrowserActivity(event)
            return
        }

        // 5. YouTube Shorts Blocking
        if (isShortsBlockingEnabled && SocialAppGuards.isYouTubePackage(packageName)) {
            inspectYouTube(event)
            return
        }

        // 6. WhatsApp Channel Exploration & Following Blocking
        if (isWhatsAppChannelsBlockingEnabled && SocialAppGuards.isWhatsAppPackage(packageName)) {
            inspectWhatsApp(event)
            return
        }

        // 7. TikTok Full App Blocking
        if (isTikTokBlockingEnabled && SocialAppGuards.isTikTokPackage(packageName)) {
            triggerTikTokBlock()
            return
        }

        // 8. Instagram Reels Blocking
        if (isInstagramReelsBlockingEnabled && SocialAppGuards.isInstagramPackage(packageName)) {
            inspectInstagram(event)
            return
        }

        // 9. Anti-Tamper / Uninstall Protection
        if (isUninstallProtectionEnabled && AntiTamperGuard.isTamperPackage(packageName)) {
            inspectTamperAndUninstall(event)
        }
    }

    private fun getCachedFirstInstallTime(packageName: String): Long {
        installTimeCache[packageName]?.let { return it }
        return try {
            if (installTimeCache.size >= MAX_INSTALL_TIME_CACHE_SIZE) {
                installTimeCache.clear()
            }
            val pkgInfo = packageManager.getPackageInfo(packageName, 0)
            val time = pkgInfo.firstInstallTime
            installTimeCache[packageName] = time
            time
        } catch (_: Exception) {
            0L
        }
    }

    private fun getAppLabelSafe(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun inspectBrowserActivity(event: AccessibilityEvent) {
        val rootNode = findRootNode(event)

        // Fast Path 0: Check if current browser address bar URL is whitelisted
        val detectedUrl = BrowserInspector.extractUrl(rootNode, event)
        if (detectedUrl != null && contentFilter.matchesWhitelist(detectedUrl, whitelistedKeywords)) {
            return
        }

        // Fast Path 1 (0ms latency): Check incoming event text & event source text immediately
        val eventText = extractEventText(event)
        if (eventText.isNotBlank() && shouldBlock(eventText)) {
            Log.w(TAG, "Instant block triggered by event text: $eventText")
            triggerBlock(eventText)
            return
        }

        // Fast Path 2: Address bar / search input / navigation URL
        if (detectedUrl != null && shouldBlock(detectedUrl)) {
            Log.w(TAG, "Instant block triggered by address bar URL: $detectedUrl")
            triggerBlock(detectedUrl)
            return
        }

        // Throttle expensive IPC + recursive hierarchy scans during rapid content updates
        val now = System.currentTimeMillis()
        if (rootNode == null || (now - lastBrowserDeepScanMs) < DEEP_SCAN_THROTTLE_MS) {
            return
        }
        lastBrowserDeepScanMs = now

        // Fast Path 3: Direct keyword inspection using native findAccessibilityNodeInfosByText
        val highPriorityKeywords = listOf("porn", "fuck", "nsfw", "sex", "pussy", "dick", "nude", "xxx")
        for (kw in highPriorityKeywords) {
            try {
                val matchingNodes = rootNode.findAccessibilityNodeInfosByText(kw)
                if (!matchingNodes.isNullOrEmpty()) {
                    for (node in matchingNodes) {
                        val txt = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                        if (txt.isNotBlank() && shouldBlock(txt)) {
                            Log.w(TAG, "Instant native keyword match on '$kw': $txt")
                            triggerBlock(txt)
                            return
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Fast Path 4: Fallback deep scan on visible web content
        val matchedContent = BrowserInspector.findRestrictedContent(rootNode) { text ->
            shouldBlock(text)
        }
        if (matchedContent != null) {
            Log.w(TAG, "Deep scan match: $matchedContent")
            triggerBlock(matchedContent)
        }
    }

    private fun findRootNode(event: AccessibilityEvent?): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }

        var current = event?.source
        while (current != null) {
            val parent = current.parent ?: return current
            current = parent
        }

        try {
            val windowList = windows
            for (window in windowList) {
                if (window.isActive || window.isFocused) {
                    window.root?.let { return it }
                }
            }
            for (window in windowList) {
                window.root?.let { return it }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun extractEventText(event: AccessibilityEvent): String {
        val sb = StringBuilder()
        for (t in event.text) {
            if (!t.isNullOrBlank()) sb.append(t).append(" ")
        }
        if (!event.contentDescription.isNullOrBlank()) {
            sb.append(event.contentDescription).append(" ")
        }
        val source = event.source
        if (source != null) {
            source.text?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append(" ") }
            source.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append(" ") }

            val parent = source.parent
            if (parent != null) {
                parent.text?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append(" ") }
                parent.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append(" ") }
            }
        }
        return sb.toString().trim()
    }

    private fun shouldBlock(input: String): Boolean {
        if (contentFilter.matchesWhitelist(input, whitelistedKeywords)) {
            return false
        }
        if (isAdultBlockingEnabled && contentFilter.isAdultContent(input)) {
            return true
        }
        if (isShortsBlockingEnabled && isYouTubeShortsUrl(input)) {
            return true
        }
        if (isWhatsAppChannelsBlockingEnabled && isWhatsAppChannelUrl(input)) {
            return true
        }
        if (isInstagramReelsBlockingEnabled && isInstagramReelsUrl(input)) {
            return true
        }
        if (isTikTokBlockingEnabled && isTikTokUrl(input)) {
            return true
        }
        return contentFilter.matchesCustomBlocklist(input, customBlocklist)
    }

    private fun isYouTubeShortsUrl(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT).trim()
        return lower.contains("youtube.com/shorts") ||
            lower.contains("m.youtube.com/shorts") ||
            lower.contains("youtu.be/shorts")
    }

    private fun isWhatsAppChannelUrl(input: String): Boolean =
        input.lowercase(Locale.ROOT).trim().contains("whatsapp.com/channel")

    private fun isInstagramReelsUrl(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT).trim()
        return lower.contains("instagram.com/reel") || lower.contains("instagram.com/reels")
    }

    private fun isTikTokUrl(input: String): Boolean =
        input.lowercase(Locale.ROOT).trim().contains("tiktok.com")

    private fun triggerBlock(target: String) {
        val now = System.currentTimeMillis()
        if (target == lastBlockedUrl && (now - lastBlockedTimestamp) < BLOCK_COOLDOWN_MS) {
            return
        }

        lastBlockedUrl = target
        lastBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        Log.w(TAG, "BLOCKED restricted content detected: $target")

        overlayManager.showFullScreenOverlay(
            target = target,
            message = blockedScreenMessage,
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                suppressBlockingFor(3000L)
                val backHandled = performGlobalAction(GLOBAL_ACTION_BACK)
                if (!backHandled) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } else {
                    mainHandler.postDelayed({
                        val currentPkg = rootInActiveWindow?.packageName?.toString()
                        if (currentPkg != null && BrowserInspector.isBrowser(currentPkg, applicationContext)) {
                            val url = BrowserInspector.extractUrl(rootInActiveWindow, null)
                            if (url != null && shouldBlock(url)) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                        }
                    }, 400L)
                }
            }
        )
    }

    private fun canRunSocialHierarchyScan(now: Long): Boolean {
        if ((now - lastSocialHierarchyScanMs) < DEEP_SCAN_THROTTLE_MS) return false
        lastSocialHierarchyScanMs = now
        return true
    }

    private fun inspectYouTube(event: AccessibilityEvent) {
        if (!isShortsBlockingEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastShortsBlockedTimestamp < SHORTS_COOLDOWN_MS) return

        if (SocialAppGuards.isYouTubeShortsEventMatch(event)) {
            triggerShortsBlock()
            return
        }

        if (!canRunSocialHierarchyScan(now)) return
        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source ?: return
        if (SocialAppGuards.isShortsActiveInHierarchy(rootNode)) {
            triggerShortsBlock()
        }
    }

    private fun triggerShortsBlock() {
        val now = System.currentTimeMillis()
        if (now - lastShortsBlockedTimestamp < SHORTS_COOLDOWN_MS) return
        lastShortsBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        Log.w(TAG, "BLOCKED: YouTube Shorts detected, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "YouTube Shorts",
            message = if (blockedScreenMessage.isNotBlank()) blockedScreenMessage else "YouTube Shorts is blocked.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                suppressBlockingFor(3000L)
                if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        )
    }

    private fun inspectWhatsApp(event: AccessibilityEvent) {
        if (!isWhatsAppChannelsBlockingEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastWhatsAppBlockedTimestamp < WHATSAPP_COOLDOWN_MS) return

        if (SocialAppGuards.isWhatsAppChannelClickMatch(event)) {
            triggerWhatsAppBlock("Exploring or following new WhatsApp channels is blocked.")
            return
        }

        if (!canRunSocialHierarchyScan(now)) return
        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source ?: return
        if (SocialAppGuards.isWhatsAppChannelDirectoryActive(rootNode)) {
            triggerWhatsAppBlock("Exploring new WhatsApp channels is blocked.")
        }
    }

    private fun triggerWhatsAppBlock(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastWhatsAppBlockedTimestamp < WHATSAPP_COOLDOWN_MS) return
        lastWhatsAppBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        Log.w(TAG, "BLOCKED: $reason, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "WhatsApp Channels",
            message = reason,
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                suppressBlockingFor(3000L)
                if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        )
    }

    private fun triggerTikTokBlock() {
        val now = System.currentTimeMillis()
        if (now - lastTikTokBlockedTimestamp < TIKTOK_COOLDOWN_MS) return
        lastTikTokBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        Log.w(TAG, "BLOCKED: TikTok application opened, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "TikTok",
            message = "TikTok is completely blocked.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                suppressBlockingFor(3000L)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    private fun inspectInstagram(event: AccessibilityEvent) {
        if (!isInstagramReelsBlockingEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastInstagramBlockedTimestamp < INSTAGRAM_COOLDOWN_MS) return

        if (SocialAppGuards.isInstagramReelsEventMatch(event)) {
            triggerInstagramReelsBlock()
            return
        }

        if (!canRunSocialHierarchyScan(now)) return
        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source ?: return
        if (SocialAppGuards.isInstagramReelsActiveInHierarchy(rootNode)) {
            triggerInstagramReelsBlock()
        }
    }

    private fun triggerInstagramReelsBlock() {
        val now = System.currentTimeMillis()
        if (now - lastInstagramBlockedTimestamp < INSTAGRAM_COOLDOWN_MS) return
        lastInstagramBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        Log.w(TAG, "BLOCKED: Instagram Reels detected, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "Instagram Reels",
            message = "Instagram Reels are blocked.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                suppressBlockingFor(3000L)
                if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        )
    }

    private fun triggerUrgeLockdownBlock(packageName: String) {
        HabitsManager.recordBlockInterception(applicationContext)
        if (overlayManager.isFullScreenShowing) return

        val remaining = FocusModeManager.getUrgeLockdownRemainingSeconds(this)
        val appName = getAppLabelSafe(packageName)

        Log.w(TAG, "BLOCKED: 2-Minute Urge Lockdown Active. Phone locked from use: $appName")
        overlayManager.showUrgeLockdownOverlay(
            appName = appName,
            remainingSeconds = remaining
        )
    }

    private fun triggerFocusBlock(packageName: String, schedule: com.example.pguard.presentation.focus.FocusSchedule) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        HabitsManager.recordBlockInterception(applicationContext)
        if (overlayManager.isFullScreenShowing) return

        val appName = getAppLabelSafe(packageName)
        Log.w(TAG, "BLOCKED: Focus Mode Active. App $appName is blocked during ${schedule.formattedTimeRange}")
        overlayManager.showFocusOverlay(
            appName = appName,
            scheduleInfo = schedule.formattedDuration,
            onGoHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
        )
    }

    private fun triggerAppBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastAppBlockedTimestamp < 1500L) return
        lastAppBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        val appName = getAppLabelSafe(packageName)
        Log.w(TAG, "BLOCKED: Blocked application accessed: $appName ($packageName)")
        overlayManager.showFullScreenOverlay(
            target = appName,
            message = "This app is in your Blocklist.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                suppressBlockingFor(3000L)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    private fun triggerUnsupportedBrowserBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastUnsupportedBrowserBlockedTimestamp < 1500L) return
        lastUnsupportedBrowserBlockedTimestamp = now

        val browserName = BrowserInspector.getBrowserDisplayName(packageName, applicationContext)
        Log.w(TAG, "BLOCKED: Unsupported browser detected: $browserName ($packageName)")

        val supportedListStr = BrowserInspector.SUPPORTED_BROWSER_NAMES.joinToString(", ")
        overlayManager.showFullScreenOverlay(
            target = browserName,
            message = "This browser is unsupported. Supported browsers are: $supportedListStr.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                suppressBlockingFor(3000L)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    private fun isSystemOrLauncherPackage(pkg: String): Boolean {
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return true
        val lower = pkg.lowercase()
        return lower.contains("launcher") || lower.contains("trebuchet") || lower.contains("quickstep")
    }

    private fun inspectTamperAndUninstall(event: AccessibilityEvent) {
        if (!isUninstallProtectionEnabled) return
        val pkg = event.packageName?.toString() ?: return
        val rootNode = findRootNode(event) ?: return

        if (AntiTamperGuard.shouldBlockTamper(pkg, rootNode, applicationContext.packageName)) {
            triggerUninstallBlock()
        }
    }

    private fun triggerUninstallBlock() {
        val now = System.currentTimeMillis()
        if (now - lastAntiTamperBlockedTimestamp < 1500L) return
        lastAntiTamperBlockedTimestamp = now

        Log.w(TAG, "Anti-tamper: Detected attempt to uninstall or tamper with PGuard. Blocking and returning to Home.")
        performGlobalAction(GLOBAL_ACTION_HOME)

        mainHandler.post {
            Toast.makeText(
                applicationContext,
                "Uninstall Protection is active. Pass your accountability check inside PGuard to disable.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "PGuard Accessibility Service interrupted")
        overlayManager.hideAll()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        _isRunning.value = false
        overlayManager.hideAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        overlayManager.hideAll()
        try {
            unregisterReceiver(dynamicInstallReceiver)
        } catch (_: Exception) {}
        Log.i(TAG, "PGuard Accessibility Service destroyed")
    }
}
