package com.nekokata.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int PAPER = 0xFFF2EFE6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(WidgetSyncPlugin.class);
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        View decor = window.getDecorView();
        decor.setBackgroundColor(PAPER);
        WidgetStateStore.captureLaunchIntent(this, getIntent());
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            if (getBridge() == null) return;
            WebView webView = getBridge().getWebView();
            if (webView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
            }
        } catch (Exception ignored) {
            // bridge/WebView not ready pager guards still apply in js
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        WidgetStateStore.captureLaunchIntent(this, intent);
    }
}
