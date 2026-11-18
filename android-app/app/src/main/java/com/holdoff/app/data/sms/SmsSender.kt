package com.holdoff.app.data.sms

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.holdoff.app.data.receiver.SmsStatusReceiver

/**
 * SmsSender — the ONLY path by which HoldOff sends an SMS.
 *
 * Rules (foundation contract + blueprint Phase 1):
 *  - Role-gated: refuses unless HoldOff is the default SMS app. In
 *    advisory (non-default) mode HoldOff does not send at all.
 *  - Permission-gated: SEND_SMS must be granted.
 *  - User-initiated only: callers invoke this after the verdict gate
 *    has cleared the message (green, or a user-confirmed yellow).
 *    Nothing in this class sends automatically.
 *  - Every send carries sent/delivered PendingIntents back to
 *    [SmsStatusReceiver] with the draft id, so the draft state machine
 *    settles from Android's real result — never assumed.
 *  - The default app must record its own sent messages in the
 *    Telephony provider, so the sent copy is inserted into
 *    content://sms/sent for the thread UI to read.
 */
object SmsSender {

    sealed class SendResult {
        data class Queued(val draftId: Long) : SendResult()
        data class Failed(val reason: String) : SendResult()
    }

    fun canSend(context: Context): Boolean =
        DefaultSmsManager.isDefaultSmsApp(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Sends [body] to [phone] on behalf of draft [draftId].
     * Returns Queued once Android has accepted the message for sending;
     * final success arrives asynchronously via SmsStatusReceiver.
     */
    fun send(context: Context, draftId: Long, phone: String, body: String): SendResult {
        if (!DefaultSmsManager.isDefaultSmsApp(context)) {
            return SendResult.Failed("HoldOff isn't your default texting app yet — sending stays off until it is.")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return SendResult.Failed("Texting permission isn't granted yet.")
        }
        if (phone.isBlank() || body.isBlank()) {
            return SendResult.Failed("Nothing to send.")
        }

        return try {
            val smsManager = smsManager(context)
            val sentIntent = statusPendingIntent(context, SmsStatusReceiver.ACTION_SMS_SENT, draftId)
            val deliveredIntent = statusPendingIntent(context, SmsStatusReceiver.ACTION_SMS_DELIVERED, draftId)

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>(List(parts.size) { sentIntent })
                val deliveredIntents = ArrayList<PendingIntent>(List(parts.size) { deliveredIntent })
                smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, deliveredIntents)
            } else {
                smsManager.sendTextMessage(phone, null, body, sentIntent, deliveredIntent)
            }

            insertIntoSent(context, phone, body)
            SendResult.Queued(draftId)
        } catch (e: Exception) {
            Log.e("HoldOff", "SmsSender failed: ${e.message}")
            SendResult.Failed(e.message ?: "Couldn't send.")
        }
    }

    private fun smsManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun statusPendingIntent(context: Context, action: String, draftId: Long): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(SmsStatusReceiver.EXTRA_DRAFT_ID, draftId)
        }
        // Distinct request codes per draft+action so concurrent sends
        // don't overwrite each other's callbacks.
        val requestCode = (draftId * 31 + action.hashCode()).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The default SMS app records its own sent messages in the provider. */
    private fun insertIntoSent(context: Context, phone: String, body: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, phone)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("HoldOff", "Failed to record sent SMS in provider: ${e.message}")
        }
    }
}
