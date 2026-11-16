package com.example.pguard.engine.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object BrowserInspector {

    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.fenix.nightly",
        "org.mozilla.focus",
        "org.mozilla.klar",
        "org.mozilla.fennec_fdroid",
        "org.torproject.torbrowser",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.mi.globalbrowser",
        "com.heytap.browser",
        "com.UCMobile.intl",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.searchlite"
    )

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

    fun isBrowser(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg.contains("firefox") || pkg.contains("mozilla") || pkg.contains("fenix") ||
            pkg.contains("focus") || pkg.contains("fennec") || pkg.contains("klar")) {
            return true
        }
        if (pkg.contains("chrome") || pkg.contains("browser") || pkg.contains("opera") ||
            pkg.contains("edge") || pkg.contains("brave")) {
            return true
        }
        if (pkg == "com.google.android.googlequicksearchbox" || pkg == "com.google.android.apps.searchlite") {
            return true
        }
        return BROWSER_PACKAGES.contains(packageName)
    }

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
