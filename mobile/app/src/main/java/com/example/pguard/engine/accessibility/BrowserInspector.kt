package com.example.pguard.engine.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

object BrowserInspector {

    data class ExtractedUrl(
        val value: String,
        val isTrustedAddressBar: Boolean
    )

    data class CollectedScreenContent(
        val texts: List<String>,
        val viewIds: List<String>
    )

    /**
     * Verified supported browsers:
     * 1. Google Chrome
     * 2. Samsung Internet
     * 3. Mozilla Firefox
     * 4. Firefox Lite
     * 5. Brave Browser
     * 6. Microsoft Edge
     * 7. Opera
     * 8. DuckDuckGo
     * 9. Vivaldi
     * 10. Spin Safe Browser
     * 11. Ecosia
     * 12. Free Adblocker Browser
     * 13. 1DM (1DM & 1DM+)
     * 14. Web Video Cast
     */
    val SUPPORTED_BROWSER_NAMES = listOf(
        "Google Chrome",
        "Samsung Internet",
        "Mozilla Firefox",
        "Firefox Lite",
        "Brave Browser",
        "Microsoft Edge",
        "Opera Browser",
        "DuckDuckGo Privacy Browser",
        "Vivaldi Browser",
        "Spin Safe Browser",
        "Ecosia",
        "Free Adblocker Browser",
        "1DM / 1DM+ Browser",
        "Web Video Cast"
    )

    val SUPPORTED_BROWSER_PACKAGES = setOf(
        // 1. Chrome
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",

        // 2. Samsung Internet
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.sec.android.app.sbrowser.lite",

        // 3. Firefox
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.fenix.nightly",
        "org.mozilla.focus",
        "org.mozilla.klar",
        "org.mozilla.fennec_fdroid",

        // 4. Firefox Lite
        "org.mozilla.rocket",

        // 5. Brave
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",

        // 6. Microsoft Edge
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.canary",
        "com.microsoft.emmx.dev",

        // 7. Opera
        "com.opera.browser",
        "com.opera.browser.beta",
        "com.opera.gx",
        "com.opera.touch",

        // 8. DuckDuckGo
        "com.duckduckgo.mobile.android",

        // 9. Vivaldi
        "com.vivaldi.browser",
        "com.vivaldi.browser.snapshot",

        // 10. Spin Safe Browser
        "com.nationaledtech.spinbrowser",

        // 11. Ecosia
        "com.ecosia.android",

        // 12. Free Adblocker Browser
        "com.hsv.freeadblockerbrowser",
        "com.adblocker.browser",

        // 13. 1DM / 1DM+
        "idm.internet.download.manager",
        "idm.internet.download.manager.plus",
        "idm.internet.download.manager.lite",

        // 14. Web Video Cast
        "com.instantbits.cast.webvideo"
    )

