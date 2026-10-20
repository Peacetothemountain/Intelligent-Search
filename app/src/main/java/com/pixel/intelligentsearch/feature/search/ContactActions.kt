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

/** A button on a contact result. Chat apps carry the package they must open in, so no other app gets the number. */
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

        /** Official Telegram builds (Play and direct download); only these may receive a contact's number. */
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
     * (the SIM's country). Null when it can't be made international, e.g. a short code or an unknown country.
     */
    fun toE164(number: String, countryIso: String): String? {
        val trimmed = number.trim()
        if (trimmed.none { it.isDigit() }) return null
        runCatching { PhoneNumberUtils.formatNumberToE164(trimmed, countryIso.uppercase(Locale.ROOT)) }
            .getOrNull()?.let { return it }
        // Without the platform's metadata, a number that already says its country code is still usable.
        val digits = trimmed.filter { it.isDigit() }
        val international = when {
            trimmed.startsWith("+") -> digits
            trimmed.startsWith("00") -> digits.removePrefix("00")
            else -> return null
        }
        return if (international.length in 8..15 && !international.startsWith("0")) "+$international" else null
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
     * once the number can be written internationally. Nothing at all when the Contact Actions setting is off.
     */
    fun forContact(contact: ContactItem, targets: ContactActionTargets, enabled: Boolean = true): List<ContactAction> {
        if (!enabled) return emptyList()
        val who = contact.name.ifBlank { contact.phoneNumber.ifBlank { contact.email } }
        val number = contact.phoneNumber.trim()
        val e164 = if (number.isNotEmpty()) toE164(number, targets.countryIso) else null
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
 * may have been installed or removed meanwhile). Nothing is checked while [enabled] is false.
 */
@Composable
internal fun rememberContactActionTargets(enabled: Boolean): ContactActionTargets {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targets by remember { mutableStateOf(ContactActionTargets.NONE) }
    LifecycleResumeEffect(enabled) {
        val job = if (enabled) {
            scope.launch {
                targets = withContext(Dispatchers.IO) { ContactActionTargets.resolve(context.applicationContext) }
            }
        } else null
        onPauseOrDispose { job?.cancel() }
    }
    return targets
}
