import re

with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'r') as f:
    content = f.read()

content = re.sub(r'Android17Slider\(\s*value = (.*?),.*?onValueChange = (.*?),.*?valueRange = (.*?),.*?modifier = Modifier\.fillMaxWidth\(\)\s*\)',
                 r'androidx.compose.material3.Slider(value = \1, onValueChange = \2, valueRange = \3, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = androidx.compose.ui.graphics.Color.White, activeTrackColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent))',
                 content, flags=re.DOTALL)

with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'w') as f:
    f.write(content)
