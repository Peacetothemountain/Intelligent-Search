package com.pixel.intelligentsearch.core.search

import androidx.compose.runtime.Immutable
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ProfileType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The kinds of result the user can pin, by the prefix of their [ResultKeys] key. */
enum class ResultKind(val prefix: String) {
    APP("app"),
    CONTACT("contact"),
    FILE("file"),
    SETTING("setting")
}

/**
 * Stable names for results, so a pin still finds its result after the index is rebuilt,
 * the app restarts or a backup is restored: an app by its package, launcher activity and profile, a contact by its
 * lookup key (which survives the contact's row id changing), a file by its document URI and an Android Settings page
 * by its catalogue id.
 */
object ResultKeys {

    /**
     * "app:<package>/<activity>@<profile>": the profile by its kind, "0" for the personal one and "work", "clone" or
     * "private" for the others. Not by Android's number for it, which changes when a work profile is made again and
     * differs on another phone or user a backup is restored to.
     */
    fun app(app: AppItem): String =
        "${ResultKind.APP.prefix}:${app.packageName}/${app.activityName.orEmpty()}@${profileTag(app.profileType)}"

    private fun profileTag(type: ProfileType): String = when (type) {
        ProfileType.PERSONAL -> PERSONAL_TAG
        ProfileType.WORK -> "work"
        ProfileType.CLONE -> "clone"
        ProfileType.PRIVATE -> PRIVATE_TAG
    }

    private const val PERSONAL_TAG = "0"
    private const val PRIVATE_TAG = "private"

    /** The profile an app [key] names: a tag [app] writes, or an older version's Android user id. */
    private fun appProfile(key: String): String = key.substringAfterLast('@', "")

    /**
     * Whether [key] names something that may be pinned or remembered: a kind this version writes, and for an app a
     * profile, never a Private Space (whose apps leave no trace).
     */
    fun isKept(key: String): Boolean = when (kindOf(key)) {
        null -> false
        ResultKind.APP -> appProfile(key).let { it in KEPT_PROFILES || isOlderProfile(it) }
        else -> true
    }

    private val KEPT_PROFILES = setOf(PERSONAL_TAG, "work", "clone")

    private fun isOlderProfile(tag: String) = tag != PERSONAL_TAG && tag.isNotEmpty() && tag.length <= 9 && tag.all { it in '0'..'9' }

    /** Whether [key] names its app's profile the way an older version did, by its Android user id. */
    fun isOlderAppKey(key: String): Boolean = kindOf(key) == ResultKind.APP && isOlderProfile(appProfile(key))

    /**
     * [key] as this version writes it, given the phone's [profiles] now (each one's Android user id and kind). Older
     * versions named an app's profile by that id: a pinned work app keeps its pin under "work", and an id no profile
     * has any more (a work profile removed, or a backup from another phone or user), or a Private Space's, gives null,
     * so it is dropped rather than kept where it never shows and can't be unpinned. Other keys are kept as they are.
     */
    fun current(key: String, profiles: Map<Int, ProfileType>): String? {
        if (!isOlderAppKey(key)) return key.takeIf(::isKept)
        val type = profiles[appProfile(key).toInt()] ?: return null
        if (type == ProfileType.PRIVATE) return null
        return key.substringBeforeLast('@') + "@" + profileTag(type)
    }

    /** "contact:<lookup key>", or null for a contact with no lookup URI (nothing to find it by again). */
    fun contact(contact: ContactItem): String? {
        val uri = contact.lookupUri.trim()
        if (uri.isEmpty()) return null
        // content://com.android.contacts/contacts/lookup/<lookup key>/<row id>: only the lookup key is stable.
        val lookupKey = if ("/lookup/" in uri) uri.substringAfter("/lookup/").substringBefore('/') else uri
        return lookupKey.takeIf { it.isNotEmpty() }?.let { "${ResultKind.CONTACT.prefix}:$it" }
    }

    /** "file:<document URI>". */
    fun file(file: FileItem): String = "${ResultKind.FILE.prefix}:${file.uri}"

    /** "setting:<catalogue page id>"; App Info rows (which stand for an app) have none. */
    fun setting(result: AndroidSettingResult): String? =
        if (result.app != null) null else "${ResultKind.SETTING.prefix}:${result.id}"

    /**
     * The kind of result [key] names, or null for a key this version doesn't write (a removed Saved Link's "link:"
     * key among them), which reads as nothing set.
     */
    fun kindOf(key: String): ResultKind? = ResultKind.entries.firstOrNull { key.startsWith(it.prefix + ":") && key.length > it.prefix.length + 1 }

    /** The catalogue page id in a [setting] key. */
    fun settingId(key: String): String? =
        key.takeIf { kindOf(it) == ResultKind.SETTING }?.substringAfter(':')

