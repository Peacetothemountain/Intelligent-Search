package com.pixel.intelligentsearch.feature.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.feature.search.ContactActionKind.CALL
import com.pixel.intelligentsearch.feature.search.ContactActionKind.EMAIL
import com.pixel.intelligentsearch.feature.search.ContactActionKind.MESSAGE
import com.pixel.intelligentsearch.feature.search.ContactActionKind.SIGNAL
import com.pixel.intelligentsearch.feature.search.ContactActionKind.TELEGRAM
import com.pixel.intelligentsearch.feature.search.ContactActionKind.WHATSAPP
import com.pixel.intelligentsearch.feature.search.ContactActionKind.WHATSAPP_BUSINESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ContactActionsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val alex = ContactItem("Alex", "(415) 555-2671", "content://contacts/lookup/a/1", email = "alex@example.com")
    private val everything = ContactActionTargets(ContactActionKind.entries.toSet(), "org.telegram.messenger", "US")

    @Test
    fun eachButtonOpensTheContactsOwnNumberOrAddress() {
        val call = ContactActions.callIntent("+1 415-555-2671")
        assertEquals(Intent.ACTION_DIAL, call.action)
        assertEquals("tel", call.data!!.scheme)
        assertEquals("+1 415-555-2671", call.data!!.schemeSpecificPart)

        val sms = ContactActions.messageIntent("+14155552671")
        assertEquals(Intent.ACTION_SENDTO, sms.action)
        assertEquals("smsto", sms.data!!.scheme)
        assertEquals("+14155552671", sms.data!!.schemeSpecificPart)

        val email = ContactActions.emailIntent("alex@example.com")
        assertEquals(Intent.ACTION_SENDTO, email.action)
        assertEquals("mailto", email.data!!.scheme)
        assertEquals("alex@example.com", email.data!!.schemeSpecificPart)
    }

    @Test
    fun chatLinksFollowEachAppsDocumentedFormatAndStayInThatApp() {
        val whatsApp = ContactActions.whatsAppIntent("+14155552671", "com.whatsapp")
        assertEquals(Intent.ACTION_VIEW, whatsApp.action)
        assertEquals("https://wa.me/14155552671", whatsApp.dataString)
        assertEquals("com.whatsapp", whatsApp.`package`)
        assertEquals("com.whatsapp.w4b", ContactActions.whatsAppIntent("+14155552671", "com.whatsapp.w4b").`package`)

        val telegram = ContactActions.telegramIntent("+14155552671", "org.telegram.messenger")
        assertEquals("tg://resolve?phone=14155552671", telegram.dataString)
        assertEquals("org.telegram.messenger", telegram.`package`)

        val signal = ContactActions.signalIntent("+14155552671")
        assertEquals("https://signal.me/#p/+14155552671", signal.dataString)
        assertEquals("org.thoughtcrime.securesms", signal.`package`)
    }

    @Test
    fun numbersBecomeE164UsingTheSimCountryForLocalOnes() {
        assertEquals("+14155552671", ContactActions.toE164("(415) 555-2671", "US"))
        assertEquals("+14155552671", ContactActions.toE164("415-555-2671", "us"))
        assertEquals("+442079460018", ContactActions.toE164("020 7946 0018", "GB"))
        // A saved country code wins over the SIM's country.
        assertEquals("+442079460018", ContactActions.toE164("+44 20 7946 0018", "US"))
    }

    @Test
    fun numbersThatCantBeMadeInternationalGiveNothing() {
        assertNull(ContactActions.toE164("", "US"))
        assertNull(ContactActions.toE164("Office", "US"))
        assertNull(ContactActions.toE164("112", "DE")) // emergency short code
        assertNull(ContactActions.toE164("020 7946 0018", "")) // local number, country unknown
    }

    @Test
    fun aContactWithANumberAndAnEmailGetsEveryAvailableButton() {
        val actions = ContactActions.forContact(alex, everything)
        assertEquals(listOf(CALL, MESSAGE, EMAIL, WHATSAPP, WHATSAPP_BUSINESS, TELEGRAM, SIGNAL), actions.map { it.kind })
        assertEquals(
            listOf(
                "Call Alex", "Message Alex", "Email Alex", "Message Alex on WhatsApp", "Message Alex on WhatsApp Business",
                "Message Alex on Telegram", "Message Alex on Signal"
            ),
            actions.map { it.contentDescription }
        )
        // Chat links carry the contact's saved number, normalised; never anything typed.
        assertEquals("https://wa.me/14155552671", actions.single { it.kind == WHATSAPP }.intent.dataString)
        assertEquals("org.telegram.messenger", actions.single { it.kind == TELEGRAM }.iconPackage)
        assertNull(actions.single { it.kind == CALL }.iconPackage)
    }

    @Test
    fun appsThatArentInstalledGetNoButton() {
        val onlyPhone = ContactActionTargets(setOf(CALL, MESSAGE), countryIso = "US")
        assertEquals(listOf(CALL, MESSAGE), ContactActions.forContact(alex, onlyPhone).map { it.kind })
        assertTrue(ContactActions.forContact(alex, ContactActionTargets.NONE).isEmpty())
    }

    @Test
    fun aContactWithoutANumberGetsOnlyEmail() {
        val emailOnly = alex.copy(phoneNumber = "")
        assertEquals(listOf(EMAIL), ContactActions.forContact(emailOnly, everything).map { it.kind })
    }

    @Test
    fun aContactWithoutAnEmailGetsNoEmailButton() {
        val noEmail = alex.copy(email = "")
        assertTrue(ContactActions.forContact(noEmail, everything).none { it.kind == EMAIL })
    }

    @Test
    fun aNumberThatCantBeMadeInternationalGetsNoChatButtons() {
        val shortCode = alex.copy(phoneNumber = "*123#", email = "")
        assertEquals(listOf(CALL, MESSAGE), ContactActions.forContact(shortCode, everything).map { it.kind })
    }

    @Test
    fun theSettingOffHidesEveryButton() {
        assertTrue(ContactActions.forContact(alex, everything, enabled = false).isEmpty())
    }

    @Test
    fun withNothingInstalledNoButtonResolves() {
        val targets = ContactActionTargets.resolve(context)
        assertTrue(targets.available.isEmpty())
        assertNull(targets.telegramPackage)
    }

    @Test
    fun onlyInstalledAppsThatHandleTheirLinkResolve() {
        install("com.android.dialer", IntentFilter(Intent.ACTION_DIAL).apply { addDataScheme("tel") })
        install("com.google.android.apps.messaging", IntentFilter(Intent.ACTION_SENDTO).apply { addDataScheme("smsto") })
        install("com.whatsapp", IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("https")
            addDataAuthority("wa.me", null)
        })
        install("org.telegram.messenger.web", IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("tg") })

        val targets = ContactActionTargets.resolve(context)
        assertEquals(setOf(CALL, MESSAGE, WHATSAPP, TELEGRAM), targets.available)
        assertEquals("org.telegram.messenger.web", targets.telegramPackage)
    }

    @Test
    fun anUnofficialAppClaimingTelegramLinksDoesntGetTheNumber() {
        install("org.example.tgclone", IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("tg") })
        val targets = ContactActionTargets.resolve(context)
        assertTrue(TELEGRAM !in targets.available)
        assertNull(targets.telegramPackage)
    }

    @Test
    fun aBrowserAloneDoesntMakeWhatsAppOrSignalAvailable() {
        install("org.example.browser", IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("https")
        })
        assertTrue(ContactActionTargets.resolve(context).available.isEmpty())
    }

    private fun install(packageName: String, filter: IntentFilter) {
        val component = ComponentName(packageName, "$packageName.Main")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(component)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        pm.addIntentFilterForActivity(component, filter)
    }
}
