package com.holdoff.app.data.receiver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.holdoff.app.data.network.HoldOffApi
import com.holdoff.app.data.prefs.TrustedContactsStore
import com.holdoff.app.data.spiral.SpiralLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * IncomingSmsProcessor — the single path every incoming SMS flows
 * through, whether it arrived via SMS_RECEIVED (HoldOff is NOT the
 * default app; advisory mode) or SMS_DELIVER (HoldOff IS the default
 * app; the message must also be written to the Telephony provider,
 * which is the default app's responsibility).
 *
 * Per message:
 *  1. (Default mode only) Insert into content://sms/inbox so the
 *     thread exists in the provider the inbox UI reads.
 *  2. If Spiral Lock is active and the sender is a trusted contact
 *     texting the unlock phrase, release the lock and say so.
 *  3. Post the "Sadie has a read on this" notification.
 *  4. Kick off /api/interpreter with thread context (unchanged
 *     pre-existing behavior).
 */
object IncomingSmsProcessor {

    fun process(context: Context, from: String, body: String, insertIntoProvider: Boolean) {
        if (insertIntoProvider) insertIntoInbox(context, from, body)

        val contactName = resolveContactName(context, from)

        // Trusted-contact Spiral Lock release. Both conditions are
        // required: the sender is a persisted trusted contact AND the
        // message carries the unlock phrase.
        if (SpiralLockManager.isLocked(context)
            && TrustedContactsStore.isTrusted(context, from)
            && SpiralLockManager.isUnlockPhrase(body)
        ) {
            SpiralLockManager.release(context, SpiralLockManager.ReleaseReason.TRUSTED_CONTACT)
            NotificationHelper.showLockReleased(context, contactName)
            Log.d("HoldOff", "Spiral Lock released by trusted contact $from")
            return
        }

        NotificationHelper.showIncomingAlert(context, contactName, from)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val threadHistory = readThreadHistory(context, from, 20)
                HoldOffApi.interpretMessage(context, body, from, threadHistory)
            } catch (e: Exception) {
                Log.e("HoldOff", "IncomingSmsProcessor interpret failed: ${e.message}")
            }
        }
    }

    /**
     * Default SMS apps must write incoming messages to the provider
     * themselves — nothing else will. Without this insert the message
     * would vanish: received, analyzed, and nowhere in any inbox.
     */
    private fun insertIntoInbox(context: Context, from: String, body: String) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, from)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("HoldOff", "Failed to insert incoming SMS into provider: ${e.message}")
        }
    }

    private fun resolveContactName(context: Context, phone: String): String {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: phone
        }
        return phone
    }

    private fun readThreadHistory(
        context: Context,
        phone: String,
        limit: Int
    ): List<Map<String, Any>> {
        val history = mutableListOf<Map<String, Any>>()
        context.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf("body", "date", "type"),
            "address = ?",
            arrayOf(phone),
            "date ASC LIMIT $limit"
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(c.getColumnIndexOrThrow("type"))
                history.add(mapOf(
                    "direction" to if (type == 2) "sent" else "received",
                    "body"      to (c.getString(c.getColumnIndexOrThrow("body")) ?: ""),
                    "timestamp" to c.getLong(c.getColumnIndexOrThrow("date"))
                ))
            }
        }
        return history
    }
}