    /**
     * Common known unsupported browser packages (including DNS-bypassing proxy/onion browsers)
     * for instant matching.
     */
    val KNOWN_UNSUPPORTED_BROWSER_PACKAGES = setOf(
        // Tor & Onion proxy browsers
        "org.torproject.torbrowser",
        "org.torproject.torbrowser_alpha",
        "org.torproject.torbrowser_nightly",
        "info.guardianproject.orfox",

        // Opera Mini (cloud proxy compression bypasses local DNS filtering)
        "com.opera.mini.native",
        "com.opera.mini.native.beta",
        "com.opera.mini.android",

        // UC Browser / UC Mini
        "com.ucmobile.intl",
        "com.uc.browser.en",
        "com.ucmobile",
        "com.uc.browser.hd",

        // Kiwi Browser
        "com.kiwibrowser.browser",
        "com.kiwibrowser.browser.dev",

        // Xiaomi / Mint Browser
        "com.mi.globalbrowser",
        "com.mi.globalbrowser.mini",
        "com.android.browser",
        "com.mint.browser",

        // Oppo / Vivo / Realme / ColorOS Browser
        "com.heytap.browser",
        "com.vivo.browser",
        "com.coloros.browser",

        // Dolphin Browser
        "mobi.mgeek.tunnybrowser",

        // Phoenix Browser
        "com.transsion.phoenix",

        // Puffin Browser
        "com.cloudmosa.puffinfree",
        "com.cloudmosa.puffin",

        // Aloha Browser
        "alohabrowser.browser",
        "alohabrowser.browser.lite",
        "com.alohamobile.browser",

        // Yandex Browser
        "com.yandex.browser",
        "com.yandex.browser.alpha",
        "com.yandex.browser.beta",

        // Huawei Browser
        "com.huawei.browser",

        // Privacy / Chromium / Gecko forks whose package IDs do not contain "browser"
        "org.bromite.bromite",
        "org.cromite.cromite",
        "net.waterfox.android.release",
        "io.github.forkmaintainers.iceraven",
        "org.gnu.icecat",
        "us.spotco.fennec_dos",
        "net.mullvad.mullvadbrowser",
        "com.naver.whale",
        "com.tencent.mtt",
        "com.oh.bro",
        "com.oh.brop",

        // Other generic & niche browsers
        "mark.via.gp",
        "com.mycompany.app.soulbrowser",
        "com.pure.browser",
        "com.quark.browser",
        "com.mx.browser",
        "com.xbrowser.play",
        "com.mmbox.xbrowser",
        "com.fevdev.nakedbrowser",
        "com.fevdev.nakedbrowserpro",
        "br.marcelo.monumentbrowser",
        "com.ghostery.android.ghostery",
        "com.ksmobile.cb",
        "com.fulldive.mobile",
        "com.chimbori.hermitcrab",
        "com.jio.web.pages"
    ).map { it.lowercase(Locale.ROOT) }.toSet()

    @Volatile
    private var cachedBrowserPackages: Set<String>? = null
    @Volatile
    private var lastBrowserCacheTime: Long = 0L

    fun getInstalledBrowsers(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        val cached = cachedBrowserPackages
        if (cached != null && now - lastBrowserCacheTime < 30_000L) {
            return cached
        }

        val browsers = mutableSetOf<String>()
        try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                PackageManager.MATCH_DEFAULT_ONLY
            }
            for (schemeUrl in listOf("https://example.com", "http://example.com")) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(schemeUrl)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                val resolved = pm.queryIntentActivities(intent, flags)
                for (ri in resolved) {
                    val pkg = ri.activityInfo?.packageName
                    if (!pkg.isNullOrBlank() && pkg != "android" && pkg != context.packageName) {
                        browsers.add(pkg.lowercase(Locale.ROOT))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BrowserInspector", "Failed to query browser packages: ${e.message}")
        }
        cachedBrowserPackages = browsers
        lastBrowserCacheTime = now
        return browsers
    }

    fun invalidateBrowserCache() {
        cachedBrowserPackages = null
        lastBrowserCacheTime = 0L
    }

    private val NON_WEB_BROWSER_TOKENS = setOf(
        "file", "files", "doc", "docs", "document", "documents",
        "media", "photo", "photos", "gallery", "image", "images",
        "music", "audio", "video", "videos", "font", "fonts",
        "sqlite", "database", "db", "ftp", "smb", "lan", "dlna", "upnp",
        "bluetooth", "nfc", "zip", "rar", "archive", "root",
        "explorer", "manager", "viewer", "reader", "network", "local",
        "log", "logs", "contacts", "package"
    )

    internal fun isLikelyWebBrowserPackageName(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        if (!pkg.contains("browser")) return false

        val prefixBeforeBrowser = pkg.substringBefore("browser").trimEnd('.', '_', '-')
        val tokens = pkg.split('.', '_', '-')
        if (tokens.any { it in NON_WEB_BROWSER_TOKENS }) return false
        if (NON_WEB_BROWSER_TOKENS.any { token ->
                prefixBeforeBrowser.endsWith(token) || pkg.contains("${token}browser")
            }
        ) {
            return false
        }

        return true
    }

    fun isSupportedBrowser(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        return pkg in SUPPORTED_BROWSER_PACKAGES
    }

    fun isUnsupportedBrowser(packageName: String, context: Context?): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        // Supported browsers are never unsupported
        if (isSupportedBrowser(pkg)) return false

