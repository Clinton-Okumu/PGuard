package com.example.pguard.engine.accessibility

import android.os.Build
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

    private val DEDICATED_PERMISSION_SUBSCREEN_PHRASES = listOf(
        "display over other apps",
        "appear on top",
        "display pop-up",
        "pop-up window",
        "draw over other apps",
        "allow display",
        "permit drawing",
        "activate this device admin",
        "activate device admin",
        "activate admin",
        "ignore battery optimization",
        "connection request",
        "set up a vpn",
        "wants to set up a vpn",
        "vpn connection"
    )

    private val APP_INFO_HEADER_MARKERS = listOf(
        "app info",
        "application info",
        "app details",
        "información de la aplicación",
        "infos sur l'appli",
        "app-info",
        "maelezo ya programu"
    )

    private val DESTRUCTIVE_KEYWORDS = listOf(
        // English
        "uninstall",
        "deactivate and uninstall",
        "delete app",
        "remove app",
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

    private val LAUNCHER_UNINSTALL_KEYWORDS = listOf(
        "uninstall",
        "delete app",
        "remove app",
        "desinstalar",
        "désinstaller",
        "deinstallieren",
        "sanidua",
        "copot pemasangan",
        "kaldır"
    )

    private val APP_INFO_DESTRUCTIVE_MARKERS = listOf(
        "uninstall",
        "deactivate and uninstall",
        "delete app",
        "remove app",
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
        "device_admin_deactivate",
        "btn_uninstall",
        "btn_force_stop",
        "btn_clear_data"
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
            lower == "com.oplus.appdetail" ||
            lower == "com.oplus.safecenter" ||
            lower == "com.coloros.safecenter" ||
            lower == "com.oneplus.security" ||
            lower == "com.iqoo.secure" ||
            lower == "com.vivo.permissionmanager" ||
            lower == "com.huawei.systemmanager" ||
            lower == "com.hihonor.systemmanager" ||
            lower == "com.samsung.android.lool" ||
            lower == "com.transsion.phonemaster" ||
            lower == "com.miui.securityadd" ||
            lower == "com.miui.securitycore" ||
            lower == "com.xiaomi.mipicks" ||
            lower == "com.xiaomi.discover" ||
            lower.contains("packageinstaller") ||
            lower.contains("permissioncontroller") ||
            lower.contains("settings") ||
            lower.contains("securitycenter") ||
            lower.contains("appdetail")
    }

    fun isLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.ROOT)
        return lower == "com.miui.home" ||
            lower == "com.mi.android.globallauncher" ||
            lower == "com.sec.android.app.launcher" ||
            lower == "com.google.android.apps.nexuslauncher" ||
            lower == "com.oppo.launcher" ||
            lower == "com.oneplus.launcher" ||
            lower == "com.huawei.android.launcher" ||
            lower == "com.transsion.hilauncher" ||
            lower.contains("launcher")
    }

    fun shouldBlockLauncherUninstall(
        appPackageName: String,
        visibleTexts: Collection<String>
    ): Boolean {
        if (visibleTexts.isEmpty()) return false
        val cleaned = visibleTexts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return false

        // Single text/node directly naming PGuard alongside an uninstall action (e.g., tooltip or dialog body)
        for (text in cleaned) {
            val lower = text.lowercase(Locale.ROOT)
            if (containsPGuardReference(text, appPackageName) &&
                LAUNCHER_UNINSTALL_KEYWORDS.any { keyword -> lower.contains(keyword) }
            ) {
                return true
            }
        }

        // Compact launcher confirm dialog / popup menu window (never full home icon grid)
        if (cleaned.size <= 12) {
            val hasPGuard = cleaned.any { containsPGuardReference(it, appPackageName) }
            val hasUninstall = cleaned.any { text ->
                val lower = text.lowercase(Locale.ROOT)
                LAUNCHER_UNINSTALL_KEYWORDS.any { keyword -> lower.contains(keyword) }
            }
            if (hasPGuard && hasUninstall) return true
        }
        return false
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

        val loweredTexts = visibleTexts.map { it.lowercase(Locale.ROOT) }
        val hasAppInfoHeader = loweredTexts.any { t ->
            APP_INFO_HEADER_MARKERS.any { marker -> t.contains(marker) }
        }
        val hasDedicatedPermissionSubscreen = loweredTexts.any { t ->
            DEDICATED_PERMISSION_SUBSCREEN_PHRASES.any { phrase ->
                t.contains(phrase) && (!phrase.contains("activate") || !t.contains("deactivate"))
            }
        }
        if (hasAppInfoHeader && !hasDedicatedPermissionSubscreen) {
            return true
        }

        if (isPermissionConfigurationTexts(visibleTexts)) {
            return false
        }

        if (isAccessibilityServiceRunning) {
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
    ): Boolean = shouldBlockTamper(
        pkg = pkg,
        roots = listOfNotNull(root),
        appPackageName = appPackageName,
        isAccessibilityServiceRunning = isAccessibilityServiceRunning,
        extraTexts = emptyList()
    )

    fun shouldBlockTamper(
        pkg: String,
        roots: List<AccessibilityNodeInfo>,
        appPackageName: String,
        isAccessibilityServiceRunning: Boolean = true,
        extraTexts: Collection<String> = emptyList()
    ): Boolean {
        val texts = LinkedHashSet<String>()
        val viewIds = LinkedHashSet<String>()
        for (extra in extraTexts) {
            val trimmed = extra.trim()
            if (trimmed.isNotEmpty()) texts.add(trimmed)
        }
        if (roots.isNotEmpty()) {
            collectVisibleInfoBfs(
                roots = roots,
                maxDepth = 40,
                maxItems = 400,
                maxVisitedNodes = 2000,
                texts = texts,
                viewIds = viewIds
            )
        }
        if (texts.isEmpty()) return false
        return shouldBlockScreenTexts(
            pkg = pkg,
            appPackageName = appPackageName,
            visibleTexts = texts,
            isAccessibilityServiceRunning = isAccessibilityServiceRunning,
            visibleViewIds = viewIds
        )
    }

    private fun collectVisibleInfoBfs(
        roots: List<AccessibilityNodeInfo>,
        maxDepth: Int,
        maxItems: Int,
        maxVisitedNodes: Int,
        texts: MutableSet<String>,
        viewIds: MutableSet<String>
    ) {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        for (root in roots.distinct()) {
            queue.addLast(root to 0)
        }
        var visited = 0

        while (queue.isNotEmpty() && visited < maxVisitedNodes && texts.size < maxItems) {
            val (node, depth) = queue.removeFirst()
            visited++

            fun recordText(raw: CharSequence?) {
                val value = raw?.toString()?.trim()
                if (!value.isNullOrBlank() && texts.size < maxItems) {
                    texts.add(value)
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
            node.viewIdResourceName?.trim()?.takeIf { it.isNotBlank() }?.let {
                if (viewIds.size < maxItems) viewIds.add(it)
            }

            if (depth < maxDepth) {
                val count = node.childCount
                for (i in 0 until count) {
                    val child = node.getChild(i) ?: continue
                    queue.addLast(child to (depth + 1))
                }
            }
        }
    }
}
