package com.pixel.intelligentsearch.core.navigation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable @SerialName("main") data object Main : Route()
    @Serializable @SerialName("appearance") data object Appearance : Route()
    @Serializable @SerialName("search_sources") data object SearchSources : Route()
    @Serializable @SerialName("search_behavior") data object SearchBehavior : Route()
    @Serializable @SerialName("launch_portal") data object LaunchPortal : Route()
    @Serializable @SerialName("app_search") data object AppSearch : Route()
    @Serializable @SerialName("search_pills") data object SearchPills : Route()
    @Serializable @SerialName("web_search") data object WebSearch : Route()
    @Serializable @SerialName("contact_search") data object ContactSearch : Route()
    @Serializable @SerialName("file_search") data object FileSearch : Route()
    @Serializable @SerialName("widget_customization") data object WidgetCustomization : Route()
    @Serializable @SerialName("manage_hidden_apps") data object ManageHiddenApps : Route()
    @Serializable @SerialName("custom_icons") data object CustomIcons : Route()
    @Serializable @SerialName("debug") data object Debug : Route()
    @Serializable @SerialName("backup_restore") data object BackupRestore : Route()
}
