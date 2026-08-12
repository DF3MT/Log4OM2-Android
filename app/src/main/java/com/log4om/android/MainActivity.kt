package com.log4om.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.log4om.android.data.model.Qso
import com.log4om.android.ui.screens.LogListScreen
import com.log4om.android.ui.screens.NewQsoScreen
import com.log4om.android.ui.screens.SettingsScreen
import com.log4om.android.ui.theme.Log4OMTheme
import com.log4om.android.ui.viewmodel.*

sealed class Screen(
    val route: String,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Log      : Screen("log",      R.string.nav_log,      Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks)
    object NewQso   : Screen("new_qso",  R.string.nav_new_qso,  Icons.Filled.Add,            Icons.Filled.Add)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings,       Icons.Outlined.Settings)
}

private val NAV_ITEMS = listOf(Screen.Log, Screen.NewQso, Screen.Settings)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as Log4OMApp
        val factory = AppViewModelFactory(app)
        setContent {
            Log4OMTheme {
                Log4OMNavHost(factory)
            }
        }
    }
}

@Composable
private fun Log4OMNavHost(factory: AppViewModelFactory) {
    val navController = rememberNavController()

    val logViewModel:      LogViewModel      = viewModel(factory = factory)
    val newQsoViewModel:   NewQsoViewModel   = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    var editQso by remember { mutableStateOf<Qso?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NAV_ITEMS.forEach { screen ->
                    val selected = currentRoute == screen.route
                    val label = stringResource(screen.labelRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (screen == Screen.NewQso) {
                                editQso = null
                                newQsoViewModel.resetForm()
                            }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = label
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Log.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Log.route) {
                LogListScreen(
                    viewModel = logViewModel,
                    onQsoClick = { qso ->
                        editQso = qso
                        navController.navigate(Screen.NewQso.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.NewQso.route) {
                NewQsoScreen(
                    viewModel    = newQsoViewModel,
                    editQso      = editQso,
                    onSaved      = {
                        logViewModel.refresh()
                        navController.navigate(Screen.Log.route) {
                            popUpTo(Screen.Log.route) { inclusive = true }
                        }
                    },
                    onNavigateUp = {
                        editQso = null
                        logViewModel.refresh()
                        navController.navigateUp()
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
