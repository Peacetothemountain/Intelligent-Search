package com.pixel.intelligentsearch.core.data

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * The device data search pulls in: installed and recent apps, app usage, upcoming calendar events,
 * contacts, files and web suggestions. Injected rather than called statically, so the search ViewModel and the
 * index can be tested with stand-ins.
 */
interface DeviceSearchSources {
    suspend fun allApps(forceRefresh: Boolean = false): List<AppItem>
    /** Drops the cached app list so the next [allApps] re-reads the device. */
    fun invalidateAppsCache()
    fun appUsage(): Map<String, SystemDataProvider.AppUsage>
    suspend fun allContacts(): List<ContactItem>
    suspend fun recentFiles(limit: Int, includeHidden: Boolean): List<FileItem>
    suspend fun recentApps(hiddenApps: Set<String>): List<AppItem>
    suspend fun contextAwareQuickApps(hiddenApps: Set<String>): List<AppItem>
    suspend fun upcomingEvents(): List<CalendarEvent>
    suspend fun contacts(query: String): List<ContactItem>
    /** Suggestions already fetched for [query], or null when none are cached. */
    fun cachedWebSuggestions(query: String, engine: String): List<String>?
    suspend fun webSuggestions(query: String, engine: String): List<String>
}

/** The real device, through the existing providers. */
class DeviceSearchSourcesImpl @Inject constructor(@ApplicationContext private val context: Context) : DeviceSearchSources {
    override suspend fun allApps(forceRefresh: Boolean): List<AppItem> = SystemDataProvider.getAllApps(context, forceRefresh)
    override fun invalidateAppsCache() = SystemDataProvider.invalidateAppsCache()
    override fun appUsage(): Map<String, SystemDataProvider.AppUsage> = SystemDataProvider.getAppUsage(context)
    override suspend fun allContacts(): List<ContactItem> = SystemDataProvider.getAllContacts(context)
    override suspend fun recentFiles(limit: Int, includeHidden: Boolean): List<FileItem> =
        SystemDataProvider.getAllRecentFiles(context, limit = limit, includeHidden = includeHidden)
    override suspend fun recentApps(hiddenApps: Set<String>): List<AppItem> = SystemDataProvider.getRecentApps(context, hiddenApps)
    override suspend fun contextAwareQuickApps(hiddenApps: Set<String>): List<AppItem> = SystemDataProvider.getContextAwareQuickApps(context, hiddenApps)
    override suspend fun upcomingEvents(): List<CalendarEvent> = SystemDataProvider.getUpcomingEvents(context)
    override suspend fun contacts(query: String): List<ContactItem> = SystemDataProvider.getContacts(context, query)
    override fun cachedWebSuggestions(query: String, engine: String): List<String>? = WebSearchProvider.getCachedSuggestions(query, engine)
    override suspend fun webSuggestions(query: String, engine: String): List<String> = WebSearchProvider.getWebSuggestions(query, engine)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceSearchSourcesModule {
    @Binds
    abstract fun bindDeviceSearchSources(impl: DeviceSearchSourcesImpl): DeviceSearchSources
}
