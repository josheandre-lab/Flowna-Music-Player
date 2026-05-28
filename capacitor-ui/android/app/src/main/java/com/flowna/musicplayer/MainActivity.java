package com.flowna.musicplayer;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.WebView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
    static final int REQUEST_AUDIO_PERMISSION = 4501;
    static final int REQUEST_DELETE_PERMISSION = 4502;
    static final int REQUEST_WRITE_PERMISSION = 4503;
    static final int REQUEST_NOTIFICATION_PERMISSION = 4504;
    static final int REQUEST_MANAGE_STORAGE_PERMISSION = 4505;

    private FlownaBridge flownaBridge;
    private MediaSessionCompat mediaSession;
    private NotificationManagerCompat notificationManager;
    private boolean manageStorageRequestInFlight = false;
    private static final String PLAYBACK_CHANNEL_ID = "flowna_playback";
    private static final int PLAYBACK_NOTIFICATION_ID = 9901;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen splashScreen =
                androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        long nativeSplashStartedAt = System.currentTimeMillis();
        splashScreen.setKeepOnScreenCondition(
                () -> System.currentTimeMillis() - nativeSplashStartedAt < 900
        );
        super.onCreate(savedInstanceState);
        setupPlaybackSession();
        flownaBridge = new FlownaBridge(this);
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(flownaBridge, "FlownaNative");
            setupSafeAreaInsets(getBridge().getWebView());
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().evaluateJavascript(
                            "window.handleAndroidBack&&window.handleAndroidBack();",
                            null
                    );
                }
            }
        });
    }

    private void setupSafeAreaInsets(WebView webView) {
        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, insets) -> {
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            float density = Math.max(1f, getResources().getDisplayMetrics().density);
            int bottomCssPx = Math.round(navigationBars.bottom / density);
            applySafeBottom(webView, bottomCssPx);
            return insets;
        });
        ViewCompat.requestApplyInsets(webView);
    }

    private void applySafeBottom(WebView webView, int bottomCssPx) {
        String script = "document.documentElement.style.setProperty('--android-safe-bottom','"
                + Math.max(0, bottomCssPx)
                + "px');";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private void setupPlaybackSession() {
        notificationManager = NotificationManagerCompat.from(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    PLAYBACK_CHANNEL_ID,
                    "Flowna Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Çalan parça bildirimleri");
            NotificationManager sys = getSystemService(NotificationManager.class);
            if (sys != null) sys.createNotificationChannel(channel);
        }
        mediaSession = new MediaSessionCompat(this, "FlownaMediaSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (flownaBridge != null) flownaBridge.handleMediaCommand("play");
            }

            @Override
            public void onPause() {
                if (flownaBridge != null) flownaBridge.handleMediaCommand("pause");
            }

            @Override
            public void onSkipToNext() {
                if (flownaBridge != null) flownaBridge.handleMediaCommand("next");
            }

            @Override
            public void onSkipToPrevious() {
                if (flownaBridge != null) flownaBridge.handleMediaCommand("previous");
            }

            @Override
            public void onSeekTo(long pos) {
                if (flownaBridge != null) flownaBridge.handleMediaSeek(pos);
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                KeyEvent event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null || event.getAction() != KeyEvent.ACTION_UP || flownaBridge == null) {
                    return super.onMediaButtonEvent(mediaButtonEvent);
                }
                int code = event.getKeyCode();
                if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    flownaBridge.handleMediaCommand("toggle");
                    return true;
                }
                if (code == KeyEvent.KEYCODE_MEDIA_PLAY) {
                    flownaBridge.handleMediaCommand("play");
                    return true;
                }
                if (code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    flownaBridge.handleMediaCommand("pause");
                    return true;
                }
                if (code == KeyEvent.KEYCODE_MEDIA_NEXT) {
                    flownaBridge.handleMediaCommand("next");
                    return true;
                }
                if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                    flownaBridge.handleMediaCommand("previous");
                    return true;
                }
                return super.onMediaButtonEvent(mediaButtonEvent);
            }
        });
        mediaSession.setActive(true);
    }

    void updatePlaybackUi(JSONObject track, boolean playing, long positionMs, long durationMs) {
        if (mediaSession == null || notificationManager == null) return;

        String title = track == null ? "Flowna" : track.optString("title", "Flowna");
        String artist = track == null ? "" : track.optString("artist", "");
        Bitmap artwork = decodeArtwork(track);
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Math.max(0, durationMs));
        if (artwork != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artwork);
        }
        mediaSession.setMetadata(metadataBuilder.build());

        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setState(state, Math.max(0, positionMs), 1.0f)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                                | PlaybackStateCompat.ACTION_PLAY
                                | PlaybackStateCompat.ACTION_PAUSE
                                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                                | PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build();
        mediaSession.setPlaybackState(playbackState);

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(playing)
                .addAction(android.R.drawable.ic_media_previous, "Önceki",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Duraklat" : "Oynat",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE))
                .addAction(android.R.drawable.ic_media_next, "Sonraki",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                );
        if (artwork != null) builder.setLargeIcon(artwork);

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            notificationManager.notify(PLAYBACK_NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Bildirim izni kapalıysa oynatma devam eder; sadece kilit ekranı bildirimi gösterilmez.
        }
    }

    void clearPlaybackUi() {
        if (notificationManager != null) notificationManager.cancel(PLAYBACK_NOTIFICATION_ID);
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }

    private Bitmap decodeArtwork(JSONObject track) {
        if (track != null) {
            String cover = track.optString("cover", "");
            int comma = cover.indexOf(',');
            if (cover.startsWith("data:image") && comma > 0) {
                try {
                    byte[] bytes = Base64.decode(cover.substring(comma + 1), Base64.DEFAULT);
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } catch (Exception ignored) {
                }
            }
        }
        return BitmapFactory.decodeResource(getResources(), getApplicationInfo().icon);
    }

    boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    boolean hasManageStoragePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    void requestAudioPermission() {
        if (hasAudioPermission()) {
            if (flownaBridge != null) flownaBridge.notifyPermissionChanged(true);
            return;
        }
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_AUDIO_PERMISSION);
    }

    void requestNotificationPermission() {
        if (hasNotificationPermission()) return;
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION
            );
        }
    }

    void requestManageStoragePermission() {
        if (hasManageStoragePermission()) {
            if (flownaBridge != null) flownaBridge.notifyStoragePermissionChanged(true);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manageStorageRequestInFlight = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION);
            } catch (Exception ignored) {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_MANAGE_STORAGE_PERMISSION);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (manageStorageRequestInFlight && flownaBridge != null) {
            boolean granted = hasManageStoragePermission();
            flownaBridge.notifyStoragePermissionChanged(granted);
            manageStorageRequestInFlight = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION && flownaBridge != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            flownaBridge.notifyPermissionChanged(granted);
        }
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION && flownaBridge != null) {
            flownaBridge.notifyNotificationPermissionChanged(hasNotificationPermission());
        }
        if (requestCode == REQUEST_MANAGE_STORAGE_PERMISSION && flownaBridge != null) {
            flownaBridge.notifyStoragePermissionChanged(hasManageStoragePermission());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE_PERMISSION && flownaBridge != null) {
            flownaBridge.notifyStoragePermissionChanged(hasManageStoragePermission());
            manageStorageRequestInFlight = false;
            return;
        }
        if (flownaBridge != null) {
            flownaBridge.onActivityResult(requestCode, resultCode);
        }
    }

    @Override
    public void onDestroy() {
        if (flownaBridge != null) {
            flownaBridge.release();
        }
        clearPlaybackUi();
        super.onDestroy();
    }
}
