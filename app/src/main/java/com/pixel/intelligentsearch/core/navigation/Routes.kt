package com.pixel.intelligentsearch.core.navigation
import kotlinx.serialization.Serializable

sealed class Route {
    @Serializable data object Main : Route()
    @Serializable data object Appearance : Route()
    @Serializable data object SearchSources : Route()
    @Serializable data object SearchBehavior : Route()
    @Serializable data object LaunchPortal : Route()
    @Serializable data object AppSearch : Route()
    @Serializable data object SearchPills : Route()
    @Serializable data object WebSearch : Route()
    @Serializable data object ContactSearch : Route()
    @Serializable data object FileSearch : Route()
    @Serializable data object WidgetCustomization : Route()
    @Serializable data object ManageHiddenApps : Route()
    @Serializable data object Debug : Route()
}
