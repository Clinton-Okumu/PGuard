package com.example.pguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.data.repository.AuthRepository
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accountability.TimeDelayNotificationScheduler
import com.example.pguard.presentation.dashboard.HomeScreen
import com.example.pguard.presentation.habits.HabitsManager
import com.example.pguard.presentation.login.LoginScreen
import com.example.pguard.presentation.login.LoginViewModel
import com.example.pguard.presentation.splash.SplashScreen
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install modern AndroidX SplashScreen and dismiss instantly to avoid double splash
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BlockingPreferences.loadSettings(applicationContext)

        val authDataStore = AuthDataStore(applicationContext)
        val authRepository = AuthRepository(
            NetworkClient.getAuthApiService(applicationContext),
            authDataStore
        )
        val loginViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LoginViewModel(authRepository) as T
                }
            }
        )[LoginViewModel::class.java]

        setContent {
            PGuardTheme {
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                val loginState by loginViewModel.uiState.collectAsState()
                val currentUser by authRepository.currentUser.collectAsState(initial = null)
                val isLoggedInState by authRepository.isLoggedIn.collectAsState(initial = null)
                val isAuthLoaded = isLoggedInState != null
                val isLoggedIn = isLoggedInState == true

                var showSplash by rememberSaveable { mutableStateOf(true) }
                var userDismissedLogin by rememberSaveable { mutableStateOf(false) }
                val isProtectionActive = remember(context) { HabitsManager.isAppDoingItsJob(context) }

                // Automatically advance if login was successful
                LaunchedEffect(loginState.isSuccess) {
                    if (loginState.isSuccess) {
                        userDismissedLogin = true
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        showSplash -> {
                            SplashScreen(
                                isReady = isAuthLoaded,
                                isProtectionActive = isProtectionActive,
                                onSplashFinished = { showSplash = false }
                            )
                        }
                        !userDismissedLogin && !isLoggedIn -> {
                            LoginScreen(
                                isLoading = loginState.isLoading,
                                errorMessage = loginState.errorMessage,
                                onErrorDismiss = { loginViewModel.dismissError() },
                                onSkipClick = { userDismissedLogin = true },
                                onGoogleLoginClick = { loginViewModel.signInWithGoogle(context) }
                            )
                        }
                        else -> {
                            HomeScreen(
                                user = currentUser,
                                onSignInRequest = {
                                    userDismissedLogin = false
                                },
                                onLogout = {
                                    coroutineScope.launch {
                                        authRepository.logout()
                                        loginViewModel.resetState()
                                        BlockingPreferences.clearPartnerCache(applicationContext)
                                        BlockingPreferences.clearTimeDelay(applicationContext)
                                        TimeDelayNotificationScheduler.cancel(applicationContext)
                                        BlockingPreferences.clearPendingBlocklistDeletes(applicationContext)
                                        BlockingPreferences.markLocalDirty(applicationContext, false)
                                        HabitsManager.clearAllUserDataOnLogout(applicationContext)
                                        userDismissedLogin = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
