package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pixel.intelligentsearch.core.data.ContactItem
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A button on a contact result. Chat apps carry the package name their intent is restricted to, so the number only
 * goes to whatever app is installed under that name.
 */
internal enum class ContactActionKind(val label: String, val packageName: String? = null) {
    CALL("Call"),
    MESSAGE("Message"),
    EMAIL("Email"),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    WHATSAPP_BUSINESS("WhatsApp Business", "com.whatsapp.w4b"),
    TELEGRAM("Telegram"),
    SIGNAL("Signal", "org.thoughtcrime.securesms")
}

/** One button on a contact result: what it opens, what TalkBack says, and the app whose icon it shows (if any). */
internal data class ContactAction(
    val kind: ContactActionKind,
    val intent: Intent,
    val contentDescription: String,
    val iconPackage: String?
)

/**
 * Which contact buttons this device can answer (a dialer, an SMS app, an email app, the installed chat apps) and the
 * country local numbers belong to. Whether an app handles a link doesn't depend on the number in it, so this is
 * resolved once per resume rather than for every result.
 */
internal data class ContactActionTargets(
    val available: Set<ContactActionKind>,
    val telegramPackage: String? = null,
    val countryIso: String = ""
) {
    companion object {
        val NONE = ContactActionTargets(emptySet())

        /**
         * The targets last resolved in this process, so an overlay opened later has its buttons from the first frame
         * instead of growing them in once a fresh check finishes. Refreshed each time the overlay resumes.
         */
        @Volatile
        var cached: ContactActionTargets? = null

        /**
         * The package names of the official Telegram builds (Play and direct download). Telegram intents are
         * restricted to these package names; who signed the app isn't checked, so a sideloaded app installed under
         * one of these names would receive the number too.
         */
        val TELEGRAM_PACKAGES = listOf("org.telegram.messenger", "org.telegram.messenger.web")

        // Stand-in addresses: only the scheme, host and package decide what resolves.
        private const val PROBE_E164 = "+15555550100"

        fun resolve(context: Context): ContactActionTargets {
            val pm = context.packageManager
            fun resolves(intent: Intent) = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
            val available = mutableSetOf<ContactActionKind>()
            if (resolves(ContactActions.callIntent(PROBE_E164))) available += ContactActionKind.CALL
            if (resolves(ContactActions.messageIntent(PROBE_E164))) available += ContactActionKind.MESSAGE
            if (resolves(ContactActions.emailIntent("probe@example.com"))) available += ContactActionKind.EMAIL
            if (resolves(ContactActions.whatsAppIntent(PROBE_E164, ContactActionKind.WHATSAPP.packageName!!))) {
                available += ContactActionKind.WHATSAPP
            }
            if (resolves(ContactActions.whatsAppIntent(PROBE_E164, ContactActionKind.WHATSAPP_BUSINESS.packageName!!))) {
                available += ContactActionKind.WHATSAPP_BUSINESS
            }
            val telegram = TELEGRAM_PACKAGES.firstOrNull { resolves(ContactActions.telegramIntent(PROBE_E164, it)) }
            if (telegram != null) available += ContactActionKind.TELEGRAM
            if (resolves(ContactActions.signalIntent(PROBE_E164))) available += ContactActionKind.SIGNAL
            return ContactActionTargets(available, telegram, ContactActions.deviceCountryIso(context))
        }
    }
}

/**
 * The Call, Message, Email and chat buttons on a contact result. Every intent is built from the number or address
 * saved on the contact, never from the typed query.
 */
internal object ContactActions {

