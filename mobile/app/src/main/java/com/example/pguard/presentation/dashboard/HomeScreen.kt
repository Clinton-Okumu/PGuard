package com.example.pguard.presentation.dashboard

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.data.repository.AccountabilityRepository
import com.example.pguard.data.repository.SettingsRepository
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.presentation.blocklist.BlocklistScreen
import com.example.pguard.presentation.focus.FocusModeScreen
import com.example.pguard.presentation.habits.HabitsScreen
import com.example.pguard.presentation.navigation.PGuardBottomBar
import com.example.pguard.presentation.navigation.PGuardTab
import com.example.pguard.presentation.premium.PremiumScreen
import com.example.pguard.presentation.settings.SettingsScreen
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.launch

private tailrec fun Context.findViewModelStoreOwner(): ViewModelStoreOwner? = when (this) {
    is ViewModelStoreOwner -> this
    is ContextWrapper -> baseContext.findViewModelStoreOwner()
    else -> null
}

@Composable
fun HomeScreen(
    user: UserDto?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val storeOwner = remember(view, context) {
        view.findViewTreeViewModelStoreOwner() ?: context.findViewModelStoreOwner()
    }

    val settingsRepository = remember {
        BlockingPreferences.loadSettings(context)
        SettingsRepository(NetworkClient.getSettingsApiService(context))
    }
    val accountabilityRepository = remember {
        AccountabilityRepository(NetworkClient.getAccountabilityApiService(context))
    }
    val authDataStore = remember { AuthDataStore(context.applicationContext) }
    val blockingViewModel = remember(storeOwner) {
        if (storeOwner != null) {
            ViewModelProvider(
                storeOwner,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return BlockingViewModel(settingsRepository, accountabilityRepository, authDataStore) as T
                    }
                }
            )[BlockingViewModel::class.java]
        } else {
            BlockingViewModel(settingsRepository, accountabilityRepository, authDataStore)
        }
    }

    LaunchedEffect(user?.email) {
        blockingViewModel.setCurrentUserEmail(user?.email)
    }

    val tabs = remember { PGuardTab.entries }
    var currentTab by remember { mutableStateOf(PGuardTab.BLOCKING) }
    var showPremiumScreen by remember { mutableStateOf(false) }
    var showFocusModeScreen by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = PGuardTab.BLOCKING.ordinal,
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                PGuardBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        if (currentTab != tab) {
                            currentTab = tab
                            coroutineScope.launch {
                                pagerState.scrollToPage(tab.ordinal)
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                beyondViewportPageCount = 3,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) { page ->
                when (tabs[page]) {
                    PGuardTab.BLOCKING -> {
                        BlockingScreen(
                            viewModel = blockingViewModel,
                            onPremiumClick = { showPremiumScreen = true },
                            onGuideClick = { /* Guide/FAQ modal */ },
                            onFocusModeClick = { showFocusModeScreen = true }
                        )
                    }
                    PGuardTab.BLOCKLIST -> {
                        BlocklistScreen(blockingViewModel = blockingViewModel)
                    }
                    PGuardTab.HABITS -> {
                        HabitsScreen(
                            user = user,
                            blockingViewModel = blockingViewModel,
                            isActiveTab = currentTab == PGuardTab.HABITS
                        )
                    }
                    PGuardTab.SETTINGS -> {
                        SettingsScreen(
                            user = user,
                            onLogout = onLogout,
                            blockingViewModel = blockingViewModel
                        )
                    }
                }
            }
        }

        // Full Screen Premium Screen overlay with smooth transition
        AnimatedVisibility(
            visible = showPremiumScreen,
            enter = fadeIn(tween(180)) + slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(200)
            ),
            exit = fadeOut(tween(150)) + slideOutVertically(
                targetOffsetY = { it / 8 },
                animationSpec = tween(150)
            )
        ) {
            PremiumScreen(
                onBackClick = { showPremiumScreen = false },
                onCheckoutClick = { plan ->
                    // Handle checkout flow
                }
            )
        }

        // Full Screen Focus Mode Screen overlay
        AnimatedVisibility(
            visible = showFocusModeScreen,
            enter = fadeIn(tween(180)) + slideInVertically(
                initialOffsetY = { it / 8 },
                animationSpec = tween(200)
            ),
            exit = fadeOut(tween(150)) + slideOutVertically(
                targetOffsetY = { it / 8 },
                animationSpec = tween(150)
            )
        ) {
            FocusModeScreen(
                onBackClick = { showFocusModeScreen = false },
                onUpgradeClick = {
                    showFocusModeScreen = false
                    showPremiumScreen = true
                },
                blockingViewModel = blockingViewModel
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PGuardTheme {
        HomeScreen(
            user = null,
            onLogout = {}
        )
    }
}
