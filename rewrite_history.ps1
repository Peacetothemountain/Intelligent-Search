
git config user.name "NB Designs"
git config user.email "support.nbdesigns@gmail.com"

$env:GIT_SEQUENCE_EDITOR = "powershell -ExecutionPolicy Bypass -File .\editor.ps1"
git rebase -i b0082f11

while (Test-Path ".git/rebase-merge") {
    git rm --cached --ignore-unmatch user_messages.txt user_messages_formatted.md git_log.txt parse_messages.py rewrite.py update_settings.py original_colorful.xml SearchWidgetProvider_b0082f11.kt SettingsScreens_b0082f11.kt WidgetCustomizationScreen.txt WidgetCustomizationScreen_extracted.txt java_pid30104.hprof 2>$null
    
    Remove-Item -Force "user_messages.txt","user_messages_formatted.md","git_log.txt","parse_messages.py","rewrite.py","update_settings.py","original_colorful.xml","SearchWidgetProvider_b0082f11.kt","SettingsScreens_b0082f11.kt","WidgetCustomizationScreen.txt","WidgetCustomizationScreen_extracted.txt","java_pid30104.hprof" -ErrorAction SilentlyContinue
    
    git commit --amend --no-edit --author="NB Designs <support.nbdesigns@gmail.com>"
    git rebase --continue
}

