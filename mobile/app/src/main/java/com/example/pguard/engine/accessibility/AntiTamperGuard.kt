package com.example.pguard.engine.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Anti-tamper and uninstall protection inspector.
 *
 * Pure-text evaluation helpers are Android-free so they can be verified directly
 * on the JVM without Robolectric.
 */
object AntiTamperGuard {

    private val PERMISSION_SCREEN_PHRASES = listOf(
        "display over other apps",
        "appear on top",
        "display pop-up",
        "pop-up window",
        "draw over other apps",
        "special app access",
        "allow display",
        "permit drawing",
        "allow notifications",
        "notification access",
        "battery optimization",
        "ignore battery optimization",
        "unrestricted",
        "autostart",
        "activate this device admin",
        "activate admin",
        "permission manager",
        "app permissions",
        "modify system settings"
    )

    private val DESTRUCTIVE_KEYWORDS = listOf(
        "uninstall",
        "force stop",
        "clear storage",
        "clear data",
        "deactivate",
        "turn off",
        "stop service"
    )

    private val APP_INFO_DESTRUCTIVE_MARKERS = listOf(
        "uninstall",
        "force stop",
        "clear storage",
        "clear data"
    )

    fun isTamperPackage(pkg: String): Boolean {
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

    fun containsPGuardReference(text: String?, appPackageName: String): Boolean {
        if (text.isNullOrBlank()) return false
        return text.contains("PGuard", ignoreCase = true) ||
            (appPackageName.isNotBlank() && text.contains(appPackageName, ignoreCase = true))
    }

    fun isPermissionConfigurationTexts(texts: Collection<String>): Boolean {
        if (texts.isEmpty()) return false
        val lowered = texts.map { it.lowercase() }

        // Never treat App Info or confirmation dialogs containing Uninstall/Force Stop/Clear Data
        // as harmless permission screens even if "Notifications" or "Battery" rows are visible.
        val hasDestructiveMarker = lowered.any { t ->
            APP_INFO_DESTRUCTIVE_MARKERS.any { marker -> t.contains(marker) }
        }
        if (hasDestructiveMarker) return false

        return lowered.any { t ->
            PERMISSION_SCREEN_PHRASES.any { phrase -> t.contains(phrase) }
        }
    }

    fun isDestructiveTamperTexts(texts: Collection<String>): Boolean {
        if (texts.isEmpty()) return false
        return texts.any { raw ->
            val lower = raw.lowercase()
            !lower.contains("allow") &&
                DESTRUCTIVE_KEYWORDS.any { keyword -> lower.contains(keyword) }
        }
    }

    fun shouldBlockScreenTexts(
        pkg: String,
        appPackageName: String,
        visibleTexts: Collection<String>
    ): Boolean {
        if (visibleTexts.isEmpty()) return false

        val isTargeted = visibleTexts.any { containsPGuardReference(it, appPackageName) }
        if (!isTargeted) return false

        val lowerPkg = pkg.lowercase()
        val isInstaller = lowerPkg.contains("packageinstaller") || lowerPkg.contains("vending")
        if (isInstaller && visibleTexts.any { it.contains("uninstall", ignoreCase = true) }) {
            return true
        }

        if (isPermissionConfigurationTexts(visibleTexts)) {
            return false
        }

        return isDestructiveTamperTexts(visibleTexts)
    }

    fun shouldBlockTamper(
        pkg: String,
        root: AccessibilityNodeInfo?,
        appPackageName: String
    ): Boolean {
        if (root == null) return false
        val texts = collectVisibleTexts(root)
        return shouldBlockScreenTexts(pkg, appPackageName, texts)
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo?,
        depth: Int = 0,
        out: MutableList<String> = mutableListOf()
    ): List<String> {
        if (node == null || depth > 16 || out.size > 250) return out
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectVisibleTexts(child, depth + 1, out)
            if (out.size > 250) break
        }
        return out
    }
}
