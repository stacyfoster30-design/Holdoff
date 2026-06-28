package com.holdoff.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted — HoldOff ready")
            // Notification channels are created in HoldOffApplication.onCreate()
            // which runs on first app launch after boot. Nothing extra needed here.
        }
    }
}
