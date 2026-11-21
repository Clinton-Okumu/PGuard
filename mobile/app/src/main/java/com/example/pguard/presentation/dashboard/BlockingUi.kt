package com.example.pguard.presentation.dashboard

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Rect

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
