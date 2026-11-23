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
            trimmed.equals("Cortos", ignoreCase = true) ||
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

        if (desc == "shorts" || desc == "cortos" || desc == "shorts tab" ||
            desc.startsWith("shorts, ") || text == "shorts" || text == "cortos"
        ) {
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

    fun isWhatsAppChannelClickMatch(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo? = null
    ): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        val text = event.text.joinToString(" ")
        val desc = event.contentDescription?.toString() ?: ""
        val srcDesc = event.source?.contentDescription?.toString() ?: ""
        val srcText = event.source?.text?.toString() ?: ""
        val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""

        if (srcId.contains("newsletter_directory") ||
            srcId.contains("directory_search") ||
            srcId.contains("newsletter_follow")
        ) {
            return true
        }

        val candidates = listOf(desc, text, srcDesc, srcText)
        if (candidates.any { isUnambiguousWhatsAppChannelAction(it) }) {
            return true
        }

        if (candidates.any { isContextualWhatsAppChannelAction(it) }) {
            return rootNode != null && isWhatsAppUpdatesOrChannelsScreen(rootNode)
        }

        return false
    }

    internal fun isUnambiguousWhatsAppChannelAction(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim().lowercase(Locale.ROOT)
        return trimmed in UNAMBIGUOUS_CHANNEL_ACTIONS ||
            trimmed.startsWith("find channels") ||
            trimmed.startsWith("explore channels") ||
            trimmed.startsWith("buscar canales") ||
            trimmed.startsWith("explorar canales") ||
            trimmed.startsWith("rechercher des chaînes")
    }

    internal fun isContextualWhatsAppChannelAction(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim().lowercase(Locale.ROOT)
        return trimmed in CONTEXTUAL_CHANNEL_ACTIONS ||
            trimmed.startsWith("follow ") ||
            trimmed.startsWith("seguir ") ||
            trimmed.startsWith("suivre ") ||
            trimmed.startsWith("ikuti ")
    }

    fun isWhatsAppUpdatesOrChannelsScreen(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 12) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val text = node.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""

        if (viewId.contains("newsletter_") ||
            viewId.contains("channels_") ||
            viewId.contains("status_and_channels") ||
            (viewId.contains("updates_tab") && (node.isSelected || node.isFocused))
        ) {
            return true
        }

        if (text in CHANNEL_DIRECTORY_HEADERS || desc in CHANNEL_DIRECTORY_HEADERS) {
            return true
        }

        if ((text in UPDATES_TAB_LABELS || desc in UPDATES_TAB_LABELS) &&
            (node.isSelected || node.isFocused)
        ) {
            return true
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (isWhatsAppUpdatesOrChannelsScreen(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    internal fun isWhatsAppChannelDirectoryLabel(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return input.trim().lowercase(Locale.ROOT) in CHANNEL_DIRECTORY_HEADERS
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

        if (isWhatsAppChannelDirectoryLabel(text) || isWhatsAppChannelDirectoryLabel(desc)) {
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

    internal fun isInstagramReelsNodeActive(
        viewId: String,
        desc: String,
        text: String,
        isSelected: Boolean,
        isFocused: Boolean
    ): Boolean {
        val lowerId = viewId.lowercase(Locale.ROOT)
        val lowerDesc = desc.lowercase(Locale.ROOT)
        val lowerText = text.lowercase(Locale.ROOT)

        // Full-screen Reels viewer containers mean Reels are actively playing
        if (lowerId.contains("clips_viewer") ||
            lowerId.contains("clips_video_container") ||
            lowerId.contains("clips_viewer_view_pager") ||
            lowerId.contains("clips_item")
        ) {
            return true
        }

        // Bottom-nav / tab nodes exist on every Instagram screen; only block when selected/focused
        if (lowerId.contains("reels_tab") || lowerId.contains("clips_tab")) {
            return isSelected || isFocused
        }

        if (lowerDesc == "reels" ||
            lowerDesc == "reels tab" ||
            lowerDesc.startsWith("reels, ") ||
            lowerText == "reels"
        ) {
            return isSelected || isFocused
        }

        return false
    }

    fun isInstagramReelsActiveInHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 20) return false

        val viewId = node.viewIdResourceName ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val text = node.text?.toString() ?: ""

        if (isInstagramReelsNodeActive(viewId, desc, text, node.isSelected, node.isFocused)) {
            return true
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

    private val UNAMBIGUOUS_CHANNEL_ACTIONS = setOf(
        "find channels",
        "explore channels",
        "discover channels",
        "follow channel",
        "join channel",
        "search channels",
        "buscar canales",
        "explorar canales",
        "seguir canal",
        "rechercher des chaînes",
        "kanäle finden",
        "encontrar canais",
        "cari saluran",
        "kanalları bul",
        "tafuta vituo"
    )

    private val CONTEXTUAL_CHANNEL_ACTIONS = setOf(
        "follow",
        "see all",
        "explore more",
        "seguir",
        "ver todo",
        "suivre",
        "tout voir",
        "folgen",
        "alle ansehen",
        "ikuti",
        "lihat semua",
        "takip et"
    )

    private val CHANNEL_DIRECTORY_HEADERS = setOf(
        "find channels",
        "explore channels",
        "search channels",
        "most active",
        "popular channels",
        "buscar canales",
        "explorar canales",
        "canales populares",
        "rechercher des chaînes",
        "kanäle finden",
        "encontrar canais",
        "cari saluran",
        "kanalları bul",
        "tafuta vituo",
        "channels",
        "canales",
        "chaînes",
        "kanäle",
        "canais",
        "saluran",
        "kanallar"
    )

    private val UPDATES_TAB_LABELS = setOf(
        "updates",
        "novedades",
        "actus",
        "aktuelles",
        "atualizações",
        "pembaruan",
        "güncellemeler",
        "masasisho"
    )
}
