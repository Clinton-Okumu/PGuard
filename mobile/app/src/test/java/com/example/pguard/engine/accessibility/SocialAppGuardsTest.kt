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

    @Test
    fun `youtube shorts click targets and active player hierarchy match without false-blocking home feed`() {
        assertTrue(SocialAppGuards.isShortsClickTarget("Funny cat, 1.2M views - play Short"))
        assertTrue(SocialAppGuards.isShortsClickTarget("Shorts"))
        assertTrue(SocialAppGuards.isShortsClickTarget("Cortos"))
        assertFalse(SocialAppGuards.isShortsClickTarget("Subscriptions"))
        assertFalse(SocialAppGuards.isShortsClickTarget("Watch later"))

        assertTrue(
            SocialAppGuards.isShortsNodeActive(
                viewId = "com.google.android.youtube:id/reel_player_overlay",
                desc = "",
                text = ""
            )
        )
        assertTrue(
            SocialAppGuards.isShortsNodeActive(
                viewId = "com.google.android.youtube:id/reel_progress_bar",
                desc = "",
                text = ""
            )
        )
        assertTrue(
            SocialAppGuards.isShortsNodeActive(
                viewId = "",
                desc = "Remix this Short",
                text = ""
            )
        )
        assertTrue(
            SocialAppGuards.isShortsNodeActive(
                viewId = "com.google.android.youtube:id/pivot_shorts",
                desc = "Shorts",
                text = "",
                stateDesc = "Selected"
            )
        )
        assertTrue(
            SocialAppGuards.isShortsNodeActive(
                viewId = "",
                desc = "Shorts",
                text = "",
                parentSelected = true
            )
        )

        // Must NOT false-trigger on unselected bottom tab or passive Home feed Shorts shelf cards
        assertFalse(
            SocialAppGuards.isShortsNodeActive(
                viewId = "com.google.android.youtube:id/pivot_shorts",
                desc = "Shorts",
                text = "Shorts",
                isSelected = false,
                isFocused = false,
                parentSelected = false
            )
        )
        assertFalse(
            SocialAppGuards.isShortsNodeActive(
                viewId = "com.google.android.youtube:id/shorts_shelf",
                desc = "Funny cat, 1.2M views - play Short",
                text = ""
            )
        )
    }

    @Test
    fun `whatsapp channels matches directory and open channel views without false-blocking regular chats`() {
        assertTrue(
            SocialAppGuards.isWhatsAppChannelNodeActive(
                viewId = "com.whatsapp:id/newsletter_info",
                desc = "",
                text = ""
            )
        )
        assertTrue(
            SocialAppGuards.isWhatsAppChannelNodeActive(
                viewId = "com.whatsapp:id/newsletter_follow",
                desc = "",
                text = "Follow"
            )
        )
        assertTrue(
            SocialAppGuards.isWhatsAppChannelNodeActive(
                viewId = "",
                desc = "",
                text = "This channel has added privacy for your profile and phone number."
            )
        )
        assertFalse(
            SocialAppGuards.isWhatsAppChannelNodeActive(
                viewId = "com.whatsapp:id/conversations_row_contact_name",
                desc = "Alice",
                text = "Messages"
            )
        )
    }

    @Test
    fun `instagram reels matches modern viewer ids and parent selection while ignoring stories`() {
        assertTrue(
            SocialAppGuards.isInstagramReelsNodeActive(
                viewId = "com.instagram.android:id/clips_media_component",
                desc = "",
                text = "",
                isSelected = false,
                isFocused = false
            )
        )
        assertTrue(
            SocialAppGuards.isInstagramReelsNodeActive(
                viewId = "",
                desc = "Remix this reel",
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
                isSelected = false,
                isFocused = false,
                stateDesc = "Selected"
            )
        )
        assertFalse(
            SocialAppGuards.isInstagramReelsNodeActive(
                viewId = "com.instagram.android:id/reel_viewer_root",
                desc = "Story",
                text = "",
                isSelected = false,
                isFocused = false
            )
        )
    }

    @Test
    fun `youtube shorts multi-signal screen signature detects obfuscated Shorts player without blocking regular videos`() {
        val modernShortsPlayerTexts = listOf(
            "Search",
            "More options",
            "like this video along with 45,120 other people",
            "Dislike this video",
            "View 312 comments",
            "Share this video",
            "Remix",
            "@creator",
            "Subscribe"
        )
        assertTrue(SocialAppGuards.isShortsScreenSignature(modernShortsPlayerTexts))

        val regularVideoWatchPageTexts = listOf(
            "Minimize",
            "Autoplay is on",
            "Enter fullscreen",
            "like this video along with 1,200 other people",
            "Dislike this video",
            "Share",
            "Remix",
            "Save to playlist",
            "Download video"
        )
        assertFalse(SocialAppGuards.isShortsScreenSignature(regularVideoWatchPageTexts))

        val homeFeedTexts = listOf(
            "Home",
            "Shorts",
            "Subscriptions",
            "You",
            "Funny cat, 1.2M views - play Short"
        )
        assertFalse(SocialAppGuards.isShortsScreenSignature(homeFeedTexts))

        // YouTube Community image posts have Like/Dislike/Comment/Share without Remix or video-specific labels
        val communityImagePostTexts = listOf(
            "Tech Channel",
            "2 hours ago",
            "Check out our new studio setup photo!",
            "Like",
            "Dislike",
            "Comment",
            "Share"
        )
        assertFalse(SocialAppGuards.isShortsScreenSignature(communityImagePostTexts))

        val communityPostWithDescTexts = listOf(
            "Like this post along with 1.4K others",
            "Dislike this post",
            "View 89 comments",
            "Share"
        )
        assertFalse(SocialAppGuards.isShortsScreenSignature(communityPostWithDescTexts))

        // Regular YouTube video watch page with auto-hidden player controls (no Minimize/Fullscreen visible)
        val regularWatchControlsHiddenTexts = listOf(
            "Video player",
            "How to Build a Clean Architecture App",
            "120K views · 3 days ago ...more",
            "Tech Channel",
            "Subscribe",
            "like this video along with 4,500 other people",
            "Dislike this video",
            "Share",
            "Remix",
            "Download",
            "Save",
            "Comments"
        )
        assertFalse(SocialAppGuards.isShortsScreenSignature(regularWatchControlsHiddenTexts))

        // Regular YouTube fullscreen video quick-actions bar
        val regularFullscreenVideoTexts = listOf(
            "like this video along with 4,500 other people",
            "Dislike this video",
            "Comments",
            "Save",
            "Share",
            "More videos"
        )
        assertFalse(SocialAppGuards.isShortsScreenSignature(regularFullscreenVideoTexts))
    }

    @Test
    fun `whatsapp recognizes selected Chats Calls and Communities tabs so off-screen Updates never re-block`() {
        assertTrue(
            SocialAppGuards.isWhatsAppNonUpdatesTabSelected(
                viewId = "com.whatsapp:id/chats_tab",
                desc = "Chats",
                text = "Chats",
                isSelected = true
            )
        )
        assertTrue(
            SocialAppGuards.isWhatsAppNonUpdatesTabSelected(
                viewId = "",
                desc = "Chats, Selected",
                text = "Chats",
                isSelected = false
            )
        )
        assertFalse(
            SocialAppGuards.isWhatsAppNonUpdatesTabSelected(
                viewId = "com.whatsapp:id/chats_tab",
                desc = "Chats",
                text = "Chats",
                isSelected = false
            )
        )
        assertFalse(
            SocialAppGuards.isWhatsAppNonUpdatesTabSelected(
                viewId = "com.whatsapp:id/updates_tab",
                desc = "Updates",
                text = "Updates",
                isSelected = true
            )
        )
    }

    @Test
    fun `recognizes official and modded YouTube APK package names`() {
        val ytPackages = listOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.rvx.android.youtube",
            "com.vanced.android.youtube",
            "org.polymorphicshade.tubular",
            "com.futo.platformplayer",
            "by.green.tuber"
        )
        for (pkg in ytPackages) {
            assertTrue("Expected $pkg to be recognized as YouTube", SocialAppGuards.isYouTubePackage(pkg))
        }
        assertFalse(SocialAppGuards.isYouTubePackage("com.android.chrome"))
    }
}
