package com.pixel.intelligentsearch.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The profile bar Application Search and Widget Customization share: a chip for each of Profile 1 to [count], its
 * outlined icon filled while it's the [selected] one.
 */
@Composable
internal fun ProfileSelector(count: Int, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    ShellSelector(
        options = (1..count).toList(),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        itemHeight = 60.dp,
        // The profile chips' own colours: no track, the active one on the primary container, the rest outlined.
        gap = 8.dp,
        contentPadding = 0.dp,
        indicatorShape = RoundedCornerShape(16.dp),
        unselectedBorder = FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
    ) { id, isSelected ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                if (isSelected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            // At large text the number wraps under "Profile" rather than spill over the chip, and on the narrowest
            // phones the chip shows just the number.
            OneLineLabel(
                "Profile $id",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                shortText = "$id"
            )
        }
    }
}

/**
 * The top bar's Reset to Saved State and Save Profile State buttons for [profile], each confirming what it did. [onReset]
 * puts the profile back to its saved state, [onSave] records its current state as that saved state.
 */
@Composable
internal fun ProfileStateActions(profile: Int, onReset: () -> Unit, onSave: () -> Unit) {
    SavedStateActions(
        onReset = onReset,
        onSave = onSave,
        resetMessage = "Profile $profile reset to its saved state.",
        saveMessage = "Profile $profile saved.",
        saveDescription = "Save Profile State"
    )
}

/** The top bar's Reset to Saved State and Save buttons any page with a saved state uses, each toasting its message. */
@Composable
internal fun SavedStateActions(
    onReset: () -> Unit,
    onSave: () -> Unit,
    resetMessage: String,
    saveMessage: String,
    saveDescription: String
) {
    val context = LocalContext.current
    IconButton(onClick = {
        onReset()
        Toast.makeText(context, resetMessage, Toast.LENGTH_SHORT).show()
    }) {
        Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset to Saved State")
    }
    IconButton(onClick = {
        onSave()
        Toast.makeText(context, saveMessage, Toast.LENGTH_SHORT).show()
    }) {
        Icon(Icons.Outlined.Save, contentDescription = saveDescription)
    }
}
