package com.pixel.intelligentsearch.feature.tabs.gallery

import com.pixel.intelligentsearch.feature.tabs.ui.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.foundation.selection.selectableGroup
import com.pixel.intelligentsearch.feature.settings.MaterialMorphShapes
import androidx.graphics.shapes.toPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/* ── Popups: menus, dialogs, the share sheet. Separate windows on Android, so solid surface container high. ── */

/** A menu row; one of a set of choices (the backdrop) says whether it is the [selected] one. */
internal class MenuItem(val icon: ImageVector, val label: String, val selected: Boolean? = null, val onClick: () -> Unit)

internal class DialogAction(val label: String, val kind: ButtonKind = ButtonKind.Text, val onClick: () -> Boolean = { true })

internal sealed interface Pop {
    class Menu(val anchor: Rect, val items: List<MenuItem>, val title: String? = null) : Pop
    class Dialog(
        val title: String,
        val body: String? = null,
        val actions: List<DialogAction>,
        val stacked: Boolean = false,
        val input: String? = null,
        val inputHint: String? = null,
        val onInput: ((String) -> String?)? = null,
        val picks: List<Look>? = null,
        val onPick: ((Int) -> Unit)? = null
    ) : Pop
    class Share(val look: Look) : Pop

    /**
     * How Looks Work, from the top bar's info icon: what a look is and how to turn the fan, then the backdrop choice,
     * Put Back Your Last Look ([putBack], while there is a look before) and Bring Back Your Wallpaper ([bringBack], while a
     * wallpaper set from here can still be undone).
     */
    class Info(
        val backdrop: Backdrop,
        val onBackdrop: (Backdrop) -> Unit,
        val putBack: (() -> Unit)?,
        val bringBack: (() -> Unit)?
    ) : Pop

    /** Set [look]'s wallpaper on the phone: where, and whether it can be undone ([canUndo]), asked before anything is set. */
    class Wallpaper(val look: Look, val canUndo: Boolean, val onSet: (PhoneWallpaper.Where) -> Unit) : Pop {
        var busy by mutableStateOf(false)
    }
}

/**
 * The open popup over the page, or nothing: a menu, a dialog over the dim, or a sheet (the preview's one sheet, which
 * slides in and out and drags down to close). A tap outside or back closes it. The page covers the pill meanwhile.
 */
@Composable
internal fun PopLayer(pop: Pop?, phoneDark: Boolean, onClose: () -> Unit, snack: (Snack) -> Unit) {
    PreviewSheet(
        value = pop?.takeIf { it is Pop.Share || it is Pop.Wallpaper || it is Pop.Info },
        onDismiss = onClose,
        paneTitle = {
            when (it) {
                is Pop.Share -> "Share Look Code"
                is Pop.Info -> "How Looks Work"
                else -> "Set Wallpaper"
            }
        }
    ) { sheet ->
        when (sheet) {
            is Pop.Share -> ShareSheet(sheet.look, phoneDark, onClose, snack)
            is Pop.Wallpaper -> WallpaperSheet(sheet, phoneDark, onClose)
            is Pop.Info -> InfoSheet(sheet, onClose)
            else -> {}
        }
    }
    if (pop == null || pop is Pop.Share || pop is Pop.Wallpaper || pop is Pop.Info) return
    BackHandler { onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .then(if (pop is Pop.Dialog) Modifier.background(scrimColor()) else Modifier)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        when (pop) {
            is Pop.Menu -> MenuPopup(pop, onClose)
            is Pop.Dialog -> DialogPopup(pop, phoneDark, onClose)
            else -> {}
        }
    }
}

/** Grows in from its anchor on the spatial spring (scale only). */
@Composable
private fun Modifier.growIn(origin: TransformOrigin): Modifier {
    val s = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) { s.animateTo(1f, Springs.spatial()) }
    return graphicsLayer {
        scaleX = s.value
        scaleY = s.value
        transformOrigin = origin
    }
}

