package com.flowna.musicplayer.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.flowna.musicplayer.ui.theme.FlownaSurface
import com.flowna.musicplayer.ui.theme.FlownaTextMuted
import com.flowna.musicplayer.ui.theme.FlownaTextPrimary
import com.flowna.musicplayer.ui.theme.FlownaTextSecondary
import com.flowna.musicplayer.ui.theme.Lavender100
import com.flowna.musicplayer.ui.theme.Lavender600
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private data class PendingRenameRequest(
    val song: FlownaSong,
    val newTitle: String
)

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
    var pendingRenameRequest by remember { mutableStateOf<PendingRenameRequest?>(null) }
    var pendingDeleteSong by remember { mutableStateOf<FlownaSong?>(null) }

    fun needsMediaConsent(error: Throwable): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (error is SecurityException || error.cause is SecurityException)
    }

    fun showOperationError(error: Throwable, fallback: String) {
        scope.launch {
            snackbarHostState.showSnackbar(error.message ?: fallback)
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val request = pendingRenameRequest
        pendingRenameRequest = null
        if (result.resultCode == Activity.RESULT_OK && request != null) {
            scope.launch {
                LibraryRepository.renameDownloadedSong(context, request.song, request.newTitle)
                    .onSuccess {
                        snackbarHostState.showSnackbar("Şarkı adı güncellendi.")
                    }
                    .onFailure {
                        snackbarHostState.showSnackbar(it.message ?: "Ad değiştirilemedi.")
                    }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Ad değiştirme izni verilmedi.") }
        }
    }

    val deletePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val song = pendingDeleteSong
        pendingDeleteSong = null
        if (result.resultCode == Activity.RESULT_OK && song != null) {
            scope.launch {
                LibraryRepository.deleteDownloadedSong(context, song)
                    .recoverCatching {
                        LibraryRepository.forgetCachedSong(context, song).getOrThrow()
                    }
                    .onSuccess {
                        snackbarHostState.showSnackbar("Şarkı silindi.")
                    }
                    .onFailure {
                        snackbarHostState.showSnackbar(it.message ?: "Şarkı silinemedi.")
                    }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Silme izni verilmedi.") }
        }
    }

    fun launchWriteConsent(song: FlownaSong, newTitle: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            pendingRenameRequest = PendingRenameRequest(song, newTitle)
            val request = MediaStore.createWriteRequest(
                context.contentResolver,
                listOf(song.uri)
            )
            writePermissionLauncher.launch(
                IntentSenderRequest.Builder(request.intentSender).build()
            )
        }.onFailure {
            pendingRenameRequest = null
            showOperationError(it, "Ad değiştirme izni başlatılamadı.")
        }
    }

    fun launchDeleteConsent(song: FlownaSong) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            pendingDeleteSong = song
            val request = MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(song.uri)
            )
            deletePermissionLauncher.launch(
                IntentSenderRequest.Builder(request.intentSender).build()
            )
        }.onFailure {
            pendingDeleteSong = null
            showOperationError(it, "Silme izni başlatılamadı.")
        }
    }

    fun renameDownloadedSong(song: FlownaSong, newTitle: String, allowConsent: Boolean) {
        scope.launch {
            LibraryRepository.renameDownloadedSong(context, song, newTitle)
                .onSuccess {
                    snackbarHostState.showSnackbar("Şarkı adı güncellendi.")
                }
                .onFailure {
                    if (allowConsent && needsMediaConsent(it)) {
                        launchWriteConsent(song, newTitle)
                    } else {
                        snackbarHostState.showSnackbar(it.message ?: "Ad değiştirilemedi.")
                    }
                }
        }
    }

    fun deleteDownloadedSong(song: FlownaSong, allowConsent: Boolean) {
        scope.launch {
            LibraryRepository.deleteDownloadedSong(context, song)
                .onSuccess {
                    snackbarHostState.showSnackbar("Şarkı silindi.")
                }
                .onFailure {
                    if (allowConsent && needsMediaConsent(it)) {
                        launchDeleteConsent(song)
                    } else {
                        snackbarHostState.showSnackbar(it.message ?: "Şarkı silinemedi.")
                    }
                }
        }
    }

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
                                onClick = onOpenSearch,
                                size = 42.dp,
                                iconSize = 22.dp
                            )
                            Box {
                                FlownaCircleIconButton(
                                    icon = Icons.Default.MoreVert,
                                    contentDescription = "Seçenekler",
                                    onClick = { showScreenMenu = true },
                                    size = 42.dp,
                                    iconSize = 22.dp
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
                                                    .onSuccess { count ->
                                                        snackbarHostState.showSnackbar("$count şarkı yeniden tarandı.")
                                                    }
                                                    .onFailure {
                                                        snackbarHostState.showSnackbar("Kütüphane taraması başarısız oldu.")
                                                    }
                                                LibraryRepository.refreshRecommendations(context, allowOnline = true)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tüm şarkıları göster") },
                                        onClick = {
                                            showScreenMenu = false
                                            selectedTabIndex = LibraryTab.ALL.ordinal
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Tüm şarkılar gösteriliyor.")
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("İndirilenleri göster") },
                                        onClick = {
                                            showScreenMenu = false
                                            selectedTabIndex = LibraryTab.DOWNLOADED.ordinal
                                            scope.launch {
                                                snackbarHostState.showSnackbar("İndirilenler gösteriliyor.")
                                            }
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
                } else if (libraryState.songs.isNotEmpty()) {
                    item {
                        RecommendationEmptyState()
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
                            onShare = { shareSong(context, song) },
                            onRename = { newTitle ->
                                renameDownloadedSong(song, newTitle, allowConsent = true)
                            },
                            onDelete = {
                                deleteDownloadedSong(song, allowConsent = true)
                            }
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
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Lavender600,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                style = MaterialTheme.typography.bodyMedium,
                color = FlownaSurface.copy(alpha = 0.82f),
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
                    .background(FlownaSurface.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = FlownaSurface
                )
            }
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
        shape = RoundedCornerShape(14.dp),
        color = FlownaSurface.copy(alpha = 0.16f),
        shadowElevation = 0.dp
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
                color = FlownaSurface.copy(alpha = 0.72f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = FlownaSurface
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
            color = FlownaTextPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = FlownaTextMuted
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
    LaunchedEffect(recommendations) {
        SearchRepository.prefetchPreviews(
            recommendations
                .filterIsInstance<RecommendationItem.OnlineCandidate>()
                .take(8)
                .map { item ->
                    com.flowna.musicplayer.data.repository.SearchResult(
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                        duration = item.durationSeconds,
                        videoId = item.videoId,
                        videoUrl = item.videoUrl,
                        uploaderName = item.artist
                    )
                }
        )
    }

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
    FlownaPanel(
        modifier = Modifier.width(168.dp),
        verticalSpacing = 10.dp
    ) {
        when (item) {
            is RecommendationItem.LocalSong -> {
                SongArtwork(
                    song = item.song,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onLocalSongClick(item.song) }
                )
            }

            is RecommendationItem.OnlineCandidate -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
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
private fun RecommendationEmptyState() {
    FlownaPanel(verticalSpacing = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Öneriler hazırlanıyor",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Birkaç şarkı dinledikçe bu alan daha isabetli önerilerle dolacak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    onShare: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingTitle by remember(song.uri) { mutableStateOf(song.title) }
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
                    onClick = { expanded = true },
                    size = 36.dp,
                    iconSize = 20.dp
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
                    if (song.isFlownaDownload) {
                        DropdownMenuItem(
                            text = { Text("Ad değiştir") },
                            onClick = {
                                expanded = false
                                pendingTitle = song.title
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sil") },
                            onClick = {
                                expanded = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Şarkı adını değiştir") },
            text = {
                OutlinedTextField(
                    value = pendingTitle,
                    onValueChange = { pendingTitle = it },
                    singleLine = true,
                    label = { Text("Yeni ad") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanedTitle = pendingTitle.trim()
                        if (cleanedTitle.isNotBlank()) {
                            onRename(cleanedTitle)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Şarkı silinsin mi?") },
            text = { Text("${song.title} cihazdan ve Flowna kütüphanesinden silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
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

