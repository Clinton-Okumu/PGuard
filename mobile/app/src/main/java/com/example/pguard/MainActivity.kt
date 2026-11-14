package com.example.pguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.data.repository.AuthRepository
import com.example.pguard.presentation.login.LoginScreen
import com.example.pguard.presentation.login.LoginViewModel
import com.example.pguard.presentation.splash.SplashScreen
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install modern AndroidX SplashScreen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PGuardTheme {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                val authDataStore = remember { AuthDataStore(context.applicationContext) }
                val authRepository = remember {
                    AuthRepository(NetworkClient.getAuthApiService(context), authDataStore)
                }
                val loginViewModel = remember { LoginViewModel(authRepository) }

                val loginState by loginViewModel.uiState.collectAsState()
                val currentUser by authRepository.currentUser.collectAsState(initial = null)
                val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = false)

                var showSplash by remember { mutableStateOf(true) }
                var userDismissedLogin by remember { mutableStateOf(false) }

                // Automatically advance if login was successful
                LaunchedEffect(loginState.isSuccess) {
                    if (loginState.isSuccess) {
                        userDismissedLogin = true
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when {
                        showSplash -> {
                            SplashScreen(
                                onSplashFinished = { showSplash = false }
                            )
                        }
                        !userDismissedLogin && !isLoggedIn -> {
                            LoginScreen(
                                isLoading = loginState.isLoading,
                                errorMessage = loginState.errorMessage,
                                onErrorDismiss = { loginViewModel.dismissError() },
                                onSkipClick = { userDismissedLogin = true },
                                onGoogleLoginClick = { loginViewModel.signInWithGoogle(context) },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        else -> {
                            MainScreenPlaceholder(
                                user = currentUser,
                                onLogout = {
                                    coroutineScope.launch {
                                        authRepository.logout()
                                        userDismissedLogin = false
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreenPlaceholder(
    user: UserDto?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PGuard Dashboard",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (user != null) {
                Text(
                    text = "Authenticated via Go Backend\n${user.displayName ?: user.email}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoralPrimary,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Running in Guest Mode (Offline)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralPrimary
                )
            ) {
                Text(
                    text = if (user != null) "Log Out" else "Return to Sign In",
                    fontSize = 14.sp
                )
            }
        }
    }
}
