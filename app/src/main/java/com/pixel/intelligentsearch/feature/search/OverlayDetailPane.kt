package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.VerticalSplit
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.SavedLink
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import com.pixel.intelligentsearch.core.ui.AppIconResult
import com.pixel.intelligentsearch.core.ui.MathResultOneBox
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import com.pixel.intelligentsearch.core.util.findActivity

/** The test tag of the detail pane beside the results on large screens. */
internal const val DETAIL_PANE_TAG = "overlay_detail_pane"

/**
 * The window widths at which the overlay shows the results beside a detail pane: Material's expanded width class
 * (840 dp and wider: an unfolded Pixel Fold or a tablet in landscape) with at least a medium height, so a phone on
 * its side, wide but short with the keyboard up, keeps its one column. Narrower windows (an unfolded foldable held
 * upright, a tablet in portrait, either half of a split screen) keep the single pane.
 */
internal fun useListDetailLayout(windowWidthDp: Float, windowHeightDp: Float): Boolean =
    windowWidthDp >= EXPANDED_WIDTH_DP && windowHeightDp >= MEDIUM_HEIGHT_DP

private const val EXPANDED_WIDTH_DP = 840f
private const val MEDIUM_HEIGHT_DP = 480f

/**
 * The result the detail pane shows: the one the keyboard highlights, else (with something typed) the top result, as
 * Enter would open it.
 */
internal fun detailStop(rows: List<List<NavStop>>, highlightedKey: String?, query: String): NavStop? =
    rows.flatten().firstOrNull { it.key == highlightedKey }
        ?: rows.firstOrNull()?.firstOrNull()?.takeIf { query.isNotEmpty() }

/**
 * The detail pane beside the results on an expanded screen, for the highlighted or top result ([detailStop]): an app
 * with its actions, a contact's card with photo and actions, a file's preview with Open and Share, a settings page, a
 * web suggestion with its picture, or an answer's card at full size. With nothing typed it says what will show here.
 * It reads the highlight itself, so moving it recomposes only this pane and the rows.
 */
@Composable
internal fun OverlayDetailPane(s: OverlayScope, modifier: Modifier = Modifier) {
    val stop = detailStop(s.navRows, s.ui.highlightedKey, s.uiState.query)
    val context = LocalContext.current
    val scroll = rememberScrollState()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(DETAIL_PANE_TAG)
            .semantics { paneTitle = "Result Details" }
    ) {
        // Scrolls when a large font makes it taller than the pane; otherwise sits by the search bar's edge.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .heightIn(min = maxHeight)
                .padding(8.dp),
            verticalArrangement = if (s.settingsState.bottomSearch) Arrangement.Bottom else Arrangement.Top
        ) {
            when {
                stop == null -> DetailEmpty()
                // The answer's own card, across the whole pane.
                stop.target is NavTarget.Answer -> when (stop.listKey) {
                    ResultListKeys.MATH -> MathResultOneBox(
                        expression = s.uiState.query,
                        result = s.uiState.mathResult.orEmpty(),
                        onOpenCalculator = { openNavTarget(context, s, stop.target) }
                    )
                    ResultListKeys.LIVE_ANSWER -> s.uiState.liveAnswer?.let { OverlayLiveAnswer(s, it) }
                    ResultListKeys.INSTANT_ANSWER -> s.uiState.instantAnswer?.let { OverlayInstantAnswer(s, it) }
                    ResultListKeys.AI_ANSWER -> s.uiState.aiAnswer?.let { answer ->
                        val aiModel by rememberSetting(SettingsManager.AI_MODEL)
                        AiAnswerCard(answer, model = answer.provider.model(aiModel)) { openNavTarget(context, s, stop.target) }
                    }
                }
                else -> DetailCard(s, stop.target)
            }
        }
    }
}

/** A result's details in the pane's matte card. */
@Composable
private fun DetailCard(s: OverlayScope, target: NavTarget) {
    MatteShell(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            // Announced as the highlight moves, so a TalkBack user with a keyboard hears what Enter will open.
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = ExpressiveCornerTokens.ShapeExtraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DetailContent(s, target)
        }
    }
}

