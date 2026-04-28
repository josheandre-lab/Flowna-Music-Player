package com.flowna.musicplayer

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowna.musicplayer.player.PlayerViewModel
import com.flowna.musicplayer.ui.FlownaNavHost
import com.flowna.musicplayer.ui.components.FlownaSplashScreen
import com.flowna.musicplayer.ui.theme.FlownaTheme
import com.flowna.musicplayer.util.PermissionHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        setContent {
            FlownaTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }

                LaunchedEffect(showSplash) {
                    if (showSplash) {
                        configureSplashSystemBars()
                    } else {
                        configureSystemBars()
                    }
                }

                if (showSplash) {
                    FlownaSplashScreen(onFinished = { showSplash = false })
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var hasPermissions by remember {
                            mutableStateOf(PermissionHelper.hasAllPermissions(this@MainActivity))
                        }

                        if (hasPermissions) {
                            val playerViewModel: PlayerViewModel = viewModel()
                            FlownaNavHost(playerViewModel = playerViewModel)
                        } else {
                            PermissionScreen(
                                missingPermissions = PermissionHelper.getMissingPermissions(this@MainActivity),
                                onPermissionsGranted = { hasPermissions = true }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun configureSplashSystemBars() {
        val surfaceColor = AndroidColor.parseColor("#0D0F1A")
        window.statusBarColor = surfaceColor
        window.navigationBarColor = surfaceColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun configureSystemBars() {
        val surfaceColor = AndroidColor.parseColor("#F7F2FB")
        window.statusBarColor = surfaceColor
        window.navigationBarColor = surfaceColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}

@Composable
private fun PermissionScreen(
    missingPermissions: List<String>,
    onPermissionsGranted: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            onPermissionsGranted()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Flowna Music Player",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Şarkı dinleyebilmek ve indirebilmek için\nbazı izinlere ihtiyacımız var.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    launcher.launch(missingPermissions.toTypedArray())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "İzin Ver",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
