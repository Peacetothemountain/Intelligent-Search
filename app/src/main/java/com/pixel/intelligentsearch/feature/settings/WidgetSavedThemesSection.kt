package com.pixel.intelligentsearch.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.core.data.widget.SavedWidgetTheme
import com.pixel.intelligentsearch.core.data.widget.WidgetThemeStore

/** What the Saved Themes section shows and does, for the design being edited. */
internal class WidgetSavedThemes(
    /** The design's saved themes, oldest first. */
    val themes: List<SavedWidgetTheme>,
    /** Saves the design as it is in the editor under a name. */
    val save: (String) -> Unit,
    /** Puts a theme in the editor (saved to the widget with Save, like any other change). */
    val apply: (SavedWidgetTheme) -> Unit,
    /** Renames a theme; false when the name is taken. */
    val rename: (SavedWidgetTheme, String) -> Boolean,
    val delete: (SavedWidgetTheme) -> Unit
)

/**
 * Named widget designs: save the design being edited under a name, then apply, rename or delete it. Applying puts
 * the theme in the editor and its preview; Save writes it to the widget, so a theme can be tried and discarded.
 */
@Composable
internal fun WidgetSavedThemesSection(saved: WidgetSavedThemes, ui: WidgetEditorState) {
    val context = LocalContext.current
    var themeNameDialogFor by ui::themeNameDialogFor
    var themeNameInput by ui::themeNameInput

    SettingsSectionHeader("Saved Themes")
    SettingsCard(lit = false) {
        SettingsRow(
            title = "Save Current Design",
            subtitle = "Keep This Design Under a Name to Apply Again Later.",
            icon = Icons.Outlined.BookmarkAdd,
            onClick = {
                themeNameInput = ""
                themeNameDialogFor = ""
            },
            showDivider = saved.themes.isNotEmpty()
        )
        saved.themes.forEachIndexed { index, theme ->
            var menuOpen by remember(theme.name) { mutableStateOf(false) }
            Box {
                SettingsRow(
                    title = theme.name,
                    subtitle = "Tap to Apply. Touch and Hold to Rename or Delete.",
                    icon = Icons.Outlined.Palette,
                    onClick = {
                        saved.apply(theme)
                        Toast.makeText(context, "\"${theme.name}\" applied: Save to put it on the widget", Toast.LENGTH_SHORT).show()
                    },
                    onLongClick = { menuOpen = true },
                    showDivider = index < saved.themes.lastIndex
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            themeNameInput = theme.name
                            themeNameDialogFor = theme.name
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            saved.delete(theme)
                        }
                    )
                }
            }
        }
    }

    val renaming = themeNameDialogFor
    if (renaming != null) {
        val name = themeNameInput.trim()
        val renamingTheme = saved.themes.firstOrNull { it.name == renaming }.takeIf { renaming.isNotEmpty() }
        val taken = saved.themes.any { it.name == name && it.name != renaming }
        AlertDialog(
            onDismissRequest = { themeNameDialogFor = null },
            title = { Text(if (renamingTheme == null) "Save Theme" else "Rename Theme") },
            text = {
                OutlinedTextField(
                    value = themeNameInput,
                    onValueChange = { themeNameInput = it.take(WidgetThemeStore.MAX_NAME_LENGTH) },
                    label = { Text("Theme Name") },
                    singleLine = true,
                    isError = taken && renamingTheme != null,
                    supportingText = when {
                        renamingTheme != null && taken -> { { Text("Another theme already has this name") } }
                        renamingTheme == null && taken -> { { Text("Saving replaces the theme with this name") } }
                        else -> null
                    }
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotEmpty() && !(taken && renamingTheme != null), onClick = {
                    if (renamingTheme == null) saved.save(name) else saved.rename(renamingTheme, name)
                    themeNameDialogFor = null
                }) { Text(if (renamingTheme == null) "Save" else "Rename") }
            },
            dismissButton = { TextButton(onClick = { themeNameDialogFor = null }) { Text("Cancel") } }
        )
    }
}
