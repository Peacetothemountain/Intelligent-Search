const fs = require('fs');
const path = require('path');

const baseDir = path.resolve('app/src/main/java');
const srcDir = path.resolve(baseDir, 'com/pixel/intelligentsearch');

function walk(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    list.forEach(function(file) {
        file = path.resolve(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) { 
            results = results.concat(walk(file));
        } else if (file.endsWith('.kt') || file.endsWith('.xml')) { 
            results.push(file);
        }
    });
    return results;
}

const files = walk(srcDir);
files.push(path.resolve('app/src/main/AndroidManifest.xml'));
files.push(path.resolve('app/src/main/res/xml/shortcuts.xml'));

const replacements = {
    'com.pixel.intelligentsearch.ui.SearchOverlayScreen': 'com.pixel.intelligentsearch.feature.search.SearchOverlayScreen',
    'com.pixel.intelligentsearch.ui.SearchViewModel': 'com.pixel.intelligentsearch.feature.search.SearchViewModel',
    'com.pixel.intelligentsearch.ui.AnimatedMatrixBackground': 'com.pixel.intelligentsearch.feature.search.AnimatedMatrixBackground',
    'com.pixel.intelligentsearch.ui.SettingsScreensHub': 'com.pixel.intelligentsearch.feature.settings.SettingsScreensHub',
    'com.pixel.intelligentsearch.ui.SettingsViewModel': 'com.pixel.intelligentsearch.feature.settings.SettingsViewModel',
    'com.pixel.intelligentsearch.ui.BouncyClickable': 'com.pixel.intelligentsearch.feature.settings.BouncyClickable',
    'com.pixel.intelligentsearch.ui.TutorialManager': 'com.pixel.intelligentsearch.feature.settings.TutorialManager',
    'com.pixel.intelligentsearch.ui.TutorialSpotlightOverlay': 'com.pixel.intelligentsearch.feature.settings.TutorialSpotlightOverlay',
    'com.pixel.intelligentsearch.ui.LocalSettingsState': 'com.pixel.intelligentsearch.feature.settings.LocalSettingsState',
    'com.pixel.intelligentsearch.ui.LocalSettingsViewModel': 'com.pixel.intelligentsearch.feature.settings.LocalSettingsViewModel',
    'com.pixel.intelligentsearch.theme.': 'com.pixel.intelligentsearch.core.theme.',
    'com.pixel.intelligentsearch.data.': 'com.pixel.intelligentsearch.core.data.',
    'com.pixel.intelligentsearch.voice.': 'com.pixel.intelligentsearch.feature.voice.',
    'com.pixel.intelligentsearch.SearchActivity': 'com.pixel.intelligentsearch.feature.search.SearchActivity',
    'com.pixel.intelligentsearch.SettingsActivity': 'com.pixel.intelligentsearch.feature.settings.SettingsActivity',
    'com.pixel.intelligentsearch.WidgetActivity': 'com.pixel.intelligentsearch.feature.widget.WidgetActivity',
    'com.pixel.intelligentsearch.SearchWidgetProvider': 'com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider',
    'com.pixel.intelligentsearch.SearchTileService': 'com.pixel.intelligentsearch.feature.widget.SearchTileService',
    'com.pixel.intelligentsearch.PinWidgetReceiver': 'com.pixel.intelligentsearch.feature.widget.PinWidgetReceiver',
    'android:name=".SettingsActivity"': 'android:name=".feature.settings.SettingsActivity"',
    'android:name=".SearchActivity"': 'android:name=".feature.search.SearchActivity"',
    'android:name=".WidgetActivity"': 'android:name=".feature.widget.WidgetActivity"',
    'android:name=".SearchWidgetProvider"': 'android:name=".feature.widget.SearchWidgetProvider"',
    'android:name=".SearchTileService"': 'android:name=".feature.widget.SearchTileService"',
    'android:name=".PinWidgetReceiver"': 'android:name=".feature.widget.PinWidgetReceiver"',
    'android:name=".voice.': 'android:name=".feature.voice.'
};

let count = 0;
files.forEach(file => {
    let content = fs.readFileSync(file, 'utf8');
    let updated = content;
    for (const [oldVal, newVal] of Object.entries(replacements)) {
        if (oldVal.includes('"')) {
            updated = updated.split(oldVal).join(newVal);
        } else {
            const re = new RegExp(oldVal.replace(/\./g, '\\.'), 'g');
            updated = updated.replace(re, newVal);
        }
    }
    
    updated = updated.replace(/com\.pixel\.intelligentsearch\.ui\.\*/g, 'com.pixel.intelligentsearch.feature.settings.*\nimport com.pixel.intelligentsearch.feature.search.*');
    
    if (content !== updated) {
        fs.writeFileSync(file, updated);
        console.log('Updated: ' + path.basename(file));
        count++;
    }
});
console.log('Total files updated: ' + count);
