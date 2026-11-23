package com.example.pguard.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pguard.R
import com.example.pguard.core.network.model.UserSettingsDto
import com.example.pguard.engine.accessibility.BrowserInspector

enum class BlockingToggle {
    ADULT_CONTENT,
    DNS_VPN_SHIELD,
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
    isDnsVpnEnabled: Boolean = false,
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ToggleSettingRow(
            title = "Network DNS & WebView Shield",
            checked = isDnsVpnEnabled,
            onCheckedChange = { onToggle(BlockingToggle.DNS_VPN_SHIELD, it) },
            onInfoClick = {
                onInfo(
                    "Network DNS & WebView Shield",
                    "Runs a 100% on-device local DNS filter via Android's VPN API so adult websites, SafeSearch enforcement, and custom blocked domains are stopped before loading across all browsers and in-app WebViews (Reddit, X, Telegram, Discord). Your browsing traffic never leaves your phone through any remote VPN server."
                )
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                    "Prevents PGuard from being uninstalled or tampered with. Can only be turned off by passing your accountability check."
                )
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ToggleSettingRow(
            title = "Block unsupported browsers",
            checked = settings.blockUnsupportedBrowsers,
            onCheckedChange = { onToggle(BlockingToggle.UNSUPPORTED_BROWSERS, it) },
            onInfoClick = {
                val browsersList = BrowserInspector.SUPPORTED_BROWSER_NAMES
                    .mapIndexed { index, name -> "${index + 1}. $name" }
                    .joinToString("\n")
                onInfo(
                    "Supported Browsers",
                    "PGuard blocks web browsers that do not support content filtering to ensure your protection is not bypassed.\n\nSupported Browsers (${BrowserInspector.SUPPORTED_BROWSER_NAMES.size}):\n$browsersList\n\nAll other browsers will be blocked when this setting is enabled."
                )
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
    onPreview: () -> Unit = {},
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        EditableSettingRow(
            title = "Blocked screen countdown",
            subtitle = countdownLabel(settings.blockedScreenCountdown),
            isPremium = true,
            onEditClick = { onEdit(SettingsEditor.COUNTDOWN) },
            onInfoClick = {
                onInfo(
                    "Blocked screen countdown",
                    "How long before the blocked screen automatically closes or redirects. Choose Manual to keep it open until you tap a button."
                )
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        EditableSettingRow(
            title = "Redirect after closing",
            subtitle = redirectLabel(settings.redirectUrl),
            isPremium = true,
            onEditClick = { onEdit(SettingsEditor.REDIRECT) },
            onInfoClick = {
                onInfo(
                    "Redirect after closing",
                    "Optionally redirect to a chosen safe website when closing the blocked screen. Blocked or adult sites are not allowed."
                )
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onPreview,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Preview Blocked Screen",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

internal fun countdownLabel(seconds: Int): String = when {
    seconds <= 0 -> "Manual (no auto-close)"
    seconds == 1 -> "1 second"
    else -> "$seconds seconds"
}

internal fun redirectLabel(url: String): String =
    url.ifBlank { "Not set" }
