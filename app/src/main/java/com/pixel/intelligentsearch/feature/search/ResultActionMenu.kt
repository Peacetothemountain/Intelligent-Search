package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.MenuStyle
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import com.pixel.intelligentsearch.core.search.AndroidSettingsSearch
import com.pixel.intelligentsearch.core.search.ResultKeys
import com.pixel.intelligentsearch.core.search.ResultShortcuts
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable

/** The TalkBack action that opens a result's long-press menu. */
internal const val MORE_OPTIONS_LABEL = "More options"

/** Marks the long-press menu's panel, for tests. */
internal const val RESULT_MENU_TAG = "result_action_menu"

/** What a long-press menu is for: a result, or a past search. */
internal sealed interface ResultMenuTarget {
    /** The [ResultKeys] name pins are stored under; null when it can't be pinned. */
    val key: String?
    val title: String

    data class App(val app: AppItem) : ResultMenuTarget {
        override val key: String = ResultKeys.app(app)
        override val title: String = app.name
    }

    data class Contact(val contact: ContactItem) : ResultMenuTarget {
        override val key: String? = ResultKeys.contact(contact)
        override val title: String = contact.name.ifBlank { contact.phoneNumber }
    }

    data class File(val file: FileItem) : ResultMenuTarget {
        override val key: String = ResultKeys.file(file)
        override val title: String = file.name
    }

    data class Setting(val setting: AndroidSettingResult) : ResultMenuTarget {
        override val key: String? = ResultKeys.setting(setting)
        override val title: String = setting.title
    }

    data class History(val query: String) : ResultMenuTarget {
        override val key: String? = null
        override val title: String = query
    }
}

/** One thing a long-press menu can do, with its Title Case label and outlined icon. */
internal enum class ResultCommand(val label: String, val icon: ImageVector) {
    PIN("Pin to Top", Icons.Outlined.PushPin),
    UNPIN("Unpin", Icons.Filled.PushPin),
    SPLIT_SCREEN("Open in Split Screen", Icons.Outlined.VerticalSplit),
    APP_INFO("App Info", Icons.Outlined.Info),
    HIDE("Hide from Results", Icons.Outlined.VisibilityOff),
    UNINSTALL("Uninstall", Icons.Outlined.Delete),
    REMOVE_FROM_HISTORY("Remove from History", Icons.Outlined.HistoryToggleOff)
}

/**
 * The menu's commands for [target], in groups shown with a divider between them: what the user can set on the result
 * (pin), then how to open it, then what to do with the app. Contacts add their Call, Message,
 * Email and chat actions after these ([ResultMenuBox]). [splitScreen] and [canUninstall] say whether the device and
 * the app allow those.
 */
internal fun resultMenuCommands(
    target: ResultMenuTarget,
    shortcuts: ResultShortcuts,
    splitScreen: Boolean = false,
    canUninstall: Boolean = false
): List<List<ResultCommand>> {
    if (target is ResultMenuTarget.History) return listOf(listOf(ResultCommand.REMOVE_FROM_HISTORY))
    val key = target.key
    val personal = if (key == null) emptyList() else buildList {
        add(if (shortcuts.isPinned(key)) ResultCommand.UNPIN else ResultCommand.PIN)
    }
    if (target !is ResultMenuTarget.App) return listOf(personal).filter { it.isNotEmpty() }
    val open = if (splitScreen) listOf(ResultCommand.SPLIT_SCREEN) else emptyList()
    val manage = buildList {
        add(ResultCommand.APP_INFO)
        add(ResultCommand.HIDE)
        if (canUninstall) add(ResultCommand.UNINSTALL)
    }
    return listOf(personal, open, manage).filter { it.isNotEmpty() }
}

/**
 * Whether Open in Split Screen is offered: the overlay already shares the screen, or the screen is large enough
 * (600dp on its shorter side) for Android to split it on request. Only for apps in this profile: another profile's
 * app opens through LauncherApps, which can't ask for the adjacent window.
 */
internal fun splitScreenOffered(inMultiWindow: Boolean, smallestWidthDp: Int, otherProfile: Boolean): Boolean =
    !otherProfile && (inMultiWindow || smallestWidthDp >= 600)

/** [app]'s launcher activity, asked to open beside this window ([Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT]). */
internal fun splitScreenIntent(context: Context, app: AppItem): Intent? {
    val intent = app.activityName?.let {
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(app.packageName, it))
    } ?: context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return null
    return intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun AppItem.isOtherProfile(): Boolean = userHandle != null && userHandle != android.os.Process.myUserHandle()

/**
 * Whether the user can uninstall [app] from its App Info page: not a system app (an updated one only drops its
 * updates there). Asked through LauncherApps so a work profile's app is looked up in its own profile.
 */
internal fun canUninstall(context: Context, app: AppItem): Boolean = try {
    val user = app.userHandle ?: android.os.Process.myUserHandle()
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    val info = launcherApps?.getApplicationInfo(app.packageName, 0, user)
    info != null && (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
} catch (e: Exception) {
    false
}

/**
 * Opens [app]'s App Info page, in its own profile. Uninstall goes here too: uninstalling straight from the menu would
 * need the REQUEST_DELETE_PACKAGES permission, while App Info's own Uninstall button needs none.
 */
internal fun openAppInfo(context: Context, app: AppItem) {
    val opened = if (app.isOtherProfile()) {
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            val activity = app.activityName
            if (launcherApps != null && activity != null) {
                launcherApps.startAppDetailsActivity(ComponentName(app.packageName, activity), app.userHandle, null, null)
                true
            } else false
        } catch (e: RuntimeException) {
            false
        }
    } else {
        launchSafeIntent(context, AndroidSettingsSearch.appDetailsIntent(app.packageName))
    }
    if (!opened) Toast.makeText(context, "Couldn't open App Info for ${app.name}.", Toast.LENGTH_SHORT).show()
}

