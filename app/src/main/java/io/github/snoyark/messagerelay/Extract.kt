package io.github.snoyark.messagerelay

// Turning a notification into messages to send. Kept free of Android classes so it can be unit tested.

data class Incoming(val sender: String?, val text: String, val time: Long)

// What a notification says, read out of its extras by RelayService.
data class Snapshot(
    val app: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    // Set for group chats; null for most one-to-one chats.
    val conversationTitle: String?,
    val isGroup: Boolean,
    // From a chat app's messaging-style notification: each message with its sender. A null
    // sender is the phone's owner, and so is a sender with their own name.
    val messages: List<Incoming>,
    val selfName: String?,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
)

// One message, as described in PROTOCOL.md. sentAt is null when the time isn't the message's own,
// so the server should recognise repeats by their text instead.
data class Outgoing(
    val app: String,
    val packageName: String,
    val title: String,
    val sender: String,
    val text: String,
    val sentAt: Long?,
) {
    // Identifies the message, so one still shown in an updated notification isn't sent twice.
    val id: String get() = listOf(app, title, sender, text, sentAt ?: "").joinToString("\u0000")
}

// Summaries like "3 new messages" stand for messages, rather than being one.
private val SUMMARY = Regex("""^\d+\s+(new\s+)?messages?(\s+from\s+\d+\s+(chats?|conversations?))?\.?$""", RegexOption.IGNORE_CASE)

private fun String?.clean(): String = this?.replace(Regex("\\s+"), " ")?.trim().orEmpty()

fun extract(n: Snapshot): List<Outgoing> {
    // A summary of a group of notifications repeats them; ongoing ones (calls, uploads) aren't messages.
    if (n.isGroupSummary || n.isOngoing) return emptyList()

    if (n.messages.isNotEmpty()) {
        val self = n.selfName.clean()
        val conversation = n.conversationTitle.clean()
        return n.messages.mapNotNull { m ->
            val sender = m.sender.clean()
            val text = m.text.clean()
            if (sender.isEmpty() || sender == self || text.isEmpty()) return@mapNotNull null
            // One-to-one chats are named after the other person.
            val title = conversation.ifEmpty { if (n.isGroup) n.title.clean().ifEmpty { sender } else sender }
            Outgoing(n.app, n.packageName, title, if (title == sender) "" else sender, text, m.time.takeIf { it > 0 })
        }
    }

    // Any other notification: its title and text, as an automation app would send them.
    val text = n.text.clean()
    if (text.isEmpty() || SUMMARY.matches(text)) return emptyList()
    return listOf(Outgoing(n.app, n.packageName, n.title.clean(), "", text, null))
}

// The webhook address as typed, with https:// added if no scheme was given.
fun endpointUrl(address: String): String {
    val url = address.trim()
    if (url.isEmpty()) return ""
    return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
}
