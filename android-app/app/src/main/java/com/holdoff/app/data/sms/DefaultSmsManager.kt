package com.holdoff.app.data.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * DefaultSmsManager — everything about HoldOff's default-SMS role.
 *
 * HoldOff can only physically hold a message before it leaves if HoldOff
 * IS the phone's texting app. That requires the ROLE_SMS role, which the
 * system grants only through its own chooser UI — the app can ask, never
 * take. Following the foundation privacy contract:
 *   - The role is requested ONLY from an explicit user tap (onboarding
 *     Full setup, or Settings) — never automatically, never at launch.
 *   - Sending stays role-gated: [com.holdoff.app.data.sms.SmsSender]
 *     refuses to send when HoldOff is not the default SMS app.
 *   - The user can decline and keep their current messaging app; Limited
 *     (manual) mode keeps working without the role.
 */
object DefaultSmsManager {

    /** True when HoldOff currently holds the default-SMS role. */
    fun isDefaultSmsApp(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm != null && rm.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    /**
     * True when the system will even consider HoldOff for the role
     * (manifest declares the required SMS_DELIVER receiver, SENDTO
     * activity, RESPOND_VIA_MESSAGE service, and MMS receiver).
     */
    fun isRoleAvailable(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)
        } else {
            true // pre-Q: the system settings screen decides
        }
    }

    /**
     * Builds the intent that asks the system to make HoldOff the default
     * SMS app. Launch it from an ActivityResultLauncher; the result code
     * tells whether the user accepted (re-check [isDefaultSmsApp] either
     * way — the chooser is the sole authority).
     *
     * API 29+: RoleManager.createRequestRoleIntent(ROLE_SMS).
     * API 26–28: Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT with the
     * EXTRA_PACKAGE_NAME extra.
     */
    fun createRoleRequestIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }
}