    /** The package an [app] key names. */
    fun appPackage(key: String): String? =
        key.takeIf { kindOf(it) == ResultKind.APP }?.substringAfter(':')?.substringBefore('/')
}

/** What the user set on one result: pinned to the top of search. */
@Immutable
data class ResultShortcut(
    val key: String,
    /** The result's name when it was pinned. */
    val title: String,
    val pinned: Boolean = false
) {
    /** Nothing set any more, so the entry can go. */
    val isEmpty: Boolean get() = !pinned
}

/**
 * Every [ResultShortcut], with the lookups search needs per keystroke. Immutable: each change returns a new set, which
 * [com.pixel.intelligentsearch.core.data.ResultShortcutStore] saves. Entries keep the order they were first set in,
 * so pinned results stay in the order they were pinned.
 */
@Immutable
class ResultShortcuts(val entries: List<ResultShortcut>) {

    private val byKey: Map<String, ResultShortcut> = entries.associateBy { it.key }

    /** Pinned results, in the order they were pinned. */
    val pinned: List<ResultShortcut> = entries.filter { it.pinned }

    operator fun get(key: String?): ResultShortcut? = key?.let(byKey::get)

    fun isPinned(key: String?): Boolean = get(key)?.pinned == true

    /** This set with [key]'s entry changed by [change]; an entry left with nothing set is dropped. */
    fun update(key: String, title: String, change: (ResultShortcut) -> ResultShortcut): ResultShortcuts {
        if (!ResultKeys.isKept(key)) return this
        val existing = byKey[key]
        val updated = change(existing ?: ResultShortcut(key, title)).copy(key = key, title = title.ifBlank { existing?.title.orEmpty() })
        val next = if (existing == null) entries + updated else entries.map { if (it.key == key) updated else it }
        return ResultShortcuts(next.filterNot { it.isEmpty })
    }

    fun withPinned(key: String, title: String, pinned: Boolean): ResultShortcuts =
        update(key, title) { it.copy(pinned = pinned) }

    /** Clears everything set on [key]. */
    fun without(key: String): ResultShortcuts = ResultShortcuts(entries.filterNot { it.key == key })

    /**
     * This set with every key as this version writes it, given the phone's [profiles] ([ResultKeys.current]): an entry
     * whose app's profile is gone goes, and of two that now name the same result the one set first stays.
     */
    fun withCurrentKeys(profiles: Map<Int, ProfileType>): ResultShortcuts {
        if (entries.none { ResultKeys.isOlderAppKey(it.key) }) return this
        val seenKeys = HashSet<String>()
        return ResultShortcuts(entries.mapNotNull { entry ->
            ResultKeys.current(entry.key, profiles)?.takeIf(seenKeys::add)?.let { entry.copy(key = it) }
        })
    }

    fun toJson(): String = JSON.encodeToString(ListSerializer(StoredShortcut.serializer()), entries.map {
        StoredShortcut(it.key, it.title, it.pinned)
    })

    override fun equals(other: Any?): Boolean = other is ResultShortcuts && entries == other.entries
    override fun hashCode(): Int = entries.hashCode()
    override fun toString(): String = "ResultShortcuts($entries)"

    /**
     * One stored entry. Older versions also stored "nicknames" and "trigger" here; those features are gone, so the
     * fields are skipped when read ([JSON] ignores unknown keys) and never written again.
     */
    @Serializable
    private data class StoredShortcut(
        val key: String,
        val title: String = "",
        val pinned: Boolean = false
    )

    companion object {
        val EMPTY = ResultShortcuts(emptyList())

        /** The most results with something set; a crafted backup can't make search scan thousands. */
        const val MAX_ENTRIES = 500

        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Reads what [toJson] wrote, or an older version's entries with nicknames and trigger words (dropped). The text
         * may come from a backup someone else made, so anything malformed reads as nothing set, and entries are checked:
         * known kinds only (never a Private Space app), one entry per result, pinned ones only.
         */
        fun fromJson(json: String): ResultShortcuts {
            if (json.isBlank()) return EMPTY
            val stored = try {
                JSON.decodeFromString(ListSerializer(StoredShortcut.serializer()), json)
            } catch (_: Exception) {
                return EMPTY
            }
            val seenKeys = HashSet<String>()
            val entries = stored.asSequence()
                .filter { ResultKeys.isKept(it.key) && seenKeys.add(it.key) }
                .map { ResultShortcut(it.key, it.title.take(200), it.pinned) }
                .filterNot { it.isEmpty }
                .take(MAX_ENTRIES)
                .toList()
            return ResultShortcuts(entries)
        }
    }
}
