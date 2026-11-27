package com.lifelog.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifelog.app.AppContainer
import com.lifelog.app.ui.screens.AppDetailScreen
import com.lifelog.app.ui.screens.AppsScreen
import com.lifelog.app.ui.screens.DeviceScreen
import com.lifelog.app.ui.screens.DiagnosticsScreen
import com.lifelog.app.ui.screens.ExclusionsScreen
import com.lifelog.app.ui.screens.HomeScreen
import com.lifelog.app.ui.screens.PrivacyScreen
import com.lifelog.app.ui.screens.ReportsScreen
import com.lifelog.app.ui.screens.SettingsScreen
import com.lifelog.app.ui.screens.TimelineScreen
import com.lifelog.app.ui.screens.VaultScreen
import com.lifelog.app.ui.screens.VaultViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("apps", "Apps", Icons.Outlined.Apps),
    Tab("timeline", "Timeline", Icons.Outlined.Timeline),
    Tab("reports", "Reports", Icons.Outlined.BarChart),
    Tab("vault", "Vault", Icons.Outlined.Lock),
)

private val titles = mapOf(
    "privacy" to "Privacy & permissions", "settings" to "Settings", "device" to "Device overview",
    "diagnostics" to "Diagnostics", "exclusions" to "Excluded apps", "app/{pkg}" to "App detail",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifelogRoot(c: AppContainer, main: MainViewModel, state: MainState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val isTab = tabs.any { it.route == route }
    val iconLoader: (String) -> androidx.compose.ui.graphics.ImageBitmap? = { c.appInfo.icon(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isTab) "LIFELOG" else titles[route] ?: "", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (!isTab) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    if (isTab) {
                        IconButton(onClick = { nav.navigate("device") }) { Icon(Icons.Outlined.PhoneAndroid, "Device overview") }
                        IconButton(onClick = { nav.navigate("privacy") }) { Icon(Icons.Outlined.PrivacyTip, "Privacy and permissions") }
                        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val content = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(viewModel(factory = viewModelFactory { initializer { HomeViewModel(c) } }), state, content, iconLoader,
                    onApp = { nav.navigate("app/$it") }, onGrant = { }, onCollect = main::collectNow, onCoverage = { nav.navigate("reports") })
            }
            composable("apps") {
                AppsScreen(viewModel(factory = viewModelFactory { initializer { AppsViewModel(c) } }), content, iconLoader) { nav.navigate("app/$it") }
            }
            composable("app/{pkg}", arguments = listOf(navArgument("pkg") { type = NavType.StringType })) { e ->
                val pkg = e.arguments?.getString("pkg").orEmpty()
                AppDetailScreen(viewModel(key = "app-$pkg", factory = viewModelFactory { initializer { AppDetailViewModel(c, pkg) } }), state, main, content, iconLoader)
            }
            composable("timeline") {
                TimelineScreen(viewModel(factory = viewModelFactory { initializer { TimelineViewModel(c) } }), state, content)
            }
            composable("reports") {
                ReportsScreen(viewModel(factory = viewModelFactory { initializer { ReportsViewModel(c) } }), state, content, iconLoader)
            }
            composable("vault") {
                VaultScreen(viewModel(factory = viewModelFactory { initializer { VaultViewModel(c) } }), content)
            }
            composable("privacy") { PrivacyScreen(main, state, content, onVault = { nav.navigate("vault") }) }
            composable("settings") {
                SettingsScreen(main, state, content, onExclusions = { nav.navigate("exclusions") }, onDiagnostics = { nav.navigate("diagnostics") }, onDevice = { nav.navigate("device") })
            }
            composable("device") { DeviceScreen(main, state, content) }
            composable("diagnostics") { DiagnosticsScreen(main, content) }
            composable("exclusions") { ExclusionsScreen(main, state, content, iconLoader) }
        }
    }
}

