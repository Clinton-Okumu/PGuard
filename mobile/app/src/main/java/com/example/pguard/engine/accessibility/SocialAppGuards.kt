package com.example.pguard.engine.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Inspects social apps (YouTube Shorts, WhatsApp Channels, TikTok, Instagram Reels)
 * with separated click fast paths and throttled hierarchy inspections.
 */
object SocialAppGuards {

    fun isYouTubePackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.google.android.youtube" ||
            lower.contains("youtube") ||
            lower.contains("revanced") ||
            lower.contains("vanced") ||
            lower.contains("morphe") ||
            lower.contains("newpipe") ||
            lower.contains("libretube") ||
            lower.contains("skytube") ||
            lower.contains("puretuber") ||
            lower.contains("songtube")
    }

    fun isYouTubeShortsEventMatch(event: AccessibilityEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text.joinToString(" ")
            val desc = event.contentDescription?.toString() ?: ""
            val srcDesc = event.source?.contentDescription?.toString() ?: ""
            val srcText = event.source?.text?.toString() ?: ""
            val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

            if (isShortsIdentifier(desc) || isShortsIdentifier(text) ||
                isShortsIdentifier(srcDesc) || isShortsIdentifier(srcText) ||
                srcId.contains("pivot_shorts") || srcId.contains("reel_tab") || srcId.contains("reel_time_bar")
            ) {
                return true
            }
        }

        val className = event.className?.toString() ?: ""
        return className.contains("ReelWatchActivity", ignoreCase = true) ||
            className.contains("ReelWatchFragment", ignoreCase = true) ||
            className.contains("ReelPlayerView", ignoreCase = true)
    }

    fun isShortsIdentifier(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return trimmed.equals("Shorts", ignoreCase = true) ||
            trimmed.startsWith("Shorts tab", ignoreCase = true) ||
            trimmed.startsWith("Shorts, ", ignoreCase = true) ||
            trimmed.equals("YouTube Shorts", ignoreCase = true)
    }

    fun isShortsActiveInHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 20) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (viewId.contains("reel_time_bar") ||
            viewId.contains("reel_watch_fragment") ||
            viewId.contains("reel_player_page_container") ||
            viewId.contains("reel_recycler") ||
            viewId.contains("reel_video_player") ||
            viewId.contains("reel_bottom_ui_container") ||
            viewId.contains("shorts_container")
        ) {
            return true
        }

        if (desc == "shorts" || desc == "shorts tab" || desc.startsWith("shorts, ") || text == "shorts") {
            if (node.isSelected || node.isFocused) {
                return true
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (isShortsActiveInHierarchy(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    fun isWhatsAppPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.whatsapp" ||
            lower == "com.whatsapp.w4b" ||
            lower.contains("whatsapp")
    }

    fun isWhatsAppChannelClickMatch(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        val text = event.text.joinToString(" ")
        val desc = event.contentDescription?.toString() ?: ""
        val srcDesc = event.source?.contentDescription?.toString() ?: ""
        val srcText = event.source?.text?.toString() ?: ""
        val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        return isChannelExploreOrFollowTarget(desc) ||
            isChannelExploreOrFollowTarget(text) ||
            isChannelExploreOrFollowTarget(srcDesc) ||
            isChannelExploreOrFollowTarget(srcText) ||
            srcId.contains("newsletter_directory") ||
            srcId.contains("directory_search") ||
            srcId.contains("newsletter_follow")
    }

    private fun isChannelExploreOrFollowTarget(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()

        if (trimmed.equals("Follow", ignoreCase = true) ||
            trimmed.equals("Follow channel", ignoreCase = true) ||
            trimmed.startsWith("Follow ", ignoreCase = true) ||
            trimmed.equals("Join channel", ignoreCase = true)
        ) {
            return true
        }

        return trimmed.equals("Explore more", ignoreCase = true) ||
            trimmed.equals("Find channels", ignoreCase = true) ||
            trimmed.equals("Explore channels", ignoreCase = true) ||
            trimmed.equals("Discover channels", ignoreCase = true) ||
            trimmed.startsWith("Find channels", ignoreCase = true) ||
            trimmed.startsWith("Explore channels", ignoreCase = true) ||
            trimmed.equals("See all", ignoreCase = true)
    }

    fun isWhatsAppChannelDirectoryActive(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 15) return false

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        if (viewId.contains("newsletter_directory") ||
            viewId.contains("directory_search") ||
            viewId.contains("directory_categories") ||
            viewId.contains("newsletter_categories")
        ) {
            return true
        }

        if (text.equals("Find channels", ignoreCase = true) ||
            text.equals("Explore channels", ignoreCase = true) ||
            desc.equals("Find channels", ignoreCase = true) ||
            desc.equals("Explore channels", ignoreCase = true) ||
            text.equals("Search channels", ignoreCase = true) ||
            desc.equals("Search channels", ignoreCase = true)
        ) {
            return true
        }

        if (text.equals("Most active", ignoreCase = true) ||
            text.equals("Popular channels", ignoreCase = true) ||
            desc.equals("Most active", ignoreCase = true)
        ) {
            return true
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (isWhatsAppChannelDirectoryActive(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    fun isTikTokPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.zhiliaoapp.musically" ||
            lower == "com.zhiliaoapp.musically.go" ||
            lower == "com.ss.android.ugc.trill" ||
            lower == "com.ss.android.ugc.aweme" ||
            lower.contains("musically") ||
            lower.contains("tiktok") ||
            lower.contains("trill")
    }

    fun isInstagramPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.instagram.android" ||
            lower == "com.instagram.lite" ||
            lower.contains("instagram") ||
            lower.contains("instander") ||
            lower.contains("honista")
    }

    fun isInstagramReelsEventMatch(event: AccessibilityEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text.joinToString(" ")
            val desc = event.contentDescription?.toString() ?: ""
            val srcDesc = event.source?.contentDescription?.toString() ?: ""
            val srcText = event.source?.text?.toString() ?: ""
            val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

            if (isInstagramReelsIdentifier(desc) ||
                isInstagramReelsIdentifier(text) ||
                isInstagramReelsIdentifier(srcDesc) ||
                isInstagramReelsIdentifier(srcText) ||
                srcId.contains("clips_tab") ||
                srcId.contains("reels_tab")
            ) {
                return true
            }
        }

        val className = event.className?.toString() ?: ""
        return className.contains("ClipsViewer", ignoreCase = true) ||
            className.contains("ClipsActivity", ignoreCase = true)
    }

    private fun isInstagramReelsIdentifier(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return trimmed.equals("Reels", ignoreCase = true) ||
            trimmed.startsWith("Reels tab", ignoreCase = true) ||
            trimmed.startsWith("Reels, ", ignoreCase = true) ||
            trimmed.equals("Instagram Reels", ignoreCase = true)
    }

    fun isInstagramReelsActiveInHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 20) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (viewId.contains("clips_viewer") ||
            viewId.contains("clips_video_container") ||
            viewId.contains("clips_viewer_view_pager") ||
            viewId.contains("reels_tab") ||
            viewId.contains("clips_item")
        ) {
            return true
        }

        if (desc == "reels" || desc == "reels tab" || desc.startsWith("reels, ") || text == "reels") {
            if (node.isSelected || node.isFocused) {
                return true
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (isInstagramReelsActiveInHierarchy(child, depth + 1)) {
                return true
            }
        }
        return false
    }
}
