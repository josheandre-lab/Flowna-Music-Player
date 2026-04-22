package com.flowna.musicplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowna.musicplayer.player.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomPlayerBar(
    playerViewModel: PlayerViewModel,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by playerViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by playerViewModel.progress.collectAsStateWithLifecycle()

    currentSong?.let { song ->
        FlownaPanel(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPlayer),
            verticalSpacing = 10.dp
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtwork(
                    song = song,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = listOfNotNull(
                            song.artist.takeIf { it.isNotBlank() },
                            song.album.takeIf { it.isNotBlank() }
                        ).joinToString(" | ").ifBlank { "Bilinmeyen Sanatçı" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Çal",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Box {
                    IconButton(onClick = { playerViewModel.next() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Sonraki",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (song.isFlownaDownload) {
                FlownaStatusBadge(label = "Flowna İndirilenler")
            }
        }
    }
}
