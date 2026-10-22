package com.pixel.intelligentsearch.core.answers

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKit
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Translation on the phone. Each language's model is downloaded from Google once, and only when the user asks for
 * it; after that, identifying and translating text happens entirely on the device. Tests replace the engine.
 */
interface TranslationEngine {
    /** The language [text] is in, as one of [TranslationLanguages.CODES], or null when it can't be told. */
    suspend fun identifyLanguage(text: String): String?

    /** Whether [language]'s model is on the phone. */
    suspend fun isDownloaded(language: String): Boolean

    /** Downloads what translating [source] to [target] needs; on Wi-Fi only when [wifiOnly]. Throws when it fails. */
    suspend fun download(source: String, target: String, wifiOnly: Boolean)

    /** [text] in [target]. Throws when it can't be translated. */
    suspend fun translate(text: String, source: String, target: String): String
}

/**
 * [TranslationEngine] over ML Kit. ML Kit starts on first use rather than with the app (its start-up provider is
 * removed in the manifest), so nothing of it runs for someone who never translates.
 */
class MlKitTranslationEngine(context: Context) : TranslationEngine {
    private val appContext = context.applicationContext

    @Volatile private var started = false

    private fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            // Already started (a test host, or another library that kept the provider) is fine.
            runCatching { MlKit.initialize(appContext) }
            started = true
        }
    }

    private val identifier: LanguageIdentifier by lazy {
        start()
        LanguageIdentification.getClient()
    }

    /** The last two translators used, each holding its models in memory; older ones are closed. */
    private val translators = object : LinkedHashMap<String, Translator>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translator>?): Boolean {
            if (size <= 2) return false
            eldest?.value?.let { runCatching { it.close() } }
            return true
        }
    }

    private fun translator(source: String, target: String): Translator = synchronized(translators) {
        start()
        translators.getOrPut("$source>$target") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
    }

    override suspend fun identifyLanguage(text: String): String? {
        val tag = identifier.identifyLanguage(text).await()
        if (tag == LanguageIdentifier.UNDETERMINED_LANGUAGE_TAG) return null
        return TranslateLanguage.fromLanguageTag(tag)?.takeIf { it in TranslationLanguages.CODES }
    }

    override suspend fun isDownloaded(language: String): Boolean {
        // English is built into ML Kit; every other language is a download.
        if (language == TranslateLanguage.ENGLISH) return true
        start()
        val model = TranslateRemoteModel.Builder(language).build()
        return RemoteModelManager.getInstance().isModelDownloaded(model).await()
    }

    override suspend fun download(source: String, target: String, wifiOnly: Boolean) {
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        translator(source, target).downloadModelIfNeeded(conditions).await()
    }

    override suspend fun translate(text: String, source: String, target: String): String =
        translator(source, target).translate(text).await()
}

/** Waits for a Play services task, throwing what it fails with. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
