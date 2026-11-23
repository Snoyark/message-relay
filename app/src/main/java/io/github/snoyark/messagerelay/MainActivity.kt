package io.github.snoyark.messagerelay

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

// The one screen: the webhook, permissions, and which apps to copy messages from.
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var accessButton: Button
    private lateinit var batteryButton: Button
    private lateinit var address: EditText
    private lateinit var key: EditText
    private lateinit var checkResult: TextView
    private lateinit var appList: LinearLayout
    private lateinit var allAppsButton: Button
    private var showAllApps = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { renderStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(build())
        address.setText(prefs.address)
        key.setText(prefs.key)
        handleSetupLink(intent)
        renderApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSetupLink(intent)
    }

    override fun onResume() {
        super.onResume()
        prefs.onChange(prefsListener)
        renderStatus()
    }

    override fun onPause() {
        super.onPause()
        prefs.offChange(prefsListener)
    }

    // messagerelay://setup?url=...&key=..., from a server's setup page.
    private fun handleSetupLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "messagerelay" || uri.host != "setup") return
        uri.getQueryParameter("url")?.let { address.setText(it) }
        uri.getQueryParameter("key")?.let { key.setText(it) }
        save()
    }

    private fun save() {
        prefs.address = address.text.toString()
        prefs.key = key.text.toString()
        checkResult.text = "Checking…"
        Outbox.check(this) { result ->
            runOnUiThread { checkResult.text = result }
            Outbox.flush(this)
        }
    }

    // Says exactly what's read and where it goes before Android's notification access screen,
    // and only continues once they agree.
    private fun askForAccess() {
        val open = {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(this, RelayService::class.java).flattenToString()),
            )
        }
        if (prefs.consented) return open()
        AlertDialog.Builder(this)
            .setTitle("Before you allow access")
            .setMessage(
                "Android will let Message Relay see all your notifications. It reads only those from the apps you switch on " +
                    "below, and ignores the rest.\n\n" +
                    "From those apps it copies each message's text, its sender, the chat's name and when it was sent, and sends " +
                    "them only to the webhook address you enter. They are not sent to the developer or anyone else. " +
                    "Messages wait on this phone until your server accepts them.\n\n" +
                    "You can turn this off at any time by switching apps off or removing notification access.",
            )
            .setPositiveButton("I agree") { _, _ ->
                prefs.consented = true
                open()
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    // --- Status ---

    private fun hasAccess() = getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(this, RelayService::class.java))

    private fun ignoresBatteryLimits() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun renderStatus() {
        accessButton.visibility = if (hasAccess()) View.GONE else View.VISIBLE
        batteryButton.visibility = if (ignoresBatteryLimits()) View.GONE else View.VISIBLE
        val lines = mutableListOf<String>()
        when {
            !hasAccess() -> lines += "Needs notification access to see messages."
            !prefs.configured -> lines += "Add the webhook address and key below."
            prefs.apps.isEmpty() -> lines += "Choose an app below to copy messages from."
            else -> lines += "On. Copying messages from ${prefs.apps.size} app${if (prefs.apps.size == 1) "" else "s"}."
        }
        val sent = prefs.lastSentAt
        lines += if (sent > 0) "Last sent ${DateUtils.getRelativeTimeSpanString(sent)}." else "Nothing sent yet."
        // Counting reads the database, so it's done off the screen's thread.
        Thread {
            val waiting = Outbox.waiting(this)
            runOnUiThread {
                val all = lines.toMutableList()
                if (waiting > 0) all += "$waiting waiting to send."
                if (prefs.lastError.isNotEmpty()) all += prefs.lastError
                status.text = all.joinToString("\n")
            }
        }.start()
    }

    // --- Apps ---

    // Chat apps worth suggesting, if installed.
    private val suggested = listOf(
        "com.google.android.apps.messaging",
        "com.discord",
        "com.facebook.orca",
        "com.whatsapp",
        "org.thoughtcrime.securesms",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.Slack",
        "com.microsoft.teams",
        "com.google.android.apps.dynamite",
    )

    private fun renderApps() {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
        val chosen = prefs.apps
        val top = installed.filter { it.first in chosen || it.first in suggested }
            .sortedWith(compareBy({ it.first !in chosen }, { it.second.lowercase() }))
        val rest = installed.filter { it !in top }.sortedBy { it.second.lowercase() }

        appList.removeAllViews()
        for ((pkg, label) in if (showAllApps) top + rest else top) {
            appList.addView(Switch(this).apply {
                text = label
                textSize = 16f
                isChecked = pkg in chosen
                setPadding(0, dp(10), 0, dp(10))
                setOnCheckedChangeListener { _, on ->
                    prefs.apps = if (on) prefs.apps + pkg else prefs.apps - pkg
                    renderStatus()
                }
            })
        }
        if (top.isEmpty() && !showAllApps) appList.addView(text("No chat apps found. Show all apps to pick one.", muted = true))
        allAppsButton.text = if (showAllApps) "Show chat apps only" else "Show all apps"
    }

    // --- Layout ---

    private fun dp(n: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n.toFloat(), resources.displayMetrics).toInt()

    private fun text(s: String, size: Float = 15f, muted: Boolean = false, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        if (muted) alpha = 0.7f
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun heading(s: String) = text(s, 13f, bold = true).apply {
        isAllCaps = true
        letterSpacing = 0.08f
        setPadding(0, dp(24), 0, dp(4))
    }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun build(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        column.addView(text(getString(R.string.app_name), 28f, bold = true))
        column.addView(text("Copies messages from the apps you choose to a webhook you control. Only incoming messages that show a notification are seen.", muted = true))

        column.addView(heading("Status"))
        status = text("").also { column.addView(it) }
        accessButton = button("Allow notification access") { askForAccess() }.also { column.addView(it) }
        batteryButton = button("Keep running in the background") {
            // Opens the list of apps; choose Message Relay → Unrestricted.
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }.also { column.addView(it) }
        column.addView(button("Send waiting messages now") { Outbox.flush(this) { runOnUiThread { renderStatus() } } })

        column.addView(heading("Send to"))
        column.addView(text("The address and key come from the server you're sending to. Servers that support Message Relay can fill them in with a setup link.", muted = true))
        address = EditText(this).apply {
            hint = "https://example.com/webhook"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }.also { column.addView(it) }
        key = EditText(this).apply {
            hint = "Key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isSingleLine = true
        }.also { column.addView(it) }
        column.addView(button("Save and check") { save() })
        checkResult = text("", muted = true).also { column.addView(it) }

        column.addView(heading("Copy messages from"))
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { column.addView(it) }
        allAppsButton = button("Show all apps") {
            showAllApps = !showAllApps
            renderApps()
        }.also { column.addView(it) }

        return ScrollView(this).apply {
            addView(column)
            // Apps draw behind the status and navigation bars on recent Android, so pad for them.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
    }
}
