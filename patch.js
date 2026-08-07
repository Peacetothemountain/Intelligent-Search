const fs = require('fs');

const path = 'app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt';
let code = fs.readFileSync(path, 'utf8');

// 1. Rename "Show Doodle" to "Google Mini Doodle"
code = code.replace(/"Show Doodle"/g, '"Google Mini Doodle"');

// 2. Fix bouncyClickable in SettingsRow, SettingsRowToggle, SettingsDropdownRow
// They usually look like:
// .fillMaxWidth()
// .bouncyClickable(
code = code.replace(/\.fillMaxWidth\(\)\s*\n\s*\.bouncyClickable/g, '.fillMaxWidth()\n                .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))\n                .bouncyClickable');

// 3. Fix Apply button onBack
//             SettingsButton(text = "Apply", onClick = {
//                 val editor = prefs.edit()
// ...
//                 updateWidgets(context)
//                 onBack()
//             }) {
//                 Text("Apply", color = MaterialTheme.colorScheme.primary)
//             }
code = code.replace(/updateWidgets\(context\)\s*\n\s*onBack\(\)/g, 'updateWidgets(context)\n                        android.widget.Toast.makeText(context, "Settings Applied", android.widget.Toast.LENGTH_SHORT).show()');

// 4. Fix G Icon Preview mapping
//                                 Icon(
//                                     imageVector = ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored),
//                                     contentDescription = "G Icon",
//                                     tint = androidx.compose.ui.graphics.Color.Unspecified,
//                                     modifier = Modifier.size(24.dp)
//                                 )
const gIconPreviewCode = `val previewGIcon = when {
                                    localThemeStyle != "Material You (Minimal)" -> {
                                        if (localMaterialGIconTheme == "Accented G Icon") com.pixel.intelligentsearch.R.drawable.ic_g_logo else com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored
                                    }
                                    else -> {
                                        when (localMaterialGIconTheme) {
                                            "System G Icon" -> com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored
                                            "Material G Icon" -> com.pixel.intelligentsearch.R.drawable.ic_g_logo
                                            "Accented G Icon" -> com.pixel.intelligentsearch.R.drawable.ic_g_logo
                                            else -> com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored
                                        }
                                    }
                                }
                                val gIconTint = if (localThemeStyle == "Material You (Minimal)" && localMaterialGIconTheme == "Accented G Icon") {
                                    if (localSubtheme == "Custom") {
                                        try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(localCustomColor)) } catch(e: Exception) { androidx.compose.ui.graphics.Color.White }
                                    } else {
                                        if (isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                                    }
                                } else {
                                    androidx.compose.ui.graphics.Color.Unspecified
                                }
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = previewGIcon),
                                    contentDescription = "G Icon",
                                    tint = gIconTint,
                                    modifier = Modifier.size(24.dp)
                                )`;
code = code.replace(/Icon\(\s*imageVector = ImageVector\.vectorResource\(id = com\.pixel\.intelligentsearch\.R\.drawable\.ic_search_ai_colored\),\s*contentDescription = "G Icon",\s*tint = androidx\.compose\.ui\.graphics\.Color\.Unspecified,\s*modifier = Modifier\.size\(24\.dp\)\s*\)/, gIconPreviewCode);

fs.writeFileSync(path, code);
console.log('SettingsScreens.kt patched');
