package com.flowna.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
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
import com.flowna.musicplayer.ui.components.PreviewPlayerBar
import com.flowna.musicplayer.ui.screens.DownloadsScreen
import com.flowna.musicplayer.ui.screens.LibraryScreen
import com.flowna.musicplayer.ui.screens.PlayerScreen
import com.flowna.musicplayer.ui.screens.SearchScreen
import com.flowna.musicplayer.ui.screens.SettingsScreen
import com.flowna.musicplayer.ui.theme.FlownaBorder
import com.flowna.musicplayer.ui.theme.FlownaSurface
import com.flowna.musicplayer.ui.theme.FlownaTextMuted
import com.flowna.musicplayer.ui.theme.FlownaTextPrimary
import com.flowna.musicplayer.ui.theme.Lavender100
import com.flowna.musicplayer.ui.theme.Lavender600
import com.flowna.musicplayer.util.AppUpdateChecker
import com.flowna.musicplayer.util.AppUpdateState
import kotlinx.coroutines.delay

sealed class FlownaScreen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Search : FlownaScreen("search", "Ara", Icons.Default.Search)
    data object Downloads : FlownaScreen("downloads", "İndirmeler", Icons.Default.Download)
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
    val previewState by playerViewModel.previewState.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LibraryRepository.ensureInitialized(context)
    }

    LaunchedEffect(Unit) {
        delay(1_500)
        AppUpdateChecker.check(force = false)
    }

    LaunchedEffect(updateInfo?.checkedAtMillis) {
        if (updateInfo?.updateAvailable == true) {
            showUpdateDialog = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != FlownaScreen.Player.route) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 0.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (previewState.isVisible) {
                        PreviewPlayerBar(
                            playerViewModel = playerViewModel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        BottomPlayerBar(
                            playerViewModel = playerViewModel,
                            onOpenPlayer = { navController.navigate(FlownaScreen.Player.route) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

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
                SearchScreen(playerViewModel = playerViewModel)
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
    Column {
        HorizontalDivider(color = FlownaBorder, thickness = 1.dp)
        Surface(
            color = FlownaSurface,
            shadowElevation = 0.dp
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            navigationItems.forEach { screen ->
                val selected = currentRoute == screen.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(listOf(Lavender100, Lavender100))
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelect(screen) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        modifier = Modifier.size(22.dp),
                        tint = if (selected) {
                            Lavender600
                        } else {
                            FlownaTextMuted
                        }
                    )
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            FlownaTextPrimary
                        } else {
                            FlownaTextMuted
                        }
                    )
                }
            }
        }
        }
    }
}
