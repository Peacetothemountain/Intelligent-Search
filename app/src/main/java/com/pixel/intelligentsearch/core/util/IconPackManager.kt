package com.pixel.intelligentsearch.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object IconPackManager {

    private val iconCache = LruCache<String, Drawable>(256)
    private val appFilterMapCache = ConcurrentHashMap<String, Map<String, String>>()

    private val ICON_PACK_INTENTS = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.solo.launcher.free.THEME",
        "com.dlto.atom.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME"
    )

    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacksMap = mutableMapOf<String, IconPackInfo>()

        for (action in ICON_PACK_INTENTS) {
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (ri in resolveInfos) {
                val pkgName = ri.activityInfo.packageName
                if (!iconPacksMap.containsKey(pkgName) && pkgName != context.packageName) {
                    val label = ri.loadLabel(pm).toString()
                    val icon = ri.loadIcon(pm)
                    iconPacksMap[pkgName] = IconPackInfo(pkgName, label, icon)
                }
            }
        }
        return iconPacksMap.values.sortedBy { it.label.lowercase() }
    }

    fun getIconForPackage(context: Context, iconPackPackage: String?, targetPackage: String): Drawable? {
        if (iconPackPackage.isNullOrEmpty() || iconPackPackage == "system_default") {
            return null
        }

        val cacheKey = "$iconPackPackage:$targetPackage"
        val cached = iconCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        try {
            val pm = context.packageManager
            val iconPackRes = pm.getResourcesForApplication(iconPackPackage)
            
            var appFilter = appFilterMapCache[iconPackPackage]
            if (appFilter == null) {
                appFilter = parseAppFilter(context, iconPackPackage)
                appFilterMapCache[iconPackPackage] = appFilter
            }

            var drawableName = appFilter[targetPackage]
            if (drawableName.isNullOrEmpty()) {
                val sanitizedPkg = targetPackage.replace(".", "_")
                drawableName = sanitizedPkg
            }

            var resId = iconPackRes.getIdentifier(drawableName, "drawable", iconPackPackage)
            if (resId == 0) {
                val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                val mainActivity = launchIntent?.component?.className
                if (mainActivity != null) {
                    val compName = "ComponentInfo{$targetPackage/$mainActivity}".lowercase()
                    val mappedName = appFilter[compName]
                    if (!mappedName.isNullOrEmpty()) {
                        resId = iconPackRes.getIdentifier(mappedName, "drawable", iconPackPackage)
                    }
                }
            }

            if (resId != 0) {
                val drawable = iconPackRes.getDrawable(resId, null)
                if (drawable != null) {
                    iconCache.put(cacheKey, drawable)
                    return drawable
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun parseAppFilter(context: Context, iconPackPackage: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val pm = context.packageManager
            val iconPackRes = pm.getResourcesForApplication(iconPackPackage)
            val resId = iconPackRes.getIdentifier("appfilter", "xml", iconPackPackage)
            if (resId != 0) {
                val xpp = iconPackRes.getXml(resId)
                var eventType = xpp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                        val comp = xpp.getAttributeValue(null, "component")
                        val drawable = xpp.getAttributeValue(null, "drawable")
                        if (!comp.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                            if (comp.startsWith("ComponentInfo{")) {
                                val pkg = comp.substringAfter("ComponentInfo{").substringBefore("/").lowercase()
                                map[pkg] = drawable
                                map[comp.lowercase()] = drawable
                            }
                        }
                    }
                    eventType = xpp.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    fun clearCache() {
        iconCache.evictAll()
        appFilterMapCache.clear()
    }
}
