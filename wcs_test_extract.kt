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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
            val breathingTransition = rememberInfiniteTransition(label = "breathing")
            val breathingScale by breathingTransition.animateFloat(
                initialValue = 0.95f, targetValue = 1.05f,
                animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "breathingScale"
            )
            val breathingAlpha by breathingTransition.animateFloat(
                initialValue = 0.1f, targetValue = 0.4f,
                animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "breathingAlpha"
            )

            // Luminous Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = breathingAlpha), androidx.compose.ui.graphics.Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Wrapper to apply scale to widget only without resizing container
                Box(modifier = Modifier.graphicsLayer { scaleX = breathingScale; scaleY = breathingScale }) {
                    // Fake Widget Preview
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
                            .padding(horizontal = 16.dp)
                    ) {
                        Image(
                            imageVector = ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored),
                            contentDescription = "G Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Mic", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            WidgetCustomizationCard {
                var showVoice by rememberBooleanPreference(prefs, "widget_show_voice", true) { updateWidgets(context) }
                SettingsRowToggle(
                    title = "Voice Search Icon",
                    subtitle = "Show voice search icon in the widget",
                    icon = Icons.Default.Mic,
                    isChecked = showVoice,
                    onCheckedChange = { showVoice = it },
                    showDivider = true
                )
                var actionIcon by rememberStringPreference(prefs, "widget_action_icon", "Search")
                SettingsDropdownRow(
                    title = "Widget Action Icon",
                    subtitle = actionIcon,
                    icon = Icons.Default.Search,
                    options = listOf("Search", "Gemini", "Now Playing"),
                    selectedOption = actionIcon,
                    onOptionSelected = {
                        actionIcon = it
                        updateWidgets(context)
                    },
                    showDivider = true,
                    optionIcons = mapOf(
                        "Search" to com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored,
                        "Gemini" to com.pixel.intelligentsearch.R.drawable.ic_gemini,
                        "Now Playing" to com.pixel.intelligentsearch.R.drawable.ic_music
                    )
                )
                var widgetShortcut by rememberStringPreference(prefs, "widget_shortcut", "None")
                SettingsRow(
                    title = "Widget Shortcut",
                    subtitle = widgetShortcut,
                    icon = Icons.Default.AddCircleOutline,
                    onClick = { showShortcutSheet = true },
                    showDivider = false
                )

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
                                            widgetShortcut = option.first
                                            showShortcutSheet = false
                                            updateWidgets(context)
                                        }.padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    if (widgetShortcut == option.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(24.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = option.second,
                                                contentDescription = option.first,
                                                tint = if (widgetShortcut == option.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                                                            widgetShortcut = option.first
                                                            showShortcutSheet = false
                                                            updateWidgets(context)
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
                                widgetShortcut = showCustomUrlDialogFor!!
                                showShortcutSheet = false
                                showCustomUrlDialogFor = null
                                updateWidgets(context)
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
                                widgetShortcut = showCustomAppDialogFor!!
                                showShortcutSheet = false
                                showCustomAppDialogFor = null
                                updateWidgets(context)
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