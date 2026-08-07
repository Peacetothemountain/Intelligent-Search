const fs = require('fs');

const path = 'app/src/main/java/com/pixel/intelligentsearch/feature/widget/SearchWidgetProvider.kt';
let code = fs.readFileSync(path, 'utf8');

// 1. We remove DoodleFetcher.fetchCurrentDoodleBitmap()
code = code.replace(/val doodleBitmap = DoodleFetcher\.fetchCurrentDoodleBitmap\(\)/g, '// doodleBitmap fetch removed');

// 2. In Material You block:
//                 views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
//                 if (showDoodle && doodleBitmap != null) {
//                     views.setImageViewBitmap(R.id.widget_g_logo, doodleBitmap)
//                 } else {
//                     views.setImageViewResource(R.id.widget_g_logo, gIconRes)
//                 }
const oldDoodleLogic1 = `                views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
                if (showDoodle && doodleBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_g_logo, doodleBitmap)
                } else {
                    views.setImageViewResource(R.id.widget_g_logo, gIconRes)
                }`;

const newDoodleLogic1 = `                if (showDoodle) {
                    views.setViewVisibility(R.id.widget_g_logo, View.GONE)
                    views.setViewVisibility(R.id.doodle_flipper, View.VISIBLE)
                    val doodleIntent = android.content.Intent(context, com.pixel.intelligentsearch.feature.widget.doodle.DoodleWidgetService::class.java).apply {
                        putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = android.net.Uri.parse(toUri(android.content.Intent.URI_INTENT_SCHEME))
                    }
                    views.setRemoteAdapter(R.id.doodle_flipper, doodleIntent)
                } else {
                    views.setViewVisibility(R.id.doodle_flipper, View.GONE)
                    views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
                    views.setImageViewResource(R.id.widget_g_logo, gIconRes)
                }`;
code = code.replace(oldDoodleLogic1, newDoodleLogic1);


// 3. In Material Design / System Design block:
const oldDoodleLogic2 = `                views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
                if (showDoodle && doodleBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_g_logo, doodleBitmap)
                    views.setInt(R.id.widget_g_logo, "setColorFilter", android.graphics.Color.TRANSPARENT)
                } else {
                    views.setImageViewResource(R.id.widget_g_logo, gIconRes)
                    if (materialGIconTheme == "Accented G Icon") {
                        views.setInt(R.id.widget_g_logo, "setColorFilter", iconTint)
                    } else {
                        // Remove color filter if previously set
                        views.setInt(R.id.widget_g_logo, "setColorFilter", android.graphics.Color.TRANSPARENT)
                    }
                }`;

const newDoodleLogic2 = `                if (showDoodle) {
                    views.setViewVisibility(R.id.widget_g_logo, View.GONE)
                    views.setViewVisibility(R.id.doodle_flipper, View.VISIBLE)
                    val doodleIntent = android.content.Intent(context, com.pixel.intelligentsearch.feature.widget.doodle.DoodleWidgetService::class.java).apply {
                        putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = android.net.Uri.parse(toUri(android.content.Intent.URI_INTENT_SCHEME))
                    }
                    views.setRemoteAdapter(R.id.doodle_flipper, doodleIntent)
                } else {
                    views.setViewVisibility(R.id.doodle_flipper, View.GONE)
                    views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
                    views.setImageViewResource(R.id.widget_g_logo, gIconRes)
                    if (materialGIconTheme == "Accented G Icon") {
                        views.setInt(R.id.widget_g_logo, "setColorFilter", iconTint)
                    } else {
                        views.setInt(R.id.widget_g_logo, "setColorFilter", android.graphics.Color.TRANSPARENT)
                    }
                }`;
code = code.replace(oldDoodleLogic2, newDoodleLogic2);

// 4. Also check for System Design G Icon fix: 
// The user said: "Display G Icon On Material Design you are applying Star Magnifying Glass where the G should be on the far left side instead of the correct G logo based on the user's selection."
// Wait, I already fixed that in SettingsScreens preview, but maybe in SearchWidgetProvider too?
// In SearchWidgetProvider.kt:
//                 val gIconRes = if (themeStyle != "Material You (Minimal)" && materialGIconTheme != "Accented G Icon") {
//                     R.drawable.ic_search_ai_colored // WAIT!!! HERE IT IS! It was ic_search_ai_colored!
//                 } else if (materialGIconTheme == "System G Icon") {
//                     R.drawable.ic_g_logo_colored
//                 } else {
//                     R.drawable.ic_g_logo
//                 }
code = code.replace(/val gIconRes = if \(themeStyle != "Material You \(Minimal\)" && materialGIconTheme != "Accented G Icon"\) \{\n\s*R\.drawable\.ic_search_ai_colored/, 'val gIconRes = if (themeStyle != "Material You (Minimal)") {\n                    if (materialGIconTheme == "Accented G Icon") R.drawable.ic_g_logo else R.drawable.ic_g_logo_colored');

fs.writeFileSync(path, code);
console.log('SearchWidgetProvider.kt patched');
