package com.example.pguard.engine.accessibility

import com.example.pguard.engine.filter.ContentFilter
import java.util.Locale

/**
 * Pure-Kotlin validator and normalizer for Blocklist & Whitelist domains, keywords, and apps.
 */
object BlocklistRuleValidator {
    const val MIN_KEYWORD_LENGTH = 3
    const val MAX_RULE_LENGTH = 120

    enum class EntryKind {
        WEBSITE,
        KEYWORD
    }

    sealed class RuleValidationResult {
        data class Valid(val normalizedValue: String, val kind: EntryKind) : RuleValidationResult()
        data class Invalid(val error: String) : RuleValidationResult()
    }

    private val HOSTNAME_CHAR_REGEX = Regex("^[a-z0-9.-]+$")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private val GENERIC_DISALLOWED_TOKENS = setOf(
        "com", "net", "org", "edu", "gov", "io", "co", "www",
        "http", "https", "html", "htm", "web", "url", "ftp",
        "mailto", "tel", "about", "blank", "chrome", "android"
    )

    fun normalizeWebsiteOrKeyword(raw: String): Pair<String, EntryKind> {
        var value = raw.trim().lowercase(Locale.ROOT)
        if (value.isEmpty()) return "" to EntryKind.KEYWORD

        val hasWhitespace = value.any { it.isWhitespace() }
        val looksLikeWebsite = !hasWhitespace && (
            value.startsWith("http://") ||
                value.startsWith("https://") ||
                value.startsWith("www.") ||
                value.contains('.')
            )

        if (!looksLikeWebsite) {
            return value.replace(MULTI_SPACE_REGEX, " ") to EntryKind.KEYWORD
        }

        value = value.removePrefix("https://").removePrefix("http://")
        val delimiterIdx = value.indexOfAny(charArrayOf('/', '?', '#'))
        if (delimiterIdx != -1) {
            value = value.substring(0, delimiterIdx)
        }
        val colonIdx = value.lastIndexOf(':')
        if (colonIdx != -1) {
            value = value.substring(0, colonIdx)
        }
        value = value.removePrefix("www.").trimEnd('.')
        return value to EntryKind.WEBSITE
    }

    fun validateRule(
        raw: String,
        isWhitelist: Boolean,
        existingBlockedRules: Collection<String> = emptySet(),
        existingWhitelistedRules: Collection<String> = emptySet(),
        blockAdultContent: Boolean = true,
        contentFilter: ContentFilter = ContentFilter.Default
    ): RuleValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return RuleValidationResult.Invalid("Enter a website domain or keyword.")
        }

        val (normalized, kind) = normalizeWebsiteOrKeyword(trimmed)
        if (normalized.isEmpty()) {
            return RuleValidationResult.Invalid("Enter a valid website domain or keyword.")
        }
        if (normalized.length < MIN_KEYWORD_LENGTH) {
            return RuleValidationResult.Invalid("Must be at least $MIN_KEYWORD_LENGTH characters.")
        }
        if (normalized.length > MAX_RULE_LENGTH) {
            return RuleValidationResult.Invalid("Must be $MAX_RULE_LENGTH characters or fewer.")
        }
        if (normalized in GENERIC_DISALLOWED_TOKENS) {
            return RuleValidationResult.Invalid("\"$normalized\" is too generic. Enter a specific domain or word.")
        }

        if (kind == EntryKind.WEBSITE) {
            if (!normalized.contains('.') ||
                normalized.startsWith('.') ||
                normalized.endsWith('.') ||
                normalized.contains("..") ||
                !HOSTNAME_CHAR_REGEX.matches(normalized)
            ) {
                return RuleValidationResult.Invalid("Enter a valid website domain (e.g. reddit.com).")
            }
        }

        val normalizedBlocked = existingBlockedRules.map { normalizeWebsiteOrKeyword(it).first }.toSet()
        val normalizedWhitelisted = existingWhitelistedRules.map { normalizeWebsiteOrKeyword(it).first }.toSet()

        if (isWhitelist) {
            val isExplicitAdult = blockAdultContent && (
                contentFilter.isAdultContent(normalized) ||
                    (kind == EntryKind.WEBSITE && contentFilter.isAdultDomain(normalized))
                )
            if (isExplicitAdult) {
                return RuleValidationResult.Invalid("Adult domains and explicit keywords cannot be whitelisted.")
            }
            if (normalized in normalizedBlocked) {
                return RuleValidationResult.Invalid("\"$normalized\" is in your Blocklist. Remove it from Blocklist first.")
            }
            if (normalized in normalizedWhitelisted) {
                return RuleValidationResult.Invalid("\"$normalized\" is already in your Whitelist.")
            }
        } else {
            if (normalized in normalizedBlocked) {
                return RuleValidationResult.Invalid("\"$normalized\" is already in your Blocklist.")
            }
        }

        return RuleValidationResult.Valid(normalizedValue = normalized, kind = kind)
    }

    fun isUnsupportedBrowserBlockedFromWhitelist(
        packageName: String,
        blockUnsupportedBrowsersEnabled: Boolean
    ): Boolean {
        return blockUnsupportedBrowsersEnabled && BrowserInspector.isUnsupportedBrowser(packageName, null)
    }
}
