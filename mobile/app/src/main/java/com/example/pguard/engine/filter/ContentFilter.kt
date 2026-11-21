package com.example.pguard.engine.filter

import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure-Kotlin content classifier used by the blocking engine.
 *
 * Matching is split by input kind so a benign search ("sex education") is not
 * treated like adult content:
 *  - host parts are compared for equality against known adult domain signatures,
 *  - URL path / query and page text are matched against whole-word adult phrases,
 *  - free text tokens are compared for equality against domain signatures,
 *  - custom blocklist entries are subdomain-aware when they contain a dot and
 *    literal whole-word matches otherwise.
 *
 * No Android dependencies, so it is fully unit-testable on the JVM.
 */
class ContentFilter(
    private val adultDomainSignatures: Set<String> = DEFAULT_ADULT_DOMAIN_SIGNATURES,
    private val adultPatterns: List<Regex> = DEFAULT_ADULT_PATTERNS,
) {

    fun isAdultContent(rawInput: String): Boolean {
        val target = Target.parse(rawInput)
        if (target.isEmpty) return false

        if (target.hostTokens.any { it in adultDomainSignatures }) return true
        if (adultPatterns.any { it.containsMatchIn(target.searchableText) }) return true
        if (!target.isPlainText && ADULT_URL_PATTERNS.any { it.containsMatchIn(target.searchableText) }) return true
        if (target.isPlainText && target.textTokens.any { it in adultDomainSignatures }) return true
        return false
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

    private fun normalizeEntry(raw: String): String = raw.trim()
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .trimEnd('/')
        .trimEnd('.')

    private class Target(
        val host: String?,
        val hostTokens: List<String>,
        val searchableText: String,
        val textTokens: List<String>,
        val isPlainText: Boolean,
        val isEmpty: Boolean,
    ) {
        companion object {
            val EMPTY = Target(null, emptyList(), "", emptyList(), isPlainText = false, isEmpty = true)

            fun parse(raw: String): Target {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return EMPTY

                val lower = trimmed.lowercase(Locale.ROOT)
                val hasScheme = lower.startsWith("http://") || lower.startsWith("https://")
                val withoutScheme = if (hasScheme) lower.substringAfter("://") else lower

                val authority = withoutScheme
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')

                val host = authority
                    .substringBefore(':')
                    .trimEnd('.')
                    .removePrefix("www.")

                val hostIsValid = host.isNotBlank() && host.none { it.isWhitespace() }
                val looksLikeUrl = hasScheme || (hostIsValid && host.contains('.'))

                if (!looksLikeUrl) {
                    return Target(
                        host = null,
                        hostTokens = emptyList(),
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
                val searchable = listOf(
                    normalizedHost.orEmpty(),
                    decode(path),
                    decode(query),
                ).joinToString(" ").lowercase(Locale.ROOT)

                return Target(
                    host = normalizedHost,
                    hostTokens = normalizedHost
                        ?.split('.', '-', '_')
                        ?.filter { it.isNotBlank() }
                        ?: emptyList(),
                    searchableText = searchable,
                    textTokens = emptyList(),
                    isPlainText = false,
                    isEmpty = false,
                )
            }

            private fun decode(value: String): String = try {
                URLDecoder.decode(value, "UTF-8")
            } catch (_: Exception) {
                value
            }

            private fun tokenize(text: String): List<String> =
                WORD_PATTERN.findAll(text).map { it.value }.toList()

            private val WORD_PATTERN = Regex("[\\p{L}\\p{N}]+")
        }
    }

    companion object {
        private const val MAX_REGEX_CACHE_SIZE = 512
        private val WHITESPACE = Regex("\\s+")
        private val domainRegexCache = ConcurrentHashMap<String, Regex>()
        private val keywordRegexCache = ConcurrentHashMap<String, Regex>()

        private val DEFAULT_ADULT_DOMAIN_SIGNATURES = setOf(
            "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
            "chaturbate", "onlyfans", "stripchat", "livejasmin", "beeg",
            "spankbang", "eporner", "tube8", "bangbros", "brazzers",
            "rule34", "nhentai", "hentai", "cam4", "camsoda", "fapello",
            "hqporner", "motherless", "heavy-r", "bonga", "bongacams",
            "daftsex", "txxx", "thumbzilla", "nuvid", "pornhat", "pornktube",
            "tnaflix", "yespornplease", "porntrex", "fuq", "youjizz", "erome",
            "manyvids", "fansly", "voyeurweb", "fetlife", "freeones",
            "slutload", "twistys", "redgifs", "erothots", "coomer",
        )

        private val ADULT_URL_PATTERNS = listOf(
            Regex("""\bnsfw\b"""),
            Regex("""\bporn\b"""),
            Regex("""\bxxx\b"""),
            Regex("""\/r\/[a-z0-9_]*porn[a-z0-9_]*""", RegexOption.IGNORE_CASE),
            Regex("""\/r\/[a-z0-9_]*nsfw[a-z0-9_]*""", RegexOption.IGNORE_CASE)
        )

        private val DEFAULT_ADULT_PATTERNS = listOf(
            Regex("""\b(?:my|free|hot|top|best|hd|asian|gay|teen|amateur|real|live)?porn\w*""", RegexOption.IGNORE_CASE),
            Regex("""\br\/[a-z0-9_]*porn[a-z0-9_]*\b""", RegexOption.IGNORE_CASE),
            Regex("""\br\/[a-z0-9_]*nsfw[a-z0-9_]*\b""", RegexOption.IGNORE_CASE),
            Regex("""\b18\s*\+?\s*nsfw\b|\bnsfw\s*\+?\s*18\b""", RegexOption.IGNORE_CASE),
            Regex("""\bxxx+\b"""),
            Regex("""\bnsfw\s*(?:content|porn|xxx|nude|sex|video|pic|media|post|channel)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:fuck|fucking|fucked|fucks)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:cock|cocks|dick|dicks|pussy|pussies|penis|penises|vagina|vaginas)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:anal|anally|dildo|dildos)\b""", RegexOption.IGNORE_CASE),
            Regex("""\berotic\w*"""),
            Regex("""\bboobs?\b"""),
            Regex("""\btits\b"""),
            Regex("""\bblow\s?jobs?\b"""),
            Regex("""\bhand\s?jobs?\b"""),
            Regex("""\bmasturbat\w*"""),
            Regex("""\borgasms?\b"""),
            Regex("""\bcum\s?(?:shots?|ming)?\b"""),
            Regex("""\bdeep\s?throats?\b"""),
            Regex("""\bthree\s?somes?\b"""),
            Regex("""\bmilfs?\b"""),
            Regex("""\bshemales?\b"""),
            Regex("""\bgang\s?bangs?\b"""),
            Regex("""\bcream\s?pies?\b"""),
            Regex("""\bfemdom\b"""),
            Regex("""\bcuckold\w*"""),
            Regex("""\b(?:nude|nudes|nudity|naked)\b"""),
            Regex("""\bsex\s?(?:videos?|movies?|clips?)\b"""),
            Regex("""\bcam\s?girls?\b"""),
            Regex("""\bhardcore\s+sex\b"""),
            Regex("""\bx[\s-]?rated\b"""),
            Regex("""\bhentai\b"""),
            Regex("""\brule\s*34\b"""),
            Regex("""\badult\s+(?:games?|content|video)\b"""),
            Regex("""\bfree\s+sex\b"""),
        )

        val Default = ContentFilter()
    }
}
