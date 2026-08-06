const fs = require('fs');
const content = fs.readFileSync('commit_6943.kt', 'utf8');
const startIdx = content.indexOf('fun WidgetSettingsScreen');
if (startIdx !== -1) {
    const nextFunc = content.indexOf('fun ', startIdx + 50);
    const widgetCode = content.substring(startIdx, nextFunc !== -1 ? nextFunc : content.length);
    fs.writeFileSync('commit_6943_widget.kt', widgetCode);
    console.log("Extracted to commit_6943_widget.kt");
} else {
    console.log("Not found");
}
