package com.wifimonitor.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wifimonitor.service.MonitorService
import com.wifimonitor.ui.screens.*
import com.wifimonitor.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it } && !isServiceRunning(MonitorService::class.java)) {
            MonitorService.start(this)
        }
    }

    override fun onStart() {
        super.onStart()
        MonitorService.setAppForeground(this, true)
    }

    override fun onStop() {
        super.onStop()
        MonitorService.setAppForeground(this, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        setContent {
            WifiMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepNavy
                ) {
                    MainScreenContent()
                }
            }
        }
    }

    @Composable
    private fun MainScreenContent() {
        val navController = androidx.navigation.compose.rememberNavController()
        var selectedItem by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val items = listOf(
            NavigationItem("Dashboard", Icons.Default.Dashboard, com.wifimonitor.ui.Screen.Dashboard.route),
            NavigationItem("Devices", Icons.Default.Devices, com.wifimonitor.ui.Screen.DeviceList.route),
            NavigationItem("Activity", Icons.Default.Timeline, com.wifimonitor.ui.Screen.Activity.route),
            NavigationItem("Domains", Icons.Default.Language, com.wifimonitor.ui.Screen.Domains.route),
            NavigationItem("Control", Icons.Default.AdminPanelSettings, com.wifimonitor.ui.Screen.Control.route)
        )

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = NavyCard,
                    contentColor = TextSecondary,
                    tonalElevation = 8.dp
                ) {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 10.sp) },
                            selected = selectedItem == index,
                            onClick = {
                                selectedItem = index
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberTeal,
                                selectedTextColor = CyberTeal,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = CyberTeal.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                // We use NavHost inside the box
                AppNavigationWithController(navController)
            }
        }
    }

    @Composable
    private fun AppNavigationWithController(navController: androidx.navigation.NavHostController) {
        androidx.navigation.compose.NavHost(navController = navController, startDestination = com.wifimonitor.ui.Screen.Dashboard.route) {
            composable(com.wifimonitor.ui.Screen.Dashboard.route) { DashboardScreen(navController = navController) }
            composable(com.wifimonitor.ui.Screen.DeviceList.route) { DeviceListScreen(navController = navController) }
            composable(com.wifimonitor.ui.Screen.Activity.route) { ActivityScreen(navController = navController) }
            composable(com.wifimonitor.ui.Screen.Domains.route) { DomainsScreen(navController = navController) }
            composable(com.wifimonitor.ui.Screen.Control.route) { ControlScreen(navController = navController) }
            composable(
                route = com.wifimonitor.ui.Screen.DeviceDetail.route,
                arguments = listOf(androidx.navigation.navArgument("mac") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                DeviceDetailScreen(mac = backStackEntry.arguments?.getString("mac") ?: "", navController = navController)
            }
        }
    }

    data class NavigationItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String)

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceClass.name }
    }
}
