package com.flowna.musicplayer.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.flowna.musicplayer.data.FlownaSong
import com.flowna.musicplayer.data.recommendation.RecommendationItem
import com.flowna.musicplayer.data.repository.LibraryRepository
import com.flowna.musicplayer.data.repository.SearchRepository
import com.flowna.musicplayer.player.PlayerViewModel
import com.flowna.musicplayer.service.DownloadService
import com.flowna.musicplayer.ui.components.FlownaCircleIconButton
import com.flowna.musicplayer.ui.components.FlownaGradientBackground
import com.flowna.musicplayer.ui.components.FlownaPanel
import com.flowna.musicplayer.ui.components.FlownaSectionHeading
import com.flowna.musicplayer.ui.components.FlownaSegmentedTabs
import com.flowna.musicplayer.ui.components.FlownaStatusBadge
import com.flowna.musicplayer.ui.components.SongArtwork
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class LibraryTab(val title: String) {
    ALL("Tüm Şarkılar"),
    DOWNLOADED("İndirilenler")
}

@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    onOpenSearch: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val libraryState by LibraryRepository.state.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(LibraryTab.ALL.ordinal) }
    var showScreenMenu by remember { mutableStateOf(false) }
    var requestedOnlineRecommendations by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LibraryRepository.ensureInitialized(context)
    }

    LaunchedEffect(libraryState.isInitialized, libraryState.songs.size) {
        if (
            libraryState.isInitialized &&
            libraryState.songs.isNotEmpty() &&
            !requestedOnlineRecommendations
        ) {
            requestedOnlineRecommendations = true
            LibraryRepository.refreshRecommendations(context, allowOnline = true)
        }
    }

    val selectedTab = LibraryTab.entries[selectedTabIndex]
    val visibleSongs = remember(libraryState.songs, selectedTabIndex) {
        if (selectedTab == LibraryTab.DOWNLOADED) {
            libraryState.songs.filter { it.isFlownaDownload }
        } else {
            libraryState.songs
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FlownaGradientBackground(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 124.dp)
            ) {
                item {
                    FlownaSectionHeading(
                        title = "Kütüphane",
                        subtitle = when {
                            libraryState.isRefreshing && libraryState.songs.isEmpty() -> "İlk tarama yapılıyor..."
                            libraryState.isRefreshing -> "Önbellek güncelleniyor..."
                            else -> "${visibleSongs.size} şarkı hazır"
                        },
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
                                        text = { Text("Kütüphaneyi yeniden tara") },
                                        onClick = {
                                            showScreenMenu = false
                                            scope.launch {
                                                LibraryRepository.refreshLibrary(context)
                                                LibraryRepository.refreshRecommendations(context, allowOnline = true)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tüm şarkıları göster") },
                                        onClick = {
                                            showScreenMenu = false
                                            selectedTabIndex = LibraryTab.ALL.ordinal
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("İndirilenleri göster") },
                                        onClick = {
                                            showScreenMenu = false
                                            selectedTabIndex = LibraryTab.DOWNLOADED.ordinal
                                        }
                                    )
                                }
                            }
                        }
                    )
                }

                item {
                    LibrarySummaryCard(
                        totalCount = libraryState.songs.size,
                        downloadedCount = libraryState.songs.count { it.isFlownaDownload },
                        isRefreshing = libraryState.isRefreshing,
                        lastScanAt = libraryState.lastScanAt,
                        errorMessage = libraryState.errorMessage
                    )
                }

                if (libraryState.recommendedItems.isNotEmpty()) {
                    item {
                        RecommendationSection(
                            recommendations = libraryState.recommendedItems,
                            onLocalSongClick = { song ->
                                playerViewModel.playSong(song, libraryState.songs)
                            },
                            onOnlinePreviewClick = { candidate ->
                                scope.launch {
                                    playerViewModel.startPreview(candidate)
                                        .onFailure {
                                            snackbarHostState.showSnackbar(
                                                it.message ?: "Önizleme başlatılamadı."
                                            )
                                        }
                                }
                            },
                            onOnlineDownloadClick = { candidate ->
                                val result = DownloadService.start(
                                    context = context,
                                    videoUrl = candidate.videoUrl,
                                    title = candidate.title,
                                    artist = candidate.artist
                                )
                                scope.launch {
                                    result.onSuccess {
                                        snackbarHostState.showSnackbar("Online öneri indirmeye eklendi.")
                                    }.onFailure {
                                        snackbarHostState.showSnackbar(
                                            it.message ?: "İndirme başlatılamadı."
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                if (libraryState.mostPlayedSongs.isNotEmpty()) {
                    item {
                        MostPlayedSection(
                            songs = libraryState.mostPlayedSongs,
                            onSongClick = { song ->
                                playerViewModel.playSong(song, libraryState.songs)
                            }
                        )
                    }
                }

                item {
                    FlownaSegmentedTabs(
                        items = LibraryTab.entries.map { it.title },
                        selectedIndex = selectedTabIndex,
                        onSelect = { selectedTabIndex = it }
                    )
                }

                if (libraryState.isRefreshing && !libraryState.isInitialized && libraryState.songs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp, bottom = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (visibleSongs.isEmpty()) {
                    item {
                        EmptyLibraryState(selectedTab = selectedTab)
                    }
                } else {
                    items(
                        items = visibleSongs,
                        key = { it.uri.toString() }
                    ) { song ->
                        LibrarySongCard(
                            song = song,
                            onClick = { playerViewModel.playSong(song, visibleSongs) },
                            onShare = { shareSong(context, song) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp)
        )
    }
}

@Composable
private fun LibrarySummaryCard(
    totalCount: Int,
    downloadedCount: Int,
    isRefreshing: Boolean,
    lastScanAt: Long?,
    errorMessage: String?
) {
    FlownaPanel(verticalSpacing = 18.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (errorMessage != null) {
                    errorMessage
                } else if (lastScanAt != null) {
                    formatScanLabel(lastScanAt)
                } else {
                    "Önbellek hazır"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(10.dp))
            FlownaStatusBadge(
                label = if (isRefreshing) "Taranıyor" else "Güncel",
                active = !isRefreshing
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniLibraryStatCard(
                modifier = Modifier.weight(1f),
                title = "Tüm Şarkılar",
                value = totalCount.toString()
            )
            MiniLibraryStatCard(
                modifier = Modifier.weight(1f),
                title = "İndirilenler",
                value = downloadedCount.toString()
            )
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MiniLibraryStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecommendationSection(
    recommendations: List<RecommendationItem>,
    onLocalSongClick: (FlownaSong) -> Unit,
    onOnlinePreviewClick: (RecommendationItem.OnlineCandidate) -> Unit,
    onOnlineDownloadClick: (RecommendationItem.OnlineCandidate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = "Senin için önerilenler",
            subtitle = "Dinleme alışkanlıkların ve benzer tarzlara göre seçildi."
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(recommendations, key = { it.stableId }) { item ->
                RecommendationCard(
                    item = item,
                    onLocalSongClick = onLocalSongClick,
                    onOnlinePreviewClick = onOnlinePreviewClick,
                    onOnlineDownloadClick = onOnlineDownloadClick
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    item: RecommendationItem,
    onLocalSongClick: (FlownaSong) -> Unit,
    onOnlinePreviewClick: (RecommendationItem.OnlineCandidate) -> Unit,
    onOnlineDownloadClick: (RecommendationItem.OnlineCandidate) -> Unit
) {
    if (item is RecommendationItem.OnlineCandidate) {
        LaunchedEffect(item.videoUrl) {
            SearchRepository.prefetchPreviews(
                listOf(
                    com.flowna.musicplayer.data.repository.SearchResult(
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                        duration = item.durationSeconds,
                        videoId = item.videoId,
                        videoUrl = item.videoUrl,
                        uploaderName = item.artist
                    )
                )
            )
        }
    }

    FlownaPanel(
        modifier = Modifier.width(182.dp),
        verticalSpacing = 10.dp
    ) {
        when (item) {
            is RecommendationItem.LocalSong -> {
                SongArtwork(
                    song = item.song,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onLocalSongClick(item.song) }
                )
            }

            is RecommendationItem.OnlineCandidate -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onOnlinePreviewClick(item) }
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Önizle",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        when (item) {
            is RecommendationItem.LocalSong -> {
                FlownaStatusBadge(label = item.reason)
            }

            is RecommendationItem.OnlineCandidate -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlownaStatusBadge(label = "Online")
                    IconButton(onClick = { onOnlineDownloadClick(item) }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "İndir",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MostPlayedSection(
    songs: List<FlownaSong>,
    onSongClick: (FlownaSong) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = "En çok dinlenenler",
            subtitle = "Son bitirilen çalmalara göre sıralanır."
        )
        FlownaPanel(verticalSpacing = 8.dp) {
            songs.forEachIndexed { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SongArtwork(
                        song = song,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist.ifBlank { "Bilinmeyen sanatçı" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatSongDuration(song.duration),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(selectedTab: LibraryTab) {
    val message = when (selectedTab) {
        LibraryTab.ALL -> "Cihazda uygun şarkı bulunamadı."
        LibraryTab.DOWNLOADED -> "Flowna klasöründe henüz şarkı yok."
    }

    FlownaPanel(
        modifier = Modifier.fillMaxWidth(),
        verticalSpacing = 14.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LibrarySongCard(
    song: FlownaSong,
    onClick: () -> Unit,
    onShare: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    FlownaPanel(
        modifier = Modifier.clickable(onClick = onClick),
        verticalSpacing = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongArtwork(
                song = song,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        song.artist.takeIf { it.isNotBlank() },
                        song.album.takeIf { it.isNotBlank() }
                    ).joinToString(" | ").ifBlank { "Bilinmeyen sanatçı" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = formatSongDuration(song.duration),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (song.isFlownaDownload) {
                        FlownaStatusBadge(label = "Flowna")
                    }
                }
            }

            Box {
                FlownaCircleIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Seçenekler",
                    onClick = { expanded = true }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Oynat") },
                        onClick = {
                            expanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paylaş") },
                        onClick = {
                            expanded = false
                            onShare()
                        }
                    )
                }
            }
        }
    }
}

private fun formatSongDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%d:%02d".format(mins, secs)
}

private fun formatScanLabel(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale("tr", "TR"))
    val localTime = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    return "Son tarama: ${formatter.format(localTime)}"
}

private fun shareSong(
    context: android.content.Context,
    song: FlownaSong
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, song.uri)
        putExtra(Intent.EXTRA_SUBJECT, song.title)
        putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Şarkıyı paylaş"))
}
