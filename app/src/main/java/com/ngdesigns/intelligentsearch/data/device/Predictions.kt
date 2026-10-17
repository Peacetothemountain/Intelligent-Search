package com.ngdesigns.intelligentsearch.data.device

import android.content.Context
import android.net.Uri
import com.ngdesigns.intelligentsearch.data.history.Launch
import com.ngdesigns.intelligentsearch.engine.Usage

/** Apps Pixel Launcher predicts the user wants next, when it shares them. */
interface LauncherPredictions {
    fun predictedPackages(): List<String>
}

class PixelLauncherPredictions(private val context: Context) : LauncherPredictions {
    override fun predictedPackages(): List<String> = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            val pkg = c.getColumnIndex("package")
            buildList { while (pkg >= 0 && c.moveToNext()) c.getString(pkg)?.let(::add) }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private companion object {
        val URI: Uri = Uri.parse("content://com.google.android.apps.nexuslauncher.prediction/predictions")
    }
}

/**
 * Suggested apps for an empty search: Pixel Launcher's predictions when it shares them, else the apps used most in
 * the last day or so (usage access), else the apps most often opened from search, else the first apps by name.
 */
object Suggestions {
    fun apps(
        apps: List<AppEntry>,
        predicted: List<String>,
        usage: Map<String, Usage>,
        launches: Map<String, Launch>,
        now: Long,
        count: Int = 8,
    ): List<AppEntry> {
        val personal = apps.filter { it.target.profile == Profile.Personal }
        val byPackage = personal.associateBy { it.target.packageName }
        val fromLauncher = predicted.mapNotNull { byPackage[it] }.distinct()
        if (fromLauncher.size >= count / 2) return fromLauncher.take(count)
        val recent = usage.filterValues { now - it.lastUsedMillis < 36L * 3600_000 }
            .entries.sortedByDescending { it.value.count * 1_000_000_000_000L + it.value.lastUsedMillis }
            .mapNotNull { byPackage[it.key] }
        val launched = launches.entries.sortedByDescending { it.value.count }.mapNotNull { byPackage[it.key] }
        return (fromLauncher + recent + launched + personal).distinctBy { it.target.packageName }.take(count)
    }
}
