$content = Get-Content "app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt" -Raw

# 1. Remove the original MaterialMorphAnimation block from Box start
$content = $content -replace '(?s)val morphAnimationEnabled by rememberBooleanPreference\(prefs, "morph_animation_enabled", true\) \{\}\s*if \(morphAnimationEnabled\) \{\s*MaterialMorphAnimation\(modifier = Modifier\.fillMaxSize\(\)\)\s*\}\s*Scaffold\(', "val morphAnimationEnabled by rememberBooleanPreference(prefs, `"morph_animation_enabled`", true) {}`r`n        Scaffold("

# 2. Add inner Column wrapping and bottom Box for each screen.
# We will do this via a regex that matches the Scaffold content padding block and its closing brace.
# Wait, parsing brackets in regex is hard.