/** Exposes [onOpen] (a result's long-press menu) to TalkBack as "More options"; nothing when there is no menu. */
internal fun Modifier.moreOptions(onOpen: (() -> Unit)?): Modifier =
    if (onOpen == null) this else semantics {
        customActions = listOf(CustomAccessibilityAction(MORE_OPTIONS_LABEL) { onOpen(); true })
    }

/**
 * The menu's contents: the result's name as a heading, then [groups] of commands and the contact's [contactActions]
 * (Call, Message, Email, chat apps), a hairline between groups. Rows press like the overlay's result rows.
 */
@Composable
internal fun ResultActionMenuItems(
    title: String,
    groups: List<List<ResultCommand>>,
    contactActions: List<ContactAction> = emptyList(),
    activeIconPack: String = "",
    onCommand: (ResultCommand) -> Unit,
    onContactAction: (ContactAction) -> Unit = {}
) {
    Column(modifier = Modifier.widthIn(min = 200.dp, max = 320.dp).testTag(RESULT_MENU_TAG)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .semantics { heading() }
        )
        groups.forEach { group ->
            MenuDivider()
            group.forEach { command ->
                MenuRow(label = command.label, onClick = { onCommand(command) }) {
                    Icon(command.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        }
        if (contactActions.isNotEmpty()) {
            MenuDivider()
            contactActions.forEach { action ->
                MenuRow(label = action.contentDescription, onClick = { onContactAction(action) }) {
                    ContactActionIcon(action, activeIconPack, MaterialTheme.colorScheme.onSurfaceVariant, 24.dp)
                }
            }
        }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .expressiveRowClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A result row with its long-press menu, which opens anchored under the row. [content] gets the menu's opener, null
 * when [target] has none; the row passes it to its `bouncyClickable` / `expressiveRowClickable` as onLongClick (so
 * the press and its one haptic are the row's own) and to [moreOptions] for TalkBack.
 */
@Composable
internal fun ResultMenuBox(
    s: OverlayScope,
    target: ResultMenuTarget?,
    modifier: Modifier = Modifier,
    content: @Composable (openMenu: (() -> Unit)?) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        content(if (target == null) null else ({ menuOpen = true }))
        if (target != null && menuOpen) {
            ResultActionMenu(s, target, onDismiss = { menuOpen = false })
        }
    }
}

@Composable
private fun ResultActionMenu(s: OverlayScope, target: ResultMenuTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val app = (target as? ResultMenuTarget.App)?.app
    val splitScreen = app != null && splitScreenOffered(
        inMultiWindow = context.findActivity()?.isInMultiWindowMode == true,
        smallestWidthDp = configuration.smallestScreenWidthDp,
        otherProfile = app.isOtherProfile()
    )
    val uninstallable = remember(app) { app != null && canUninstall(context, app) }
    val groups = resultMenuCommands(target, s.uiState.shortcuts, splitScreen, uninstallable)
    val contactActions = (target as? ResultMenuTarget.Contact)?.let { ContactActions.forContact(it.contact, s.contactActionTargets) }.orEmpty()
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        offset = DpOffset(16.dp, 0.dp),
        shape = MenuStyle.shape,
        containerColor = MenuStyle.containerColor(),
        border = MenuStyle.border()
    ) {
        ResultActionMenuItems(
            title = target.title,
            groups = groups,
            contactActions = contactActions,
            activeIconPack = s.settingsState.activeIconPack,
            onCommand = { command ->
                onDismiss()
                runCommand(context, s, target, command)
            },
            onContactAction = { action ->
                onDismiss()
                s.ui.hasStartedTyping = false
                launchSafeIntent(context, Intent(action.intent))
            }
        )
    }
}

private fun runCommand(context: Context, s: OverlayScope, target: ResultMenuTarget, command: ResultCommand) {
    val key = target.key
    val app = (target as? ResultMenuTarget.App)?.app
    when (command) {
        ResultCommand.PIN, ResultCommand.UNPIN -> if (key != null) s.viewModel.setPinned(key, target.title, command == ResultCommand.PIN)
        ResultCommand.APP_INFO, ResultCommand.UNINSTALL -> if (app != null) {
            s.ui.hasStartedTyping = false
            openAppInfo(context, app)
        }
        ResultCommand.HIDE -> if (app != null) s.viewModel.hideApp(app.packageName)
        ResultCommand.SPLIT_SCREEN -> if (app != null) {
            val intent = splitScreenIntent(context, app)
            if (intent == null || !launchSafeIntent(context, intent)) {
                Toast.makeText(context, "Couldn't open ${app.name} in split screen.", Toast.LENGTH_SHORT).show()
            } else {
                s.ui.hasStartedTyping = false
            }
        }
        ResultCommand.REMOVE_FROM_HISTORY -> (target as? ResultMenuTarget.History)?.let { s.viewModel.removeSearchHistory(it.query) }
    }
}
