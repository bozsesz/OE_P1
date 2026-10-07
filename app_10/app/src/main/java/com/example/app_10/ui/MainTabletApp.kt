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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app_10.ui.analytics.AnalyticsScreen
import com.example.app_10.ui.dashboard.DashBoardScreen
import com.example.app_10.ui.home.HomeScreen
import com.example.app_10.ui.theme.App_10Theme

enum class AppScreen {
    MAIN_MENU,
    DASHBOARD,
    ANALYTICS
}

@Composable
fun MainTabletApp(
    modifier: Modifier = Modifier,
    viewModel: SensorViewModel = viewModel()
) {
    val sensorReadings by viewModel.sensorReadings.collectAsState()
    val faultySensors by viewModel.faultySensors.collectAsState()
    val sensorHistory by viewModel.sensorHistory.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    MainTabletAppContent(
        modifier = modifier,
        sensorReadings = sensorReadings,
        faultySensors = faultySensors,
        sensorHistory = sensorHistory,
        connectionStatus = connectionStatus,
        onToggleFault = { sensorId -> viewModel.toggleSensorFault(sensorId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabletAppContent(
    modifier: Modifier = Modifier,
    sensorReadings: Map<Int, Float> = mapOf(
        1 to 22.5f, 2 to 18.0f, 3 to -999.0f, 4 to 15.0f, 5 to 25.0f, 6 to 40.0f
    ),
    faultySensors: Set<Int> = setOf(3),
    sensorHistory: Map<Int, List<Float>> = mapOf(
        1 to listOf(22.5f, 35.0f, 48.2f, 62.0f, 75.5f, 88.0f, 94.2f),
        2 to listOf(18.0f, 24.5f, 31.0f, 40.2f, 49.8f, 58.4f, 65.0f),
        3 to emptyList(),
        4 to listOf(15.0f, 28.0f, 45.0f, 65.0f, 82.0f, 98.5f, 112.0f),
        5 to listOf(25.0f, 27.0f, 29.5f, 31.0f, 33.2f, 36.0f, 38.5f),
        6 to listOf(40.0f, 42.0f, 48.0f, 56.5f, 68.0f, 80.2f, 91.0f)
    ),
    connectionStatus: String = "Listening on Wi-Fi (UDP Port 5005)",
    onToggleFault: (Int) -> Unit = {}
) {
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
                DashBoardScreen(
                    sensorReadings = sensorReadings,
                    faultySensors = faultySensors,
                    onToggleFault = onToggleFault,
                    connectionStatus = connectionStatus,
                    modifier = contentModifier
                )
            }
            AppScreen.ANALYTICS -> {
                AnalyticsScreen(
                    sensorHistory = sensorHistory,
                    faultySensors = faultySensors,
                    modifier = contentModifier
                )
            }
        }
    }
}

@Preview(name = "Tablet View", device = Devices.TABLET, showBackground = true)
@Composable
fun MainTabletAppPreview() {
    App_10Theme {
        MainTabletAppContent()
    }
}
