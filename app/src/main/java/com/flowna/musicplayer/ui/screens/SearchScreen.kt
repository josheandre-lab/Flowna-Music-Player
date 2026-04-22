package com.flowna.musicplayer.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.flowna.musicplayer.service.DownloadService
import com.flowna.musicplayer.ui.components.FlownaGradientBackground
import com.flowna.musicplayer.ui.components.FlownaPanel
import com.flowna.musicplayer.ui.components.FlownaSectionHeading
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val resultsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var recentQueries by rememberSaveable {
        mutableStateOf(
            listOf("Zeynep Bastik", "Sezen Aksu", "Sagopa Kajmer", "Mabel Matiz", "Mor ve Otesi")
        )
    }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    val suggestions = remember { mutableStateListOf<String>() }

    fun performSearch(rawQuery: String = query) {
        val trimmedQuery = rawQuery.trim()
        if (trimmedQuery.isBlank()) return

        query = trimmedQuery
        recentQueries = listOf(trimmedQuery) +
            recentQueries.filterNot { it.equals(trimmedQuery, ignoreCase = true) }.take(5)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        isLoading = true
        hasSearched = true
        suggestions.clear()

        scope.launch {
            val result = SearchRepository.search(trimmedQuery)
            result.onSuccess { items ->
                results = items
            }.onFailure {
                results = emptyList()
                snackbarHostState.showSnackbar(
                    "Arama basarisiz oldu. Baglantini kontrol edip tekrar dene."
                )
            }
            isLoading = false
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
            .onSuccess { items ->
                suggestions.clear()
                suggestions.addAll(items)
            }
            .onFailure {
                suggestions.clear()
            }
        isLoadingSuggestions = false
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                    subtitle = "Sarki, sanatci veya album kesfet."
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
                        }
                    },
                    placeholder = {
                        Text(
                            text = "Sarki, sanatci veya album ara...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { performSearch() }
                    ),
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
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SuggestionPanel(
                        suggestions = suggestions,
                        onSuggestionClick = { suggestion -> performSearch(suggestion) }
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
                            onClearRecent = { recentQueries = emptyList() }
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
                                text = "Sonuc bulunamadi",
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
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(results, key = { it.videoUrl }) { result ->
                                SearchResultItem(
                                    result = result,
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
                                                    "Indirme basladi. Durumu Indirmeler ekranindan takip edebilirsin."
                                                )
                                            }.onFailure {
                                                snackbarHostState.showSnackbar(
                                                    "Indirme servisi baslatilamadi: ${it.message ?: "Bilinmeyen hata"}"
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
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
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
                        text = "Tumunu temizle",
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
                        text = "Henuz arama gecmisi yok.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Yukaridan sarki, sanatci veya album yazarak aramaya baslayabilirsin.",
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
    FlownaPanel(
        modifier = modifier.clickable(onClick = onClick),
        verticalSpacing = 0.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onDownload: () -> Unit
) {
    FlownaPanel(verticalSpacing = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = result.title,
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.uploaderName.ifBlank { "Bilinmeyen sanatci" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(result.duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        FilledTonalButton(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Indir"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Indir",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