/** What the pane says before anything is typed. */
@Composable
private fun DetailEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ManageSearch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Type to See the Top Result Here. Use the Arrow Keys to Look Through the Others.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ColumnScope.DetailContent(s: OverlayScope, target: NavTarget) {
    val context = LocalContext.current
    val open = { openNavTarget(context, s, target) }
    when (target) {
        is NavTarget.Result -> when (val item = target.item) {
            is AppItem -> {
                val otherProfile = item.userHandle != null && item.userHandle != android.os.Process.myUserHandle()
                DetailAppIcon(item, s.settingsState.activeIconPack, otherProfile)
                DetailTitle(item.name, if (otherProfile) "Work App" else "Application")
                val configuration = LocalConfiguration.current
                val splitScreen = splitScreenOffered(isInMultiWindow(context), configuration.smallestScreenWidthDp, otherProfile)
                DetailActions {
                    DetailAction("Open", Icons.AutoMirrored.Outlined.ArrowForward, emphasized = true, onClick = open)
                    DetailAction("App Info", Icons.Outlined.Info) {
                        s.ui.hasStartedTyping = false
                        openAppInfo(context, item)
                    }
                    if (splitScreen) DetailAction("Open in Split Screen", Icons.Outlined.VerticalSplit) {
                        splitScreenIntent(context, item)?.let { launchSafeIntent(context, it) }
                    }
                }
            }
            is ContactItem -> {
                ContactAvatar(item, DETAIL_PICTURE_SIZE) { DetailSymbol(Icons.Outlined.Person) }
                DetailTitle(item.name.ifBlank { item.phoneNumber }, listOf(item.phoneNumber, item.email).filter { it.isNotBlank() }.joinToString("\n").ifBlank { null })
                val actions = remember(item, s.contactActionTargets, s.settingsState.contactActions) {
                    ContactActions.forContact(item, s.contactActionTargets, s.settingsState.contactActions)
                }
                DetailActions {
                    DetailAction("Open Contact", Icons.AutoMirrored.Outlined.ArrowForward, emphasized = true) {
                        s.ui.hasStartedTyping = false
                        launchSafeIntent(context, ContactActions.openIntent(item, directCall = false))
                    }
                }
                if (actions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ContactActionChips(actions, s.settingsState.activeIconPack) { s.ui.hasStartedTyping = false }
                }
            }
            is FileItem -> {
                if (s.settingsState.filesThumbnails) FileIconThumbnail(item.uri, item.mimeType, size = DETAIL_PICTURE_SIZE)
                else DetailSymbol(Icons.AutoMirrored.Outlined.InsertDriveFile)
                DetailTitle(item.name, fileLocationLabel(item))
                DetailActions {
                    DetailAction("Open", Icons.AutoMirrored.Outlined.ArrowForward, emphasized = true, onClick = open)
                    DetailAction("Share", Icons.Outlined.Share) {
                        s.ui.hasStartedTyping = false
                        launchSafeIntent(context, shareFileIntent(item))
                    }
                }
            }
            is SavedLink -> {
                SavedLinkIcon(item, rememberSavedLinkIcon(item), DETAIL_PICTURE_SIZE)
                DetailTitle(item.name, item.host.ifBlank { null })
                DetailActions { DetailAction("Open", Icons.AutoMirrored.Outlined.ArrowForward, emphasized = true, onClick = open) }
            }
            is AndroidSettingResult -> {
                val app = item.app
                if (app != null) DetailAppIcon(app, s.settingsState.activeIconPack, otherProfile = false)
                else DetailSymbol(androidSettingIcon(item.iconType))
                DetailTitle(item.title, item.subtitle.ifBlank { null })
                DetailActions { DetailAction("Open Settings", Icons.AutoMirrored.Outlined.ArrowForward, emphasized = true, onClick = open) }
            }
        }
        is NavTarget.WebQuery -> {
            val suggestion = target.suggestion
            val imageUrl = suggestion?.imageUrl?.takeIf { s.settingsState.filesThumbnails && !target.isRecent }
            val symbol = @Composable { DetailSymbol(Icons.Outlined.Search) }
            if (imageUrl != null) ResultThumbnail(ThumbnailSource.Web(imageUrl), DETAIL_PICTURE_SIZE, fallback = symbol) else symbol()
            DetailTitle(
                suggestion?.title?.takeIf { it.isNotBlank() } ?: target.query,
                suggestion?.subtitle?.takeIf { it.isNotBlank() && !target.isRecent } ?: if (target.isRecent) "Recent Search" else null
            )
            DetailActions {
                DetailAction("Search ${s.searchProviderName}", Icons.Outlined.Search, emphasized = true, onClick = open)
            }
        }
        is NavTarget.Correction -> {
            DetailSymbol(Icons.Outlined.Spellcheck)
            DetailTitle(target.corrected, "Did You Mean")
            DetailActions { DetailAction("Search for ${target.corrected}", Icons.Outlined.Search, emphasized = true, onClick = open) }
        }
        is NavTarget.Action -> {
            DetailSymbol(directActionIcon(target.action.iconType))
            DetailTitle(target.action.title, target.action.subtitle.ifBlank { null })
            DetailActions { DetailAction(target.action.title, Icons.AutoMirrored.Outlined.ArrowForward, emphasized = true, onClick = open) }
        }
        is NavTarget.AskAi -> {
            DetailSymbol(Icons.Outlined.AutoAwesome)
            DetailTitle(target.question, "Ask ${target.provider.displayName}")
            DetailActions { DetailAction("Ask ${target.provider.displayName}", Icons.AutoMirrored.Outlined.ArrowForward, emphasized = true, onClick = open) }
        }
        is NavTarget.RemoveFileTypeFilter -> {
            DetailSymbol(fileTypeIcon(target.group))
            DetailTitle("${target.group.label} Only", "Files Are Limited to This Type.")
            DetailActions { DetailAction("Show All Files", Icons.Outlined.FilterListOff, emphasized = true, onClick = open) }
        }
        is NavTarget.Answer -> Unit // drawn as the answer's own card by OverlayDetailPane
    }
}

