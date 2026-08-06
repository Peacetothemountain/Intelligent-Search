import re

with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'r') as f:
    content = f.read()

# 1. Update SettingsCard to be glassmorphic
glassmorphic_card = '''fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = glowAlpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).border(0.5.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            content()
        }
    }
}'''
content = re.sub(r'fun SettingsCard\(content: @Composable ColumnScope\.\(\) -> Unit\) \{.*?\n    \}\n\}', glassmorphic_card, content, flags=re.DOTALL)

# 2. Update Live Preview Card with Breathing Animation
preview_card_regex = r'// Live Preview Card.*?Box\(\s*modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.height\(200\.dp\)\s*\.background\(androidx\.compose\.ui\.graphics\.Color\(0xFF1E1B24\), RoundedCornerShape\(24\.dp\)\),\s*contentAlignment = Alignment\.Center\s*\) \{'
breathing_preview = '''// Live Preview Card
            val breathingTransition = rememberInfiniteTransition(label = "breathing")
            val breathingScale by breathingTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathingScale"
            )
            val breathingAlpha by breathingTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathingAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFF1E1B24), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Luminous Glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(100.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = breathingAlpha), androidx.compose.ui.graphics.Color.Transparent)
                            )
                        )
                )
                // Wrapper to apply scale to widget only without resizing container
                Box(modifier = Modifier.androidx.compose.ui.graphics.graphicsLayer {
                    scaleX = breathingScale
                    scaleY = breathingScale
                }) {'''
content = re.sub(preview_card_regex, breathing_preview, content, flags=re.DOTALL)
# Close the new Box wrapper inside the preview card
content = re.sub(r'(?<=// Action Circle).*?(?=\n\s*\}\n\s*\}\n\s*// Toggles Card)', r'\g<0>\n                }', content, flags=re.DOTALL)

# 3. Replace Android17Slider inside WidgetCustomizationScreen with androidx.compose.material3.Slider
content = re.sub(r'Android17Slider\(\s*value = localHue,\s*onValueChange = \{ localHue = it \},\s*valueRange = 0f\.\.360f,\s*modifier = Modifier\.fillMaxWidth\(\)\s*\)', 
                 r'androidx.compose.material3.Slider(value = localHue, onValueChange = { localHue = it }, valueRange = 0f..360f, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = androidx.compose.ui.graphics.Color.White, activeTrackColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent))', content)

content = re.sub(r'Android17Slider\(\s*value = localSaturation,\s*onValueChange = \{ localSaturation = it \},\s*valueRange = 0f\.\.100f,\s*modifier = Modifier\.fillMaxWidth\(\)\s*\)', 
                 r'androidx.compose.material3.Slider(value = localSaturation, onValueChange = { localSaturation = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = androidx.compose.ui.graphics.Color.White, activeTrackColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent))', content)

content = re.sub(r'Android17Slider\(\s*value = localOpacity,\s*onValueChange = \{ localOpacity = it \},\s*valueRange = 0f\.\.100f,\s*modifier = Modifier\.fillMaxWidth\(\)\s*\)', 
                 r'androidx.compose.material3.Slider(value = localOpacity, onValueChange = { localOpacity = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = androidx.compose.ui.graphics.Color.White, activeTrackColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent))', content)


with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'w') as f:
    f.write(content)

print("SettingsScreens patched!")
