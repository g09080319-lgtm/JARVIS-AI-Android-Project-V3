package com.jarvis.ai.v4.notifications

import java.util.Locale

enum class WhatsAppNotificationKind {
    MESSAGE,
    VOICE_MESSAGE,
    AUDIO_CALL,
    VIDEO_CALL,
    CALL,
    MISSED_CALL,
}

data class WhatsAppAnnouncement(
    val kind: WhatsAppNotificationKind,
    val sender: String,
    val preview: String?,
    val spokenText: String,
)

object WhatsAppAnnouncementParser {
    private const val UNAVAILABLE = "information unavailable"
    private val genericTitles = listOf(
        "whatsapp",
        "whatsapp business",
        "new notification",
        "notification",
    )
    private val voiceMessageTerms = listOf(
        "voice message", "voice note", "audio message",
        "وائس میسج", "پیغام صوتی", "صوتی پیغام",
        "वॉइस मैसेज", "आवाज़ संदेश", "आवाज संदेश",
    )
    private val missedCallTerms = listOf(
        "missed call", "missed voice call", "missed video call",
        "مِسڈ کال", "मिस्ड कॉल",
    )
    private val videoCallTerms = listOf(
        "video call", "video calling", "incoming video",
        "ویڈیو کال", "वीडियो कॉल",
    )
    private val audioCallTerms = listOf(
        "audio call", "voice call", "audio calling", "incoming voice",
        "آڈیو کال", "وائس کال", "ऑडियो कॉल", "वॉइस कॉल",
    )
    private val callTerms = missedCallTerms + videoCallTerms + audioCallTerms + listOf(
        "incoming call", "calling", "کال آ رہی", "کال کر رہے",
        "इनकमिंग कॉल", "कॉल कर रहे",
    )

    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        category: String?,
        language: String,
        includePreview: Boolean,
    ): WhatsAppAnnouncement? {
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") return null

        val details = listOfNotNull(text, bigText)
            .map(String::trim)
            .filter(String::isNotEmpty)
        val searchable = (listOfNotNull(title) + details).joinToString(" ").lowercase(Locale.ROOT)
        val kind = when {
            category == "missed_call" || missedCallTerms.any(searchable::contains) ->
                WhatsAppNotificationKind.MISSED_CALL
            videoCallTerms.any(searchable::contains) -> WhatsAppNotificationKind.VIDEO_CALL
            audioCallTerms.any(searchable::contains) -> WhatsAppNotificationKind.AUDIO_CALL
            voiceMessageTerms.any(searchable::contains) -> WhatsAppNotificationKind.VOICE_MESSAGE
            category == "call" || callTerms.any(searchable::contains) -> WhatsAppNotificationKind.CALL
            else -> WhatsAppNotificationKind.MESSAGE
        }

        val sender = findSender(title, details, kind)
        val preview = if (kind == WhatsAppNotificationKind.MESSAGE && includePreview) {
            details.firstOrNull()?.removePrefix("$sender:")?.trim()?.take(140)
        } else {
            null
        }
        return WhatsAppAnnouncement(
            kind = kind,
            sender = sender,
            preview = preview,
            spokenText = formatAnnouncement(kind, sender, preview, language),
        )
    }

    private fun findSender(title: String?, details: List<String>, kind: WhatsAppNotificationKind): String {
        val candidate = title?.trim()?.takeIf(String::isNotEmpty)
        if (candidate != null && !isGenericTitle(candidate) &&
            callTerms.none { candidate.contains(it, ignoreCase = true) }
        ) {
            return candidate.take(80)
        }
        val callerFromTitle = candidate
            ?.substringAfterLast(" from ", "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 80 }
        if (callerFromTitle != null) return callerFromTitle
        if (kind == WhatsAppNotificationKind.MESSAGE) {
            val prefix = details.firstOrNull()?.substringBefore(':', "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= 80 }
            if (prefix != null) return prefix
        }
        return UNAVAILABLE
    }

    private fun isGenericTitle(candidate: String): Boolean =
        genericTitles.any { candidate.equals(it, ignoreCase = true) } ||
            candidate.startsWith("whatsapp", ignoreCase = true) ||
            candidate.matches(Regex("\\d+\\s+(new\\s+)?messages?", RegexOption.IGNORE_CASE))

    fun localized(
        announcement: WhatsAppAnnouncement,
        language: String,
        includePreview: Boolean,
    ): String = formatAnnouncement(
        announcement.kind,
        announcement.sender,
        announcement.preview.takeIf { includePreview },
        language,
    )

    fun englishFallback(announcement: WhatsAppAnnouncement, includePreview: Boolean): String =
        localized(announcement, "en-US", includePreview)

    private fun formatAnnouncement(
        kind: WhatsAppNotificationKind,
        sender: String,
        preview: String?,
        language: String,
    ): String {
        val english = when (kind) {
            WhatsAppNotificationKind.MESSAGE ->
                "A new WhatsApp message has arrived from $sender." + (preview?.let { " Message: $it" } ?: "")
            WhatsAppNotificationKind.VOICE_MESSAGE -> "You have received a new WhatsApp voice message from $sender."
            WhatsAppNotificationKind.AUDIO_CALL -> "You have an incoming WhatsApp audio call from $sender."
            WhatsAppNotificationKind.VIDEO_CALL -> "You have an incoming WhatsApp video call from $sender."
            WhatsAppNotificationKind.CALL -> "You have an incoming WhatsApp call from $sender."
            WhatsAppNotificationKind.MISSED_CALL -> "You missed a WhatsApp call from $sender."
        }
        return when (language) {
            "ur-PK" -> when (kind) {
                WhatsAppNotificationKind.MESSAGE -> "آپ کو $sender کی طرف سے واٹس ایپ پیغام موصول ہوا ہے۔" +
                    (preview?.let { " پیغام: $it" } ?: "")
                WhatsAppNotificationKind.VOICE_MESSAGE -> "آپ کو $sender کی طرف سے واٹس ایپ وائس میسج موصول ہوا ہے۔"
                WhatsAppNotificationKind.AUDIO_CALL -> "$sender کی واٹس ایپ آڈیو کال آ رہی ہے۔"
                WhatsAppNotificationKind.VIDEO_CALL -> "$sender کی واٹس ایپ ویڈیو کال آ رہی ہے۔"
                WhatsAppNotificationKind.CALL -> "$sender کی واٹس ایپ کال آ رہی ہے۔"
                WhatsAppNotificationKind.MISSED_CALL -> "آپ کی $sender کی واٹس ایپ کال مس ہو گئی۔"
            }
            "hi-IN" -> when (kind) {
                WhatsAppNotificationKind.MESSAGE -> "आपको $sender से एक नया WhatsApp संदेश मिला है।" +
                    (preview?.let { " संदेश: $it" } ?: "")
                WhatsAppNotificationKind.VOICE_MESSAGE -> "आपको $sender से एक नया WhatsApp वॉइस मैसेज मिला है।"
                WhatsAppNotificationKind.AUDIO_CALL -> "$sender की WhatsApp ऑडियो कॉल आ रही है।"
                WhatsAppNotificationKind.VIDEO_CALL -> "$sender की WhatsApp वीडियो कॉल आ रही है।"
                WhatsAppNotificationKind.CALL -> "$sender की WhatsApp कॉल आ रही है।"
                WhatsAppNotificationKind.MISSED_CALL -> "$sender की WhatsApp कॉल छूट गई।"
            }
            else -> english
        }
    }

}
