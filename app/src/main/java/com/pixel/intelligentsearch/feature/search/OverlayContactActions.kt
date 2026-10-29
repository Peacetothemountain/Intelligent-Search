package com.pixel.intelligentsearch.feature.search

import androidx.compose.runtime.getValue
import com.pixel.intelligentsearch.core.ui.rememberAppIcon
import com.pixel.intelligentsearch.core.ui.AppIconImage
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.pixel.intelligentsearch.feature.settings.bouncyClickable

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

/**
 * A contact row's buttons: round 40dp icon buttons under the name, in the row's own avatar colors, each in its own 48dp
 * touch area so neighbors don't share one.
 */
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
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ContactActionIcon(action, activeIconPack, MaterialTheme.colorScheme.onSurfaceVariant, 20.dp)
                }
            }
        }
    }
}

/**
 * The system actions' Material icon, or a chat app's own (themed) icon. Until that loads (or without one) a chat app
 * shows two bubbles, so it isn't taken for Message's single one.
 */
@Composable
internal fun ContactActionIcon(action: ContactAction, activeIconPack: String, tint: Color, size: Dp) {
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
    AppIconImage(rememberAppIcon(iconPackage, activeIconPack), Modifier.size(size), tint = tint) {
        Icon(imageVector = Icons.Default.Forum, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}
