package com.example.pguard.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.pguard.engine.filter.ContentFilter
import com.example.pguard.presentation.blocked.BlockedActivity
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
        const val KEY_MESSAGE = "blocked_screen_message"
        const val KEY_COUNTDOWN = "blocked_screen_countdown"
        const val KEY_REDIRECT_URL = "redirect_url"
        const val KEY_ACCOUNTABILITY_MODE = "accountability_mode"
        const val KEY_TIME_DELAY_UNLOCK_AT = "time_delay_unlock_at"
        const val KEY_TIME_DELAY_TARGET = "time_delay_target"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        // Configuration toggles (in-memory cache)
        var isAdultBlockingEnabled: Boolean = true
        var isShortsBlockingEnabled: Boolean = false
        var isWhatsAppChannelsBlockingEnabled: Boolean = false
        var isInstagramReelsBlockingEnabled: Boolean = false
        var isTikTokBlockingEnabled: Boolean = false
        var isUninstallProtectionEnabled: Boolean = false
        var isUnsupportedBrowsersBlockingEnabled: Boolean = false
        var isNewlyInstalledAppsBlockingEnabled: Boolean = false
        var newAppsEnabledAt: Long = 0L
        var blockedScreenMessage: String = "This page is blocked."
        var blockedScreenCountdown: Int = 3
        var redirectUrl: String = ""
        var accountabilityMode: String = "none"
        var timeDelayUnlockAt: Long = 0L
        var timeDelayTarget: String = ""

        val customBlocklist: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val blockedAppPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()

        @Volatile
        var appContext: Context? = null

        fun loadSettings(context: Context) {
            appContext = context.applicationContext
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isAdultBlockingEnabled = prefs.getBoolean(KEY_ADULT, true)
            isShortsBlockingEnabled = prefs.getBoolean(KEY_SHORTS, false)
            isWhatsAppChannelsBlockingEnabled = prefs.getBoolean(KEY_WHATSAPP, false)
            isInstagramReelsBlockingEnabled = prefs.getBoolean(KEY_INSTAGRAM_REELS, false)
            isTikTokBlockingEnabled = prefs.getBoolean(KEY_TIKTOK, false)
            isUninstallProtectionEnabled = prefs.getBoolean(KEY_UNINSTALL, false)
            isUnsupportedBrowsersBlockingEnabled = prefs.getBoolean(KEY_UNSUPPORTED_BROWSERS, false)
            isNewlyInstalledAppsBlockingEnabled = prefs.getBoolean(KEY_NEW_INSTALLED_APPS, false)
            newAppsEnabledAt = prefs.getLong(KEY_NEW_APPS_ENABLED_AT, 0L)
            blockedScreenMessage = prefs.getString(KEY_MESSAGE, "This page is blocked.") ?: "This page is blocked."
            blockedScreenCountdown = prefs.getInt(KEY_COUNTDOWN, 3)
            redirectUrl = prefs.getString(KEY_REDIRECT_URL, "") ?: ""
            accountabilityMode = prefs.getString(KEY_ACCOUNTABILITY_MODE, "none") ?: "none"
            timeDelayUnlockAt = prefs.getLong(KEY_TIME_DELAY_UNLOCK_AT, 0L)
            timeDelayTarget = prefs.getString(KEY_TIME_DELAY_TARGET, "") ?: ""

            val savedBlocked = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
            blockedAppPackages.clear()
            blockedAppPackages.addAll(savedBlocked)

            Log.d(TAG, "Loaded settings: adult=$isAdultBlockingEnabled, shorts=$isShortsBlockingEnabled, whatsapp=$isWhatsAppChannelsBlockingEnabled, reels=$isInstagramReelsBlockingEnabled, tiktok=$isTikTokBlockingEnabled, unsupportedBrowsers=$isUnsupportedBrowsersBlockingEnabled, newApps=$isNewlyInstalledAppsBlockingEnabled, mode=$accountabilityMode")
        }

        fun addBlockedApp(context: Context, packageName: String, label: String = packageName) {
            blockedAppPackages.add(packageName)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
            set.add(packageName)
            prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, set).apply()
            Log.i(TAG, "Added blocked app package: $packageName ($label)")
        }

        fun removeBlockedApp(context: Context, packageName: String) {
            blockedAppPackages.remove(packageName)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toMutableSet() ?: mutableSetOf()
            set.remove(packageName)
            prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, set).apply()
            Log.i(TAG, "Removed blocked app package: $packageName")
        }

        fun getBlockedApps(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
            blockedAppPackages.clear()
            blockedAppPackages.addAll(set)
            return set
        }

        fun saveAccountabilityMode(context: Context, mode: String) {
            accountabilityMode = mode
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCOUNTABILITY_MODE, mode)
                .apply()
        }

        fun setTimeDelay(context: Context, unlockAt: Long, target: String) {
            timeDelayUnlockAt = unlockAt
            timeDelayTarget = target
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_TIME_DELAY_UNLOCK_AT, unlockAt)
                .putString(KEY_TIME_DELAY_TARGET, target)
                .apply()
        }

        fun clearTimeDelay(context: Context) {
            timeDelayUnlockAt = 0L
            timeDelayTarget = ""
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TIME_DELAY_UNLOCK_AT)
                .remove(KEY_TIME_DELAY_TARGET)
                .apply()
        }

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
            redirect: String = redirectUrl
        ) {
            if (context != null) {
                appContext = context.applicationContext
            }
            val targetContext = context ?: appContext

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
                ?.apply()
            Log.d(TAG, "Persisted settings: adult=$adult, shorts=$shorts, whatsapp=$whatsapp, reels=$instagramReels, tiktok=$tiktok, unsupportedBrowsers=$unsupportedBrowsers, newInstalledApps=$newInstalledApps")
        }

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

    private var lastBlockedUrl: String? = null
    private var lastBlockedTimestamp: Long = 0L
    private var lastShortsBlockedTimestamp: Long = 0L
    private var lastWhatsAppBlockedTimestamp: Long = 0L
    private var lastInstagramBlockedTimestamp: Long = 0L
    private var lastTikTokBlockedTimestamp: Long = 0L
    private var lastAntiTamperBlockedTimestamp: Long = 0L
    private var lastUnsupportedBrowserBlockedTimestamp: Long = 0L
    private var lastAppBlockedTimestamp: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    val overlayManager by lazy { AccessibilityOverlayManager(this) }
    private val dynamicInstallReceiver = com.example.pguard.engine.receiver.NewAppInstallReceiver()

    @Suppress("DEPRECATION")
    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        loadSettings(this)

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

        // Dynamic State Reset / Scroll-Through Clearance (Feeds Only)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && overlayManager.isContainerShowing) {
            overlayManager.hideContainerOverlay()
        }

        // 0. App Blocking (Blocked apps from blocklist / auto-blocked newly installed apps)
        if (blockedAppPackages.contains(packageName) && packageName != applicationContext.packageName) {
            triggerAppBlock(packageName)
            return
        }

        // Auto-detect and block newly installed apps upon launch
        if (isNewlyInstalledAppsBlockingEnabled && newAppsEnabledAt > 0L && packageName != applicationContext.packageName && !isSystemOrLauncherPackage(packageName)) {
            try {
                val pkgInfo = packageManager.getPackageInfo(packageName, 0)
                if (pkgInfo.firstInstallTime >= newAppsEnabledAt) {
                    val appName = try {
                        packageManager.getApplicationLabel(pkgInfo.applicationInfo ?: applicationInfo).toString()
                    } catch (_: Exception) {
                        packageName
                    }
                    addBlockedApp(applicationContext, packageName, appName)
                    triggerAppBlock(packageName)
                    return
                }
            } catch (_: Exception) {}
        }

        // 1. Unsupported Browsers Blocking (when toggle is enabled)
        if (isUnsupportedBrowsersBlockingEnabled && BrowserInspector.isUnsupportedBrowser(packageName, applicationContext)) {
            triggerUnsupportedBrowserBlock(packageName)
            return
        }

        // 1. Browser URL and Content Scanning
        if (BrowserInspector.isBrowser(packageName, applicationContext)) {
            inspectBrowserActivity(event)
            return
        }

        // 2. YouTube Shorts Blocking (Official YouTube, Morphe, ReVanced, Vanced, NewPipe, and all modded apps)
        if (isShortsBlockingEnabled && isYouTubePackage(packageName)) {
            inspectYouTube(event)
            return
        }

        // 3. WhatsApp Channel Exploration & Following Blocking
        if (isWhatsAppChannelsBlockingEnabled && isWhatsAppPackage(packageName)) {
            inspectWhatsApp(event)
            return
        }

        // 4. TikTok Full App Blocking
        if (isTikTokBlockingEnabled && isTikTokPackage(packageName)) {
            triggerTikTokBlock()
            return
        }

        // 5. Instagram Reels Blocking
        if (isInstagramReelsBlockingEnabled && isInstagramPackage(packageName)) {
            inspectInstagram(event)
            return
        }

        // 6. Anti-Tamper / Uninstall Protection
        if (isUninstallProtectionEnabled && isTamperPackage(packageName)) {
            inspectTamperAndUninstall(event)
            return
        }
    }

    private fun inspectBrowserActivity(event: AccessibilityEvent) {
        // Fast Path 1 (0ms latency, zero IPC): Check incoming event text & event source text immediately
        val eventText = extractEventText(event)
        if (eventText.isNotBlank() && shouldBlock(eventText)) {
            Log.w(TAG, "Instant block triggered by event text: $eventText")
            triggerBlock(eventText, isBrowserContext = true)
            return
        }

        val rootNode = findRootNode(event)

        // Fast Path 2: Extract from address bar / search input / navigation URL
        val detectedUrl = BrowserInspector.extractUrl(rootNode, event)
        if (detectedUrl != null && shouldBlock(detectedUrl)) {
            Log.w(TAG, "Instant block triggered by address bar URL: $detectedUrl")
            triggerBlock(detectedUrl, isBrowserContext = true)
            return
        }

        // Fast Path 3: Direct keyword inspection using native C++ findAccessibilityNodeInfosByText (single fast IPC)
        if (rootNode != null) {
            val highPriorityKeywords = listOf("porn", "fuck", "nsfw", "sex", "pussy", "dick", "nude", "xxx")
            for (kw in highPriorityKeywords) {
                try {
                    val matchingNodes = rootNode.findAccessibilityNodeInfosByText(kw)
                    if (!matchingNodes.isNullOrEmpty()) {
                        for (node in matchingNodes) {
                            val txt = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                            if (txt.isNotBlank() && shouldBlock(txt)) {
                                Log.w(TAG, "Instant native keyword match on '$kw': $txt")
                                triggerBlock(txt, isBrowserContext = true)
                                return
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Fast Path 4: Fallback deep scan on visible web content
        if (rootNode != null) {
            val matchedContent = BrowserInspector.findRestrictedContent(rootNode) { text ->
                shouldBlock(text)
            }
            if (matchedContent != null) {
                Log.w(TAG, "Deep scan match: $matchedContent")
                triggerBlock(matchedContent, isBrowserContext = true)
            }
        }
    }

    private fun findRootNode(event: AccessibilityEvent?): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }

        var current = event?.source
        while (current != null) {
            val parent = current.parent
            if (parent == null) {
                return current
            }
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
            val srcText = source.text?.toString()
            if (!srcText.isNullOrBlank()) sb.append(srcText).append(" ")
            val srcDesc = source.contentDescription?.toString()
            if (!srcDesc.isNullOrBlank()) sb.append(srcDesc).append(" ")

            val parent = source.parent
            if (parent != null) {
                val pText = parent.text?.toString()
                if (!pText.isNullOrBlank()) sb.append(pText).append(" ")
                val pDesc = parent.contentDescription?.toString()
                if (!pDesc.isNullOrBlank()) sb.append(pDesc).append(" ")
            }
        }
        return sb.toString().trim()
    }

    private fun shouldBlock(input: String): Boolean {
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
        if (contentFilter.matchesCustomBlocklist(input, customBlocklist)) {
            return true
        }
        return false
    }

    private fun isYouTubeShortsUrl(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT).trim()
        return lower.contains("youtube.com/shorts") ||
                lower.contains("m.youtube.com/shorts") ||
                lower.contains("youtu.be/shorts")
    }

    private fun isWhatsAppChannelUrl(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT).trim()
        return lower.contains("whatsapp.com/channel")
    }

    private fun isInstagramReelsUrl(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT).trim()
        return lower.contains("instagram.com/reel") ||
                lower.contains("instagram.com/reels")
    }

    private fun isTikTokUrl(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT).trim()
        return lower.contains("tiktok.com")
    }

    private fun triggerBlock(
        target: String,
        matchedNode: AccessibilityNodeInfo? = null,
        isBrowserContext: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (target == lastBlockedUrl && (now - lastBlockedTimestamp) < BLOCK_COOLDOWN_MS) {
            return
        }

        lastBlockedUrl = target
        lastBlockedTimestamp = now

        Log.w(TAG, "BLOCKED restricted content detected: $target (browser=$isBrowserContext)")

        // 1. Standard Scrollable Feeds in native apps (not web browsers):
        // Context classification for targeted container overlay over feed items
        if (!isBrowserContext && matchedNode != null && isFeedContainerItem(matchedNode)) {
            val bounds = Rect()
            matchedNode.getBoundsInScreen(bounds)
            Log.i(TAG, "Displaying Tier 1 container blur/warning overlay over feed item: $bounds")
            overlayManager.showContainerOverlay(bounds, target)
            return
        }

        // 2. Full-Screen Layouts / Web Browsers:
        // System-level TYPE_ACCESSIBILITY_OVERLAY window with authentic hardware Gaussian blur
        // and prominent "Go Back" button. Neutralizes visual exposure without force-closing the app.
        overlayManager.showFullScreenOverlay(
            target = target,
            message = blockedScreenMessage,
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        )
    }

    private fun isFeedContainerItem(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var hasScrollableAncestor = false
        while (current != null) {
            val className = current.className?.toString() ?: ""
            if (current.isScrollable ||
                className.contains("RecyclerView", ignoreCase = true) ||
                className.contains("ListView", ignoreCase = true) ||
                className.contains("ScrollView", ignoreCase = true) ||
                className.contains("ViewPager", ignoreCase = true)
            ) {
                hasScrollableAncestor = true
                break
            }
            current = current.parent
        }

        if (!hasScrollableAncestor) return false

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        return bounds.height() > 0 && bounds.height() < (screenHeight * 0.8)
    }

    private fun isYouTubePackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.google.android.youtube" ||
                lower.contains("youtube") ||
                lower.contains("revanced") ||
                lower.contains("vanced") ||
                lower.contains("morphe") ||
                lower.contains("newpipe") ||
                lower.contains("libretube") ||
                lower.contains("skytube") ||
                lower.contains("puretuber") ||
                lower.contains("songtube")
    }

    private fun inspectYouTube(event: AccessibilityEvent) {
        if (!isShortsBlockingEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastShortsBlockedTimestamp < SHORTS_COOLDOWN_MS) {
            return
        }

        // 1. Direct Click detection on "Shorts" tab or button in YouTube / Modded YouTube
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text.joinToString(" ")
            val desc = event.contentDescription?.toString() ?: ""
            val srcDesc = event.source?.contentDescription?.toString() ?: ""
            val srcText = event.source?.text?.toString() ?: ""
            val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

            if (isShortsIdentifier(desc) || isShortsIdentifier(text) ||
                isShortsIdentifier(srcDesc) || isShortsIdentifier(srcText) ||
                srcId.contains("pivot_shorts") || srcId.contains("reel_tab") || srcId.contains("reel_time_bar")) {
                Log.w(TAG, "Shorts clicked/selected in YouTube/Mod ($srcId), triggering block")
                triggerShortsBlock()
                return
            }
        }

        // 2. Class name checks (YouTube Reel container / activity / fragment)
        val className = event.className?.toString() ?: ""
        if (className.contains("ReelWatchActivity", ignoreCase = true) ||
            className.contains("ReelWatchFragment", ignoreCase = true) ||
            className.contains("ReelPlayerView", ignoreCase = true)) {
            Log.w(TAG, "YouTube Reel container class active ($className), triggering block")
            triggerShortsBlock()
            return
        }

        // 3. Hierarchy inspection for active Shorts player or selected Shorts bottom tab
        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source ?: return
        if (isShortsActiveInHierarchy(rootNode)) {
            Log.w(TAG, "Shorts player or selected Shorts tab active in YouTube/Mod, triggering block")
            triggerShortsBlock()
        }
    }

    private fun isShortsIdentifier(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return trimmed.equals("Shorts", ignoreCase = true) ||
                trimmed.startsWith("Shorts tab", ignoreCase = true) ||
                trimmed.startsWith("Shorts, ", ignoreCase = true) ||
                trimmed.equals("YouTube Shorts", ignoreCase = true)
    }

    private fun isShortsActiveInHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 20) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""

        // A. Reel player/container view IDs (Universal across Official YouTube & Morphe / ReVanced mods)
        if (viewId.contains("reel_time_bar") ||
            viewId.contains("reel_watch_fragment") ||
            viewId.contains("reel_player_page_container") ||
            viewId.contains("reel_recycler") ||
            viewId.contains("reel_video_player") ||
            viewId.contains("reel_bottom_ui_container") ||
            viewId.contains("shorts_container")) {
            return true
        }

        // B. Selected Shorts tab in bottom navigation
        if (desc == "shorts" || desc == "shorts tab" || desc.startsWith("shorts, ") || text == "shorts") {
            if (node.isSelected || node.isFocused) {
                return true
            }
        }

        // C. Traverse children
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (isShortsActiveInHierarchy(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun triggerShortsBlock() {
        val now = System.currentTimeMillis()
        if (now - lastShortsBlockedTimestamp < SHORTS_COOLDOWN_MS) {
            return
        }
        lastShortsBlockedTimestamp = now

        Log.w(TAG, "BLOCKED: YouTube Shorts detected, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "YouTube Shorts",
            message = if (blockedScreenMessage.isNotBlank()) blockedScreenMessage else "YouTube Shorts is blocked.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        )
    }

    private fun isWhatsAppPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.whatsapp" ||
                lower == "com.whatsapp.w4b" ||
                lower.contains("whatsapp")
    }

    private fun inspectWhatsApp(event: AccessibilityEvent) {
        if (!isWhatsAppChannelsBlockingEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastWhatsAppBlockedTimestamp < WHATSAPP_COOLDOWN_MS) {
            return
        }

        // 1. Click-level interception: "Explore more", "Find channels", or "Follow"
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text.joinToString(" ")
            val desc = event.contentDescription?.toString() ?: ""
            val srcDesc = event.source?.contentDescription?.toString() ?: ""
            val srcText = event.source?.text?.toString() ?: ""
            val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

            if (isChannelExploreOrFollowTarget(desc) ||
                isChannelExploreOrFollowTarget(text) ||
                isChannelExploreOrFollowTarget(srcDesc) ||
                isChannelExploreOrFollowTarget(srcText) ||
                srcId.contains("newsletter_directory") ||
                srcId.contains("directory_search") ||
                srcId.contains("newsletter_follow")) {
                Log.w(TAG, "WhatsApp Channel Explore / Follow clicked ($srcId), blocking")
                triggerWhatsAppBlock("Exploring or following new WhatsApp channels is blocked.")
                return
            }
        }

        // 2. Window / Screen hierarchy check: Are we on the "Find channels" / Directory screen?
        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source ?: return
        if (isWhatsAppChannelDirectoryActive(rootNode)) {
            Log.w(TAG, "WhatsApp Channel directory / explore active, blocking")
            triggerWhatsAppBlock("Exploring new WhatsApp channels is blocked.")
        }
    }

    private fun isChannelExploreOrFollowTarget(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()

        // Tapping "Follow" on a channel the user does not follow
        if (trimmed.equals("Follow", ignoreCase = true) ||
            trimmed.equals("Follow channel", ignoreCase = true) ||
            trimmed.startsWith("Follow ", ignoreCase = true) ||
            trimmed.equals("Join channel", ignoreCase = true)) {
            return true
        }

        // Tapping explore / find channels / see all
        if (trimmed.equals("Explore more", ignoreCase = true) ||
            trimmed.equals("Find channels", ignoreCase = true) ||
            trimmed.equals("Explore channels", ignoreCase = true) ||
            trimmed.equals("Discover channels", ignoreCase = true) ||
            trimmed.startsWith("Find channels", ignoreCase = true) ||
            trimmed.startsWith("Explore channels", ignoreCase = true) ||
            trimmed.equals("See all", ignoreCase = true)) {
            return true
        }

        return false
    }

    private fun isWhatsAppChannelDirectoryActive(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 15) return false

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        // Directory container / search IDs
        if (viewId.contains("newsletter_directory") ||
            viewId.contains("directory_search") ||
            viewId.contains("directory_categories") ||
            viewId.contains("newsletter_categories")) {
            return true
        }

        // Directory title
        if (text.equals("Find channels", ignoreCase = true) ||
            text.equals("Explore channels", ignoreCase = true) ||
            desc.equals("Find channels", ignoreCase = true) ||
            desc.equals("Explore channels", ignoreCase = true) ||
            text.equals("Search channels", ignoreCase = true) ||
            desc.equals("Search channels", ignoreCase = true)) {
            return true
        }

        // Directory category chips
        if (text.equals("Most active", ignoreCase = true) ||
            text.equals("Popular channels", ignoreCase = true) ||
            desc.equals("Most active", ignoreCase = true)) {
            return true
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (isWhatsAppChannelDirectoryActive(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun triggerWhatsAppBlock(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastWhatsAppBlockedTimestamp < WHATSAPP_COOLDOWN_MS) {
            return
        }
        lastWhatsAppBlockedTimestamp = now

        Log.w(TAG, "BLOCKED: $reason, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "WhatsApp Channels",
            message = reason,
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        )
    }

    // --- TIKTOK BLOCKING ---

    private fun isTikTokPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.zhiliaoapp.musically" ||
                lower == "com.zhiliaoapp.musically.go" ||
                lower == "com.ss.android.ugc.trill" ||
                lower == "com.ss.android.ugc.aweme" ||
                lower.contains("musically") ||
                lower.contains("tiktok") ||
                lower.contains("trill")
    }

    private fun triggerTikTokBlock() {
        val now = System.currentTimeMillis()
        if (now - lastTikTokBlockedTimestamp < TIKTOK_COOLDOWN_MS) {
            return
        }
        lastTikTokBlockedTimestamp = now

        Log.w(TAG, "BLOCKED: TikTok application opened, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "TikTok",
            message = "TikTok is completely blocked.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    // --- INSTAGRAM REELS BLOCKING ---

    private fun isInstagramPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.instagram.android" ||
                lower == "com.instagram.lite" ||
                lower.contains("instagram") ||
                lower.contains("instander") ||
                lower.contains("honista")
    }

    private fun inspectInstagram(event: AccessibilityEvent) {
        if (!isInstagramReelsBlockingEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastInstagramBlockedTimestamp < INSTAGRAM_COOLDOWN_MS) {
            return
        }

        // 1. Direct click on Reels tab
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text.joinToString(" ")
            val desc = event.contentDescription?.toString() ?: ""
            val srcDesc = event.source?.contentDescription?.toString() ?: ""
            val srcText = event.source?.text?.toString() ?: ""
            val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

            if (isInstagramReelsIdentifier(desc) ||
                isInstagramReelsIdentifier(text) ||
                isInstagramReelsIdentifier(srcDesc) ||
                isInstagramReelsIdentifier(srcText) ||
                srcId.contains("clips_tab") ||
                srcId.contains("reels_tab")) {
                Log.w(TAG, "Instagram Reels clicked ($srcId), blocking")
                triggerInstagramReelsBlock()
                return
            }
        }

        // 2. Class name check (ClipsViewerFragment, ModalActivity with clips)
        val className = event.className?.toString() ?: ""
        if (className.contains("ClipsViewer", ignoreCase = true) ||
            className.contains("ClipsActivity", ignoreCase = true)) {
            Log.w(TAG, "Instagram Clips/Reels container class active ($className), blocking")
            triggerInstagramReelsBlock()
            return
        }

        // 3. Hierarchy inspection for active Reels viewer or selected Reels tab
        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source ?: return
        if (isInstagramReelsActiveInHierarchy(rootNode)) {
            Log.w(TAG, "Instagram Reels active view/tab detected, blocking")
            triggerInstagramReelsBlock()
        }
    }

    private fun isInstagramReelsIdentifier(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return trimmed.equals("Reels", ignoreCase = true) ||
                trimmed.startsWith("Reels tab", ignoreCase = true) ||
                trimmed.startsWith("Reels, ", ignoreCase = true) ||
                trimmed.equals("Instagram Reels", ignoreCase = true)
    }

    private fun isInstagramReelsActiveInHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 20) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""

        // A. Reels container / video view IDs
        if (viewId.contains("clips_viewer") ||
            viewId.contains("clips_video_container") ||
            viewId.contains("clips_viewer_view_pager") ||
            viewId.contains("reels_tab") ||
            viewId.contains("clips_item")) {
            return true
        }

        // B. Selected Reels tab in bottom navigation
        if (desc == "reels" || desc == "reels tab" || desc.startsWith("reels, ") || text == "reels") {
            if (node.isSelected || node.isFocused) {
                return true
            }
        }

        // C. Traverse children
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (isInstagramReelsActiveInHierarchy(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun triggerInstagramReelsBlock() {
        val now = System.currentTimeMillis()
        if (now - lastInstagramBlockedTimestamp < INSTAGRAM_COOLDOWN_MS) {
            return
        }
        lastInstagramBlockedTimestamp = now

        Log.w(TAG, "BLOCKED: Instagram Reels detected, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "Instagram Reels",
            message = "Instagram Reels are blocked.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        )
    }

    private fun triggerAppBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastAppBlockedTimestamp < 1500L) {
            return
        }
        lastAppBlockedTimestamp = now

        val appName = try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }

        Log.w(TAG, "BLOCKED: Blocked application accessed: $appName ($packageName)")
        overlayManager.showFullScreenOverlay(
            target = appName,
            message = "This app is in your Blocklist.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = redirectUrl,
            onGoBack = {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    private fun triggerUnsupportedBrowserBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastUnsupportedBrowserBlockedTimestamp < 1500L) {
            return
        }
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
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    private fun isTamperPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.android.settings" ||
                lower == "com.google.android.settings" ||
                lower == "com.google.android.packageinstaller" ||
                lower == "com.android.packageinstaller" ||
                lower == "com.google.android.permissioncontroller" ||
                lower == "com.android.permissioncontroller" ||
                lower == "com.android.vending" ||
                lower == "android" ||
                lower.contains("packageinstaller") ||
                lower.contains("permissioncontroller") ||
                lower.contains("settings") ||
                lower.contains("securitycenter")
    }

    private fun isSystemOrLauncherPackage(pkg: String): Boolean {
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") return true
        val lower = pkg.lowercase()
        return lower.contains("launcher") || lower.contains("trebuchet") || lower.contains("quickstep")
    }

    private fun isPermissionConfigurationScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val permissionKeywords = listOf(
            "display over other apps",
            "appear on top",
            "display pop-up",
            "pop-up window",
            "draw over other apps",
            "special app access",
            "allow display",
            "permit drawing",
            "notification",
            "battery",
            "unrestricted",
            "autostart",
            "activate this device admin",
            "activate admin",
            "permission manager",
            "app permissions",
            "modify system settings"
        )
        for (keyword in permissionKeywords) {
            val matches = root.findAccessibilityNodeInfosByText(keyword)
            if (!matches.isNullOrEmpty()) {
                return true
            }
        }
        return false
    }

    private fun isUninstallDialog(pkg: String, root: AccessibilityNodeInfo): Boolean {
        val lowerPkg = pkg.lowercase()
        val isInstaller = lowerPkg.contains("packageinstaller") || lowerPkg.contains("vending")
        if (isInstaller && isPGuardTargetedInNodeTree(root)) {
            val matches = root.findAccessibilityNodeInfosByText("uninstall")
            if (!matches.isNullOrEmpty()) return true
        }
        return false
    }

    private fun isDestructiveTamperAction(root: AccessibilityNodeInfo): Boolean {
        val destructiveKeywords = listOf(
            "uninstall",
            "force stop",
            "clear storage",
            "clear data",
            "deactivate",
            "turn off",
            "stop service"
        )
        for (keyword in destructiveKeywords) {
            val matches = root.findAccessibilityNodeInfosByText(keyword)
            if (!matches.isNullOrEmpty()) {
                val isDangerous = matches.any { node ->
                    val text = (node.text?.toString() ?: "").lowercase()
                    val desc = (node.contentDescription?.toString() ?: "").lowercase()
                    (text.contains(keyword) || desc.contains(keyword)) &&
                    !text.contains("allow") && !desc.contains("allow")
                }
                if (isDangerous) return true
            }
        }
        return false
    }

    private fun inspectTamperAndUninstall(event: AccessibilityEvent) {
        if (!isUninstallProtectionEnabled) return

        val rootNode = findRootNode(event) ?: return

        // 1. NEVER block user from granting required permissions (Display over other apps, Notifications, Battery, Admin activation)
        if (isPermissionConfigurationScreen(rootNode)) {
            return
        }

        val pkg = event.packageName?.toString() ?: ""

        // 2. Package installer uninstall prompt for PGuard
        if (isUninstallDialog(pkg, rootNode)) {
            triggerUninstallBlock()
            return
        }

        // 3. Settings: Only block if PGuard is targeted AND a destructive action (Uninstall, Force Stop, Clear Data, Deactivate) is present
        if (isPGuardTargetedInNodeTree(rootNode) && isDestructiveTamperAction(rootNode)) {
            triggerUninstallBlock()
        }
    }

    private fun containsPGuardReference(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.contains("PGuard", ignoreCase = true) ||
                text.contains("com.example.pguard", ignoreCase = true)
    }

    private fun isPGuardTargetedInNodeTree(root: AccessibilityNodeInfo): Boolean {
        val byName = root.findAccessibilityNodeInfosByText("PGuard")
        if (!byName.isNullOrEmpty()) return true

        val byPkg = root.findAccessibilityNodeInfosByText("com.example.pguard")
        if (!byPkg.isNullOrEmpty()) return true

        val text = root.text?.toString()
        if (containsPGuardReference(text)) return true

        val desc = root.contentDescription?.toString()
        if (containsPGuardReference(desc)) return true

        return false
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
