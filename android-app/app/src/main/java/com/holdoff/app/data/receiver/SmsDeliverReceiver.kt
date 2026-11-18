package com.holdoff.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * SmsDeliverReceiver — handles SMS_DELIVER, the broadcast only the
 * DEFAULT SMS app receives. Declared in the manifest with the
 * BROADCAST_SMS permission; one of the components Android requires
 * before HoldOff can hold ROLE_SMS.
 *
 * Each message is written to the Telephony provider and then run
 * through the shared incoming pipeline (notification, trusted-contact
 * lock release, interpreter) by [IncomingSmsProcessor].
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Group PDUs by sender: a long message arrives as several
        // SmsMessage parts sharing one originating address.
        val bySender = messages.groupBy { it.originatingAddress ?: "" }
        for ((from, parts) in bySender) {
            if (from.isBlank()) continue
            val body = parts.joinToString("") { it.messageBody ?: "" }
            if (body.isBlank()) continue
            Log.d("HoldOff", "SMS_DELIVER from $from: ${body.take(40)}")
            IncomingSmsProcessor.process(context, from, body, insertIntoProvider = true)
        }
    }
}
