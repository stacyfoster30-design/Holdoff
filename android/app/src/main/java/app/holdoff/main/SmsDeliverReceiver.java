package app.holdoff.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import org.json.JSONArray;
import org.json.JSONObject;

public class SmsDeliverReceiver extends BroadcastReceiver {
    static final String PREFS_NAME = "holdoff_prefs";
    static final String QUEUE_KEY = "native_sms_queue";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) return;

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        StringBuilder body = new StringBuilder();
        String from = null;
        long timestamp = System.currentTimeMillis();

        for (SmsMessage sms : messages) {
            if (sms == null) continue;
            if (from == null) from = sms.getDisplayOriginatingAddress();
            body.append(sms.getMessageBody());
            timestamp = sms.getTimestampMillis();
        }

        if (from == null || body.length() == 0) return;
        enqueueSms(context, from, body.toString(), timestamp, "incoming");
    }

    static void enqueueIncomingSms(Context context, String from, String body, long timestamp) {
        enqueueSms(context, from, body, timestamp, "incoming");
    }

    static void enqueueOutgoingSms(Context context, String to, String body, long timestamp) {
        enqueueSms(context, to, body, timestamp, "outgoing");
    }

    static void enqueueSms(Context context, String address, String body, long timestamp, String direction) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(QUEUE_KEY, "[]");
            JSONArray queue = new JSONArray(raw);
            JSONObject item = new JSONObject();
            if ("outgoing".equals(direction)) {
                item.put("to", address);
            } else {
                item.put("from", address);
            }
            item.put("phoneNumber", address);
            item.put("body", body);
            item.put("timestamp", timestamp);
            item.put("direction", direction);
            queue.put(item);
            prefs.edit().putString(QUEUE_KEY, queue.toString()).apply();
        } catch (Exception ignored) {}
    }
}
