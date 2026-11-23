package com.example.pguard.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pguard.R
import com.example.pguard.presentation.theme.pguardColors

@Composable
internal fun BlockingHeader(
    onPremiumClick: () -> Unit,
    onGuideClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold = MaterialTheme.pguardColors.gold
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPremiumClick,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.2.dp, gold),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = gold.copy(alpha = 0.08f),
                contentColor = gold
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_crown),
                contentDescription = null,
                tint = gold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Premium",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = gold
            )
        }

        OutlinedButton(
            onClick = onGuideClick,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.2.dp, primary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = primary.copy(alpha = 0.08f),
                contentColor = primary
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_guide),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Guide",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = primary
            )
        }
    }
}

@Composable
internal fun AlertActionCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    trailingIcon: Int? = null,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onAction,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(id = trailingIcon),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = primary
                    )
                }
            }
        }
    }
}

@Composable
internal fun AccountabilityCard(
    mode: AccountabilityMode,
    partnerStatus: String,
    onManageMode: () -> Unit,
    onPartnerAction: () -> Unit,
    partnerActionLabel: String? = null,
    isPendingPartner: Boolean = false,
    isLinkedPartner: Boolean = false,
    onResendInvite: (() -> Unit)? = null,
    onViewSecurityAlerts: (() -> Unit)? = null,
    onInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val modeName = when (mode) {
        AccountabilityMode.NONE -> "None"
        AccountabilityMode.FRIEND -> "Friend (Accountability Partner)"
        AccountabilityMode.SELF -> "Myself (Typing Challenge)"
        AccountabilityMode.TIME_DELAY -> "24-Hour Time Delay"
    }

    val modeDetail = when (mode) {
        AccountabilityMode.NONE -> "No friction method active. Toggles can be turned off anytime without restriction."
        AccountabilityMode.FRIEND -> when {
            partnerStatus.startsWith("Active") -> partnerStatus
            partnerStatus.startsWith("Pending") -> "$partnerStatus (awaiting confirmation)"
            else -> "No partner linked yet. Tap Invite to add an accountability partner."
        }
        AccountabilityMode.SELF -> "60-second randomized typing challenge required to turn off blocks."
        AccountabilityMode.TIME_DELAY -> "24-hour waiting delay required to turn off blocks."
    }

    val cardIcon = when (mode) {
        AccountabilityMode.FRIEND -> R.drawable.ic_partner
        AccountabilityMode.SELF -> R.drawable.ic_target
        AccountabilityMode.TIME_DELAY -> R.drawable.ic_focus_mode
        AccountabilityMode.NONE -> R.drawable.ic_feature_shield
    }

    val badgeText = when (mode) {
        AccountabilityMode.NONE -> "None Active"
        AccountabilityMode.FRIEND -> if (partnerStatus.startsWith("Active")) "Friend Active" else "Setup Required"
        AccountabilityMode.SELF -> "Myself Active"
        AccountabilityMode.TIME_DELAY -> "24h Delay Active"
    }

    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = cardIcon),
                        contentDescription = null,
                        tint = if (mode == AccountabilityMode.NONE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Accountability",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (mode == AccountabilityMode.NONE) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            primary.copy(alpha = 0.15f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (mode == AccountabilityMode.NONE) {
                                MaterialTheme.colorScheme.outlineVariant
                            } else {
                                primary.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mode == AccountabilityMode.NONE) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                primary
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Status: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = modeName,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (mode == AccountabilityMode.NONE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = modeDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mode == AccountabilityMode.FRIEND && partnerStatus.startsWith("Active")) {
                        MaterialTheme.pguardColors.success
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (mode == AccountabilityMode.FRIEND && isPendingPartner && onResendInvite != null) {
                        OutlinedButton(
                            onClick = onResendInvite,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Resend",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (mode == AccountabilityMode.FRIEND && isLinkedPartner && onViewSecurityAlerts != null) {
                        OutlinedButton(
                            onClick = onViewSecurityAlerts,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Alerts",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (mode == AccountabilityMode.FRIEND && partnerActionLabel != null) {
                        OutlinedButton(
                            onClick = onPartnerAction,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = partnerActionLabel,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onManageMode,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(
    title: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .clip(TabHeaderShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(start = 16.dp, end = 26.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun ToggleSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isPremium: Boolean = false,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val extendedColors = MaterialTheme.pguardColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (onInfoClick != null) {
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPremium) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_crown),
                    contentDescription = "Premium",
                    tint = extendedColors.gold,
                    modifier = Modifier.size(18.dp)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = extendedColors.success,
                    uncheckedThumbColor = extendedColors.switchThumbOff,
                    uncheckedTrackColor = extendedColors.switchTrackOff,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
internal fun EditableSettingRow(
    title: String,
    subtitle: String,
    isPremium: Boolean = false,
    onEditClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (onInfoClick != null) {
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isPremium) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_crown),
                    contentDescription = "Premium",
                    tint = MaterialTheme.pguardColors.gold,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onEditClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
