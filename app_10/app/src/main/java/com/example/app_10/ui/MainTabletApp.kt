package com.example.app_10.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.example.app_10.ui.analytics.AnalyticsScreen
import com.example.app_10.ui.dashboard.DashBoardScreen
import com.example.app_10.ui.home.HomeScreen
import com.example.app_10.ui.theme.App_10Theme

enum class AppScreen {
    MAIN_MENU,
    DASHBOARD,
    ANALYTICS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabletApp(modifier: Modifier = Modifier) {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.MAIN_MENU) }

    // Intercept hardware/system back gesture when on Dashboard or Analytics
    BackHandler(enabled = currentScreen != AppScreen.MAIN_MENU) {
        currentScreen = AppScreen.MAIN_MENU
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (currentScreen != AppScreen.MAIN_MENU) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                AppScreen.DASHBOARD -> "Dashboard Workspace"
                                AppScreen.ANALYTICS -> "Analytics Workspace"
                                AppScreen.MAIN_MENU -> "Main Menu"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { currentScreen = AppScreen.MAIN_MENU }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Main Menu"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (currentScreen) {
            AppScreen.MAIN_MENU -> {
                HomeScreen(
                    onNavigateToDashboard = { currentScreen = AppScreen.DASHBOARD },
                    onNavigateToAnalytics = { currentScreen = AppScreen.ANALYTICS },
                    modifier = contentModifier
                )
            }
            AppScreen.DASHBOARD -> {
                DashBoardScreen(modifier = contentModifier)
            }
            AppScreen.ANALYTICS -> {
                AnalyticsScreen(modifier = contentModifier)
            }
        }
    }
}

@Preview(name = "Tablet View", device = Devices.TABLET, showBackground = true)
@Composable
fun MainTabletAppPreview() {
    App_10Theme {
        MainTabletApp()
    }
}