@Composable
private fun MenuPopup(menu: Pop.Menu, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val m = with(density) { 8.dp.roundToPx() }
        val up = menu.anchor.bottom + size.height + m > h
        val x = (menu.anchor.right - size.width).roundToInt().coerceIn(m, (w - size.width - m).coerceAtLeast(m))
        val y = (if (up) menu.anchor.top - size.height else menu.anchor.bottom).roundToInt().coerceIn(m, (h - size.height - m).coerceAtLeast(m))
        Column(
            Modifier
                .offset { IntOffset(x, y) }
                .onSizeChanged { size = it }
                .growIn(TransformOrigin(1f, if (up) 1f else 0f))
                .widthIn(min = 200.dp, max = 300.dp)
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .semantics { paneTitle = menu.title ?: "Menu" }
                .padding(4.dp)
        ) {
            if (menu.title != null) {
                Text(
                    menu.title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp).semantics { heading() }
                )
            }
            for (item in menu.items) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .bouncyClickable(RoundedCornerShape(16.dp), role = if (item.selected != null) Role.RadioButton else Role.Button) {
                            onClose()
                            item.onClick()
                        }
                        .then(if (item.selected != null) Modifier.semantics { selected = item.selected } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(item.icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(item.label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun DialogPopup(d: Pop.Dialog, phoneDark: Boolean, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(TextFieldValue(d.input ?: "", TextRange(0, (d.input ?: "").length))) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime).padding(24.dp), contentAlignment = Alignment.Center) {
      DialogSurface(d.title, Modifier.heightIn(max = 640.dp)) {
        // What it says scrolls if it must; its buttons stay below it, always in reach.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            SheetTitle(d.title)
            d.body?.let { SheetText(it); Spacer(Modifier.height(12.dp)) }
            if (d.onInput != null) {
                val multi = d.inputHint != null
                BasicTextField(
                    value = text,
                    onValueChange = { text = if (multi) it else it.copy(text = it.text.take(24)); error = null },
                    singleLine = !multi,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = scheme.onSurface,
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                        fontSize = if (multi) 12.sp else MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { d.onInput.invoke(text.text.trim())?.let { error = it } ?: onClose() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = if (multi) "Look code" else "Name" }
                        .border(if (error != null) 2.dp else 1.dp, if (error != null) scheme.error else scheme.outline, RoundedCornerShape(12.dp))
                        // A long pasted code scrolls inside the field, which keeps to a few lines, so the buttons stay in view.
                        .heightIn(min = if (multi) 118.dp else 56.dp, max = if (multi) 132.dp else Dp.Unspecified)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.text.isEmpty() && d.inputHint != null) Text(d.inputHint, color = scheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                            inner()
                        }
                    }
                )
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                // A code is long: Paste fills the field from the clipboard in one tap.
                if (multi) {
                    val context = LocalContext.current
                    PreviewButton("Paste", ButtonKind.Text, Modifier.padding(top = 4.dp), icon = Symbols.ContentPaste) {
                        val clip = runCatching {
                            context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        }.getOrNull()
                        if (!clip.isNullOrBlank()) {
                            text = TextFieldValue(clip, TextRange(clip.length))
                            error = null
                        }
                    }
                }
                Text(
                    error ?: "", color = scheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp).heightIn(min = 18.dp).semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }
            d.picks?.forEachIndexed { k, look ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .heightIn(min = 56.dp)
                        .bouncyClickable(RoundedCornerShape(16.dp), onClickLabel = "Replace ${look.name}") { d.onPick?.invoke(k) }
                        .background(scheme.surfaceContainerHighest)
                        .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(132.dp, 44.dp).lookWindow(look, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        GalleryBar(look, phoneDark, scale = 0.38f)
                    }
                    Text(look.name, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                }
            }
        }
            if (d.stacked) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (a in d.actions) PreviewButton(a.label, a.kind, Modifier.fillMaxWidth()) {
                        if (d.onInput != null) d.onInput.invoke(text.text.trim())?.let { error = it } ?: onClose()
                        else if (a.onClick()) onClose()
                    }
                }
            } else {
                SheetButtons {
                    d.actions.forEachIndexed { i, a ->
                        // A code's Try It waits until something that could be a code is in the field.
                        val ready = d.onInput == null || d.inputHint == null || i != d.actions.lastIndex || text.text.contains("IS2.", ignoreCase = true)
                        PreviewButton(a.label, a.kind, enabled = ready) {
                            // The last action of an input dialog is its confirm: it checks the input first.
                            if (d.onInput != null && i == d.actions.lastIndex) d.onInput.invoke(text.text.trim())?.let { error = it } ?: onClose()
                            else if (a.onClick()) onClose()
                        }
                    }
                }
            }
      }
    }
}

