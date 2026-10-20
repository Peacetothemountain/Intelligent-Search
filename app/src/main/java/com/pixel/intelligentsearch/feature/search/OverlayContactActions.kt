package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Best Match card's contact buttons: labelled chips like an app's quick actions, so WhatsApp and WhatsApp
 * Business stay told apart. TalkBack reads each chip's full description ("Message Alex on WhatsApp"). They feel and
 * act like the card's own contact row: a tick on press, a click on release, and the keyboard put away.
 */
@Composable
internal fun ContactActionChips(actions: List<ContactAction>, activeIconPack: String, onLaunched: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val sensoryEngine = rememberTactileSonicEngine()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            LaunchedEffect(isPressed) {
                if (isPressed) sensoryEngine.tick(view)
            }
            AssistChip(
                onClick = {
                    // A double tap opens the app once, like the rows' buttons (same window as bouncyClickable).
                    if (ClickDebouncer.canClick(120L, interactionSource)) {
                        sensoryEngine.click(view)
                        onLaunched()
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        launchSafeIntent(context, Intent(action.intent))
                    }
                },
                interactionSource = interactionSource,
                label = {
                    Text(
                        action.kind.label,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontFamily = GoogleSansFlex,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                },
                leadingIcon = { ContactActionIcon(action, activeIconPack, MaterialTheme.colorScheme.onPrimaryContainer, AssistChipDefaults.IconSize) },
                modifier = Modifier.semantics { contentDescription = action.contentDescription },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                border = null,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/** A contact row's buttons: round icon buttons under the name, in the row's own avatar colours. */
@Composable
internal fun ContactActionButtons(actions: List<ContactAction>, activeIconPack: String, modifier: Modifier = Modifier, onLaunched: () -> Unit) {
    val context = LocalContext.current
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        actions.forEach { action ->
            Box(
                modifier = Modifier
                    .bouncyClickable(shape = CircleShape) {
                        onLaunched()
                        launchSafeIntent(context, Intent(action.intent))
                    }
                    .semantics { contentDescription = action.contentDescription }
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ContactActionIcon(action, activeIconPack, MaterialTheme.colorScheme.onSurfaceVariant, 20.dp)
            }
        }
    }
}

/** The system actions' Material icon, or a chat app's own (themed) icon. */
@Composable
private fun ContactActionIcon(action: ContactAction, activeIconPack: String, tint: Color, size: Dp) {
    val iconPackage = action.iconPackage
    if (iconPackage == null) {
        val vector = when (action.kind) {
            ContactActionKind.CALL -> Icons.Default.Call
            ContactActionKind.EMAIL -> Icons.Default.Email
            else -> Icons.AutoMirrored.Filled.Message
        }
        Icon(imageVector = vector, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        return
    }
    val context = LocalContext.current
    var appIcon by remember(iconPackage, activeIconPack) { mutableStateOf(peekThemedAppIcon(iconPackage, activeIconPack)) }
    LaunchedEffect(iconPackage, activeIconPack) {
        if (appIcon == null) {
            appIcon = withContext(Dispatchers.IO) { getThemedAppIcon(context, iconPackage, activePackOverride = activeIconPack) }
        }
    }
    val icon = appIcon
    if (icon != null) {
        Image(
            bitmap = icon.bitmap,
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = if (icon.isMonochrome) ColorFilter.tint(tint) else null
        )
    } else {
        Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}
