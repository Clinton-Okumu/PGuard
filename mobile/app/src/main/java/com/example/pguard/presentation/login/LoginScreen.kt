package com.example.pguard.presentation.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme
import com.example.pguard.presentation.theme.SurfaceDark
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onSkipClick: () -> Unit = {},
    onGoogleLoginClick: () -> Unit = {},
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val contentAlpha = remember { Animatable(0f) }
    val contentOffsetY = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        launch {
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }
        launch {
            contentOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Ambient Radial Glow behind the shield
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopCenter)
                .offset(y = 20.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CoralPrimary.copy(alpha = 0.20f),
                            CoralPrimary.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Top "SKIP" Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, end = 20.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onSkipClick,
                shape = CircleShape,
                border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.85f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CoralPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = "SKIP",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = CoralPrimary
                )
            }
        }

        // Main Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .alpha(contentAlpha.value)
                .offset(y = contentOffsetY.value.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Official PGuard Shield Brand Logo
            Image(
                painter = painterResource(id = R.drawable.ic_pguard_shield),
                contentDescription = "PGuard Shield Logo",
                modifier = Modifier.size(92.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Main Value Proposition Headline
            Text(
                text = "Take Control of Your Focus",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Block adult content across all browsers & apps.\nStay accountable to what truly matters.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Trust & Feature Highlights Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark.copy(alpha = 0.7f))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureHighlightRow(
                    iconRes = R.drawable.ic_feature_shield,
                    title = "System-Wide Protection",
                    subtitle = "Blocks adult sites in all browsers, apps & private tabs"
                )
                FeatureHighlightRow(
                    iconRes = R.drawable.ic_feature_lock,
                    title = "100% Private & On-Device",
                    subtitle = "Zero browsing history is ever logged or shared"
                )
                FeatureHighlightRow(
                    iconRes = R.drawable.ic_feature_bolt,
                    title = "Fast & Battery Friendly",
                    subtitle = "Local DNS engine ensures zero lag and zero battery drain"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Google Login Button
            Button(
                onClick = onGoogleLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralPrimary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = "Google Logo",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Login with Google",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Terms & Privacy Policy Disclaimer
            val termsAnnotatedString = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        fontSize = 11.5.sp
                    )
                ) {
                    append("By continuing, you agree to our\n")
                }
                pushStringAnnotation(tag = "terms", annotation = "terms")
                withStyle(
                    style = SpanStyle(
                        color = CoralPrimary.copy(alpha = 0.9f),
                        fontSize = 11.5.sp,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Terms of Service")
                }
                pop()

                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        fontSize = 11.5.sp
                    )
                ) {
                    append(" and ")
                }

                pushStringAnnotation(tag = "privacy", annotation = "privacy")
                withStyle(
                    style = SpanStyle(
                        color = CoralPrimary.copy(alpha = 0.9f),
                        fontSize = 11.5.sp,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Privacy Policy")
                }
                pop()

                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        fontSize = 11.5.sp
                    )
                ) {
                    append(".")
                }
            }

            Text(
                text = termsAnnotatedString,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .clickable {
                        // Future handler for links
                    }
            )
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(CoralPrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = CoralPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun LoginScreenPreview() {
    PGuardTheme(darkTheme = true) {
        LoginScreen()
    }
}
