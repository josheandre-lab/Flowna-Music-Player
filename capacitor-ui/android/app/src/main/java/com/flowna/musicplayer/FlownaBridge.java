package com.flowna.musicplayer;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import kotlin.Unit;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class FlownaBridge {
    private static final String TAG = "FlownaBridge";
    private static final String FLOWNA_RELATIVE_PATH = "Music/Flowna";
    private static final String ARTWORK_CACHE_PREFS_KEY = "artworkCache";
    private static final String ARTWORK_CACHE_VERSION_KEY = "artworkCacheVersion";
    private static final int ARTWORK_CACHE_VERSION = 3;
    private static final long MIN_DURATION_MS = 10000L;
    private static final long YT_DLP_UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private final MainActivity activity;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final ExecutorService downloadIo = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean newPipeReady = new AtomicBoolean(false);
    private final AtomicBoolean ytDlpReady = new AtomicBoolean(false);
    private final AtomicBoolean ffmpegReady = new AtomicBoolean(false);
    private final AtomicBoolean ytDlpUpdateRunning = new AtomicBoolean(false);
    private final AtomicBoolean downloadRunning = new AtomicBoolean(false);
    private MediaPlayer player;
    private JSONObject currentTrack;
    private String pendingDeleteUri;
    private String pendingRenameUri;
    private String pendingRenameTitle;

    FlownaBridge(MainActivity activity) {
        this.activity = activity;
        ensureArtworkCacheVersion();
    }

    private void ensureArtworkCacheVersion() {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("flowna_cache", Activity.MODE_PRIVATE);
        if (prefs.getInt(ARTWORK_CACHE_VERSION_KEY, 0) >= ARTWORK_CACHE_VERSION) return;
        prefs.edit()
                .remove(ARTWORK_CACHE_PREFS_KEY)
                .putInt(ARTWORK_CACHE_VERSION_KEY, ARTWORK_CACHE_VERSION)
                .apply();
        deleteDir(new File(activity.getFilesDir(), "artwork-cache"));
    }

    @JavascriptInterface
    public String getInitialState() {
        JSONObject out = ok();
        put(out, "permissionGranted", activity.hasAudioPermission());
        put(out, "notificationPermission", activity.hasNotificationPermission());
        put(out, "manageStoragePermission", activity.hasManageStoragePermission());
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
    public String requestNotificationPermission() {
        activity.runOnUiThread(activity::requestNotificationPermission);
        JSONObject out = ok();
        put(out, "requested", true);
        put(out, "granted", activity.hasNotificationPermission());
        return out.toString();
    }

    @JavascriptInterface
    public String requestManageStoragePermission() {
        activity.runOnUiThread(activity::requestManageStoragePermission);
        JSONObject out = ok();
        put(out, "requested", true);
        put(out, "granted", activity.hasManageStoragePermission());
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
                sendPlaybackEvent("paused", currentTrack);
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

    void handleMediaCommand(String command) {
        activity.runOnUiThread(() -> {
            try {
                if ("play".equals(command)) {
                    if (player != null && !player.isPlaying()) {
                        player.start();
                        sendPlaybackEvent("playing", currentTrack);
                    }
                    return;
                }
                if ("pause".equals(command)) {
                    if (player != null && player.isPlaying()) {
                        player.pause();
                        sendPlaybackEvent("paused", currentTrack);
                    }
                    return;
                }
                if ("toggle".equals(command)) {
                    if (player != null && player.isPlaying()) handleMediaCommand("pause");
                    else handleMediaCommand("play");
                    return;
                }
                JSONObject event = event("mediaCommand");
                put(event, "command", command);
                sendEvent(event);
            } catch (Exception error) {
                sendError("Medya komutu uygulanamadı: " + friendly(error));
            }
        });
    }

    void handleMediaSeek(long positionMs) {
        activity.runOnUiThread(() -> {
            try {
                if (player != null) {
                    player.seekTo((int) Math.max(0L, positionMs));
                    sendPlaybackEvent(player.isPlaying() ? "playing" : "paused", currentTrack);
                }
            } catch (Exception error) {
                sendError("İleri sarma uygulanamadı: " + friendly(error));
            }
        });
    }

    @JavascriptInterface
    public String search(String query, String callbackId) {
        io.execute(() -> {
            JSONObject event = event("searchResults");
            put(event, "callbackId", callbackId);
            try {
                JSONArray results = searchOnline(query);
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
        downloadIo.execute(() -> {
            String downloadId = "dl_" + System.currentTimeMillis();
            downloadRunning.set(true);
            String ytDlpFailure = null;
            try {
                JSONObject result = new JSONObject(resultJson);
                sendDownload(downloadId, "active", 0, "Hazırlanıyor", result, callbackId, null);
                Log.i(TAG, "download queued: " + result.optString("title") + " / " + result.optString("videoUrl"));
                Uri savedUri;
                try {
                    savedUri = downloadWithYoutubeDl(downloadId, result, callbackId);
                } catch (Exception ytDlpError) {
                    ytDlpFailure = friendly(ytDlpError);
                    Log.w(TAG, "yt-dlp failed, falling back to direct stream mp3: " + ytDlpFailure, ytDlpError);
                    sendDownload(downloadId, "active", 5, "Alternatif MP3 indirme deneniyor", result, callbackId, null);
                    savedUri = downloadDirectStreamAsMp3(downloadId, result, callbackId);
                }
                JSONObject savedSong = findSongByUri(savedUri);
                sendDownload(downloadId, "completed", 100, "İndirme tamamlandı", savedSong == null ? result : savedSong, callbackId, null);
            } catch (Exception error) {
                String errorText = friendly(error);
                if (ytDlpFailure != null && !ytDlpFailure.trim().isEmpty() && !ytDlpFailure.equals(errorText)) {
                    errorText = errorText + " (yt-dlp: " + ytDlpFailure + ")";
                }
                Log.e(TAG, "download failed: " + errorText, error);
                JSONObject fallback = new JSONObject();
                try {
                    JSONObject result = new JSONObject(resultJson);
                    fallback = result;
                } catch (Exception ignored) {
                }
                sendDownload(downloadId, "failed", 0, "İndirme başarısız", fallback, callbackId, errorText);
            } finally {
                downloadRunning.set(false);
            }
        });
        JSONObject out = ok();
        put(out, "queued", true);
        return out.toString();
    }

    @JavascriptInterface
    public String warmupDownloadEngine() {
        io.execute(() -> {
            try {
                ensureYtDlp();
                Log.i(TAG, "download engine warmed up");
                timers.schedule(this::maybeUpdateYtDlpAsync, 30, TimeUnit.SECONDS);
            } catch (Exception error) {
                Log.w(TAG, "download engine warmup skipped: " + friendly(error));
            }
        });
        return ok().toString();
    }

    @JavascriptInterface
    public String deleteSong(String uriString) {
        try {
            return performDelete(Uri.parse(uriString), true).toString();
        } catch (Exception error) {
            return fail("Dosya silinemedi: " + friendly(error)).toString();
        }
    }

    @JavascriptInterface
    public String renameSong(String uriString, String newTitle) {
        String safeTitle = sanitizeFileName(newTitle);
        if (safeTitle.isEmpty()) return fail("Yeni ad boş olamaz.").toString();
        try {
            return performRename(Uri.parse(uriString), safeTitle, true).toString();
        } catch (Exception error) {
            return fail("Ad değiştirilemedi: " + friendly(error)).toString();
        }
    }

    @JavascriptInterface
    public String refreshArtwork(String trackJson, String callbackId) {
        io.execute(() -> {
            JSONObject event = event("artworkReady");
            put(event, "callbackId", callbackId);
            try {
                JSONObject track = new JSONObject(trackJson);
                Uri uri = Uri.parse(track.optString("uri", ""));
                String title = clean(track.optString("title"), "");
                String artist = clean(track.optString("artist"), "");
                String cover = resolveArtworkDataUri(uri, -1L, title, artist, true);
                if (cover.isEmpty()) {
                    put(event, "ok", false);
                    put(event, "error", "Kapak bulunamadı.");
                } else {
                    JSONObject outTrack = cloneTrack(track);
                    put(outTrack, "cover", cover);
                    put(event, "ok", true);
                    put(event, "track", outTrack);
                }
            } catch (Exception error) {
                put(event, "ok", false);
                put(event, "error", "Kapak yenilenemedi: " + friendly(error));
            }
            sendEvent(event);
        });
        return ok().toString();
    }

    @JavascriptInterface
    public String saveSettings(String settingsJson) {
        activity.getSharedPreferences("flowna_settings", Activity.MODE_PRIVATE)
                .edit()
                .putString("settings", settingsJson)
                .apply();
        return ok().toString();
    }

    private JSONObject performDelete(Uri uri, boolean allowPermissionRequest) throws Exception {
        try {
            int rows = activity.getContentResolver().delete(uri, null, null);
            if (rows > 0) return fileActionSuccess("delete", uri);
        } catch (SecurityException error) {
            if (activity.hasManageStoragePermission() && deleteFileDirectly(uri)) {
                return fileActionSuccess("delete", uri);
            }
            JSONObject storagePending = requestManageStorageAccess("delete", uri, allowPermissionRequest);
            if (storagePending != null) return storagePending;
            JSONObject pending = maybeRequestDeletePermission(uri, allowPermissionRequest, error);
            if (pending != null) return pending;
            throw error;
        }

        if (activity.hasManageStoragePermission() && deleteFileDirectly(uri)) {
            return fileActionSuccess("delete", uri);
        }

        JSONObject storagePending = requestManageStorageAccess("delete", uri, allowPermissionRequest);
        if (storagePending != null) return storagePending;

        JSONObject pending = maybeRequestDeletePermission(uri, allowPermissionRequest, null);
        if (pending != null) return pending;
        return fail("Dosya silinemedi. Android izin vermedi veya dosya bulunamadı.");
    }

    private JSONObject performRename(Uri uri, String safeTitle, boolean allowPermissionRequest) throws Exception {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.TITLE, safeTitle);
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, safeTitle + extensionForUri(uri));
            int rows = activity.getContentResolver().update(uri, values, null, null);
            if (rows > 0) return fileActionSuccess("rename", uri);
        } catch (SecurityException error) {
            if (activity.hasManageStoragePermission() && renameFileDirectly(uri, safeTitle)) {
                return fileActionSuccess("rename", uri);
            }
            JSONObject storagePending = requestManageStorageAccess("rename", uri, allowPermissionRequest);
            if (storagePending != null) return storagePending;
            JSONObject pending = maybeRequestWritePermission(uri, safeTitle, allowPermissionRequest, error);
            if (pending != null) return pending;
            throw error;
        }

        if (activity.hasManageStoragePermission() && renameFileDirectly(uri, safeTitle)) {
            return fileActionSuccess("rename", uri);
        }

        JSONObject storagePending = requestManageStorageAccess("rename", uri, allowPermissionRequest);
        if (storagePending != null) return storagePending;

        JSONObject pending = maybeRequestWritePermission(uri, safeTitle, allowPermissionRequest, null);
        if (pending != null) return pending;
        return fail("Dosya adı değiştirilemedi. Android izin vermedi veya dosya bulunamadı.");
    }

    private JSONObject requestManageStorageAccess(String action, Uri uri, boolean allowPermissionRequest) {
        if (!allowPermissionRequest || activity.hasManageStoragePermission() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        JSONObject out = ok();
        put(out, "action", action);
        put(out, "uri", uri.toString());
        put(out, "pendingPermission", true);
        put(out, "manageStorageRequired", true);
        put(out, "message", "Tek seferlik dosya erişimi gerekli.");
        activity.runOnUiThread(activity::requestManageStoragePermission);
        return out;
    }

    private JSONObject maybeRequestDeletePermission(Uri uri, boolean allow, SecurityException error) throws Exception {
        if (!allow) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error instanceof RecoverableSecurityException) {
            return requestDeletePermission(uri, ((RecoverableSecurityException) error).getUserAction().getActionIntent());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PendingIntent request = MediaStore.createDeleteRequest(
                    activity.getContentResolver(),
                    Collections.singletonList(uri)
            );
            return requestDeletePermission(uri, request);
        }
        return null;
    }

    private JSONObject maybeRequestWritePermission(Uri uri, String safeTitle, boolean allow, SecurityException error) throws Exception {
        if (!allow) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error instanceof RecoverableSecurityException) {
            return requestWritePermission(uri, safeTitle, ((RecoverableSecurityException) error).getUserAction().getActionIntent());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PendingIntent request = MediaStore.createWriteRequest(
                    activity.getContentResolver(),
                    Collections.singletonList(uri)
            );
            return requestWritePermission(uri, safeTitle, request);
        }
        return null;
    }

    private JSONObject requestDeletePermission(Uri uri, PendingIntent request) throws Exception {
        pendingDeleteUri = uri.toString();
        activity.startIntentSenderForResult(
                request.getIntentSender(),
                MainActivity.REQUEST_DELETE_PERMISSION,
                null,
                0,
                0,
                0
        );
        JSONObject out = ok();
        put(out, "action", "delete");
        put(out, "uri", uri.toString());
        put(out, "pendingPermission", true);
        put(out, "message", "Android silme onayı açıldı.");
        return out;
    }

    private JSONObject requestWritePermission(Uri uri, String safeTitle, PendingIntent request) throws Exception {
        pendingRenameUri = uri.toString();
        pendingRenameTitle = safeTitle;
        activity.startIntentSenderForResult(
                request.getIntentSender(),
                MainActivity.REQUEST_WRITE_PERMISSION,
                null,
                0,
                0,
                0
        );
        JSONObject out = ok();
        put(out, "action", "rename");
        put(out, "uri", uri.toString());
        put(out, "pendingPermission", true);
        put(out, "message", "Android düzenleme onayı açıldı.");
        return out;
    }

    private JSONObject fileActionSuccess(String action, Uri uri) {
        JSONObject out = ok();
        put(out, "action", action);
        put(out, "uri", uri.toString());
        put(out, "pendingPermission", false);
        put(out, "songs", querySongs(false));
        put(out, "downloadedSongs", querySongs(true));
        return out;
    }

    private void sendFileActionResult(String action, String uriString, JSONObject result) {
        JSONObject event = event("fileActionResult");
        put(event, "action", action);
        put(event, "uri", uriString == null ? "" : uriString);
        put(event, "ok", result.optBoolean("ok", false));
        put(event, "pendingPermission", false);
        if (result.has("error")) put(event, "error", result.optString("error"));
        if (result.has("songs")) put(event, "songs", result.optJSONArray("songs"));
        if (result.has("downloadedSongs")) put(event, "downloadedSongs", result.optJSONArray("downloadedSongs"));
        sendEvent(event);
    }

    private String extensionForUri(Uri uri) {
        String displayName = "";
        String[] projection = {MediaColumns.DISPLAY_NAME};
        try (Cursor cursor = activity.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME));
            }
        } catch (Exception ignored) {
        }
        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot < displayName.length() - 1) {
                String ext = displayName.substring(dot);
                if (ext.length() <= 8) return ext;
            }
        }
        return ".mp3";
    }

    private boolean deleteFileDirectly(Uri uri) {
        File file = fileForMediaUri(uri);
        if (file == null || !file.exists()) return false;
        String path = file.getAbsolutePath();
        boolean deleted = file.delete();
        MediaScannerConnection.scanFile(activity, new String[]{path}, null, null);
        if (deleted) {
            try {
                activity.getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
            }
        }
        return deleted;
    }

    private boolean renameFileDirectly(Uri uri, String safeTitle) {
        File file = fileForMediaUri(uri);
        if (file == null || !file.exists()) return false;
        String oldPath = file.getAbsolutePath();
        File target = new File(file.getParentFile(), safeTitle + extensionForUri(uri));
        int suffix = 1;
        while (target.exists() && suffix < 100) {
            target = new File(file.getParentFile(), safeTitle + " (" + suffix + ")" + extensionForUri(uri));
            suffix++;
        }
        boolean renamed = file.renameTo(target);
        if (renamed) {
            MediaScannerConnection.scanFile(activity, new String[]{oldPath, target.getAbsolutePath()}, null, null);
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.TITLE, safeTitle);
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, target.getName());
                activity.getContentResolver().update(uri, values, null, null);
            } catch (Exception ignored) {
            }
        }
        return renamed;
    }

    private File fileForMediaUri(Uri uri) {
        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{MediaColumns.DISPLAY_NAME, MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DATA};
        } else {
            projection = new String[]{MediaColumns.DISPLAY_NAME, MediaStore.Audio.Media.DATA};
        }
        try (Cursor cursor = activity.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            String data = cursorValue(cursor, MediaStore.Audio.Media.DATA);
            if (data != null && !data.trim().isEmpty()) {
                File file = new File(data);
                if (file.exists()) return file;
            }
            String displayName = cursorValue(cursor, MediaColumns.DISPLAY_NAME);
            String relativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? cursorValue(cursor, MediaStore.Audio.Media.RELATIVE_PATH)
                    : "";
            if (displayName == null || displayName.trim().isEmpty()) return null;
            File root = Environment.getExternalStorageDirectory();
            return new File(root, (relativePath == null ? "" : relativePath) + displayName);
        } catch (Exception error) {
            Log.w(TAG, "file path resolve failed: " + friendly(error));
            return null;
        }
    }

    private static String cursorValue(Cursor cursor, String column) {
        try {
            int index = cursor.getColumnIndex(column);
            return index >= 0 ? cursor.getString(index) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    @JavascriptInterface
    public String clearCache() {
        long deleted = deleteDir(activity.getCacheDir());
        deleted += deleteDir(new File(activity.getFilesDir(), "artwork-cache"));
        activity.getSharedPreferences("flowna_cache", Activity.MODE_PRIVATE)
                .edit()
                .remove(ARTWORK_CACHE_PREFS_KEY)
                .putInt(ARTWORK_CACHE_VERSION_KEY, ARTWORK_CACHE_VERSION)
                .apply();
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

    @JavascriptInterface
    public String checkYtDlpUpdateNow() {
        JSONObject out = ok();
        if (downloadRunning.get()) {
            put(out, "ok", false);
            put(out, "error", "İndirme sırasında yt-dlp güncellemesi yapılamaz.");
            return out.toString();
        }
        if (!ytDlpUpdateRunning.compareAndSet(false, true)) {
            put(out, "ok", false);
            put(out, "error", "yt-dlp güncellemesi zaten çalışıyor.");
            return out.toString();
        }
        try {
            ensureYtDlp();
        } catch (Exception error) {
            ytDlpUpdateRunning.set(false);
            put(out, "ok", false);
            put(out, "error", "yt-dlp başlatılamadı: " + friendly(error));
            return out.toString();
        }
        io.execute(() -> {
            try {
                sendYtDlpUpdate("started", "yt-dlp güncellemesi başlatıldı.");
                YoutubeDL.getInstance().updateYoutubeDL(
                        activity.getApplicationContext(),
                        YoutubeDL.UpdateChannel._STABLE
                );
                sendYtDlpUpdate("completed", "yt-dlp güncellemesi tamamlandı.");
            } catch (Exception error) {
                sendYtDlpUpdate("failed", "yt-dlp güncellemesi başarısız: " + friendly(error));
            } finally {
                ytDlpUpdateRunning.set(false);
                activity.getSharedPreferences("flowna_settings", Activity.MODE_PRIVATE)
                        .edit()
                        .putLong("lastYtDlpUpdateCheck", System.currentTimeMillis())
                        .apply();
            }
        });
        put(out, "queued", true);
        put(out, "message", "yt-dlp güncellemesi başlatıldı.");
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

    void notifyNotificationPermissionChanged(boolean granted) {
        JSONObject event = event("notificationPermissionChanged");
        put(event, "granted", granted);
        sendEvent(event);
    }

    void notifyStoragePermissionChanged(boolean granted) {
        JSONObject event = event("storagePermissionChanged");
        put(event, "granted", granted);
        if (activity.hasAudioPermission()) {
            put(event, "songs", querySongs(false));
            put(event, "downloadedSongs", querySongs(true));
        }
        sendEvent(event);
    }

    void onActivityResult(int requestCode, int resultCode) {
        if (requestCode == MainActivity.REQUEST_DELETE_PERMISSION) {
            String uriString = pendingDeleteUri;
            pendingDeleteUri = null;
            if (uriString != null) {
                JSONObject result;
                if (resultCode == Activity.RESULT_OK) {
                    try {
                        result = performDelete(Uri.parse(uriString), false);
                    } catch (Exception error) {
                        Log.w(TAG, "delete after permission failed: " + friendly(error));
                        result = fail("Silme tamamlanamadı: " + friendly(error));
                    }
                } else {
                    result = fail("Silme izni verilmedi.");
                }
                sendFileActionResult("delete", uriString, result);
            }
            return;
        }
        if (requestCode == MainActivity.REQUEST_WRITE_PERMISSION) {
            String uriString = pendingRenameUri;
            String title = pendingRenameTitle;
            pendingRenameUri = null;
            pendingRenameTitle = null;
            if (uriString != null && title != null) {
                JSONObject result;
                if (resultCode == Activity.RESULT_OK) {
                    try {
                        result = performRename(Uri.parse(uriString), title, false);
                    } catch (Exception error) {
                        Log.w(TAG, "rename after permission failed: " + friendly(error));
                        result = fail("Ad değiştirme tamamlanamadı: " + friendly(error));
                    }
                } else {
                    result = fail("Düzenleme izni verilmedi.");
                }
                sendFileActionResult("rename", uriString, result);
            }
            return;
        }
    }

    void release() {
        io.shutdownNow();
        downloadIo.shutdownNow();
        timers.shutdownNow();
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
            Log.w(TAG, "MediaPlayer reported error what=" + what + " extra=" + extra + " for " + song.optString("title", ""));
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
                String title = clean(cursor.getString(titleCol), "Bilinmeyen \u015fark\u0131");
                String artist = clean(cursor.getString(artistCol), isFlowna ? "Flowna" : "Bilinmeyen sanat\u00e7\u0131");
                String album = clean(cursor.getString(albumCol), isFlowna ? "Flowna \u0130ndirilenler" : "");
                JSONObject song = new JSONObject();
                put(song, "id", String.valueOf(id));
                put(song, "title", title);
                put(song, "artist", artist);
                put(song, "album", album);
                put(song, "durationMs", duration);
                put(song, "duration", formatDuration(duration));
                put(song, "uri", uri.toString());
                put(song, "cover", resolveArtworkDataUri(uri, albumId, title, artist, isFlowna));
                put(song, "source", "local");
                put(song, "downloaded", isFlowna);
                songs.put(song);
            }
        } catch (Exception ignored) {
        }
        return songs;
    }

    private String resolveArtworkDataUri(Uri songUri, long albumId, String title, String artist, boolean downloaded) {
        byte[] embedded = readEmbeddedArtwork(songUri);
        if (embedded != null && embedded.length > 0) {
            return toDataUri(embedded);
        }

        if (!downloaded && albumId > 0) {
            Uri albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
            );
            byte[] albumArt = readBytesFromUri(albumArtUri);
            if (albumArt != null && albumArt.length > 0) {
                return toDataUri(albumArt);
            }
        }

        String cached = cachedArtworkDataUri(songUri, title, artist);
        if (!cached.isEmpty()) return cached;

        if (downloaded) {
            String fetched = fetchArtworkDataUri(songUri, title, artist);
            if (!fetched.isEmpty()) return fetched;
        }
        return "";
    }

    private String cachedArtworkDataUri(Uri songUri, String title, String artist) {
        JSONObject cache = readArtworkCache();
        String path = firstNonEmpty(
                cache.optString("uri:" + songUri, ""),
                cache.optString(mediaIdArtworkKey(songUri), "")
        );
        if (path.isEmpty()) return "";
        byte[] bytes = readBytesFromFile(new File(path));
        if (bytes == null || bytes.length == 0) return "";
        return toDataUri(bytes);
    }

    private String fetchArtworkDataUri(Uri songUri, String title, String artist) {
        String query = (clean(artist, "") + " " + clean(title, "")).trim();
        if (query.isEmpty()) return "";
        try {
            JSONArray items = searchWithYoutubeHtml(query);
            if (items.length() == 0) {
                items = searchWithYoutubeDl(query);
            }
            for (int i = 0; i < items.length() && i < 4; i++) {
                JSONObject song = items.optJSONObject(i);
                if (song == null) continue;
                String coverUrl = normalizeThumbnailUrl(song.optString("cover", ""));
                if (coverUrl.isEmpty()) continue;
                byte[] bytes = downloadArtworkBytes(coverUrl);
                if (bytes == null || bytes.length == 0) continue;
                cacheArtworkBytes(songUri, title, artist, bytes);
                return toDataUri(bytes);
            }
        } catch (Exception error) {
            Log.w(TAG, "artwork backfill skipped: " + friendly(error));
        }
        return "";
    }

    private void cacheArtworkForSong(Uri songUri, JSONObject result) {
        String coverUrl = normalizeThumbnailUrl(result.optString("cover", ""));
        if (coverUrl.isEmpty()) return;
        try {
            byte[] bytes = downloadArtworkBytes(coverUrl);
            if (bytes == null || bytes.length == 0) return;
            cacheArtworkBytes(
                    songUri,
                    clean(result.optString("title"), ""),
                    clean(result.optString("artist"), ""),
                    bytes
            );
        } catch (Exception error) {
            Log.w(TAG, "artwork cache skipped: " + friendly(error));
        }
    }

    private void cacheArtworkBytes(Uri songUri, String title, String artist, byte[] bytes) throws Exception {
        File dir = new File(activity.getFilesDir(), "artwork-cache");
        if (!dir.exists() && !dir.mkdirs()) return;

        File file = new File(
                dir,
                "art_" + Integer.toHexString(Math.abs(songUri.toString().hashCode())) + artworkFileExtension(bytes)
        );
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }

        JSONObject cache = readArtworkCache();
        cache.put("uri:" + songUri, file.getAbsolutePath());
        String mediaIdKey = mediaIdArtworkKey(songUri);
        if (!mediaIdKey.isEmpty()) {
            cache.put(mediaIdKey, file.getAbsolutePath());
        }
        writeArtworkCache(cache);
    }

    private JSONObject readArtworkCache() {
        String raw = activity.getSharedPreferences("flowna_cache", Activity.MODE_PRIVATE)
                .getString(ARTWORK_CACHE_PREFS_KEY, "{}");
        try {
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private void writeArtworkCache(JSONObject cache) {
        activity.getSharedPreferences("flowna_cache", Activity.MODE_PRIVATE)
                .edit()
                .putString(ARTWORK_CACHE_PREFS_KEY, cache == null ? "{}" : cache.toString())
                .apply();
    }

    private static String artworkMetaKey(String title, String artist) {
        String safeTitle = sanitizeFileName(title).toLowerCase(Locale.ROOT);
        String safeArtist = sanitizeFileName(artist).toLowerCase(Locale.ROOT);
        if (safeTitle.isEmpty() && safeArtist.isEmpty()) return "";
        return safeTitle + "|" + safeArtist;
    }

    private byte[] downloadArtworkBytes(String url) {
        try {
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStream input = connection.getInputStream()) {
                return readFully(input, 2 * 1024 * 1024);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] readEmbeddedArtwork(Uri songUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(activity, songUri);
            return retriever.getEmbeddedPicture();
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private byte[] readBytesFromUri(Uri uri) {
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            return readFully(input, 4 * 1024 * 1024);
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] readBytesFromFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null;
        try (InputStream input = new FileInputStream(file)) {
            return readFully(input, 4 * 1024 * 1024);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] readFully(InputStream input, int maxBytes) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (total + read > maxBytes) return null;
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private static String readText(URLConnection connection) throws java.io.IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }
        return text.toString();
    }

    private static String toDataUri(byte[] bytes) {
        String prefix = "data:" + imageMimeType(bytes) + ";base64,";
        return prefix + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private JSONObject findSongByUri(Uri uri) {
        JSONArray songs = querySongs(false);
        for (int i = 0; i < songs.length(); i++) {
            JSONObject song = songs.optJSONObject(i);
            if (song != null && uri.toString().equals(song.optString("uri"))) return song;
        }
        return null;
    }

    private JSONArray searchOnline(String query) throws Exception {
        JSONArray results = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<String> failures = new ArrayList<>();

        collectSearchResults(results, seen, failures, "HTML", () -> searchWithYoutubeHtml(query));
        if (results.length() < 12) {
            collectSearchResults(results, seen, failures, "NewPipe", () -> searchWithNewPipe(query));
        }
        if (results.length() < 12) {
            collectSearchResults(results, seen, failures, "yt-dlp", () -> searchWithYoutubeDl(query));
        }

        if (results.length() == 0 && !failures.isEmpty()) {
            throw new IllegalStateException(failures.get(0));
        }
        return results;
    }

    private interface SearchProvider {
        JSONArray load() throws Exception;
    }

    private void collectSearchResults(
            JSONArray target,
            LinkedHashSet<String> seen,
            List<String> failures,
            String source,
            SearchProvider provider
    ) {
        try {
            JSONArray items = provider.load();
            for (int i = 0; i < items.length() && target.length() < 24; i++) {
                JSONObject song = items.optJSONObject(i);
                if (song == null) continue;
                String id = song.optString("id", "");
                String videoUrl = song.optString("videoUrl", "");
                String key = !id.isEmpty() ? id : videoUrl;
                if (key.isEmpty() || seen.contains(key)) continue;
                seen.add(key);
                if (song.optString("cover", "").isEmpty() && id.length() == 11) {
                    put(song, "cover", youtubeThumbnail(id));
                }
                target.put(song);
            }
        } catch (Exception error) {
            String failure = source + ": " + friendly(error);
            failures.add(failure);
            Log.w(TAG, "search provider failed: " + failure, error);
        }
    }

    private JSONArray searchWithNewPipe(String query) throws Exception {
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
            put(song, "cover", normalizeThumbnailUrl(thumbnailOf(stream)));
            put(song, "videoUrl", url);
            put(song, "source", "online");
            put(song, "downloaded", false);
            results.put(song);
            if (results.length() >= 24) break;
        }
        return results;
    }

    private JSONArray searchWithYoutubeDl(String query) throws Exception {
        ensureYtDlp();
        YoutubeDLRequest request = new YoutubeDLRequest("ytsearch24:" + query);
        request.addOption("--dump-single-json");
        request.addOption("--skip-download");
        request.addOption("--no-playlist");
        request.addOption("--no-warnings");
        request.addOption("--ignore-errors");

        YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, "search_" + System.currentTimeMillis());
        JSONObject payload = new JSONObject(response.getOut());
        JSONArray entries = payload.optJSONArray("entries");
        JSONArray results = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (entries == null) return results;

        for (int i = 0; i < entries.length() && results.length() < 24; i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) continue;
            String videoId = item.optString("id", "");
            if (videoId.length() != 11) videoId = extractVideoId(item.optString("webpage_url", ""));
            if (videoId.length() != 11 || seen.contains(videoId)) continue;
            seen.add(videoId);

            long durationSeconds = item.optLong("duration", 0L);
            JSONObject song = new JSONObject();
            put(song, "id", videoId);
            put(song, "title", clean(item.optString("title"), "Bilinmeyen şarkı"));
            put(song, "artist", clean(firstNonEmpty(item.optString("artist"), item.optString("uploader"), item.optString("channel")), "YouTube"));
            put(song, "durationMs", Math.max(0L, durationSeconds) * 1000L);
            put(song, "duration", formatDuration(Math.max(0L, durationSeconds) * 1000L));
            put(song, "cover", normalizeThumbnailUrl(firstNonEmpty(item.optString("thumbnail"), youtubeThumbnail(videoId))));
            put(song, "videoUrl", "https://www.youtube.com/watch?v=" + videoId);
            put(song, "source", "online");
            put(song, "downloaded", false);
            results.put(song);
        }
        return results;
    }

    private JSONArray searchWithYoutubeHtml(String query) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        URLConnection connection = new URL("https://www.youtube.com/results?search_query=" + encoded).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        connection.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7");

        String html = readText(connection);
        String initialData = extractAssignedJson(html, "var ytInitialData = ");
        if (initialData.isEmpty()) {
            initialData = extractAssignedJson(html, "ytInitialData = ");
        }
        if (initialData.isEmpty()) {
            throw new IllegalStateException("YouTube arama verisi bulunamadi.");
        }
        JSONArray results = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        collectVideoRenderers(new JSONObject(initialData), results, seen);
        return results;
    }

    private void collectVideoRenderers(Object node, JSONArray results, LinkedHashSet<String> seen) {
        if (node == null || results.length() >= 24) return;
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            JSONObject video = object.optJSONObject("videoRenderer");
            if (video != null) {
                addVideoRenderer(video, results, seen);
                if (results.length() >= 24) return;
            }

            JSONArray names = object.names();
            if (names == null) return;
            for (int i = 0; i < names.length() && results.length() < 24; i++) {
                collectVideoRenderers(object.opt(names.optString(i)), results, seen);
            }
            return;
        }

        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length() && results.length() < 24; i++) {
                collectVideoRenderers(array.opt(i), results, seen);
            }
        }
    }

    private void addVideoRenderer(JSONObject renderer, JSONArray results, LinkedHashSet<String> seen) {
        String videoId = renderer.optString("videoId", "");
        if (videoId.length() != 11 || seen.contains(videoId)) return;

        String title = jsonText(renderer.optJSONObject("title"));
        if (title.isEmpty()) return;

        String artist = jsonText(renderer.optJSONObject("ownerText"));
        if (artist.isEmpty()) artist = jsonText(renderer.optJSONObject("longBylineText"));
        String durationText = jsonText(renderer.optJSONObject("lengthText"));

        seen.add(videoId);
        JSONObject song = new JSONObject();
        put(song, "id", videoId);
        put(song, "title", clean(title, "Bilinmeyen şarkı"));
        put(song, "artist", clean(artist, "YouTube"));
        put(song, "durationMs", parseDurationMs(durationText));
        put(song, "duration", durationText);
        put(song, "cover", normalizeThumbnailUrl(thumbnailFrom(renderer.optJSONObject("thumbnail"))));
        put(song, "videoUrl", "https://www.youtube.com/watch?v=" + videoId);
        put(song, "source", "online");
        put(song, "downloaded", false);
        results.put(song);
    }

    private static String jsonText(JSONObject object) {
        if (object == null) return "";
        String simple = object.optString("simpleText", "");
        if (!simple.isEmpty()) return simple.trim();

        JSONArray runs = object.optJSONArray("runs");
        if (runs != null) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < runs.length(); i++) {
                JSONObject run = runs.optJSONObject(i);
                if (run == null) continue;
                String text = run.optString("text", "");
                if (!text.isEmpty()) builder.append(text);
            }
            String combined = builder.toString().trim();
            if (!combined.isEmpty()) return combined;
        }

        JSONObject accessibility = object.optJSONObject("accessibility");
        if (accessibility != null) {
            JSONObject data = accessibility.optJSONObject("accessibilityData");
            if (data != null) return data.optString("label", "").trim();
        }
        return "";
    }

    private static String thumbnailFrom(JSONObject thumbnail) {
        if (thumbnail == null) return "";
        JSONArray thumbnails = thumbnail.optJSONArray("thumbnails");
        if (thumbnails == null || thumbnails.length() == 0) return "";
        JSONObject best = thumbnails.optJSONObject(thumbnails.length() - 1);
        return best == null ? "" : best.optString("url", "");
    }

    private StreamPayload resolveAudioStream(String rawVideoUrl) throws Exception {
        String videoUrl = normalizeVideoUrl(rawVideoUrl, "");
        try {
            return resolveAudioStreamWithYoutubeDl(videoUrl);
        } catch (Exception ignored) {
            // NewPipe is a useful fallback when yt-dlp cannot expose a direct stream.
        }
        return resolveAudioStreamWithNewPipe(videoUrl);
    }

    private StreamPayload resolveAudioStreamWithNewPipe(String videoUrl) throws Exception {
        ensureNewPipe();
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

    private StreamPayload resolveAudioStreamWithYoutubeDl(String videoUrl) throws Exception {
        ensureYtDlp();
        YoutubeDLRequest request = new YoutubeDLRequest(videoUrl);
        request.addOption("--no-playlist");
        request.addOption("--no-warnings");
        request.addOption("--skip-download");
        request.addOption("-f", "bestaudio");
        request.addOption("-g");
        YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, "stream_" + System.currentTimeMillis());
        String streamUrl = firstStreamUrl(response.getOut());
        if (streamUrl.isEmpty()) throw new IllegalStateException(extractYtDlpError(response));
        return new StreamPayload(streamUrl, "audio/mp4", ".m4a");
    }

    private Uri downloadWithYoutubeDl(String downloadId, JSONObject result, String callbackId) throws Exception {
        ensureYtDlp();
        ensureFfmpeg();
        String videoUrl = normalizeVideoUrl(result.optString("videoUrl"), result.optString("id"));
        if (videoUrl.isEmpty()) throw new IllegalStateException("Video bağlantısı bulunamadı.");

        File tempDir = new File(activity.getCacheDir(), "flowna_downloads/" + downloadId);
        deleteDir(tempDir);
        if (!tempDir.mkdirs() && !tempDir.exists()) {
            throw new IllegalStateException("Geçici indirme klasörü oluşturulamadı.");
        }

        try {
            YoutubeDLResponse response = tryDownloadWithYtDlp(downloadId, result, callbackId, videoUrl, tempDir, true);
            File downloaded = findFirstAudioFile(tempDir);
            if (downloaded == null) {
                // Second attempt with a minimal option set. This often helps when YouTube breaks specific clients/flags.
                Log.w(TAG, "yt-dlp did not produce an audio file, retrying with minimal flags");
                response = tryDownloadWithYtDlp(downloadId, result, callbackId, videoUrl, tempDir, false);
                downloaded = findFirstAudioFile(tempDir);
            }

            if (downloaded == null) {
                throw new IllegalStateException("Ses dosyası oluşturulamadı. " + extractYtDlpError(response));
            }

            sendDownload(downloadId, "active", 99, "Kütüphaneye ekleniyor", result, callbackId, null);
            return saveDownloadedFileToMediaStore(downloaded, result);
        } finally {
            deleteDir(tempDir);
        }
    }

    private YoutubeDLResponse tryDownloadWithYtDlp(String downloadId, JSONObject result, String callbackId, String videoUrl, File tempDir, boolean robustFlags) throws Exception {
        JSONObject settings = readSettings();
        String bitrate = selectedBitrate(settings);

        YoutubeDLRequest request = new YoutubeDLRequest(videoUrl);
        request.addOption("--no-playlist");
        request.addOption("--no-mtime");
        request.addOption("--force-overwrites");
        request.addOption("--newline");
        request.addOption("--progress");
        request.addOption("-f", "bestaudio/best");
        request.addOption("-x");
        request.addOption("--audio-format", "mp3");
        request.addOption("--audio-quality", bitrate);
        request.addOption("-o", new File(tempDir, "track.%(ext)s").getAbsolutePath());

        if (robustFlags) {
            request.addOption("--socket-timeout", "20");
            request.addOption("--retries", "3");
            request.addOption("--fragment-retries", "3");
            request.addOption("--concurrent-fragments", "4");
            request.addOption("--extractor-args", "youtube:player_client=android,web,mweb");
        }

        sendDownload(downloadId, "active", 3, robustFlags ? "yt-dlp hazırlanıyor" : "yt-dlp (uyumlu mod) deneniyor", result, callbackId, null);

        AtomicBoolean finished = new AtomicBoolean(false);
        timers.schedule(() -> {
            if (!finished.get()) {
                Log.w(TAG, "yt-dlp download timed out, killing process " + downloadId);
                YoutubeDL.getInstance().destroyProcessById(downloadId);
            }
        }, 120, TimeUnit.SECONDS);

        try {
            YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, downloadId, true, (progress, eta, line) -> {
                int progressInt = Math.max(3, Math.min(99, Math.round(progress)));
                String message = line == null || line.trim().isEmpty() ? "İndiriliyor" : progressLineToStatus(line);
                sendDownload(downloadId, "active", progressInt, message, result, callbackId, null);
                return Unit.INSTANCE;
            });
            if (response != null && response.getExitCode() != 0) {
                throw new IllegalStateException(extractYtDlpError(response));
            }
            return response;
        } finally {
            finished.set(true);
        }
    }

    private Uri saveDownloadedFileToMediaStore(File source, JSONObject result) throws Exception {
        if (source == null || !source.exists() || source.length() <= 0) {
            throw new IllegalStateException("MP3 dosyası oluşturulamadı.");
        }
        String title = sanitizeFileName(result.optString("title", "Flowna Track"));
        String artist = clean(result.optString("artist"), "Flowna");
        String extension = source.getName().toLowerCase(Locale.ROOT).endsWith(".mp3") ? ".mp3" : ".m4a";
        String mimeType = extension.equals(".mp3") ? "audio/mpeg" : "audio/mp4";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, title + extension);
        values.put(MediaStore.Audio.Media.TITLE, title);
        values.put(MediaStore.Audio.Media.ARTIST, artist);
        values.put(MediaStore.Audio.Media.ALBUM, "Flowna İndirilenler");
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
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

        try (InputStream input = new FileInputStream(source);
             OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Dosya yazılamadı.");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Audio.Media.IS_PENDING, 0);
                activity.getContentResolver().update(uri, done, null, null);
            }
            cacheArtworkForSong(uri, result);
            return uri;
        } catch (Exception error) {
            activity.getContentResolver().delete(uri, null, null);
            throw error;
        }
    }

    private Uri downloadDirectStreamAsMp3(String downloadId, JSONObject result, String callbackId) throws Exception {
        String videoUrl = normalizeVideoUrl(result.optString("videoUrl"), result.optString("id"));
        if (videoUrl.isEmpty()) throw new IllegalStateException("Video bağlantısı bulunamadı.");

        File tempDir = new File(activity.getCacheDir(), "flowna_direct_mp3/" + downloadId);
        deleteDir(tempDir);
        if (!tempDir.mkdirs() && !tempDir.exists()) {
            throw new IllegalStateException("Geçici MP3 klasörü oluşturulamadı.");
        }

        try {
            sendDownload(downloadId, "active", 8, "Ses akışı hazırlanıyor", result, callbackId, null);
            StreamPayload payload = resolveAudioStream(videoUrl);
            File source = new File(tempDir, "source" + payload.extension);
            File mp3 = new File(tempDir, "track.mp3");
            downloadStreamToFile(downloadId, result, payload.url, source, callbackId);
            ensureFfmpeg();
            convertToMp3(downloadId, result, source, mp3, callbackId);
            sendDownload(downloadId, "active", 99, "Kütüphaneye ekleniyor", result, callbackId, null);
            return saveDownloadedFileToMediaStore(mp3, result);
        } finally {
            deleteDir(tempDir);
        }
    }

    private void downloadStreamToFile(String downloadId, JSONObject result, String streamUrl, File target, String callbackId) throws Exception {
        URLConnection connection = new URL(streamUrl).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        long total = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? Math.max(0, connection.getContentLengthLong())
                : Math.max(0, connection.getContentLength());
        long copied = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = connection.getInputStream();
             OutputStream output = new FileOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                copied += read;
                if (total > 0) {
                    int progress = 8 + (int) Math.min(64, (copied * 64) / total);
                    sendDownload(downloadId, "active", progress, "Ses indiriliyor", result, callbackId, null);
                }
            }
        }
        if (!target.exists() || target.length() <= 0) {
            throw new IllegalStateException("Ses akışı indirilemedi.");
        }
    }

    private void convertToMp3(String downloadId, JSONObject result, File source, File mp3, String callbackId) throws Exception {
        File ffmpeg = findFfmpegBinary();
        if (ffmpeg == null || !ffmpeg.exists()) {
            throw new IllegalStateException("FFmpeg bulunamadı.");
        }
        //noinspection ResultOfMethodCallIgnored
        ffmpeg.setExecutable(true);

        String bitrate = selectedBitrate(readSettings()).toLowerCase(Locale.ROOT);
        String title = clean(result.optString("title"), "Flowna Track");
        String artist = clean(result.optString("artist"), "Flowna");
        sendDownload(downloadId, "active", 76, "MP3'e dönüştürülüyor", result, callbackId, null);

        File artworkFile = writeTempArtworkFile(result, source.getParentFile());
        ArrayList<String> command = new ArrayList<>();
        command.add(ffmpeg.getAbsolutePath());
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-i");
        command.add(source.getAbsolutePath());
        if (artworkFile != null && artworkFile.exists()) {
            command.add("-i");
            command.add(artworkFile.getAbsolutePath());
        } else {
            command.add("-vn");
        }
        command.add("-codec:a");
        command.add("libmp3lame");
        command.add("-b:a");
        command.add(bitrate);
        if (artworkFile != null && artworkFile.exists()) {
            command.add("-map");
            command.add("0:a");
            command.add("-map");
            command.add("1:v");
            command.add("-c:v");
            command.add("mjpeg");
            command.add("-id3v2_version");
            command.add("3");
            command.add("-metadata:s:v");
            command.add("title=Album cover");
            command.add("-metadata:s:v");
            command.add("comment=Cover (front)");
        }
        command.add("-metadata");
        command.add("title=" + title);
        command.add("-metadata");
        command.add("artist=" + artist);
        command.add(mp3.getAbsolutePath());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(90);
            while (true) {
                while (reader.ready()) {
                    String line = reader.readLine();
                    if (line != null) output.append(line).append('\n');
                }
                try {
                    int exit = process.exitValue();
                    if (exit != 0) {
                        throw new IllegalStateException("FFmpeg MP3 dönüşümü başarısız: " + friendlyText(output.toString().trim()));
                    }
                    break;
                } catch (IllegalThreadStateException running) {
                    if (System.currentTimeMillis() > deadline) {
                        process.destroy();
                        throw new IllegalStateException("MP3 dönüşümü zaman aşımına uğradı.");
                    }
                    Thread.sleep(160L);
                }
            }
        } finally {
            if (artworkFile != null && artworkFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                artworkFile.delete();
            }
        }
        if (!mp3.exists() || mp3.length() <= 0) {
            throw new IllegalStateException("MP3 dosyası oluşturulamadı.");
        }
    }

    private File writeTempArtworkFile(JSONObject result, File dir) {
        String coverUrl = normalizeThumbnailUrl(result.optString("cover", ""));
        if (coverUrl.isEmpty() || dir == null) return null;
        try {
            byte[] bytes = downloadArtworkBytes(coverUrl);
            if (bytes == null || bytes.length == 0) return null;
            File file = new File(dir, "cover" + artworkFileExtension(bytes));
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(bytes);
            }
            return file;
        } catch (Exception ignored) {
            return null;
        }
    }

    private File findFfmpegBinary() {
        File root = new File(activity.getNoBackupFilesDir(), "youtubedl-android");
        File found = findExecutable(root, "ffmpeg");
        if (found != null) return found;
        return findExecutable(new File(activity.getApplicationInfo().nativeLibraryDir), "ffmpeg");
    }

    private static File findExecutable(File dir, String name) {
        if (dir == null || !dir.exists()) return null;
        if (dir.isFile() && dir.getName().equals(name)) return dir;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && file.getName().equals(name)) return file;
            if (file.isDirectory()) {
                File nested = findExecutable(file, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static File findFirstAudioFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isDirectory()) {
                File nested = findFirstAudioFile(file);
                if (nested != null) return nested;
            } else {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".opus") || name.endsWith(".webm")) {
                    return file;
                }
            }
        }
        return null;
    }

    private static String progressLineToStatus(String line) {
        String lower = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (lower.contains("ffmpeg") || lower.contains("extractaudio") || lower.contains("post-process")) {
            return "Dönüştürülüyor";
        }
        if (lower.contains("destination")) return "Dosya hazırlanıyor";
        if (lower.contains("download")) return "İndiriliyor";
        return "İndiriliyor";
    }

    private static String firstStreamUrl(String output) {
        if (output == null) return "";
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        }
        return "";
    }

    private static String extractYtDlpError(YoutubeDLResponse response) {
        String combined = ((response == null ? "" : response.getErr()) + "\n" + (response == null ? "" : response.getOut())).trim();
        if (combined.isEmpty()) return "yt-dlp sonuç döndürmedi.";
        for (String line : combined.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("[download]")) return friendlyText(trimmed);
        }
        return friendlyText(combined);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String youtubeThumbnail(String videoId) {
        return videoId == null || videoId.isEmpty() ? "" : "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
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
            long total = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? Math.max(0, connection.getContentLengthLong())
                    : Math.max(0, connection.getContentLength());
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
            cacheArtworkForSong(uri, result);
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

    private void ensureYtDlp() throws Exception {
        if (ytDlpReady.get()) return;
        synchronized (ytDlpReady) {
            if (ytDlpReady.get()) return;
            try {
                YoutubeDL.getInstance().init(activity.getApplicationContext());
                ytDlpReady.set(true);
            } catch (Exception error) {
                ytDlpReady.set(false);
                throw error;
            }
        }
    }

    private void ensureFfmpeg() throws Exception {
        if (ffmpegReady.get()) return;
        synchronized (ffmpegReady) {
            if (ffmpegReady.get()) return;
            try {
                FFmpeg.getInstance().init(activity.getApplicationContext());
                ffmpegReady.set(true);
            } catch (Exception error) {
                ffmpegReady.set(false);
                throw error;
            }
        }
    }

    private void maybeUpdateYtDlpAsync() {
        if (downloadRunning.get()) return;
        if (!readSettings().optBoolean("autoUp", true)) return;
        long now = System.currentTimeMillis();
        long last = activity.getSharedPreferences("flowna_settings", Activity.MODE_PRIVATE)
                .getLong("lastYtDlpUpdateCheck", 0L);
        if (now - last < YT_DLP_UPDATE_INTERVAL_MS) return;
        if (!ytDlpUpdateRunning.compareAndSet(false, true)) return;

        activity.getSharedPreferences("flowna_settings", Activity.MODE_PRIVATE)
                .edit()
                .putLong("lastYtDlpUpdateCheck", now)
                .apply();
        io.execute(() -> {
            try {
                if (downloadRunning.get()) return;
                ensureYtDlp();
                YoutubeDL.getInstance().updateYoutubeDL(
                        activity.getApplicationContext(),
                        YoutubeDL.UpdateChannel._STABLE
                    );
                Log.i(TAG, "yt-dlp update check completed");
            } catch (Exception ignored) {
                // Updating yt-dlp is best-effort and must never block search/download.
            } finally {
                ytDlpUpdateRunning.set(false);
            }
        });
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

    private static String selectedBitrate(JSONObject settings) {
        String quality = clean(settings == null ? "" : settings.optString("quality"), "320K")
                .toUpperCase(Locale.ROOT);
        if (!quality.equals("128K") && !quality.equals("192K") && !quality.equals("320K")) {
            quality = "320K";
        }
        return quality;
    }

    private void ensureYtDlpSafe() {
        try {
            ensureYtDlp();
        } catch (Exception error) {
            Log.w(TAG, "yt-dlp init failed: " + friendly(error));
        }
    }

    private void sendPlaybackEvent(String status, JSONObject song) {
        JSONObject event = event("playback");
        put(event, "status", status);
        put(event, "track", song == null ? JSONObject.NULL : song);
        sendEvent(event);
        try {
            boolean playing = "playing".equals(status) || "loading".equals(status);
            if (activity != null) {
                activity.updatePlaybackUi(song, playing, getSafePosition(), getSafeDuration());
            }
        } catch (Exception ignored) {
        }
    }

    private long getSafePosition() {
        try {
            return player != null ? Math.max(0, player.getCurrentPosition()) : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long getSafeDuration() {
        try {
            return player != null ? Math.max(0, player.getDuration()) : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
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

    private void sendYtDlpUpdate(String status, String message) {
        JSONObject event = event("ytDlpUpdate");
        put(event, "status", status);
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
        if (error == null) return "Bilinmeyen hata";
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) return friendlyText(message);
        String summary = throwableSummary(error);
        return summary.isEmpty() ? "Bilinmeyen hata" : summary;
    }

    private static String friendlyText(String message) {
        if (message == null || message.trim().isEmpty()) return "Bilinmeyen hata";
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("failed to initialize")) return "İndirme motoru başlatılamadı.";
        if (lower.contains("instance not initialized")) return "İndirme motoru hazır değil.";
        if (lower.contains("software caused connection abort")) return "Bağlantı sistem tarafından kesildi.";
        if (lower.contains("page needs to be reloaded")) return "YouTube akışı hazırlanamadı, tekrar deneyin.";
        if (lower.contains("timeout")) return "Bağlantı zaman aşımına uğradı.";
        if (lower.contains("sign in") || lower.contains("login")) return "Bu video için YouTube doğrulaması gerekiyor.";
        if (lower.contains("private video")) return "Bu video gizli olduğu için indirilemiyor.";
        if (lower.contains("unsupported url") || lower.contains("invalid url")) return "Video bağlantısı uygun değil.";
        if (lower.contains("permission")) return "Android izni gerekli.";
        return message;
    }

    private static String throwableSummary(Throwable error) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        Throwable cursor = error;
        while (cursor != null && depth < 4) {
            String name = cursor.getClass() == null ? "" : cursor.getClass().getSimpleName();
            String msg = cursor.getMessage() == null ? "" : cursor.getMessage().trim();
            if (!name.isEmpty()) {
                if (out.length() > 0) out.append(" → ");
                out.append(name);
            }
            if (!msg.isEmpty() && (out.length() == 0 || !out.toString().contains(msg))) {
                out.append(": ").append(friendlyText(msg));
            }
            cursor = cursor.getCause();
            depth++;
        }
        return out.toString().trim();
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

    private static String firstJsonValue(String chunk, String key) {
        return firstAfterAny(chunk, "\"" + key + "\":\"", "\\\"" + key + "\\\":\\\"");
    }

    private static String firstAfterAny(String chunk, String... prefixes) {
        for (String prefix : prefixes) {
            int start = chunk.indexOf(prefix);
            if (start < 0) continue;
            start += prefix.length();
            StringBuilder out = new StringBuilder();
            boolean escaped = false;
            for (int i = start; i < chunk.length(); i++) {
                char c = chunk.charAt(i);
                if (!escaped && c == '"') break;
                if (!escaped && c == '\\') {
                    escaped = true;
                    out.append(c);
                    continue;
                }
                escaped = false;
                out.append(c);
            }
            String value = out.toString();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String decodeJsonText(String value) {
        if (value == null) return "";
        String decoded = value
                .replace("\\u0026", "&")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ");
        Matcher matcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(decoded);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = String.valueOf((char) Integer.parseInt(matcher.group(1), 16));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString().trim();
    }

    private static String normalizeThumbnailUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        String normalized = url.replace("\\u0026", "&").replace("\\/", "/");
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        } else if (normalized.startsWith("/vi/")) {
            normalized = "https://i.ytimg.com" + normalized;
        } else if (normalized.startsWith("/")) {
            normalized = "https://www.youtube.com" + normalized;
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex > 0) normalized = normalized.substring(0, queryIndex);
        return normalized;
    }

    private static String extractAssignedJson(String text, String marker) {
        if (text == null || marker == null || marker.isEmpty()) return "";
        int markerIndex = text.indexOf(marker);
        if (markerIndex < 0) return "";
        int startIndex = markerIndex + marker.length();
        while (startIndex < text.length() && Character.isWhitespace(text.charAt(startIndex))) {
            startIndex++;
        }
        if (startIndex >= text.length()) return "";

        char first = text.charAt(startIndex);
        if (first == '"' || first == '\'') {
            String quoted = readQuotedLiteral(text, startIndex);
            return decodeJavascriptStringLiteral(quoted);
        }

        int start = text.indexOf('{', startIndex);
        if (start < 0) return "";

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private static String readQuotedLiteral(String text, int quoteIndex) {
        if (text == null || quoteIndex < 0 || quoteIndex >= text.length()) return "";
        char quote = text.charAt(quoteIndex);
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                out.append('\\').append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == quote) {
                return out.toString();
            }
            out.append(c);
        }
        return "";
    }

    private static String decodeJavascriptStringLiteral(String value) {
        if (value == null || value.isEmpty()) return "";
        String decoded = value
                .replace("\\/", "/")
                .replace("\\&", "&")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ");

        Matcher hexMatcher = Pattern.compile("\\\\x([0-9a-fA-F]{2})").matcher(decoded);
        StringBuffer hexBuffer = new StringBuffer();
        while (hexMatcher.find()) {
            String replacement = String.valueOf((char) Integer.parseInt(hexMatcher.group(1), 16));
            hexMatcher.appendReplacement(hexBuffer, Matcher.quoteReplacement(replacement));
        }
        hexMatcher.appendTail(hexBuffer);

        Matcher unicodeMatcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(hexBuffer.toString());
        StringBuffer unicodeBuffer = new StringBuffer();
        while (unicodeMatcher.find()) {
            String replacement = String.valueOf((char) Integer.parseInt(unicodeMatcher.group(1), 16));
            unicodeMatcher.appendReplacement(unicodeBuffer, Matcher.quoteReplacement(replacement));
        }
        unicodeMatcher.appendTail(unicodeBuffer);
        return unicodeBuffer.toString().trim();
    }

    private static long parseDurationMs(String value) {
        if (value == null || value.isEmpty()) return 0L;
        String[] parts = value.split(":");
        long seconds = 0;
        for (String part : parts) {
            try {
                seconds = seconds * 60 + Long.parseLong(part.trim());
            } catch (Exception ignored) {
                return 0L;
            }
        }
        return seconds * 1000L;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes != null
                && bytes.length > 4
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47;
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes != null
                && bytes.length > 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50;
    }

    private static String imageMimeType(byte[] bytes) {
        if (isPng(bytes)) return "image/png";
        if (isWebp(bytes)) return "image/webp";
        return "image/jpeg";
    }

    private static String artworkFileExtension(byte[] bytes) {
        if (isPng(bytes)) return ".png";
        if (isWebp(bytes)) return ".webp";
        return ".jpg";
    }

    private static String mediaIdArtworkKey(Uri uri) {
        if (uri == null) return "";
        String id = uri.getLastPathSegment();
        if (id == null || id.trim().isEmpty()) return "";
        return "id:" + id.trim();
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
            return info.versionName == null ? "1.0.22" : info.versionName;
        } catch (Exception ignored) {
            return "1.0.22";
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
