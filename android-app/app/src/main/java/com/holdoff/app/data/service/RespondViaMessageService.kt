package com.holdoff.app.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.holdoff.app.data.receiver.NotificationHelper

/**
 * RespondViaMessageService — required component for the default-SMS
 * role: other apps (Assistant, car head units, wearables) hand reply
 * requests to the default messaging app via RESPOND_VIA_MESSAGE.
 *
 * HoldOff's contract (from the foundation privacy rules): NEVER
 * auto-send. The service surfaces a "reply via HoldOff" notification
 * that opens the composer for that conversation, where the normal
 * verdict gate applies to whatever the user writes.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        // URI shapes: smsto:+15551234567, sms:+15551234567, mmsto:...
        val phone = uri?.schemeSpecificPart
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }

        Log.d("HoldOff", "RESPOND_VIA_MESSAGE for ${phone ?: "unknown"}")
        if (phone != null) {
            NotificationHelper.showRespondPrompt(this, phone)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
