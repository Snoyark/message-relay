package io.github.snoyark.messagerelay

import android.app.Notification
import android.app.Person
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

// Android hands this every notification once it has notification access. Only ones from the apps
// chosen in Message Relay are read; the rest are ignored without looking at them.
class RelayService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in Prefs(this).apps) return
        Outbox.add(this, extract(snapshot(sbn)))
    }

    private fun snapshot(sbn: StatusBarNotification): Snapshot {
        val n = sbn.notification
        val extras = n.extras
        return Snapshot(
            app = appLabel(sbn.packageName),
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION),
            messages = messages(extras),
            selfName = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)?.name?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString(),
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
    }

    // A chat app's messages, each a Bundle as Notification.MessagingStyle stores them.
    private fun messages(extras: Bundle): List<Incoming> {
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java) ?: return emptyList()
        return bundles.mapNotNull { item ->
            val b = item as? Bundle ?: return@mapNotNull null
            val text = b.getCharSequence("text")?.toString() ?: return@mapNotNull null
            val sender = b.getParcelable("sender_person", Person::class.java)?.name?.toString() ?: b.getCharSequence("sender")?.toString()
            Incoming(sender, text, b.getLong("time"))
        }
    }

    private fun appLabel(packageName: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
}
