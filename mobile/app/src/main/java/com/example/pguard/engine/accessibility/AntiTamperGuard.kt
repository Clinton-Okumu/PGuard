package com.example.pguard.engine.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

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
        "modify system settings",
        "connection request",
        "set up a vpn",
        "wants to set up a vpn",
        "vpn connection"
    )

    private val DESTRUCTIVE_KEYWORDS = listOf(
        // English
        "uninstall",
        "force stop",
        "clear storage",
        "clear data",
        "deactivate",
        "turn off",
        "stop service",
        "stop pguard",
        "disable",
        "disconnect",
        "forget vpn",
        "delete vpn",
        "revoke",
        // Spanish
        "desinstalar",
        "forzar detención",
        "borrar datos",
        "borrar almacenamiento",
        "desactivar",
        "detener",
        // French
        "désinstaller",
        "forcer l'arrêt",
        "effacer les données",
        "désactiver",
        "arrêter",
        // German
        "deinstallieren",
        "beenden erzwingen",
        "daten löschen",
        "deaktivieren",
        "stoppen",
        // Portuguese
        "forçar parada",
        "limpar dados",
        "parar",
        // Swahili
        "sanidua",
        "lazimisha kusimama",
        "futa data",
        "zima",
        "simamisha",
        // Indonesian
        "copot pemasangan",
        "paksa berhenti",
        "hapus data",
        "nonaktifkan",
        // Turkish
        "kaldır",
        "durmaya zorla",
        "verileri temizle",
        "devre dışı bırak"
    )

    private val STANDALONE_STOP_BUTTONS = setOf(
        "stop",
        "stop app",
        "remove",
        "desinstalar",
        "detener",
        "arrêter",
        "stoppen",
        "parar",
        "simamisha",
        "kaldır"
    )

    private val APP_INFO_DESTRUCTIVE_MARKERS = listOf(
        "uninstall",
        "force stop",
        "clear storage",
        "clear data",
        "stop pguard",
        "stop service",
        "deactivate",
        "desinstalar",
        "forzar detención",
        "desactivar",
        "désinstaller",
        "forcer l'arrêt",
        "désactiver",
        "deinstallieren",
        "beenden erzwingen",
        "deaktivieren",
        "forçar parada",
        "sanidua",
        "copot pemasangan",
        "nonaktifkan",
        "kaldır",
        "devre dışı bırak"
    )

    private val DESTRUCTIVE_VIEW_ID_MARKERS = listOf(
        "uninstall_button",
        "force_stop_button",
        "clear_data_button",
        "device_admin_deactivate"
    )

    private val ACCESSIBILITY_CONTROL_PHRASES = listOf(
        "use pguard",
        "use service",
        "downloaded services",
        "installed services",
        "installed apps",
        "accessibility",
        "shortcut",
        "stop pguard",
        "usar servicio",
        "utiliser le service",
        "dienst verwenden"
    )

    fun isTamperPackage(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.ROOT)
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
        val lowered = texts.map { it.lowercase(Locale.ROOT) }

        // Never treat App Info or confirmation dialogs containing Uninstall/Force Stop/Clear Data/Deactivate
        // as harmless permission screens even if "Notifications" or "Battery" rows are visible.
        val hasDestructiveMarker = lowered.any { t ->
            APP_INFO_DESTRUCTIVE_MARKERS.any { marker -> t.contains(marker) }
        }
        if (hasDestructiveMarker) return false

        return lowered.any { t ->
            PERMISSION_SCREEN_PHRASES.any { phrase ->
                t.contains(phrase) && (!phrase.contains("activate") || !t.contains("deactivate"))
            }
        }
    }

    fun isDestructiveTamperTexts(texts: Collection<String>): Boolean {
        if (texts.isEmpty()) return false
        return texts.any { raw ->
            val lower = raw.trim().lowercase(Locale.ROOT)
            if (lower in STANDALONE_STOP_BUTTONS) return@any true
            !lower.contains("allow") &&
                DESTRUCTIVE_KEYWORDS.any { keyword -> lower.contains(keyword) }
        }
    }

    fun shouldBlockScreenTexts(
        pkg: String,
        appPackageName: String,
        visibleTexts: Collection<String>,
        isAccessibilityServiceRunning: Boolean = false,
        visibleViewIds: Collection<String> = emptyList()
    ): Boolean {
        if (visibleTexts.isEmpty()) return false

        val isTargeted = visibleTexts.any { containsPGuardReference(it, appPackageName) }
        if (!isTargeted) return false

        val lowerPkg = pkg.lowercase(Locale.ROOT)
        val isInstaller = lowerPkg.contains("packageinstaller") || lowerPkg.contains("vending")
        if (isInstaller && visibleTexts.any { t ->
                val l = t.lowercase(Locale.ROOT)
                l.contains("uninstall") || l.contains("desinstalar") ||
                    l.contains("désinstaller") || l.contains("deinstallieren") ||
                    l.contains("sanidua") || l.contains("kaldır")
            }
        ) {
            return true
        }

        val loweredIds = visibleViewIds.map { it.lowercase(Locale.ROOT) }
        if (loweredIds.any { id -> DESTRUCTIVE_VIEW_ID_MARKERS.any { id.contains(it) } }) {
            return true
        }

        if (isPermissionConfigurationTexts(visibleTexts)) {
            return false
        }

        if (isAccessibilityServiceRunning) {
            val loweredTexts = visibleTexts.map { it.lowercase(Locale.ROOT) }
            val hasAccessibilityControls = loweredTexts.any { text ->
                ACCESSIBILITY_CONTROL_PHRASES.any { phrase -> text.contains(phrase) }
            }
            val hasSwitchWidget = loweredIds.any { id ->
                id.contains("switch_widget") || id.contains("switchbar") || id.contains("switch_bar")
            }
            if (hasAccessibilityControls || hasSwitchWidget) {
                return true
            }
        }

        return isDestructiveTamperTexts(visibleTexts)
    }

    fun shouldBlockTamper(
        pkg: String,
        root: AccessibilityNodeInfo?,
        appPackageName: String,
        isAccessibilityServiceRunning: Boolean = true
    ): Boolean {
        if (root == null) return false
        val texts = mutableListOf<String>()
        val viewIds = mutableListOf<String>()
        collectVisibleInfo(root, depth = 0, texts = texts, viewIds = viewIds)
        return shouldBlockScreenTexts(
            pkg = pkg,
            appPackageName = appPackageName,
            visibleTexts = texts,
            isAccessibilityServiceRunning = isAccessibilityServiceRunning,
            visibleViewIds = viewIds
        )
    }

    private fun collectVisibleInfo(
        node: AccessibilityNodeInfo?,
        depth: Int = 0,
        texts: MutableList<String>,
        viewIds: MutableList<String>
    ) {
        if (node == null || depth > 16 || texts.size > 250) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { viewIds.add(it) }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectVisibleInfo(child, depth + 1, texts, viewIds)
            if (texts.size > 250) break
        }
    }
}
