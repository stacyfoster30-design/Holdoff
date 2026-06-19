package app.holdoff.main;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.telephony.SmsManager;

public class RespondViaMessageService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getData() != null) {
            String message = intent.getStringExtra(Intent.EXTRA_TEXT);
            String phone = extractPhone(intent.getData());
            if (phone != null && message != null && !message.trim().isEmpty()) {
                SmsManager.getDefault().sendTextMessage(phone, null, message.trim(), null, null);
                SmsDeliverReceiver.enqueueIncomingSms(this, phone, message.trim(), System.currentTimeMillis());
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private String extractPhone(Uri uri) {
        if (uri == null) return null;
        String s = uri.toString();
        int idx = s.indexOf(':');
        if (idx >= 0 && idx + 1 < s.length()) return s.substring(idx + 1);
        return null;
    }
}
