package com.example.pguard.engine.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserInspectorTest {

    @Test
    fun `samsung internet is recognized as a supported browser`() {
        assertTrue(BrowserInspector.isSupportedBrowser("com.sec.android.app.sbrowser"))
        assertTrue(BrowserInspector.isSupportedBrowser("com.sec.android.app.sbrowser.beta"))
        assertTrue(BrowserInspector.isSupportedBrowser("com.sec.android.app.sbrowser.lite"))
    }

    @Test
    fun `samsung internet is not flagged as unsupported browser`() {
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.sec.android.app.sbrowser", null))
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.sec.android.app.sbrowser.beta", null))
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.sec.android.app.sbrowser.lite", null))
    }

    @Test
    fun `known unsupported browsers are flagged correctly`() {
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.microsoft.emmx", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.UCMobile.intl", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.kiwibrowser.browser", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.mi.globalbrowser", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.transsion.phoenix", null))
    }

    @Test
    fun `all 15 supported browser names are present`() {
        assertEquals(15, BrowserInspector.SUPPORTED_BROWSER_NAMES.size)
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Samsung Internet"))
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Google Chrome"))
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Mozilla Firefox"))
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Brave Browser"))
    }

    @Test
    fun `isBrowser recognizes samsung internet and other browsers`() {
        assertTrue(BrowserInspector.isBrowser("com.sec.android.app.sbrowser", null))
        assertTrue(BrowserInspector.isBrowser("com.android.chrome", null))
        assertTrue(BrowserInspector.isBrowser("org.mozilla.firefox", null))
    }

    @Test
    fun `getBrowserDisplayName returns Samsung Internet for sbrowser packages`() {
        assertEquals("Samsung Internet", BrowserInspector.getBrowserDisplayName("com.sec.android.app.sbrowser", null))
        assertEquals("Samsung Internet", BrowserInspector.getBrowserDisplayName("com.sec.android.app.sbrowser.beta", null))
    }
}
