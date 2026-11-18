package com.holdoff.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Listens for incoming SMS via SMS_RECEIVED — the broadcast available
 * when HoldOff is NOT the default SMS app (advisory mode). When
 * HoldOff holds the default role, [SmsDeliverReceiver] handles
 * SMS_DELIVER instead and additionally writes to the provider.
 *
 * All per-message work (notification, trusted-contact lock release,
 * interpreter call) lives in [IncomingSmsProcessor] so both modes
 * behave identically.
 */
class SMSReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val bySender = messages.groupBy { it.originatingAddress ?: "" }
        for ((from, parts) in bySender) {
            if (from.isBlank()) continue
            val body = parts.joinToString("") { it.messageBody ?: "" }
            if (body.isBlank()) continue
            Log.d("HoldOff", "Incoming SMS from $from: ${body.take(40)}")
            IncomingSmsProcessor.process(context, from, body, insertIntoProvider = false)
        }
    }
}
