package com.flowna.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowna.musicplayer.service.DownloadService
import com.flowna.musicplayer.ui.components.FlownaCircleIconButton
import com.flowna.musicplayer.ui.components.FlownaGradientBackground
import com.flowna.musicplayer.ui.components.FlownaPanel
import com.flowna.musicplayer.ui.components.FlownaSectionHeading
import com.flowna.musicplayer.ui.components.FlownaSegmentedTabs
import com.flowna.musicplayer.ui.components.FlownaStatusBadge
import com.flowna.musicplayer.util.DownloadItem
import com.flowna.musicplayer.util.DownloadStatus
import com.flowna.musicplayer.util.DownloadTracker
import kotlinx.coroutines.launch

private enum class DownloadTab(val label: String) {
    ACTIVE("Aktif"),
    COMPLETED("Tamamlandı"),
    FAILED("Başarısız")
}

@Composable
fun DownloadsScreen(
    onOpenSearch: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by DownloadTracker.downloads.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showScreenMenu by remember { mutableStateOf(false) }
    val selectedTab = DownloadTab.entries[selectedTabIndex]

    val filteredDownloads = remember(downloads, selectedTab) {
        downloads.filter { item ->
            when (selectedTab) {
                DownloadTab.ACTIVE -> item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.CONVERTING
                DownloadTab.COMPLETED -> item.status == DownloadStatus.COMPLETED
                DownloadTab.FAILED -> item.status == DownloadStatus.FAILED
            }
        }
    }

    FlownaGradientBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            FlownaSectionHeading(
                title = "İndirmeler",
                subtitle = "${downloads.size} kayıt takip ediliyor",
                trailing = {
                    FlownaCircleIconButton(
                        icon = Icons.Default.Search,
                        contentDescription = "Ara",
                        onClick = onOpenSearch
                    )
                    Box {
                        FlownaCircleIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "Seçenekler",
                            onClick = { showScreenMenu = true }
                        )
                        DropdownMenu(
                            expanded = showScreenMenu,
                            onDismissRequest = { showScreenMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tamamlananları temizle") },
                                onClick = {
                                    showScreenMenu = false
                                    DownloadTracker.clearCompleted()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Başarısızları temizle") },
                                onClick = {
                                    showScreenMenu = false
                                    DownloadTracker.clearFailed()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tüm kayıtları temizle") },
                                onClick = {
                                    showScreenMenu = false
                                    DownloadTracker.clearAll()
                                }
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            FlownaSegmentedTabs(
                items = DownloadTab.entries.map { it.label },
                selectedIndex = selectedTabIndex,
                onSelect = { selectedTabIndex = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredDownloads.isEmpty()) {
                EmptyDownloadsState(
                    modifier = Modifier.weight(1f),
                    tab = selectedTab
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(filteredDownloads, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onRetry = {
                                val result = DownloadService.start(
                                    context = context,
                                    videoUrl = item.videoUrl,
                                    title = item.title,
                                    artist = item.artist,
                                    downloadId = item.id
                                )
                                scope.launch {
                                    result.onFailure {
                                        DownloadTracker.updateStatus(
                                            id = item.id,
                                            status = DownloadStatus.FAILED,
                                            statusMessage = "İndirme yeniden başlatılamadı",
                                            errorMessage = it.message ?: "Bilinmeyen hata"
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadsState(
    modifier: Modifier = Modifier,
    tab: DownloadTab
) {
    val icon = when (tab) {
        DownloadTab.ACTIVE -> Icons.Default.Downloading
        DownloadTab.COMPLETED -> Icons.Default.DownloadDone
        DownloadTab.FAILED -> Icons.Default.ErrorOutline
    }
    val message = when (tab) {
        DownloadTab.ACTIVE -> "Şu anda aktif indirme yok."
        DownloadTab.COMPLETED -> "Tamamlanan indirme henüz yok."
        DownloadTab.FAILED -> "Başarısız indirme kaydı yok."
    }
    val subtitle = when (tab) {
        DownloadTab.ACTIVE -> "Yeni bir indirme başlatmak için arama yapabilirsin."
        DownloadTab.COMPLETED -> "İndirilen parçalar burada listelenecek."
        DownloadTab.FAILED -> "Hata alan kayıtlar burada görünecek."
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        FlownaPanel(
            modifier = Modifier.fillMaxWidth(),
            verticalSpacing = 16.dp
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(68.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItem,
    onRetry: () -> Unit
) {
    FlownaPanel(verticalSpacing = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.artist.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            FlownaStatusBadge(
                label = item.statusMessage ?: item.status.toDisplayText(),
                active = item.status != DownloadStatus.FAILED
            )
        }

        when (item.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.CONVERTING -> {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "%${item.progress.coerceIn(0, 100)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DownloadStatus.FAILED -> {
                item.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                FilledTonalButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tekrar dene")
                }
            }

            DownloadStatus.COMPLETED -> {
                Text(
                    text = "Kütüphaneye eklendi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

private fun DownloadStatus.toDisplayText(): String = when (this) {
    DownloadStatus.DOWNLOADING -> "İndiriliyor"
    DownloadStatus.CONVERTING -> "Hazırlanıyor"
    DownloadStatus.COMPLETED -> "Tamamlandı"
    DownloadStatus.FAILED -> "Başarısız"
}
