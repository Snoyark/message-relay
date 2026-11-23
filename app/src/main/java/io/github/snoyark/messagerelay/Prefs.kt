package io.github.snoyark.messagerelay

import android.content.Context
import android.content.SharedPreferences

// The webhook address, key, chosen apps, and how sending is going.
class Prefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("relay", Context.MODE_PRIVATE)

    var address: String
        get() = p.getString("address", "") ?: ""
        set(value) = p.edit().putString("address", value.trim()).apply()

    var key: String
        get() = p.getString("key", "") ?: ""
        set(value) = p.edit().putString("key", value.trim()).apply()

    var apps: Set<String>
        get() = p.getStringSet("apps", emptySet())?.toSet() ?: emptySet()
        set(value) = p.edit().putStringSet("apps", value).apply()

    var lastSentAt: Long
        get() = p.getLong("lastSentAt", 0)
        set(value) = p.edit().putLong("lastSentAt", value).apply()

    var lastError: String
        get() = p.getString("lastError", "") ?: ""
        set(value) = p.edit().putString("lastError", value).apply()

    // Agreed to the explanation of what's read and where it goes (shown before notification access).
    var consented: Boolean
        get() = p.getBoolean("consented", false)
        set(value) = p.edit().putBoolean("consented", value).apply()

    val configured: Boolean get() = address.isNotEmpty() && key.isNotEmpty()

    fun onChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(listener)
    fun offChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) = p.unregisterOnSharedPreferenceChangeListener(listener)
}
