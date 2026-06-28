package com.holdoff.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS handling — required to be declared as default SMS app
        // Full MMS download and display handled via ContentObserver on MMS content URI
        Log.d("MmsReceiver", "MMS received: ${intent.action}")
    }
}
