package com.example.pguard.presentation.dashboard

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.pguard.R
import com.example.pguard.core.network.model.UserSettingsDto

enum class BlockingToggle {
    ADULT_CONTENT,
    SHORTS,
    WHATSAPP_CHANNELS,
    INSTAGRAM_REELS,
    TIKTOK,
    UNINSTALL_PROTECTION,
    UNSUPPORTED_BROWSERS,
    NEW_INSTALLED_APPS
}

@Composable
internal fun ContentBlockingSection(
    settings: UserSettingsDto,
    onToggle: (BlockingToggle, Boolean) -> Unit,
    onInfo: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "Content Blocking",
        iconRes = R.drawable.ic_pguard_globe_shield,
        modifier = modifier
    ) {
        ToggleSettingRow(
            title = "Block adult content",
            checked = settings.blockAdultContent,
            onCheckedChange = { onToggle(BlockingToggle.ADULT_CONTENT, it) },
            onInfoClick = {
                onInfo(
                    "Block adult content",
                    "Automatically blocks pornography, adult websites, and explicit content across supported browsers."
                )
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        ToggleSettingRow(
            title = "Block Youtube Shorts",
            checked = settings.blockShorts,
            onCheckedChange = { onToggle(BlockingToggle.SHORTS, it) },
            onInfoClick = {
                onInfo(
                    "Block Youtube Shorts",
                    "Automatically closes YouTube Shorts to stop infinite algorithm rabbit holes."
                )
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        ToggleSettingRow(
            title = "Block WhatsApp channels",
            isPremium = true,
            checked = settings.blockWhatsAppChannels,
            onCheckedChange = { onToggle(BlockingToggle.WHATSAPP_CHANNELS, it) },
            onInfoClick = {
                onInfo(
                    "Block WhatsApp channels",
                    "Blocks public WhatsApp channels and updates tab to protect your attention."
                )
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        ToggleSettingRow(
            title = "Block Instagram Reels",
            checked = settings.blockInstagramReels,
            onCheckedChange = { onToggle(BlockingToggle.INSTAGRAM_REELS, it) },
            onInfoClick = {
                onInfo(
                    "Block Instagram Reels",
                    "Closes Instagram Reels to stop doomscrolling while keeping your main feed, direct messages, and stories open."
                )
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        ToggleSettingRow(
            title = "Block TikTok",
            checked = settings.blockTikTok,
            onCheckedChange = { onToggle(BlockingToggle.TIKTOK, it) },
            onInfoClick = {
                onInfo(
                    "Block TikTok",
                    "Blocks the TikTok application and web domain entirely to eliminate short-form video distraction."
                )
            }
        )
    }
}

@Composable
internal fun AdvancedBlockingSection(
    settings: UserSettingsDto,
    onToggle: (BlockingToggle, Boolean) -> Unit,
    onInfo: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "Advanced Blocking",
        iconRes = R.drawable.ic_nav_blocking,
        modifier = modifier
    ) {
        ToggleSettingRow(
            title = "Uninstall protection",
            checked = settings.uninstallProtection,
            onCheckedChange = { onToggle(BlockingToggle.UNINSTALL_PROTECTION, it) },
            onInfoClick = {
                onInfo(
                    "Uninstall protection",
                    "Prevents PGuard from being removed or tampered with without authorization."
                )
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        ToggleSettingRow(
            title = "Block unsupported browsers",
            checked = settings.blockUnsupportedBrowsers,
            onCheckedChange = { onToggle(BlockingToggle.UNSUPPORTED_BROWSERS, it) },
            onInfoClick = {
                onInfo(
                    "Block unsupported browsers",
                    "Blocks web browsers that do not support PGuard content filtering."
                )
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        ToggleSettingRow(
            title = "Block newly installed apps",
            isPremium = true,
            checked = settings.blockNewlyInstalledApps,
            onCheckedChange = { onToggle(BlockingToggle.NEW_INSTALLED_APPS, it) },
            onInfoClick = {
                onInfo(
                    "Block newly installed apps",
                    "Automatically blocks any newly installed apps until you review and allow them."
                )
            }
        )
    }
}

@Composable
internal fun CustomizeBlockedScreenSection(
    settings: UserSettingsDto,
    onEdit: (SettingsEditor) -> Unit,
    onInfo: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "Customize Blocked screen",
        iconRes = R.drawable.ic_blocked_screen,
        modifier = modifier
    ) {
        EditableSettingRow(
            title = "Blocked screen message",
            subtitle = settings.blockedScreenMessage,
            isPremium = true,
            onEditClick = { onEdit(SettingsEditor.MESSAGE) },
            onInfoClick = {
                onInfo(
                    "Blocked screen message",
                    "The message displayed on screen whenever a blocked website or app is accessed."
                )
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        EditableSettingRow(
            title = "Blocked screen countdown",
            subtitle = countdownLabel(settings.blockedScreenCountdown),
            isPremium = true,
            onEditClick = { onEdit(SettingsEditor.COUNTDOWN) },
            onInfoClick = {
                onInfo(
                    "Blocked screen countdown",
                    "The countdown timer duration before the blocked screen automatically dismisses."
                )
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        EditableSettingRow(
            title = "Redirect after closing (any URL)",
            subtitle = redirectLabel(settings.redirectUrl),
            isPremium = true,
            onEditClick = { onEdit(SettingsEditor.REDIRECT) },
            onInfoClick = {
                onInfo(
                    "Redirect after closing",
                    "Optionally redirect to a chosen safe URL or homepage when closing the blocked screen."
                )
            }
        )
    }
}

internal fun countdownLabel(seconds: Int): String =
    if (seconds <= 0) "Immediately" else "$seconds seconds"

internal fun redirectLabel(url: String): String =
    url.ifBlank { "Not set" }
