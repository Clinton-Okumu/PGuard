package com.example.pguard.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary

@Composable
internal fun BlockingHeader(
    onPremiumClick: () -> Unit,
    onGuideClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPremiumClick,
            shape = CircleShape,
            border = BorderStroke(1.2.dp, GoldColor),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = GoldColor.copy(alpha = 0.08f),
                contentColor = GoldColor
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_crown),
                contentDescription = null,
                tint = GoldColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Premium",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = GoldColor
            )
        }

        OutlinedButton(
            onClick = onGuideClick,
            shape = CircleShape,
            border = BorderStroke(1.2.dp, CoralPrimary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CoralPrimary.copy(alpha = 0.08f),
                contentColor = CoralPrimary
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_guide),
                contentDescription = null,
                tint = CoralPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Guide",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = CoralPrimary
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, ActionAlertBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
            }

            OutlinedButton(
                onClick = onAction,
                shape = CircleShape,
                border = BorderStroke(1.dp, CoralPrimary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CoralPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(id = trailingIcon),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = CoralPrimary
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
    onInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val modeName = when (mode) {
        AccountabilityMode.FRIEND -> "Friend (Email Invite)"
        AccountabilityMode.SELF -> "Myself (Typing Challenge)"
        AccountabilityMode.TIME_DELAY -> "24-Hour Time Delay"
        AccountabilityMode.NONE -> "Not configured"
    }

    val modeDetail = when (mode) {
        AccountabilityMode.FRIEND -> partnerStatus
        AccountabilityMode.SELF -> "60-second typing challenge required to turn off blocks"
        AccountabilityMode.TIME_DELAY -> "24-hour waiting delay required to turn off blocks"
        AccountabilityMode.NONE -> "Tap Setup to activate unlock protection"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, dividerColor())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Heading is ALWAYS "Accountability Partner"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_partner),
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Accountability Partner",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = "Info",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = dividerColor()
            )

            // Content below heading: Type line + Additional details + Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Type: ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = modeName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = modeDetail,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (mode == AccountabilityMode.FRIEND && partnerStatus.startsWith("Active")) {
                            Color(0xFF4CAF50)
                        } else {
                            Color.White.copy(alpha = 0.65f)
                        },
                        lineHeight = 16.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (partnerActionLabel != null) {
                        OutlinedButton(
                            onClick = onPartnerAction,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White.copy(alpha = 0.85f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = partnerActionLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onManageMode,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CoralPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (mode == AccountabilityMode.NONE) "Setup" else "Change",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
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
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, dividerColor())
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .clip(TabHeaderShape)
                    .background(SectionHeaderColor)
                    .padding(start = 16.dp, end = 26.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        ),
                        color = Color.White
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(vertical = 10.dp),
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
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (onInfoClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = "Info",
                        tint = Color.White.copy(alpha = 0.5f),
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
                    tint = GoldColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SwitchTrackOn,
                    uncheckedThumbColor = SwitchThumbOff,
                    uncheckedTrackColor = SwitchTrackOff,
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
            .padding(vertical = 10.dp),
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (onInfoClick != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = "Info",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPremium) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_crown),
                    contentDescription = "Premium",
                    tint = GoldColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "Edit",
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
