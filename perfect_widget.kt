@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
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

    var localShowGIcon by remember { mutableStateOf(prefs.getBoolean("widget_show_g_icon", true)) }
    var localShowDoodle by remember { mutableStateOf(prefs.getBoolean("widget_show_doodle", true)) }
    var localThemeStyle by remember { mutableStateOf(prefs.getString("widget.theme.style", "System Default") ?: "System Default") }
    var localSubtheme by remember { mutableStateOf(prefs.getString("widget_subtheme", "System") ?: "System") }
    var localMaterialGIconTheme by remember { mutableStateOf(prefs.getString("widget_material_g_icon", "Material G Icon") ?: "Material G Icon") }
    var localHue by remember { mutableStateOf(prefs.getInt("widget_custom_hue", 277).toFloat()) }
    var localSaturation by remember { mutableStateOf(prefs.getInt("widget_custom_saturation", 51).toFloat()) }
    var localOpacity by remember { mutableStateOf(prefs.getInt("search.background.transparency", 28).toFloat()) }
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
                        localSubtheme = "System"
                        localMaterialGIconTheme = "Material G Icon"
                        localHue = 277f
                        localSaturation = 51f
                        localOpacity = 28f
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
                            .putString("widget_subtheme", localSubtheme)
                            .putString("widget_material_g_icon", localMaterialGIconTheme)
                            .putInt("widget_custom_hue", localHue.toInt())
                            .putInt("widget_custom_saturation", localSaturation.toInt())
                            .putInt("search.background.transparency", localOpacity.toInt())
                            .putBoolean("widget_show_voice", localShowVoice)
                            .putString("widget_action_icon", localActionIcon)
                            .putString("widget_shortcut", localShortcut)
                            .apply()
                        updateWidgets(context)
                        onBack()
                    }) {
                        Text("Apply", color = MaterialTheme.colorScheme.primary)
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
            SettingsCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(androidx.compose.ui.graphics.Color(0xFF1E1B24)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFF33303D), RoundedCornerShape(28.dp))
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (localShowGIcon) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored),
                                    contentDescription = "G Icon",
                                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (localShortcut != "None") {
                                val shortcutOption = shortcutOptions.find { it.first == localShortcut }
                                if (shortcutOption != null) {
                                    Icon(
                                        imageVector = shortcutOption.second,
                                        contentDescription = localShortcut,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                            if (localShowVoice) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        if (localThemeStyle == "Material You (Minimal)") {
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(androidx.compose.ui.graphics.Color(0xFF33303D), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val iconRes = when (localActionIcon) {
                                    "Search" -> com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored
                                    "Gemini" -> com.pixel.intelligentsearch.R.drawable.ic_gemini
                                    "Now Playing" -> com.pixel.intelligentsearch.R.drawable.ic_music
                                    else -> com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored
                                }
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = iconRes),
                                    contentDescription = "Action Icon",
                                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // G Icon options
            SettingsCard {
                SettingsRowToggle(
                    title = "Display G Icon",
                    subtitle = "Show Google logo in search bar",
                    icon = Icons.Default.Star,
                    isChecked = localShowGIcon,
                    onCheckedChange = { localShowGIcon = it },
                    showDivider = true
                )
                SettingsRowToggle(
                    title = "G Icon Doodle",
                    subtitle = "Show special event doodles",
                    icon = Icons.Default.Brush,
                    isChecked = localShowDoodle,
                    onCheckedChange = { localShowDoodle = it },
                    showDivider = false
                )
            }

            // Theme Buttons
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isSystem = localThemeStyle == "System Default"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isSystem) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                            .bouncyClickable { localThemeStyle = "System Default" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("System Design", style = MaterialTheme.typography.labelLarge, color = if (isSystem) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (!isSystem) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                            .bouncyClickable { localThemeStyle = "Material You (Minimal)" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Material Design", style = MaterialTheme.typography.labelLarge, color = if (!isSystem) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Floating Theme Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (localThemeStyle == "System Default") {
                    val systemOpts = listOf("System", "Light", "Dark", "Custom")
                    systemOpts.forEach { opt ->
                        val isSel = localSubtheme == opt
                        val bgModifier = if (opt == "Custom") {
                            Modifier.background(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(androidx.compose.ui.graphics.Color(0xFF3F51B5), androidx.compose.ui.graphics.Color(0xFFE91E63))
                                ),
                                shape = RoundedCornerShape(32.dp)
                            )
                        } else {
                            Modifier.background(if (isSel) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                        }
                        
                        val borderMod = if (isSel && opt != "Custom") Modifier.border(1.dp, androidx.compose.ui.graphics.Color.White, RoundedCornerShape(32.dp)) else Modifier
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .then(bgModifier)
                                .then(borderMod)
                                .bouncyClickable { localSubtheme = opt },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(opt, style = MaterialTheme.typography.labelMedium, color = if (opt == "Custom" || isSel) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val matOpts = listOf("Material", "Custom")
                    matOpts.forEach { opt ->
                        val isSel = localSubtheme == opt
                        val bgModifier = if (opt == "Custom") {
                            Modifier.background(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(androidx.compose.ui.graphics.Color(0xFF5E35B1), androidx.compose.ui.graphics.Color(0xFFAD1457))
                                ),
                                shape = RoundedCornerShape(32.dp)
                            )
                        } else {
                            Modifier.background(if (isSel) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                        }
                        val borderMod = if (isSel && opt != "Custom") Modifier.border(1.dp, androidx.compose.ui.graphics.Color.White, RoundedCornerShape(32.dp)) else Modifier
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .then(bgModifier)
                                .then(borderMod)
                                .bouncyClickable { localSubtheme = opt },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(opt, style = MaterialTheme.typography.labelMedium, color = if (opt == "Custom" || isSel) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (localThemeStyle == "Material You (Minimal)" && localSubtheme == "Custom") {
                // Material G Icon Row
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val opts = listOf("System G Icon", "Material G Icon", "Accented G Icon")
                        opts.forEach { opt ->
                            val isSel = localMaterialGIconTheme == opt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(if (isSel) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                                    .bouncyClickable { localMaterialGIconTheme = opt },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(opt, style = MaterialTheme.typography.labelSmall, color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (localSubtheme == "Custom") {
                // Sliders Card
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Hue
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Palette, contentDescription = "Hue", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Hue", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localHue.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.Red,
                                            androidx.compose.ui.graphics.Color.Yellow,
                                            androidx.compose.ui.graphics.Color.Green,
                                            androidx.compose.ui.graphics.Color.Cyan,
                                            androidx.compose.ui.graphics.Color.Blue,
                                            androidx.compose.ui.graphics.Color.Magenta,
                                            androidx.compose.ui.graphics.Color.Red
                                        )
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )) {
                                    Android17Slider(
                                        value = localHue,
                                        onValueChange = { localHue = it },
                                        valueRange = 0f..360f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Saturation
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.WaterDrop, contentDescription = "Saturation", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Saturation", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localSaturation.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.White,
                                            androidx.compose.ui.graphics.Color(0xFFE91E63)
                                        )
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )) {
                                    Android17Slider(
                                        value = localSaturation,
                                        onValueChange = { localSaturation = it },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Opacity
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Contrast, contentDescription = "Opacity", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Opacity", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localOpacity.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color.White
                                        )
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )) {
                                    Android17Slider(
                                        value = localOpacity,
                                        onValueChange = { localOpacity = it },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text("WIDGET ACTIONS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))

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
                if (localThemeStyle == "Material You (Minimal)") {
                    SettingsDropdownRow(
                        title = "Widget Action Icon",
                        subtitle = localActionIcon,
                        icon = Icons.Default.Search,
                        options = listOf("Search", "Gemini", "Now Playing"),
                        selectedOption = localActionIcon,
                        onOptionSelected = { localActionIcon = it },
                        showDivider = true,
                        optionIcons = mapOf(
                            "Search" to com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored,
                            "Gemini" to com.pixel.intelligentsearch.R.drawable.ic_gemini,
                            "Now Playing" to com.pixel.intelligentsearch.R.drawable.ic_music
                        )
                    )
                }
                SettingsRow(
                    title = "Widget Shortcut",
                    subtitle = localShortcut,
                    icon = Icons.Default.AddCircleOutline,
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
                                        if (isCustomizable) {
                                            expandedDropdownFor = option.first
                                        } else {
                                            localShortcut = option.first
                                            showShortcutSheet = false
                                        }
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = option.second,
                                            contentDescription = option.first,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(
                                                    if (localShortcut == option.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    androidx.compose.foundation.shape.CircleShape
                                                )
                                                .padding(16.dp),
                                            tint = if (localShortcut == option.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isCustomizable) {
                                            androidx.compose.material3.DropdownMenu(
                                                expanded = expandedDropdownFor == option.first,
                                                onDismissRequest = { expandedDropdownFor = null }
                                            ) {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Default (Google)") },
                                                    onClick = {
                                                        localShortcut = option.first
                                                        showShortcutSheet = false
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Custom Website (URL)") },
                                                    onClick = {
                                                        showCustomUrlDialogFor = option.first
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Custom App (APK)") },
                                                    onClick = {
                                                        showCustomAppDialogFor = option.first
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = option.first,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showCustomUrlDialogFor != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCustomUrlDialogFor = null },
                    title = { Text("Custom URL for ${showCustomUrlDialogFor}") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Enter URL") }
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
                    title = { Text("Custom App for ${showCustomAppDialogFor}") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Enter Package Name") }
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
