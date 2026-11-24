package com.example.pguard.engine.filter

import com.example.pguard.engine.accessibility.InAppAdultGuard
import java.net.IDN
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure-Kotlin content classifier used by the blocking engine.
 *
 * Matching is split by input kind and confidence tier so benign searches
 * ("sex education") and isolated profanity/medical terms in page bodies do not
 * false-trigger, while explicit URLs, queries, domains, and dense explicit
 * pages are caught accurately:
 *  - host labels & tokens are compared against adult TLDs and unambiguous adult
 *    domain signatures (so "heavy-r.com" matches while "notpornhub.com" does not),
 *  - URLs and search-bar inputs use strict pattern matching (including iterative
 *    percent-decoding and Unicode/punycode normalization),
 *  - visible page body text uses tiered scoring (unambiguous phrases block at
 *    weight 3; contextual profanity/anatomy terms require 3+ distinct hits),
 *  - custom blocklist and whitelist entries are subdomain-aware for domains and
 *    whole-word/plural matches for keywords.
 *
 * No Android dependencies, so it is fully unit-testable on the JVM.
 */
class ContentFilter(
    private val adultDomainSignatures: Set<String> = DEFAULT_ADULT_DOMAIN_SIGNATURES,
    private val plainTextDomainSignatures: Set<String> = adultDomainSignatures - DOMAIN_ONLY_ADULT_SIGNATURES,
    private val adultPatterns: List<Regex> = DEFAULT_ADULT_PATTERNS,
    private val strongPagePatterns: List<Regex> = STRONG_ADULT_PATTERNS,
    private val contextualPagePatterns: List<Regex> = CONTEXTUAL_ADULT_PATTERNS,
    private val adultTlds: Set<String> = DEFAULT_ADULT_TLDS,
) {

    fun isAdultContent(rawInput: String): Boolean {
        val cleaned = InAppAdultGuard.stripSfwRedditSubreddits(rawInput)
        val target = Target.parse(cleaned)
        if (target.isEmpty) return false

        if (target.hostTld != null && target.hostTld in adultTlds) return true
        if (target.hostLabels.any { it in adultDomainSignatures }) return true
        if (target.hostTokens.any { it in adultDomainSignatures }) return true
        if (adultPatterns.any { it.containsMatchIn(target.searchableText) }) return true
        if (!target.isPlainText && ADULT_URL_PATTERNS.any { it.containsMatchIn(target.searchableText) }) return true
        if (target.isPlainText && target.textTokens.any { it in plainTextDomainSignatures }) return true
        return false
    }

    /**
     * Evaluates a DNS hostname or bare domain against adult TLDs and domain signatures.
     */
    fun isAdultDomain(rawDomain: String): Boolean {
        val normalized = normalizeHost(rawDomain) ?: return false
        val labels = normalized.split('.').filter { it.isNotBlank() }
        if (labels.size >= 2 && labels.last() in adultTlds) return true
        if (labels.any { it in adultDomainSignatures }) return true
        val tokens = normalized.split('.', '-', '_').filter { it.isNotBlank() }
        return tokens.any { it in adultDomainSignatures }
    }

    /**
     * Tiered page-body scanner for rendered browser text.
     *
     * Immediate block when any node matches:
     *  - an adult TLD, adult domain signature, explicit URL pattern, or a strong adult pattern.
     * Contextual terms (isolated profanity or medical anatomy terms) accumulate score (+1 per
     * distinct contextual rule hit across the visible page) and only block at threshold >= 3.
     */
    fun findAdultPageContent(pageTexts: Collection<String>): String? {
        if (pageTexts.isEmpty()) return null
        val matchedContextualRules = HashSet<Int>()

        for (rawText in pageTexts) {
            val cleaned = InAppAdultGuard.stripSfwRedditSubreddits(rawText)
            val target = Target.parse(cleaned)
            if (target.isEmpty) continue

            if (target.hostTld != null && target.hostTld in adultTlds) return rawText
            if (target.hostLabels.any { it in adultDomainSignatures }) return rawText
            if (target.hostTokens.any { it in adultDomainSignatures }) return rawText
            if (!target.isPlainText && ADULT_URL_PATTERNS.any { it.containsMatchIn(target.searchableText) }) return rawText
            if (target.isPlainText && target.textTokens.any { it in plainTextDomainSignatures }) return rawText
            if (strongPagePatterns.any { it.containsMatchIn(target.searchableText) }) return rawText

            contextualPagePatterns.forEachIndexed { idx, pattern ->
                if (pattern.containsMatchIn(target.searchableText)) {
                    matchedContextualRules.add(idx)
                }
            }
            if (matchedContextualRules.size >= PAGE_ADULT_SCORE_THRESHOLD) {
                return rawText
            }
        }
        return null
    }

    fun matchesCustomBlocklist(rawInput: String, blocklist: Collection<String>): Boolean {
        if (blocklist.isEmpty()) return false
        val target = Target.parse(rawInput)
        if (target.isEmpty) return false
        return blocklist.any { entry -> matchesEntry(target, entry) }
    }

    fun matchesWhitelist(rawInput: String, whitelist: Collection<String>): Boolean {
        if (whitelist.isEmpty()) return false
        val target = Target.parse(rawInput)
        if (target.isEmpty) return false
        return whitelist.any { entry -> matchesEntry(target, entry) }
    }

    /**
     * Evaluates a DNS domain exclusively against domain-style entries (containing '.')
     * in a custom blocklist or whitelist.
     */
    fun matchesDomainEntries(rawDomain: String, entries: Collection<String>): Boolean {
        if (entries.isEmpty()) return false
        val host = normalizeHost(rawDomain) ?: return false
        val withoutWww = host.removePrefix("www.")
        return entries.any { rawEntry ->
            val entry = normalizeEntry(rawEntry)
            if (!entry.contains('.')) return@any false
            val domain = entry.substringBefore('/').substringBefore('?').substringBefore('#')
            hostMatchesDomain(host, domain) || hostMatchesDomain(withoutWww, domain)
        }
    }

    private fun matchesEntry(target: Target, rawEntry: String): Boolean {
        val entry = normalizeEntry(rawEntry)
        if (entry.isEmpty()) return false

        return if (entry.contains('.')) {
            val domain = entry.substringBefore('/').substringBefore('?').substringBefore('#')
            hostMatchesDomain(target.host, domain) || textContainsDomain(target.searchableText, domain)
        } else {
            keywordRegex(entry).containsMatchIn(target.searchableText)
        }
    }

    private fun hostMatchesDomain(host: String?, domain: String): Boolean {
        if (host.isNullOrBlank() || domain.isBlank()) return false
        return host == domain || host.endsWith(".$domain")
    }

    private fun textContainsDomain(text: String, domain: String): Boolean {
        if (text.isBlank() || domain.isBlank()) return false
        val pattern = domainRegexCache[domain] ?: run {
            if (domainRegexCache.size >= MAX_REGEX_CACHE_SIZE) {
                domainRegexCache.clear()
            }
            val compiled = Regex(
                "(?<![\\p{L}\\p{N}.\\-])" + Regex.escape(domain) + "(?![\\p{L}\\p{N}\\-])"
            )
            domainRegexCache.putIfAbsent(domain, compiled) ?: compiled
        }
        return pattern.containsMatchIn(text)
    }

    private fun keywordRegex(entry: String): Regex {
        return keywordRegexCache[entry] ?: run {
            if (keywordRegexCache.size >= MAX_REGEX_CACHE_SIZE) {
                keywordRegexCache.clear()
            }
            val escaped = entry.split(WHITESPACE)
                .filter { it.isNotBlank() }
                .joinToString("""\s+""") { Regex.escape(it) }
            val compiled = Regex("(?<![\\p{L}\\p{N}])$escaped(?:s|es)?(?![\\p{L}\\p{N}])")
            keywordRegexCache.putIfAbsent(entry, compiled) ?: compiled
        }
    }

    private fun normalizeEntry(raw: String): String = normalizeText(raw)
        .trim()
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .trimEnd('/')
        .trimEnd('.')

    private class Target(
        val host: String?,
        val hostLabels: List<String>,
        val hostTokens: List<String>,
        val hostTld: String?,
        val searchableText: String,
        val textTokens: List<String>,
        val isPlainText: Boolean,
        val isEmpty: Boolean,
    ) {
        companion object {
            val EMPTY = Target(
                host = null,
                hostLabels = emptyList(),
                hostTokens = emptyList(),
                hostTld = null,
                searchableText = "",
                textTokens = emptyList(),
                isPlainText = false,
                isEmpty = true
            )

            fun parse(raw: String): Target {
                val cleaned = normalizeText(raw).trim()
                if (cleaned.isEmpty()) return EMPTY

                val lower = cleaned.lowercase(Locale.ROOT)
                val hasScheme = lower.startsWith("http://") || lower.startsWith("https://")
                val withoutScheme = if (hasScheme) lower.substringAfter("://") else lower

                val authority = withoutScheme
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')

                val rawHost = authority
                    .substringBefore(':')
                    .trimEnd('.')
                    .removePrefix("www.")

                val host = if (rawHost.contains("xn--")) {
                    decodePunycode(rawHost)
                } else {
                    rawHost
                }

                val hostIsValid = host.isNotBlank() && host.none { it.isWhitespace() }
                val looksLikeUrl = hasScheme || (hostIsValid && host.contains('.'))

                if (!looksLikeUrl) {
                    return Target(
                        host = null,
                        hostLabels = emptyList(),
                        hostTokens = emptyList(),
                        hostTld = null,
                        searchableText = lower,
                        textTokens = tokenize(lower),
                        isPlainText = true,
                        isEmpty = false,
                    )
                }

                val afterAuthority = withoutScheme.removePrefix(authority)
                val path = afterAuthority.substringBefore('?').substringBefore('#')
                val query = afterAuthority.substringAfter('?', "").substringBefore('#')

                val normalizedHost = host.takeIf { hostIsValid }
                val labels = normalizedHost
                    ?.split('.')
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
                val tokens = normalizedHost
                    ?.split('.', '-', '_')
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
                val tld = if (labels.size >= 2) labels.last() else null

                val searchable = listOf(
                    normalizedHost.orEmpty(),
                    normalizeText(decodeUrlIteratively(path)),
                    normalizeText(decodeUrlIteratively(query)),
                ).joinToString(" ").lowercase(Locale.ROOT)

                return Target(
                    host = normalizedHost,
                    hostLabels = labels,
                    hostTokens = tokens,
                    hostTld = tld,
                    searchableText = searchable,
                    textTokens = emptyList(),
                    isPlainText = false,
                    isEmpty = false,
                )
            }

            private fun decodeUrlIteratively(value: String): String {
                var current = value
                repeat(3) {
                    val decoded = try {
                        URLDecoder.decode(current, "UTF-8")
                    } catch (_: Exception) {
                        return current
                    }
                    if (decoded == current) return current
                    current = decoded
                }
                return current
            }

            private fun tokenize(text: String): List<String> {
                val words = WORD_PATTERN.findAll(text).map { it.value }
                val hyphenated = HYPHENATED_WORD_PATTERN.findAll(text).map { it.value }
                return (words + hyphenated).distinct().toList()
            }

            private val WORD_PATTERN = Regex("[\\p{L}\\p{N}]+")
            private val HYPHENATED_WORD_PATTERN = Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)+")
        }
    }

    companion object {
        private const val MAX_REGEX_CACHE_SIZE = 512
        private const val PAGE_ADULT_SCORE_THRESHOLD = 3
        private val ZERO_WIDTH_CHARS = Regex("[\\u200B-\\u200D\\uFEFF]")
        private val WHITESPACE = Regex("\\s+")
        private val domainRegexCache = ConcurrentHashMap<String, Regex>()
        private val keywordRegexCache = ConcurrentHashMap<String, Regex>()

        private fun normalizeText(input: String): String {
            val stripped = ZERO_WIDTH_CHARS.replace(input, "")
            return Normalizer.normalize(stripped, Normalizer.Form.NFKC)
        }

        private fun decodePunycode(host: String): String = try {
            normalizeText(IDN.toUnicode(host)).lowercase(Locale.ROOT)
        } catch (_: Exception) {
            host
        }

        private fun normalizeHost(rawDomain: String): String? {
            val trimmed = normalizeText(rawDomain)
                .trim()
                .lowercase(Locale.ROOT)
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringBefore(':')
                .trimEnd('.')
            if (trimmed.isBlank() || trimmed.any { it.isWhitespace() }) return null
            return if (trimmed.contains("xn--")) decodePunycode(trimmed) else trimmed
        }

        val DEFAULT_ADULT_TLDS = setOf("xxx", "porn", "sex", "adult", "sexy")

        private val DEFAULT_ADULT_DOMAIN_SIGNATURES = setOf(
            "pornhub", "pornhubpremium", "xvideos", "xvideos2", "xvideos3", "xnxx",
            "xhamster", "redtube", "youporn", "chaturbate", "onlyfans", "stripchat",
            "livejasmin", "beeg", "spankbang", "spankwire", "eporner", "tube8",
            "bangbros", "brazzers", "brazzersnetwork", "rule34", "nhentai", "hentai",
            "hentaihaven", "hanime", "fakku", "tsumino", "hitomi", "e-hentai", "exhentai",
            "gelbooru", "danbooru", "e621", "cam4", "camsoda", "myfreecams", "imlive",
            "flirt4free", "streamate", "xcams", "camster", "dirtyroulette", "jerkmate",
            "fapello", "faphouse", "hqporner", "motherless", "heavy-r",
            "bongacams", "daftsex", "txxx", "thumbzilla", "nuvid", "pornhat",
            "pornktube", "pornone", "porndoe", "porn300", "porntube", "porn00",
            "porndish", "pornbox", "gotporn", "gotgayporn", "manporn", "boyfriendtv",
            "gaymaletube", "tnaflix", "yespornplease", "porntrex", "fuq", "youjizz",
            "erome", "manyvids", "fansly", "fansone", "candfans", "loyalfans",
            "justforfans", "admireme", "voyeurweb", "fetlife", "freeones", "slutload",
            "twistys", "redgifs", "erothots", "coomer", "kemono", "simpcity", "thothub",
            "literotica", "sexstories", "keezmovies", "extremetube", "mofos",
            "realitykings", "naughtyamerica", "evilangel", "teamskeet", "digitalplayground",
            "hclips", "upornia", "vjav", "javhd", "javmost", "javdoe", "missav", "jable",
            "supjav", "empflix", "drtuber", "alohatube", "sunporno", "4tube", "tubegalore",
            "ixxx", "bdsmstreak"
        )

        /**
         * Signatures that match adult domains/URLs (e.g. motherless.com, hitomi.la) but are
         * ordinary words, names, or short ambiguous tokens in plain text and must not block
         * everyday sentences or banking/chat screens.
         */
        private val DOMAIN_ONLY_ADULT_SIGNATURES = setOf(
            "motherless", "hitomi", "kemono", "beeg", "jable", "admireme",
            "fuq", "coomer", "cam4", "e621", "tube8", "4tube", "mofos",
            "twistys", "erome", "missav", "vjav", "javhd", "javmost", "javdoe", "supjav"
        )

        private val ADULT_URL_PATTERNS = listOf(
            Regex("""\bnsfw\b"""),
            Regex("""\bporn\b"""),
            Regex("""\bxxx\b"""),
            Regex("""\/r\/[a-z0-9_]*porn[a-z0-9_]*""", RegexOption.IGNORE_CASE),
            Regex("""\/r\/[a-z0-9_]*nsfw[a-z0-9_]*""", RegexOption.IGNORE_CASE)
        )

        val STRONG_ADULT_PATTERNS = listOf(
            Regex("""\b(?:my|free|hot|top|best|hd|asian|gay|teen|amateur|real|live)?porn\w*""", RegexOption.IGNORE_CASE),
            Regex("""\br\/[a-z0-9_]*porn[a-z0-9_]*\b""", RegexOption.IGNORE_CASE),
            Regex("""\br\/[a-z0-9_]*nsfw[a-z0-9_]*\b""", RegexOption.IGNORE_CASE),
            Regex("""\b18\s*\+?\s*nsfw\b|\bnsfw\s*\+?\s*18\b""", RegexOption.IGNORE_CASE),
            Regex("""\bxxx\b"""),
            Regex("""\bnsfw\s*(?:content|porn|xxx|nude|sex|video|pic|media|post|channel)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bblow\s?jobs?\b"""),
            Regex("""\bhand\s?jobs?\b"""),
            Regex("""\bcum\s?(?:shots?|ming|slut\w*)\b"""),
            Regex("""\bdeep\s?throats?\b"""),
            Regex("""\bmilfs?\b"""),
            Regex("""\bshemales?\b"""),
            Regex("""\bgang\s?bangs?\b"""),
            Regex("""\bcream\s?pies?\b"""),
            Regex("""\bfemdom\b"""),
            Regex("""\bcuckold\w*"""),
            Regex("""\bsex\s?(?:videos?|movies?|clips?)\b"""),
            Regex("""\bcam\s?girls?\b"""),
            Regex("""\bhardcore\s+sex\b"""),
            Regex("""\bx[\s-]?rated\b"""),
            Regex("""\bhentai\b"""),
            Regex("""\brule\s*34\b"""),
            Regex("""\badult\s+(?:games?|content|video)\b"""),
            Regex("""\bfree\s+sex\b"""),
        )

        val CONTEXTUAL_ADULT_PATTERNS = listOf(
            Regex("""\b(?:fuck|fucking|fucked|fucks)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:cock|cocks|dick|dicks|pussy|pussies|penis|penises|vagina|vaginas)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:anal|anally|dildo|dildos)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bcum\b(?!\s+laude\b)"""),
            Regex("""\berotic\w*"""),
            Regex("""\bboobs?\b"""),
            Regex("""\btits\b"""),
            Regex("""\bmasturbat\w*"""),
            Regex("""\borgasms?\b"""),
            Regex("""\bthree\s?somes?\b"""),
            Regex("""\b(?:nude|nudes|nudity|naked)\b"""),
            Regex("""\bsexy\s+(?:ass|asses|boobs?|tits?|pussy|cock|dick|nudes?|naked)\b""", RegexOption.IGNORE_CASE),
        )

        private val DEFAULT_ADULT_PATTERNS = STRONG_ADULT_PATTERNS + CONTEXTUAL_ADULT_PATTERNS

        val Default = ContentFilter()
    }
}
