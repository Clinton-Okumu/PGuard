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
}
