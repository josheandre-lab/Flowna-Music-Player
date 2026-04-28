package com.flowna.musicplayer.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.flowna.musicplayer.data.FlownaSong

@Composable
fun SongArtwork(
    song: FlownaSong?,
    modifier: Modifier = Modifier,
    contentDescription: String? = song?.title,
    contentScale: ContentScale = ContentScale.Crop
) {
    val artworkBitmap = remember(song?.id, song?.embeddedArtwork) {
        song?.embeddedArtwork?.let { artwork ->
            runCatching {
                BitmapFactory.decodeByteArray(artwork, 0, artwork.size)
            }.getOrNull()
        }
    }

    when {
        artworkBitmap != null -> {
            Image(
                bitmap = artworkBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier.clip(MaterialTheme.shapes.large),
                contentScale = contentScale
            )
        }

        song?.albumArtUri != null -> {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = contentDescription,
                modifier = modifier.clip(MaterialTheme.shapes.large),
                contentScale = contentScale
            )
        }

        else -> {
            Surface(
                modifier = modifier.clip(MaterialTheme.shapes.large),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
