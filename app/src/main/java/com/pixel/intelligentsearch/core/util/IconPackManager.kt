package com.pixel.intelligentsearch.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object IconPackManager {

    private val iconCache = LruCache<String, Drawable>(512)
    private val appFilterMapCache = ConcurrentHashMap<String, Map<String, String>>()

    private val ICON_PACK_ACTIONS = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.solo.launcher.free.THEME",
        "com.dlto.atom.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME"
    )

    private val ICON_PACK_CATEGORIES = listOf(
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme"
    )

    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacksMap = mutableMapOf<String, IconPackInfo>()

        // 1. Query by actions
        for (action in ICON_PACK_ACTIONS) {
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

        // 2. Query by categories
        for (cat in ICON_PACK_CATEGORIES) {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(cat) }
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
            val iconPackContext = try {
                context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
            } catch (e: Exception) {
                null
            }

            var appFilter = appFilterMapCache[iconPackPackage]
            if (appFilter == null) {
                appFilter = parseAppFilter(context, iconPackPackage)
                appFilterMapCache[iconPackPackage] = appFilter
            }

            var drawableName: String? = null

            // 1. Check exact component info match (ComponentInfo{pkg/activity})
            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
            val mainActivity = launchIntent?.component?.className
            if (mainActivity != null) {
                val compKey = "componentinfo{$targetPackage/$mainActivity}".lowercase()
                drawableName = appFilter[compKey]
            }

            // 2. Check target package match
            if (drawableName.isNullOrEmpty()) {
                drawableName = appFilter[targetPackage.lowercase()]
            }

            // 3. Check sanitized package match (com_google_android_youtube)
            if (drawableName.isNullOrEmpty()) {
                val sanitizedPkg = targetPackage.replace(".", "_").lowercase()
                drawableName = appFilter[sanitizedPkg] ?: sanitizedPkg
            }

            var resId = 0
            if (!drawableName.isNullOrEmpty()) {
                resId = iconPackRes.getIdentifier(drawableName, "drawable", iconPackPackage)
            }

            // 4. Fallback attempt by component activity name
            if (resId == 0 && mainActivity != null) {
                val actSimpleName = mainActivity.substringAfterLast(".").lowercase()
                resId = iconPackRes.getIdentifier(actSimpleName, "drawable", iconPackPackage)
            }

            // 5. Fallback attempt by simple package suffix
            if (resId == 0) {
                val simplePkg = targetPackage.substringAfterLast(".").lowercase()
                resId = iconPackRes.getIdentifier(simplePkg, "drawable", iconPackPackage)
                if (resId == 0) {
                    resId = iconPackRes.getIdentifier("ic_$simplePkg", "drawable", iconPackPackage)
                }
            }

            if (resId != 0) {
                val theme = iconPackContext?.theme
                val drawable = if (theme != null) {
                    iconPackRes.getDrawable(resId, theme)
                } else {
                    iconPackRes.getDrawable(resId, null)
                }
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

            // Attempt 1: Parse res/xml/appfilter.xml
            val resId = iconPackRes.getIdentifier("appfilter", "xml", iconPackPackage)
            if (resId != 0) {
                val xpp = iconPackRes.getXml(resId)
                parseXmlParser(xpp, map)
            }

            // Attempt 2: Parse assets/appfilter.xml if XML resource produced empty or failed
            if (map.isEmpty()) {
                try {
                    val iconPackContext = context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
                    val assetStream: InputStream = iconPackContext.assets.open("appfilter.xml")
                    val factory = XmlPullParserFactory.newInstance()
                    factory.isNamespaceAware = true
                    val xpp = factory.newPullParser()
                    xpp.setInput(assetStream, "UTF-8")
                    parseXmlParser(xpp, map)
                    assetStream.close()
                } catch (e: Exception) {
                    // Asset appfilter.xml not present
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun parseXmlParser(xpp: XmlPullParser, map: MutableMap<String, String>) {
        var eventType = xpp.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                var comp: String? = null
                var drawable: String? = null
                var pkg: String? = null
                for (i in 0 until xpp.attributeCount) {
                    when (xpp.getAttributeName(i).lowercase()) {
                        "component" -> comp = xpp.getAttributeValue(i)
                        "drawable" -> drawable = xpp.getAttributeValue(i)
                        "package" -> pkg = xpp.getAttributeValue(i)
                    }
                }

                if (!drawable.isNullOrEmpty()) {
                    if (!comp.isNullOrEmpty()) {
                        val lowerComp = comp.lowercase()
                        map[lowerComp] = drawable
                        if (lowerComp.contains("{") && lowerComp.contains("/")) {
                            val pkgFromComp = lowerComp.substringAfter("{").substringBefore("/")
                            map[pkgFromComp] = drawable
                        }
                    }
                    if (!pkg.isNullOrEmpty()) {
                        map[pkg.lowercase()] = drawable
                    }
                }
            }
            eventType = xpp.next()
        }
    }

    fun clearCache() {
        iconCache.evictAll()
        appFilterMapCache.clear()
        com.pixel.intelligentsearch.feature.search.clearThemedIconCache()
    }
}

