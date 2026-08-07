@OptIn(ExperimentalMaterial3Api::class)
fun WidgetCustomizationScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    var showShortcutSheet by remember { mutableStateOf(false) }
    
    var showCustomUrlDialogFor by remember { mutableStateOf<String?>(null) }
    var showCustomAppDialogFor by remember { mutableStateOf<String?>(null) }
    var customInputValue by remember { mutableStateOf("") }
    var expandedDropdownFor by remember { mutableStateOf<String?>(null) }
    
    val shortcutOptions = listOf(
        "None" to Icons.Default.Close,
        "Google Lens" to ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_camera),
        "Live" to Icons.Default.AutoAwesome,
        "Translate (text)" to Icons.Default.Translate,
        "Translate (camera)" to Icons.Default.DocumentScanner,
        "Weather" to Icons.Default.WbSunny,
        "Sports" to Icons.Default.SportsBasketball,
        "Dictionary" to Icons.AutoMirrored.Filled.MenuBook,
        "Homework" to Icons.Default.School,
        "Finance" to Icons.AutoMirrored.Filled.TrendingUp,
        "Saved" to Icons.Default.Bookmark,
        "News" to Icons.AutoMirrored.Filled.Article
    )

    // Local State Initialization
    var localShowGIcon by remember { mutableStateOf(prefs.getBoolean("widget_show_g_icon", true)) }
    var localShowDoodle by remember { mutableStateOf(prefs.getBoolean("widget_show_doodle", false)) }
    var localThemeStyle by remember { mutableStateOf(prefs.getString("widget.theme.style", "System Default") ?: "System Default") }
    var localMaterialSubtheme by remember { mutableStateOf(prefs.getString("widget_material_subtheme", "Material") ?: "Material") }
    
    var localShowVoice by remember { mutableStateOf(prefs.getBoolean("widget_show_voice", true)) }
    var localActionIcon by remember { mutableStateOf(prefs.getString("widget_action_icon", "Search") ?: "Search") }
    var localShortcut by remember { mutableStateOf(prefs.getString("widget_shortcut", "Google Lens") ?: "Google Lens") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = {
                        localShowGIcon = true
                        localShowDoodle = false
                        localThemeStyle = "System Default"
                        localMaterialSubtheme = "Material"
                        localShowVoice = true
                        localActionIcon = "Search"
                        localShortcut = "Google Lens"
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.onSurface)
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        prefs.edit()
                            .putBoolean("widget_show_g_icon", localShowGIcon)
                            .putBoolean("widget_show_doodle", localShowDoodle)
                            .putString("widget.theme.style", localThemeStyle)
                            .putString("widget_material_subtheme", localMaterialSubtheme)
                            .putBoolean("widget_show_voice", localShowVoice)
                            .putString("widget_action_icon", localActionIcon)
                            .putString("widget_shortcut", localShortcut)
                            .apply()
                        updateWidgets(context)
                        onBack()
                    }) {
                        Text("Apply", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Simulate widget look based on local state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Pill
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .background(if (localThemeStyle == "Material You (Minimal)") MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.White, RoundedCornerShape(28.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (localShowGIcon) {
                            if (localShowDoodle) {
                                Icon(Icons.Default.Celebration, contentDescription = "Doodle", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored), contentDescription = "G Logo", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        
                        if (localShowVoice) {
                            Icon(ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_mic_original), contentDescription = "Voice", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        
                        if (localShortcut != "None") {
                            val iconRes = shortcutOptions.find { it.first == localShortcut }?.second ?: Icons.Default.Close
                            Icon(iconRes, contentDescription = "Shortcut", tint = if (localThemeStyle == "Material You (Minimal)") MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.size(24.dp))
                        }
                    }
                    
                    // Action Circle
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (localThemeStyle == "Material You (Minimal)") MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val actIcon = when (localActionIcon) {
                            "Search" -> ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored)
                            "Gemini" -> ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_gemini)
                            "Now Playing" -> ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_music)
                            else -> Icons.Default.Search
                        }
                        Icon(actIcon, contentDescription = "Action", tint = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Toggles Card
            SettingsCard {
                SettingsRowToggle(
                    title = "Display G Icon",
                    subtitle = null,
                    icon = null, // In screenshot there is no icon for G Icon? Wait, screenshot showed a G icon. I'll use text or a dummy icon.
                    isChecked = localShowGIcon,
                    onCheckedChange = { localShowGIcon = it },
                    showDivider = true
                )
                SettingsRowToggle(
                    title = "G Icon Doodle",
                    subtitle = "Use Google's Event Icon to Celebrate Special Occasions.",
                    icon = Icons.Default.CalendarToday,
                    isChecked = localShowDoodle,
                    onCheckedChange = { localShowDoodle = it },
                    showDivider = false
                )
            }
            
            // Design Segmented Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isSystem = localThemeStyle != "Material You (Minimal)"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .background(if (isSystem) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(28.dp))
                        .bouncyClickable { localThemeStyle = "Google App (Default)" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("System Design", style = MaterialTheme.typography.labelLarge, color = if (isSystem) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .background(if (!isSystem) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(28.dp))
                        .bouncyClickable { localThemeStyle = "Material You (Minimal)" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Material Design", style = MaterialTheme.typography.labelLarge, color = if (!isSystem) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Sub-theme Segmented Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isMaterial = localMaterialSubtheme == "Material"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, if (isMaterial) MaterialTheme.colorScheme.outline else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(32.dp))
                        .background(if (isMaterial) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                        .bouncyClickable { localMaterialSubtheme = "Material" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Material", style = MaterialTheme.typography.labelLarge)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            brush = if (!isMaterial) androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(androidx.compose.ui.graphics.Color(0xFF00ACC1), androidx.compose.ui.graphics.Color(0xFF5E35B1))
                            ) else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.2f))),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .bouncyClickable { localMaterialSubtheme = "Custom" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Custom", style = MaterialTheme.typography.labelLarge, color = if (!isMaterial) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }

            Text("WIDGET ACTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp))

            // Actions Card
            SettingsCard {
                SettingsRowToggle(
                    title = "Voice Search Icon",
                    subtitle = "Show voice search icon in the widget",
                    icon = Icons.Default.Mic,
                    isChecked = localShowVoice,
                    onCheckedChange = { localShowVoice = it },
                    showDivider = true
                )
                
                SettingsDropdownRow(
                    title = "Widget Action Icon",
                    subtitle = if (localThemeStyle == "Material You (Minimal)") "(Material Design only) $localActionIcon" else localActionIcon,
                    icon = Icons.Default.Search,
                    options = listOf("Search", "Gemini", "Now Playing"),
                    selectedOption = localActionIcon,
                    onOptionSelected = {
                        localActionIcon = it
                    },
                    showDivider = true,
                    optionIcons = mapOf(
                        "Search" to com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored,
                        "Gemini" to com.pixel.intelligentsearch.R.drawable.ic_gemini,
                        "Now Playing" to com.pixel.intelligentsearch.R.drawable.ic_music
                    )
                )
                
                SettingsRow(
                    title = "Widget Shortcut",
                    subtitle = localShortcut,
                    icon = ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_camera), // Just an icon
                    onClick = { showShortcutSheet = true },
                    showDivider = false
                )
            }

            if (showShortcutSheet) {
                ModalBottomSheet(onDismissRequest = { showShortcutSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Select Widget Shortcut", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(shortcutOptions.size) { index ->
                                val option = shortcutOptions[index]
                                val isCustomizable = option.first in listOf("Weather", "Sports", "Dictionary")
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.bouncyClickable {
                                        if (option.first == "Google Lens" || option.first == "Translate (camera)") {
                                            val isInstalled = try {
                                                context.packageManager.getPackageInfo("com.google.ar.lens", 0)
                                                true
                                            } catch (e: Exception) {
                                                false
                                            }
                                            if (!isInstalled) {
                                                try {
                                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.ar.lens")))
                                                } catch (e: Exception) {
                                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.lens")))
                                                }
                                                showShortcutSheet = false
                                                return@bouncyClickable
                                            }
                                        }
                                        localShortcut = option.first
                                        showShortcutSheet = false
                                    }.padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                if (localShortcut == option.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(24.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = option.second,
                                            contentDescription = option.first,
                                            tint = if (localShortcut == option.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (isCustomizable) {
                                        Box {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.bouncyClickable { expandedDropdownFor = option.first }) {
                                                Text(
                                                    text = option.first,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Customize", modifier = Modifier.size(16.dp))
                                            }
                                            androidx.compose.material3.DropdownMenu(
                                                expanded = expandedDropdownFor == option.first,
                                                onDismissRequest = { expandedDropdownFor = null },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                            ) {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Default (Google)") },
                                                    onClick = {
                                                        prefs.edit().putString("${option.first}_custom_type", "default").apply()
                                                        expandedDropdownFor = null
                                                        localShortcut = option.first
                                                        showShortcutSheet = false
                                                    }
                                                )
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Custom Website (URL)") },
                                                    onClick = {
                                                        val currentVal = prefs.getString("${option.first}_custom_value", "") ?: ""
                                                        customInputValue = if (prefs.getString("${option.first}_custom_type", "") == "url") currentVal else ""
                                                        showCustomUrlDialogFor = option.first
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Custom App (APK)") },
                                                    onClick = {
                                                        val currentVal = prefs.getString("${option.first}_custom_value", "") ?: ""
                                                        customInputValue = if (prefs.getString("${option.first}_custom_type", "") == "app") currentVal else ""
                                                        showCustomAppDialogFor = option.first
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = option.first,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
            
            if (showCustomUrlDialogFor != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCustomUrlDialogFor = null },
                    title = { Text("Custom URL for $showCustomUrlDialogFor") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Enter full URL (https://...)") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            prefs.edit()
                                .putString("${showCustomUrlDialogFor}_custom_type", "url")
                                .putString("${showCustomUrlDialogFor}_custom_value", customInputValue)
                                .apply()
                            localShortcut = showCustomUrlDialogFor!!
                            showShortcutSheet = false
                            showCustomUrlDialogFor = null
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showCustomUrlDialogFor = null }) { Text("Cancel") }
                    }
                )
            }

            if (showCustomAppDialogFor != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCustomAppDialogFor = null },
                    title = { Text("Custom App for $showCustomAppDialogFor") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Enter Package Name (e.g. com.example.app)") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            prefs.edit()
                                .putString("${showCustomAppDialogFor}_custom_type", "app")
                                .putString("${showCustomAppDialogFor}_custom_value", customInputValue)
                                .apply()
                            localShortcut = showCustomAppDialogFor!!
                            showShortcutSheet = false
                            showCustomAppDialogFor = null
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showCustomAppDialogFor = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}
