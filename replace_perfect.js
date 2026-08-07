const fs = require('fs');
const content = fs.readFileSync('C:/Users/caref/.gemini/antigravity/scratch/IntelligentSearch/app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'utf8');
const widgetStart = content.indexOf('fun WidgetSettingsScreen');
const widgetEnd = content.indexOf('fun updateWidgets', widgetStart);

const newWidget = fs.readFileSync('C:/Users/caref/.gemini/antigravity/scratch/IntelligentSearch/perfect_widget.kt', 'utf8');

if (widgetStart !== -1 && widgetEnd !== -1) {
    const newContent = content.substring(0, widgetStart) + newWidget + '\n\n' + content.substring(widgetEnd);
    fs.writeFileSync('C:/Users/caref/.gemini/antigravity/scratch/IntelligentSearch/app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', newContent);
    console.log("Replaced successfully!");
} else {
    console.log("Failed to find boundaries");
}
