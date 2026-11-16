package com.example.pguard.presentation.dashboard

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

internal val GoldColor = Color(0xFFFFB800)
internal val CardBackground = Color(0xFF18181C)
internal val ActionAlertBorderColor = Color(0x33FF5252)
internal val DialogContainerColor = Color(0xFF1E1E24)
internal val SectionHeaderColor = Color(0xFF383842)
internal val SwitchTrackOn = Color(0xFF4CAF50)
internal val SwitchThumbOff = Color(0xFF8E8E93)
internal val SwitchTrackOff = Color(0xFF2C2C30)

internal fun dividerColor() = Color.White.copy(alpha = 0.08f)

internal val TabHeaderShape = GenericShape { size, _ ->
    val cut = 18f
    val radius = 32f
    moveTo(0f, size.height)
    lineTo(0f, radius)
    arcTo(
        rect = Rect(0f, 0f, radius * 2, radius * 2),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    lineTo(size.width - cut - 8f, 0f)
    quadraticTo(
        size.width - cut, 0f,
        size.width - cut + 4f, 4f
    )
    lineTo(size.width, size.height)
    close()
}
