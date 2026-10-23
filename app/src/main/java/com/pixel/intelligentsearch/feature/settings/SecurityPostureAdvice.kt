package com.pixel.intelligentsearch.feature.settings

import android.content.Intent
import android.provider.Settings
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.security.SecurityPosture.Axis
import com.pixel.intelligentsearch.core.security.SecurityPosture.Rating

/**
 * Something on the phone that would raise a posture check, and where Android lets the user do it. Only the checks a
 * user can change from the phone have one: the screen lock and the security update.
 */
internal enum class PostureFix(val label: String) {
    SetScreenLock("Set Screen Lock"),
    CheckForUpdates("Check for Updates");

    /** Android's pages for it, best first; the first one that opens is used. */
    fun intents(): List<Intent> = when (this) {
        SetScreenLock -> listOf(Intent(Settings.ACTION_SECURITY_SETTINGS))
        // The update page has no public action; the device info page is where Android points to it otherwise.
        CheckForUpdates -> listOf(Intent("android.settings.SYSTEM_UPDATE_SETTINGS"), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
    }.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}

/** What a posture check means for this phone, in a sentence or two, and the [fix] for it when there is one. */
internal data class PostureAdvice(val text: String, val fix: PostureFix?)

/** A score at or above this is full marks: nothing is missing, so there is no gap to show. */
internal const val FULL_MARKS = 0.999f

/** What [score] means and what, if anything, the user can do about it. */
internal fun postureAdvice(score: SecurityPosture.AxisScore): PostureAdvice {
    if (score.rating == Rating.Pending) return PostureAdvice("Still checking. This fills in once the hardware has answered.", null)
    return when (score.axis) {
        Axis.KeyStorage -> when (score.value) {
            "StrongBox" -> PostureAdvice("Your keys live in a separate security chip, the strongest place Android offers. Nothing to do.", null)
            "TEE" -> PostureAdvice("Your keys live in the processor's secure area (TrustZone). Phones with a separate security chip (StrongBox) keep them safer still.", null)
            else -> PostureAdvice("This phone keeps keys in software only, with no hardware to protect them. Only a phone with secure hardware can raise this.", null)
        }
        Axis.Attestation -> when (score.rating) {
            Rating.Good -> PostureAdvice("The security chip proved your key is real hardware, signed all the way back to Google's root. Nothing to do.", null)
            Rating.Fair -> PostureAdvice("The key's certificate leads back to Google's software root, not a hardware one, so the hardware can't be confirmed.", null)
            else -> PostureAdvice("The phone couldn't prove its key is in secure hardware. This is common on modified or uncertified phones.", null)
        }
        Axis.VerifiedBoot -> when (score.value) {
            "Verified" -> PostureAdvice("At startup Android checked that the system hasn't been changed. Nothing to do.", null)
            "Self-Signed" -> PostureAdvice("The phone runs a custom system signed by its owner and relocked. It is checked at startup, but not by the maker.", null)
            "Unconfirmed" -> PostureAdvice("Without a hardware-verified certificate, the boot state can't be confirmed.", null)
            else -> PostureAdvice("Startup can't confirm the system is unmodified. Reinstalling the maker's factory image restores it.", null)
        }
        Axis.PatchLevel -> when {
            score.value == "Current" -> PostureAdvice("You have this month's security update. Nothing to do.", null)
            score.value == "Unknown" -> PostureAdvice("The phone doesn't say when it was last patched. Check for a system update.", PostureFix.CheckForUpdates)
            score.rating == Rating.Good -> PostureAdvice("Your last security update is ${score.value.lowercase()}. A newer one may be ready.", PostureFix.CheckForUpdates)
            else -> PostureAdvice("Your last security update is ${score.value.lowercase()}, so fixes since then are missing. Check for a system update.", PostureFix.CheckForUpdates)
        }
        Axis.Bootloader -> when (score.value) {
            "Locked" -> PostureAdvice("The bootloader is locked, so nobody can replace the system without wiping the phone. Nothing to do.", null)
            "Unlocked" -> PostureAdvice("The bootloader is unlocked, so anyone holding the phone can install another system. Relocking it takes a computer and wipes the phone.", null)
            else -> PostureAdvice("The bootloader's lock can't be confirmed without a hardware-verified certificate.", null)
        }
        Axis.ScreenLock -> if (score.rating == Rating.Good) {
            PostureAdvice("A PIN, pattern or password protects this phone and its keys. Nothing to do.", null)
        } else {
            PostureAdvice("There's no screen lock, so anyone holding the phone can open it. Set a PIN, pattern or password.", PostureFix.SetScreenLock)
        }
    }
}