/** The size of the pane's picture: an app icon, a contact photo, a file preview or a suggestion's image. */
private val DETAIL_PICTURE_SIZE = 96.dp

/** The result's name and what it is, centred under its picture. */
@Composable
private fun DetailTitle(title: String, subtitle: String?) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    if (subtitle != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** A result's symbol in a matte circle, where it has no picture of its own. */
@Composable
private fun DetailSymbol(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(DETAIL_PICTURE_SIZE)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
    }
}

/** An app's icon at the pane's size, themed as the results draw it; another profile's copy keeps its badged icon. */
@Composable
private fun DetailAppIcon(app: AppItem, iconPack: String, otherProfile: Boolean) {
    val context = LocalContext.current
    val themed = remember(app.packageName, iconPack, otherProfile) {
        mutableStateOf<AppIconResult?>(if (otherProfile) null else peekThemedAppIcon(app.packageName, iconPack))
    }
    LaunchedEffect(app.packageName, iconPack, otherProfile) {
        if (themed.value == null && !otherProfile) {
            themed.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                getThemedAppIcon(context, app.packageName, activePackOverride = iconPack)
            }
        }
    }
    val icon by themed
    val px = with(LocalDensity.current) { DETAIL_PICTURE_SIZE.roundToPx() }
    val current = icon
    if (current != null) {
        Image(
            bitmap = current.bitmap,
            contentDescription = null,
            modifier = Modifier.size(DETAIL_PICTURE_SIZE),
            colorFilter = if (current.isMonochrome) ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
        )
    } else {
        val fallback = remember(app.packageName, px) { runCatching { app.icon.toBitmap(px, px).asImageBitmap() }.getOrNull() }
        if (fallback != null) Image(bitmap = fallback, contentDescription = null, modifier = Modifier.size(DETAIL_PICTURE_SIZE))
        else DetailSymbol(Icons.AutoMirrored.Outlined.ArrowForward)
    }
}

/** The pane's actions, flowing onto more lines at large text sizes. */
@Composable
private fun DetailActions(content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(20.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** One action, styled as the answer cards' chips; [emphasized] for the one Enter does. */
@Composable
private fun DetailAction(label: String, icon: ImageVector, emphasized: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 6.dp)) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        shape = ExpressiveCornerTokens.ShapeLarge,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (emphasized) scheme.primaryContainer else scheme.surfaceContainerHighest,
            labelColor = if (emphasized) scheme.onPrimaryContainer else scheme.onSurface,
            leadingIconContentColor = if (emphasized) scheme.onPrimaryContainer else scheme.primary
        ),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f))
    )
}

/** Shares [file] with read access to its address, through the system's share sheet. */
internal fun shareFileIntent(file: FileItem): Intent {
    val uri = file.uri.toUri()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType.ifBlank { "*/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, file.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** Whether this window shares the screen with another app. */
private fun isInMultiWindow(context: Context): Boolean = context.findActivity()?.isInMultiWindowMode == true
