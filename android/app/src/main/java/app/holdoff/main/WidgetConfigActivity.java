package app.holdoff.main;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class WidgetConfigActivity extends Activity {

    public static final String PREFS_NAME = "holdoff_widget_prefs";
    public static final String KEY_WIDGET_SIZE_PREFIX = "widget_size_";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the result to canceled in case the user backs out
        setResult(RESULT_CANCELED);

        // Get the appWidgetId from the intent
        Intent intent = getIntent();
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_widget_config);

        RadioGroup sizeGroup = findViewById(R.id.widget_size_group);
        RadioButton radio2x2 = findViewById(R.id.radio_2x2);
        Button btnOk = findViewById(R.id.btn_ok);
        Button btnCancel = findViewById(R.id.btn_cancel);

        // Load saved preference if exists
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedSize = prefs.getString(KEY_WIDGET_SIZE_PREFIX + appWidgetId, "2x2");
        if ("4x2".equals(savedSize)) {
            radio2x2.setChecked(false);
            sizeGroup.check(R.id.radio_4x2);
        } else {
            sizeGroup.check(R.id.radio_2x2);
        }

        btnOk.setOnClickListener(v -> {
            String widgetSize = sizeGroup.getCheckedRadioButtonId() == R.id.radio_4x2 ? "4x2" : "2x2";

            // Save the widget size preference
            prefs.edit().putString(KEY_WIDGET_SIZE_PREFIX + appWidgetId, widgetSize).apply();

            // Tell the widget to update with the selected size
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            FilterWidgetProvider.updateWidget(this, appWidgetManager, appWidgetId, null, widgetSize);

            // Return success with the appWidgetId
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });

        btnCancel.setOnClickListener(v -> finish());
    }
}