package com.pixel.intelligentsearch.di

import com.pixel.intelligentsearch.core.performance.ADPFThermalManager
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import kotlinx.coroutines.CoroutineScope
import android.content.Context
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.search.CorpusIndexManager
import com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator
import com.pixel.intelligentsearch.core.security.BiometricSearchGate
import com.pixel.intelligentsearch.core.security.StrongBoxSecurityManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The app's singletons for places Hilt can't inject into (content providers, objects, composables,
 * AppFunction services). Prefer constructor injection wherever it is available.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun settingsManager(): SettingsManager
    fun multiProfileManager(): MultiProfileManager
    fun unifiedSearchCoordinator(): UnifiedSearchCoordinator
    fun corpusIndexManager(): CorpusIndexManager
    fun biometricSearchGate(): BiometricSearchGate
    fun strongBoxSecurityManager(): StrongBoxSecurityManager
    fun backupManager(): com.pixel.intelligentsearch.core.backup.BackupManager
    fun currencyRateRepository(): com.pixel.intelligentsearch.core.currency.CurrencyRateRepository
    /** The in-memory notification index behind Search Your Notifications and At a Glance's codes and timers. */
    fun notificationIndex(): com.pixel.intelligentsearch.core.glance.NotificationIndex

    @ApplicationScope
    fun applicationScope(): CoroutineScope

    fun pixelHapticEngine(): PixelHapticEngine
    fun tactileSonicEngine(): TactileSonicEngine
    fun adpfThermalManager(): ADPFThermalManager
}

/** The [AppEntryPoint] for this context's application. */
val Context.appEntryPoint: AppEntryPoint
    get() = EntryPointAccessors.fromApplication(applicationContext, AppEntryPoint::class.java)
