package com.jarvis.ai.v4.notifications

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.ai.v4.data.EncryptedPreferenceStore
import java.time.LocalTime
import java.util.Locale

class NotificationAnnouncementPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val encryptedPreferences = EncryptedPreferenceStore(
        context,
        SENSITIVE_PREFERENCES,
        SENSITIVE_KEY_ALIAS,
    )

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) { preferences.edit().putBoolean(KEY_ENABLED, value).apply() }

    var includeMessagePreview: Boolean
        get() = preferences.getBoolean(KEY_PREVIEW, false)
        set(value) { preferences.edit().putBoolean(KEY_PREVIEW, value).apply() }

    var announceVoiceMessages: Boolean
        get() = preferences.getBoolean(KEY_VOICE_MESSAGES, true)
        set(value) { preferences.edit().putBoolean(KEY_VOICE_MESSAGES, value).apply() }

    var announceCalls: Boolean
        get() = preferences.getBoolean(KEY_CALLS, true)
        set(value) { preferences.edit().putBoolean(KEY_CALLS, value).apply() }

    var quietHoursEnabled: Boolean
        get() = preferences.getBoolean(KEY_QUIET_ENABLED, false)
        set(value) { preferences.edit().putBoolean(KEY_QUIET_ENABLED, value).apply() }

    var quietStart: String
        get() = preferences.getString(KEY_QUIET_START, "22:00") ?: "22:00"
        set(value) { preferences.edit().putString(KEY_QUIET_START, value).apply() }

    var quietEnd: String
        get() = preferences.getString(KEY_QUIET_END, "07:00") ?: "07:00"
        set(value) { preferences.edit().putString(KEY_QUIET_END, value).apply() }

    var language: String
        get() = preferences.getString(KEY_LANGUAGE, "en-US") ?: "en-US"
        set(value) { preferences.edit().putString(KEY_LANGUAGE, value).apply() }

    var mutedSenders: Set<String>
        get() {
            val encrypted = encryptedPreferences.read(KEY_MUTED_SENDERS)
            if (encrypted == null) {
                return if (encryptedPreferences.contains(KEY_MUTED_SENDERS)) setOf(MUTE_ALL_SENTINEL) else emptySet()
            }
            return encrypted.split('\n').filter(String::isNotBlank).toSet()
        }
        set(value) {
            encryptedPreferences.write(
                KEY_MUTED_SENDERS,
                value.map { it.trim().lowercase(Locale.ROOT) }
                    .filter(String::isNotEmpty)
                    .sorted()
                    .joinToString("\n"),
            )
        }

    val mutedSenderFiltersUnreadable: Boolean
        get() = encryptedPreferences.contains(KEY_MUTED_SENDERS) &&
            encryptedPreferences.read(KEY_MUTED_SENDERS) == null

    fun isQuietNow(now: LocalTime = LocalTime.now()): Boolean {
        if (!quietHoursEnabled) return false
        return QuietHours.isActive(quietStart, quietEnd, now)
    }

    fun isSenderMuted(sender: String): Boolean =
        MUTE_ALL_SENTINEL in mutedSenders || sender.lowercase(Locale.ROOT) in mutedSenders

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    internal companion object {
        const val PREFERENCES = "jarvis_notification_announcements"
        const val SENSITIVE_PREFERENCES = "jarvis_notification_private"
        const val SENSITIVE_KEY_ALIAS = "jarvis-v4-notification-filters"
        const val MUTE_ALL_SENTINEL = "*"
        const val KEY_ENABLED = "enabled"
        const val KEY_PREVIEW = "include_preview"
        const val KEY_VOICE_MESSAGES = "announce_voice_messages"
        const val KEY_CALLS = "announce_calls"
        const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_START = "quiet_start"
        const val KEY_QUIET_END = "quiet_end"
        const val KEY_LANGUAGE = "language"
        const val KEY_MUTED_SENDERS = "muted_senders"
    }
}

object QuietHours {
    fun isActive(startValue: String, endValue: String, now: LocalTime): Boolean {
        val start = parseTime(startValue) ?: return false
        val end = parseTime(endValue) ?: return false
        val current = now.toSecondOfDay() / 60
        val startMinute = start.toSecondOfDay() / 60
        val endMinute = end.toSecondOfDay() / 60
        if (startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            current >= startMinute && current < endMinute
        } else {
            current >= startMinute || current < endMinute
        }
    }

    private fun parseTime(value: String): LocalTime? =
        runCatching { LocalTime.parse(value) }.getOrNull()
}
