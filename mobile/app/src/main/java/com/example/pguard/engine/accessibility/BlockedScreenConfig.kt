package com.example.pguard.engine.accessibility

import com.example.pguard.engine.filter.ContentFilter
import java.net.URI
import java.util.Locale

/**
 * Pure-Kotlin configuration helpers, validators, and overlay resolution rules
 * for PGuard's Customize Blocked Screen feature.
 */
object BlockedScreenConfig {
    const val DEFAULT_MESSAGE = "This page is blocked."
    const val MAX_MESSAGE_LENGTH = 300

    sealed class ValidationResult {
        data class Valid(val normalized: String) : ValidationResult()
        data class Invalid(val error: String) : ValidationResult()
    }

    fun messageLength(raw: String): Int = raw.codePointCount(0, raw.length)

    fun validateMessage(raw: String): ValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Message cannot be empty.")
        }
        if (messageLength(trimmed) > MAX_MESSAGE_LENGTH) {
            return ValidationResult.Invalid("Message must be $MAX_MESSAGE_LENGTH characters or fewer.")
        }
        return ValidationResult.Valid(trimmed)
    }

    fun validateRedirectUrl(
        raw: String,
        contentFilter: ContentFilter = ContentFilter.Default,
        customBlocklist: Set<String> = emptySet(),
        blockAdultContent: Boolean = true,
        blockShorts: Boolean = false,
        blockWhatsAppChannels: Boolean = false,
        blockInstagramReels: Boolean = false,
        blockTikTok: Boolean = false
    ): ValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Valid("")
        }
        if (trimmed.any { it.isWhitespace() }) {
            return ValidationResult.Invalid("Redirect URL cannot contain spaces.")
        }

        val schemeIdx = trimmed.indexOf("://")
        val candidate = when {
            schemeIdx != -1 -> {
                val scheme = trimmed.substring(0, schemeIdx).lowercase(Locale.ROOT)
                if (scheme != "http" && scheme != "https") {
                    return ValidationResult.Invalid("Redirect URL must start with http:// or https://.")
                }
                scheme + trimmed.substring(schemeIdx)
            }
            trimmed.contains(":") -> {
                return ValidationResult.Invalid("Redirect URL must use http:// or https://.")
            }
            else -> "https://$trimmed"
        }

        val parsed = try {
            URI(candidate)
        } catch (_: Exception) {
            return ValidationResult.Invalid("Enter a valid website URL (e.g. https://example.com).")
        }

        val parsedScheme = parsed.scheme?.lowercase(Locale.ROOT)
        val host = parsed.host?.lowercase(Locale.ROOT)
        if ((parsedScheme != "http" && parsedScheme != "https") ||
            host.isNullOrBlank() ||
            !host.contains('.') ||
            host.startsWith('.') ||
            host.endsWith('.') ||
            host.contains("..")
        ) {
            return ValidationResult.Invalid("Enter a valid website URL (e.g. https://example.com).")
        }

        if (blockAdultContent && contentFilter.isAdultContent(candidate)) {
            return ValidationResult.Invalid("Redirect URL cannot point to blocked adult content.")
        }
        if (customBlocklist.isNotEmpty() && contentFilter.matchesCustomBlocklist(candidate, customBlocklist)) {
            return ValidationResult.Invalid("Redirect URL matches an item in your Blocklist.")
        }

        val lower = candidate.lowercase(Locale.ROOT)
        if (blockTikTok && lower.contains("tiktok.com")) {
            return ValidationResult.Invalid("Redirect URL cannot point to TikTok while TikTok blocking is enabled.")
        }
        if (blockShorts && (
                lower.contains("youtube.com/shorts") ||
                    lower.contains("m.youtube.com/shorts") ||
                    lower.contains("youtu.be/shorts")
                )
        ) {
            return ValidationResult.Invalid("Redirect URL cannot point to YouTube Shorts while Shorts blocking is enabled.")
        }
        if (blockInstagramReels && (
                lower.contains("instagram.com/reel") ||
                    lower.contains("instagram.com/reels")
                )
        ) {
            return ValidationResult.Invalid("Redirect URL cannot point to Instagram Reels while Reels blocking is enabled.")
        }
        if (blockWhatsAppChannels && lower.contains("whatsapp.com/channel")) {
            return ValidationResult.Invalid("Redirect URL cannot point to WhatsApp Channels while Channel blocking is enabled.")
        }

        return ValidationResult.Valid(candidate)
    }

    fun extractDisplayHost(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return try {
            val host = URI(withScheme).host.orEmpty()
            if (host.isNotBlank()) {
                host.removePrefix("www.")
            } else {
                trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    fun resolveOverlayMessage(customMessage: String, defaultFallback: String): String {
        val trimmed = customMessage.trim()
        return if (trimmed.isNotEmpty() && !trimmed.equals(DEFAULT_MESSAGE, ignoreCase = true)) {
            trimmed
        } else {
            defaultFallback
        }
    }
}