        // Exclude system UI, launchers, settings, Google Search app, and PGuard
        if (pkg == "android" ||
            pkg.startsWith("com.android.systemui") ||
            pkg == "com.google.android.googlequicksearchbox" ||
            pkg == "com.google.android.apps.searchlite" ||
            pkg.contains("launcher") ||
            pkg.contains("accessibility") ||
            pkg.contains("settings") ||
            (context != null && pkg == context.packageName)
        ) {
            return false
        }

        // 1. Static check of known unsupported browser packages
        if (pkg in KNOWN_UNSUPPORTED_BROWSER_PACKAGES) return true

        // 2. Package naming pattern heuristic (rejecting non-web file/media/db browsers)
        if (isLikelyWebBrowserPackageName(pkg)) {
            return true
        }

        // 3. Dynamic check via PackageManager
        if (context != null) {
            val installed = getInstalledBrowsers(context)
            if (pkg in installed) {
                return true
            }
        }

        return false
    }

    fun isBrowser(packageName: String, context: Context? = null): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        if (isSupportedBrowser(pkg)) return true
        if (pkg in KNOWN_UNSUPPORTED_BROWSER_PACKAGES) return true
        if (pkg.contains("firefox") || pkg.contains("mozilla") || pkg.contains("fenix") ||
            pkg.contains("focus") || pkg.contains("fennec") || pkg.contains("klar") ||
            pkg.contains("chrome") || isLikelyWebBrowserPackageName(pkg) || pkg.contains("opera") ||
            pkg.contains("brave") || pkg.contains("sbrowser")) {
            return true
        }
        if (pkg == "com.google.android.googlequicksearchbox" || pkg == "com.google.android.apps.searchlite") {
            return true
        }
        if (context != null) {
            val installed = getInstalledBrowsers(context)
            if (pkg in installed) return true
        }
        return false
    }

    fun getBrowserDisplayName(packageName: String, context: Context?): String {
        if (context != null) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                if (label.isNotBlank()) return label
            } catch (_: Exception) {}
        }
        val lower = packageName.lowercase(Locale.ROOT)
        return when {
            lower.contains("sbrowser") -> "Samsung Internet"
            lower.contains("edge") || lower.contains("emmx") -> "Microsoft Edge"
            lower.contains("torproject") -> "Tor Browser"
            lower.contains("opera.mini") -> "Opera Mini"
            lower.contains("ucmobile") || lower.contains("uc.browser") -> "UC Browser"
            lower.contains("kiwi") -> "Kiwi Browser"
            lower.contains("mi.globalbrowser") || lower.contains("mint") -> "Mi Browser"
            lower.contains("heytap") || lower.contains("coloros") -> "HeyTap Browser"
            lower.contains("phoenix") -> "Phoenix Browser"
            lower.contains("puffin") -> "Puffin Browser"
            lower.contains("aloha") -> "Aloha Browser"
            lower.contains("yandex") -> "Yandex Browser"
            lower.contains("dolphin") || lower.contains("tunny") -> "Dolphin Browser"
            else -> packageName
        }
    }

    private val ADDRESS_BAR_VIEW_SUFFIXES = setOf(
        "mozac_browser_toolbar_url_view",
        "mozac_browser_toolbar_edit_url_view",
        "toolbar_display_url",
        "toolbar_url",
        "toolbar_url_view",
        "url_bar_title",
        "display_url",
        "url_bar",
        "url_view",
        "search_box_text",
        "location_bar_edit_text",
        "location_bar",
        "control_url",
        "custom_url_bar",
        "omnibartextinput",
        "url_field",
        "search_edit_text",
        "url_text",
        "address_bar"
    )

    private val GENERIC_FALLBACK_SUFFIXES = listOf(
        "url_bar",
        "location_bar_edit_text",
        "mozac_browser_toolbar_url_view",
        "address_bar",
        "url_field",
        "url_text"
    )

    private val ACTION_VIEW_ID_MARKERS = listOf(
        "menu", "tab", "button", "action", "overflow", "share"
    )

    /**
     * Maps a browser package directly to its 1–4 known address-bar view resource IDs,
     * avoiding expensive cross-package IPC probing across 19+ guesses per event.
     */
    fun addressBarIdsForPackage(packageName: String): List<String> {
        val rawPkg = packageName.trim()
        if (rawPkg.isBlank()) return emptyList()
        val lower = rawPkg.lowercase(Locale.ROOT)

        val suffixes = when {
            lower == "com.android.chrome" || lower.startsWith("com.chrome.") ||
                lower.startsWith("com.brave.browser") ||
                lower.startsWith("com.microsoft.emmx") ||
                lower.startsWith("com.vivaldi.browser") ||
                lower == "com.ecosia.android" -> listOf("url_bar")

            lower.startsWith("com.sec.android.app.sbrowser") ->
                listOf("location_bar_edit_text", "url_bar_title")

            lower.startsWith("org.mozilla.") || lower.startsWith("org.torproject.") ->
                listOf(
                    "mozac_browser_toolbar_url_view",
                    "mozac_browser_toolbar_edit_url_view",
                    "display_url",
                    "url_view"
                )

            lower.startsWith("com.opera.") ->
                listOf("url_field", "url_bar")

            lower == "com.duckduckgo.mobile.android" ->
                listOf("omnibarTextInput")

            lower == "com.nationaledtech.spinbrowser" ->
                listOf("mozac_browser_toolbar_url_view", "url_bar")

            lower == "com.hsv.freeadblockerbrowser" || lower == "com.adblocker.browser" ->
                listOf("url_bar", "address_bar")

            lower.startsWith("idm.internet.download.manager") ->
                listOf("url_bar", "search_edit_text", "url_text")

            lower == "com.instantbits.cast.webvideo" ->
                listOf("url_text", "address_bar", "url_bar")

            lower == "com.google.android.googlequicksearchbox" ||
                lower == "com.google.android.apps.searchlite" ->
                listOf("search_box_text", "url_bar")

            else -> GENERIC_FALLBACK_SUFFIXES
        }

        return suffixes.map { suffix -> "$rawPkg:id/$suffix" }
    }

    fun isLikelyAddressBarViewId(viewId: String?): Boolean {
        if (viewId.isNullOrBlank()) return false
        val lower = viewId.lowercase(Locale.ROOT)
        if (ACTION_VIEW_ID_MARKERS.any { lower.contains(it) }) return false
        val resourceSuffix = lower.substringAfter(":id/", lower)
        return resourceSuffix in ADDRESS_BAR_VIEW_SUFFIXES ||
            lower.endsWith("url_view") ||
            lower.endsWith("edit_url_view") ||
            lower.endsWith("display_url") ||
            lower.endsWith("url_bar") ||
            lower.endsWith("address_bar") ||
            lower.endsWith("url_text") ||
            lower.endsWith("url_field") ||
            lower.endsWith("search_box_text") ||
            lower.endsWith("location_bar_edit_text") ||
            lower.contains("toolbar_url") ||
            lower.contains("awesomebar") ||
            lower.contains("omnibar")
    }

    fun isAddressBarSource(viewId: String?, className: String?): Boolean {
        val lowerId = viewId?.lowercase(Locale.ROOT).orEmpty()
        if (ACTION_VIEW_ID_MARKERS.any { lowerId.contains(it) }) return false
        if (isLikelyAddressBarViewId(viewId)) return true
        return className?.contains("EditText", ignoreCase = true) == true
    }

    private val IGNORED_PLACEHOLDERS = setOf(
        "search or enter address",
        "search or enter web address",
        "search or type url",
        "search or type web address",
        "search or type a url",
        "search the web",
        "type a url",
        "new tab",
        "firefox",
        "address and search bar",
        "home",
        "navigation bar",
        "menu",
        "tabs",
        "open tabs",
        "tab",
        "back",
        "forward",
        "reload",
        "refresh",
        "stop",
        "more options",
        "share",
        "bookmark",
        "bookmarks",
        "close",
        "cancel"
    )

    /**
     * Extracts active URL or search-bar text with provenance metadata (`isTrustedAddressBar`).
     *
     * Only verified address-bar or EditText nodes are marked trusted; arbitrary links on a web
     * page are never treated as the active URL so they cannot bypass the whitelist.
     */
    fun extractUrlInfo(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        allowFallbackTraversal: Boolean = false
    ): ExtractedUrl? {
        val packageName = event?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: rootNode?.packageName?.toString().orEmpty()

        // 1. Direct lookup using the browser's exact address-bar resource IDs
        if (rootNode != null && packageName.isNotBlank()) {
            for (fullId in addressBarIdsForPackage(packageName)) {
                val nodes = try {
                    rootNode.findAccessibilityNodeInfosByViewId(fullId)
                } catch (_: Exception) {
                    emptyList()
                }
                for (node in nodes) {
                    val text = getNodeText(node)
                    if (!text.isNullOrBlank() && !isIgnoredPlaceholder(text)) {
                        return ExtractedUrl(text, isTrustedAddressBar = true)
                    }
                    for (c in 0 until node.childCount) {
                        val child = node.getChild(c) ?: continue
                        val childText = getNodeText(child)
                        if (!childText.isNullOrBlank() && !isIgnoredPlaceholder(childText)) {
                            return ExtractedUrl(childText, isTrustedAddressBar = true)
                        }
                    }
                }
            }
        }

        // 2. Direct event source check (for address bar / EditText edits before tree updates)
        val source = event?.source
        if (source != null && isAddressBarSource(source.viewIdResourceName, source.className?.toString())) {
            val srcText = getNodeText(source)
            if (!srcText.isNullOrBlank() && !isIgnoredPlaceholder(srcText)) {
                return ExtractedUrl(srcText, isTrustedAddressBar = true)
            }
        }

        // 3. Throttled fallback tree traversal for verified address-bar / EditText nodes only
        if (allowFallbackTraversal && rootNode != null) {
            val fallbackUrl = findAddressBarTextFallback(rootNode, depth = 0, maxDepth = 18)
            if (!fallbackUrl.isNullOrBlank() && !isIgnoredPlaceholder(fallbackUrl)) {
                return ExtractedUrl(fallbackUrl, isTrustedAddressBar = true)
            }
        }

        return null
    }

    /**
     * Backwards-compatible wrapper returning only the trusted address-bar URL string.
     */
    fun extractUrl(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?): String? =
        extractUrlInfo(rootNode, event, allowFallbackTraversal = false)?.value

    /**
     * Multi-window BFS collector capturing visible text, content descriptions, state descriptions,
     * pane titles, tooltips, and view resource IDs across activity windows, dialogs, and shadow DOMs.
     * Prioritizes visible-to-user text first so hidden navigation drawers do not crowd out page results.
     */
    fun collectVisibleWindowContent(
        roots: List<AccessibilityNodeInfo>,
        maxDepth: Int = 64,
        maxItems: Int = 450,
        maxVisitedNodes: Int = 2500
    ): CollectedScreenContent {
        if (roots.isEmpty()) return CollectedScreenContent(emptyList(), emptyList())
        val visibleTexts = LinkedHashSet<String>()
        val fallbackTexts = LinkedHashSet<String>()
        val viewIdsOut = LinkedHashSet<String>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        var visited = 0

        for (root in roots.distinct()) {
            queue.addLast(root to 0)
        }

        while (queue.isNotEmpty() && visited < maxVisitedNodes) {
            if (visibleTexts.size >= maxItems && viewIdsOut.size >= maxItems) break
            val (node, depth) = queue.removeFirst()
            visited++

            val viewId = node.viewIdResourceName?.trim()
            if (!viewId.isNullOrBlank() && viewIdsOut.size < maxItems) {
                viewIdsOut.add(viewId)
            }

            val isVisible = try {
                node.isVisibleToUser
            } catch (_: Exception) {
                true
            }
            val textBucket = if (isVisible) visibleTexts else fallbackTexts

            fun recordText(raw: CharSequence?) {
                val value = raw?.toString()?.trim()
                if (!value.isNullOrBlank() && !isIgnoredPlaceholder(value) && textBucket.size < maxItems) {
                    textBucket.add(value)
                }
            }

            recordText(node.text)
            recordText(node.contentDescription)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                recordText(node.stateDescription)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                recordText(node.paneTitle)
                recordText(node.tooltipText)
            }

            if (depth < maxDepth) {
                val count = node.childCount
                for (i in 0 until count) {
                    val child = node.getChild(i) ?: continue
                    queue.addLast(child to (depth + 1))
                }
            }
        }

        val combinedTexts = LinkedHashSet<String>(visibleTexts.size + fallbackTexts.size)
        for (text in visibleTexts) {
            if (combinedTexts.size >= maxItems) break
            combinedTexts.add(text)
        }
        if (combinedTexts.size < maxItems) {
            for (text in fallbackTexts) {
                if (combinedTexts.size >= maxItems) break
                combinedTexts.add(text)
            }
        }
        return CollectedScreenContent(combinedTexts.toList(), viewIdsOut.toList())
    }

    /**
     * Collects text, content descriptions, and state descriptions from a tapped container
     * node and its immediate children (e.g., clickable Reddit post or search result rows).
     */
    fun collectSubtreeTexts(
        node: AccessibilityNodeInfo?,
        maxDepth: Int = 6,
        maxItems: Int = 25
    ): List<String> {
        if (node == null) return emptyList()
        return collectVisibleWindowContent(listOf(node), maxDepth = maxDepth, maxItems = maxItems).texts
    }

    /**
     * Single-pass bounded collector of visible browser texts so callers can evaluate
     * blocklists and multi-signal adult scoring without redundant IPC lookups.
     */
    fun collectVisibleWebTexts(
        rootNode: AccessibilityNodeInfo?,
        maxDepth: Int = 64,
        maxItems: Int = 450
    ): List<String> {
        if (rootNode == null) return emptyList()
        return collectVisibleWindowContent(listOf(rootNode), maxDepth = maxDepth, maxItems = maxItems).texts
    }

    /**
     * Deeply scans the browser window for any node whose text or contentDescription
     * matches restricted content (e.g. adult keywords/domains), acting as a fail-safe.
     */
    fun findRestrictedContent(rootNode: AccessibilityNodeInfo?, isRestricted: (String) -> Boolean): String? {
        if (rootNode == null) return null
        return scanForRestrictedNode(rootNode, isRestricted, depth = 0)
    }

    fun findRestrictedNodeWithInfo(
        rootNode: AccessibilityNodeInfo?,
        isRestricted: (String) -> Boolean
    ): Pair<AccessibilityNodeInfo, String>? {
        if (rootNode == null) return null
        return scanForRestrictedNodeInfo(rootNode, isRestricted, depth = 0)
    }

    private fun scanForRestrictedNodeInfo(
        node: AccessibilityNodeInfo?,
        isRestricted: (String) -> Boolean,
        depth: Int
    ): Pair<AccessibilityNodeInfo, String>? {
        if (node == null || depth > 40) return null

        val text = node.text?.toString()
        if (!text.isNullOrBlank() && !isIgnoredPlaceholder(text) && isRestricted(text)) {
            return Pair(node, text)
        }

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank() && !isIgnoredPlaceholder(desc) && isRestricted(desc)) {
            return Pair(node, desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = scanForRestrictedNodeInfo(child, isRestricted, depth + 1)
            if (found != null) return found
        }

        return null
    }

    private fun findAddressBarTextFallback(
        node: AccessibilityNodeInfo?,
        depth: Int,
        maxDepth: Int
    ): String? {
        if (node == null || depth > maxDepth) return null

        val viewId = node.viewIdResourceName
        val className = node.className?.toString()
        val text = getNodeText(node)

        if (!text.isNullOrBlank() && isAddressBarSource(viewId, className) && !isIgnoredPlaceholder(text)) {
            return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findAddressBarTextFallback(child, depth + 1, maxDepth)
            if (result != null) return result
        }

        return null
    }

    private fun scanForRestrictedNode(
        node: AccessibilityNodeInfo?,
        isRestricted: (String) -> Boolean,
        depth: Int
    ): String? {
        if (node == null || depth > 40) return null

        val text = node.text?.toString()
        if (!text.isNullOrBlank() && !isIgnoredPlaceholder(text) && isRestricted(text)) {
            return text
        }

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank() && !isIgnoredPlaceholder(desc) && isRestricted(desc)) {
            return desc
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = scanForRestrictedNode(child, isRestricted, depth + 1)
            if (found != null) return found
        }

        return null
    }

    private fun getNodeText(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) return text

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) return desc

        return null
    }

    private fun isIgnoredPlaceholder(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        return lower in IGNORED_PLACEHOLDERS ||
            lower.isEmpty() ||
            lower.startsWith("switch to tab") ||
            lower.endsWith("tabs") ||
            lower.endsWith("open tabs")
    }
}
