const fs = require('fs');
const settingsPath = 'app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt';
const wcsPath = 'wcs_prepared.kt';

let settingsContent = fs.readFileSync(settingsPath, 'utf8');
let wcsContent = fs.readFileSync(wcsPath, 'utf8');

wcsContent = wcsContent.replace(/fun updateWidgets\(context: Context\) \{[\s\S]*/, '');

const funcStr = 'fun WidgetSettingsScreen(prefs: SharedPreferences, onBack: () -> Unit)';
let funcIdx = settingsContent.indexOf(funcStr);
if (funcIdx === -1) {
    console.log("Could not find WidgetSettingsScreen");
    process.exit(1);
}

let startIdx = settingsContent.lastIndexOf('@OptIn', funcIdx);
if (startIdx === -1) {
    startIdx = settingsContent.lastIndexOf('@Composable', funcIdx);
}

const endIdx = settingsContent.indexOf('fun updateWidgets(context: Context)', startIdx);
if (endIdx === -1) {
    console.log("Could not find end");
    process.exit(1);
}

// Ensure endIdx goes back to the beginning of the line or the previous empty space
let replacementEnd = endIdx;
while (settingsContent[replacementEnd - 1] === '\n' || settingsContent[replacementEnd - 1] === '\r') {
    replacementEnd--;
}

const newContent = settingsContent.substring(0, startIdx) + wcsContent + '\n\n' + settingsContent.substring(replacementEnd);
fs.writeFileSync(settingsPath, newContent);
console.log("Replacement successful.");
