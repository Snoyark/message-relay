package io.github.snoyark.messagerelay

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// Messages waiting to reach the webhook. They're kept on the phone until the server accepts them, so
// nothing is lost while the phone is offline or the server is down.
// The version of PROTOCOL.md that messages follow.
const val PROTOCOL_VERSION = 1

object Outbox {
    private const val KEEP_SEEN_MS = 3L * 24 * 60 * 60 * 1000
    // Older messages are dropped unsent, so a long-broken setup doesn't send a flood of stale ones.
    private const val GIVE_UP_MS = 30L * 24 * 60 * 60 * 1000
    private const val JOB_ID = 1

    // One thread does all sending, in order.
    private val worker = Executors.newSingleThreadExecutor()

    private class Db(context: Context) : SQLiteOpenHelper(context, "outbox.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE outbox (id INTEGER PRIMARY KEY, body TEXT NOT NULL, created INTEGER NOT NULL)")
            // Messages already queued, so ones still shown in an updated notification aren't sent again.
            db.execSQL("CREATE TABLE seen (id TEXT PRIMARY KEY, at INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    @Volatile private var db: Db? = null
    private fun db(context: Context) = db ?: synchronized(this) { db ?: Db(context.applicationContext).also { db = it } }

    // Queues new messages and tries to send them.
    fun add(context: Context, messages: List<Outgoing>) {
        if (messages.isEmpty()) return
        val app = context.applicationContext
        worker.execute {
            val w = db(app).writableDatabase
            val now = System.currentTimeMillis()
            w.beginTransaction()
            try {
                w.delete("seen", "at < ?", arrayOf((now - KEEP_SEEN_MS).toString()))
                for (m in messages) {
                    val seen = ContentValues().apply { put("id", m.id); put("at", now) }
                    if (w.insertWithOnConflict("seen", null, seen, SQLiteDatabase.CONFLICT_IGNORE) == -1L) continue
                    val body = JSONObject().apply {
                        put("version", PROTOCOL_VERSION)
                        put("app", m.app)
                        put("package", m.packageName)
                        put("title", m.title)
                        put("sender", m.sender)
                        put("text", m.text)
                        if (m.sentAt != null) put("sentAt", m.sentAt)
                    }
                    w.insert("outbox", null, ContentValues().apply { put("body", body.toString()); put("created", now) })
                }
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
            sendAll(app)
        }
    }

    // Sends from any thread; used by the retry job and the Send now button.
    fun flush(context: Context, done: (Boolean) -> Unit = {}) {
        val app = context.applicationContext
        worker.execute { done(sendAll(app)) }
    }

    fun waiting(context: Context): Int {
        db(context).readableDatabase.rawQuery("SELECT COUNT(*) FROM outbox", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    // Sends queued messages oldest first. Returns true when none are left.
    private fun sendAll(context: Context): Boolean {
        val prefs = Prefs(context)
        if (!prefs.configured) {
            prefs.lastError = "Add the webhook address and key."
            return false
        }
        val w = db(context).writableDatabase
        w.delete("outbox", "created < ?", arrayOf((System.currentTimeMillis() - GIVE_UP_MS).toString()))
        while (true) {
            val next = w.rawQuery("SELECT id, body FROM outbox ORDER BY id LIMIT 1", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) to c.getString(1) else null
            } ?: return true
            val (id, body) = next
            val status = try {
                post(endpointUrl(prefs.address), prefs.key, body)
            } catch (e: IOException) {
                prefs.lastError = "Couldn't reach the server (${e.message ?: e.javaClass.simpleName}). Will retry."
                scheduleRetry(context)
                return false
            }
            when {
                status in 200..299 -> {
                    w.delete("outbox", "id = ?", arrayOf(id.toString()))
                    prefs.lastSentAt = System.currentTimeMillis()
                    prefs.lastError = ""
                }
                status == 401 -> {
                    // Kept, to send once the key is fixed.
                    prefs.lastError = "The server refused the key. Check it and save again."
                    return false
                }
                status in 400..499 -> {
                    // The server can't use this one (for example, an empty message); retrying won't help.
                    w.delete("outbox", "id = ?", arrayOf(id.toString()))
                    prefs.lastError = "The server rejected a message (HTTP $status)."
                }
                else -> {
                    prefs.lastError = "The server had a problem (HTTP $status). Will retry."
                    scheduleRetry(context)
                    return false
                }
            }
        }
    }

    // Checks the address and key without sending a message. Returns a sentence for the person.
    fun check(context: Context, done: (String) -> Unit) {
        val prefs = Prefs(context)
        worker.execute {
            done(
                try {
                    val connection = URL(endpointUrl(prefs.address)).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Authorization", "Bearer ${prefs.key}")
                    when (val status = connection.responseCode) {
                        in 200..299 -> "Connected."
                        401 -> "The server is reachable, but the key is wrong."
                        404 -> "Nothing at that address accepts messages. Check the address."
                        else -> "The server answered with HTTP $status."
                    }.also { connection.disconnect() }
                } catch (e: Exception) {
                    "Couldn't reach the server: ${e.message ?: e.javaClass.simpleName}."
                },
            )
        }
    }

    private fun post(url: String, key: String, body: String): Int {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    // Asks Android to run SendJob once there's a network, backing off if it keeps failing.
    private fun scheduleRetry(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, SendJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(30_000)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(false)
                .build(),
        )
    }
}
