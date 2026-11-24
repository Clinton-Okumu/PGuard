package com.example.pguard.engine.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAppInstallGuardTest {

    private val selfPkg = "com.example.pguard"

    @Test
    fun `auto-blocks newly installed user launchable app when feature enabled`() {
        assertTrue(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.spotify.music",
                selfPackageName = selfPkg,
                isFeatureEnabled = true,
                isSystemApp = false,
                hasLaunchIntent = true,
                isInputMethod = false,
                isWhitelisted = false,
                isApprovedNewApp = false
            )
        )
    }

    @Test
    fun `does not re-block previously approved new app after removal from blocklist`() {
        assertFalse(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.spotify.music",
                selfPackageName = selfPkg,
                isFeatureEnabled = true,
                isSystemApp = false,
                hasLaunchIntent = true,
                isInputMethod = false,
                isWhitelisted = false,
                isApprovedNewApp = true
            )
        )
    }

    @Test
    fun `skips auto-block when feature disabled or already whitelisted or blocked`() {
        assertFalse(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.spotify.music",
                selfPackageName = selfPkg,
                isFeatureEnabled = false,
                isSystemApp = false,
                hasLaunchIntent = true,
                isInputMethod = false,
                isWhitelisted = false,
                isApprovedNewApp = false
            )
        )
        assertFalse(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.spotify.music",
                selfPackageName = selfPkg,
                isFeatureEnabled = true,
                isSystemApp = false,
                hasLaunchIntent = true,
                isInputMethod = false,
                isWhitelisted = true,
                isApprovedNewApp = false
            )
        )
        assertFalse(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.spotify.music",
                selfPackageName = selfPkg,
                isFeatureEnabled = true,
                isSystemApp = false,
                hasLaunchIntent = true,
                isInputMethod = false,
                isWhitelisted = false,
                isApprovedNewApp = false,
                isAlreadyBlocked = true
            )
        )
    }

    @Test
    fun `skips system apps keyboards and non-launchable background packages`() {
        assertFalse(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.vendor.helper",
                selfPackageName = selfPkg,
                isFeatureEnabled = true,
                isSystemApp = true,
                hasLaunchIntent = true,
                isInputMethod = false,
                isWhitelisted = false,
                isApprovedNewApp = false
            )
        )
        assertFalse(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.example.iconpack",
                selfPackageName = selfPkg,
                isFeatureEnabled = true,
                isSystemApp = false,
                hasLaunchIntent = false,
                isInputMethod = false,
                isWhitelisted = false,
                isApprovedNewApp = false
            )
        )
        assertFalse(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.google.android.inputmethod.latin",
                selfPackageName = selfPkg,
                isFeatureEnabled = true,
                isSystemApp = false,
                hasLaunchIntent = true,
                isInputMethod = true,
                isWhitelisted = false,
                isApprovedNewApp = false
            )
        )
    }

    @Test
    fun `core packages Play Store settings installers and self are always excluded`() {
        val corePackages = listOf(
            "com.example.pguard",
            "android",
            "com.android.systemui",
            "com.android.vending",
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.touchtype.swiftkey",
            "com.google.android.dialer"
        )
        for (pkg in corePackages) {
            assertTrue(NewAppInstallGuard.isCoreOrSystemPackageName(pkg, selfPkg))
            assertFalse(
                NewAppInstallGuard.shouldAutoBlockNewApp(
                    packageName = pkg,
                    selfPackageName = selfPkg,
                    isFeatureEnabled = true,
                    isSystemApp = false,
                    hasLaunchIntent = true,
                    isInputMethod = false,
                    isWhitelisted = false,
                    isApprovedNewApp = false
                )
            )
        }
    }

    @Test
    fun `auto-blocks newly installed unsupported browsers and regular apps`() {
        assertTrue(
            NewAppInstallGuard.shouldAutoBlockNewApp(
                packageName = "com.opera.mini.native",
                selfPackageName = selfPkg,
                isFeatureEnabled = true,
                isSystemApp = false,
                hasLaunchIntent = true,
                isInputMethod = false,
                isWhitelisted = false,
                isApprovedNewApp = false
            )
        )
    }
}
