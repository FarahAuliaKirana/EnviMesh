package com.envimesh.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.envimesh.app.ui.EnviMeshViewModel
import com.envimesh.app.ui.screens.AlertScreen
import com.envimesh.app.ui.screens.HomeScreen
import com.envimesh.app.ui.screens.IntroScreen
import com.envimesh.app.ui.screens.MapScreen
import com.envimesh.app.ui.screens.MonitoringScreen
import com.envimesh.app.ui.screens.ScanScreen
import com.envimesh.app.ui.screens.SettingsScreen
import com.envimesh.app.ui.screens.SetupScreen
import com.envimesh.app.ui.theme.EnviAccent

object Routes {
    const val INTRO = "intro"
    const val SCAN = "scan"
    const val HOME = "home"
    const val MONITORING = "monitoring/{nodeId}"
    const val SETUP = "setup"
    const val MAP = "map"
    const val ALERT = "alert"
    const val SETTINGS = "settings"

    fun monitoring(nodeId: String) = "monitoring/$nodeId"
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, "Home", Icons.Filled.Home),
    BottomItem(Routes.MAP, "Map", Icons.Filled.LocationOn),
    BottomItem(Routes.ALERT, "Alert", Icons.Filled.Notifications),
    BottomItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun EnviMeshApp(vm: EnviMeshViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (bottomItems.any { it.route == route }) BottomBar(route, nav)
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.INTRO,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.INTRO) {
                IntroScreen(onFinished = {
                    nav.navigate(Routes.SCAN) { popUpTo(Routes.INTRO) { inclusive = true } }
                })
            }
            composable(Routes.SCAN) {
                ScanScreen(
                    state = state,
                    onScan = vm::scan,
                    onSelect = vm::connect,
                    onContinue = {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.SCAN) { inclusive = true } }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    state = state,
                    onSetup = { nav.navigate(Routes.SETUP) },
                    onOpenNode = { id -> nav.navigate(Routes.monitoring(id)) },
                )
            }
            composable(
                route = Routes.MONITORING,
                arguments = listOf(navArgument("nodeId") { type = NavType.StringType }),
            ) { entry ->
                MonitoringScreen(
                    state = state,
                    nodeId = entry.arguments?.getString("nodeId").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onShowOnMap = { lat, lon ->
                        vm.focusMap(lat, lon)
                        nav.navigate(Routes.MAP) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.SETUP) {
                SetupScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onSave = { config ->
                        vm.saveConfig(config)
                        nav.popBackStack()
                    },
                )
            }
            composable(Routes.MAP) { MapScreen(state) }
            composable(Routes.ALERT) {
                AlertScreen(
                    state = state,
                    onNavigate = { lat, lon ->
                        vm.focusMap(lat, lon)
                        nav.navigate(Routes.MAP) { launchSingleTop = true }
                    },
                    onConfirm = vm::confirmAlert,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = state,
                    onDisconnect = {
                        vm.disconnect()
                        nav.navigate(Routes.SCAN) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(current: String?, nav: NavHostController) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = current == item.route,
                onClick = {
                    if (current != item.route) {
                        nav.navigate(item.route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = EnviAccent,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
