package com.example.pguard.engine.filter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {

    private val filter = ContentFilter.Default

    @Test
    fun `blocks known adult host`() {
        assertTrue(filter.isAdultContent("https://www.pornhub.com/video/123"))
        assertTrue(filter.isAdultContent("xvideos.com"))
        assertTrue(filter.isAdultContent("HTTP://XHAMSTER.COM"))
    }

    @Test
    fun `blocks adult hosts with port and subdomain`() {
        assertTrue(filter.isAdultContent("http://m.xnxx.com:8080/watch"))
        assertTrue(filter.isAdultContent("https://cdn.chaturbate.com/room/1"))
    }

    @Test
    fun `blocks adult domain signatures in free text`() {
        assertTrue(filter.isAdultContent("XHamster - Free videos"))
        assertTrue(filter.isAdultContent("Watch redtube now"))
    }

    @Test
    fun `blocks explicit adult phrases as whole words`() {
        assertTrue(filter.isAdultContent("free porn videos"))
        assertTrue(filter.isAdultContent("best sex videos 2026"))
        assertTrue(filter.isAdultContent("pornography"))
        assertTrue(filter.isAdultContent("masturbation tips"))
        assertTrue(filter.isAdultContent("r/myporn00"))
        assertTrue(filter.isAdultContent("Ts parris fuck so good"))
        assertTrue(filter.isAdultContent("18 NSFW"))
        assertTrue(filter.isAdultContent("https://www.reddit.com/r/myporn00/comments/123"))
    }

    @Test
    fun `blocks adult paths and percent encoded queries`() {
        assertTrue(filter.isAdultContent("https://example.com/porn/collection"))
        assertTrue(filter.isAdultContent("https://example.com/?q=free%20porn%20videos"))
        assertTrue(filter.isAdultContent("https://example.com/search?q=nsfw"))
    }

    @Test
    fun `does not block harmless search phrases`() {
        assertFalse(filter.isAdultContent("sex education"))
        assertFalse(filter.isAdultContent("sexual health resources"))
        assertFalse(filter.isAdultContent("Sussex county council"))
        assertFalse(filter.isAdultContent("Essex weather today"))
        assertFalse(filter.isAdultContent("adult education classes"))
    }

    @Test
    fun `does not block lookalike hosts`() {
        assertFalse(filter.isAdultContent("notpornhub.com"))
        assertFalse(filter.isAdultContent("xhamsterarchive.com"))
    }

    @Test
    fun `blocks adult signature used as a subdomain label`() {
        assertTrue(filter.isAdultContent("pornhub.mirror.example"))
    }

    @Test
    fun `ignores blank and numeric input`() {
        assertFalse(filter.isAdultContent(""))
        assertFalse(filter.isAdultContent("   "))
        assertFalse(filter.isAdultContent("192.168.100.80:8080/health"))
        assertFalse(filter.isAdultContent("127.0.0.1"))
    }

    @Test
    fun `custom domain blocks subdomains but not lookalikes`() {
        val blocklist = listOf("youtube.com")

        assertTrue(filter.matchesCustomBlocklist("https://youtube.com/watch?v=1", blocklist))
        assertTrue(filter.matchesCustomBlocklist("https://m.youtube.com/watch?v=1", blocklist))
        assertTrue(filter.matchesCustomBlocklist("youtube.com", blocklist))
        assertFalse(filter.matchesCustomBlocklist("https://notyoutube.com/watch?v=1", blocklist))
        assertTrue(filter.matchesCustomBlocklist("https://youtube.com.evil.example", blocklist))
        assertFalse(filter.matchesCustomBlocklist("https://youtube.org/watch?v=1", blocklist))
    }

    @Test
    fun `custom domain normalizes scheme www and trailing slash`() {
        assertTrue(
            filter.matchesCustomBlocklist(
                "https://news.example.org/article",
                listOf("https://www.example.org/")
            )
        )
    }

    @Test
    fun `custom keyword matches whole words and plurals only`() {
        val blocklist = listOf("casino")

        assertTrue(filter.matchesCustomBlocklist("online casino bonus", blocklist))
        assertTrue(filter.matchesCustomBlocklist("best casinos 2026", blocklist))
        assertTrue(filter.matchesCustomBlocklist("https://example.com/casino/join", blocklist))
        assertFalse(filter.matchesCustomBlocklist("casinopolis fan page", blocklist))
        assertFalse(filter.matchesCustomBlocklist("concassino", blocklist))
    }

    @Test
    fun `custom multi word keyword tolerates flexible spacing`() {
        val blocklist = listOf("social media")

        assertTrue(filter.matchesCustomBlocklist("social  media detox", blocklist))
        assertFalse(filter.matchesCustomBlocklist("socialmedia detox", blocklist))
    }

    @Test
    fun `empty custom blocklist never matches`() {
        assertFalse(filter.matchesCustomBlocklist("https://youtube.com", emptyList()))
    }

    @Test
    fun `custom blocklist ignores blank entries`() {
        assertFalse(filter.matchesCustomBlocklist("https://example.com", listOf("", "   ")))
    }

    @Test
    fun `accepts injected signatures and patterns`() {
        val custom = ContentFilter(
            adultDomainSignatures = setOf("example"),
            adultPatterns = listOf(Regex("""\bforbidden\b""")),
        )

        assertTrue(custom.isAdultContent("https://example.com"))
        assertTrue(custom.isAdultContent("this is forbidden content"))
        assertFalse(custom.isAdultContent("pornhub.com"))
    }

    @Test
    fun `matches hyphenated adult domains like heavy-r in url and plain text`() {
        assertTrue(filter.isAdultContent("https://www.heavy-r.com/video/123"))
        assertTrue(filter.isAdultContent("heavy-r.com"))
        assertTrue(filter.isAdultContent("Watch heavy-r today"))
        assertTrue(filter.isAdultDomain("heavy-r.com"))
    }

    @Test
    fun `matches adult TLDs in urls and dns domains`() {
        assertTrue(filter.isAdultContent("https://site.xxx/home"))
        assertTrue(filter.isAdultContent("https://watch.porn"))
        assertTrue(filter.isAdultDomain("site.xxx"))
        assertTrue(filter.isAdultDomain("stream.adult"))
        assertTrue(filter.isAdultDomain("cams.sexy"))
        assertFalse(filter.isAdultDomain("wikipedia.org"))
    }

    @Test
    fun `decodes double percent encoding and strips zero width characters`() {
        assertTrue(filter.isAdultContent("https://example.com/search?q=free%2520porn%2520videos"))
        assertTrue(filter.isAdultContent("https://example.com/?q=%2570%256f%2572%256e"))
        assertTrue(filter.isAdultContent("https://p\u200Bornhub.com/"))
    }

    @Test
    fun `matchesDomainEntries checks subdomains for domain rules and ignores plain keywords`() {
        val list = listOf("https://www.reddit.com/", "casino", "x.com")
        assertTrue(filter.matchesDomainEntries("old.reddit.com", list))
        assertTrue(filter.matchesDomainEntries("reddit.com.", list))
        assertTrue(filter.matchesDomainEntries("mobile.x.com", list))
        assertFalse(filter.matchesDomainEntries("notreddit.com", list))
        assertFalse(filter.matchesDomainEntries("casino.org", list))
    }

    @Test
    fun `tiered page scanner avoids isolated profanity or anatomy false positives`() {
        val newsArticle = listOf(
            "Breaking News Today",
            "The Senator said that is complete fucking nonsense during the hearing.",
            "Public reactions continue to pour in."
        )
        val medicalArticle = listOf(
            "Human Reproductive Anatomy - Wikipedia",
            "The penis and vagina are primary reproductive organs.",
            "Discusses orgasm and sexual health education."
        )

        assertTrue(filter.findAdultPageContent(newsArticle) == null)
        assertTrue(filter.findAdultPageContent(medicalArticle) == null)
    }

    @Test
    fun `tiered page scanner blocks strong adult hits or multi-signal explicit pages`() {
        val strongPage = listOf(
            "Welcome",
            "Watch hardcore sex and hentai clips now"
        )
        val denseContextualPage = listOf(
            "Trending",
            "Hot boobs and tits",
            "Big cock and pussy",
            "Anal dildo compilation"
        )

        assertTrue(filter.findAdultPageContent(strongPage) != null)
        assertTrue(filter.findAdultPageContent(denseContextualPage) != null)
    }

    @Test
    fun `allows SFW reddit porn network names while blocking real adult subreddits`() {
        assertFalse(filter.isAdultContent("https://www.reddit.com/r/FoodPorn"))
        assertFalse(filter.isAdultContent("r/EarthPorn"))
        assertTrue(filter.findAdultPageContent(listOf("Welcome to r/FoodPorn", "Homemade pasta")) == null)

        assertTrue(filter.isAdultContent("https://www.reddit.com/r/myporn00/comments/123"))
        assertTrue(filter.isAdultContent("https://pornhub.com/r/FoodPorn"))
    }
}
