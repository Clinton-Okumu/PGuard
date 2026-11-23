package com.example.pguard.presentation.settings

import com.example.pguard.presentation.dashboard.ProtectedAction
import com.example.pguard.presentation.dashboard.targetDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSupportTest {

    @Test
    fun `formatAppVersion formats standard blank and null values safely`() {
        assertEquals("2.4.1 (Build 18)", formatAppVersion("2.4.1", 18L))
        assertEquals("1.0 (Build 5)", formatAppVersion("   ", 5L))
        assertEquals("1.0 (Build 1)", formatAppVersion(null, null))
    }

    @Test
    fun `buildSupportEmailBody includes diagnostics without leaking PII`() {
        val body = buildSupportEmailBody(
            appVersion = "1.0 (Build 1)",
            androidRelease = "15",
            sdkInt = 35,
            isSignedIn = false,
            accessibilityRunning = true,
            vpnRunning = false,
            deviceAdminActive = true,
            batteryUnrestricted = false,
            notificationsEnabled = true,
            accountabilityModeTitle = "24-Hour Time Delay"
        )

        assertTrue(body.contains("App Version: 1.0 (Build 1)"))
        assertTrue(body.contains("Android: 15 (SDK 35)"))
        assertTrue(body.contains("Account Status: Guest Mode"))
        assertTrue(body.contains("Accountability Mode: 24-Hour Time Delay"))
        assertTrue(body.contains("Accessibility Service: Active"))
        assertTrue(body.contains("DNS VPN Shield: Off"))
        assertTrue(body.contains("Device Admin: Active"))
        assertTrue(body.contains("Battery Optimization Ignored: No"))
        assertTrue(body.contains("Notifications Enabled: Yes"))
        assertFalse(body.contains("@"))
    }

    @Test
    fun `uninstall protected action maps consistently`() {
        assertEquals("UNINSTALL_APP", ProtectedAction.UninstallApp.identifier)
        assertEquals("Uninstall App", ProtectedAction.UninstallApp.title)
        assertEquals("Uninstall App", targetDisplayName(ProtectedAction.UninstallApp.identifier))
    }
}
