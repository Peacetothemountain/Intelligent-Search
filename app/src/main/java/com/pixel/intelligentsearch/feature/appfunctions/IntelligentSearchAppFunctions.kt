package com.pixel.intelligentsearch.feature.appfunctions

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine
import com.pixel.intelligentsearch.core.security.AdvancedProtectionMonitor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** An installed app that matched a search. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppSearchResult(
    /** The app's display name. */
    val name: String,
    /** The app's package name, which can be used to launch it. */
    val packageName: String
)

/** A contact that matched a search. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ContactSearchResult(
    /** The contact's display name. */
    val name: String,
    /** The contact's primary phone number, or an empty string if none is saved. */
    val phoneNumber: String
)

/** The result of a calculation or unit conversion. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CalculationResult(
    /** The expression that was evaluated. */
    val expression: String,
    /** The formatted answer, for example "42" or "62.14 mi". */
    val result: String
)

/**
 * Lets on-device assistants use Intelligent Search's local index. Everything runs on the device
 * and nothing is sent over the network. Hidden apps are never returned, and contact search only
 * works when the user has granted contacts access and Advanced Protection is off.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "IntelligentSearchAppFunctionService",
    appFunctionXmlFileName = "intelligent_search_app_functions"
)
abstract class IntelligentSearchAppFunctions : AppFunctionService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsEntryPoint {
        fun settingsManager(): SettingsManager
    }

    private suspend fun currentSettings(): IntelligentSearchSettings? = try {
        EntryPointAccessors.fromApplication(applicationContext, SettingsEntryPoint::class.java)
            .settingsManager()
            .settingsFlow
            .first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun hiddenApps(): Set<String> = currentSettings()?.hiddenApps ?: emptySet()

    /**
     * Searches the apps installed on this device by name.
     *
     * @param query Part of the app name to look for, for example "maps" or "cam".
     * @param limit The maximum number of apps to return, between 1 and 20.
     * @return The matching apps, best matches first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun searchApps(query: String, limit: Int): List<AppSearchResult> {
        if (!androidx.core.os.UserManagerCompat.isUserUnlocked(applicationContext)) return emptyList()
        val needle = query.trim().take(128)
        if (needle.isEmpty()) return emptyList()
        val hidden = hiddenApps()
        val max = limit.coerceIn(1, 20)
        return SystemDataProvider.getAllApps(applicationContext)
            .asSequence()
            .filter { it.packageName !in hidden && !it.isPrivateProfile }
            .filter { it.name.contains(needle, ignoreCase = true) }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<com.pixel.intelligentsearch.core.data.AppItem> {
                    it.name.startsWith(needle, ignoreCase = true)
                }.thenBy { it.name.lowercase() }
            )
            .take(max)
            .map { AppSearchResult(name = it.name, packageName = it.packageName) }
            .toList()
    }

    /**
     * Searches the user's contacts by name or number.
     *
     * Returns an empty list if contact search is turned off in Intelligent Search, contacts access
     * hasn't been granted, or Android Advanced Protection is on.
     *
     * @param query Part of a name or phone number, for example "alex" or "555".
     * @param limit The maximum number of contacts to return, between 1 and 10.
     * @return The matching contacts.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun searchContacts(query: String, limit: Int): List<ContactSearchResult> {
        if (!androidx.core.os.UserManagerCompat.isUserUnlocked(applicationContext)) return emptyList()
        val needle = query.trim().take(128)
        if (needle.isEmpty()) return emptyList()
        if (AdvancedProtectionMonitor.isEnabled(applicationContext)) return emptyList()
        // Respect the in-app contact search switch; fail closed if settings can't be read.
        if (currentSettings()?.searchContacts != true) return emptyList()
        if (applicationContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return SystemDataProvider.getContacts(applicationContext, needle)
            .take(limit.coerceIn(1, 10))
            .map { ContactSearchResult(name = it.name, phoneNumber = it.phoneNumber) }
    }

    /**
     * Evaluates a math expression, base conversion or unit conversion offline.
     *
     * @param expression For example "sqrt(144) + 3", "255 to hex" or "100 km to miles".
     * @return The answer, or null if the expression can't be evaluated.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    fun calculate(expression: String): CalculationResult? {
        val input = expression.trim().take(256)
        val text = when (val result = MathematicalExpressionEngine.evaluate(input) ?: return null) {
            is MathematicalExpressionEngine.MathEvaluationResult.Computation -> result.formattedResult
            is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion -> result.formatted
            is MathematicalExpressionEngine.MathEvaluationResult.Bitwise -> result.expression.substringAfterLast(" = ")
        }
        return CalculationResult(expression = input, result = text)
    }
}
