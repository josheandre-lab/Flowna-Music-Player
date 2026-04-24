package com.flowna.musicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.flowna.musicplayer.player.PlayerViewModel

@Composable
fun PreviewPlayerBar(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val previewState by playerViewModel.previewState.collectAsStateWithLifecycle()
    val track = previewState.track

    if (!previewState.isVisible || previewState.errorMessage != null) return

    FlownaPanel(
        modifier = modifier.fillMaxWidth(),
        verticalSpacing = 7.dp
    ) {
        LinearProgressIndicator(
            progress = { previewState.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (track?.artworkUrl?.isNotBlank() == true) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(15.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {}
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = when {
                        previewState.isLoading -> "Önizleme hazırlanıyor"
                        previewState.errorMessage != null -> "Önizleme başlatılamadı"
                        else -> track?.title ?: "Önizleme"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        previewState.isLoading -> "Akış yükleniyor..."
                        previewState.errorMessage != null -> previewState.errorMessage.orEmpty()
                        else -> track?.artist?.ifBlank { "Online öneri" } ?: "Online öneri"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                IconButton(
                    onClick = { playerViewModel.togglePreviewPlayPause() },
                    enabled = !previewState.isLoading && previewState.errorMessage == null && track != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (previewState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = if (previewState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (previewState.isPlaying) "Duraklat" else "Çal",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
            ) {
                IconButton(
                    onClick = { playerViewModel.downloadPreviewTrack() },
                    enabled = !previewState.isLoading && previewState.errorMessage == null && track != null,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "İndir",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
            ) {
                IconButton(
                    onClick = { playerViewModel.stopPreviewAndRestore() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
