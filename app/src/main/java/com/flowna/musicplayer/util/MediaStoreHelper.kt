package com.flowna.musicplayer.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.flowna.musicplayer.data.FlownaSong
import java.io.File
import java.io.FileInputStream

object MediaStoreHelper {

    private const val FLOWNA_FOLDER = "Flowna"
    private const val RELATIVE_PATH = "Music/$FLOWNA_FOLDER"
    private const val MIN_DURATION_MS = 10_000L
    private val BLOCKED_PATH_SEGMENTS = listOf(
        "/notifications/",
        "\\notifications\\",
        "/ringtones/",
        "\\ringtones\\",
        "/alarms/",
        "\\alarms\\",
        "/ui/",
        "\\ui\\"
    )
    private val UNKNOWN_ARTIST_VALUES = setOf("unknown", "<unknown>", "<unknown artist>")

    /**
     * Cihazdaki şarkıları sorgular. onlyDownloaded=true ise sadece Music/Flowna
     * altındaki şarkılar döndürülür.
     */
    fun querySongs(context: Context, onlyDownloaded: Boolean = false): List<FlownaSong> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val storageColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.DATA
        }

        val selection: String
        val selectionArgs: Array<String>

        if (onlyDownloaded && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            selectionArgs = arrayOf(MIN_DURATION_MS.toString(), "$RELATIVE_PATH%")
        } else if (onlyDownloaded) {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val flownaDir = File(musicDir, FLOWNA_FOLDER)
            @Suppress("DEPRECATION")
            val dataColumn = MediaStore.Audio.Media.DATA
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ? AND $dataColumn LIKE ?"
            selectionArgs = arrayOf(MIN_DURATION_MS.toString(), "${flownaDir.absolutePath}%")
        } else {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
            selectionArgs = arrayOf(MIN_DURATION_MS.toString())
        }

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        return querySongsInternal(
            context = context,
            collection = collection,
            storageColumn = storageColumn,
            selection = selection,
            selectionArgs = selectionArgs,
            sortOrder = sortOrder,
            onlyDownloaded = onlyDownloaded
        )
    }

    fun querySongByUri(context: Context, songUri: Uri): FlownaSong? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val storageColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.DATA
        }

        val songId = runCatching { ContentUris.parseId(songUri) }.getOrNull()
            ?: return null

        return querySongsInternal(
            context = context,
            collection = collection,
            storageColumn = storageColumn,
            selection = "${MediaStore.Audio.Media._ID} = ?",
            selectionArgs = arrayOf(songId.toString()),
            sortOrder = null,
            onlyDownloaded = false
        ).firstOrNull()
    }

    /**
     * MP3 dosyasını Music/Flowna dizinine kaydeder
     */
    fun saveToMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        artist: String = "Bilinmeyen Sanatçı"
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$displayName.mp3")
            put(MediaStore.Audio.Media.TITLE, displayName)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, "Flowna İndirilenler")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val flownaDir = File(musicDir, FLOWNA_FOLDER)
                if (!flownaDir.exists()) flownaDir.mkdirs()
                val destFile = File(flownaDir, "$displayName.mp3")
                @Suppress("DEPRECATION")
                put(MediaStore.Audio.Media.DATA, destFile.absolutePath)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collection, values) ?: return null

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, updateValues, null, null)
            }
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            return null
        }

        return uri
    }

    /**
     * Aynı isimli dosyanın zaten var olup olmadığını kontrol eder
     */
    fun isDuplicate(context: Context, displayName: String): Boolean {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val selection: String
        val selectionArgs: Array<String>

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf("$RELATIVE_PATH%", "$displayName.mp3")
        } else {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val flownaDir = File(musicDir, FLOWNA_FOLDER)
            val filePath = File(flownaDir, "$displayName.mp3").absolutePath
            @Suppress("DEPRECATION")
            val dataColumn = MediaStore.Audio.Media.DATA
            selection = "$dataColumn = ?"
            selectionArgs = arrayOf(filePath)
        }

        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            return cursor.count > 0
        }

        return false
    }

    fun renameSong(context: Context, song: FlownaSong, newTitle: String): FlownaSong? {
        val safeTitle = sanitizeDisplayText(newTitle)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, safeTitle)
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeTitle.mp3")
        }

        val updatedRows = context.contentResolver.update(song.uri, values, null, null)
        if (updatedRows <= 0) return null

        return querySongByUri(context, song.uri) ?: song.copy(title = safeTitle)
    }

    fun deleteSong(context: Context, song: FlownaSong): Boolean {
        return context.contentResolver.delete(song.uri, null, null) > 0
    }

    private fun shouldIncludeSong(
        duration: Long,
        storagePath: String,
        onlyDownloaded: Boolean,
        isFlownaDownload: Boolean
    ): Boolean {
        if (duration < MIN_DURATION_MS) return false
        if (onlyDownloaded) return isFlownaDownload

        val normalizedPath = storagePath.lowercase()
        return BLOCKED_PATH_SEGMENTS.none { normalizedPath.contains(it) }
    }

    private fun isFlownaPath(storagePath: String): Boolean {
        val normalizedPath = storagePath.lowercase()
        return normalizedPath.contains("music/flowna") || normalizedPath.contains("music\\flowna")
    }

    private fun readEmbeddedMetadata(context: Context, contentUri: Uri): EmbeddedMetadata {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, contentUri)
            EmbeddedMetadata(
                title = sanitizeDisplayText(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ),
                artist = sanitizeArtist(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ),
                albumArtist = sanitizeArtist(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ),
                album = sanitizeDisplayText(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ),
                artwork = retriever.embeddedPicture
            )
        }.getOrDefault(EmbeddedMetadata()).also {
            runCatching { retriever.release() }
        }
    }

    private fun sanitizeDisplayText(value: String?): String? {
        return TextNormalizer.normalizeHumanText(value)
            ?.takeUnless { it.equals("null", ignoreCase = true) }
    }

    private fun sanitizeArtist(value: String?): String? {
        val cleaned = sanitizeDisplayText(value) ?: return null
        return cleaned.takeUnless { UNKNOWN_ARTIST_VALUES.contains(it.lowercase()) }
    }

    private fun sanitizeAlbum(value: String?): String? {
        val cleaned = sanitizeDisplayText(value) ?: return null
        return if (cleaned.equals("Flowna Download", ignoreCase = true)) {
            "Flowna İndirilenler"
        } else {
            cleaned
        }
    }

    private fun splitTitle(title: String): Pair<String, String>? {
        val separator = " - "
        if (!title.contains(separator)) return null

        val artist = title.substringBefore(separator).trim()
        val songTitle = title.substringAfter(separator).trim()
        if (artist.isBlank() || songTitle.isBlank()) return null
        return artist to songTitle
    }

    private fun querySongsInternal(
        context: Context,
        collection: Uri,
        storageColumn: String,
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String?,
        onlyDownloaded: Boolean
    ): List<FlownaSong> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.IS_MUSIC,
            storageColumn
        )

        val songs = mutableListOf<FlownaSong>()

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val storagePathColumn = cursor.getColumnIndexOrThrow(storageColumn)

            while (cursor.moveToNext()) {
                buildSongFromCursor(
                    context = context,
                    collection = collection,
                    cursorId = cursor.getLong(idColumn),
                    rawTitle = cursor.getString(titleColumn) ?: "Bilinmeyen",
                    rawArtist = cursor.getString(artistColumn),
                    duration = cursor.getLong(durationColumn),
                    albumId = cursor.getLong(albumIdColumn),
                    storagePath = cursor.getString(storagePathColumn).orEmpty(),
                    onlyDownloaded = onlyDownloaded
                )?.let(songs::add)
            }
        }

        return songs
    }

    private fun buildSongFromCursor(
        context: Context,
        collection: Uri,
        cursorId: Long,
        rawTitle: String,
        rawArtist: String?,
        duration: Long,
        albumId: Long,
        storagePath: String,
        onlyDownloaded: Boolean
    ): FlownaSong? {
        val isFlownaDownload = isFlownaPath(storagePath)
        if (!shouldIncludeSong(duration, storagePath, onlyDownloaded, isFlownaDownload)) {
            return null
        }

        val contentUri = ContentUris.withAppendedId(collection, cursorId)
        val albumArtUri = albumId.takeIf { it > 0 }?.let {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        }
        val embeddedMetadata = readEmbeddedMetadata(context, contentUri)

        var resolvedTitle = embeddedMetadata.title ?: sanitizeDisplayText(rawTitle) ?: "Bilinmeyen"
        var resolvedArtist = embeddedMetadata.artist
            ?: sanitizeArtist(rawArtist)
            ?: sanitizeArtist(embeddedMetadata.albumArtist)

        if (resolvedArtist == null) {
            splitTitle(resolvedTitle)?.let { (artistPart, titlePart) ->
                resolvedArtist = artistPart
                resolvedTitle = titlePart
            }
        }

        val resolvedAlbum = sanitizeAlbum(embeddedMetadata.album)
            ?: if (isFlownaDownload) "Flowna İndirilenler" else ""

        return FlownaSong(
            id = cursorId,
            title = resolvedTitle,
            artist = resolvedArtist ?: if (isFlownaDownload) "Flowna" else "Bilinmeyen Sanatçı",
            album = resolvedAlbum,
            duration = duration,
            uri = contentUri,
            albumArtUri = albumArtUri,
            embeddedArtwork = embeddedMetadata.artwork,
            isFlownaDownload = isFlownaDownload
        )
    }

    private data class EmbeddedMetadata(
        val title: String? = null,
        val artist: String? = null,
        val albumArtist: String? = null,
        val album: String? = null,
        val artwork: ByteArray? = null
    )
}
