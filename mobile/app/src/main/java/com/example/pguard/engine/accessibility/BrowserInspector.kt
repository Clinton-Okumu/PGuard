package com.example.pguard.engine.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object BrowserInspector {

    /**
     * Verified supported browsers:
     * 1. Google Chrome
     * 2. Samsung Internet
     * 3. Mozilla Firefox
     * 4. Firefox Lite
     * 5. Brave Browser
     * 6. Opera
     * 7. Opera Mini
     * 8. DuckDuckGo
     * 9. Vivaldi
     * 10. Tor Browser
     * 11. Spin Safe Browser
     * 12. Ecosia
     * 13. Free Adblocker Browser
     * 14. 1DM (1DM & 1DM+)
     * 15. Web Video Cast
     */
    val SUPPORTED_BROWSER_NAMES = listOf(
        "Google Chrome",
        "Samsung Internet",
        "Mozilla Firefox",
        "Firefox Lite",
        "Brave Browser",
        "Opera Browser",
        "Opera Mini",
        "DuckDuckGo Privacy Browser",
        "Vivaldi Browser",
        "Tor Browser",
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

        // 6. Opera
        "com.opera.browser",
        "com.opera.browser.beta",
        "com.opera.gx",
        "com.opera.touch",

        // 7. Opera Mini
        "com.opera.mini.native",
        "com.opera.mini.native.beta",

        // 8. DuckDuckGo
        "com.duckduckgo.mobile.android",

        // 9. Vivaldi
        "com.vivaldi.browser",
        "com.vivaldi.browser.snapshot",

        // 10. Tor Browser
        "org.torproject.torbrowser",
        "org.torproject.torbrowser_alpha",

        // 11. Spin Safe Browser
        "com.nationaledtech.spinbrowser",

        // 12. Ecosia
        "com.ecosia.android",

        // 13. Free Adblocker Browser
        "com.hsv.freeadblockerbrowser",
        "com.adblocker.browser",

        // 14. 1DM / 1DM+
        "idm.internet.download.manager",
        "idm.internet.download.manager.plus",
        "idm.internet.download.manager.lite",

        // 15. Web Video Cast
        "com.instantbits.cast.webvideo"
    )

    /**
     * Common known unsupported browser packages for instant matching.
     */
    val KNOWN_UNSUPPORTED_BROWSER_PACKAGES = setOf(
        // Microsoft Edge
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.canary",
        "com.microsoft.emmx.dev",

        // UC Browser / UC Mini
        "com.ucmobile.intl",
        "com.uc.browser.en",
        "com.ucmobile",
        "com.uc.browser.hd",

        // Kiwi Browser
        "com.kiwibrowser.browser",

        // Xiaomi / Mint Browser
        "com.mi.globalbrowser",
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

        // Yandex Browser
        "com.yandex.browser",
        "com.yandex.browser.alpha",
        "com.yandex.browser.beta",

        // Huawei Browser
        "com.huawei.browser",

        // Other generic & niche browsers
        "mark.via.gp",
        "com.mycompany.app.soulbrowser",
        "com.pure.browser",
        "br.marcelo.monumentbrowser",
        "com.ghostery.android.ghostery",
        "com.ksmobile.cb",
        "com.fulldive.mobile",
        "com.chimbori.hermitcrab",
        "com.jio.web.pages"
    ).map { it.lowercase() }.toSet()

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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                PackageManager.MATCH_DEFAULT_ONLY
            }
            val resolved = pm.queryIntentActivities(intent, flags)
            for (ri in resolved) {
                val pkg = ri.activityInfo?.packageName
                if (!pkg.isNullOrBlank() && pkg != "android" && pkg != context.packageName) {
                    browsers.add(pkg.lowercase())
                }
            }
        } catch (e: Exception) {
            Log.w("BrowserInspector", "Failed to query browser packages: ${e.message}")
        }
        cachedBrowserPackages = browsers
        lastBrowserCacheTime = now
        return browsers
    }

    fun isSupportedBrowser(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg in SUPPORTED_BROWSER_PACKAGES
    }

    fun isUnsupportedBrowser(packageName: String, context: Context?): Boolean {
        val pkg = packageName.lowercase()
        // Supported browsers are never unsupported
        if (isSupportedBrowser(pkg)) return false

        // Exclude system UI, launchers, settings, and PGuard
        if (pkg == "android" ||
            pkg.startsWith("com.android.systemui") ||
            pkg.contains("launcher") ||
            pkg.contains("accessibility") ||
            pkg.contains("settings") ||
            (context != null && pkg == context.packageName)
        ) {
            return false
        }

        // 1. Static check of known unsupported browser packages
        if (pkg in KNOWN_UNSUPPORTED_BROWSER_PACKAGES) return true

        // 2. Package naming pattern heuristic
        if (pkg.contains("browser") ||
            pkg.contains("webbrowser") ||
            pkg.contains("internet.browser") ||
            pkg.endsWith(".browser")
        ) {
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
        val pkg = packageName.lowercase()
        if (isSupportedBrowser(pkg)) return true
        if (pkg in KNOWN_UNSUPPORTED_BROWSER_PACKAGES) return true
        if (pkg.contains("firefox") || pkg.contains("mozilla") || pkg.contains("fenix") ||
            pkg.contains("focus") || pkg.contains("fennec") || pkg.contains("klar") ||
            pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("opera") ||
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
        val lower = packageName.lowercase()
        return when {
            lower.contains("sbrowser") -> "Samsung Internet"
            lower.contains("edge") || lower.contains("emmx") -> "Microsoft Edge"
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

    private val ADDRESS_BAR_ID_SUFFIXES = listOf(
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
        "omnibarTextInput",
        "url_field",
        "search_edit_text",
        "url_text",
        "address_bar"
    )

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
     * Extracts active URL or search text from event and node hierarchy.
     */
    fun extractUrl(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?): String? {
        val packageName = event?.packageName?.toString() ?: ""

        // 1. Direct lookup by address bar view IDs (both dynamic package-prefixed & suffixes)
        if (rootNode != null) {
            val candidatePackages = mutableListOf<String>()
            if (packageName.isNotBlank()) candidatePackages.add(packageName)
            if (packageName.contains("firefox", ignoreCase = true) || packageName.contains("mozilla", ignoreCase = true)) {
                candidatePackages.add("org.mozilla.firefox")
                candidatePackages.add("org.mozilla.fenix")
            }

            for (pkg in candidatePackages) {
                for (suffix in ADDRESS_BAR_ID_SUFFIXES) {
                    val fullId = "$pkg:id/$suffix"
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(fullId)
                    for (node in nodes) {
                        val text = getNodeText(node)
                        if (!text.isNullOrBlank() && !isIgnoredPlaceholder(text)) {
                            return text
                        }
                        // Check immediate children if container
                        for (c in 0 until node.childCount) {
                            val child = node.getChild(c) ?: continue
                            val childText = getNodeText(child)
                            if (!childText.isNullOrBlank() && !isIgnoredPlaceholder(childText)) {
                                return childText
                            }
                        }
                    }
                }
            }
        }

        // 2. Traversal through candidate address bar / toolbar nodes
        val fallbackUrl = findAddressBarTextFallback(rootNode, depth = 0)
        if (!fallbackUrl.isNullOrBlank() && !isIgnoredPlaceholder(fallbackUrl)) {
            return fallbackUrl
        }

        // 3. Check event text (only if it looks like a URL or search query, not placeholder)
        if (event != null && event.text.isNotEmpty()) {
            for (charSeq in event.text) {
                val str = charSeq?.toString()
                if (!str.isNullOrBlank() && !isIgnoredPlaceholder(str) &&
                    (str.contains('.') || str.contains('/') || str.contains(' '))) {
                    return str
                }
            }
        }

        return null
    }

    /**
     * Deeply scans the browser window for any node whose text or contentDescription
     * matches restricted content (e.g. adult keywords/domains), acting as a fail-safe.
     */
    fun findRestrictedContent(rootNode: AccessibilityNodeInfo?, isRestricted: (String) -> Boolean): String? {
        if (rootNode == null) return null
        return scanForRestrictedNode(rootNode, isRestricted, depth = 0)
    }

    private fun findAddressBarTextFallback(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 35) return null

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = getNodeText(node)

        // Exclude standard action buttons from being mistaken for the address bar
        val isActionButton = viewId.contains("menu") ||
                viewId.contains("tab") ||
                viewId.contains("button") ||
                viewId.contains("action") ||
                viewId.contains("overflow") ||
                viewId.contains("share")

        val isLikelyAddressBar = !isActionButton && (
                viewId.endsWith("url_view") ||
                viewId.endsWith("edit_url_view") ||
                viewId.endsWith("display_url") ||
                viewId.endsWith("url_bar") ||
                viewId.endsWith("address_bar") ||
                viewId.endsWith("url_text") ||
                viewId.endsWith("search_box_text") ||
                viewId.endsWith("location_bar_edit_text") ||
                viewId.contains("omnibar") ||
                node.className?.toString()?.contains("EditText", ignoreCase = true) == true
        )

        if (!text.isNullOrBlank()) {
            if (isLikelyAddressBar && !isIgnoredPlaceholder(text)) {
                return text
            }
            // Even without explicit address bar viewId, if it starts with http or looks like a domain:
            val trimmed = text.trim()
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                (trimmed.contains('.') && !trimmed.contains(' ') && trimmed.length > 4)) {
                if (!isIgnoredPlaceholder(trimmed)) {
                    return trimmed
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findAddressBarTextFallback(child, depth + 1)
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
        val lower = text.trim().lowercase()
        return lower in IGNORED_PLACEHOLDERS ||
                lower.isEmpty() ||
                lower.startsWith("switch to tab") ||
                lower.endsWith("tabs") ||
                lower.endsWith("open tabs")
    }
}