/** How Looks Work: three plain lines in tiles, the backdrop choice, and the ways back. */
@Composable
private fun ColumnScope.InfoSheet(info: Pop.Info, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var backdrop by remember(info) { mutableStateOf(info.backdrop) }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(scheme.tertiary, ScallopShape), contentAlignment = Alignment.Center) {
                Icon(Symbols.Info, null, tint = scheme.onTertiary, modifier = Modifier.size(24.dp))
            }
            Column {
                SheetTitle("How Looks Work", Modifier.padding(bottom = 0.dp))
                SheetText("The short version.")
            }
        }
        // The three lines, each in a tile of its own, stacked as one group.
        val lines = listOf(
            Triple(Symbols.Checkroom, "A look is an outfit for your search bar.", "Its shape, its colors, and a wallpaper to match."),
            Triple(Symbols.SwipeVertical, "Turn the fan to browse.", "Swipe it, or push a swatch up or down."),
            Triple(Symbols.TouchApp, "Tap the one standing up to try it on.", "Nothing changes until you tap Apply.")
        )
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.forEachIndexed { i, (icon, title, text) ->
                val top = if (i == 0) 20.dp else 6.dp
                val bottom = if (i == lines.lastIndex) 20.dp else 6.dp
                InfoLine(icon, title, text, RoundedCornerShape(top, top, bottom, bottom))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Backdrop", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
                Text("What sits behind each look.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            // A connected button group: one choice of three.
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Backdrop.entries.forEachIndexed { i, choice ->
                    val on = choice == backdrop
                    val outer = 20.dp
                    val inner by animateDpAsState(if (on) 20.dp else 6.dp, Springs.morph(), label = "backdrop")
                    val shape = RoundedCornerShape(
                        topStart = if (i == 0) outer else inner, bottomStart = if (i == 0) outer else inner,
                        topEnd = if (i == Backdrop.entries.lastIndex) outer else inner, bottomEnd = if (i == Backdrop.entries.lastIndex) outer else inner
                    )
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .bouncyClickable(shape, role = Role.RadioButton) { backdrop = choice; info.onBackdrop(choice) }
                            .background(if (on) scheme.secondaryContainer else scheme.surfaceContainerHighest)
                            .semantics { selected = on; contentDescription = choice.label }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            choice.short, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            color = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    SheetButtons {
        info.bringBack?.let { PreviewButton("Bring Back Your Wallpaper", ButtonKind.Text, icon = Symbols.Wallpaper) { onClose(); it() } }
        info.putBack?.let { PreviewButton("Put Back Your Last Look", ButtonKind.Tonal, icon = Symbols.Undo) { onClose(); it() } }
        PreviewButton("Got It", ButtonKind.Filled, onClick = onClose)
    }
}

/** The sheet's badge: Material's nine-sided cookie. */
private val ScallopShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val polygon = MaterialMorphShapes.byName.getValue("Cookie9Sided")
        val path = polygon.toPath()
        val b = android.graphics.RectF().also { path.computeBounds(it, true) }
        val m = android.graphics.Matrix().apply {
            setRectToRect(b, android.graphics.RectF(0f, 0f, size.width, size.height), android.graphics.Matrix.ScaleToFit.FILL)
        }
        path.transform(m)
        return Outline.Generic(path.asComposePath())
    }
}

