package com.example.pguard.presentation.splash

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Globe entrance
    val globeScale = remember { Animatable(0.75f) }
    val globeAlpha = remember { Animatable(0f) }

    // 2. Shield Snap entrance
    val shieldScale = remember { Animatable(0.35f) }
    val shieldAlpha = remember { Animatable(0f) }

    // 3. Radar ping / firewall pulse
    val radarScale = remember { Animatable(0.9f) }
    val radarAlpha = remember { Animatable(0f) }

    // 4. Status Dot illumination
    val statusDotScale = remember { Animatable(0f) }
    val statusDotGlowAlpha = remember { Animatable(0f) }

    // 5. Staggered typography
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(14f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffsetY = remember { Animatable(10f) }
    val footerAlpha = remember { Animatable(0f) }

    // 6. Smooth exit
    val screenExitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // --- Stage 1: Globe fades and scales in (0ms - 200ms) ---
        launch {
            globeAlpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        }
        launch {
            globeScale.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }

        delay(160)

        // --- Stage 2: The Shield Snap with bouncy spring physics (160ms - 340ms) ---
        launch {
            shieldAlpha.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        }
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

        // --- Stage 3: Radar Ping & Green Status Dot pop (280ms - 650ms) ---
        launch {
            radarAlpha.snapTo(0.65f)
            radarScale.snapTo(0.9f)
            launch {
                radarScale.animateTo(1.6f, tween(420, easing = FastOutSlowInEasing))
            }
            launch {
                radarAlpha.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            }
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
            launch {
                statusDotGlowAlpha.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
            }
        }

        delay(100)

        // --- Stage 4: Staggered Brand Text Reveal (~380ms - 620ms) ---
        launch {
            titleAlpha.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }

        delay(80)

        launch {
            subtitleAlpha.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
        launch {
            subtitleOffsetY.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }

        launch {
            footerAlpha.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        }

        // --- Stage 5: Hold briefly for brand perception (Total active: ~780ms) ---
        delay(320)

        // --- Stage 6: Snappy, seamless exit transition (780ms - 940ms) ---
        screenExitAlpha.animateTo(0f, tween(160, easing = FastOutSlowInEasing))

        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .alpha(screenExitAlpha.value)
    ) {
        // Centered Emblem and Typography
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Outward Radar Ping / Active Firewall Pulse
                if (radarAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .scale(radarScale.value)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        CoralPrimary.copy(alpha = radarAlpha.value),
                                        CoralPrimary.copy(alpha = radarAlpha.value * 0.4f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }

                // Ambient soft shadow glow behind squircle
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(26.dp),
                            spotColor = CoralPrimary,
                            ambientColor = CoralPrimary
                        )
                )

                // Branded Squircle Container with Gradient
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    CoralPrimary,
                                    Color(0xFFE64A19)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Layer 1: Globe (scales and fades in first)
                    Image(
                        painter = painterResource(id = R.drawable.ic_pguard_globe_only),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .scale(globeScale.value)
                            .alpha(globeAlpha.value)
                    )

                    // Layer 2: The Shield Snap (snaps in over globe with spring bounce and clean cutout mask)
                    Image(
                        painter = painterResource(id = R.drawable.ic_pguard_shield_only),
                        contentDescription = "PGuard Shield",
                        modifier = Modifier
                            .size(72.dp)
                            .scale(shieldScale.value)
                            .alpha(shieldAlpha.value)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Brand Title ("PGuard")
            Text(
                text = "PGuard",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    letterSpacing = 1.2.sp
                ),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .offset(y = titleOffsetY.value.dp)
                    .alpha(titleAlpha.value)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Pill Badge with Illuminating Green Status Dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .offset(y = subtitleOffsetY.value.dp)
                    .alpha(subtitleAlpha.value)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(14.dp)
                ) {
                    // Status dot pulse glow ring
                    if (statusDotGlowAlpha.value > 0f) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    Color(0xFF4CAF50).copy(alpha = statusDotGlowAlpha.value),
                                    CircleShape
                                )
                        )
                    }
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .scale(statusDotScale.value)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                }

                Text(
                    text = "Always-On Content Shield",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                )
            }
        }

        // Bottom Security Watermark
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
                .alpha(footerAlpha.value)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_feature_shield),
                contentDescription = null,
                tint = CoralPrimary.copy(alpha = 0.65f),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "SECURE  •  PRIVATE  •  ACCOUNTABLE",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 1.4.sp
            )
        }
    }
}
