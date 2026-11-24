package com.jarvis.ai.v4.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.Locale

class WhatsAppNotificationListener : NotificationListenerService() {
    private lateinit var preferences: NotificationAnnouncementPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recentAnnouncements = LinkedHashMap<String, Long>()
    private val pendingAnnouncements = ArrayDeque<WhatsAppAnnouncement>()
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val shouldStopSpeech =
            key == NotificationAnnouncementPreferences.KEY_ENABLED && !preferences.enabled ||
                key == NotificationAnnouncementPreferences.KEY_PREVIEW && !preferences.includeMessagePreview ||
                key == NotificationAnnouncementPreferences.KEY_CALLS && !preferences.announceCalls ||
                key == NotificationAnnouncementPreferences.KEY_VOICE_MESSAGES && !preferences.announceVoiceMessages
        if (shouldStopSpeech) {
            mainHandler.post {
                textToSpeech?.stop()
                pendingAnnouncements.clear()
            }
        }
    }
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        preferences = NotificationAnnouncementPreferences(this)
        preferences.registerChangeListener(preferenceChangeListener)
    }

    private fun initializeTextToSpeech() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val tts = textToSpeech ?: return@TextToSpeech
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            ttsReady = true
            while (pendingAnnouncements.isNotEmpty()) speak(pendingAnnouncements.removeFirst())
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!preferences.enabled || preferences.isQuietNow()) return

        val notification = sbn.notification
        val extras = notification.extras
        val announcement = WhatsAppAnnouncementParser.parse(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            category = notification.category,
            language = preferences.language,
            includePreview = preferences.includeMessagePreview,
        ) ?: return

        if (preferences.isSenderMuted(announcement.sender)) return
        if (!preferences.announceVoiceMessages && announcement.kind == WhatsAppNotificationKind.VOICE_MESSAGE) return
        if (!preferences.announceCalls && announcement.kind in CALL_KINDS) return

        val identity = listOf(
            sbn.key,
            announcement.kind.name,
            announcement.sender,
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
        ).joinToString("\u0000")
        val deduplicationKey = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        val now = System.currentTimeMillis()
        synchronized(recentAnnouncements) {
            recentAnnouncements.entries.removeAll { now - it.value > DEDUPLICATION_WINDOW_MS }
            if (recentAnnouncements.containsKey(deduplicationKey)) return
            recentAnnouncements[deduplicationKey] = now
            while (recentAnnouncements.size > MAX_RECENT_ANNOUNCEMENTS) {
                recentAnnouncements.remove(recentAnnouncements.keys.first())
            }
        }

        mainHandler.post {
            initializeTextToSpeech()
            if (ttsReady) {
                speak(announcement)
            } else if (pendingAnnouncements.size < MAX_PENDING_ANNOUNCEMENTS) {
                pendingAnnouncements.addLast(announcement)
            }
        }
    }

    override fun onDestroy() {
        preferences.unregisterChangeListener(preferenceChangeListener)
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
        super.onDestroy()
    }

    private fun speak(announcement: WhatsAppAnnouncement) {
        if (!preferences.enabled || preferences.isQuietNow()) return
        if (preferences.isSenderMuted(announcement.sender)) return
        if (!preferences.announceVoiceMessages && announcement.kind == WhatsAppNotificationKind.VOICE_MESSAGE) return
        if (!preferences.announceCalls && announcement.kind in CALL_KINDS) return

        val tts = textToSpeech ?: return
        val requestedLocale = Locale.forLanguageTag(preferences.language)
        val languageStatus = tts.setLanguage(requestedLocale)
        val phrase = if (
            languageStatus == TextToSpeech.LANG_MISSING_DATA ||
            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.setLanguage(Locale.US)
            WhatsAppAnnouncementParser.englishFallback(announcement, preferences.includeMessagePreview)
        } else {
            WhatsAppAnnouncementParser.localized(
                announcement,
                preferences.language,
                preferences.includeMessagePreview,
            )
        }
        tts.speak(phrase, TextToSpeech.QUEUE_ADD, null, "jarvis-whatsapp-${System.nanoTime()}")
    }

    companion object {
        private const val DEDUPLICATION_WINDOW_MS = 60_000L
        private const val MAX_RECENT_ANNOUNCEMENTS = 128
        private const val MAX_PENDING_ANNOUNCEMENTS = 8
        private val CALL_KINDS = setOf(
            WhatsAppNotificationKind.AUDIO_CALL,
            WhatsAppNotificationKind.VIDEO_CALL,
            WhatsAppNotificationKind.CALL,
            WhatsAppNotificationKind.MISSED_CALL,
        )

        fun hasNotificationAccess(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val component = ComponentName(context, WhatsAppNotificationListener::class.java)
            return enabledListeners.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
