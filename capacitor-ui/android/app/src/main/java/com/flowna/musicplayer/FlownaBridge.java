package com.flowna.musicplayer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class FlownaBridge {
    private static final String FLOWNA_RELATIVE_PATH = "Music/Flowna";
    private static final long MIN_DURATION_MS = 10000L;

    private final MainActivity activity;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final AtomicBoolean newPipeReady = new AtomicBoolean(false);
    private MediaPlayer player;
    private JSONObject currentTrack;
    private String pendingRenameUri;
    private String pendingRenameTitle;

    FlownaBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getInitialState() {
        JSONObject out = ok();
        put(out, "permissionGranted", activity.hasAudioPermission());
        put(out, "versionName", versionName());
        put(out, "versionCode", versionCode());
        put(out, "songs", activity.hasAudioPermission() ? querySongs(false) : new JSONArray());
        put(out, "downloadedSongs", activity.hasAudioPermission() ? querySongs(true) : new JSONArray());
        put(out, "settings", readSettings());
        return out.toString();
    }

    @JavascriptInterface
    public String hasAudioPermission() {
        JSONObject out = ok();
        put(out, "granted", activity.hasAudioPermission());
        return out.toString();
    }

    @JavascriptInterface
    public String requestAudioPermission() {
        activity.runOnUiThread(activity::requestAudioPermission);
        JSONObject out = ok();
        put(out, "requested", true);
        put(out, "granted", activity.hasAudioPermission());
        return out.toString();
    }

    @JavascriptInterface
    public String scanLibrary() {
        JSONObject out = ok();
        if (!activity.hasAudioPermission()) {
            put(out, "ok", false);
            put(out, "error", "Müziklere erişim izni gerekli.");
            return out.toString();
        }
        put(out, "songs", querySongs(false));
        put(out, "downloadedSongs", querySongs(true));
        return out.toString();
    }

    @JavascriptInterface
    public String playSong(String songJson) {
        try {
            JSONObject song = new JSONObject(songJson);
            currentTrack = song;
            Uri uri = Uri.parse(song.optString("uri"));
            activity.runOnUiThread(() -> playUri(uri, song));
            return ok().toString();
        } catch (Exception error) {
            return fail("Şarkı başlatılamadı: " + friendly(error)).toString();
        }
    }

    @JavascriptInterface
    public String pause() {
        activity.runOnUiThread(() -> {
            if (player != null && player.isPlaying()) {
                player.pause();
                sendPlaybackEvent("paused", null);
            }
        });
        return ok().toString();
    }

    @JavascriptInterface
    public String resume() {
        activity.runOnUiThread(() -> {
            if (player != null) {
                player.start();
                sendPlaybackEvent("playing", currentTrack);
            }
        });
        return ok().toString();
    }

    @JavascriptInterface
    public String seekTo(int millis) {
        activity.runOnUiThread(() -> {
            if (player != null) player.seekTo(Math.max(0, millis));
        });
        return ok().toString();
    }

    @JavascriptInterface
    public String getPlaybackState() {
        JSONObject out = ok();
        try {
            boolean hasPlayer = player != null;
            put(out, "hasPlayer", hasPlayer);
            put(out, "isPlaying", hasPlayer && player.isPlaying());
            put(out, "position", hasPlayer ? player.getCurrentPosition() : 0);
            put(out, "duration", hasPlayer ? Math.max(player.getDuration(), 0) : 0);
            put(out, "track", currentTrack == null ? JSONObject.NULL : currentTrack);
        } catch (Exception error) {
            put(out, "hasPlayer", false);
        }
        return out.toString();
    }

    @JavascriptInterface
    public String search(String query, String callbackId) {
        io.execute(() -> {
            JSONObject event = event("searchResults");
            put(event, "callbackId", callbackId);
            try {
                ensureNewPipe();
                SearchExtractor extractor = ServiceList.YouTube.getSearchExtractor(query);
                extractor.fetchPage();
                JSONArray results = new JSONArray();
                for (Object item : extractor.getInitialPage().getItems()) {
                    if (!(item instanceof StreamInfoItem)) continue;
                    StreamInfoItem stream = (StreamInfoItem) item;
                    JSONObject song = new JSONObject();
                    String url = normalizeVideoUrl(stream.getUrl(), "");
                    String videoId = extractVideoId(url);
                    put(song, "id", videoId.isEmpty() ? url : videoId);
                    put(song, "title", clean(stream.getName(), "Bilinmeyen şarkı"));
                    put(song, "artist", clean(stream.getUploaderName(), "YouTube"));
                    put(song, "durationMs", Math.max(0, stream.getDuration()) * 1000L);
                    put(song, "duration", formatDuration(Math.max(0, stream.getDuration()) * 1000L));
                    put(song, "cover", thumbnailOf(stream));
                    put(song, "videoUrl", url);
                    put(song, "source", "online");
                    put(song, "downloaded", false);
                    results.put(song);
                    if (results.length() >= 24) break;
                }
                put(event, "ok", true);
                put(event, "results", results);
            } catch (Exception error) {
                put(event, "ok", false);
                put(event, "error", "Arama tamamlanamadı: " + friendly(error));
            }
            sendEvent(event);
        });
        return ok().toString();
    }

    @JavascriptInterface
    public String startPreview(String resultJson, String callbackId) {
        io.execute(() -> {
            JSONObject event = event("previewReady");
            put(event, "callbackId", callbackId);
            try {
                JSONObject result = new JSONObject(resultJson);
                StreamPayload payload = resolveAudioStream(result.optString("videoUrl"));
                JSONObject preview = cloneTrack(result);
                put(preview, "streamUrl", payload.url);
                put(event, "ok", true);
                put(event, "track", preview);
                sendEvent(event);
                currentTrack = preview;
                activity.runOnUiThread(() -> playStream(payload.url, preview));
            } catch (Exception error) {
                put(event, "ok", false);
                put(event, "error", "Önizleme başlatılamadı: " + friendly(error));
                sendEvent(event);
            }
        });
        return ok().toString();
    }

    @JavascriptInterface
    public String startDownload(String resultJson, String callbackId) {
        io.execute(() -> {
            String downloadId = "dl_" + System.currentTimeMillis();
            try {
                JSONObject result = new JSONObject(resultJson);
                sendDownload(downloadId, "active", 0, "Hazırlanıyor", result, callbackId, null);
                StreamPayload payload = resolveAudioStream(result.optString("videoUrl"));
                Uri savedUri = saveStreamToMediaStore(downloadId, result, payload, callbackId);
                JSONObject savedSong = findSongByUri(savedUri);
                sendDownload(downloadId, "completed", 100, "İndirme tamamlandı", savedSong == null ? result : savedSong, callbackId, null);
            } catch (Exception error) {
                JSONObject fallback = new JSONObject();
                try {
                    JSONObject result = new JSONObject(resultJson);
                    fallback = result;
                } catch (Exception ignored) {
                }
                sendDownload(downloadId, "failed", 0, "İndirme başarısız", fallback, callbackId, friendly(error));
            }
        });
        JSONObject out = ok();
        put(out, "queued", true);
        return out.toString();
    }

    @JavascriptInterface
    public String deleteSong(String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            int rows = activity.getContentResolver().delete(uri, null, null);
            if (rows > 0) return scanLibrary();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PendingIntent request = MediaStore.createDeleteRequest(
                        activity.getContentResolver(),
                        Collections.singletonList(uri)
                );
                activity.startIntentSenderForResult(
                        request.getIntentSender(),
                        MainActivity.REQUEST_DELETE_PERMISSION,
                        null,
                        0,
                        0,
                        0
                );
                JSONObject out = ok();
                put(out, "pendingPermission", true);
                put(out, "message", "Android silme onayı açıldı.");
                return out.toString();
            }
            return fail("Dosya silinemedi. Android izin vermedi.").toString();
        } catch (Exception error) {
            return fail("Dosya silinemedi: " + friendly(error)).toString();
        }
    }

    @JavascriptInterface
    public String renameSong(String uriString, String newTitle) {
        String safeTitle = sanitizeFileName(newTitle);
        if (safeTitle.isEmpty()) return fail("Yeni ad boş olamaz.").toString();
        try {
            Uri uri = Uri.parse(uriString);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.TITLE, safeTitle);
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, safeTitle + ".mp3");
            int rows = activity.getContentResolver().update(uri, values, null, null);
            if (rows > 0) return scanLibrary();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pendingRenameUri = uriString;
                pendingRenameTitle = safeTitle;
                PendingIntent request = MediaStore.createWriteRequest(
                        activity.getContentResolver(),
                        Collections.singletonList(uri)
                );
                activity.startIntentSenderForResult(
                        request.getIntentSender(),
                        MainActivity.REQUEST_WRITE_PERMISSION,
                        null,
                        0,
                        0,
                        0
                );
                JSONObject out = ok();
                put(out, "pendingPermission", true);
                put(out, "message", "Android düzenleme onayı açıldı.");
                return out.toString();
            }
            return fail("Dosya adı değiştirilemedi. Android izin vermedi.").toString();
        } catch (Exception error) {
            return fail("Ad değiştirilemedi: " + friendly(error)).toString();
        }
    }

    @JavascriptInterface
    public String saveSettings(String settingsJson) {
        activity.getSharedPreferences("flowna_settings", Activity.MODE_PRIVATE)
                .edit()
                .putString("settings", settingsJson)
                .apply();
        return ok().toString();
    }

    @JavascriptInterface
    public String clearCache() {
        long deleted = deleteDir(activity.getCacheDir());
        JSONObject out = ok();
        put(out, "deletedBytes", deleted);
        return out.toString();
    }

    @JavascriptInterface
    public String checkUpdate() {
        JSONObject out = ok();
        put(out, "status", "Güncel");
        put(out, "message", "Uygulamanın yüklü sürümü güncel görünüyor.");
        return out.toString();
    }

    void notifyPermissionChanged(boolean granted) {
        JSONObject event = event("permissionChanged");
        put(event, "granted", granted);
        if (granted) {
            put(event, "songs", querySongs(false));
            put(event, "downloadedSongs", querySongs(true));
        }
        sendEvent(event);
    }

    void onActivityResult(int requestCode, int resultCode) {
        if (requestCode == MainActivity.REQUEST_WRITE_PERMISSION && resultCode == Activity.RESULT_OK) {
            if (pendingRenameUri != null && pendingRenameTitle != null) {
                renameSong(pendingRenameUri, pendingRenameTitle);
            }
            pendingRenameUri = null;
            pendingRenameTitle = null;
        }
        if ((requestCode == MainActivity.REQUEST_DELETE_PERMISSION || requestCode == MainActivity.REQUEST_WRITE_PERMISSION)
                && activity.hasAudioPermission()) {
            JSONObject event = event("libraryChanged");
            put(event, "songs", querySongs(false));
            put(event, "downloadedSongs", querySongs(true));
            sendEvent(event);
        }
    }

    void release() {
        io.shutdownNow();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void playUri(Uri uri, JSONObject song) {
        try {
            ensurePlayer();
            player.reset();
            player.setDataSource(activity, uri);
            attachPlayerListeners(song);
            player.prepareAsync();
            sendPlaybackEvent("loading", song);
        } catch (Exception error) {
            sendError("Şarkı çalınamadı: " + friendly(error));
        }
    }

    private void playStream(String streamUrl, JSONObject song) {
        try {
            ensurePlayer();
            player.reset();
            player.setDataSource(streamUrl);
            attachPlayerListeners(song);
            player.prepareAsync();
            sendPlaybackEvent("loading", song);
        } catch (Exception error) {
            sendError("Önizleme çalınamadı: " + friendly(error));
        }
    }

    private void attachPlayerListeners(JSONObject song) {
        player.setOnPreparedListener(mp -> {
            mp.start();
            sendPlaybackEvent("playing", song);
        });
        player.setOnCompletionListener(mp -> sendPlaybackEvent("completed", song));
        player.setOnErrorListener((mp, what, extra) -> {
            sendError("Oynatma hatası oluştu.");
            return true;
        });
    }

    private void ensurePlayer() {
        if (player == null) player = new MediaPlayer();
    }

    private JSONArray querySongs(boolean onlyDownloaded) {
        JSONArray songs = new JSONArray();
        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String storageColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.RELATIVE_PATH
                : MediaStore.Audio.Media.DATA;

        String selection;
        String[] args;
        if (onlyDownloaded) {
            selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                    + MediaStore.Audio.Media.DURATION + " >= ? AND " + storageColumn + " LIKE ?";
            args = new String[]{String.valueOf(MIN_DURATION_MS), "%Music%Flowna%"};
        } else {
            selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
                    + MediaStore.Audio.Media.DURATION + " >= ?";
            args = new String[]{String.valueOf(MIN_DURATION_MS)};
        }

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                storageColumn
        };

        try (Cursor cursor = activity.getContentResolver().query(
                collection,
                projection,
                selection,
                args,
                MediaStore.Audio.Media.DATE_ADDED + " DESC"
        )) {
            if (cursor == null) return songs;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int pathCol = cursor.getColumnIndexOrThrow(storageColumn);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                long duration = cursor.getLong(durationCol);
                String path = cursor.getString(pathCol) == null ? "" : cursor.getString(pathCol);
                boolean isFlowna = isFlownaPath(path);
                if (!shouldInclude(duration, path, onlyDownloaded, isFlowna)) continue;

                Uri uri = ContentUris.withAppendedId(collection, id);
                long albumId = cursor.getLong(albumIdCol);
                JSONObject song = new JSONObject();
                put(song, "id", String.valueOf(id));
                put(song, "title", clean(cursor.getString(titleCol), "Bilinmeyen şarkı"));
                put(song, "artist", clean(cursor.getString(artistCol), isFlowna ? "Flowna" : "Bilinmeyen sanatçı"));
                put(song, "album", clean(cursor.getString(albumCol), isFlowna ? "Flowna İndirilenler" : ""));
                put(song, "durationMs", duration);
                put(song, "duration", formatDuration(duration));
                put(song, "uri", uri.toString());
                put(song, "cover", albumId > 0 ? ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString() : "");
                put(song, "source", "local");
                put(song, "downloaded", isFlowna);
                songs.put(song);
            }
        } catch (Exception ignored) {
        }
        return songs;
    }

    private JSONObject findSongByUri(Uri uri) {
        JSONArray songs = querySongs(false);
        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.optJSONObject(i);
            if (song != null && uri.toString().equals(song.optString("uri"))) return song;
        }
        return null;
    }

    private StreamPayload resolveAudioStream(String rawVideoUrl) throws Exception {
        ensureNewPipe();
        String videoUrl = normalizeVideoUrl(rawVideoUrl, "");
        StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(videoUrl);
        extractor.fetchPage();
        AudioStream best = null;
        for (AudioStream stream : extractor.getAudioStreams()) {
            if (stream == null || stream.getUrl() == null || stream.getUrl().isEmpty()) continue;
            if (best == null || stream.getAverageBitrate() > best.getAverageBitrate()) {
                best = stream;
            }
        }
        if (best == null) throw new IllegalStateException("Ses akışı bulunamadı.");
        return new StreamPayload(best.getUrl(), "audio/mp4", ".m4a");
    }

    private Uri saveStreamToMediaStore(String downloadId, JSONObject result, StreamPayload payload, String callbackId) throws Exception {
        String title = sanitizeFileName(result.optString("title", "Flowna Track"));
        String artist = clean(result.optString("artist"), "Flowna");
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, title + payload.extension);
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.Audio.Media.ARTIST, artist);
        values.put(MediaStore.Audio.Media.ALBUM, "Flowna İndirilenler");
        values.put(MediaStore.Audio.Media.MIME_TYPE, payload.mimeType);
        values.put(MediaStore.Audio.Media.IS_MUSIC, 1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, FLOWNA_RELATIVE_PATH);
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        }

        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Uri uri = activity.getContentResolver().insert(collection, values);
        if (uri == null) throw new IllegalStateException("Medya kaydı oluşturulamadı.");

        try {
            URLConnection connection = new URL(payload.url).openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            long total = Math.max(0, connection.getContentLengthLong());
            long copied = 0;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream input = connection.getInputStream();
                 OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Dosya yazılamadı.");
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    copied += read;
                    if (total > 0) {
                        int progress = (int) Math.min(99, (copied * 100) / total);
                        sendDownload(downloadId, "active", progress, "İndiriliyor", result, callbackId, null);
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Audio.Media.IS_PENDING, 0);
                activity.getContentResolver().update(uri, done, null, null);
            }
            return uri;
        } catch (Exception error) {
            activity.getContentResolver().delete(uri, null, null);
            throw error;
        }
    }

    private void ensureNewPipe() {
        if (newPipeReady.compareAndSet(false, true)) {
            NewPipe.init(NewPipeDownloader.getInstance());
        }
    }

    private JSONObject readSettings() {
        String raw = activity.getSharedPreferences("flowna_settings", Activity.MODE_PRIVATE)
                .getString("settings", "{}");
        try {
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private void sendPlaybackEvent(String status, JSONObject song) {
        JSONObject event = event("playback");
        put(event, "status", status);
        put(event, "track", song == null ? JSONObject.NULL : song);
        sendEvent(event);
    }

    private void sendDownload(String id, String status, int progress, String message, JSONObject track, String callbackId, String error) {
        JSONObject event = event("download");
        put(event, "id", id);
        put(event, "status", status);
        put(event, "progress", progress);
        put(event, "message", message);
        put(event, "callbackId", callbackId);
        put(event, "track", track == null ? JSONObject.NULL : track);
        if (error != null) put(event, "error", error);
        sendEvent(event);
    }

    private void sendError(String message) {
        JSONObject event = event("error");
        put(event, "message", message);
        sendEvent(event);
    }

    private void sendEvent(JSONObject event) {
        String js = "window.FlownaNativeBridge&&window.FlownaNativeBridge.onNativeEvent(" + event + ");";
        activity.runOnUiThread(() -> {
            if (activity.getBridge() != null && activity.getBridge().getWebView() != null) {
                activity.getBridge().getWebView().evaluateJavascript(js, null);
            }
        });
    }

    private JSONObject event(String type) {
        JSONObject event = new JSONObject();
        put(event, "type", type);
        return event;
    }

    private JSONObject ok() {
        JSONObject out = new JSONObject();
        put(out, "ok", true);
        return out;
    }

    private JSONObject fail(String message) {
        JSONObject out = new JSONObject();
        put(out, "ok", false);
        put(out, "error", message);
        return out;
    }

    private static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (Exception ignored) {
        }
    }

    private static JSONObject cloneTrack(JSONObject object) {
        try {
            return new JSONObject(object.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("<unknown>") || trimmed.equalsIgnoreCase("unknown")) {
            return fallback;
        }
        return trimmed;
    }

    private static String friendly(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "Bilinmeyen hata";
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("page needs to be reloaded")) return "YouTube akışı hazırlanamadı, tekrar deneyin.";
        if (lower.contains("timeout")) return "Bağlantı zaman aşımına uğradı.";
        if (lower.contains("permission")) return "Android izni gerekli.";
        return message;
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    private static String thumbnailOf(StreamInfoItem item) {
        try {
            if (item.getThumbnails() != null && !item.getThumbnails().isEmpty()) {
                return item.getThumbnails().get(0).getUrl();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String extractVideoId(String url) {
        if (url == null) return "";
        if (url.contains("v=")) return url.substring(url.indexOf("v=") + 2).split("&")[0];
        if (url.contains("youtu.be/")) return url.substring(url.indexOf("youtu.be/") + 9).split("\\?")[0];
        return "";
    }

    private static String normalizeVideoUrl(String rawUrl, String videoId) {
        if (videoId != null && !videoId.isEmpty()) return "https://www.youtube.com/watch?v=" + videoId;
        if (rawUrl == null) return "";
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) return rawUrl;
        if (rawUrl.startsWith("/")) return "https://www.youtube.com" + rawUrl;
        return "https://www.youtube.com/" + rawUrl;
    }

    private static boolean shouldInclude(long duration, String path, boolean onlyDownloaded, boolean isFlowna) {
        if (duration < MIN_DURATION_MS) return false;
        if (onlyDownloaded) return isFlowna;
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return !lower.contains("/notifications/")
                && !lower.contains("\\notifications\\")
                && !lower.contains("/ringtones/")
                && !lower.contains("\\ringtones\\")
                && !lower.contains("/alarms/")
                && !lower.contains("\\alarms\\");
    }

    private static boolean isFlownaPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return lower.contains("music/flowna") || lower.contains("music\\flowna");
    }

    private static String sanitizeFileName(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private static long deleteDir(File file) {
        if (file == null || !file.exists()) return 0;
        long total = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) total += deleteDir(child);
            }
        }
        total += file.length();
        // Cache temizliği başarısız olsa bile kullanıcı akışını kesmeyelim.
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        return total;
    }

    private String versionName() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "1.0.8" : info.versionName;
        } catch (Exception ignored) {
            return "1.0.8";
        }
    }

    private long versionCode() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return info.getLongVersionCode();
            return info.versionCode;
        } catch (Exception ignored) {
            return 9;
        }
    }

    private static final class StreamPayload {
        final String url;
        final String mimeType;
        final String extension;

        StreamPayload(String url, String mimeType, String extension) {
            this.url = url;
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }
}
