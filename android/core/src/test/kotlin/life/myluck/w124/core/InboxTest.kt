package life.myluck.w124.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxTest {
    @Test
    fun inquiryWritesLogAndPendingInbox() {
        val state = GarageJson.decodeState(
            javaClass.classLoader!!.getResource("seed-state.json")!!.readText(),
        )
        val inbox = InboxBook(updatedAt = "2026-08-26T10:00:00Z")
        val log = LogEntry(
            id = "log-1",
            date = "2026-08-26",
            title = "Заметка · 322 300 км",
            body = "Свист на холодную громче",
            tags = listOf("заметка", "inbox"),
            updatedAt = "2026-08-26T17:00:00Z",
        )
        val item = InboxItem(
            id = "in-1",
            date = "2026-08-26",
            odometer = 322300,
            body = "Свист на холодную громче",
            logId = "log-1",
            updatedAt = "2026-08-26T17:00:00Z",
        )
        val (nextState, nextInbox) = GarageMutations.addInquiry(state, inbox, item, log)
        assertEquals(322300, nextState.odometer.km)
        assertTrue(nextState.logbook.any { it.id == "log-1" })
        assertEquals(InboxStatus.PENDING, nextInbox.items.single().status)
    }

    @Test
    fun answerMarksInboxAndSurvivesMerge() {
        val pending = InboxBook(
            updatedAt = "2026-08-26T17:00:00Z",
            items = listOf(
                InboxItem(
                    id = "in-1",
                    date = "2026-08-26",
                    odometer = 322300,
                    body = "Свист",
                    logId = "log-1",
                    updatedAt = "2026-08-26T17:00:00Z",
                ),
            ),
        )
        val answered = GarageMutations.answerInbox(pending, "in-1", "Похоже на ролик натяжителя.", "2026-08-26T17:05:00Z")
        assertEquals(InboxStatus.ANSWERED, answered.items.single().status)
        val merged = GarageMerge.mergeInbox(pending, answered)
        assertEquals("Похоже на ролик натяжителя.", merged.items.single().answer)
    }

    @Test
    fun apkMagicDetectsZip() {
        assertTrue(AppVersion.looksLikeApk(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(AppVersion.looksLikeApk(byteArrayOf(0x3C, 0x68.toByte())))
    }
}
