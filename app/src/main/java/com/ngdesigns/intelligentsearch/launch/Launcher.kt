package com.ngdesigns.intelligentsearch.launch

import androidx.core.net.toUri
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings as SystemSettings
import com.ngdesigns.intelligentsearch.data.device.Target
import com.ngdesigns.intelligentsearch.engine.actions.Action
import com.ngdesigns.intelligentsearch.engine.actions.MessagingApp
import com.ngdesigns.intelligentsearch.engine.toggles.Toggle
import com.ngdesigns.intelligentsearch.engine.web.SearchEngine
import java.time.ZoneId

/**
 * Opens results and runs actions. Every launch is an explicit, public intent; web addresses only ever open in a
 * browser (Tor searches only in Tor Browser). Returns false when nothing on the phone can handle it, so the screen
 * can say so instead of failing silently.
 */
class Launcher(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun open(target: Target, bounds: Rect? = null, options: Bundle? = null): Boolean = when (target) {
        is Target.App -> attempt { launcherApps.startMainActivity(target.component, target.user, bounds, options) }
        is Target.Shortcut -> attempt { launcherApps.startShortcut(target.packageName, target.shortcutId, bounds, options, target.user) }
        is Target.Contact -> start(Intent(Intent.ACTION_VIEW, target.lookupUri.toUri()))
        is Target.Event -> start(
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, target.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, target.beginMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, target.endMillis),
        )
        is Target.File -> start(
            Intent(Intent.ACTION_VIEW).setDataAndType(target.uri.toUri(), target.mimeType ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        is Target.Setting, is Target.Query -> false // handled inside the app
    }

    fun call(phone: String, direct: Boolean): Boolean =
        start(Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) ||
            (direct && start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))))

    fun message(phone: String, body: String? = null): Boolean =
        start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)).apply { body?.let { putExtra("sms_body", it) } })

    fun appInfo(packageName: String): Boolean =
        start(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    fun uninstall(packageName: String): Boolean = start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))

    /** Opens an https address in a browser; never anything else. */
    fun web(url: String, engine: SearchEngine? = null): Boolean {
        val uri = url.toUri()
        if (uri.scheme != "https") return false
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        if (engine is SearchEngine.Tor) {
            val tor = SearchEngine.Tor.browserPackages.firstOrNull { pkg -> runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess }
                ?: return false
            return start(intent.setPackage(tor))
        }
        return start(intent)
    }

    fun search(query: String, engine: SearchEngine): Boolean = web(engine.searchUrl(query), engine)

    /** Google Lens: the Lens app, else the Google app's Lens, else Lens on the web. */
    fun lens(): Boolean =
        context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")?.let(::start) == true ||
            start(Intent().setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")) ||
            web("https://lens.google.com")

    /** An app link such as vnd.youtube://… or spotify:search:…, opened by the app that owns it. */
    fun openUri(uri: String): Boolean = start(Intent(Intent.ACTION_VIEW, uri.toUri()))

    fun calculator(): Boolean = start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))

    /** A new note in the user's notes app (the public "create note" action Keep and others handle). */
    fun newNote(text: String = ""): Boolean =
        start(Intent("com.google.android.gms.actions.CREATE_NOTE").putExtra(Intent.EXTRA_TEXT, text).setType("text/plain"))

    /** Runs a parsed command. Timers and alarms start in the clock app; events and reminders open prefilled. */
    fun run(action: Action, resolvePhone: (String) -> String? = { null }): Boolean = when (action) {
        is Action.Timer -> start(
            Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, action.seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                .apply { action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } },
        )
        is Action.Alarm -> start(
            Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, action.hour).putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                .apply { action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } },
        )
        is Action.Event -> start(
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, action.title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.start.plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
        )
        is Action.Reminder -> start(
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, action.task)
                .apply {
                    action.at?.let { at ->
                        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ms).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ms + 15 * 60_000)
                        putExtra(CalendarContract.Events.HAS_ALARM, 1)
                    }
                },
        )
        is Action.Message -> {
            val phone = resolvePhone(action.recipient) ?: action.recipient.takeIf { it.any(Char::isDigit) }
            when {
                action.app == MessagingApp.WhatsApp && phone != null ->
                    web("https://wa.me/${phone.filter(Char::isDigit)}" + (action.body?.let { "?text=" + Uri.encode(it) } ?: ""))
                phone != null -> message(phone, action.body)
                else -> start(Intent(Intent.ACTION_SENDTO, "smsto:".toUri()).apply { action.body?.let { putExtra("sms_body", it) } })
            }
        }
        is Action.Call -> {
            val phone = resolvePhone(action.target) ?: action.target.takeIf { it.count(Char::isDigit) >= 3 }
            phone != null && call(phone, direct = false)
        }
        is Action.Navigate -> start(Intent(Intent.ACTION_VIEW, ("google.navigation:q=" + Uri.encode(action.destination)).toUri()))
            || start(Intent(Intent.ACTION_VIEW, ("geo:0,0?q=" + Uri.encode(action.destination)).toUri()))
    }

    /** The settings panel or page for a system toggle; apps can't flip most of these directly. */
    fun toggle(toggle: Toggle): Boolean = start(
        Intent(
            when (toggle) {
                Toggle.Wifi -> SystemSettings.Panel.ACTION_WIFI
                Toggle.Bluetooth -> SystemSettings.ACTION_BLUETOOTH_SETTINGS
                Toggle.Flashlight -> return false // flipped directly through the camera service
                Toggle.BatterySaver -> SystemSettings.ACTION_BATTERY_SAVER_SETTINGS
                Toggle.DoNotDisturb -> SystemSettings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
                Toggle.AirplaneMode -> SystemSettings.ACTION_AIRPLANE_MODE_SETTINGS
                Toggle.AutoRotate -> SystemSettings.ACTION_DISPLAY_SETTINGS
                Toggle.Hotspot -> SystemSettings.ACTION_WIRELESS_SETTINGS
                Toggle.DarkTheme -> SystemSettings.ACTION_DISPLAY_SETTINGS
                Toggle.Nfc -> SystemSettings.Panel.ACTION_NFC
                Toggle.Location -> SystemSettings.ACTION_LOCATION_SOURCE_SETTINGS
            },
        ),
    )

    fun contactPhone(lookupUri: String): String? = runCatching {
        val contactUri = ContactsContract.Contacts.lookupContact(context.contentResolver, lookupUri.toUri()) ?: return null
        val id = ContentUris.parseId(contactUri)
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun start(intent: Intent): Boolean = attempt {
        context.startActivity(
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ActivityOptions.makeBasic().toBundle(),
        )
    }

    private inline fun attempt(block: () -> Unit): Boolean = try {
        block(); true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    }
}
