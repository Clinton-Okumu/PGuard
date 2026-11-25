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
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.safaricom.mpesa.lifestyle", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.equitybank.mobile", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.kcb.mobilebanking", "com.example.pguard"))
        assertTrue(InAppAdultGuard.isExcludedFromInAppAdultScan("com.x8bit.bitwarden", "com.example.pguard"))

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

    @Test
    fun `blocks mid-string nsfw 18 plus blurred media and mature content warnings outright`() {
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/sub • 2h • NSFW • Photo")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/sub • 18+ • 50k members")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("This post contains mature content")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("Blurred because it may contain mature content", "Tap to unblur")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.some.social.app",
                activeUrl = null,
                visibleTexts = listOf("Content warning: Nudity", "This photo may contain sensitive content")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.discord",
                activeUrl = null,
                visibleTexts = listOf("Age-restricted")
            )
        )
        assertNotNull(InAppAdultGuard.findAdultInputMatch("mature content"))
        assertNotNull(InAppAdultGuard.findAdultClickMatch(listOf("Tap to unblur"), isSocialContext = false))
    }

    @Test
    fun `allows benign uses of mature outside warnings and badges`() {
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.some.article.app",
                activeUrl = null,
                visibleTexts = listOf("Planting a mature oak tree in your garden", "Long-term financial maturity guide")
            )
        )
    }

    @Test
    fun `blocks icon-only adult badges via viewIds and expanded curated adult subreddits`() {
        assertTrue(InAppAdultGuard.isAdultViewId("com.reddit.frontpage:id/nsfw_badge"))
        assertTrue(InAppAdultGuard.isAdultViewId("com.reddit.frontpage:id/over_18_pill"))
        assertTrue(InAppAdultGuard.isAdultViewId("com.discord:id/age_gate"))
        assertFalse(InAppAdultGuard.isAdultViewId("com.reddit.frontpage:id/toolbar_title"))
        assertFalse(InAppAdultGuard.isAdultViewId("com.reddit.frontpage:id/user_avatar"))

        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("Media post"),
                viewIds = listOf("com.reddit.frontpage:id/post_nsfw_tag")
            )
        )

        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/ass", "Join")
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/hardcore", "Top posts")
            )
        )
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.reddit.frontpage",
                activeUrl = null,
                visibleTexts = listOf("r/assassinscreed", "r/teenagers", "Gaming news")
            )
        )
    }

    @Test
    fun `blocks reddit web search results with 18NSFW, Safe Search Off, and explicit subreddits in Firefox`() {
        val screenshotTexts = listOf(
            "Tsparis",
            "Posts",
            "Communities",
            "Media",
            "Comments",
            "Relevance",
            "All time",
            "Safe Search Off",
            "Showing results for Ts Paris",
            "u/Greedy-Training7808 · 2y ago",
            "18NSFW",
            "Ts Paris stroke and poke",
            "18+",
            "r/ebonyshemalee · 1y ago",
            "ts paris sexy ass",
            "r/ebonyshemalee · 8mo ago",
            "ts paris looks amazing from the back omg"
        )

        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "org.mozilla.firefox",
                activeUrl = "reddit.com/search/?q=Tsparis",
                visibleTexts = screenshotTexts,
                isBrowser = true
            )
        )

        // Even when Compose toolbar URL extraction is null, social context is inferred from Reddit clues
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "org.mozilla.firefox",
                activeUrl = null,
                visibleTexts = screenshotTexts,
                isBrowser = true
            )
        )
    }

    @Test
    fun `blocks concatenated 18NSFW badge and Safe Search Off paired with explicit clues without blocking clean searches`() {
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "org.mozilla.firefox",
                activeUrl = null,
                visibleTexts = listOf("u/someone", "(18)NSFW"),
                isBrowser = true
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "org.mozilla.firefox",
                activeUrl = "https://www.reddit.com/search/?q=test",
                visibleTexts = listOf("Safe Search Off", "u/someone", "ts paris sexy ass"),
                isBrowser = true
            )
        )
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "org.mozilla.firefox",
                activeUrl = "https://www.reddit.com/search/?q=kotlin",
                visibleTexts = listOf("Posts", "Safe Search Off", "Kotlin Coroutines tutorial", "u/kotlin_dev"),
                isBrowser = true
            )
        )
        assertNotNull(
            InAppAdultGuard.findAdultClickMatch(
                eventTexts = listOf("Safe Search Off"),
                isSocialContext = true
            )
        )
    }

    @Test
    fun `reddit browser feed allows gaming news headline mentioning pornhub without NSFW signals`() {
        val redditFeedTexts = listOf(
            "r/gaming · 3h ago",
            "Real Life Strip Club Says It's Had 'Hundreds of Inquiries' From GTA 6 Fans, as Pornhub Traffic Plummets",
            "IGN",
            "3.8K upvotes",
            "420 comments"
        )
        assertNull(
            InAppAdultGuard.findAdultScreenMatch(
                packageName = "com.android.chrome",
                activeUrl = "https://www.reddit.com/",
                visibleTexts = redditFeedTexts,
                isBrowser = true
            )
        )
    }
}
