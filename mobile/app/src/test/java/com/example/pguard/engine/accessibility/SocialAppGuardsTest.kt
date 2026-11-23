package com.example.pguard.engine.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAppGuardsTest {

    @Test
    fun `instagram reels_tab only triggers when selected or focused`() {
        assertFalse(
            SocialAppGuards.isInstagramReelsNodeActive(
                viewId = "com.instagram.android:id/reels_tab",
                desc = "Reels",
                text = "",
                isSelected = false,
                isFocused = false
            )
        )
        assertTrue(
            SocialAppGuards.isInstagramReelsNodeActive(
                viewId = "com.instagram.android:id/reels_tab",
                desc = "Reels",
                text = "",
                isSelected = true,
                isFocused = false
            )
        )
        assertTrue(
            SocialAppGuards.isInstagramReelsNodeActive(
                viewId = "com.instagram.android:id/clips_viewer_view_pager",
                desc = "",
                text = "",
                isSelected = false,
                isFocused = false
            )
        )
    }

    @Test
    fun `whatsapp distinguishes unambiguous channel labels from contextual actions`() {
        assertTrue(SocialAppGuards.isUnambiguousWhatsAppChannelAction("Find channels"))
        assertTrue(SocialAppGuards.isUnambiguousWhatsAppChannelAction("Buscar canales"))
        assertFalse(SocialAppGuards.isUnambiguousWhatsAppChannelAction("Follow"))
        assertFalse(SocialAppGuards.isUnambiguousWhatsAppChannelAction("See all"))

        assertTrue(SocialAppGuards.isContextualWhatsAppChannelAction("Follow"))
        assertTrue(SocialAppGuards.isContextualWhatsAppChannelAction("Follow BBC News"))
        assertTrue(SocialAppGuards.isContextualWhatsAppChannelAction("See all"))
    }

    @Test
    fun `youtube shorts recognizes english and localized shorts labels`() {
        assertTrue(SocialAppGuards.isShortsIdentifier("Shorts"))
        assertTrue(SocialAppGuards.isShortsIdentifier("Cortos"))
        assertTrue(SocialAppGuards.isShortsIdentifier("Shorts tab"))
        assertFalse(SocialAppGuards.isShortsIdentifier("Subscriptions"))
    }
}
