package com.example.pguard.presentation.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.PGuardTheme
import com.example.pguard.presentation.theme.pguardColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    isReady: Boolean = true,
    isProtectionActive: Boolean = true,
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentIsReady by rememberUpdatedState(isReady)
    val currentOnSplashFinished by rememberUpdatedState(onSplashFinished)

    val globeScale = remember { Animatable(0.75f) }
    val globeAlpha = remember { Animatable(0f) }

    val shieldScale = remember { Animatable(0.35f) }
    val shieldAlpha = remember { Animatable(0f) }

    val radarScale = remember { Animatable(0.9f) }
    val radarAlpha = remember { Animatable(0f) }

    val statusDotScale = remember { Animatable(0f) }
    val statusDotGlowAlpha = remember { Animatable(0f) }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(14f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffsetY = remember { Animatable(10f) }
    val footerAlpha = remember { Animatable(0f) }

    val screenExitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val animationsDisabled = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }

        if (animationsDisabled) {
            globeScale.snapTo(1f)
            globeAlpha.snapTo(1f)
            shieldScale.snapTo(1f)
            shieldAlpha.snapTo(1f)
            radarAlpha.snapTo(0f)
            statusDotScale.snapTo(1f)
            statusDotGlowAlpha.snapTo(0f)
            titleAlpha.snapTo(1f)
            titleOffsetY.snapTo(0f)
            subtitleAlpha.snapTo(1f)
            subtitleOffsetY.snapTo(0f)
            footerAlpha.snapTo(1f)

            if (!currentIsReady) {
                snapshotFlow { currentIsReady }.first { it }
            }
            currentOnSplashFinished()
            return@LaunchedEffect
        }

        // Stage 1: Globe fades and scales in
        launch { globeAlpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
        launch { globeScale.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
        delay(160)

        // Stage 2: Shield snap with bouncy spring physics
        launch { shieldAlpha.animateTo(1f, tween(140, easing = FastOutSlowInEasing)) }
        launch {
            shieldScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        delay(120)

        // Stage 3: Radar ping & status dot pop
        launch {
            radarAlpha.snapTo(0.65f)
            radarScale.snapTo(0.9f)
            launch { radarScale.animateTo(1.6f, tween(420, easing = FastOutSlowInEasing)) }
            launch { radarAlpha.animateTo(0f, tween(420, easing = FastOutSlowInEasing)) }
        }
        launch {
            statusDotGlowAlpha.snapTo(0.7f)
            launch {
                statusDotScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch { statusDotGlowAlpha.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
        }
        delay(100)

        // Stage 4: Staggered brand text reveal
        launch { titleAlpha.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
        launch { titleOffsetY.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
        delay(80)
        launch { subtitleAlpha.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
        launch { subtitleOffsetY.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
        launch { footerAlpha.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }

        // Stage 5: Brief hold + wait until initial auth state has loaded
        delay(320)
        if (!currentIsReady) {
            snapshotFlow { currentIsReady }.first { it }
        }

        // Stage 6: Exit fade
        screenExitAlpha.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
        currentOnSplashFinished()
    }

    val primary = MaterialTheme.colorScheme.primary
    val statusColor = if (isProtectionActive) MaterialTheme.pguardColors.success else MaterialTheme.pguardColors.gold
    val statusText = if (isProtectionActive) "Always-On Content Shield" else "Shield Inactive"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer { alpha = screenExitAlpha.value }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .drawBehind {
                            val currentAlpha = radarAlpha.value
                            if (currentAlpha > 0f) {
                                val radius = (size.minDimension / 2f) * radarScale.value
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            primary.copy(alpha = currentAlpha),
                                            primary.copy(alpha = currentAlpha * 0.4f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = radius.coerceAtLeast(1f)
                                    ),
                                    radius = radius
                                )
                            }
                        }
                )

                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = MaterialTheme.shapes.extraLarge,
                            spotColor = primary,
                            ambientColor = primary
                        )
                )

                // Flat primary fill matches the cutout mask in ic_pguard_shield_only without gradient seams
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(primary),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pguard_globe_only),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                scaleX = globeScale.value
                                scaleY = globeScale.value
                                alpha = globeAlpha.value
                            }
                    )

                    Image(
                        painter = painterResource(id = R.drawable.ic_pguard_shield_only),
                        contentDescription = "PGuard Shield",
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                scaleX = shieldScale.value
                                scaleY = shieldScale.value
                                alpha = shieldAlpha.value
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PGuard",
                style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer {
                    translationY = titleOffsetY.value * density
                    alpha = titleAlpha.value
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .graphicsLayer {
                        translationY = subtitleOffsetY.value * density
                        alpha = subtitleAlpha.value
                    }
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(14.dp)
                        .drawBehind {
                            val glowAlpha = statusDotGlowAlpha.value
                            if (glowAlpha > 0f) {
                                drawCircle(
                                    color = statusColor.copy(alpha = glowAlpha),
                                    radius = size.minDimension / 2f
                                )
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer {
                                scaleX = statusDotScale.value
                                scaleY = statusDotScale.value
                            }
                            .background(statusColor, CircleShape)
                    )
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .graphicsLayer { alpha = footerAlpha.value }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_feature_shield),
                contentDescription = null,
                tint = primary.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "SECURE  •  PRIVATE  •  ACCOUNTABLE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    PGuardTheme {
        SplashScreen(
            isReady = true,
            isProtectionActive = true,
            onSplashFinished = {}
        )
    }
}
