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
    fun `known unsupported browsers and DNS-bypass browsers are flagged correctly`() {
        assertTrue(BrowserInspector.isUnsupportedBrowser("org.torproject.torbrowser", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("org.torproject.torbrowser_alpha", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.opera.mini.native", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.opera.mini.native.beta", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.UCMobile.intl", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.kiwibrowser.browser", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.mi.globalbrowser", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.transsion.phoenix", null))
    }

    @Test
    fun `microsoft edge is recognized as a supported browser`() {
        assertTrue(BrowserInspector.isSupportedBrowser("com.microsoft.emmx"))
        assertTrue(BrowserInspector.isSupportedBrowser("com.microsoft.emmx.beta"))
        assertTrue(BrowserInspector.isSupportedBrowser("com.microsoft.emmx.canary"))
        assertTrue(BrowserInspector.isSupportedBrowser("com.microsoft.emmx.dev"))
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.microsoft.emmx", null))
        assertEquals(
            listOf("com.microsoft.emmx:id/url_bar"),
            BrowserInspector.addressBarIdsForPackage("com.microsoft.emmx")
        )
    }

    @Test
    fun `non-web utility browsers and Google search app are not falsely flagged as unsupported browsers`() {
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.estrongs.android.filebrowser", null))
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.example.photobrowser", null))
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.dev.sqlitebrowser", null))
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.smb.lanbrowser", null))
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.google.android.googlequicksearchbox", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("org.niche.webbrowser", null))
        assertTrue(BrowserInspector.isUnsupportedBrowser("com.fast.browser", null))
    }

    @Test
    fun `all 14 supported browser names exclude Tor and Opera Mini and include Edge`() {
        assertEquals(14, BrowserInspector.SUPPORTED_BROWSER_NAMES.size)
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Samsung Internet"))
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Google Chrome"))
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Mozilla Firefox"))
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Brave Browser"))
        assertTrue(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Microsoft Edge"))
        assertFalse(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Tor Browser"))
        assertFalse(BrowserInspector.SUPPORTED_BROWSER_NAMES.contains("Opera Mini"))
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

    @Test
    fun `addressBarIdsForPackage maps exact ids without cross-package multiplication`() {
        assertEquals(
            listOf("com.android.chrome:id/url_bar"),
            BrowserInspector.addressBarIdsForPackage("com.android.chrome")
        )
        assertEquals(
            listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                "com.sec.android.app.sbrowser:id/url_bar_title"
            ),
            BrowserInspector.addressBarIdsForPackage("com.sec.android.app.sbrowser")
        )
        val firefoxIds = BrowserInspector.addressBarIdsForPackage("org.mozilla.firefox")
        assertTrue(firefoxIds.all { it.startsWith("org.mozilla.firefox:id/") })
        assertEquals(4, firefoxIds.size)
        assertEquals(
            listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
            BrowserInspector.addressBarIdsForPackage("com.duckduckgo.mobile.android")
        )
    }

    @Test
    fun `isLikelyAddressBarViewId accepts url bars and rejects action buttons`() {
        assertTrue(BrowserInspector.isLikelyAddressBarViewId("com.android.chrome:id/url_bar"))
        assertTrue(BrowserInspector.isLikelyAddressBarViewId("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"))
        assertTrue(BrowserInspector.isLikelyAddressBarViewId("com.duckduckgo.mobile.android:id/omnibarTextInput"))
        assertFalse(BrowserInspector.isLikelyAddressBarViewId("com.android.chrome:id/menu_button"))
        assertFalse(BrowserInspector.isLikelyAddressBarViewId("com.android.chrome:id/tab_switcher_button"))
        assertFalse(BrowserInspector.isLikelyAddressBarViewId("com.android.chrome:id/share_url_button"))
    }

    @Test
    fun `blocks privacy and chromium fork unsupported browsers without browser in package name and keeps utility viewers out`() {
        val unsupportedForks = listOf(
            "org.bromite.bromite",
            "org.cromite.cromite",
            "net.waterfox.android.release",
            "io.github.forkmaintainers.iceraven",
            "com.naver.whale",
            "com.tencent.mtt",
            "com.oh.bro"
        )
        for (pkg in unsupportedForks) {
            assertTrue("Expected $pkg to be unsupported", BrowserInspector.isUnsupportedBrowser(pkg, null))
            assertTrue("Expected $pkg to be recognized as browser", BrowserInspector.isBrowser(pkg, null))
        }

        assertFalse(BrowserInspector.isBrowser("com.estrongs.android.filebrowser", null))
        assertFalse(BrowserInspector.isUnsupportedBrowser("com.estrongs.android.filebrowser", null))
    }
}
