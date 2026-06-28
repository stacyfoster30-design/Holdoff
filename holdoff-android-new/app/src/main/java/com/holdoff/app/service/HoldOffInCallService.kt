package com.holdoff.app.service

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.holdoff.app.ui.dialer.InCallActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * HoldOffInCallService — registered as the default InCallService.
 * Manages call state and launches the in-call UI.
 */
class HoldOffInCallService : InCallService() {

    companion object {
        private const val TAG = "HoldOffInCallService"

        // Shared call state observable by the UI
        private val _currentCall = MutableStateFlow<Call?>(null)
        val currentCall: StateFlow<Call?> = _currentCall

        private val _callState = MutableStateFlow(CallState.IDLE)
        val callState: StateFlow<CallState> = _callState
    }

    enum class CallState {
        IDLE, RINGING, DIALING, ACTIVE, HOLDING, DISCONNECTING, DISCONNECTED
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Log.d(TAG, "Call state changed: $state")
            _callState.value = mapCallState(state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                _currentCall.value = null
                _callState.value = CallState.IDLE
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.state}")
        _currentCall.value = call
        _callState.value = mapCallState(call.state)
        call.registerCallback(callCallback)

        // Launch in-call UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
        call.unregisterCallback(callCallback)
        _currentCall.value = null
        _callState.value = CallState.IDLE
    }

    private fun mapCallState(state: Int): CallState = when (state) {
        Call.STATE_RINGING -> CallState.RINGING
        Call.STATE_DIALING, Call.STATE_CONNECTING -> CallState.DIALING
        Call.STATE_ACTIVE -> CallState.ACTIVE
        Call.STATE_HOLDING -> CallState.HOLDING
        Call.STATE_DISCONNECTING -> CallState.DISCONNECTING
        Call.STATE_DISCONNECTED -> CallState.DISCONNECTED
        else -> CallState.IDLE
    }
}
