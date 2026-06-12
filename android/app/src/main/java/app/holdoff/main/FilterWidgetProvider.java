package app.holdoff.main;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

public class FilterWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_RECHECK = "app.holdoff.main.ACTION_RECHECK";
    public static final String EXTRA_LAST_VERDICT = "last_verdict";

    public static final String PREFS_NAME = "holdoff_widget_prefs";
    public static final String KEY_WIDGET_SIZE_PREFIX = "widget_size_";
    public static final String KEY_STREAK = "streak_count";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            String widgetSize = getWidgetSize(context, appWidgetId);
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int streak = prefs.getInt(KEY_STREAK, 0);
            String lastVerdict = prefs.getString("last_verdict", null);
            updateWidget(context, appWidgetManager, appWidgetId, lastVerdict, widgetSize, streak);
        }
    }

    public static String getWidgetSize(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_WIDGET_SIZE_PREFIX + appWidgetId, "2x2");
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager,
                                   int appWidgetId, String lastVerdict, String widgetSize, int streak) {
        int layoutId = "4x2".equals(widgetSize) ? R.layout.widget_filter_large : R.layout.widget_filter;
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setData(Uri.parse("holdoff://filter"));
        intent.putExtra(EXTRA_LAST_VERDICT, lastVerdict);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_subtitle, pendingIntent);

        // Dynamic subtitle based on last verdict
        if ("HOLD".equals(lastVerdict) || "REWRITE".equals(lastVerdict)) {
            views.setTextViewText(R.id.widget_subtitle, "Still holding? Tap to recheck");
        } else {
            views.setTextViewText(R.id.widget_subtitle, "Check a text");
        }

        // Streak row — only show when streak > 0
        try {
            int streakRowId = R.id.widget_streak_row;
            int streakCountId = R.id.widget_streak_count;
            if (streak > 0) {
                views.setViewVisibility(streakRowId, View.VISIBLE);
                views.setTextViewText(streakCountId, String.valueOf(streak));
            } else {
                views.setViewVisibility(streakRowId, View.GONE);
            }
        } catch (Exception ignored) { }

        // Large widget verdict summary
        if ("4x2".equals(widgetSize) && lastVerdict != null) {
            views.setViewVisibility(R.id.widget_verdict_summary, View.VISIBLE);
            views.setTextViewText(R.id.widget_verdict_summary, "Last: " + lastVerdict);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // Convenience overload for callers that don't have streak
    public static void updateWidget(Context context, AppWidgetManager appWidgetManager,
                                   int appWidgetId, String lastVerdict, String widgetSize) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int streak = prefs.getInt(KEY_STREAK, 0);
        updateWidget(context, appWidgetManager, appWidgetId, lastVerdict, widgetSize, streak);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_RECHECK.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new android.content.ComponentName(context, FilterWidgetProvider.class)
            );
            String lastVerdict = intent.getStringExtra(EXTRA_LAST_VERDICT);
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int streak = prefs.getInt(KEY_STREAK, 0);
            for (int id : appWidgetIds) {
                String widgetSize = getWidgetSize(context, id);
                updateWidget(context, appWidgetManager, id, lastVerdict, widgetSize, streak);
            }
        }
    }
}