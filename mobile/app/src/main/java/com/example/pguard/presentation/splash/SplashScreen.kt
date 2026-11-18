package com.example.pguard.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
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
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(25f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffsetY = remember { Animatable(15f) }
    val footerAlpha = remember { Animatable(0f) }
    val screenExitAlpha = remember { Animatable(1f) }

    // Breathing glow animation around the shield emblem
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LaunchedEffect(Unit) {
        // 1. Logo spring bounce entrance
        launch {
            logoAlpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        delay(220)

        // 2. Title slide up and fade in
        launch {
            titleAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        delay(160)

        // 3. Subtitle badge slide up and fade in
        launch {
            subtitleAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        }
        launch {
            subtitleOffsetY.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        }

        // 4. Security footer watermark fade in
        launch {
            footerAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }

        // Display for perceived branding and smooth pulse
        delay(1250)

        // 5. Smooth exit fade transition
        screenExitAlpha.animateTo(0f, tween(320, easing = FastOutSlowInEasing))

        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
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
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            ) {
                // Outer Ambient Glowing Aura
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    CoralPrimary.copy(alpha = pulseAlpha),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Secondary soft glow ring
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .background(
                            CoralPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(32.dp)
                        )
                )

                // Branded Shield Container with Gradient & Shadow
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(26.dp),
                            spotColor = CoralPrimary,
                            ambientColor = CoralPrimary
                        )
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
                    Image(
                        painter = painterResource(id = R.drawable.ic_pguard_globe_shield),
                        contentDescription = "PGuard Logo",
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Main Brand Title
            Text(
                text = "PGuard",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp,
                    letterSpacing = 1.2.sp
                ),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .offset(y = titleOffsetY.value.dp)
                    .alpha(titleAlpha.value)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Live Protection Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .offset(y = subtitleOffsetY.value.dp)
                    .alpha(subtitleAlpha.value)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
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
                .padding(bottom = 36.dp)
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
