package de.cetruo.desktop

import java.util.prefs.Preferences

/**
 * Application preferences with one-time lazy migration from the pre-Cetruo package node.
 */
object AppPreferences {
    private val current: Preferences = Preferences.userRoot().node("/de/cetruo/desktop")
    private val legacy: Preferences = Preferences.userRoot().node("/com/example/cardforge")

    fun get(key: String, defaultValue: String): String {
        current.get(key, null)?.let { return it }
        val oldValue = legacy.get(key, null) ?: return defaultValue
        current.put(key, oldValue)
        return oldValue
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        get(key, defaultValue.toString()).toBooleanStrictOrNull() ?: defaultValue

    fun put(key: String, value: String) {
        current.put(key, value)
    }

    fun putBoolean(key: String, value: Boolean) {
        current.putBoolean(key, value)
    }
}
