package io.github.snoyark.messagerelay

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractTest {
    private fun snapshot(
        title: String? = null,
        text: String? = null,
        conversationTitle: String? = null,
        isGroup: Boolean = false,
        messages: List<Incoming> = emptyList(),
        selfName: String? = "Me",
        isGroupSummary: Boolean = false,
        isOngoing: Boolean = false,
    ) = Snapshot("Messages", "com.google.android.apps.messaging", title, text, conversationTitle, isGroup, messages, selfName, isGroupSummary, isOngoing)

    @Test
    fun oneToOneChatsAreNamedAfterTheSender() {
        val out = extract(snapshot(title = "Alex", messages = listOf(Incoming("Alex", "Dinner  at\n7?", 1000L))))
        assertEquals(listOf(Outgoing("Messages", "com.google.android.apps.messaging", "Alex", "", "Dinner at 7?", 1000L)), out)
    }

    @Test
    fun groupChatsKeepTheSenderAndSkipTheOwnersMessages() {
        val out = extract(
            snapshot(
                conversationTitle = "Night Owls",
                isGroup = true,
                messages = listOf(
                    Incoming("maya", "run at 9?", 1L),
                    Incoming(null, "I'm in", 2L),
                    Incoming("Me", "also me", 3L),
                    Incoming("Sam", "  ", 4L),
                ),
            ),
        )
        assertEquals(listOf(Outgoing("Messages", "com.google.android.apps.messaging", "Night Owls", "maya", "run at 9?", 1L)), out)
    }

    @Test
    fun groupWithoutAConversationTitleFallsBackToTheNotificationTitle() {
        val out = extract(snapshot(title = "#general", isGroup = true, messages = listOf(Incoming("maya", "hi", 0L))))
        assertEquals(listOf(Outgoing("Messages", "com.google.android.apps.messaging", "#general", "maya", "hi", null)), out)
    }

    @Test
    fun otherNotificationsUseTitleAndTextWithoutATime() {
        assertEquals(
            listOf(Outgoing("Messages", "com.google.android.apps.messaging", "Sam", "", "Sent you photos", null)),
            extract(snapshot(title = "Sam", text = "Sent you photos")),
        )
    }

    @Test
    fun summariesAndOngoingNotificationsAreSkipped() {
        assertEquals(emptyList<Outgoing>(), extract(snapshot(title = "Messages", text = "3 new messages")))
        assertEquals(emptyList<Outgoing>(), extract(snapshot(title = "Messages", text = "5 messages from 2 chats")))
        assertEquals(emptyList<Outgoing>(), extract(snapshot(title = "x", text = "y", isGroupSummary = true)))
        assertEquals(emptyList<Outgoing>(), extract(snapshot(title = "Call", text = "Ongoing call", isOngoing = true)))
        assertEquals(emptyList<Outgoing>(), extract(snapshot(title = "x", text = " ")))
    }

    @Test
    fun repeatsHaveTheSameId() {
        val a = extract(snapshot(messages = listOf(Incoming("Alex", "hi", 5L))))[0]
        val b = extract(snapshot(messages = listOf(Incoming("Alex", "hi", 5L), Incoming("Alex", "there", 6L))))
        assertEquals(a.id, b[0].id)
        assertEquals(2, b.map { it.id }.toSet().size)
    }

    @Test
    fun endpointUrlAddsHttpsOnlyWhenMissing() {
        assertEquals("https://example.com/hook", endpointUrl(" example.com/hook "))
        assertEquals("http://192.168.1.5:8787/hooks/notification", endpointUrl("http://192.168.1.5:8787/hooks/notification"))
        assertEquals("", endpointUrl(""))
    }
}
