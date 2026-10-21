package com.pixel.intelligentsearch.feature.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.speech.RecognitionService
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Which installed recognition service the assistant's speech recognizer forwards the microphone to. Any app can
 * declare one under any package name, so a system recognizer wins over an installed one, whatever it is called.
 */
@RunWith(RobolectricTestRunner::class)
class RecognitionDelegateTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Installs [packageName] with one recognition service, as part of the system image when [system]. */
    private fun install(packageName: String, system: Boolean) {
        val app = ApplicationInfo().apply {
            this.packageName = packageName
            flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
        }
        val service = ServiceInfo().apply {
            this.packageName = packageName
            name = "$packageName.Recognizer"
            applicationInfo = app
            exported = true
        }
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = app
        })
        pm.addOrUpdateService(service)
        pm.addIntentFilterForService(ComponentName(packageName, service.name), IntentFilter(RecognitionService.SERVICE_INTERFACE))
    }

    /** What the service does: every recognition service on the phone, its own included, then the choice. */
    private fun chosen(): ComponentName? {
        val services = context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), PackageManager.ResolveInfoFlags.of(0L))
            .mapNotNull { it.serviceInfo }
        return chooseRecognitionDelegate(services, context.packageName)
    }

    @Test
    fun aSystemRecognizerWinsOverAnInstalledOneWithAGoogleName() {
        install("com.google.evil", system = false)
        install("com.vendor.speech", system = true)
        assertEquals("com.vendor.speech", chosen()?.packageName)
    }

    @Test
    fun googlesOwnRecognizerWinsAmongSystemOnes() {
        install("com.vendor.speech", system = true)
        install("com.google.android.tts", system = true)
        assertEquals(ComponentName("com.google.android.tts", "com.google.android.tts.Recognizer"), chosen())
    }

    @Test
    fun withNoSystemRecognizerAnInstalledOneIsStillUsed() {
        // A de-Googled phone: the only recognizer is one the user installed.
        install("org.example.speech", system = false)
        assertEquals("org.example.speech", chosen()?.packageName)
    }

    @Test
    fun itNeverForwardsToItself() {
        // Only this app's own recognizer is declared, so there is nothing to forward to.
        assertNull(chosen())
    }
}