@Composable
private fun InfoLine(icon: ImageVector, title: String, text: String, shape: Shape) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh, shape).padding(14.dp).semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.padding(top = 1.dp).size(22.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/** Share a look as a code, through Android's share sheet as plain text (no file or storage access needed). */
@Composable
private fun ColumnScope.ShareSheet(look: Look, phoneDark: Boolean, onClose: () -> Unit, snack: (Snack) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val code = remember(look) { LookCode.encode(look) }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        SheetTitle(look.name)
        SheetText("Share this look as a code.", Modifier.padding(bottom = 12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barScale = ((maxWidth.value - 16f) / BAR_WIDTH.value).coerceAtMost(0.86f)
            Box(Modifier.fillMaxWidth().height(84.dp).lookWindow(look, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                GalleryBar(look, phoneDark, scale = barScale)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            code,
            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex, fontSize = 11.5.sp, lineHeight = 17.sp, color = scheme.onSurface,
            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHighest)
                .semantics { contentDescription = "Look code" }.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)
        )
        SheetText(
            "The code brings the search bar and the search page. Your own buttons, icon pack and Doodles stay as each person has them.",
            Modifier.padding(top = 10.dp)
        )
    }
    SheetButtons {
        PreviewButton("Copy Code", ButtonKind.Tonal, icon = Symbols.ContentCopy) {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Look code", code))
            onClose()
            snack(Snack("Code copied."))
        }
        PreviewButton("Share", ButtonKind.Filled, icon = Symbols.IosShare) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code)
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            onClose()
        }
    }
}

/**
 * Set a look's wallpaper on the phone. This is the one thing in Gallery that changes the phone itself, so the sheet
 * says what will happen and whether it can be undone, and nothing is set until its button is tapped.
 */
@Composable
private fun ColumnScope.WallpaperSheet(sheet: Pop.Wallpaper, phoneDark: Boolean, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val look = sheet.look
    var where by remember { mutableStateOf(PhoneWallpaper.Where.Both) }
    // What it says scrolls if it must (the largest text); its two buttons stay in reach below.
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        SheetTitle("Set Wallpaper")
        SheetText("This replaces the wallpaper on your phone. Your phone's colors may change to match it.", Modifier.padding(bottom = 12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barScale = ((maxWidth.value - 16f) / BAR_WIDTH.value).coerceAtMost(0.86f)
            Box(Modifier.fillMaxWidth().height(96.dp).lookWindow(look, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                GalleryBar(look, phoneDark, scale = barScale)
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).semantics { contentDescription = "Where" }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (choice in PhoneWallpaper.Where.entries) {
                val on = choice == where
                val corner by animateDpAsState(if (on) 28.dp else 12.dp, Springs.morph(), label = "where")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .bouncyClickable(RoundedCornerShape(corner), role = Role.RadioButton) { where = choice }
                        .background(if (on) scheme.secondaryContainer else scheme.surfaceContainerHighest)
                        .semantics { selected = on }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (on) Symbols.RadioButtonChecked else Symbols.RadioButtonUnchecked, null,
                        tint = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(22.dp)
                    )
                    Text(choice.label, style = MaterialTheme.typography.bodyLarge, color = if (on) scheme.onSecondaryContainer else scheme.onSurface)
                }
            }
        }
        Text(
            if (sheet.canUndo) "You can undo this right after, or later from Gallery's menu."
            else "This can't be undone from here. To go back, choose your wallpaper again in your phone's wallpaper settings.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)
        )
    }
    SheetButtons {
        PreviewButton("Cancel", ButtonKind.Text, onClick = onClose)
        PreviewButton(if (sheet.busy) "Setting…" else "Set Wallpaper", ButtonKind.Filled) {
            if (!sheet.busy) {
                sheet.busy = true
                sheet.onSet(where)
            }
        }
    }
}
