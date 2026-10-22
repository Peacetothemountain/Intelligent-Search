package com.pixel.intelligentsearch.core.data

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.net.toUri

/**
 * Which app opens a file result of a type, as chosen under Open Files With in File Search settings: the system's
 * "Open with" sheet every time, the phone's default app for the type, or one particular app.
 */
sealed interface OpenWithChoice {
    /** Stored form of the choice. */
    val stored: String

    data object AskEveryTime : OpenWithChoice {
        override val stored = "ask"
    }

    data object DefaultApp : OpenWithChoice {
        override val stored = "default"
    }

    data class App(val packageName: String) : OpenWithChoice {
        override val stored = "app:$packageName"
    }

    companion object {
        const val DEFAULT_STORED = "default"

        // A Java package name: dot-separated identifiers, at least two of them.
        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        /** The choice stored as [stored]; anything unreadable (an old or hand-edited backup) is the default app. */
        fun parse(stored: String): OpenWithChoice = when {
            stored == AskEveryTime.stored -> AskEveryTime
            stored.startsWith("app:") && PACKAGE_NAME.matches(stored.removePrefix("app:")) -> App(stored.removePrefix("app:"))
            else -> DefaultApp
        }
    }
}

/** An installed app that can open a file type: its package, name and icon, for the Open Files With menus. */
class FileOpenerApp(val packageName: String, val label: String, val icon: Drawable?)

object FileOpeners {

    /**
     * The apps that open files of [group], by name. Asks the package manager (a Binder call per type), so call off
     * the main thread. Leaves out this app, which only searches files.
     */
    fun appsFor(pm: PackageManager, group: FileTypeGroup, ownPackage: String): List<FileOpenerApp> {
        val byPackage = LinkedHashMap<String, FileOpenerApp>()
        for (mime in group.queryMimes) {
            val probe = Intent(Intent.ACTION_VIEW).setDataAndType("content://$ownPackage.probe/file".toUri(), mime)
            val handlers = runCatching { pm.queryIntentActivities(probe, 0) }.getOrDefault(emptyList())
            for (info in handlers) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == ownPackage || pkg in byPackage || info.activityInfo?.exported == false) continue
                val appInfo = info.activityInfo.applicationInfo
                val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(pkg).ifBlank { pkg }
                byPackage[pkg] = FileOpenerApp(pkg, label, runCatching { appInfo.loadIcon(pm) }.getOrNull())
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }
}
