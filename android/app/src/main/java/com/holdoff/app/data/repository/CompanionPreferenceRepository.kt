package com.holdoff.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.holdoff.app.domain.companion.CompanionCatalog
import com.holdoff.app.domain.companion.CompanionVariant

/**
 * Stores the user's chosen companion variant so the rest of the app
 * (verdict tone, Sadie chat replies, story narration voice) can read it.
 */
class CompanionPreferenceRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "holdoff_companion_prefs",
        Context.MODE_PRIVATE
    )

    fun setSelected(variant: CompanionVariant) {
        prefs.edit().putString(KEY_SELECTED_ID, variant.id).apply()
    }

    fun getSelectedId(): String? = prefs.getString(KEY_SELECTED_ID, null)

    fun getSelected(): CompanionVariant? =
        getSelectedId()?.let { CompanionCatalog.byId(it) }

    fun clear() {
        prefs.edit().remove(KEY_SELECTED_ID).apply()
    }

    companion object {
        private const val KEY_SELECTED_ID = "selected_companion_id"
    }
}
