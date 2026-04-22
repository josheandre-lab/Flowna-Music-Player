package com.flowna.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.flowna.musicplayer.data.repository.LibraryRepository
import com.flowna.musicplayer.player.PlayerViewModel
import com.flowna.musicplayer.ui.components.BottomPlayerBar
import com.flowna.musicplayer.ui.screens.DownloadsScreen
import com.flowna.musicplayer.ui.screens.LibraryScreen
import com.flowna.musicplayer.ui.screens.PlayerScreen
import com.flowna.musicplayer.ui.screens.SearchScreen
import com.flowna.musicplayer.ui.screens.SettingsScreen
import com.flowna.musicplayer.util.AppUpdateChecker
import com.flowna.musicplayer.util.AppUpdateState

sealed class FlownaScreen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Search : FlownaScreen("search", "Ara", Icons.Default.Search)
    data object Downloads : FlownaScreen("downloads", "İndirme", Icons.Default.Download)
    data object Library : FlownaScreen("library", "Kütüphane", Icons.Default.LibraryMusic)
    data object Settings : FlownaScreen("settings", "Ayarlar", Icons.Default.Settings)
    data object Player : FlownaScreen("player", "Çalıyor", Icons.Default.LibraryMusic)
}

private val navigationItems = listOf(
    FlownaScreen.Search,
    FlownaScreen.Downloads,
    FlownaScreen.Library,
    FlownaScreen.Settings
)

@Composable
fun FlownaNavHost(playerViewModel: PlayerViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val updateInfo by AppUpdateState.info.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LibraryRepository.ensureInitialized(context)
    }

    LaunchedEffect(updateInfo?.checkedAtMillis) {
        if (updateInfo?.updateAvailable == true) {
            showUpdateDialog = true
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentRoute != FlownaScreen.Player.route) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BottomPlayerBar(
                        playerViewModel = playerViewModel,
                        onOpenPlayer = { navController.navigate(FlownaScreen.Player.route) }
                    )
                    FlownaBottomNavigation(
                        currentRoute = currentRoute,
                        onSelect = { screen ->
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = FlownaScreen.Search.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(FlownaScreen.Search.route) {
                SearchScreen()
            }
            composable(FlownaScreen.Downloads.route) {
                DownloadsScreen(
                    onOpenSearch = { navController.navigate(FlownaScreen.Search.route) }
                )
            }
            composable(FlownaScreen.Library.route) {
                LibraryScreen(
                    playerViewModel = playerViewModel,
                    onOpenSearch = { navController.navigate(FlownaScreen.Search.route) }
                )
            }
            composable(FlownaScreen.Settings.route) {
                SettingsScreen()
            }
            composable(FlownaScreen.Player.route) {
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Yeni sürüm hazır") },
            text = { Text(updateInfo?.summary.orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        updateInfo?.let { AppUpdateChecker.openUpdate(context, it) }
                    }
                ) {
                    Text(if (updateInfo?.hasPublishedApk == true) "Güncelle" else "Aç")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Daha sonra")
                }
            }
        )
    }
}

@Composable
private fun FlownaBottomNavigation(
    currentRoute: String?,
    onSelect: (FlownaScreen) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            navigationItems.forEach { screen ->
                val selected = currentRoute == screen.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Transparent)
                                )
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { onSelect(screen) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
