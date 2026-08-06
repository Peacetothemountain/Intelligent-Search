import sys
import re

def process():
    path = "app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    new_path = "C:/Users/caref/.gemini/antigravity/brain/c1245d00-9456-47ec-aead-18f7f76b3d78/scratch/new_widget_customization.kt"
    with open(new_path, "r", encoding="utf-8") as f:
        new_func = f.read()

    # Find the start of the function
    start_pattern = r"@OptIn\(ExperimentalMaterial3Api::class\)\s*@Composable\s*fun WidgetCustomizationScreen"
    match = re.search(start_pattern, content)
    if not match:
        print("Could not find start of WidgetCustomizationScreen")
        return
        
    start_idx = match.start()
    
    # Bracket matching to find the end
    brace_count = 0
    in_func = False
    end_idx = -1
    
    for i in range(start_idx, len(content)):
        if content[i] == '{':
            brace_count += 1
            in_func = True
        elif content[i] == '}':
            brace_count -= 1
            if in_func and brace_count == 0:
                end_idx = i + 1
                break
                
    if end_idx == -1:
        print("Could not find end of WidgetCustomizationScreen")
        return
        
    new_content = content[:start_idx] + new_func + content[end_idx:]
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(new_content)
        
    print("Successfully replaced WidgetCustomizationScreen")

process()
