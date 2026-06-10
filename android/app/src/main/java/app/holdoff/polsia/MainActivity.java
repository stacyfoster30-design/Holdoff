package app.holdoff.polsia;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.role.RoleManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Telephony;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.LayoutInflater;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "holdoff_prefs";
    private static final String KEY_A11Y_PROMPT_SHOWN = "a11y_prompt_shown";
    private static final String KEY_SMS_ROLE_PROMPT_SHOWN = "sms_role_prompt_shown";
    private static final String KEY_LAST_VERDICT = "last_verdict";
    private static final String KEY_WIDGET_AUTO_ADDED = "widget_auto_added";
    private static final int REQUEST_CODE_ADD_WIDGET = 1001;

    private WebView webView;
    private FrameLayout promptOverlay;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());

        // Show accessibility prompt overlay on first launch
        showAccessibilityPromptIfNeeded();

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });

        webView.loadUrl("https://holdoff.polsia.app/filter");

        // Handle deep link from widget
        handleWidgetIntent(getIntent());

        // Bridge: JS calls window.Android.updateWidget(verdict) to update the home widget
        webView.addJavascriptInterface(new WidgetBridge(), "Android");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleWidgetIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showSmsRolePromptIfNeeded();
        showWidgetPromptIfNeeded();
    }

    private void handleWidgetIntent(Intent intent) {
        String lastVerdict = intent.getStringExtra(FilterWidgetProvider.EXTRA_LAST_VERDICT);
        if (lastVerdict != null) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_LAST_VERDICT, lastVerdict).apply();
        }
    }

    private final class WidgetBridge {
        @android.webkit.JavascriptInterface
        public void updateWidget(final String verdict) {
            postOnUiThread(() -> {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString(KEY_LAST_VERDICT, verdict).apply();
                updateHomeWidgetWithStreak(verdict, -1);
            });
        }

        @android.webkit.JavascriptInterface
        public void updateStreak(final int streak) {
            postOnUiThread(() -> {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putInt("streak_count", streak).apply();
                updateHomeWidgetWithStreak(null, streak);
            });
        }
    }

    private void updateHomeWidgetWithStreak(String lastVerdict, int streak) {
        android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(this);
        android.content.ComponentName component = new android.content.ComponentName(this, FilterWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (streak < 0) streak = prefs.getInt("streak_count", 0);
        for (int id : ids) {
            String widgetSize = FilterWidgetProvider.getWidgetSize(this, id);
            FilterWidgetProvider.updateWidget(this, manager, id, lastVerdict, widgetSize, streak);
        }
    }

    private void updateHomeWidget(String lastVerdict) {
        updateHomeWidgetWithStreak(lastVerdict, -1);
    }

    private void showAccessibilityPromptIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean promptShown = prefs.getBoolean(KEY_A11Y_PROMPT_SHOWN, false);
        boolean a11yEnabled = isAccessibilityServiceEnabled();

        // Show prompt if never shown AND service not yet enabled
        if (!promptShown && !a11yEnabled) {
            postOnUiThread(this::inflateAccessibilityPrompt);
        }
    }

    private void showSmsRolePromptIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean smsPromptShown = prefs.getBoolean(KEY_SMS_ROLE_PROMPT_SHOWN, false);

        if (smsPromptShown) return;
        if (!isAccessibilityServiceEnabled()) return;
        if (promptOverlay != null) return;

        postOnUiThread(this::inflateSmsRolePrompt);
    }

    private void showWidgetPromptIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean widgetAutoAdded = prefs.getBoolean(KEY_WIDGET_AUTO_ADDED, false);

        if (widgetAutoAdded) return;
        if (promptOverlay != null) return;

        postOnUiThread(this::inflateWidgetPrompt);
    }

    private void inflateWidgetPrompt() {
        if (promptOverlay != null) return;

        promptOverlay = new FrameLayout(this);
        WindowManager.LayoutParams wrapParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        );

        LayoutInflater.from(this).inflate(R.layout.widget_prompt, promptOverlay, true);

        Button btnAddWidget = promptOverlay.findViewById(R.id.btn_add_widget);
        Button btnSkipWidget = promptOverlay.findViewById(R.id.btn_skip_widget);

        if (btnAddWidget != null) {
            btnAddWidget.setOnClickListener(v -> {
                openWidgetPicker();
                dismissWidgetPrompt();
            });
        }
        if (btnSkipWidget != null) {
            btnSkipWidget.setOnClickListener(v -> dismissWidgetPrompt());
        }

        try {
            getWindowManager().addView(promptOverlay, wrapParams);
        } catch (Exception ignored) {}
    }

    private void dismissWidgetPrompt() {
        if (promptOverlay != null) {
            try {
                getWindowManager().removeView(promptOverlay);
            } catch (Exception ignored) {}
            promptOverlay = null;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_WIDGET_AUTO_ADDED, true).apply();
    }

    private void openWidgetPicker() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        android.content.ComponentName component = new android.content.ComponentName(this, FilterWidgetProvider.class);

        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component);
        startActivityForResult(pickIntent, REQUEST_CODE_ADD_WIDGET);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_WIDGET && resultCode == RESULT_OK && data != null) {
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(KEY_WIDGET_AUTO_ADDED, true).apply();
            }
        }
    }

    private void inflateSmsRolePrompt() {
        if (promptOverlay != null) return;

        promptOverlay = new FrameLayout(this);
        WindowManager.LayoutParams wrapParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        );

        LayoutInflater.from(this).inflate(R.layout.sms_role_prompt, promptOverlay, true);

        Button btnEnable = promptOverlay.findViewById(R.id.btn_enable_sms_role);
        Button btnSkip = promptOverlay.findViewById(R.id.btn_skip_sms_role);

        if (btnEnable != null) {
            btnEnable.setOnClickListener(v -> {
                openSmsRoleSettings();
                dismissSmsPrompt();
            });
        }
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> dismissSmsPrompt());
        }

        try {
            getWindowManager().addView(promptOverlay, wrapParams);
        } catch (Exception ignored) {}
    }

    private void dismissSmsPrompt() {
        if (promptOverlay != null) {
            try {
                getWindowManager().removeView(promptOverlay);
            } catch (Exception ignored) {}
            promptOverlay = null;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_SMS_ROLE_PROMPT_SHOWN, true).apply();
    }

    private void openSmsRoleSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
                startActivity(intent);
                return;
            }
        }

        try {
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        }
    }

    private void inflateAccessibilityPrompt() {
        // Check again before showing (might have been enabled while loading)
        if (isAccessibilityServiceEnabled()) return;

        promptOverlay = new FrameLayout(this);
        WindowManager.LayoutParams wrapParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        );

        LayoutInflater.from(this).inflate(R.layout.accessibility_prompt, promptOverlay, true);

        Button btnEnable = promptOverlay.findViewById(R.id.btn_enable_accessibility);
        Button btnSkip = promptOverlay.findViewById(R.id.btn_skip_accessibility);

        if (btnEnable != null) {
            btnEnable.setOnClickListener(v -> {
                openAccessibilitySettings();
                dismissPrompt();
            });
        }
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> dismissPrompt());
        }

        try {
            getWindowManager().addView(promptOverlay, wrapParams);
        } catch (Exception ignored) {}
    }

    private void dismissPrompt() {
        if (promptOverlay != null) {
            try {
                getWindowManager().removeView(promptOverlay);
            } catch (Exception ignored) {}
            promptOverlay = null;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_A11Y_PROMPT_SHOWN, true).apply();
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName serviceComponent = new ComponentName(this, HoldOffAccessibilityService.class);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            return true;
        }
        // Also check via Settings.Secure
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices != null) {
            return enabledServices.contains(serviceComponent.flattenToString());
        }
        return false;
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void postOnUiThread(Runnable r) {
        handler.post(r);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
