package com.example.pguard.engine.accessibility

import com.example.pguard.engine.filter.ContentFilter
import java.util.Locale

/**
 * Pure-Kotlin detector extending Adult Content & NSFW protection beyond web browsers to all
 * non-system Android apps (Reddit, X/Twitter, Discord, Telegram, Instagram, WebViews, etc.)
 * as well as social-platform pages inside mobile browsers.
 */
object InAppAdultGuard {

    private val EXCLUDED_EXACT_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.vending",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.miui.home",
        "com.oneplus.launcher"
    )

    private val SOCIAL_APP_PACKAGES = setOf(
        "com.reddit.frontpage",
        "com.rubenmayayo.reddit",
        "ml.docilealligator.infinityforreddit",
        "reddit.news",
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.andrewshu.android.reddit",
        "com.onelouder.baconreader",
        "o.o.joey",
        "me.ccrama.redditslide",
        "org.quantumbadger.redreader",
        "com.twitter.android",
        "com.twitter.android.lite",
        "com.discord",
        "org.telegram.messenger",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "tw.nekomimi.nekogram",
        "com.tumblr",
        "xyz.blueskyweb.app",
        "com.instagram.barcelona",
        "com.instagram.android",
        "com.instagram.lite",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.quora.android",
        "com.pinterest",
        "com.imgur.mobile",
        "com.ninegag.android.app",
        "jp.pxv.android",
        "com.deviantart.android.damobile",
        "com.vkontakte.android",
        "org.joinmastodon.android",
        "com.jerboa"
    )

    private val SOCIAL_DOMAINS = setOf(
        "reddit.com",
        "redd.it",
        "x.com",
        "twitter.com",
        "discord.com",
        "discord.gg",
        "tumblr.com",
        "bsky.app",
        "threads.net",
        "instagram.com",
        "facebook.com",
        "quora.com",
        "pinterest.com",
        "imgur.com",
        "pixiv.net",
        "deviantart.com",
        "vk.com",
        "9gag.com",
        "t.me"
    )

    internal val SFW_REDDIT_PORN_SUBREDDITS = setOf(
        "foodporn",
        "earthporn",
        "mapporn",
        "designporn",
        "cityporn",
        "spaceporn",
        "roomporn",
        "historyporn",
        "architectureporn",
        "abandonedporn",
        "carporn",
        "machineporn",
        "animalporn",
        "waterporn",
        "weatherporn",
        "cableporn",
        "powerwashingporn",
        "penmanshipporn",
        "toolporn",
        "woodworkingporn",
        "adporn",
        "movieposterporn",
        "bookporn",
        "artporn",
        "fossilporn",
        "geologyporn",
        "botanicalporn",
        "mineralporn",
        "skyporn",
        "infrastructureporn",
        "engineeringporn"
    )

    private val EXPLICIT_SUBREDDIT_STEMS = listOf(
        "nsfw",
        "porn",
        "gonewild",
        "hentai",
        "rule34",
        "onlyfans",
        "lewd",
        "ecchi",
        "milf",
        "boob",
        "tits",
        "titty",
        "pussy",
        "cock",
        "dick",
        "nude",
        "nudit",
        "cumslut",
        "blowjob",
        "handjob",
        "anal",
        "bdsm",
        "cuckold",
        "hotwife",
        "fappening",
        "redgif",
        "erome",
        "camgirl",
        "jerkoff",
        "fap",
        "yiff",
        "ahegao",
        "slut",
        "whore"
    )

    private val CURATED_NSFW_SUBREDDITS = setOf(
        "realgirls",
        "gwcouples",
        "bustypetite",
        "pawg",
        "thick",
        "collegesluts",
        "legalteens",
        "dirtyr4r",
        "dirtypenpals",
        "sexstories",
        "erotica",
        "juicyasians",
        "breedingmaterial",
        "holdthemoan",
        "ratemynudebody",
        "swingers",
        "asiansgonewild",
        "gonewildaudio",
        "gonewildstories",
        "nsfwhardcore",
        "nsfw_gifs",
        "nsfwgif",
        "tiktoknsfw",
        "celebnsfw"
    )

    internal val SUBREDDIT_REGEX = Regex(
        """(?<![\p{L}\p{N}_])/?r/([a-z0-9_]{2,})""",
        RegexOption.IGNORE_CASE
    )
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")
    internal val NSFW_WORD_REGEX = Regex("""\bnsfw\b""", RegexOption.IGNORE_CASE)

    private val STANDALONE_NSFW_REGEX = Regex(
        """^(?:nsfw(?:\s*[•·|\-].*)?|.*[•·|\-]\s*nsfw)$""",
        RegexOption.IGNORE_CASE
    )

    private val STANDALONE_18_PLUS_REGEX = Regex(
        """^(?:18\+(?:\s*[•·|\-].*)?|.*[•·|\-]\s*18\+)$""",
        RegexOption.IGNORE_CASE
    )

    private val SOCIAL_MATURE_BADGE_REGEX = Regex(
        """^(?:mature\s+content|mature\s*\(18\+\)|sensitive\s+content|over\s+18)$""",
        RegexOption.IGNORE_CASE
    )

    private val CLICK_NSFW_GATE_PATTERNS = listOf(
        Regex("""\byes,\s*i['’]?m\s+over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\byes,\s*i\s+am\s+over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\bi\s+am\s+18\s+or\s+older\b""", RegexOption.IGNORE_CASE),
        Regex("""\bi['’]?m\s+over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\bconfirm\s+(?:you\s+are\s+)?over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\byes,\s*view\s+profile\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:view|see|show|continue\s+to|include)\s+nsfw\b""", RegexOption.IGNORE_CASE),
        Regex("""\bshow\s+mature\s+content\b""", RegexOption.IGNORE_CASE),
        Regex("""\binclude\s+nsfw\s+results\b""", RegexOption.IGNORE_CASE),
        Regex("""\ballow\s+access\s+to\s+age-restricted\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdisplay\s+media\s+that\s+may\s+contain\s+sensitive\s+content\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:view|show)\s+sensitive\s+content\b""", RegexOption.IGNORE_CASE)
    )

    private val UNIVERSAL_NSFW_SCREEN_PATTERNS = listOf(
        STANDALONE_NSFW_REGEX,
        Regex("""\bmarked\s+as\s+nsfw\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnsfw\s+(?:community|channel|post|content|results|server|feed|profile|18\+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthis\s+community\s+is\s+marked\s+as\s+mature\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:you\s+must\s+be\s+(?:at\s+least\s+)?18(?:\+|(?:\s+or\s+older))?(?:\s+to\s+view)?)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:confirm\s+you\s+are\s+over\s+18|are\s+you\s+18\s+or\s+older)\b""", RegexOption.IGNORE_CASE),
        Regex("""\byes,\s*i['’]?m\s+over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\byes,\s*i\s+am\s+over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\bi\s+am\s+18\s+or\s+older\b""", RegexOption.IGNORE_CASE),
        Regex("""\bage-restricted\s+(?:channel|content|community|server|profile)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpotentially\s+sensitive\s+content\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmay\s+contain\s+sensitive\s+material\b""", RegexOption.IGNORE_CASE),
        Regex("""\bused\s+to\s+spread\s+pornographic\b""", RegexOption.IGNORE_CASE),
        Regex("""\badult\s+content\s+warning\b""", RegexOption.IGNORE_CASE),
        Regex("""\b18\+\s*(?:only|content|community|channel|nsfw)\b""", RegexOption.IGNORE_CASE)
    )

    fun isExcludedFromInAppAdultScan(packageName: String, ownPackageName: String): Boolean {
        val pkg = packageName.trim().lowercase(Locale.ROOT)
        val own = ownPackageName.trim().lowercase(Locale.ROOT)
        if (pkg.isBlank() || pkg == own) return true
        if (pkg in EXCLUDED_EXACT_PACKAGES || pkg.startsWith("com.android.systemui.")) return true

        return pkg.contains("dialer") ||
            pkg.contains("incallui") ||
            pkg.contains("launcher") ||
            pkg.contains("trebuchet") ||
            pkg.contains("inputmethod") ||
            pkg.contains("gboard") ||
            pkg.contains("swiftkey") ||
            pkg.contains("honeyboard") ||
            pkg.contains("fleksy") ||
            pkg.contains("gingerkeyboard") ||
            pkg.endsWith(".ime") ||
            pkg.contains(".ime.") ||
            pkg.contains("keyboard")
    }

    fun isSocialOrCommunityApp(packageName: String): Boolean {
        val pkg = packageName.trim().lowercase(Locale.ROOT)
        if (pkg.isBlank()) return false
        if (pkg in SOCIAL_APP_PACKAGES) return true
        return pkg.contains("reddit") ||
            pkg.contains("telegram") ||
            pkg.contains("discord") ||
            pkg.contains("instagram") ||
            pkg.contains("instander") ||
            pkg.contains("honista") ||
            pkg.contains("mastodon") ||
            pkg.contains("lemmy")
    }

    fun isSocialOrCommunityUrl(url: String?): Boolean {
        val host = extractHost(url) ?: return false
        return SOCIAL_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
    }

    fun stripSfwRedditSubreddits(input: String): String {
        if (!input.contains("r/", ignoreCase = true)) return input
        var changed = false
        val stripped = SUBREDDIT_REGEX.replace(input) { match ->
            val sub = match.groupValues[1].lowercase(Locale.ROOT)
            if (sub in SFW_REDDIT_PORN_SUBREDDITS) {
                changed = true
                ""
            } else {
                match.value
            }
        }
        return if (changed) {
            MULTI_SPACE_REGEX.replace(stripped, " ").trim()
        } else {
            input
        }
    }

    fun isSfwRedditPornSubreddit(
        input: String,
        contentFilter: ContentFilter = ContentFilter.Default
    ): Boolean {
        val matches = SUBREDDIT_REGEX.findAll(input).toList()
        if (matches.isEmpty()) return false
        val allSfw = matches.all { it.groupValues[1].lowercase(Locale.ROOT) in SFW_REDDIT_PORN_SUBREDDITS }
        if (!allSfw) return false
        val remainder = stripSfwRedditSubreddits(input)
        return remainder.isBlank() || !contentFilter.isAdultContent(remainder)
    }

    fun matchesExplicitSubreddit(input: String): Boolean {
        for (match in SUBREDDIT_REGEX.findAll(input)) {
            val sub = match.groupValues[1].lowercase(Locale.ROOT)
            if (sub in SFW_REDDIT_PORN_SUBREDDITS) continue
            if (sub in CURATED_NSFW_SUBREDDITS) return true
            if (EXPLICIT_SUBREDDIT_STEMS.any { stem -> sub.contains(stem) }) return true
        }
        return false
    }

    fun findAdultInputMatch(
        inputText: String,
        contentFilter: ContentFilter = ContentFilter.Default,
        customBlocklist: Collection<String> = emptyList(),
        whitelist: Collection<String> = emptyList()
    ): String? {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) return null
        if (contentFilter.matchesWhitelist(trimmed, whitelist)) return null
        if (matchesExplicitSubreddit(trimmed)) return trimmed

        val cleaned = stripSfwRedditSubreddits(trimmed)
        if (cleaned.isBlank()) return null
        if (NSFW_WORD_REGEX.containsMatchIn(cleaned)) return trimmed
        if (contentFilter.isAdultContent(cleaned)) return trimmed
        if (customBlocklist.isNotEmpty() && contentFilter.matchesCustomBlocklist(cleaned, customBlocklist)) {
            return trimmed
        }
        return null
    }

    fun findAdultClickMatch(
        eventTexts: Collection<String>,
        isSocialContext: Boolean,
        contentFilter: ContentFilter = ContentFilter.Default,
        whitelist: Collection<String> = emptyList()
    ): String? {
        for (raw in eventTexts) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            if (contentFilter.matchesWhitelist(trimmed, whitelist)) continue
            if (isSfwRedditPornSubreddit(trimmed, contentFilter)) continue

            if (matchesExplicitSubreddit(trimmed)) return trimmed
            if (STANDALONE_NSFW_REGEX.matches(trimmed)) return trimmed
            if (CLICK_NSFW_GATE_PATTERNS.any { it.containsMatchIn(trimmed) }) return trimmed
            if (isSocialContext && STANDALONE_18_PLUS_REGEX.matches(trimmed)) return trimmed
            if (contentFilter.isAdultContent(trimmed)) return trimmed
        }
        return null
    }

    fun findAdultScreenMatch(
        packageName: String,
        activeUrl: String?,
        visibleTexts: Collection<String>,
        isBrowser: Boolean = false,
        contentFilter: ContentFilter = ContentFilter.Default,
        whitelist: Collection<String> = emptyList()
    ): String? {
        if (visibleTexts.isEmpty()) return null
        val isSocialContext = isSocialOrCommunityApp(packageName) || (isBrowser && isSocialOrCommunityUrl(activeUrl))

        val scannable = ArrayList<String>(visibleTexts.size)
        for (raw in visibleTexts) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            if (contentFilter.matchesWhitelist(trimmed, whitelist)) continue
            if (isSfwRedditPornSubreddit(trimmed, contentFilter)) continue

            if (matchesExplicitSubreddit(trimmed)) return trimmed
            if (UNIVERSAL_NSFW_SCREEN_PATTERNS.any { it.containsMatchIn(trimmed) }) return trimmed
            if (isSocialContext) {
                if (STANDALONE_18_PLUS_REGEX.matches(trimmed) || SOCIAL_MATURE_BADGE_REGEX.matches(trimmed)) {
                    return trimmed
                }
            }
            scannable.add(trimmed)
        }

        if (scannable.isEmpty()) return null
        return contentFilter.findAdultPageContent(scannable)
    }

    private fun extractHost(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (trimmed.isEmpty()) return null
        val withoutScheme = when {
            trimmed.startsWith("http://") -> trimmed.substringAfter("http://")
            trimmed.startsWith("https://") -> trimmed.substringAfter("https://")
            else -> trimmed
        }
        val authority = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val host = authority
            .substringBefore(':')
            .trimEnd('.')
            .removePrefix("www.")
        return host.takeIf { it.contains('.') && it.none(Char::isWhitespace) }
    }
}
