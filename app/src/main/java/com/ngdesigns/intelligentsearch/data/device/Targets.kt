package com.ngdesigns.intelligentsearch.data.device

import android.content.ComponentName
import android.os.UserHandle

/** Which profile something belongs to. Private Space only appears while it is unlocked. */
enum class Profile { Personal, Work, Private }

/** What opening a search result does. Each result's candidate id maps to one of these. */
sealed interface Target {
    data class App(val component: ComponentName, val user: UserHandle, val profile: Profile) : Target {
        val packageName: String get() = component.packageName
    }
    data class Shortcut(val packageName: String, val shortcutId: String, val user: UserHandle) : Target
    data class Contact(val lookupUri: String, val phone: String?) : Target
    data class Event(val eventId: Long, val beginMillis: Long, val endMillis: Long) : Target
    data class File(val uri: String, val mimeType: String?) : Target
    data class Setting(val key: String) : Target
    data class Query(val text: String) : Target
}

data class AppEntry(val label: String, val target: Target.App)

data class ShortcutEntry(val label: String, val longLabel: String, val target: Target.Shortcut, val appLabel: String)

data class ContactEntry(val name: String, val phones: List<String>, val photoUri: String?, val starred: Boolean, val target: Target.Contact)

data class EventEntry(val title: String, val location: String?, val allDay: Boolean, val target: Target.Event)

data class FileEntry(val name: String, val sizeBytes: Long, val modifiedMillis: Long, val target: Target.File)
