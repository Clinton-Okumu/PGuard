package com.example.pguard.engine.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Inspects social apps (YouTube Shorts, WhatsApp Channels, TikTok, Instagram Reels)
 * with separated click fast paths and bounded BFS multi-window hierarchy inspections.
 */
object SocialAppGuards {

    private const val MAX_HIERARCHY_DEPTH = 44
    private const val MAX_VISITED_NODES = 1500

    fun isYouTubePackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return lower == "com.google.android.youtube" ||
            lower.contains("youtube") ||
            lower.contains("revanced") ||
            lower.contains("vanced") ||
            lower.contains("rvx") ||
            lower.contains("morphe") ||
            lower.contains("newpipe") ||
            lower.contains("pipepipe") ||
            lower.contains("libretube") ||
            lower.contains("skytube") ||
            lower.contains("puretuber") ||
            lower.contains("songtube") ||
            lower.contains("playtube") ||
            lower.contains("vuetube") ||
            lower.contains("smarttube") ||
            lower.contains("tubular") ||
            lower.contains("tuber") ||
            lower.contains("grayjay") ||
            lower == "com.futo.platformplayer" ||
            lower.contains("clipious") ||
            lower.contains("ytpro")
    }

    fun isYouTubeShortsEventMatch(event: AccessibilityEvent): Boolean {
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SELECTED
        ) {
            val text = event.text.joinToString(" ")
            val desc = event.contentDescription?.toString() ?: ""
            val srcDesc = event.source?.contentDescription?.toString() ?: ""
            val srcText = event.source?.text?.toString() ?: ""
            val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            val parentId = event.source?.parent?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            val subtreeTexts = BrowserInspector.collectSubtreeTexts(event.source, maxDepth = 5, maxItems = 15)

            if (isShortsClickTarget(desc) || isShortsClickTarget(text) ||
                isShortsClickTarget(srcDesc) || isShortsClickTarget(srcText) ||
                subtreeTexts.any { isShortsClickTarget(it) } ||
                isShortsClickViewId(srcId) || isShortsClickViewId(parentId)
            ) {
                return true
            }
        }

        val className = event.className?.toString() ?: ""
        return className.contains("ReelWatchActivity", ignoreCase = true) ||
            className.contains("ReelWatchFragment", ignoreCase = true) ||
            className.contains("ReelPlayerView", ignoreCase = true) ||
            className.contains("ShortsWatch", ignoreCase = true) ||
            className.contains("ShortsFragment", ignoreCase = true)
    }

    private fun isShortsClickViewId(viewId: String): Boolean {
        if (viewId.isBlank()) return false
        return viewId.contains("pivot_shorts") ||
            viewId.contains("reel_tab") ||
            viewId.contains("reel_time_bar") ||
            viewId.contains("shorts_lockup") ||
            viewId.contains("reel_item")
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

    /**
     * Click-only Shorts matcher (includes Shorts thumbnail CTA strings that must not
     * be matched during passive Home feed hierarchy scans).
     */
    internal fun isShortsClickTarget(text: String?): Boolean {
        if (isShortsIdentifier(text)) return true
        val lower = text?.trim()?.lowercase(Locale.ROOT) ?: return false
        return lower.contains("- play short") ||
            lower.endsWith("play short") ||
            lower == "play short" ||
            lower == "shorts video" ||
            lower == "remix this short" ||
            lower == "search shorts"
    }

    internal fun isShortsNodeActive(
        viewId: String,
        desc: String,
        text: String,
        stateDesc: String = "",
        isSelected: Boolean = false,
        isFocused: Boolean = false,
        parentSelected: Boolean = false
    ): Boolean {
        val lowerId = viewId.lowercase(Locale.ROOT)
        val lowerDesc = desc.trim().lowercase(Locale.ROOT)
        val lowerText = text.trim().lowercase(Locale.ROOT)
        val lowerState = stateDesc.trim().lowercase(Locale.ROOT)

        if (ACTIVE_SHORTS_VIEW_IDS.any { lowerId.contains(it) }) {
            return true
        }

        if (ACTIVE_SHORTS_PLAYER_LABELS.any { label ->
                lowerDesc == label || lowerDesc.startsWith("$label,") || lowerText == label
            }
        ) {
            return true
        }

        val isShortsTabNode = lowerDesc == "shorts" ||
            lowerDesc == "cortos" ||
            lowerDesc == "shorts tab" ||
            lowerDesc.startsWith("shorts, ") ||
            lowerText == "shorts" ||
            lowerText == "cortos" ||
            lowerId.contains("pivot_shorts") ||
            lowerId.contains("reel_tab")

        if (isShortsTabNode) {
            return isSelected ||
                isFocused ||
                parentSelected ||
                lowerState.contains("selected") ||
                lowerDesc.contains("selected")
        }

        return false
    }

    /**
     * Multi-signal screen signature for the active YouTube Shorts player across all YouTube
     * versions and modded APKs (ReVanced, RVX, Morphe, Vanced, etc.), even when Litho/R8
     * strips or obfuscates all view resource IDs.
     *
     * Requires >=3 vertical Shorts player action labels (like/dislike/comments/share/remix)
     * AND the absence of regular horizontal watch-page controls (fullscreen, minimize, autoplay,
     * save to playlist, download) so Home feed and normal videos never false-trigger.
     */
    internal fun isShortsScreenSignature(texts: Collection<String>): Boolean {
        if (texts.isEmpty()) return false
        var hasVideoLike = false
        var hasVideoDislike = false
        var hasLike = false
        var hasDislike = false
        var hasComments = false
        var hasShare = false
        var hasRemix = false

        for (raw in texts) {
            val lower = raw.trim().lowercase(Locale.ROOT)
            if (lower.isEmpty()) continue

            if (REGULAR_YOUTUBE_WATCH_MARKERS.any { marker -> lower.contains(marker) } ||
                NON_SHORTS_YOUTUBE_MARKERS.any { marker -> lower.contains(marker) }
            ) {
                return false
            }

            if (!hasVideoLike && lower.startsWith("like this video")) {
                hasVideoLike = true
                hasLike = true
            } else if (!hasLike && lower == "like") {
                hasLike = true
            }
            if (!hasVideoDislike && lower.startsWith("dislike this video")) {
                hasVideoDislike = true
                hasDislike = true
            } else if (!hasDislike && lower == "dislike") {
                hasDislike = true
            }
            if (!hasComments && (
                    (lower.startsWith("view ") && lower.contains("comment")) ||
                        lower == "comments" ||
                        lower == "comment"
                    )
            ) {
                hasComments = true
            }
            if (!hasShare && (lower == "share" || lower.startsWith("share this video"))) {
                hasShare = true
            }
            if (!hasRemix && (lower == "remix" || lower.startsWith("remix this"))) {
                hasRemix = true
            }
        }

        val signals = (if (hasLike) 1 else 0) +
            (if (hasDislike) 1 else 0) +
            (if (hasComments) 1 else 0) +
            (if (hasShare) 1 else 0) +
            (if (hasRemix) 1 else 0)

        // Community image posts expose bare "Like" + "Dislike" + "Comment" + "Share" without
        // "Remix" or video-specific "like this video" / "dislike this video" descriptions.
        return (hasRemix && signals >= 3) ||
            (hasVideoLike && hasVideoDislike && (hasComments || hasShare))
    }

    fun isShortsActiveInHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        return isShortsActiveInHierarchy(listOfNotNull(node))
    }

    fun isShortsActiveInHierarchy(roots: List<AccessibilityNodeInfo>): Boolean {
        val collectedTexts = ArrayList<String>(128)
        var nonShortsTabSelected = false
        val directMatch = bfsMatchesAny(roots) { node, viewId, desc, text, stateDesc, parentSelected ->
            if (collectedTexts.size < 250) {
                if (desc.isNotBlank()) collectedTexts.add(desc)
                if (text.isNotBlank()) collectedTexts.add(text)
            }
            val isSelected = node.isSelected || node.isChecked
            val lowerDesc = desc.trim().lowercase(Locale.ROOT)
            val lowerText = text.trim().lowercase(Locale.ROOT)
            val lowerState = stateDesc.trim().lowercase(Locale.ROOT)
            if (!nonShortsTabSelected &&
                (isSelected || parentSelected || lowerState.contains("selected") || lowerDesc.contains("selected")) &&
                (lowerText in NON_SHORTS_YOUTUBE_TABS ||
                    lowerDesc in NON_SHORTS_YOUTUBE_TABS ||
                    NON_SHORTS_YOUTUBE_TABS.any { tab -> lowerDesc.startsWith("$tab,") || lowerDesc.startsWith("$tab tab") })
            ) {
                nonShortsTabSelected = true
            }
            isShortsNodeActive(
                viewId = viewId,
                desc = desc,
                text = text,
                stateDesc = stateDesc,
                isSelected = isSelected,
                isFocused = node.isFocused,
                parentSelected = parentSelected
            )
        }
        return directMatch || (!nonShortsTabSelected && isShortsScreenSignature(collectedTexts))
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
    ): Boolean = isWhatsAppChannelClickMatch(event, listOfNotNull(rootNode))

    fun isWhatsAppChannelClickMatch(
        event: AccessibilityEvent,
        roots: List<AccessibilityNodeInfo>
    ): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false
        val text = event.text.joinToString(" ")
        val desc = event.contentDescription?.toString() ?: ""
        val srcDesc = event.source?.contentDescription?.toString() ?: ""
        val srcText = event.source?.text?.toString() ?: ""
        val srcId = event.source?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val parentId = event.source?.parent?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val subtreeTexts = BrowserInspector.collectSubtreeTexts(event.source, maxDepth = 5, maxItems = 15)

        if (srcId.contains("newsletter_directory") ||
            srcId.contains("directory_search") ||
            srcId.contains("newsletter_follow") ||
            parentId.contains("newsletter_") ||
            parentId.contains("directory_search")
        ) {
            return true
        }

        val candidates = (listOf(desc, text, srcDesc, srcText) + subtreeTexts).distinct()
        if (candidates.any { isUnambiguousWhatsAppChannelAction(it) }) {
            return true
        }

        if (candidates.any { isContextualWhatsAppChannelAction(it) }) {
            return roots.isNotEmpty() && isWhatsAppUpdatesOrChannelsScreen(roots)
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

    fun isWhatsAppUpdatesOrChannelsScreen(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean =
        isWhatsAppUpdatesOrChannelsScreen(listOfNotNull(node))

    fun isWhatsAppUpdatesOrChannelsScreen(roots: List<AccessibilityNodeInfo>): Boolean {
        return bfsMatchesAny(roots) { node, viewId, desc, text, stateDesc, parentSelected ->
            val lowerId = viewId.lowercase(Locale.ROOT)
            val lowerText = text.trim().lowercase(Locale.ROOT)
            val lowerDesc = desc.trim().lowercase(Locale.ROOT)
            val lowerState = stateDesc.trim().lowercase(Locale.ROOT)
            val isTabSelected = node.isSelected || node.isFocused || parentSelected ||
                lowerState.contains("selected") || lowerDesc.contains("selected")

            if (lowerId.contains("newsletter_") ||
                lowerId.contains("channels_") ||
                lowerId.contains("status_and_channels") ||
                (lowerId.contains("updates_tab") && isTabSelected)
            ) {
                true
            } else if (lowerText in CHANNEL_DIRECTORY_HEADERS || lowerDesc in CHANNEL_DIRECTORY_HEADERS) {
                true
            } else {
                (lowerText in UPDATES_TAB_LABELS || lowerDesc in UPDATES_TAB_LABELS) && isTabSelected
            }
        }
    }

    internal fun isWhatsAppChannelDirectoryLabel(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return input.trim().lowercase(Locale.ROOT) in CHANNEL_DIRECTORY_HEADERS
    }

    internal fun isWhatsAppChannelNodeActive(viewId: String, desc: String, text: String): Boolean {
        val lowerId = viewId.lowercase(Locale.ROOT)
        val lowerDesc = desc.trim().lowercase(Locale.ROOT)
        val lowerText = text.trim().lowercase(Locale.ROOT)

        if (WHATSAPP_CHANNEL_VIEW_IDS.any { lowerId.contains(it) }) {
            return true
        }

        if (isWhatsAppChannelDirectoryLabel(lowerText) || isWhatsAppChannelDirectoryLabel(lowerDesc)) {
            return true
        }

        return WHATSAPP_ACTIVE_CHANNEL_PHRASES.any { phrase ->
            lowerText.contains(phrase) || lowerDesc.contains(phrase)
        }
    }

    internal fun isWhatsAppNonUpdatesTabSelected(
        viewId: String,
        desc: String,
        text: String,
        stateDesc: String = "",
        isSelected: Boolean = false,
        parentSelected: Boolean = false
    ): Boolean {
        val lowerId = viewId.lowercase(Locale.ROOT)
        val lowerDesc = desc.trim().lowercase(Locale.ROOT)
        val lowerText = text.trim().lowercase(Locale.ROOT)
        val lowerState = stateDesc.trim().lowercase(Locale.ROOT)
        val selected = isSelected ||
            parentSelected ||
            lowerState.contains("selected") ||
            lowerDesc.contains("selected")
        if (!selected) return false

        if (lowerId.contains("chats_tab") ||
            lowerId.contains("calls_tab") ||
            lowerId.contains("communities_tab") ||
            lowerId.contains("conversations_tab")
        ) {
            return true
        }
        return lowerText in WHATSAPP_NON_UPDATES_TABS ||
            lowerDesc in WHATSAPP_NON_UPDATES_TABS ||
            WHATSAPP_NON_UPDATES_TABS.any { tab ->
                lowerDesc.startsWith("$tab,") || lowerDesc.startsWith("$tab tab")
            }
    }

    fun isWhatsAppChannelDirectoryActive(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean =
        isWhatsAppChannelDirectoryActive(listOfNotNull(node))

    fun isWhatsAppChannelDirectoryActive(roots: List<AccessibilityNodeInfo>): Boolean {
        var matchedChannelNode = false
        var nonUpdatesTabSelected = false
        bfsMatchesAny(roots) { node, viewId, desc, text, stateDesc, parentSelected ->
            if (isWhatsAppNonUpdatesTabSelected(
                    viewId = viewId,
                    desc = desc,
                    text = text,
                    stateDesc = stateDesc,
                    isSelected = node.isSelected || node.isChecked,
                    parentSelected = parentSelected
                )
            ) {
                nonUpdatesTabSelected = true
                return@bfsMatchesAny true
            }
            if (isWhatsAppChannelNodeActive(viewId = viewId, desc = desc, text = text)) {
                matchedChannelNode = true
            }
            false
        }
        // If Chats / Calls / Communities tab is visibly selected, the user is not on Channels
        // (off-screen ViewPager Updates fragments must never re-trigger a Channels block).
        return matchedChannelNode && !nonUpdatesTabSelected
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
            val parentId = event.source?.parent?.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            val subtreeTexts = BrowserInspector.collectSubtreeTexts(event.source, maxDepth = 5, maxItems = 15)

            if (isInstagramReelsIdentifier(desc) ||
                isInstagramReelsIdentifier(text) ||
                isInstagramReelsIdentifier(srcDesc) ||
                isInstagramReelsIdentifier(srcText) ||
                subtreeTexts.any { isInstagramReelsIdentifier(it) } ||
                srcId.contains("clips_tab") ||
                srcId.contains("reels_tab") ||
                parentId.contains("clips_tab") ||
                parentId.contains("reels_tab") ||
                parentId.contains("clips_video_container")
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
            trimmed.equals("Instagram Reels", ignoreCase = true) ||
            trimmed.equals("Remix this reel", ignoreCase = true) ||
            trimmed.equals("Watch more reels", ignoreCase = true)
    }

    internal fun isInstagramReelsNodeActive(
        viewId: String,
        desc: String,
        text: String,
        isSelected: Boolean,
        isFocused: Boolean,
        stateDesc: String = "",
        parentSelected: Boolean = false
    ): Boolean {
        val lowerId = viewId.lowercase(Locale.ROOT)
        val lowerDesc = desc.trim().lowercase(Locale.ROOT)
        val lowerText = text.trim().lowercase(Locale.ROOT)
        val lowerState = stateDesc.trim().lowercase(Locale.ROOT)

        // Full-screen Reels viewer containers mean Reels are actively playing (never match reel_viewer = Stories)
        if (lowerId.contains("clips_viewer") ||
            lowerId.contains("clips_video_container") ||
            lowerId.contains("clips_viewer_view_pager") ||
            lowerId.contains("clips_item") ||
            lowerId.contains("clips_media_component") ||
            lowerId.contains("clips_author_info") ||
            lowerId.contains("clips_ufi") ||
            lowerId.contains("clips_video_view")
        ) {
            return true
        }

        if (lowerDesc == "remix this reel" ||
            lowerDesc == "watch more reels" ||
            lowerText == "watch more reels" ||
            lowerDesc == "reels camera"
        ) {
            return true
        }

        val tabSelected = isSelected ||
            isFocused ||
            parentSelected ||
            lowerState.contains("selected") ||
            lowerDesc.contains("selected")

        // Bottom-nav / tab nodes exist on every Instagram screen; only block when selected/focused
        if (lowerId.contains("reels_tab") || lowerId.contains("clips_tab")) {
            return tabSelected
        }

        if (lowerDesc == "reels" ||
            lowerDesc == "reels tab" ||
            lowerDesc.startsWith("reels, ") ||
            lowerText == "reels"
        ) {
            return tabSelected
        }

        return false
    }

    fun isInstagramReelsActiveInHierarchy(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean =
        isInstagramReelsActiveInHierarchy(listOfNotNull(node))

    fun isInstagramReelsActiveInHierarchy(roots: List<AccessibilityNodeInfo>): Boolean {
        return bfsMatchesAny(roots) { node, viewId, desc, text, stateDesc, parentSelected ->
            isInstagramReelsNodeActive(
                viewId = viewId,
                desc = desc,
                text = text,
                isSelected = node.isSelected,
                isFocused = node.isFocused,
                stateDesc = stateDesc,
                parentSelected = parentSelected
            )
        }
    }

    private inline fun bfsMatchesAny(
        roots: List<AccessibilityNodeInfo>,
        predicate: (
            node: AccessibilityNodeInfo,
            viewId: String,
            desc: String,
            text: String,
            stateDesc: String,
            parentSelected: Boolean
        ) -> Boolean
    ): Boolean {
        if (roots.isEmpty()) return false
        val viewportRect = Rect()
        try {
            roots.firstOrNull()?.getBoundsInScreen(viewportRect)
        } catch (_: Exception) {
            viewportRect.setEmpty()
        }
        val hasViewport = viewportRect.width() > 0 && viewportRect.height() > 0
        val nodeRect = Rect()

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        for (root in roots.distinct()) {
            queue.addLast(root to 0)
        }
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_VISITED_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            val isVisible = try {
                node.isVisibleToUser
            } catch (_: Exception) {
                true
            }
            val isOnScreen = if (isVisible && hasViewport) {
                try {
                    node.getBoundsInScreen(nodeRect)
                    nodeRect.isEmpty || Rect.intersects(viewportRect, nodeRect)
                } catch (_: Exception) {
                    true
                }
            } else {
                isVisible
            }

            if (isOnScreen) {
                val viewId = node.viewIdResourceName ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                val text = node.text?.toString() ?: ""
                val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    node.stateDescription?.toString() ?: ""
                } else {
                    ""
                }
                val parentSelected = try {
                    val p = node.parent
                    (p?.isSelected == true) || (p?.isChecked == true) || (p?.parent?.isSelected == true)
                } catch (_: Exception) {
                    false
                }

                if (predicate(node, viewId, desc, text, stateDesc, parentSelected)) {
                    return true
                }
            }

            if (depth < MAX_HIERARCHY_DEPTH) {
                val count = node.childCount
                for (i in 0 until count) {
                    val child = node.getChild(i) ?: continue
                    queue.addLast(child to (depth + 1))
                }
            }
        }
        return false
    }

    private val REGULAR_YOUTUBE_WATCH_MARKERS = listOf(
        "enter fullscreen",
        "exit fullscreen",
        "minimize",
        "collapse",
        "autoplay is",
        "save to playlist",
        "download video",
        "next video",
        "previous video",
        "play next"
    )

    private val NON_SHORTS_YOUTUBE_MARKERS = listOf(
        "like this post",
        "dislike this post",
        "comment on this post",
        "community post",
        "image post",
        "search youtube",
        "notifications"
    )

    private val NON_SHORTS_YOUTUBE_TABS = setOf(
        "home",
        "subscriptions",
        "you",
        "library",
        "inicio",
        "suscripciones",
        "tú",
        "accueil",
        "abonnements",
        "vous"
    )

    private val WHATSAPP_NON_UPDATES_TABS = setOf(
        "chats",
        "calls",
        "communities",
        "llamadas",
        "comunidades",
        "discussions",
        "appels",
        "communautés",
        "anrufe",
        "conversas",
        "chamadas",
        "comunidades",
        "panggilan",
        "komunitas",
        "sohbetler",
        "aramalar",
        "soga",
        "simu"
    )

    private val ACTIVE_SHORTS_VIEW_IDS = listOf(
        "reel_time_bar",
        "reel_watch_fragment",
        "reel_watch_player",
        "reel_player_page_container",
        "reel_player_overlay",
        "reel_recycler",
        "reel_video_player",
        "reel_bottom_ui_container",
        "reel_progress_bar",
        "reel_metapanel",
        "reel_channel_bar",
        "reel_dyn_",
        "reel_pivot_button",
        "reel_main_container",
        "reel_vod_link",
        "shorts_container",
        "shorts_player",
        "shorts_video_player",
        "shorts_overlay",
        "shorts_action_bar"
    )

    private val ACTIVE_SHORTS_PLAYER_LABELS = listOf(
        "remix this short",
        "sound from this short",
        "like this short",
        "dislike this short",
        "share this short",
        "comment on this short",
        "pause short",
        "mute short",
        "unmute short",
        "search shorts",
        "more actions for this short",
        "captions for this short",
        "next short",
        "previous short"
    )

    private val WHATSAPP_CHANNEL_VIEW_IDS = listOf(
        "newsletter_directory",
        "directory_search",
        "directory_categories",
        "newsletter_categories",
        "newsletter_info",
        "newsletter_follow",
        "newsletter_unfollow",
        "newsletter_reaction",
        "newsletter_footer",
        "newsletter_chat",
        "channel_info"
    )

    private val WHATSAPP_ACTIVE_CHANNEL_PHRASES = listOf(
        "this channel has added privacy",
        "find channels to follow",
        "stay updated on topics that matter to you",
        "unfollow channel",
        "follow channel"
    )

    private val UNAMBIGUOUS_CHANNEL_ACTIONS = setOf(
        "find channels",
        "explore channels",
        "discover channels",
        "follow channel",
        "unfollow channel",
        "join channel",
        "search channels",
        "channel info",
        "view channel",
        "find channels to follow",
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
        "view all",
        "explore",
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
