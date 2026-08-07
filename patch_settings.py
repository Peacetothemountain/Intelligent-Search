import re
import sys

with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'r') as f:
    content = f.read()

# Define the start and end of WidgetCustomizationScreen
start_idx = content.find('fun WidgetCustomizationScreen')
end_idx = content.find('fun updateWidgets', start_idx)

if start_idx == -1 or end_idx == -1:
    print('Failed to find boundaries')
    sys.exit(1)

wcs_content = content[start_idx:end_idx]
rest_of_file = content[end_idx:]
header = content[:start_idx]

# 1. Replace SettingsCard with WidgetCustomizationCard
wcs_content = wcs_content.replace('SettingsCard {', 'WidgetCustomizationCard {')

# 2. Replace Android17Slider with standard Slider
# Regex to match Android17Slider(...) inside wcs_content
# Using a robust regex to replace it
wcs_content = re.sub(
    r'Android17Slider\(\s*value\s*=\s*(.*?),\s*onValueChange\s*=\s*\{(.*?)\},\s*valueRange\s*=\s*(.*?),\s*modifier\s*=\s*(.*?)\s*\)',
    r'androidx.compose.material3.Slider(\n                                        value = \1,\n                                        onValueChange = {\2},\n                                        valueRange = \3,\n                                        modifier = \4\n                                    )',
    wcs_content
)

# 3. Add Breathing Preview
# Look for "// Widget Container" and "Row("
old_preview_code = '''// Widget Container
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Pill'''

new_preview_code = '''val breathingTransition = rememberInfiniteTransition(label = "breathing")
                val breathingScale by breathingTransition.animateFloat(
                    initialValue = 0.95f, targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                    label = "breathingScale"
                )
                val breathingAlpha by breathingTransition.animateFloat(
                    initialValue = 0.1f, targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                    label = "breathingAlpha"
                )
                
                // Luminous Glow
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(100.dp).background(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = breathingAlpha), androidx.compose.ui.graphics.Color.Transparent)
                    )
                ))
                
                // Wrapper to apply scale to widget only without resizing container
                Box(modifier = Modifier.graphicsLayer { scaleX = breathingScale; scaleY = breathingScale }) {
                    // Widget Container
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Pill'''

if old_preview_code in wcs_content:
    wcs_content = wcs_content.replace(old_preview_code, new_preview_code)
else:
    print('Failed to find Live Preview Box')

# Find where the Live Preview Box ends to close the Wrapper Box
old_toggles_card = '''// Toggles Card'''
new_toggles_card = '''} // Close Wrapper Box
            
            // Toggles Card'''
wcs_content = wcs_content.replace(old_toggles_card, new_toggles_card)


# 4. Define WidgetCustomizationCard
wcs_card_def = '''
@Composable
private fun WidgetCustomizationCard(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1E1B24).copy(alpha = 0.6f)
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content
        )
    }
}

'''

final_content = header + wcs_card_def + wcs_content + rest_of_file

with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'w') as f:
    f.write(final_content)

print('Success')
