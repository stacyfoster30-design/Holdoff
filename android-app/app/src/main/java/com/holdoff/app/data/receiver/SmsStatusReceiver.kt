package com.holdoff.app.data.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.holdoff.app.data.repository.DraftRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SmsStatusReceiver — receives the sent/delivered result broadcasts
 * that [com.holdoff.app.data.sms.SmsSender] attaches to each outgoing
 * message, and settles the draft's state machine:
 *
 *   SMS_SENT + RESULT_OK      -> draft SENT (sentAt recorded)
 *   SMS_SENT + any error code -> logged; draft stays READY_TO_SEND so
 *                                the user can retry from the thread
 *   SMS_DELIVERED             -> logged (delivery confirmation is
 *                                carrier-dependent; no state change)
 *
 * The draft id travels in the PendingIntent extras (EXTRA_DRAFT_ID),
 * set by SmsSender at send time.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT = "com.holdoff.app.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.holdoff.app.SMS_DELIVERED"
        const val EXTRA_DRAFT_ID = "draft_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val draftId = intent.getLongExtra(EXTRA_DRAFT_ID, -1L)
        when (intent.action) {
            ACTION_SMS_SENT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d("HoldOff", "SMS sent (draft $draftId)")
                    if (draftId > 0) {
                        val pending = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                DraftRepository(context).markSent(draftId)
                            } finally {
                                pending.finish()
                            }
                        }
                    }
                } else {
                    Log.e("HoldOff", "SMS send failed (draft $draftId, resultCode $resultCode)")
                }
            }
            ACTION_SMS_DELIVERED -> {
                Log.d("HoldOff", "SMS delivered (draft $draftId, resultCode $resultCode)")
            }
        }
    }
}
