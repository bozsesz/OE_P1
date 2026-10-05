package com.example.app_10.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.example.app_10.ui.analytics.AnalyticsScreen
import com.example.app_10.ui.dashboard.DashBoardScreen
import com.example.app_10.ui.home.HomeScreen
import com.example.app_10.ui.theme.App_10Theme

enum class AppDestination(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    DASHBOARD("Dashboard", Icons.Default.Menu),
    ANALYTICS("Analytics", Icons.Default.Settings)
}

@Composable
fun MainTabletApp(modifier: Modifier = Modifier) {
    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = { Icon(destination.icon, contentDescription = destination.title) },
                    label = { Text(destination.title) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestination.HOME -> HomeScreen()
            AppDestination.DASHBOARD -> DashBoardScreen()
            AppDestination.ANALYTICS -> AnalyticsScreen()
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
