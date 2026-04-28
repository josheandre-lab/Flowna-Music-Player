package com.flowna.musicplayer.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowna.musicplayer.data.FlownaSong
import com.flowna.musicplayer.player.PlayerViewModel
import com.flowna.musicplayer.ui.components.FlownaCircleIconButton
import com.flowna.musicplayer.ui.components.FlownaPanel
import com.flowna.musicplayer.ui.components.PremiumAudioSpectrum
import com.flowna.musicplayer.ui.components.SongArtwork
import com.flowna.musicplayer.ui.theme.FlownaSurface
import com.flowna.musicplayer.ui.theme.FlownaTextMuted
import com.flowna.musicplayer.ui.theme.FlownaTextPrimary
import com.flowna.musicplayer.ui.theme.FlownaTextSecondary
import com.flowna.musicplayer.ui.theme.Lavender100
import com.flowna.musicplayer.ui.theme.Lavender600
import com.flowna.musicplayer.util.ArtworkPalette
import com.flowna.musicplayer.util.ArtworkPaletteResolver
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentSong by playerViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by playerViewModel.progress.collectAsStateWithLifecycle()
    val currentPositionMs by playerViewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by playerViewModel.durationMs.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val maxPullPx = with(density) { 220.dp.toPx() }
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    var showPlayerMenu by remember { mutableStateOf(false) }
    var artworkOffsetTarget by remember { mutableFloatStateOf(0f) }
    var isFavorite by remember(currentSong?.uri?.toString()) {
        mutableStateOf(false)
    }
    val palette by produceState(
        initialValue = ArtworkPalette.Default,
        key1 = currentSong?.uri?.toString()
    ) {
        value = ArtworkPaletteResolver.resolve(context, currentSong)
    }

    DisposableEffect(Unit) {
        playerViewModel.setPlaybackUiVisible(true)
        onDispose { playerViewModel.setPlaybackUiVisible(false) }
    }

    DisposableEffect(currentSong?.uri?.toString()) {
        isFavorite = currentSong?.uri?.toString()?.let { uri ->
            com.flowna.musicplayer.util.PreferencesHelper.isFavoriteSong(uri)
        } == true
        onDispose { }
    }

    fun queueCurrentSong() {
        if (currentSong == null) {
            Toast.makeText(context, "Önce bir şarkı seç.", Toast.LENGTH_SHORT).show()
            return
        }
        playerViewModel.addCurrentSongToQueue()
        Toast.makeText(context, "Şarkı sıraya eklendi.", Toast.LENGTH_SHORT).show()
    }

    val animatedArtworkOffset by animateFloatAsState(
        targetValue = artworkOffsetTarget,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "artworkOffset"
    )
    val animatedArtworkScale by animateFloatAsState(
        targetValue = (1f - (artworkOffsetTarget / (maxPullPx * 5f))).coerceIn(0.93f, 1f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 350f),
        label = "artworkScale"
    )
    val artworkDragState = rememberDraggableState { delta ->
        if (delta > 0f || artworkOffsetTarget > 0f) {
            artworkOffsetTarget = (artworkOffsetTarget + delta).coerceIn(0f, maxPullPx)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.backgroundStart,
                        palette.backgroundEnd,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.glow,
                            Color.Transparent
                        ),
                        radius = with(density) { 280.dp.toPx() }
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlownaCircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    onClick = onBack
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Şimdi Çalıyor",
                        style = MaterialTheme.typography.labelLarge,
                        color = FlownaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PremiumAudioSpectrum(
                        isActive = isPlaying,
                        accent = Lavender600,
                        glow = Lavender600.copy(alpha = 0.22f),
                        modifier = Modifier.width(130.dp)
                    )
                }
                Box {
                    FlownaCircleIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "Seçenekler",
                        onClick = { showPlayerMenu = true }
                    )
                    DropdownMenu(
                        expanded = showPlayerMenu,
                        onDismissRequest = { showPlayerMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Şarkıyı paylaş") },
                            onClick = {
                                showPlayerMenu = false
                                shareSong(context = context, song = currentSong)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sıraya ekle") },
                            onClick = {
                                showPlayerMenu = false
                                queueCurrentSong()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .offset { IntOffset(0, animatedArtworkOffset.roundToInt()) }
                    .graphicsLayer {
                        scaleX = animatedArtworkScale
                        scaleY = animatedArtworkScale
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = artworkDragState,
                        onDragStopped = { velocity ->
                            if (artworkOffsetTarget > dismissThresholdPx || velocity > 2200f) {
                                artworkOffsetTarget = 0f
                                onBack()
                            } else {
                                artworkOffsetTarget = 0f
                            }
                        }
                    ),
                shape = RoundedCornerShape(24.dp),
                color = FlownaSurface.copy(alpha = 0.70f),
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.25f)),
                shadowElevation = 14.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    palette.glow.copy(alpha = 0.42f),
                                    Color.Transparent,
                                    Color.Transparent
                                )
                            )
                        )
                ) {
                    SongArtwork(
                        song = currentSong,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "Şarkı seçilmedi",
                        style = MaterialTheme.typography.titleLarge,
                        color = FlownaTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(
                            currentSong?.artist?.takeIf { it.isNotBlank() },
                            currentSong?.album?.takeIf { it.isNotBlank() }
                        ).joinToString(" • ").ifBlank { "Bilinmeyen sanatçı" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = FlownaTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlownaCircleIconButton(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Favoriden kaldır" else "Favorilere ekle",
                    onClick = {
                        currentSong?.let { song ->
                            isFavorite = playerViewModel.toggleFavorite(song)
                        }
                    },
                    accent = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Slider(
                value = progress,
                onValueChange = { playerViewModel.seekTo(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Lavender600,
                    activeTrackColor = Lavender600,
                    inactiveTrackColor = Lavender100
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatPlaybackTime(currentPositionMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlownaTextMuted
                )
                Text(
                    text = formatPlaybackTime(durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlownaTextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerShortcut(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Sıraya Ekle",
                    onClick = { queueCurrentSong() }
                )
                PlayerShortcut(
                    icon = Icons.Default.Share,
                    label = "Paylaş",
                    onClick = { shareSong(context = context, song = currentSong) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerTransportButton(
                    icon = Icons.Default.SkipPrevious,
                    description = "Önceki",
                    onClick = { playerViewModel.previous() }
                )
                PlayerTransportButton(
                    icon = Icons.Default.FastRewind,
                    description = "10 saniye geri",
                    onClick = { playerViewModel.seekBackward() }
                )
                Surface(
                    modifier = Modifier.size(62.dp),
                    shape = CircleShape,
                    color = Lavender600,
                    shadowElevation = 10.dp
                ) {
                    IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Çal",
                            modifier = Modifier.size(30.dp),
                            tint = FlownaSurface
                        )
                    }
                }
                PlayerTransportButton(
                    icon = Icons.Default.FastForward,
                    description = "10 saniye ileri",
                    onClick = { playerViewModel.seekForward() }
                )
                PlayerTransportButton(
                    icon = Icons.Default.SkipNext,
                    description = "Sonraki",
                    onClick = { playerViewModel.next() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlownaPanel(verticalSpacing = 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerInfoBlock(
                        modifier = Modifier.weight(1f),
                        title = "Oynatma cihazı",
                        value = "Bu cihaz"
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    PlayerInfoBlock(
                        modifier = Modifier.weight(1f),
                        title = "Oynatma modu",
                        value = "Sırayla çal",
                        trailingIcon = Icons.Default.Repeat
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Lavender100
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = FlownaTextSecondary
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = FlownaTextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlayerTransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = FlownaSurface.copy(alpha = 0.94f)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = FlownaTextPrimary
            )
        }
    }
}

@Composable
private fun PlayerInfoBlock(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        trailingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun shareSong(
    context: android.content.Context,
    song: FlownaSong?
) {
    val targetSong = song ?: return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, targetSong.uri)
        putExtra(Intent.EXTRA_SUBJECT, targetSong.title)
        putExtra(Intent.EXTRA_TEXT, "${targetSong.title} - ${targetSong.artist}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Şarkıyı paylaş"))
}

private fun formatPlaybackTime(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
