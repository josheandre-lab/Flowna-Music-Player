package com.flowna.musicplayer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flowna.musicplayer.data.repository.SearchRepository
import com.flowna.musicplayer.data.repository.SearchResult
import com.flowna.musicplayer.player.PlayerViewModel
import com.flowna.musicplayer.service.DownloadService
import com.flowna.musicplayer.ui.components.FlownaGradientBackground
import com.flowna.musicplayer.ui.components.FlownaPanel
import com.flowna.musicplayer.ui.components.FlownaSectionHeading
import com.flowna.musicplayer.ui.theme.FlownaBorder
import com.flowna.musicplayer.ui.theme.FlownaSuccess
import com.flowna.musicplayer.ui.theme.FlownaSurface
import com.flowna.musicplayer.ui.theme.FlownaTextMuted
import com.flowna.musicplayer.ui.theme.FlownaTextPrimary
import com.flowna.musicplayer.ui.theme.FlownaTextSecondary
import com.flowna.musicplayer.ui.theme.Lavender100
import com.flowna.musicplayer.ui.theme.Lavender600
import com.flowna.musicplayer.util.PreferencesHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(playerViewModel: PlayerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val resultsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var recentQueries by rememberSaveable { mutableStateOf(PreferencesHelper.getRecentSearches()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(false) }
    var currentSearchQuery by rememberSaveable { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    val suggestions = remember { mutableStateListOf<String>() }

    DisposableEffect(Unit) {
        onDispose { SearchRepository.cancelPreviewPrefetch() }
    }

    fun performSearch(rawQuery: String = query) {
        val trimmedQuery = rawQuery.trim()
        if (trimmedQuery.isBlank()) return

        query = trimmedQuery
        currentSearchQuery = trimmedQuery
        recentQueries = (listOf(trimmedQuery) +
            recentQueries.filterNot { it.equals(trimmedQuery, ignoreCase = true) }).take(6)
        PreferencesHelper.setRecentSearches(recentQueries)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        isLoading = true
        isLoadingMore = false
        canLoadMore = false
        hasSearched = true
        suggestions.clear()

        scope.launch {
            SearchRepository.search(trimmedQuery)
                .onSuccess { freshResults ->
                    results = freshResults.distinctBy { it.videoUrl }
                    canLoadMore = freshResults.isNotEmpty()
                    resultsListState.scrollToItem(0)
                }
                .onFailure {
                    results = emptyList()
                    snackbarHostState.showSnackbar(
                        "Arama başarısız oldu. Bağlantını kontrol edip tekrar dene."
                    )
                }
            isLoading = false
        }
    }

    fun loadMoreResults() {
        val loadQuery = currentSearchQuery.takeIf { it.isNotBlank() } ?: return
        if (!hasSearched || isLoading || isLoadingMore || !canLoadMore) return

        isLoadingMore = true
        scope.launch {
            SearchRepository.searchMore(loadQuery)
                .onSuccess { moreResults ->
                    if (moreResults.isEmpty()) {
                        canLoadMore = false
                    } else {
                        results = (results + moreResults).distinctBy { it.videoUrl }
                    }
                }
                .onFailure {
                    canLoadMore = false
                    snackbarHostState.showSnackbar(
                        it.message ?: "Daha fazla sonuç yüklenemedi."
                    )
                }
            isLoadingMore = false
        }
    }

    LaunchedEffect(hasSearched, canLoadMore, isLoadingMore, results.size) {
        if (!hasSearched || !canLoadMore || isLoadingMore || results.isEmpty()) return@LaunchedEffect

        snapshotFlow {
            val layoutInfo = resultsListState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItemsCount) ->
                if (totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 4) {
                    loadMoreResults()
                }
            }
    }

    LaunchedEffect(query, hasSearched) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2 || hasSearched) {
            suggestions.clear()
            isLoadingSuggestions = false
            return@LaunchedEffect
        }

        delay(300)
        isLoadingSuggestions = true
        SearchRepository.suggestions(trimmedQuery)
            .onSuccess {
                suggestions.clear()
                suggestions.addAll(it)
            }
            .onFailure {
                suggestions.clear()
            }
        isLoadingSuggestions = false
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        FlownaGradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                FlownaSectionHeading(
                    title = "Ara",
                    subtitle = "Şarkı, sanatçı veya albüm keşfet."
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        hasSearched = false
                        if (it.isBlank()) {
                            results = emptyList()
                            suggestions.clear()
                            canLoadMore = false
                        }
                    },
                    placeholder = {
                        Text(
                            text = "Şarkı, sanatçı veya albüm ara...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    trailingIcon = {
                        if (isLoadingSuggestions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { performSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Ara",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Lavender600,
                        unfocusedBorderColor = FlownaBorder,
                        focusedContainerColor = FlownaSurface,
                        unfocusedContainerColor = FlownaSurface,
                        cursorColor = Lavender600,
                        focusedTextColor = FlownaTextPrimary,
                        unfocusedTextColor = FlownaTextPrimary,
                        focusedPlaceholderColor = FlownaTextMuted,
                        unfocusedPlaceholderColor = FlownaTextMuted
                    )
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SuggestionPanel(
                        suggestions = suggestions,
                        onSuggestionClick = { performSearch(it) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(54.dp))
                        }
                    }

                    results.isEmpty() && !hasSearched -> {
                        SearchDiscoveryBody(
                            modifier = Modifier.weight(1f),
                            recentQueries = recentQueries,
                            onRecentClick = { performSearch(it) },
                            onClearRecent = {
                                recentQueries = emptyList()
                                PreferencesHelper.clearRecentSearches()
                            }
                        )
                    }

                    results.isEmpty() && hasSearched -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sonuç bulunamadı",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            state = resultsListState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 220.dp)
                        ) {
                            item {
                                Text(
                                    text = "${results.size} sonuç bulundu",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            items(results, key = { it.videoUrl }) { result ->
                                SearchResultItem(
                                    result = result,
                                    onPreview = {
                                        scope.launch {
                                            playerViewModel.startPreview(result)
                                                .onFailure {
                                                    snackbarHostState.showSnackbar(
                                                        it.message ?: "Önizleme başlatılamadı."
                                                    )
                                                }
                                        }
                                    },
                                    onDownload = {
                                        val startResult = DownloadService.start(
                                            context = context,
                                            videoUrl = result.videoUrl,
                                            title = result.title,
                                            artist = result.uploaderName
                                        )
                                        scope.launch {
                                            startResult.onSuccess {
                                                snackbarHostState.showSnackbar(
                                                    "İndirme başladı. Durumu İndirmeler ekranından takip edebilirsin."
                                                )
                                            }.onFailure {
                                                snackbarHostState.showSnackbar(
                                                    "İndirme servisi başlatılamadı: ${it.message ?: "Bilinmeyen hata"}"
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            if (isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDiscoveryBody(
    modifier: Modifier = Modifier,
    recentQueries: List<String>,
    onRecentClick: (String) -> Unit,
    onClearRecent: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 220.dp)
    ) {
        item {
            FlownaPanel(verticalSpacing = 8.dp) {
                Text(
                    text = "Hızlı başlangıç",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Kapak görseline dokunarak şarkıyı indirmeden önce önizlemede dinleyebilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Son aramalar",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (recentQueries.isNotEmpty()) {
                    Text(
                        text = "Tümünü temizle",
                        modifier = Modifier.clickable(onClick = onClearRecent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (recentQueries.isNotEmpty()) {
            items(recentQueries.chunked(2)) { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    chunk.forEach { item ->
                        SearchChip(
                            modifier = Modifier.weight(1f),
                            label = item,
                            onClick = { onRecentClick(item) }
                        )
                    }
                    if (chunk.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            item {
                FlownaPanel(verticalSpacing = 8.dp) {
                    Text(
                        text = "Henüz arama geçmişi yok.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Yukarıdan şarkı, sanatçı veya albüm yazarak aramaya başlayabilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionPanel(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    FlownaPanel(verticalSpacing = 0.dp) {
        suggestions.forEachIndexed { index, suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (index != suggestions.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                )
            }
        }
    }
}

@Composable
private fun SearchChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = FlownaSurface,
        border = BorderStroke(1.dp, FlownaBorder),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = FlownaTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onPreview: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = FlownaSurface,
        border = BorderStroke(1.dp, FlownaBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onPreview)
                ) {
                    AsyncImage(
                        model = result.thumbnailUrl,
                        contentDescription = result.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Lavender600.copy(alpha = 0.94f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Önizle",
                            tint = FlownaSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = FlownaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = result.uploaderName.ifBlank { "Bilinmeyen sanatçı" },
                        style = MaterialTheme.typography.labelSmall,
                        color = FlownaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = formatDuration(result.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = FlownaTextMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Lavender100,
                        contentColor = Lavender600
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Önizle",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Önizle",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalButton(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = FlownaSuccess.copy(alpha = 0.12f),
                        contentColor = FlownaSuccess
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "İndir",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "İndir",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
