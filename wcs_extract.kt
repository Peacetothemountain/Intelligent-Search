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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            // Live Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFF1E1B24), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Widget Container
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
                            .background(androidx.compose.ui.graphics.Color(0xFF33303D), RoundedCornerShape(28.dp))
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
                            Icon(ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_mic_original), contentDescription = "Voice", tint = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        
                        if (localShortcut != "None") {
                            val iconRes = shortcutOptions.find { it.first == localShortcut }?.second ?: Icons.Default.Close
                            Icon(iconRes, contentDescription = "Shortcut", tint = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.size(24.dp))
                        }
                    }
                    
                    // Action Circle
                    if (localThemeStyle == "Material You (Minimal)") {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFF33303D), androidx.compose.foundation.shape.CircleShape),
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
            }

            // Toggles Card
            SettingsCard {
                SettingsRowToggle(
                    title = "Display G Icon",
                    subtitle = "",
                    icon = ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored),
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
            
            // Design Segmented Buttons (System Design | Material Design)
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
                            Icon(Icons.Default.InvertColors, contentDescription = "Saturation", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
                                            androidx.compose.ui.graphics.Color(0xFF333333),
                                            androidx.compose.ui.graphics.Color(0xFFAA00FF)
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
                            Icon(Icons.Default.Opacity, contentDescription = "Opacity", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
                                            androidx.compose.ui.graphics.Color(0xFF222222),
                                            androidx.compose.ui.graphics.Color(0xFFBB86FC)
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
            
            if (localThemeStyle == "System Default" && localSubtheme == "Custom") {
                // If System Design -> Custom is selected, presumably they should also see sliders, but wait, the screenshot doesn't explicitly show it.
                // Let's assume the sliders are the same.
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
                            Icon(Icons.Default.InvertColors, contentDescription = "Saturation", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
                                            androidx.compose.ui.graphics.Color(0xFF333333),
                                            androidx.compose.ui.graphics.Color(0xFFAA00FF)
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
                            Icon(Icons.Default.Opacity, contentDescription = "Opacity", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
                                            androidx.compose.ui.graphics.Color(0xFF222222),
                                            androidx.compose.ui.graphics.Color(0xFFBB86FC)
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

            Text("WIDGET ACTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))

            // Actions Card
            SettingsCard {
                SettingsRowToggle(
                    title = "Voice Search Icon",
                    subtitle = "Show voice search icon in the widget",
                    icon = Icons.Default.Mic,
                    isChecked = localShowVoice,
                    onCheckedChange = { localShowVoice = it },
                    showDivider = localThemeStyle == "Material You (Minimal)" || localShortcut != "None"
                )
                
                if (localThemeStyle == "Material You (Minimal)") {
                    SettingsDropdownRow(
                        title = "Widget Action Icon",
                        subtitle = "(Material Design only) $localActionIcon",
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
                }
                
                SettingsRow(
                    title = "Widget Shortcut",
                    subtitle = localShortcut,
                    icon = ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_camera), 
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
                                    }
                                ) {
                                    val isSel = localShortcut == option.first
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                            .border(
                                                width = if (isSel) 2.dp else 0.dp,
                                                color = if (isSel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = option.second,
                                            contentDescription = option.first,
                                            tint = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCustomizable) {
                                            Box(modifier = Modifier.bouncyClickable {
                                                expandedDropdownFor = option.first
                                            }) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = option.first,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                                }
                                                androidx.compose.material3.DropdownMenu(
                                                    expanded = expandedDropdownFor == option.first,
                                                    onDismissRequest = { expandedDropdownFor = null }
                                                ) {
                                                    androidx.compose.material3.DropdownMenuItem(
                                                        text = { Text("Default") },
                                                        onClick = {
                                                            localShortcut = option.first
                                                            expandedDropdownFor = null
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
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
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
}


fun updateWidgets(context: Context) {