    fun callIntent(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))

    /**
     * What tapping [contact] itself opens: the dialler with its number when [directCall] is on (the Direct Call
     * setting) or when it has no card to show, else its card in Contacts, else the Contacts app. Numbers go through
     * [callIntent], so "*86#" reaches the dialler whole instead of being cut at the "#".
     */
    fun openIntent(contact: ContactItem, directCall: Boolean): Intent = when {
        directCall && contact.phoneNumber.isNotBlank() -> callIntent(contact.phoneNumber)
        // Contacts reads the lookup URI with its own permission; no grant needed.
        contact.lookupUri.isNotBlank() -> Intent(Intent.ACTION_VIEW, contact.lookupUri.toUri())
        contact.phoneNumber.isNotBlank() -> callIntent(contact.phoneNumber)
        else -> Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
    }

    fun messageIntent(number: String): Intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))

    fun emailIntent(address: String): Intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))

    /** WhatsApp's click-to-chat link, opened only in [packageName] (WhatsApp or WhatsApp Business). */
    fun whatsAppIntent(e164: String, packageName: String): Intent =
        Intent(Intent.ACTION_VIEW, "https://wa.me/${e164.removePrefix("+")}".toUri()).setPackage(packageName)

    fun telegramIntent(e164: String, packageName: String): Intent =
        Intent(Intent.ACTION_VIEW, "tg://resolve?phone=${e164.removePrefix("+")}".toUri()).setPackage(packageName)

    fun signalIntent(e164: String): Intent =
        Intent(Intent.ACTION_VIEW, "https://signal.me/#p/$e164".toUri()).setPackage(ContactActionKind.SIGNAL.packageName)

    /**
     * [number] in E.164 ("+14155551234"), reading numbers saved without a country code as numbers of [countryIso]
     * (the SIM's country). An extension is dropped: a chat goes to the line itself. Null when the number isn't valid
     * or can't be made international (a short code, an unknown country), so the chat buttons are hidden rather than
     * opening a chat with whoever owns the digits. [format] is the platform's formatter, replaceable in tests.
     */
    fun toE164(
        number: String,
        countryIso: String,
        format: (String, String) -> String? = PhoneNumberUtils::formatNumberToE164
    ): String? {
        val line = asciiDigits(withoutExtension(number)).trim()
        if (line.none { it in '0'..'9' }) return null
        val e164 = try {
            // Null means the platform read the number and found it invalid; that is final.
            format(line, countryIso.uppercase(Locale.ROOT)) ?: return null
        } catch (_: Throwable) {
            // Only without the platform's metadata: a number that already says its country code is still usable.
            val digits = line.filter { it in '0'..'9' }
            val international = when {
                line.startsWith("+") -> digits
                line.startsWith("00") -> digits.removePrefix("00")
                else -> return null
            }
            if (international.length in 8..15 && !international.startsWith("0")) "+$international" else return null
        }
        return e164.takeIf { E164.matches(it) }
    }

    /** A plus and up to 15 ASCII digits, the first not a zero: all a chat link may carry. */
    private val E164 = Regex("""\+[1-9][0-9]{1,14}""")

    /**
     * An extension and everything after it: "x", "ext", "ext." or a dialler pause, wait or hash, whether written as
     * "," and ";" or as the letters "p" and "w". Dropping a letter alone would glue the digits after it onto the line.
     */
    private val EXTENSION = Regex("""(?i)(ext|x|[,;#pw]).*""", RegexOption.DOT_MATCHES_ALL)

    private fun withoutExtension(number: String): String = EXTENSION.replace(number, "")

    /** [number] with other scripts' decimal digits (Arabic-Indic, Devanagari, full-width...) written in ASCII. */
    private fun asciiDigits(number: String): String = buildString(number.length) {
        for (c in number) {
            val digit = if (c.isDigit()) Character.digit(c, 10) else -1
            append(if (digit in 0..9) '0' + digit else c)
        }
    }

    /** The country the SIM (else the network, else the locale) belongs to, as an ISO code like "US". */
    fun deviceCountryIso(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val iso = runCatching { telephony?.simCountryIso?.takeIf { it.isNotBlank() } ?: telephony?.networkCountryIso }
            .getOrNull()
        return (iso?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country).uppercase(Locale.ROOT)
    }

    /**
     * The buttons [contact] gets: Call and Message with a number, Email with an address, and each installed chat app
     * once the number can be written internationally. Call and Message dial the number as saved; the chat apps get
     * the contact's chat number (its default, else a mobile) when it has one. They have no switch: contact results
     * always offer them.
     */
    fun forContact(contact: ContactItem, targets: ContactActionTargets): List<ContactAction> {
        val who = contact.name.ifBlank { contact.phoneNumber.ifBlank { contact.email } }
        val number = contact.phoneNumber.trim()
        val chatNumber = contact.chatNumber.trim().ifEmpty { number }
        val e164 = if (chatNumber.isNotEmpty()) toE164(chatNumber, targets.countryIso) else null
        val actions = mutableListOf<ContactAction>()
        fun add(kind: ContactActionKind, intent: Intent, description: String, iconPackage: String? = null) {
            if (kind in targets.available) actions += ContactAction(kind, intent, description, iconPackage)
        }
        if (number.isNotEmpty()) {
            add(ContactActionKind.CALL, callIntent(number), "Call $who")
            add(ContactActionKind.MESSAGE, messageIntent(number), "Message $who")
        }
        val email = contact.email.trim()
        if (email.isNotEmpty()) add(ContactActionKind.EMAIL, emailIntent(email), "Email $who")
        if (e164 != null) {
            for (kind in listOf(ContactActionKind.WHATSAPP, ContactActionKind.WHATSAPP_BUSINESS)) {
                add(kind, whatsAppIntent(e164, kind.packageName!!), "Message $who on ${kind.label}", kind.packageName)
            }
            targets.telegramPackage?.let { telegram ->
                add(ContactActionKind.TELEGRAM, telegramIntent(e164, telegram), "Message $who on Telegram", telegram)
            }
            add(ContactActionKind.SIGNAL, signalIntent(e164), "Message $who on Signal", ContactActionKind.SIGNAL.packageName)
        }
        return actions
    }
}

/**
 * The contact buttons this device can answer, checked off the main thread each time the overlay resumes (a chat app
 * may have been installed or removed meanwhile). Nothing is checked while [enabled] is false. Until a check finishes,
 * the last one made in this process ([ContactActionTargets.cached]) stands in, so the buttons don't pop in on open.
 */
@Composable
internal fun rememberContactActionTargets(enabled: Boolean): ContactActionTargets {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targets by remember { mutableStateOf(ContactActionTargets.cached ?: ContactActionTargets.NONE) }
    LifecycleResumeEffect(enabled) {
        val job = if (enabled) {
            scope.launch {
                val resolved = withContext(Dispatchers.IO) { ContactActionTargets.resolve(context.applicationContext) }
                ContactActionTargets.cached = resolved
                targets = resolved
            }
        } else null
        onPauseOrDispose { job?.cancel() }
    }
    return targets
}
