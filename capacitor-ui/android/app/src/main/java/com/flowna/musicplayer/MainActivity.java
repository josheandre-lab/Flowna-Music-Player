package com.flowna.musicplayer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    static final int REQUEST_AUDIO_PERMISSION = 4501;
    static final int REQUEST_DELETE_PERMISSION = 4502;
    static final int REQUEST_WRITE_PERMISSION = 4503;

    private FlownaBridge flownaBridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        flownaBridge = new FlownaBridge(this);
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(flownaBridge, "FlownaNative");
        }
    }

    boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION && flownaBridge != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            flownaBridge.notifyPermissionChanged(granted);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (flownaBridge != null) {
            flownaBridge.onActivityResult(requestCode, resultCode);
        }
    }

    @Override
    public void onDestroy() {
        if (flownaBridge != null) {
            flownaBridge.release();
        }
        super.onDestroy();
    }
}
