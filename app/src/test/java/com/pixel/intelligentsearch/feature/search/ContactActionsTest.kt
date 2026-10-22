package com.pixel.intelligentsearch.feature.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.feature.search.ContactActionKind.CALL
import com.pixel.intelligentsearch.feature.search.ContactActionKind.EMAIL
import com.pixel.intelligentsearch.feature.search.ContactActionKind.MESSAGE
import com.pixel.intelligentsearch.feature.search.ContactActionKind.SIGNAL
import com.pixel.intelligentsearch.feature.search.ContactActionKind.TELEGRAM
import com.pixel.intelligentsearch.feature.search.ContactActionKind.WHATSAPP
import com.pixel.intelligentsearch.feature.search.ContactActionKind.WHATSAPP_BUSINESS
import java.util.Locale
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
    fun openingAContactDialsEveryCharacterOfItsNumber() {
        // Parsed as "tel:*86#" the "#" starts a fragment, and the dialler got "*86".
        val voicemail = ContactItem("Voicemail", "*86#", "")
        val dial = ContactActions.openIntent(voicemail, directCall = true)
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals("*86#", dial.data!!.schemeSpecificPart)
        // Without a card to show, the number is dialled even when the setting says to open the card.
        assertEquals("*86#", ContactActions.openIntent(voicemail, directCall = false).data!!.schemeSpecificPart)
    }

    @Test
    fun openingAContactShowsItsCardUnlessTheSettingSaysCall() {
        val card = ContactActions.openIntent(alex, directCall = false)
        assertEquals(Intent.ACTION_VIEW, card.action)
        assertEquals(alex.lookupUri, card.dataString)
        assertEquals("(415) 555-2671", ContactActions.openIntent(alex, directCall = true).data!!.schemeSpecificPart)
        // Nothing to dial or show: the Contacts app.
        val nothing = ContactActions.openIntent(ContactItem("Nobody", "", ""), directCall = true)
        assertEquals(android.provider.ContactsContract.Contacts.CONTENT_URI, nothing.data)
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
    fun aNumberWithAnExtensionNeverReachesAnotherNumber() {
        // Read with its extension as part of the number, each of these is someone else's (or no one's) number.
        assertNull(ContactActions.toE164("+44 7911 12345 x6", "US"))
        assertNull(ContactActions.toE164("+1 415 555 267 ext 1234", "US"))
    }

    @Test
    fun aPauseOrWaitWrittenAsALetterNeverReachesAnotherNumber() {
        // With the 6 after the pause or wait glued on, each would be +447911123456: a valid number, someone else's.
        for (saved in listOf("+44 7911 12345 p6", "+44 7911 12345 w6", "+44 7911 12345 P6", "+44 7911 12345 W6")) {
            assertNull(saved, ContactActions.toE164(saved, "US"))
            // Calls and texts still dial the number as saved; only the chat buttons go.
            val actions = ContactActions.forContact(alex.copy(phoneNumber = saved, email = ""), everything)
            assertEquals(saved, listOf(CALL, MESSAGE), actions.map { it.kind })
            assertEquals(saved, saved, actions.single { it.kind == CALL }.intent.data!!.schemeSpecificPart)
            assertEquals(saved, saved, actions.single { it.kind == MESSAGE }.intent.data!!.schemeSpecificPart)
        }
    }

    @Test
    fun aValidNumberKeepsWorkingWithoutItsPauseOrWait() {
        for (saved in listOf("+44 20 7946 0018p6", "+44 20 7946 0018 w6", "+44 20 7946 0018P6", "+44 20 7946 0018 W 6")) {
            assertEquals(saved, "+442079460018", ContactActions.toE164(saved, "US"))
        }
    }

    @Test
    fun onlyAPlusAndUpTo15AsciiDigitsEverReachAChatLink() {
        // Whatever the formatter hands back, a chat link only ever carries a well-formed E.164 number.
        for (malformed in listOf(
            "+44 20 7946 0018", "442079460018", "+0442079460018", "+4420794600181234", "+٤٤٢٠٧٩٤٦٠٠١٨", "+44207946001x", ""
        )) {
            assertNull(malformed, ContactActions.toE164("+44 20 7946 0018", "US") { _, _ -> malformed })
        }
        assertEquals("+442079460018", ContactActions.toE164("+44 20 7946 0018", "US") { _, _ -> "+442079460018" })
    }

    @Test
    fun aNumberInNoCountryGivesNothing() {
        assertNull(ContactActions.toE164("+999 123456789", "US"))
    }

    @Test
    fun aValidNumberKeepsWorkingWithoutItsExtension() {
        for (saved in listOf(
            "+44 20 7946 0018 x6", "+44 20 7946 0018 X 6", "+44 20 7946 0018 ext 6", "+44 20 7946 0018 ext. 6",
            "+44 20 7946 0018 extension 6", "+44 20 7946 0018,6", "+44 20 7946 0018;6", "+44 20 7946 0018#6"
        )) {
            assertEquals(saved, "+442079460018", ContactActions.toE164(saved, "US"))
        }
        assertEquals("+14155552671", ContactActions.toE164("(415) 555-2671 ext. 99", "US"))
    }

    @Test
    fun otherScriptsDigitsBecomeAsciiDigits() {
        // Arabic-Indic and Extended Arabic-Indic (Persian) digits.
        assertEquals("+442079460018", ContactActions.toE164("+٤٤ ٢٠ ٧٩٤٦ ٠٠١٨", "US"))
        assertEquals("+442079460018", ContactActions.toE164("+۴۴ ۲۰ ۷۹۴۶ ۰۰۱۸", "US"))
        val contact = alex.copy(phoneNumber = "+٤٤ ٢٠ ٧٩٤٦ ٠٠١٨")
        assertEquals("https://wa.me/442079460018", ContactActions.forContact(contact, everything).single { it.kind == WHATSAPP }.intent.dataString)
    }

    @Test
    fun withoutThePlatformsMetadataANumberWithItsCountryCodeIsStillUsable() {
        val noMetadata: (String, String) -> String? = { _, _ -> throw NoClassDefFoundError("com.android.i18n.phonenumbers.PhoneNumberUtil") }
        assertEquals("+442079460018", ContactActions.toE164("+44 20 7946 0018", "US", noMetadata))
        assertEquals("+442079460018", ContactActions.toE164("0044 20 7946 0018", "US", noMetadata))
        // Even then an extension is dropped and other digits become ASCII.
        assertEquals("+442079460018", ContactActions.toE164("+44 20 7946 0018 x6", "US", noMetadata))
        assertEquals("+442079460018", ContactActions.toE164("+٤٤ ٢٠ ٧٩٤٦ ٠٠١٨", "US", noMetadata))
        // A local number can't be placed without the metadata, and a number too short to be one is refused.
        assertNull(ContactActions.toE164("020 7946 0018", "GB", noMetadata))
        assertNull(ContactActions.toE164("+44 123", "GB", noMetadata))
    }

    @Test
    fun aNumberThePlatformFindsInvalidIsNeverFixedUp() {
        val invalid: (String, String) -> String? = { _, _ -> null }
        assertNull(ContactActions.toE164("+44 20 7946 0018", "US", invalid))
        assertNull(ContactActions.toE164("0044 20 7946 0018", "US", invalid))
    }

    @Test
    fun anInvalidNumberStillGetsCallAndMessageAsSavedButNoChatButtons() {
        val wrong = alex.copy(phoneNumber = "+44 7911 12345 x6", email = "")
        val actions = ContactActions.forContact(wrong, everything)
        assertEquals(listOf(CALL, MESSAGE), actions.map { it.kind })
        assertEquals("+44 7911 12345 x6", actions.single { it.kind == CALL }.intent.data!!.schemeSpecificPart)
        assertEquals("+44 7911 12345 x6", actions.single { it.kind == MESSAGE }.intent.data!!.schemeSpecificPart)
    }

    @Test
    fun aValidNumberWithAnExtensionDialsAsSavedAndChatsWithoutIt() {
        val withExtension = alex.copy(phoneNumber = "+1 415-555-2671 ext. 99")
        val actions = ContactActions.forContact(withExtension, everything)
        assertEquals("+1 415-555-2671 ext. 99", actions.single { it.kind == CALL }.intent.data!!.schemeSpecificPart)
        assertEquals("https://wa.me/14155552671", actions.single { it.kind == WHATSAPP }.intent.dataString)
        assertEquals("https://signal.me/#p/+14155552671", actions.single { it.kind == SIGNAL }.intent.dataString)
    }

    @Test
    fun chatButtonsUseTheChatNumberWhileCallAndMessageUseTheShownOne() {
        val landlineFirst = alex.copy(phoneNumber = "020 7946 0018", chatNumber = "+1 415-555-2671")
        val actions = ContactActions.forContact(landlineFirst, everything.copy(countryIso = "GB"))
        assertEquals("020 7946 0018", actions.single { it.kind == CALL }.intent.data!!.schemeSpecificPart)
        assertEquals("020 7946 0018", actions.single { it.kind == MESSAGE }.intent.data!!.schemeSpecificPart)
        assertEquals("https://wa.me/14155552671", actions.single { it.kind == WHATSAPP }.intent.dataString)
    }

    @Test
    fun localNumbersBelongToTheSimsCountryThenTheNetworksThenTheLocales() {
        val telephony = shadowOf(context.getSystemService(TelephonyManager::class.java))
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            telephony.setSimCountryIso("gb")
            telephony.setNetworkCountryIso("de")
            assertEquals("GB", ContactActions.deviceCountryIso(context))
            telephony.setSimCountryIso("")
            assertEquals("DE", ContactActions.deviceCountryIso(context))
            telephony.setNetworkCountryIso("")
            assertEquals("FR", ContactActions.deviceCountryIso(context))
        } finally {
            Locale.setDefault(locale)
        }
    }

    @Test
    fun theResolvedTargetsCarryTheSimsCountry() {
        shadowOf(context.getSystemService(TelephonyManager::class.java)).setSimCountryIso("gb")
        assertEquals("GB", ContactActionTargets.resolve(context).countryIso)
    }

    /**
     * Contact results come from the phone-number table, so the overlay never shows a contact without a number; this
     * pins [ContactActions.forContact]'s own contract for one anyway.
     */
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
