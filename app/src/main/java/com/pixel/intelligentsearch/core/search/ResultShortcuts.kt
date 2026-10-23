package com.pixel.intelligentsearch.core.search

import androidx.compose.runtime.Immutable
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Locale

/** The kinds of result the user can pin, give nicknames or a trigger word, by the prefix of their [ResultKeys] key. */
enum class ResultKind(val prefix: String) {
    APP("app"),
    CONTACT("contact"),
    FILE("file"),
    SETTING("setting")
}

/**
 * Stable names for results, so a pin, nickname or trigger word still finds its result after the index is rebuilt,
 * the app restarts or a backup is restored: an app by its package, launcher activity and profile, a contact by its
 * lookup key (which survives the contact's row id changing), a file by its document URI and an Android Settings page
 * by its catalogue id.
 */
object ResultKeys {

    /** "app:<package>/<activity>@<user>", the user being the profile's id (0 for the personal profile). */
    fun app(app: AppItem): String {
        val user = (app.userHandle ?: android.os.Process.myUserHandle())?.hashCode() ?: 0
        return "${ResultKind.APP.prefix}:${app.packageName}/${app.activityName.orEmpty()}@$user"
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

/** What the user set on one result: pinned to the top, nicknames it is also found by, a trigger word that opens it. */
@Immutable
data class ResultShortcut(
    val key: String,
    /** The result's name when it was set, for the Nicknames & Triggers page. */
    val title: String,
    val pinned: Boolean = false,
    val nicknames: List<String> = emptyList(),
    /** Lowercase letters and digits; blank for none. */
    val trigger: String = ""
) {
    val kind: ResultKind? get() = ResultKeys.kindOf(key)

    /** Nothing set any more, so the entry can go. */
    val isEmpty: Boolean get() = !pinned && nicknames.isEmpty() && trigger.isBlank()
}

/**
 * Every [ResultShortcut], with the lookups search needs per keystroke. Immutable: each change returns a new set, which
 * [com.pixel.intelligentsearch.core.data.ResultShortcutStore] saves. Entries keep the order they were first set in,
 * so pinned results stay in the order they were pinned.
 */
@Immutable
class ResultShortcuts(val entries: List<ResultShortcut>) {

    private val byKey: Map<String, ResultShortcut> = entries.associateBy { it.key }
    private val byTrigger: Map<String, ResultShortcut> =
        entries.filter { it.trigger.isNotBlank() }.associateBy { it.trigger }

    /** Each nickname, normalized as queries are, with the key it belongs to. */
    private val nicknameIndex: List<Pair<String, String>> = entries.flatMap { entry ->
        entry.nicknames.map { QueryNormalizer.normalize(it) to entry.key }.filter { it.first.isNotEmpty() }
    }

    /** Pinned results, in the order they were pinned. */
    val pinned: List<ResultShortcut> = entries.filter { it.pinned }

    /** Entries with a nickname or a trigger word, for the Nicknames & Triggers page. */
    val named: List<ResultShortcut> = entries.filter { it.nicknames.isNotEmpty() || it.trigger.isNotBlank() }

    val hasNicknames: Boolean get() = nicknameIndex.isNotEmpty()

    operator fun get(key: String?): ResultShortcut? = key?.let(byKey::get)

    fun isPinned(key: String?): Boolean = get(key)?.pinned == true

    fun nicknamesOf(key: String?): List<String> = get(key)?.nicknames.orEmpty()

    /** The result [word] is the trigger of (any case), if any. */
    fun forTrigger(word: String): ResultShortcut? = byTrigger[word.lowercase(Locale.ROOT)]

    /**
     * Keys of results with a nickname that is [query] or starts with it (both normalized), so "dad" or "da" finds the
     * contact nicknamed "dad". Nicknames are few, so a scan beats keeping a tree of them.
     */
    fun keysWithNicknameFor(query: String): Set<String> {
        val q = QueryNormalizer.normalize(query)
        if (q.isEmpty() || nicknameIndex.isEmpty()) return emptySet()
        return nicknameIndex.asSequence().filter { (nickname, _) -> nickname.startsWith(q) }.map { it.second }.toSet()
    }

    /** This set with [key]'s entry changed by [change]; an entry left with nothing set is dropped. */
    fun update(key: String, title: String, change: (ResultShortcut) -> ResultShortcut): ResultShortcuts {
        if (ResultKeys.kindOf(key) == null) return this
        val existing = byKey[key]
        val updated = change(existing ?: ResultShortcut(key, title)).copy(key = key, title = title.ifBlank { existing?.title.orEmpty() })
        val next = if (existing == null) entries + updated else entries.map { if (it.key == key) updated else it }
        return ResultShortcuts(next.filterNot { it.isEmpty })
    }

    fun withPinned(key: String, title: String, pinned: Boolean): ResultShortcuts =
        update(key, title) { it.copy(pinned = pinned) }

    /** Adds [nickname] (trimmed) to [key]'s, unless it already has it in any case or has [MAX_NICKNAMES]. */
    fun withNickname(key: String, title: String, nickname: String): ResultShortcuts {
        val clean = cleanNickname(nickname) ?: return this
        return update(key, title) { entry ->
            if (entry.nicknames.any { it.equals(clean, ignoreCase = true) } || entry.nicknames.size >= MAX_NICKNAMES) entry
            else entry.copy(nicknames = entry.nicknames + clean)
        }
    }

    /** Sets [key]'s trigger word; blank removes it. A word [QuickKeys.check] rejects leaves the set unchanged. */
    fun withTrigger(key: String, title: String, trigger: String, bangSymbol: String = "!"): ResultShortcuts {
        val word = QuickKeys.normalize(trigger)
        if (word.isNotEmpty() && QuickKeys.check(word, key, this, bangSymbol) != QuickKeys.Check.Ok) return this
        return update(key, title) { it.copy(trigger = word) }
    }

    /** Clears everything set on [key]. */
    fun without(key: String): ResultShortcuts = ResultShortcuts(entries.filterNot { it.key == key })

    fun toJson(): String = JSON.encodeToString(ListSerializer(StoredShortcut.serializer()), entries.map {
        StoredShortcut(it.key, it.title, it.pinned, it.nicknames, it.trigger)
    })

    override fun equals(other: Any?): Boolean = other is ResultShortcuts && entries == other.entries
    override fun hashCode(): Int = entries.hashCode()
    override fun toString(): String = "ResultShortcuts($entries)"

    @Serializable
    private data class StoredShortcut(
        val key: String,
        val title: String = "",
        val pinned: Boolean = false,
        val nicknames: List<String> = emptyList(),
        val trigger: String = ""
    )

    companion object {
        val EMPTY = ResultShortcuts(emptyList())

        /** The most nicknames one result keeps, and the longest a nickname may be. */
        const val MAX_NICKNAMES = 10
        const val MAX_NICKNAME_LENGTH = 40
        /** The most results with something set; a crafted backup can't make search scan thousands. */
        const val MAX_ENTRIES = 500

        private val JSON = Json { ignoreUnknownKeys = true }

        /** [nickname] as stored: trimmed, inner spaces collapsed; null if blank or too long. */
        /**
         * The nickname [typed] adds to a result that has [existing] ones, cleaned, or null when it can't be added:
         * empty, one it already has (in any case), or the result has [MAX_NICKNAMES]. Both editors use it.
         */
        fun canAddNickname(typed: String, existing: List<String>): String? = cleanNickname(typed)?.takeIf { clean ->
            existing.none { it.equals(clean, ignoreCase = true) } && existing.size < MAX_NICKNAMES
        }

        /** Why [typed] can't be added to [title]'s [existing] nicknames, for under the field; null when it can or is empty. */
        fun nicknameError(typed: String, existing: List<String>, title: String): String? {
            val clean = cleanNickname(typed) ?: return null
            return when {
                existing.any { it.equals(clean, ignoreCase = true) } -> "$title Already Has This Nickname."
                existing.size >= MAX_NICKNAMES -> "Up to $MAX_NICKNAMES Nicknames."
                else -> null
            }
        }

        fun cleanNickname(nickname: String): String? =
            nickname.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() && it.length <= MAX_NICKNAME_LENGTH }

        /**
         * Reads what [toJson] wrote. The text may come from a backup someone else made, so anything malformed reads as
         * nothing set, and entries are checked as the editors would: known kinds only, one entry per result, clean
         * nicknames, and trigger words that are valid and unique (the first one keeps a word claimed twice).
         */
        fun fromJson(json: String): ResultShortcuts {
            if (json.isBlank()) return EMPTY
            val stored = try {
                JSON.decodeFromString(ListSerializer(StoredShortcut.serializer()), json)
            } catch (_: Exception) {
                return EMPTY
            }
            val seenKeys = HashSet<String>()
            val seenTriggers = HashSet<String>()
            val entries = stored.asSequence()
                .filter { ResultKeys.kindOf(it.key) != null && seenKeys.add(it.key) }
                .map { item ->
                    val nicknames = item.nicknames.mapNotNull(::cleanNickname)
                        .distinctBy { it.lowercase(Locale.ROOT) }.take(MAX_NICKNAMES)
                    val trigger = QuickKeys.normalize(item.trigger)
                        .takeIf { QuickKeys.isWellFormed(it) && it !in QuickKeys.RESERVED && seenTriggers.add(it) }.orEmpty()
                    ResultShortcut(item.key, item.title.take(200), item.pinned, nicknames, trigger)
                }
                .filterNot { it.isEmpty }
                .take(MAX_ENTRIES)
                .toList()
            return ResultShortcuts(entries)
        }
    }
}

/**
 * Trigger words ("quick keys", as Spotlight calls them): a short word set on a result opens it the moment it is typed
 * followed by a space, or typed alone and sent with Enter. A trigger is letters and digits only, so it can never be
 * mistaken for a web shortcut, which always starts with its symbol ("!yt").
 */
object QuickKeys {

