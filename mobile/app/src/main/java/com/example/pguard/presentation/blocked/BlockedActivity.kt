package com.example.pguard.presentation.blocked

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pguard.R
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.delay

class BlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_URL = "extra_blocked_url"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_COUNTDOWN = "extra_countdown"
        const val EXTRA_REDIRECT_URL = "extra_redirect_url"

        fun start(
            context: Context,
            url: String,
            message: String = "This page is blocked.",
            countdownSeconds: Int = 3,
            redirectUrl: String = ""
        ) {
            val intent = Intent(context, BlockedActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_URL, url)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_COUNTDOWN, countdownSeconds)
                putExtra(EXTRA_REDIRECT_URL, redirectUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }

    private var blockedUrlState by mutableStateOf("Restricted content")
    private var messageState by mutableStateOf("This page is blocked.")
    private var countdownState by mutableIntStateOf(3)
    private var redirectUrlState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIntentExtras(intent)

        setContent {
            PGuardTheme {
                BackHandler {
                    handleDismiss(redirectUrlState)
                }

                BlockedScreenContent(
                    blockedUrl = blockedUrlState,
                    message = messageState,
                    initialCountdown = countdownState,
                    onGoHome = { handleDismiss(redirectUrlState) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentExtras(intent)
    }

    private fun applyIntentExtras(intent: Intent?) {
        blockedUrlState = intent?.getStringExtra(EXTRA_BLOCKED_URL) ?: "Restricted content"
        messageState = intent?.getStringExtra(EXTRA_MESSAGE) ?: "This page is blocked."
        countdownState = intent?.getIntExtra(EXTRA_COUNTDOWN, 3) ?: 3
        redirectUrlState = intent?.getStringExtra(EXTRA_REDIRECT_URL) ?: ""
    }

    private fun handleDismiss(redirectUrl: String) {
        if (redirectUrl.isNotBlank()) {
            try {
                val fullUrl = if (!redirectUrl.startsWith("http://") && !redirectUrl.startsWith("https://")) {
                    "https://$redirectUrl"
                } else {
                    redirectUrl
                }
                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fullUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(browserIntent)
                finish()
                return
            } catch (e: Exception) {
                android.util.Log.e("BlockedActivity", "Failed to launch redirect URL: ${e.message}")
            }
        }
        returnToHomeScreen()
    }

    private fun returnToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
private fun BlockedScreenContent(
    blockedUrl: String,
    message: String,
    initialCountdown: Int,
    onGoHome: () -> Unit
) {
    var timeLeft by remember(blockedUrl, initialCountdown) {
        mutableIntStateOf(if (initialCountdown <= 0) 0 else initialCountdown)
    }

    LaunchedEffect(blockedUrl, initialCountdown) {
        if (timeLeft > 0) {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft -= 1
            }
            onGoHome()
        }
    }

    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // PGuard App Brand Logo
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(primary),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_pguard_globe_shield),
                    contentDescription = "PGuard Logo",
                    modifier = Modifier.size(62.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Access Blocked",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Blocked item chip
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = blockedUrl,
                    color = primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Action button
            Button(
                onClick = onGoHome,
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(50.dp)
            ) {
                Text(
                    text = if (timeLeft > 0) "Go Back ($timeLeft s)" else "Go Back to Safety",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
