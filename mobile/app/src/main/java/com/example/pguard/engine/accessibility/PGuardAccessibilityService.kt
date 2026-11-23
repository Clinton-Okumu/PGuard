package com.example.pguard.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.pguard.engine.accountability.SecurityAlertReporter
import com.example.pguard.engine.filter.ContentFilter
import com.example.pguard.engine.vpn.PGuardVpnService
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
        private const val SCOPED_DISMISS_COOLDOWN_MS = 2000L
        private const val SHORTS_COOLDOWN_MS = 1200L
        private const val WHATSAPP_COOLDOWN_MS = 1200L
        private const val INSTAGRAM_COOLDOWN_MS = 1200L
        private const val TIKTOK_COOLDOWN_MS = 1500L
        private const val DEEP_SCAN_THROTTLE_MS = 250L
        private const val IN_APP_ADULT_SCAN_THROTTLE_MS = 400L
        private const val VPN_WATCHDOG_INTERVAL_MS = 60_000L
        private const val MAX_INSTALL_TIME_CACHE_SIZE = 512
        private val installTimeCache = ConcurrentHashMap<String, Long>()

        fun invalidateInstallTimeCache(packageName: String) {
            installTimeCache.remove(packageName)
        }

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
        val isDnsVpnEnabled: Boolean get() = BlockingPreferences.isDnsVpnEnabled
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
    private val scopedSuppressions = ConcurrentHashMap<String, Long>()

    private var lastBlockedUrl: String? = null
    private var lastBlockedTimestamp: Long = 0L
    private var lastShortsBlockedTimestamp: Long = 0L
    private var lastWhatsAppBlockedTimestamp: Long = 0L
    private var lastInstagramBlockedTimestamp: Long = 0L
    private var lastTikTokBlockedTimestamp: Long = 0L
    private var lastAntiTamperBlockedTimestamp: Long = 0L
    private var lastUnsupportedBrowserBlockedTimestamp: Long = 0L
    private var lastAppBlockedTimestamp: Long = 0L
    private var lastFocusBlockedTimestamp: Long = 0L
    private var lastUrgeBlockedTimestamp: Long = 0L

    private var lastBrowserDeepScanMs: Long = 0L
    private var lastInAppAdultScanMs: Long = 0L
    private var lastVpnWatchdogCheckMs: Long = 0L
    private var lastSocialHierarchyScanMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    val overlayManager by lazy { AccessibilityOverlayManager(this) }
    private val dynamicInstallReceiver = com.example.pguard.engine.receiver.NewAppInstallReceiver()

    private fun suppressScope(scopeKey: String, durationMs: Long = SCOPED_DISMISS_COOLDOWN_MS) {
        if (scopedSuppressions.size >= 128) {
            scopedSuppressions.clear()
        }
        scopedSuppressions[scopeKey] = System.currentTimeMillis() + durationMs
    }

    private fun isScopeSuppressed(scopeKey: String): Boolean {
        val until = scopedSuppressions[scopeKey] ?: return false
        val now = System.currentTimeMillis()
        if (now < until) return true
        scopedSuppressions.remove(scopeKey, until)
        return false
    }

    @Suppress("DEPRECATION")
    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        loadSettings(this)
        FocusModeManager.init(this)

        val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
        info.flags = (info.flags and
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv() and
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY.inv()) or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info

        try {
            val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dynamicInstallReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(dynamicInstallReceiver, filter)
            }
            Log.i(TAG, "Registered dynamic NewAppInstallReceiver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register dynamic install receiver: ${e.message}")
        }

        // Auto-resume local DNS Shield VPN if user enabled it and consent is granted
        try {
            if (BlockingPreferences.isDnsVpnEnabled && VpnService.prepare(this) == null && !PGuardVpnService.isRunning.value) {
                PGuardVpnService.start(this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to auto-resume DNS VPN service: ${e.message}")
        }

        Log.i(TAG, "PGuard Accessibility Service connected and active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (overlayManager.isOverlayCurrentlyAttached()) {
            return
        }

        maybeAutoResumeDnsVpn()

        // 0. Emergency Urge Lockdown (never suppressed)
        if (FocusModeManager.isUrgeLockdownActive(this)) {
            if (!FocusModeManager.isPackageAllowedDuringUrgeLockdown(this, packageName)) {
                triggerUrgeLockdownBlock(packageName)
                return
            }
        }

        // 1. Anti-Tamper / Uninstall Protection (never suppressed, highest non-lockdown priority)
        if (isUninstallProtectionEnabled && AntiTamperGuard.isTamperPackage(packageName)) {
            if (inspectTamperAndUninstall(event)) {
                return
            }
        }

        // 2. Focus Mode Scheduled / Quick Focus Blocking (never suppressed)
        val focusBlockReason = FocusModeManager.getBlockingFocusReason(this, packageName)
        if (focusBlockReason != null) {
            triggerFocusBlock(packageName, focusBlockReason)
            return
        }

        // 3. App Blocking (Blocked apps from blocklist / auto-blocked newly installed apps)
        val isAppWhitelisted = whitelistedAppPackages.contains(packageName)
        if (!isAppWhitelisted) {
            if (blockedAppPackages.contains(packageName) &&
                packageName != applicationContext.packageName &&
                !isScopeSuppressed("app:$packageName")
            ) {
                triggerAppBlock(packageName)
                return
            }

            if (isNewlyInstalledAppsBlockingEnabled && newAppsEnabledAt > 0L &&
                !BlockingPreferences.isNewAppApproved(packageName) &&
                !isScopeSuppressed("app:$packageName") &&
                NewAppInstallGuard.shouldAutoBlockInstalledApp(applicationContext, packageName)
            ) {
                val firstInstall = getCachedFirstInstallTime(packageName)
                if (firstInstall >= newAppsEnabledAt) {
                    val appName = getAppLabelSafe(packageName)
                    addBlockedApp(applicationContext, packageName, appName)
                    NewAppInstallGuard.syncBlockedAppToBackendAsync(applicationContext, packageName, appName)
                    triggerAppBlock(packageName)
                    return
                }
            }
        }

        // 4. Unsupported Browsers Blocking
        if (isUnsupportedBrowsersBlockingEnabled &&
            !isAppWhitelisted &&
            !isScopeSuppressed("unsupported_browser:$packageName") &&
            BrowserInspector.isUnsupportedBrowser(packageName, applicationContext)
        ) {
            triggerUnsupportedBrowserBlock(packageName)
            return
        }

        // 5. Browser URL and Content Scanning
        if (BrowserInspector.isBrowser(packageName, applicationContext)) {
            inspectBrowserActivity(event, packageName)
            return
        }

        // 6. YouTube Shorts Blocking
        if (isShortsBlockingEnabled && SocialAppGuards.isYouTubePackage(packageName)) {
            if (inspectYouTube(event, packageName)) return
        }

        // 7. WhatsApp Channel Exploration & Following Blocking
        if (isWhatsAppChannelsBlockingEnabled && SocialAppGuards.isWhatsAppPackage(packageName)) {
            if (inspectWhatsApp(event, packageName)) return
        }

        // 8. TikTok Full App Blocking
        if (isTikTokBlockingEnabled && SocialAppGuards.isTikTokPackage(packageName)) {
            if (!isScopeSuppressed("tiktok:$packageName")) {
                triggerTikTokBlock(packageName)
            }
            return
        }

        // 9. Instagram Reels Blocking
        if (isInstagramReelsBlockingEnabled && SocialAppGuards.isInstagramPackage(packageName)) {
            if (inspectInstagram(event, packageName)) return
        }

        // 10. Universal in-app adult & custom blocklist guard (Reddit, X, Discord, Telegram, WebViews, etc.)
        if (!isAppWhitelisted &&
            !InAppAdultGuard.isExcludedFromInAppAdultScan(packageName, applicationContext.packageName) &&
            (isAdultBlockingEnabled || customBlocklist.isNotEmpty()) &&
            !isScopeSuppressed("inapp:$packageName")
        ) {
            inspectInAppContent(event, packageName)
        }
    }

    private fun inspectInAppContent(event: AccessibilityEvent, packageName: String) {
        // Never inspect password inputs
        if (event.isPassword || event.source?.isPassword == true) return

        // Fast Path A: Editable text input / search bars in any app
        val source = event.source
        val isEditableInput = event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            (source?.className?.toString()?.contains("EditText", ignoreCase = true) == true)
        if (isEditableInput) {
            val inputCandidate = source?.text?.toString()?.takeIf { it.isNotBlank() }
                ?: extractEventText(event)
            if (inputCandidate.isNotBlank()) {
                val match = if (isAdultBlockingEnabled) {
                    InAppAdultGuard.findAdultInputMatch(
                        inputText = inputCandidate,
                        contentFilter = contentFilter,
                        customBlocklist = customBlocklist,
                        whitelist = whitelistedKeywords
                    )
                } else if (!contentFilter.matchesWhitelist(inputCandidate, whitelistedKeywords) &&
                    contentFilter.matchesCustomBlocklist(inputCandidate, customBlocklist)
                ) {
                    inputCandidate
                } else {
                    null
                }
                if (match != null) {
                    Log.w(TAG, "In-app input block in $packageName: $match")
                    triggerBlock(match, packageName, scopePrefix = "inapp")
                    return
                }
            }
        }

        // Fast Path B: Clicks on explicit subreddits, NSFW badges, or "Yes, I'm over 18" gates
        if (isAdultBlockingEnabled && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val clickCandidates = buildList {
                event.text.forEach { if (!it.isNullOrBlank()) add(it.toString()) }
                event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
                source?.text?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
                source?.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            val clickMatch = InAppAdultGuard.findAdultClickMatch(
                eventTexts = clickCandidates,
                isSocialContext = InAppAdultGuard.isSocialOrCommunityApp(packageName),
                contentFilter = contentFilter,
                whitelist = whitelistedKeywords
            )
            if (clickMatch != null) {
                Log.w(TAG, "In-app click block in $packageName: $clickMatch")
                triggerBlock(clickMatch, packageName, scopePrefix = "inapp")
                return
            }
        }

        // Fast Path C: Embedded WebView event text (adult + custom blocklist)
        val eventClass = event.className?.toString().orEmpty()
        val srcClass = source?.className?.toString().orEmpty()
        val isWebView = eventClass.contains("WebView", ignoreCase = true) ||
            srcClass.contains("WebView", ignoreCase = true)
        if (isWebView) {
            val eventText = extractEventText(event)
            if (eventText.isNotBlank() && !contentFilter.matchesWhitelist(eventText, whitelistedKeywords)) {
                val blockedMatch = contentFilter.matchesCustomBlocklist(eventText, customBlocklist) ||
                    (isAdultBlockingEnabled && contentFilter.findAdultPageContent(listOf(eventText)) != null)
                if (blockedMatch) {
                    Log.w(TAG, "In-app WebView block in $packageName: $eventText")
                    triggerBlock(eventText, packageName, scopePrefix = "inapp")
                    return
                }
            }
        }

        // Throttled visible hierarchy scan on window transitions, scrolls, and content updates
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }

        val now = System.currentTimeMillis()
        if ((now - lastInAppAdultScanMs) < IN_APP_ADULT_SCAN_THROTTLE_MS) return
        lastInAppAdultScanMs = now

        val rootNode = findRootNode(event) ?: return
        val visibleTexts = BrowserInspector.collectVisibleWebTexts(rootNode, maxDepth = 18, maxItems = 90)
        if (visibleTexts.isEmpty()) return

        if (isAdultBlockingEnabled) {
            val adultMatch = InAppAdultGuard.findAdultScreenMatch(
                packageName = packageName,
                activeUrl = null,
                visibleTexts = visibleTexts,
                isBrowser = false,
                contentFilter = contentFilter,
                whitelist = whitelistedKeywords
            )
            if (adultMatch != null) {
                Log.w(TAG, "In-app adult screen block in $packageName: $adultMatch")
                triggerBlock(adultMatch, packageName, scopePrefix = "inapp")
                return
            }
        }

        if (customBlocklist.isNotEmpty()) {
            for (txt in visibleTexts) {
                if ((txt.contains('.') || isWebView) &&
                    !contentFilter.matchesWhitelist(txt, whitelistedKeywords) &&
                    contentFilter.matchesCustomBlocklist(txt, customBlocklist)
                ) {
                    Log.w(TAG, "In-app custom blocklist match in $packageName: $txt")
                    triggerBlock(txt, packageName, scopePrefix = "inapp")
                    return
                }
            }
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

    private fun inspectBrowserActivity(event: AccessibilityEvent, packageName: String) {
        if (isScopeSuppressed("browser:$packageName")) return

        val rootNode = findRootNode(event)

        // Fast Path 0: Verified address-bar URL lookup via direct package view IDs / EditText source
        val detectedUrl = BrowserInspector.extractUrlInfo(rootNode, event, allowFallbackTraversal = false)
        if (detectedUrl != null) {
            if (detectedUrl.isTrustedAddressBar &&
                contentFilter.matchesWhitelist(detectedUrl.value, whitelistedKeywords)
            ) {
                return
            }
            if (shouldBlockUrlOrQuery(detectedUrl.value)) {
                Log.w(TAG, "Instant block triggered by address bar URL: ${detectedUrl.value}")
                triggerBlock(detectedUrl.value, packageName)
                return
            }
        }

        // Fast Path 1: Incoming event text (distinguish editable address/search bar from passive page snippets)
        val eventText = extractEventText(event)
        if (eventText.isNotBlank()) {
            val source = event.source
            val isHighIntentSource = source != null &&
                BrowserInspector.isAddressBarSource(source.viewIdResourceName, source.className?.toString())
            if (isHighIntentSource) {
                if (contentFilter.matchesWhitelist(eventText, whitelistedKeywords)) {
                    return
                }
                if (shouldBlockUrlOrQuery(eventText)) {
                    Log.w(TAG, "Instant block triggered by input event text: $eventText")
                    triggerBlock(eventText, packageName)
                    return
                }
            } else if (isAdultBlockingEnabled && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val clickCandidates = buildList {
                    event.text.forEach { if (!it.isNullOrBlank()) add(it.toString()) }
                    event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
                    source?.text?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
                    source?.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                val clickMatch = InAppAdultGuard.findAdultClickMatch(
                    eventTexts = clickCandidates,
                    isSocialContext = InAppAdultGuard.isSocialOrCommunityUrl(detectedUrl?.value) ||
                        detectedUrl == null,
                    contentFilter = contentFilter,
                    whitelist = whitelistedKeywords
                )
                if (clickMatch != null) {
                    Log.w(TAG, "Instant block triggered by browser click: $clickMatch")
                    triggerBlock(clickMatch, packageName)
                    return
                }
            } else if (!contentFilter.matchesWhitelist(eventText, whitelistedKeywords) &&
                shouldBlockPageSnippet(eventText)
            ) {
                Log.w(TAG, "Instant block triggered by page snippet: $eventText")
                triggerBlock(eventText, packageName)
                return
            }
        }

        // Throttle expensive deep hierarchy scans during rapid content updates
        val now = System.currentTimeMillis()
        if (rootNode == null || (now - lastBrowserDeepScanMs) < DEEP_SCAN_THROTTLE_MS) {
            return
        }
        lastBrowserDeepScanMs = now

        // Fallback address-bar scan if direct IDs missed
        if (detectedUrl == null) {
            val fallbackUrl = BrowserInspector.extractUrlInfo(rootNode, event, allowFallbackTraversal = true)
            if (fallbackUrl != null) {
                if (fallbackUrl.isTrustedAddressBar &&
                    contentFilter.matchesWhitelist(fallbackUrl.value, whitelistedKeywords)
                ) {
                    return
                }
                if (shouldBlockUrlOrQuery(fallbackUrl.value)) {
                    Log.w(TAG, "Throttled fallback address bar match: ${fallbackUrl.value}")
                    triggerBlock(fallbackUrl.value, packageName)
                    return
                }
            }
        }

        // Single-pass bounded collection for custom blocklist + tiered adult page scoring
        val pageTexts = BrowserInspector.collectVisibleWebTexts(rootNode)
        if (pageTexts.isEmpty()) return

        for (txt in pageTexts) {
            if (!contentFilter.matchesWhitelist(txt, whitelistedKeywords) &&
                shouldBlockCustomOrSocialUrl(txt)
            ) {
                Log.w(TAG, "Deep scan custom/social match: $txt")
                triggerBlock(txt, packageName)
                return
            }
        }

        if (isAdultBlockingEnabled) {
            val scannableTexts = if (whitelistedKeywords.isEmpty()) {
                pageTexts
            } else {
                pageTexts.filterNot { contentFilter.matchesWhitelist(it, whitelistedKeywords) }
            }
            val matchedAdult = InAppAdultGuard.findAdultScreenMatch(
                packageName = packageName,
                activeUrl = detectedUrl?.value,
                visibleTexts = scannableTexts,
                isBrowser = true,
                contentFilter = contentFilter,
                whitelist = whitelistedKeywords
            )
            if (matchedAdult != null) {
                Log.w(TAG, "Tiered/social adult page match: $matchedAdult")
                triggerBlock(matchedAdult, packageName)
            }
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

    private fun shouldBlockCustomOrSocialUrl(input: String): Boolean {
        if (isShortsBlockingEnabled && isYouTubeShortsUrl(input)) return true
        if (isWhatsAppChannelsBlockingEnabled && isWhatsAppChannelUrl(input)) return true
        if (isInstagramReelsBlockingEnabled && isInstagramReelsUrl(input)) return true
        if (isTikTokBlockingEnabled && isTikTokUrl(input)) return true
        return contentFilter.matchesCustomBlocklist(input, customBlocklist)
    }

    private fun shouldBlockUrlOrQuery(input: String): Boolean {
        if (contentFilter.matchesWhitelist(input, whitelistedKeywords)) {
            return false
        }
        if (isAdultBlockingEnabled &&
            InAppAdultGuard.findAdultInputMatch(
                inputText = input,
                contentFilter = contentFilter,
                customBlocklist = emptyList(),
                whitelist = whitelistedKeywords
            ) != null
        ) {
            return true
        }
        return shouldBlockCustomOrSocialUrl(input)
    }

    private fun shouldBlockPageSnippet(input: String): Boolean {
        if (shouldBlockCustomOrSocialUrl(input)) return true
        return isAdultBlockingEnabled && contentFilter.findAdultPageContent(listOf(input)) != null
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

    private fun resolveSafeRedirectUrl(): String {
        val result = BlockedScreenConfig.validateRedirectUrl(
            raw = redirectUrl,
            contentFilter = contentFilter,
            customBlocklist = customBlocklist,
            blockAdultContent = isAdultBlockingEnabled,
            blockShorts = isShortsBlockingEnabled,
            blockWhatsAppChannels = isWhatsAppChannelsBlockingEnabled,
            blockInstagramReels = isInstagramReelsBlockingEnabled,
            blockTikTok = isTikTokBlockingEnabled
        )
        return (result as? BlockedScreenConfig.ValidationResult.Valid)?.normalized.orEmpty()
    }

    private fun maybeAutoResumeDnsVpn() {
        if (!BlockingPreferences.isDnsVpnEnabled) return
        val now = System.currentTimeMillis()
        if ((now - lastVpnWatchdogCheckMs) < VPN_WATCHDOG_INTERVAL_MS) return
        lastVpnWatchdogCheckMs = now
        try {
            if (!PGuardVpnService.isRunning.value && VpnService.prepare(this) == null) {
                PGuardVpnService.start(this)
                Log.i(TAG, "Watchdog resumed DNS Shield VPN service")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog could not resume DNS VPN service: ${e.message}")
        }
    }

    private fun triggerBlock(
        target: String,
        packageName: String,
        scopePrefix: String = "browser"
    ) {
        val now = System.currentTimeMillis()
        if (target == lastBlockedUrl && (now - lastBlockedTimestamp) < BLOCK_COOLDOWN_MS) {
            return
        }

        lastBlockedUrl = target
        lastBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        val scopeKey = "$scopePrefix:$packageName"
        Log.w(TAG, "BLOCKED restricted content detected: $target")

        overlayManager.showFullScreenOverlay(
            target = target,
            message = BlockedScreenConfig.resolveOverlayMessage(blockedScreenMessage, BlockedScreenConfig.DEFAULT_MESSAGE),
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = resolveSafeRedirectUrl(),
            onBeforeRedirect = {
                suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS)
            },
            onGoBack = {
                suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS)
                val backHandled = performGlobalAction(GLOBAL_ACTION_BACK)
                if (!backHandled) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } else {
                    mainHandler.postDelayed({
                        val activeRoot = rootInActiveWindow
                        val currentPkg = activeRoot?.packageName?.toString() ?: return@postDelayed
                        if (BrowserInspector.isBrowser(currentPkg, applicationContext)) {
                            val url = BrowserInspector.extractUrlInfo(
                                activeRoot,
                                null,
                                allowFallbackTraversal = false
                            )?.value
                            if (url != null && shouldBlockUrlOrQuery(url)) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                        } else if (scopePrefix == "inapp" && currentPkg == packageName && isAdultBlockingEnabled) {
                            val visibleTexts = BrowserInspector.collectVisibleWebTexts(
                                activeRoot,
                                maxDepth = 18,
                                maxItems = 80
                            )
                            if (visibleTexts.isNotEmpty() &&
                                InAppAdultGuard.findAdultScreenMatch(
                                    packageName = currentPkg,
                                    activeUrl = null,
                                    visibleTexts = visibleTexts,
                                    isBrowser = false,
                                    contentFilter = contentFilter,
                                    whitelist = whitelistedKeywords
                                ) != null
                            ) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                        }
                    }, 420L)
                }
            }
        )
    }

    private fun canRunSocialHierarchyScan(now: Long): Boolean {
        if ((now - lastSocialHierarchyScanMs) < DEEP_SCAN_THROTTLE_MS) return false
        lastSocialHierarchyScanMs = now
        return true
    }

    private fun inspectYouTube(event: AccessibilityEvent, packageName: String): Boolean {
        if (!isShortsBlockingEnabled || isScopeSuppressed("shorts:$packageName")) return false
        val now = System.currentTimeMillis()
        if (now - lastShortsBlockedTimestamp < SHORTS_COOLDOWN_MS) return false

        if (SocialAppGuards.isYouTubeShortsEventMatch(event)) {
            triggerShortsBlock(packageName)
            return true
        }

        if (!canRunSocialHierarchyScan(now)) return false
        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source ?: return false
        if (SocialAppGuards.isShortsActiveInHierarchy(rootNode)) {
            triggerShortsBlock(packageName)
            return true
        }
        return false
    }

    private fun triggerShortsBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastShortsBlockedTimestamp < SHORTS_COOLDOWN_MS) return
        lastShortsBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        val scopeKey = "shorts:$packageName"
        Log.w(TAG, "BLOCKED: YouTube Shorts detected, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "YouTube Shorts",
            message = BlockedScreenConfig.resolveOverlayMessage(blockedScreenMessage, "YouTube Shorts is blocked."),
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = resolveSafeRedirectUrl(),
            onBeforeRedirect = { suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS) },
            onGoBack = {
                suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS)
                if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        )
    }

    private fun inspectWhatsApp(event: AccessibilityEvent, packageName: String): Boolean {
        if (!isWhatsAppChannelsBlockingEnabled || isScopeSuppressed("whatsapp:$packageName")) return false
        val now = System.currentTimeMillis()
        if (now - lastWhatsAppBlockedTimestamp < WHATSAPP_COOLDOWN_MS) return false

        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source
        if (SocialAppGuards.isWhatsAppChannelClickMatch(event, rootNode)) {
            triggerWhatsAppBlock(packageName, "Exploring or following new WhatsApp channels is blocked.")
            return true
        }

        if (rootNode == null || !canRunSocialHierarchyScan(now)) return false
        if (SocialAppGuards.isWhatsAppChannelDirectoryActive(rootNode)) {
            triggerWhatsAppBlock(packageName, "Exploring new WhatsApp channels is blocked.")
            return true
        }
        return false
    }

    private fun triggerWhatsAppBlock(packageName: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastWhatsAppBlockedTimestamp < WHATSAPP_COOLDOWN_MS) return
        lastWhatsAppBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        val scopeKey = "whatsapp:$packageName"
        Log.w(TAG, "BLOCKED: $reason, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "WhatsApp Channels",
            message = BlockedScreenConfig.resolveOverlayMessage(blockedScreenMessage, reason),
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = resolveSafeRedirectUrl(),
            onBeforeRedirect = { suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS) },
            onGoBack = {
                suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS)
                if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        )
    }

    private fun triggerTikTokBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastTikTokBlockedTimestamp < TIKTOK_COOLDOWN_MS) return
        lastTikTokBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        val scopeKey = "tiktok:$packageName"
        Log.w(TAG, "BLOCKED: TikTok application opened, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "TikTok",
            message = BlockedScreenConfig.resolveOverlayMessage(blockedScreenMessage, "TikTok is completely blocked."),
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = resolveSafeRedirectUrl(),
            onBeforeRedirect = { suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS) },
            onGoBack = {
                suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    private fun inspectInstagram(event: AccessibilityEvent, packageName: String): Boolean {
        if (!isInstagramReelsBlockingEnabled || isScopeSuppressed("instagram:$packageName")) return false
        val now = System.currentTimeMillis()
        if (now - lastInstagramBlockedTimestamp < INSTAGRAM_COOLDOWN_MS) return false

        if (SocialAppGuards.isInstagramReelsEventMatch(event)) {
            triggerInstagramReelsBlock(packageName)
            return true
        }

        if (!canRunSocialHierarchyScan(now)) return false
        val rootNode = findRootNode(event) ?: rootInActiveWindow ?: event.source ?: return false
        if (SocialAppGuards.isInstagramReelsActiveInHierarchy(rootNode)) {
            triggerInstagramReelsBlock(packageName)
            return true
        }
        return false
    }

    private fun triggerInstagramReelsBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastInstagramBlockedTimestamp < INSTAGRAM_COOLDOWN_MS) return
        lastInstagramBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        val scopeKey = "instagram:$packageName"
        Log.w(TAG, "BLOCKED: Instagram Reels detected, showing full-screen overlay")
        overlayManager.showFullScreenOverlay(
            target = "Instagram Reels",
            message = BlockedScreenConfig.resolveOverlayMessage(blockedScreenMessage, "Instagram Reels are blocked."),
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = resolveSafeRedirectUrl(),
            onBeforeRedirect = { suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS) },
            onGoBack = {
                suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS)
                if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        )
    }

    private fun triggerUrgeLockdownBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastUrgeBlockedTimestamp < 1500L) return
        lastUrgeBlockedTimestamp = now
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

    private fun triggerFocusBlock(packageName: String, focusInfo: String) {
        val now = System.currentTimeMillis()
        if (now - lastFocusBlockedTimestamp < 1500L) return
        lastFocusBlockedTimestamp = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        HabitsManager.recordBlockInterception(applicationContext)
        if (overlayManager.isFullScreenShowing) return

        val appName = getAppLabelSafe(packageName)
        Log.w(TAG, "BLOCKED: Focus Mode Active. App $appName is blocked during $focusInfo")
        overlayManager.showFocusOverlay(
            appName = appName,
            scheduleInfo = focusInfo,
            onGoHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
        )
    }

    private fun triggerAppBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastAppBlockedTimestamp < 1500L) return
        lastAppBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        val appName = getAppLabelSafe(packageName)
        val scopeKey = "app:$packageName"
        Log.w(TAG, "BLOCKED: Blocked application accessed: $appName ($packageName)")
        overlayManager.showFullScreenOverlay(
            target = appName,
            message = BlockedScreenConfig.resolveOverlayMessage(blockedScreenMessage, "This app is in your Blocklist."),
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = resolveSafeRedirectUrl(),
            onBeforeRedirect = { suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS) },
            onGoBack = {
                suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    private fun triggerUnsupportedBrowserBlock(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastUnsupportedBrowserBlockedTimestamp < 1500L) return
        lastUnsupportedBrowserBlockedTimestamp = now
        HabitsManager.recordBlockInterception(applicationContext)

        val browserName = BrowserInspector.getBrowserDisplayName(packageName, applicationContext)
        val scopeKey = "unsupported_browser:$packageName"
        Log.w(TAG, "BLOCKED: Unsupported browser detected: $browserName ($packageName)")

        overlayManager.showFullScreenOverlay(
            target = browserName,
            message = "This browser isn't supported by PGuard. Switch to Chrome, Samsung Internet, Edge, Firefox, Brave, or another supported browser.",
            countdownSeconds = blockedScreenCountdown,
            redirectUrl = "",
            onBeforeRedirect = { suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS) },
            onGoBack = {
                suppressScope(scopeKey, SCOPED_DISMISS_COOLDOWN_MS)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
    }

    private fun inspectTamperAndUninstall(event: AccessibilityEvent): Boolean {
        if (!isUninstallProtectionEnabled) return false
        val pkg = event.packageName?.toString() ?: return false
        val rootNode = findRootNode(event) ?: return false

        if (AntiTamperGuard.shouldBlockTamper(
                pkg = pkg,
                root = rootNode,
                appPackageName = applicationContext.packageName,
                isAccessibilityServiceRunning = true
            )
        ) {
            triggerUninstallBlock()
            return true
        }
        return false
    }

    private fun triggerUninstallBlock() {
        val now = System.currentTimeMillis()
        if (now - lastAntiTamperBlockedTimestamp < 1500L) return
        lastAntiTamperBlockedTimestamp = now

        Log.w(TAG, "Anti-tamper: Detected attempt to uninstall or tamper with PGuard. Blocking and returning to Home.")
        SecurityAlertReporter.reportIfPartnerLinked(
            applicationContext,
            "uninstall_attempt",
            "An attempt to uninstall, force-stop, or disable PGuard protection was intercepted."
        )
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

    override fun onUnbind(intent: Intent?): Boolean {
        _isRunning.value = false
        overlayManager.hideAll()
        if (isUninstallProtectionEnabled) {
            SecurityAlertReporter.reportIfPartnerLinked(
                applicationContext,
                "permission_revoked",
                "PGuard Accessibility Service was disabled while Uninstall Protection was active."
            )
        }
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
