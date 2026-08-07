import re

with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'r') as f:
    content = f.read()

# Fix 1: Reset Tutorial Progress
content = content.replace(
'''            SettingsRow(
                title = "Reset Tutorial Progress",
                subtitle = "Mark tutorial as incomplete and restart step guide",
                icon = Icons.Outlined.Refresh,
                onClick = {
                    prefs.edit()''',
'''            val view = androidx.compose.ui.platform.LocalView.current
            SettingsRow(
                title = "Reset Tutorial Progress",
                subtitle = "Mark tutorial as incomplete and restart step guide",
                icon = Icons.Outlined.Refresh,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLICK)
                    prefs.edit()'''
)

# Fix 2: Reset & Save buttons in WidgetSettingsScreen
content = content.replace(
'''    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = {
                        localShowGIcon = true''',
'''    val view = androidx.compose.ui.platform.LocalView.current
    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLICK)
                        localShowGIcon = true'''
)

# Fix 3: Remove onBack() and add haptics to Save button
content = content.replace(
'''                    androidx.compose.material3.TextButton(onClick = {
                        onBack()
                    }) {
                        Text("Save", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )''',
'''                    androidx.compose.material3.TextButton(onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLICK)
                    }) {
                        Text("Save", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )'''
)

# Fix 4: ManageHiddenAppsScreen delay
content = content.replace(
'''    fun ManageHiddenAppsScreen(prefs: SharedPreferences, onBack: () -> Unit) {'''.strip(),
'''fun ManageHiddenAppsScreen(prefs: SharedPreferences, onBack: () -> Unit) {'''
)
content = content.replace(
'''    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply { addCategory(android.content.Intent.CATEGORY_LAUNCHER) }
            val apps = pm.queryIntentActivities(intent, 0).map { ''',
'''    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply { addCategory(android.content.Intent.CATEGORY_LAUNCHER) }
            val apps = pm.queryIntentActivities(intent, 0).map { '''
)

# Fix 5: ManageHiddenAppsScreen search bar Pill
content = content.replace(
'''                    if (isSearching) {
                        androidx.compose.material3.TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search apps...") },
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {''',
'''                    if (isSearching) {
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(end = 16.dp)
                        ) {
                            androidx.compose.material3.TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search apps...") },
                                singleLine = true,
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }
                    } else {'''
)

with open('app/src/main/java/com/pixel/intelligentsearch/feature/settings/SettingsScreens.kt', 'w') as f:
    f.write(content)
