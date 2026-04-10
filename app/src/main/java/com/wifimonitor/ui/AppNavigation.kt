package com.wifimonitor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wifimonitor.ui.screens.*

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object DeviceList : Screen("device_list")
    object DeviceDetail : Screen("device_detail/{mac}") {
        fun createRoute(mac: String) = "device_detail/$mac"
    }
    object Traffic : Screen("traffic")
    object Activity : Screen("activity")
    object Domains : Screen("domains")
    object Control : Screen("control")
    object Alerts : Screen("alerts")
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
    object Timeline : Screen("timeline")
    object Analytics : Screen("analytics")
    object TacticalMap : Screen("tactical_map")
    object RuleManager : Screen("rule_manager")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.DeviceList.route) {
            DeviceListScreen(navController = navController)
        }
        composable(
            route = Screen.DeviceDetail.route,
            arguments = listOf(navArgument("mac") { type = NavType.StringType })
        ) { backStackEntry ->
            DeviceDetailScreen(
                mac = backStackEntry.arguments?.getString("mac") ?: "",
                navController = navController
            )
        }
        composable(Screen.Traffic.route) {
            TrafficScreen(navController = navController)
        }
        composable(Screen.Activity.route) {
            ActivityScreen(navController = navController)
        }
        composable(Screen.Domains.route) {
            DomainsScreen(navController = navController)
        }
        composable(Screen.Control.route) {
            ControlScreen(navController = navController)
        }
        composable(Screen.Alerts.route) {
            AlertsScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen()
        }
        composable(Screen.Timeline.route) {
            TimelineScreen(navController = navController)
        }
        composable(Screen.Analytics.route) {
            AnalyticsScreen()
        }
        composable(Screen.TacticalMap.route) {
            TacticalMapScreen(navController = navController)
        }
        composable(Screen.RuleManager.route) {
            RuleManagerScreen(navController = navController)
        }
    }
}
