package com.example.pguard.engine.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiTamperGuardTest {

    @Test
    fun `blocks Android App Info screen for PGuard even when Notifications and Battery rows are visible`() {
        val appInfoTexts = listOf(
            "App info",
            "PGuard",
            "Open",
            "Uninstall",
            "Force stop",
            "Notifications",
            "Permissions",
            "Storage & cache",
            "Battery"
        )

        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = appInfoTexts
            )
        )
    }

    @Test
    fun `allows granting Display over other apps permission for PGuard`() {
        val overlayPermissionTexts = listOf(
            "Display over other apps",
            "PGuard",
            "Allow display over other apps"
        )

        assertFalse(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = overlayPermissionTexts
            )
        )
    }

    @Test
    fun `allows activating Device Admin permission for PGuard`() {
        val adminActivationTexts = listOf(
            "Activate device admin app",
            "PGuard",
            "Activate this device admin app"
        )

        assertFalse(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = adminActivationTexts
            )
        )
    }

    @Test
    fun `blocks package installer uninstall prompt targeting custom applicationId`() {
        val uninstallDialogTexts = listOf(
            "com.acme.pguard",
            "Do you want to uninstall this app?",
            "Cancel",
            "Uninstall"
        )

        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.google.android.packageinstaller",
                appPackageName = "com.acme.pguard",
                visibleTexts = uninstallDialogTexts
            )
        )
    }

    @Test
    fun `does not block App Info for unrelated apps`() {
        val otherAppTexts = listOf(
            "App info",
            "Calculator",
            "Uninstall",
            "Force stop"
        )

        assertFalse(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = otherAppTexts
            )
        )
    }

    @Test
    fun `blocks stock Android Accessibility stop confirmation dialog targeting PGuard`() {
        val stopDialogTexts = listOf(
            "Stop PGuard Protection Service?",
            "Tapping Stop will stop PGuard Protection Service.",
            "Cancel",
            "Stop"
        )

        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = stopDialogTexts
            )
        )
    }

    @Test
    fun `blocks active Accessibility service detail page while service is running`() {
        val serviceDetailTexts = listOf(
            "PGuard Protection Service",
            "Use PGuard Protection Service",
            "PGuard Protection Service shortcut"
        )

        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = serviceDetailTexts,
                isAccessibilityServiceRunning = true,
                visibleViewIds = listOf("com.android.settings:id/switch_widget")
            )
        )
    }

    @Test
    fun `allows VPN consent request dialog for PGuard`() {
        val vpnConsentTexts = listOf(
            "Connection request",
            "PGuard wants to set up a VPN connection that allows it to monitor network traffic.",
            "Cancel",
            "OK"
        )

        assertFalse(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = vpnConsentTexts,
                isAccessibilityServiceRunning = true
            )
        )
    }

    @Test
    fun `blocks VPN disconnect or forget screens for PGuard`() {
        val vpnDisconnectTexts = listOf(
            "PGuard DNS Shield",
            "Disconnect",
            "Forget VPN"
        )

        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = vpnDisconnectTexts,
                isAccessibilityServiceRunning = true
            )
        )
    }

    @Test
    fun `blocks localized uninstall and force stop screens in Spanish Swahili and French`() {
        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = listOf("PGuard", "Desinstalar", "Forzar detención")
            )
        )
        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.google.android.packageinstaller",
                appPackageName = "com.example.pguard",
                visibleTexts = listOf("PGuard", "Je, unataka kusanidua programu hii?", "Sanidua")
            )
        )
        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = listOf("PGuard", "Désinstaller", "Forcer l'arrêt")
            )
        )
    }

    @Test
    fun `allows Device Admin activation screen with PGuard tamper explanation while accessibility runs`() {
        val activationTexts = listOf(
            "Activate device admin app?",
            "PGuard Tamper Protection",
            "Activate Device Administrator to protect PGuard from unauthorized removal until you pass your accountability check.",
            "Activate this device admin app",
            "Cancel"
        )

        assertFalse(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = activationTexts,
                isAccessibilityServiceRunning = true
            )
        )
    }

    @Test
    fun `blocks Device Admin deactivation screen even though text contains activate substring`() {
        val deactivationTexts = listOf(
            "PGuard Tamper Protection",
            "Protects PGuard from unauthorized removal until you pass your accountability check.",
            "Deactivate this device admin app",
            "Cancel"
        )

        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = deactivationTexts,
                isAccessibilityServiceRunning = true
            )
        )
    }

    @Test
    fun `blocks Device Admin deactivation screen matched by destructive view ID`() {
        val texts = listOf(
            "PGuard",
            "Activate device admin"
        )

        assertTrue(
            AntiTamperGuard.shouldBlockScreenTexts(
                pkg = "com.android.settings",
                appPackageName = "com.example.pguard",
                visibleTexts = texts,
                isAccessibilityServiceRunning = true,
                visibleViewIds = listOf("com.android.settings:id/device_admin_deactivate")
            )
        )
    }
}
