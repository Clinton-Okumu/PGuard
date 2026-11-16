package com.example.pguard.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
        const val KEY_MESSAGE = "blocked_screen_message"
        const val KEY_COUNTDOWN = "blocked_screen_countdown"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        // Configuration toggles (in-memory cache)
        var isAdultBlockingEnabled: Boolean = true
        var isShortsBlockingEnabled: Boolean = false
        var isWhatsAppChannelsBlockingEnabled: Boolean = false
        var isInstagramReelsBlockingEnabled: Boolean = false
        var isTikTokBlockingEnabled: Boolean = false
        var isUninstallProtectionEnabled: Boolean = false
        var blockedScreenMessage: String = "This page is blocked."
        var blockedScreenCountdown: Int = 3

        val customBlocklist: MutableSet<String> = ConcurrentHashMap.newKeySet()

        fun loadSettings(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isAdultBlockingEnabled = prefs.getBoolean(KEY_ADULT, true)
            isShortsBlockingEnabled = prefs.getBoolean(KEY_SHORTS, false)
            isWhatsAppChannelsBlockingEnabled = prefs.getBoolean(KEY_WHATSAPP, false)
            isInstagramReelsBlockingEnabled = prefs.getBoolean(KEY_INSTAGRAM_REELS, false)
            isTikTokBlockingEnabled = prefs.getBoolean(KEY_TIKTOK, false)
            isUninstallProtectionEnabled = prefs.getBoolean(KEY_UNINSTALL, false)
            blockedScreenMessage = prefs.getString(KEY_MESSAGE, "This page is blocked.") ?: "This page is blocked."
            blockedScreenCountdown = prefs.getInt(KEY_COUNTDOWN, 3)
            Log.d(TAG, "Loaded settings: adult=$isAdultBlockingEnabled, shorts=$isShortsBlockingEnabled, whatsapp=$isWhatsAppChannelsBlockingEnabled, reels=$isInstagramReelsBlockingEnabled, tiktok=$isTikTokBlockingEnabled")
        }

        fun saveSettings(
            context: Context,
            adult: Boolean = isAdultBlockingEnabled,
            shorts: Boolean = isShortsBlockingEnabled,
            whatsapp: Boolean = isWhatsAppChannelsBlockingEnabled,
            instagramReels: Boolean = isInstagramReelsBlockingEnabled,
            tiktok: Boolean = isTikTokBlockingEnabled,
            uninstall: Boolean = isUninstallProtectionEnabled,
            message: String = blockedScreenMessage,
            countdown: Int = blockedScreenCountdown
        ) {
            isAdultBlockingEnabled = adult
            isShortsBlockingEnabled = shorts
            isWhatsAppChannelsBlockingEnabled = whatsapp
            isInstagramReelsBlockingEnabled = instagramReels
            isTikTokBlockingEnabled = tiktok
            isUninstallProtectionEnabled = uninstall
            blockedScreenMessage = message
            blockedScreenCountdown = countdown

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ADULT, adult)
                .putBoolean(KEY_SHORTS, shorts)
                .putBoolean(KEY_WHATSAPP, whatsapp)
                .putBoolean(KEY_INSTAGRAM_REELS, instagramReels)
                .putBoolean(KEY_TIKTOK, tiktok)
                .putBoolean(KEY_UNINSTALL, uninstall)
                .putString(KEY_MESSAGE, message)
                .putInt(KEY_COUNTDOWN, countdown)
                .apply()
            Log.d(TAG, "Persisted settings: adult=$adult, shorts=$shorts, whatsapp=$whatsapp, reels=$instagramReels, tiktok=$tiktok")
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

        Log.i(TAG, "PGuard Accessibility Service connected and active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // 1. Browser URL and Content Scanning
        if (BrowserInspector.isBrowser(packageName)) {
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
        if (isUninstallProtectionEnabled && packageName == "com.android.settings") {
            inspectSettingsForUninstall(event)
        }
    }

    private fun inspectBrowserActivity(event: AccessibilityEvent) {
        var blockedTarget: String? = null

        // A. Immediate check on the event itself (contains URL or page title directly)
        val eventText = extractEventText(event)
        if (eventText.isNotBlank() && shouldBlock(eventText)) {
            blockedTarget = eventText
        }

        val rootNode = findRootNode(event)

        // B. Primary: Extract from address bar / search input / event text
        if (blockedTarget == null) {
            val detectedUrl = BrowserInspector.extractUrl(rootNode, event)
            if (detectedUrl != null && shouldBlock(detectedUrl)) {
                blockedTarget = detectedUrl
            }
        }

        // C. Fallback scan: Deep scan for visible adult keywords or domain nodes
        if (blockedTarget == null && rootNode != null) {
            val matchedContent = BrowserInspector.findRestrictedContent(rootNode) { text ->
                shouldBlock(text)
            }
            if (matchedContent != null) {
                blockedTarget = matchedContent
            }
        }

        if (blockedTarget != null) {
            triggerBlock(blockedTarget)
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

    private fun triggerBlock(target: String) {
        val now = System.currentTimeMillis()
        if (target == lastBlockedUrl && (now - lastBlockedTimestamp) < BLOCK_COOLDOWN_MS) {
            return
        }

        lastBlockedUrl = target
        lastBlockedTimestamp = now

        Log.w(TAG, "BLOCKED restricted content detected: $target")

        // Immediately close or navigate away from the page
        performGlobalAction(GLOBAL_ACTION_BACK)

        // Launch full-screen Blocked warning
        try {
            BlockedActivity.start(
                context = this,
                url = target,
                message = blockedScreenMessage,
                countdownSeconds = blockedScreenCountdown
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BlockedActivity, falling back to home: ${e.message}")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
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

        Log.w(TAG, "BLOCKED: YouTube Shorts detected, pressing BACK")
        performGlobalAction(GLOBAL_ACTION_BACK)

        try {
            BlockedActivity.start(
                context = this,
                url = "YouTube Shorts",
                message = if (blockedScreenMessage.isNotBlank()) blockedScreenMessage else "YouTube Shorts is blocked.",
                countdownSeconds = blockedScreenCountdown
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedActivity for Shorts: ${e.message}")
        }
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

        Log.w(TAG, "BLOCKED: $reason, pressing BACK")
        performGlobalAction(GLOBAL_ACTION_BACK)

        try {
            BlockedActivity.start(
                context = this,
                url = "WhatsApp Channels",
                message = reason,
                countdownSeconds = blockedScreenCountdown
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedActivity for WhatsApp: ${e.message}")
        }
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

        Log.w(TAG, "BLOCKED: TikTok application opened, returning HOME")
        performGlobalAction(GLOBAL_ACTION_HOME)

        try {
            BlockedActivity.start(
                context = this,
                url = "TikTok",
                message = "TikTok is completely blocked.",
                countdownSeconds = blockedScreenCountdown
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedActivity for TikTok: ${e.message}")
        }
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

        Log.w(TAG, "BLOCKED: Instagram Reels closed, pressing BACK")
        performGlobalAction(GLOBAL_ACTION_BACK)

        try {
            BlockedActivity.start(
                context = this,
                url = "Instagram Reels",
                message = "Instagram Reels are blocked.",
                countdownSeconds = blockedScreenCountdown
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BlockedActivity for Reels: ${e.message}")
        }
    }

    private fun inspectSettingsForUninstall(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: event.source ?: return
        val text = rootNode.text?.toString() ?: ""
        if (text.contains("PGuard", ignoreCase = true)) {
            Log.w(TAG, "Anti-tamper: Device settings opened for PGuard, returning home")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "PGuard Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        Log.i(TAG, "PGuard Accessibility Service destroyed")
    }
}
