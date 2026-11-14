package com.example.pguard.presentation.dashboard

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.presentation.blocklist.BlocklistScreen
import com.example.pguard.presentation.habits.HabitsScreen
import com.example.pguard.presentation.navigation.PGuardBottomBar
import com.example.pguard.presentation.navigation.PGuardTab
import com.example.pguard.presentation.premium.PremiumScreen
import com.example.pguard.presentation.settings.SettingsScreen
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    user: UserDto?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { PGuardTab.entries }
    var currentTab by remember { mutableStateOf(PGuardTab.BLOCKING) }
    var showPremiumScreen by remember { mutableStateOf(false) }

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
                            onPremiumClick = { showPremiumScreen = true },
                            onGuideClick = { /* Guide/FAQ modal */ },
                            onFocusModeClick = { /* Focus mode sheet */ }
                        )
                    }
                    PGuardTab.BLOCKLIST -> {
                        BlocklistScreen()
                    }
                    PGuardTab.HABITS -> {
                        HabitsScreen()
                    }
                    PGuardTab.SETTINGS -> {
                        SettingsScreen(
                            user = user,
                            onLogout = onLogout
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
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun HomeScreenPreview() {
    PGuardTheme(darkTheme = true) {
        HomeScreen(
            user = null,
            onLogout = {}
        )
    }
}
