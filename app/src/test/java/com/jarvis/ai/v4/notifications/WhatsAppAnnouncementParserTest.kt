package com.jarvis.ai.v4.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WhatsAppAnnouncementParserTest {
    @Test
    fun messagePreviewIsHiddenUnlessExplicitlyEnabled() {
        val hidden = parse(text = "Alice: private message", includePreview = false)
        assertEquals(WhatsAppNotificationKind.MESSAGE, hidden.kind)
        assertEquals("Alice", hidden.sender)
        assertFalse(hidden.spokenText.contains("private message"))

        val visible = parse(text = "Alice: private message", includePreview = true)
        assertTrue(visible.spokenText.contains("private message"))
    }

    @Test
    fun notificationKindsAreIdentifiedOnlyFromAvailableNotificationText() {
        assertEquals(WhatsAppNotificationKind.VOICE_MESSAGE, parse(text = "Voice message (0:12)").kind)
        assertEquals(WhatsAppNotificationKind.AUDIO_CALL, parse(text = "Incoming audio call").kind)
        assertEquals(WhatsAppNotificationKind.VIDEO_CALL, parse(text = "Incoming video call").kind)
        assertEquals(WhatsAppNotificationKind.MISSED_CALL, parse(text = "Missed call").kind)
        assertEquals(WhatsAppNotificationKind.CALL, parse(category = "call").kind)
    }

    @Test
    fun missingSenderIsReportedAndEnglishFallbackRetainsSender() {
        val announcement = parse(title = "WhatsApp", text = "Incoming video call", language = "ur-PK")
        assertEquals("information unavailable", announcement.sender)
        assertTrue(announcement.spokenText.contains("واٹس ایپ ویڈیو کال"))
        assertTrue(WhatsAppAnnouncementParser.englishFallback(announcement, includePreview = false)
            .contains("information unavailable"))
    }

    @Test
    fun missedCallTitleCanSupplyCallerWithoutGuessing() {
        val announcement = parse(title = "Missed call from +15551234567")
        assertEquals(WhatsAppNotificationKind.MISSED_CALL, announcement.kind)
        assertEquals("+15551234567", announcement.sender)
    }

    @Test
    fun genericWhatsAppSummaryDoesNotBecomeSenderIdentity() {
        val announcement = parse(title = "WhatsApp (2 messages)", text = "3 new messages")
        assertEquals("information unavailable", announcement.sender)
    }

    @Test
    fun otherAppsAreIgnored() {
        assertNull(
            WhatsAppAnnouncementParser.parse(
                packageName = "com.example.chat",
                title = "Alice",
                text = "hello",
                bigText = null,
                category = null,
                language = "en-US",
                includePreview = true,
            ),
        )
    }

    @Test
    fun quietHoursSupportOvernightWindowsAndExclusiveEnd() {
        assertTrue(QuietHours.isActive("22:00", "07:00", LocalTime.of(23, 30)))
        assertTrue(QuietHours.isActive("22:00", "07:00", LocalTime.of(6, 59)))
        assertFalse(QuietHours.isActive("22:00", "07:00", LocalTime.of(7, 0)))
        assertFalse(QuietHours.isActive("22:00", "07:00", LocalTime.NOON))
        assertFalse(QuietHours.isActive("22:00", "22:00", LocalTime.of(23, 0)))
        assertFalse(QuietHours.isActive("invalid", "07:00", LocalTime.of(23, 0)))
    }

    private fun parse(
        title: String? = "Alice",
        text: String? = null,
        category: String? = null,
        language: String = "en-US",
        includePreview: Boolean = false,
    ) = WhatsAppAnnouncementParser.parse(
        packageName = "com.whatsapp",
        title = title,
        text = text,
        bigText = null,
        category = category,
        language = language,
        includePreview = includePreview,
    )!!
}