    const val MAX_LENGTH = 12

    /** What the trigger word field says while there is nothing wrong, in both editors. */
    val HINT: String = "Letters and Digits, Up to $MAX_LENGTH."

    /** How a trigger word is used, the editors' sentence above the field. */
    fun howTo(title: String): String = "Type the word and a space in search to open $title."

    /** Whether a trigger word may be saved, or why not. */
    sealed interface Check {
        data object Ok : Check
        data object Empty : Check
        data object TooLong : Check
        /** Starts with the web shortcuts' symbol, which belongs to them. */
        data object ReservedForWebShortcuts : Check
        data object LettersAndDigitsOnly : Check
        /** Another result already has it. */
        data class Taken(val title: String) : Check
        /** Search itself reads it at the start of a query ("pdf", "weather", "remind", "ask"). */
        data object ReservedForSearch : Check
    }

    /**
     * The first words search already acts on: the file types ("pdf invoice"), instant answers ("define", "weather",
     * "translate"), actions ("remind", "timer", "alarm", "call", "text", "set", "navigate") and AI Answers ("ask").
     * A trigger word opens its result as soon as it is typed with a space, so it would take these phrasings over.
     */
    val RESERVED: Set<String> = com.pixel.intelligentsearch.core.data.FileTypeQuery.typeWords + setOf(
        "ask", "define", "definition", "meaning", "translate", "weather", "forecast",
        "remind", "reminder", "timer", "alarm", "wake", "set", "start",
        "call", "dial", "phone", "text", "message", "send",
        "navigate", "directions", "route", "take", "add", "new", "meeting", "schedule"
    )

