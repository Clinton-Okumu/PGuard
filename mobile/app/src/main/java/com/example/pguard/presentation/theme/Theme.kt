package com.example.pguard.presentation.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class PGuardExtendedColors(
    val success: Color,
    val gold: Color,
    val info: Color,
    val switchThumbOff: Color,
    val switchTrackOff: Color,
    val coralDeep: Color
)

private val DarkExtendedColors = PGuardExtendedColors(
    success = SuccessGreen,
    gold = PremiumGold,
    info = InfoBlue,
    switchThumbOff = SwitchThumbOffDark,
    switchTrackOff = SwitchTrackOffDark,
    coralDeep = CoralDeep
)

val LocalPGuardColors = staticCompositionLocalOf { DarkExtendedColors }

val MaterialTheme.pguardColors: PGuardExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPGuardColors.current

private val DarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralContainer,
    onPrimaryContainer = OnCoralContainer,
    secondary = CoralSecondary,
    onSecondary = Color.White,
    tertiary = CoralTertiary,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceHighDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = SurfaceLowestDark,
    surfaceContainerLow = SurfaceLowDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = Color.White
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PGuardTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalPGuardColors provides DarkExtendedColors) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            shapes = PGuardShapes,
            content = content
        )
    }
}
