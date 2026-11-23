package com.example.pguard.engine.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedScreenConfigTest {

    @Test
    fun `validateMessage rejects blank and over-limit strings and trims valid input`() {
        assertTrue(BlockedScreenConfig.validateMessage("   ") is BlockedScreenConfig.ValidationResult.Invalid)
        assertTrue(BlockedScreenConfig.validateMessage("") is BlockedScreenConfig.ValidationResult.Invalid)

        val trimmed = BlockedScreenConfig.validateMessage("  Stay strong and focused!  ")
        assertEquals(BlockedScreenConfig.ValidationResult.Valid("Stay strong and focused!"), trimmed)

        val emojiMsg = "🛡".repeat(300)
        assertEquals(BlockedScreenConfig.ValidationResult.Valid(emojiMsg), BlockedScreenConfig.validateMessage(emojiMsg))

        val tooLongEmojiMsg = "🛡".repeat(301)
        assertTrue(BlockedScreenConfig.validateMessage(tooLongEmojiMsg) is BlockedScreenConfig.ValidationResult.Invalid)
    }

    @Test
    fun `validateRedirectUrl allows blank normalizes bare domains and rejects unsafe schemes`() {
        assertEquals(BlockedScreenConfig.ValidationResult.Valid(""), BlockedScreenConfig.validateRedirectUrl("   "))

        assertEquals(
            BlockedScreenConfig.ValidationResult.Valid("https://example.com/focus"),
            BlockedScreenConfig.validateRedirectUrl("example.com/focus")
        )
        assertEquals(
            BlockedScreenConfig.ValidationResult.Valid("http://example.org"),
            BlockedScreenConfig.validateRedirectUrl("HTTP://example.org")
        )

        assertTrue(BlockedScreenConfig.validateRedirectUrl("https://bad url.com") is BlockedScreenConfig.ValidationResult.Invalid)
        assertTrue(BlockedScreenConfig.validateRedirectUrl("javascript:alert(1)") is BlockedScreenConfig.ValidationResult.Invalid)
        assertTrue(BlockedScreenConfig.validateRedirectUrl("file:///etc/passwd") is BlockedScreenConfig.ValidationResult.Invalid)
        assertTrue(BlockedScreenConfig.validateRedirectUrl("intent://foo#Intent") is BlockedScreenConfig.ValidationResult.Invalid)
        assertTrue(BlockedScreenConfig.validateRedirectUrl("not-a-domain") is BlockedScreenConfig.ValidationResult.Invalid)
        assertTrue(BlockedScreenConfig.validateRedirectUrl("https://.example.com") is BlockedScreenConfig.ValidationResult.Invalid)
        assertTrue(BlockedScreenConfig.validateRedirectUrl("https://bad..com") is BlockedScreenConfig.ValidationResult.Invalid)
    }

    @Test
    fun `validateRedirectUrl rejects adult sites blocklist matches and active social blocks`() {
        assertTrue(
            BlockedScreenConfig.validateRedirectUrl("https://pornhub.com", blockAdultContent = true)
                is BlockedScreenConfig.ValidationResult.Invalid
        )
        assertTrue(
            BlockedScreenConfig.validateRedirectUrl("https://reddit.com/r/all", customBlocklist = setOf("reddit.com"))
                is BlockedScreenConfig.ValidationResult.Invalid
        )
        assertTrue(
            BlockedScreenConfig.validateRedirectUrl("https://www.tiktok.com/@creator", blockTikTok = true)
                is BlockedScreenConfig.ValidationResult.Invalid
        )
        assertTrue(
            BlockedScreenConfig.validateRedirectUrl("https://tiktok.com", blockTikTok = false)
                is BlockedScreenConfig.ValidationResult.Valid
        )
        assertTrue(
            BlockedScreenConfig.validateRedirectUrl("https://www.youtube.com/shorts/abc", blockShorts = true)
                is BlockedScreenConfig.ValidationResult.Invalid
        )
        assertTrue(
            BlockedScreenConfig.validateRedirectUrl("https://www.instagram.com/reels/xyz", blockInstagramReels = true)
                is BlockedScreenConfig.ValidationResult.Invalid
        )
        assertTrue(
            BlockedScreenConfig.validateRedirectUrl("https://whatsapp.com/channel/123", blockWhatsAppChannels = true)
                is BlockedScreenConfig.ValidationResult.Invalid
        )
    }

    @Test
    fun `extractDisplayHost strips scheme www and path`() {
        assertEquals("google.com", BlockedScreenConfig.extractDisplayHost("https://www.google.com/search?q=focus"))
        assertEquals("khanacademy.org", BlockedScreenConfig.extractDisplayHost("khanacademy.org/math"))
        assertEquals("", BlockedScreenConfig.extractDisplayHost("   "))
    }

    @Test
    fun `resolveOverlayMessage uses custom message only when personalized`() {
        assertEquals(
            "TikTok is completely blocked.",
            BlockedScreenConfig.resolveOverlayMessage(BlockedScreenConfig.DEFAULT_MESSAGE, "TikTok is completely blocked.")
        )
        assertEquals(
            "This app is in your Blocklist.",
            BlockedScreenConfig.resolveOverlayMessage("   ", "This app is in your Blocklist.")
        )
        assertEquals(
            "Protect your peace.",
            BlockedScreenConfig.resolveOverlayMessage("  Protect your peace.  ", "This app is in your Blocklist.")
        )
    }
}