    fun normalize(trigger: String): String = trigger.trim().lowercase(Locale.ROOT)

    /** Letters and digits only, one to [MAX_LENGTH] of them. */
    fun isWellFormed(word: String): Boolean =
        word.isNotEmpty() && word.length <= MAX_LENGTH && word.all { it.isLetterOrDigit() }

    /** Whether [trigger] may be set on [key]: well formed, not a web shortcut's symbol, and not another result's. */
    fun check(trigger: String, key: String, shortcuts: ResultShortcuts, bangSymbol: String = "!"): Check {
        val word = normalize(trigger)
        return when {
            word.isEmpty() -> Check.Empty
            word.startsWith("!") || (bangSymbol.isNotBlank() && word.startsWith(bangSymbol)) -> Check.ReservedForWebShortcuts
            !word.all { it.isLetterOrDigit() } -> Check.LettersAndDigitsOnly
            word.length > MAX_LENGTH -> Check.TooLong
            word in RESERVED -> Check.ReservedForSearch
            else -> shortcuts.forTrigger(word)?.takeIf { it.key != key }?.let { Check.Taken(it.title) } ?: Check.Ok
        }
    }

    /** What the editor says under the field for [check]; null when the word can be saved. */
    fun message(check: Check, bangSymbol: String = "!"): String? = when (check) {
        Check.Ok -> null
        Check.Empty -> "Type a Trigger Word."
        Check.TooLong -> "Use $MAX_LENGTH Letters or Fewer."
        Check.ReservedForWebShortcuts -> "${bangSymbol.ifBlank { "!" }} Starts Web Shortcuts. Use Letters and Digits."
        Check.LettersAndDigitsOnly -> "Use Letters and Digits Only."
        is Check.Taken -> "Already Opens ${check.title}."
        Check.ReservedForSearch -> "Already Used by Search."
    }

    /** The result to open as [query] is typed: a trigger word followed by one space ("sp "), nothing before it. */
    fun matchTyped(query: String, shortcuts: ResultShortcuts): ResultShortcut? {
        if (!query.endsWith(' ')) return null
        val word = query.dropLast(1)
        if (!isWellFormed(word)) return null
        return shortcuts.forTrigger(word)
    }

    /** The result to open when [query] is sent with Enter: exactly a trigger word. */
    fun matchSubmitted(query: String, shortcuts: ResultShortcuts): ResultShortcut? {
        val word = query.trim()
        if (!isWellFormed(word)) return null
        return shortcuts.forTrigger(word)
    }
}
