package com.example.pguard.engine.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppAdultGuardTest {

    @Test
    fun `excludes system settings play store keyboards launchers and dialers`() {
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.example.pguard", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.android.systemui", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.android.settings", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.android.vending", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.google.android.inputmethod.latin", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.touchtype.swiftkey", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.sec.android.app.launcher", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.google.android.dialer", "com.example.pguard"))

        assertFalse(InAppAdultGuard.isExcludedFromInAppAdultScan("com.reddit.frontpage", "com.example.pguard"))
        assertFalse(InAppAdultGuard.isExcludedFromInAppAdultScan("com.twitter.android", "com.example.pguard"))
        assertFalse(InAppAdultGuard.isExcludedFromInAppAdultScan("com.discord", "com.example.pguard"))
        assertFalse(InAppAdultGuard.isExcludedFromInAppAdultScan("org.telegram.messenger", "com.example.pguard"))
        assertFalse(InAppAdultGuard.isExcludedFromInAppAdultScan("com.example.thirdpartyapp", "com.example.pguard"))
    }

    @Test
    fun `reddit app blocks nsfw badges 18 plus gates explicit subreddits and mature prompts`() {
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("Home", "NSFW", "Some post title")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("u/example", "18+", "View posts")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("Mature content", "This community is marked as mature.", "Yes, I'm Over 18")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/gonewild", "Join")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/realgirls", "Popular")
            )
        )
    }

    @Test
    fun `reddit app allows SFW subreddits and passive general settings`() {
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/androiddev", "Kotlin Coroutines Guide", "Hot", "New")
            )
        )
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/FoodPorn", "Homemade ramen recipe", "2.4k upvotes")
            )
        )
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("Settings", "Dark mode", "Show mature content (I'm over 18)", "Blur mature images (18+)")
            )
        )
    }

    @Test
    fun `blocks tapping NSFW overrides or mature toggle in settings`() {
        assertNotNull(
            InAppAdultGuard.findAdultClickMatch(
                eventTexts = listOf("Show mature content (I'm over 18)"),
                isSocialContext = true
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultClickMatch(
                eventTexts = listOf("Yes, I'm Over 18"),
                isSocialContext = true
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultClickMatch(
                eventTexts = listOf("Yes, view profile"),
                isSocialContext = true
            )
        )
    }

    @Test
    fun `blocks adult searches in any app while allowing SFW foodporn search`() {
        assertNotNull(InAppAdultGuard.findAdultInputMatch("nsfw"))
        assertNotNull(InAppAdultGuard.findAdultInputMatch("r/gonewild"))
        assertNotNull(InAppAdultGuard.findAdultInputMatch("pornhub"))
        assertNull(InAppAdultGuard.findAdultInputMatch("r/FoodPorn"))
        assertNull(InAppAdultGuard.findAdultInputMatch("kotlin flow tutorial"))
    }

    @Test
    fun `x discord telegram and browser social urls are protected`() {
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.twitter.android",
                activeUrl = null,
                visibleTexts = listOf("Caution: This profile may include potentially sensitive content", "Yes, view profile")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.discord",
                activeUrl = null,
                visibleTexts = listOf("Age-Restricted Channel", "You must be 18 or older to access this channel.")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "org.telegram.messenger",
                activeUrl = null,
                visibleTexts = listOf("This channel can't be displayed because it was used to spread pornographic content.")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.android.chrome",
                activeUrl = "https://www.reddit.com/r/realgirls/",
                visibleTexts = listOf("18+", "r/realgirls"),
                isBrowser = true
            )
        )
    }

    @Test
    fun `generic non-social apps block adult brands and explicit content but ignore benign 18 plus labels`() {
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.some.thirdparty.app",
                activeUrl = null,
                visibleTexts = listOf("Open link: onlyfans.com/creator")
            )
        )
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.some.game.app",
                activeUrl = null,
                visibleTexts = listOf("Call of Duty", "18+", "Action Shooter")
            )
        )
    }

    @Test
    fun `respects user whitelist`() {
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/nsfw_art", "NSFW"),
                whitelist = listOf("nsfw")
            )
        )
    }
}
