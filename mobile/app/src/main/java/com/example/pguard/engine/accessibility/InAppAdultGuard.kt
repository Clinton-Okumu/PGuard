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
        "whore",
        "shemale",
        "ladyboy",
        "tranny",
        "transporn",
        "sissy",
        "femboy",
        "bukkake",
        "creampie",
        "deepthroat",
        "gangbang",
        "threesome",
        "orgasm",
        "masturbat",
        "dildo",
        "vibrator",
        "bondage",
        "femdom",
        "findom",
        "footfetish",
        "upskirt",
        "voyeur",
        "exhibitionist",
        "incest",
        "barelylegal",
        "horny",
        "erotic",
        "xxx",
        "sext",
        "dickpic",
        "titdrop",
        "boobjob",
        "paizuri",
        "futanari",
        "doujin",
        "nhentai",
        "pornhub",
        "xvideos",
        "chaturbate",
        "fansly",
        "manyvids",
        "bongacams",
        "stripchat",
        "bbw",
        "pawg",
        "gilf",
        "dilf",
        "pegging",
        "fisting",
        "squirt",
        "titfuck",
        "facefuck",
        "buttplug",
        "rimjob",
        "strapon",
        "gloryhole",
        "cumshot",
        "gooning",
        "gooncave"
    )

    private val CURATED_NSFW_SUBREDDITS = setOf(
        "ass",
        "bigasses",
        "booty",
        "sexy",
        "sex",
        "naked",
        "breasts",
        "bigtits",
        "smallboobs",
        "busty",
        "curvy",
        "amateur",
        "homemadexxx",
        "girlsfinishingthejob",
        "grool",
        "hardcore",
        "lesbians",
        "gaybrosgonewild",
        "femboys",
        "jav",
        "gooning",
        "gooncaves",
        "barelylegal",
        "gonewild",
        "gw",
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

    private val ADULT_VIEW_ID_TOKENS = setOf(
        "nsfw",
        "over_18",
        "over18",
        "mature_content",
        "mature_badge",
        "mature_tag",
        "age_gate",
        "sensitive_media",
        "sensitive_content",
        "adult_content"
    )

    internal val SUBREDDIT_REGEX = Regex(
        """(?<![\p{L}\p{N}_])/?r/([a-z0-9_]{2,})""",
        RegexOption.IGNORE_CASE
    )
    private val REDDIT_USER_REGEX = Regex(
        """(?<![\p{L}\p{N}_])/?u/[a-z0-9_\-]{2,}""",
        RegexOption.IGNORE_CASE
    )
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")
    internal val NSFW_WORD_REGEX = Regex("""(?<![\p{L}])nsfw(?![\p{L}])""", RegexOption.IGNORE_CASE)

    private val TOKEN_18_PLUS_REGEX = Regex(
        """(?<![\p{L}\p{N}])18\+(?![\p{L}\p{N}])"""
    )

    private val SOCIAL_MATURE_TAG_REGEX = Regex(
        """^(?:mature(?:\s*[•·|\-].*)?|.*[•·|\-]\s*mature|content\s+warning)$|\bover\s+18\b""",
        RegexOption.IGNORE_CASE
    )

    private val SAFE_SEARCH_OFF_REGEX = Regex(
        """\bsafe\s*search\s*:?\s*off\b""",
        RegexOption.IGNORE_CASE
    )

    private val SOCIAL_EXPLICIT_PHRASE_REGEX = Regex(
        """\bsexy\s+(?:ass|asses|boobs?|tits?|pussy|cock|dick|nudes?|naked|girls?|babes?|videos?|pics?)\b|\bstroke\s+and\s+poke\b""",
        RegexOption.IGNORE_CASE
    )

    private val INPUT_MATURE_PATTERNS = listOf(
        NSFW_WORD_REGEX,
        Regex("""\b(?:mature|adult|18\+)\s+content\b""", RegexOption.IGNORE_CASE)
    )

    private val PASSIVE_SETTINGS_TOGGLE_PREFIXES = listOf(
        "show mature content",
        "blur mature",
        "display media that may contain sensitive content",
        "mark media you post as containing material that may be sensitive",
        "allow access to age-restricted"
    )

    private val CLICK_NSFW_GATE_PATTERNS = listOf(
        NSFW_WORD_REGEX,
        Regex("""\byes,\s*i['’]?m\s+over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\byes,\s*i\s+am\s+over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\bi\s+am\s+18\s+or\s+older\b""", RegexOption.IGNORE_CASE),
        Regex("""\bi['’]?m\s+over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\bconfirm\s+(?:you\s+are\s+)?over\s+18\b""", RegexOption.IGNORE_CASE),
        Regex("""\byes,\s*view\s+profile\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:tap|click|press)\s+to\s+unblur\b""", RegexOption.IGNORE_CASE),
        Regex("""\bunblur\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:view|see|show|continue\s+to|include)\s+(?:nsfw|mature|sensitive)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bblur\s+mature\b""", RegexOption.IGNORE_CASE),
        Regex("""\bshow\s+mature\s+content\b""", RegexOption.IGNORE_CASE),
        Regex("""\binclude\s+nsfw\s+results\b""", RegexOption.IGNORE_CASE),
        Regex("""\ballow\s+access\s+to\s+age[\s\-]?restricted\b""", RegexOption.IGNORE_CASE),
        Regex("""\bage[\s\-]?restricted\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdisplay\s+media\s+that\s+may\s+contain\s+sensitive\s+content\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:potentially\s+)?sensitive\s+(?:content|media|material)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:view|show)\s+sensitive\s+content\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:turn\s+off|disable)\s+safe\s*search\b|\bsafe\s*search\s*:?\s*off\b""", RegexOption.IGNORE_CASE)
    )

    private val UNIVERSAL_NSFW_SCREEN_PATTERNS = listOf(
        NSFW_WORD_REGEX,
        Regex("""\bmarked\s+as\s+nsfw\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmature\s+(?:content|media|posts?|communit(?:y|ies)|profile|account|audience|themes?|material|warning|filter|results|channel|feed|videos?|images?|photos?)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:marked|flagged|labeled|tagged)\s+as\s+(?:mature|adult|explicit|sensitive|nsfw|18\+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:contains?|includes?|features?)\s+(?:mature|adult|explicit|nsfw|18\+|sexually\s+explicit)\s+(?:content|media|material|themes?|imagery)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:tap|click|press)\s+to\s+(?:unblur|(?:view|see|reveal|show)\s+(?:mature|nsfw|sensitive|adult|18\+|explicit))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:blurred|hidden)\s+(?:because|due\s+to|for)\b.*\b(?:mature|nsfw|sensitive|adult|18\+|explicit)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcontent\s+warning\b.*\b(?:adult|nudity|sexual|mature|nsfw|sensitive|18\+|explicit)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:potentially\s+)?sensitive\s+(?:content|media|material)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:adult|explicit)\s+(?:content|media|material|only|warning|profile|community|channel)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bage[\s\-]?restricted\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:you\s+must\s+be\s+(?:at\s+least\s+)?18(?:\+|(?:\s+or\s+older))?|confirm\s+(?:you\s+are\s+)?over\s+18|are\s+you\s+(?:18\s+or\s+older|over\s+18)|yes,?\s*i['’]?(?:m|\s+am)\s+(?:over\s+18|18\s+or\s+older)|i\s+am\s+18\s+or\s+older|must\s+be\s+18\+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b18\+\s*(?:only|content|community|channel|nsfw|mature|adult|media|post|profile|warning|restricted)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:mature|adult|nsfw|explicit)\s*\(?18\+\)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthis\s+community\s+is\s+marked\s+as\s+mature\b""", RegexOption.IGNORE_CASE),
        Regex("""\bused\s+to\s+spread\s+pornographic\b""", RegexOption.IGNORE_CASE)
    )

    private fun isPassiveSettingsToggleLabel(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        return PASSIVE_SETTINGS_TOGGLE_PREFIXES.any { lower.startsWith(it) || lower.contains(it) }
    }

    private val EXCLUDED_PACKAGE_KEYWORDS = listOf(
        // System, telephony, launchers, keyboards
        "dialer", "incallui", "launcher", "trebuchet", "quickstep",
        "inputmethod", "gboard", "swiftkey", "honeyboard", "fleksy",
        "gingerkeyboard", "keyboard", "pixelboard", "miui.securitycenter",
        "wellbeing",
        // Banking, mobile money, wallets, and finance apps
        "bank", "mpesa", "safaricom", "equity", "kcb", "coop", "absa", "ncba",
        "stanbic", "stanchart", "dtb", "imbank", "familybank", "sacco",
        "pesalink", "airtelmoney", "paypal", "wise", "revolut", "cashapp",
        "venmo", "wallet", "finance", "fintech", "payment", "paystack",
        "flutterwave", "chipper", "kuda", "opay", "palmpay", "moniepoint",
        "chase", "wellsfargo", "citibank", "hsbc", "barclays", "monzo",
        "starling", "nubank", "binance", "coinbase",
        // Password managers & authenticators
        "bitwarden", "1password", "lastpass", "keepass", "dashlane",
        "authenticator", "authy",
        // Safe system/productivity utilities
        "calculator", "deskclock", "calendar", "contacts", "camera",
        "gallery", "photos", "documentsui", "maps", "spotify", "weather"
    )

    fun isExcludedFromInAppAdultScan(packageName: String, ownPackageName: String): Boolean {
        val pkg = packageName.trim().lowercase(Locale.ROOT)
        val own = ownPackageName.trim().lowercase(Locale.ROOT)
        if (pkg.isBlank() || pkg == own) return true
        if (pkg in EXCLUDED_EXACT_PACKAGES || pkg.startsWith("com.android.systemui.")) return true
        if (pkg.endsWith(".ime") || pkg.contains(".ime.")) return true
        return EXCLUDED_PACKAGE_KEYWORDS.any { pkg.contains(it) }
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
        if (INPUT_MATURE_PATTERNS.any { it.containsMatchIn(cleaned) }) return trimmed
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
            if (CLICK_NSFW_GATE_PATTERNS.any { it.containsMatchIn(trimmed) }) return trimmed
            if (isSocialContext && (TOKEN_18_PLUS_REGEX.containsMatchIn(trimmed) || SOCIAL_MATURE_TAG_REGEX.containsMatchIn(trimmed))) {
                return trimmed
            }
            if (contentFilter.isAdultContent(trimmed)) return trimmed
        }
        return null
    }

    fun isAdultViewId(viewId: String?): Boolean {
        if (viewId.isNullOrBlank()) return false
        val lower = viewId.trim().lowercase(Locale.ROOT)
        val suffix = lower.substringAfter(":id/", lower)
        if (suffix in ADULT_VIEW_ID_TOKENS) return true
        return ADULT_VIEW_ID_TOKENS.any { token ->
            suffix.contains("_$token") || suffix.contains("${token}_")
        }
    }

    fun findAdultScreenMatch(
        packageName: String,
        activeUrl: String?,
        visibleTexts: Collection<String>,
        viewIds: Collection<String> = emptyList(),
        isBrowser: Boolean = false,
        contentFilter: ContentFilter = ContentFilter.Default,
        whitelist: Collection<String> = emptyList()
    ): String? {
        for (viewId in viewIds) {
            if (isAdultViewId(viewId) && !contentFilter.matchesWhitelist(viewId, whitelist)) {
                return "NSFW badge"
            }
        }
        if (visibleTexts.isEmpty()) return null
        val inferredSocialContext = isSocialOrCommunityApp(packageName) ||
            (isBrowser && isSocialOrCommunityUrl(activeUrl)) ||
            (isBrowser && visibleTexts.any { raw ->
                val t = raw.trim()
                t.isNotEmpty() && (
                    isSocialOrCommunityUrl(t) ||
                        t.contains("reddit.com/", ignoreCase = true) ||
                        t.contains("x.com/", ignoreCase = true) ||
                        t.contains("twitter.com/", ignoreCase = true) ||
                        t.contains("discord.com/", ignoreCase = true) ||
                        SUBREDDIT_REGEX.containsMatchIn(t) ||
                        REDDIT_USER_REGEX.containsMatchIn(t)
                    )
            })

        var hasSafeSearchOff = false
        var socialAdultClueMatch: String? = null

        val scannable = ArrayList<String>(visibleTexts.size)
        for (raw in visibleTexts) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            if (contentFilter.matchesWhitelist(trimmed, whitelist)) continue
            if (isSfwRedditPornSubreddit(trimmed, contentFilter)) continue
            if (isPassiveSettingsToggleLabel(trimmed)) continue

            if (matchesExplicitSubreddit(trimmed)) return trimmed
            if (UNIVERSAL_NSFW_SCREEN_PATTERNS.any { it.containsMatchIn(trimmed) }) return trimmed
            if (inferredSocialContext) {
                if (TOKEN_18_PLUS_REGEX.containsMatchIn(trimmed) || SOCIAL_MATURE_TAG_REGEX.containsMatchIn(trimmed)) {
                    return trimmed
                }
                if (SAFE_SEARCH_OFF_REGEX.containsMatchIn(trimmed)) {
                    hasSafeSearchOff = true
                }
                if (socialAdultClueMatch == null && (
                        SOCIAL_EXPLICIT_PHRASE_REGEX.containsMatchIn(trimmed) ||
                            ContentFilter.CONTEXTUAL_ADULT_PATTERNS.any { it.containsMatchIn(trimmed) }
                        )
                ) {
                    socialAdultClueMatch = trimmed
                }
            }
            scannable.add(trimmed)
        }

        if (inferredSocialContext && hasSafeSearchOff && socialAdultClueMatch != null) {
            return socialAdultClueMatch
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
