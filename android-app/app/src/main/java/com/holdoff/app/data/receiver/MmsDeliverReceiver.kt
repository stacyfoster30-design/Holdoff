package com.holdoff.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * MmsDeliverReceiver — handles WAP_PUSH_DELIVER for MMS.
 *
 * Android requires a default-SMS candidate to declare an MMS receive
 * path. PHASE 1 LIMITATION (honest): this receiver acknowledges the
 * push and alerts the user, but full MMS PDU parsing and provider
 * insertion are NOT implemented in this build — an incoming MMS will
 * notify but its content will not appear in the thread. Full MMS
 * receive/store is tracked as remaining Phase 1 work; SMS (the
 * product's intervention surface) is fully handled.
 */
class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val contentType = intent.type ?: ""
        if (!contentType.contains("application/vnd.wap.mms-message")) return
        Log.d("HoldOff", "WAP_PUSH_DELIVER (MMS) received — full MMS handling not yet implemented")
        NotificationHelper.showMmsAlert(context)
    }
}
