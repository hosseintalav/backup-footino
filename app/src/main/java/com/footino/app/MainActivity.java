package com.footino.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * MainActivity - اکتیویتی اصلی با همه قابلیت‌های زیر:
 * 1. نمایش splash screen آنی (بدون fade-out در انتها)
 * 2. بارگذاری سایت Footino در WebView در پس‌زمینه حین نمایش splash
 * 3. انتقال آنی از splash به WebView
 * 4. Status bar شفاف
 * 5. Pull-to-Refresh
 * 6. باز کردن لینک‌های خارجی در مرورگر خارجی (با پشتیبانی از target="_blank")
 * 7. درخواست مجوز POST_NOTIFICATIONS در اندروید 13+
 * 8. راه‌اندازی WorkManager برای بررسی دوره‌ای matches.json و notif.json
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String TARGET_URL = "https://hosseintalav.github.io/Footino/";

    private static final long MIN_SPLASH_DURATION_MS = 800;
    private static final long MAX_SPLASH_DURATION_MS = 6000;
    private static final long ICON_ANIM_DURATION_MS = 700;

    private static final String WORK_NAME = "footino_match_sync_work";

    private WebView webView;
    private NestedSwipeRefreshLayout swipeRefresh;
    private LinearLayout errorLayout;
    private Button btnRetry;

    private View splashOverlay;
    private ImageView splashIcon;
    private TextView brandText;

    private long splashStartTime;
    private boolean splashHidden = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.parseColor("#FF191919"));
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        try { window.setWindowAnimations(0); } catch (Exception ignored) {}

        setContentView(R.layout.activity_main);

        initViews();
        setupWebView();
        setupSwipeRefresh();
        setupNotificationPermissionLauncher();

        splashStartTime = System.currentTimeMillis();
        playIconAppearAnimation(splashIcon);
        playBrandTextAppearAnimation(brandText);

        mainHandler.postDelayed(this::hideSplashInstantly, MAX_SPLASH_DURATION_MS);

        if (savedInstanceState == null) {
            loadSite();
        } else {
            webView.restoreState(savedInstanceState);
        }

        initNotificationSystem();
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        errorLayout = findViewById(R.id.error_layout);
        btnRetry = findViewById(R.id.btn_retry);

        splashOverlay = findViewById(R.id.splash_overlay);
        splashIcon = findViewById(R.id.splash_icon);
        brandText = findViewById(R.id.splash_brand_text);

        swipeRefresh.setWebView(webView);
        btnRetry.setOnClickListener(v -> loadSite());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.parseColor("#FF191919"));

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isInternalUrl(url)) return false;
                openExternalUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                errorLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                onWebViewPageFinished();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    if (!isNetworkAvailable()) {
                        showError();
                        onWebViewPageFinished();
                    }
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                final WebView tempWebView = new WebView(MainActivity.this);
                tempWebView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                ));

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(tempWebView);
                resultMsg.sendToTarget();

                tempWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        openExternalUrl(url);
                        cleanupTempWebView(tempWebView);
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                        if (url != null && !url.equals("about:blank")) {
                            openExternalUrl(url);
                            cleanupTempWebView(tempWebView);
                        }
                    }
                });

                return true;
            }
        });
    }

    private void cleanupTempWebView(WebView tempWebView) {
        try {
            if (tempWebView.getParent() instanceof ViewGroup) {
                ((ViewGroup) tempWebView.getParent()).removeView(tempWebView);
            }
            tempWebView.destroy();
        } catch (Exception ignored) {}
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(
            R.color.footino_gold, R.color.white, R.color.footino_gold
        );
        swipeRefresh.setOnRefreshListener(this::loadSite);
    }

    private void loadSite() {
        if (isNetworkAvailable()) {
            errorLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(TARGET_URL);
        } else {
            showError();
            onWebViewPageFinished();
        }
    }

    private boolean isInternalUrl(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null) return false;
        return host.endsWith("github.io")
            && uri.getPath() != null
            && uri.getPath().contains("Footino");
    }

    private void openExternalUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "مرورگری برای باز کردن لینک یافت نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError() {
        webView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(false);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void onWebViewPageFinished() {
        long elapsed = System.currentTimeMillis() - splashStartTime;
        long waitTime = Math.max(0, MIN_SPLASH_DURATION_MS - elapsed);
        mainHandler.postDelayed(this::hideSplashInstantly, waitTime);
    }

    private void hideSplashInstantly() {
        if (splashHidden) return;
        splashHidden = true;
        if (splashOverlay == null) return;

        splashOverlay.setVisibility(View.GONE);
        splashOverlay.setClickable(false);
        splashOverlay.setFocusable(false);
        splashOverlay.invalidate();
    }

    private void playIconAppearAnimation(ImageView iconView) {
        if (iconView == null) return;

        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new AccelerateDecelerateInterpolator());

        ScaleAnimation scaleAnim = new ScaleAnimation(
            0.3f, 1.05f, 0.3f, 1.05f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnim.setDuration(ICON_ANIM_DURATION_MS);
        scaleAnim.setStartOffset(100);

        ScaleAnimation settleAnim = new ScaleAnimation(
            1.05f, 1.0f, 1.05f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        );
        settleAnim.setStartOffset(100 + ICON_ANIM_DURATION_MS);
        settleAnim.setDuration(150);

        AlphaAnimation fadeAnim = new AlphaAnimation(0.0f, 1.0f);
        fadeAnim.setDuration(ICON_ANIM_DURATION_MS);
        fadeAnim.setStartOffset(100);

        TranslateAnimation translateAnim = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0.1f,
            Animation.RELATIVE_TO_SELF, 0f
        );
        translateAnim.setDuration(ICON_ANIM_DURATION_MS);
        translateAnim.setStartOffset(100);

        set.addAnimation(scaleAnim);
        set.addAnimation(fadeAnim);
        set.addAnimation(translateAnim);
        set.addAnimation(settleAnim);

        iconView.setVisibility(View.VISIBLE);
        iconView.startAnimation(set);
    }

    private void playBrandTextAppearAnimation(TextView brandTextView) {
        if (brandTextView == null) return;
        brandTextView.setVisibility(View.VISIBLE);
        brandTextView.setAlpha(0f);
        brandTextView.setScaleX(0.9f);
        brandTextView.setScaleY(0.9f);
        brandTextView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setStartDelay(400)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }

    private void setupNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "مجوز اعلان داده شد");
                    scheduleBackgroundSync();
                    runOneTimeSync();
                } else {
                    Log.w(TAG, "مجوز اعلان رد شد");
                    scheduleBackgroundSync();
                }
            }
        );
    }

    private void initNotificationSystem() {
        new NotificationHelper(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }

        scheduleBackgroundSync();
        runOneTimeSync();
    }

    private void scheduleBackgroundSync() {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
            MatchSyncWorker.class,
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        );

        Log.d(TAG, "WorkManager دوره‌ای برای بررسی JSON ثبت شد (هر 15 دقیقه)");
    }

    private void runOneTimeSync() {
        androidx.work.OneTimeWorkRequest oneTimeWork =
            new androidx.work.OneTimeWorkRequest.Builder(MatchSyncWorker.class)
                .setConstraints(new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build();
        WorkManager.getInstance(this).enqueue(oneTimeWork);
        Log.d(TAG, "Worker یکباره برای sync اولیه ثبت شد");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CookieManager.getInstance().flush();
        runOneTimeSync();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
