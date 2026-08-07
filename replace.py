import sys
with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()
with open('scratch_new_widget.kt', 'r', encoding='utf-8') as f:
    new_lines = f.readlines()
new_content = ''.join(lines[:1940]) + ''.join(new_lines) + '\n' + ''.join(lines[2203:])
with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)
