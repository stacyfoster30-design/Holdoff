package app.holdoff.polsia;

import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationInterceptorService extends NotificationListenerService {

    private static final String PREFS_NAME = "holdoff_prefs";
    private static final String KEY_ACTIVE_MESSAGING_PACKAGE = "active_messaging_package";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (pkg == null) return;

        if (isMessagingApp(pkg)) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putString(KEY_ACTIVE_MESSAGING_PACKAGE, pkg).apply();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // No action needed
    }

    private boolean isMessagingApp(String pkg) {
        switch (pkg) {
            case "com.whatsapp":
            case "com.whatsapp.w4b":
            case "com.google.android.apps.messaging":
            case "com.android.mms":
            case "com.samsung.android.messaging":
            case "org.thoughtcrime.securesms":
            case "org.telegram.messenger":
            case "com.facebook.orca":
            case "com.instagram.android":
            case "com.snapchat.android":
                return true;
            default:
                return false;
        }
    }
}
