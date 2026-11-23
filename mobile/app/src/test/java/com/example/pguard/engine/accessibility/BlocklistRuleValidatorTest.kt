package com.example.pguard.engine.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistRuleValidatorTest {

    @Test
    fun `normalizeWebsiteOrKeyword strips scheme www port and path matching backend`() {
        assertEquals(
            "reddit.com" to BlocklistRuleValidator.EntryKind.WEBSITE,
            BlocklistRuleValidator.normalizeWebsiteOrKeyword("https://www.reddit.com/r/popular?sort=top#top")
        )
        assertEquals(
            "example.org" to BlocklistRuleValidator.EntryKind.WEBSITE,
            BlocklistRuleValidator.normalizeWebsiteOrKeyword("HTTP://Example.ORG:8080/login")
        )
        assertEquals(
            "casino games" to BlocklistRuleValidator.EntryKind.KEYWORD,
            BlocklistRuleValidator.normalizeWebsiteOrKeyword("  Casino   Games ")
        )
    }

    @Test
    fun `validateRule rejects short generic and malformed rules`() {
        assertTrue(BlocklistRuleValidator.validateRule("   ", isWhitelist = false) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule("a", isWhitelist = false) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule("ab", isWhitelist = true) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule("com", isWhitelist = true) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule("http", isWhitelist = false) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule("https", isWhitelist = true) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule("www", isWhitelist = false) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule(".com", isWhitelist = false) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule("bad..com", isWhitelist = false) is BlocklistRuleValidator.RuleValidationResult.Invalid)
        assertTrue(BlocklistRuleValidator.validateRule("a".repeat(121), isWhitelist = false) is BlocklistRuleValidator.RuleValidationResult.Invalid)
    }

    @Test
    fun `validateRule prevents whitelisting adult sites keywords or currently blocked rules`() {
        assertTrue(
            BlocklistRuleValidator.validateRule(
                raw = "pornhub.com",
                isWhitelist = true,
                blockAdultContent = true
            ) is BlocklistRuleValidator.RuleValidationResult.Invalid
        )
        assertTrue(
            BlocklistRuleValidator.validateRule(
                raw = "https://www.xvideos.com/video123",
                isWhitelist = true,
                blockAdultContent = true
            ) is BlocklistRuleValidator.RuleValidationResult.Invalid
        )
        assertTrue(
            BlocklistRuleValidator.validateRule(
                raw = "porn",
                isWhitelist = true,
                blockAdultContent = true
            ) is BlocklistRuleValidator.RuleValidationResult.Invalid
        )
        assertTrue(
            BlocklistRuleValidator.validateRule(
                raw = "https://www.reddit.com/r/news",
                isWhitelist = true,
                existingBlockedRules = setOf("reddit.com")
            ) is BlocklistRuleValidator.RuleValidationResult.Invalid
        )
        assertTrue(
            BlocklistRuleValidator.validateRule(
                raw = "youtube.com",
                isWhitelist = false,
                existingBlockedRules = setOf("youtube.com")
            ) is BlocklistRuleValidator.RuleValidationResult.Invalid
        )
    }

    @Test
    fun `validateRule accepts valid websites and keywords`() {
        val validWebsite = BlocklistRuleValidator.validateRule(
            raw = "https://www.khanacademy.org/math",
            isWhitelist = true
        )
        assertEquals(
            BlocklistRuleValidator.RuleValidationResult.Valid("khanacademy.org", BlocklistRuleValidator.EntryKind.WEBSITE),
            validWebsite
        )

        val validKeyword = BlocklistRuleValidator.validateRule(
            raw = "gambling",
            isWhitelist = false
        )
        assertEquals(
            BlocklistRuleValidator.RuleValidationResult.Valid("gambling", BlocklistRuleValidator.EntryKind.KEYWORD),
            validKeyword
        )
    }

    @Test
    fun `isUnsupportedBrowserBlockedFromWhitelist blocks Tor and Opera Mini when unsupported browser blocking is active`() {
        assertTrue(
            BlocklistRuleValidator.isUnsupportedBrowserBlockedFromWhitelist(
                packageName = "org.torproject.torbrowser",
                blockUnsupportedBrowsersEnabled = true
            )
        )
        assertTrue(
            BlocklistRuleValidator.isUnsupportedBrowserBlockedFromWhitelist(
                packageName = "com.opera.mini.native",
                blockUnsupportedBrowsersEnabled = true
            )
        )
        assertFalse(
            BlocklistRuleValidator.isUnsupportedBrowserBlockedFromWhitelist(
                packageName = "com.android.chrome",
                blockUnsupportedBrowsersEnabled = true
            )
        )
        assertFalse(
            BlocklistRuleValidator.isUnsupportedBrowserBlockedFromWhitelist(
                packageName = "org.torproject.torbrowser",
                blockUnsupportedBrowsersEnabled = false
            )
        )
    }
}
