package com.pixel.intelligentsearch.feature.tabs.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import com.pixel.intelligentsearch.core.ui.symbols.Symbols

/**
 * The top bar of every page in the preview, Intelligent Search's SettingsTopBar: the back arrow, the page's title in
 * headline small bold (one line, shrinking to fit), and the page's actions on the right, all icon buttons that press
 * and vibrate as every button does. It has no color of its own unless [containerColor] gives one (Gallery's, whose
 * windows on the wallpaper pass under it), so the page and its glow run unbroken from the top of the screen down.
 *
 * [onTitleClick], when given, springs the page back to its top (Android 18's scroll to top).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = containerColor),
        // The shell pads the page by the status bar already.
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = {
            if (onTitleClick == null) SettingsPageTitle(title)
            // The pane title stays outside the button, which merges what is in it.
            // Its touch target's padding is taken back, so the title starts where every other page's does.
            else Box(Modifier.semantics { paneTitle = title }.offset(x = (-8).dp)) {
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .bouncyClickable(RoundedCornerShape(16.dp), onClickLabel = "Scroll to the top", onClick = onTitleClick)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) { SettingsPageTitle(title, isPaneTitle = false) }
            }
        },
        navigationIcon = { PreviewIconButton(Symbols.AutoMirrored.Outlined.ArrowBack, "Back", tint = LocalContentColor.current, onClick = onBack) },
        actions = actions
    )
}

/** A top bar's action: an icon button in the bar's action color. */
@Composable
internal fun TopBarAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    PreviewIconButton(icon, contentDescription, modifier, tint = LocalContentColor.current, onClick = onClick)

/**
 * A page's title in its top bar: one line, shrinking to fit, and both the page's heading and its pane title, so
 * TalkBack names the page as it opens.
 */
@Composable
internal fun SettingsPageTitle(
    text: String,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    fontWeight: FontWeight = FontWeight.Bold,
    isPaneTitle: Boolean = true
) {
    Text(
        text,
        style = style,
        fontWeight = fontWeight,
        maxLines = 1,
        autoSize = oneLineAutoSize(style),
        modifier = Modifier.semantics {
            heading()
            if (isPaneTitle) paneTitle = text
        }
    )
}

/** A one-line text's auto size: from its own size down by at most [ONE_LINE_SHRINK], in quarter-sp steps. */
internal fun oneLineAutoSize(style: TextStyle): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = oneLineMinFontSize(style), maxFontSize = style.fontSize, stepSize = ONE_LINE_AUTO_SIZE_STEP.sp)

private const val ONE_LINE_AUTO_SIZE_STEP = 0.25f
private const val ONE_LINE_SHRINK = 0.4f

internal fun oneLineMinFontSize(style: TextStyle): TextUnit {
    val max = style.fontSize.value
    return (max - kotlin.math.floor(max * ONE_LINE_SHRINK / ONE_LINE_AUTO_SIZE_STEP) * ONE_LINE_AUTO_SIZE_STEP).sp
}
