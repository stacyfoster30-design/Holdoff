package com.holdoff.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.holdoff.app.data.model.TrustedContact
import org.json.JSONArray
import org.json.JSONObject

/**
 * TrustedContactsStore — durable storage for trusted contacts.
 *
 * Replaces the previous in-memory-only list on the Trusted Contacts
 * screen (contacts vanished when the user left the screen, and nothing
 * could consume them). Stored as JSON in SharedPreferences; the list is
 * small (product rule: max [MAX_CONTACTS]).
 *
 * Consumers:
 *  - Trusted Contacts screen (add/remove UI)
 *  - Spiral Lock: messaging a trusted contact is never blocked, and a
 *    trusted contact can release a lock by texting the unlock phrase.
 */
object TrustedContactsStore {

    private const val PREFS_NAME = "holdoff_trusted_contacts"
    private const val KEY_CONTACTS = "contacts_json"

    const val MAX_CONTACTS = 2

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getContacts(ctx: Context): List<TrustedContact> {
        val raw = prefs(ctx).getString(KEY_CONTACTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        TrustedContact(
                            name = o.optString("name"),
                            phone = o.optString("phone"),
                            relation = o.optString("relation", "Friend")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveContacts(ctx: Context, contacts: List<TrustedContact>) {
        val arr = JSONArray()
        contacts.take(MAX_CONTACTS).forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("phone", c.phone)
                put("relation", c.relation)
            })
        }
        prefs(ctx).edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    fun addContact(ctx: Context, contact: TrustedContact): List<TrustedContact> {
        val updated = (getContacts(ctx) + contact).take(MAX_CONTACTS)
        saveContacts(ctx, updated)
        return updated
    }

    fun removeContact(ctx: Context, phone: String): List<TrustedContact> {
        val updated = getContacts(ctx).filterNot { samePhone(it.phone, phone) }
        saveContacts(ctx, updated)
        return updated
    }

    /** True when [phone] belongs to a trusted contact (loose digit match). */
    fun isTrusted(ctx: Context, phone: String): Boolean =
        getContacts(ctx).any { samePhone(it.phone, phone) }

    /**
     * Phone comparison tolerant of formatting and country codes:
     * compares the last 10 digits when both sides have at least 10.
     */
    fun samePhone(a: String, b: String): Boolean {
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        return if (da.length >= 10 && db.length >= 10) da.takeLast(10) == db.takeLast(10)
        else da == db
    }
}
