package com.pixel.intelligentsearch.core.ui.icons.automirrored.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.pixel.intelligentsearch.core.ui.icons.icon

// The `Icons.AutoMirrored.Outlined` icons the app uses, as `material-icons-extended` draws them; see [icon].

val Icons.AutoMirrored.Outlined.ArrowForward: ImageVector
    get() = _arrowForward ?: icon("AutoMirrored.Outlined.ArrowForward", "M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8-8l-8-8z", autoMirror = true).also { _arrowForward = it }

private var _arrowForward: ImageVector? = null

val Icons.AutoMirrored.Outlined.InsertDriveFile: ImageVector
    get() = _insertDriveFile ?: icon("AutoMirrored.Outlined.InsertDriveFile", "M14,2H6c-1.1,0-1.99.9-1.99,2L4,20c0,1.1.89,2,1.99,2H18c1.1,0,2-.9,2-2V8l-6-6zM6,20V4h7v5h5v11H6z", autoMirror = true).also { _insertDriveFile = it }

private var _insertDriveFile: ImageVector? = null

val Icons.AutoMirrored.Outlined.Label: ImageVector
    get() = _label ?: icon("AutoMirrored.Outlined.Label", "M17.63,5.84C17.27,5.33,16.67,5,16,5L5,5.01C3.9,5.01,3,5.9,3,7v10c0,1.1.9,1.99,2,1.99L16,19c.67,0,1.27-.33,1.63-.84L22,12l-4.37-6.16zM16,17H5V7h11l3.55,5L16,17z", autoMirror = true).also { _label = it }

private var _label: ImageVector? = null

val Icons.AutoMirrored.Outlined.ManageSearch: ImageVector
    get() = _manageSearch ?: icon("AutoMirrored.Outlined.ManageSearch", "M7,9H2V7h5V9zM7,12H2v2h5V12zM20.59,19l-3.83-3.83C15.96,15.69,15.02,16,14,16c-2.76,0-5-2.24-5-5s2.24-5,5-5s5,2.24,5,5c0,1.02-.31,1.96-.83,2.75L22,17.59L20.59,19zM17,11c0-1.65-1.35-3-3-3s-3,1.35-3,3s1.35,3,3,3S17,12.65,17,11zM2,19h10v-2H2V19z", autoMirror = true).also { _manageSearch = it }

private var _manageSearch: ImageVector? = null

val Icons.AutoMirrored.Outlined.VolumeOff: ImageVector
    get() = _volumeOff ?: icon("AutoMirrored.Outlined.VolumeOff", "M4.34,2.93L2.93,4.34L7.29,8.7L7,9L3,9v6h4l5,5v-6.59l4.18,4.18c-.65.49-1.38.88-2.18,1.11v2.06c1.34-.3,2.57-.92,3.61-1.75l2.05,2.05l1.41-1.41L4.34,2.93zM10,15.17L7.83,13L5,13v-2h2.83l.88-.88L10,11.41v3.76zM19,12c0,.82-.15,1.61-.41,2.34l1.53,1.53c.56-1.17.88-2.48.88-3.87c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86,5,3.54,5,6.71zM12,4l-1.88,1.88L12,7.76zM16.5,12c0-1.77-1.02-3.29-2.5-4.03v1.79l2.48,2.48c.01-.08.02-.16.02-.24z", autoMirror = true).also { _volumeOff = it }

private var _volumeOff: ImageVector? = null

val Icons.AutoMirrored.Outlined.VolumeUp: ImageVector
    get() = _volumeUp ?: icon("AutoMirrored.Outlined.VolumeUp", "M3,9v6h4l5,5L12,4L7,9L3,9zM10,8.83v6.34L7.83,13L5,13v-2h2.83L10,8.83zM16.5,12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73,2.5-2.25,2.5-4.02zM14,3.23v2.06c2.89.86,5,3.54,5,6.71s-2.11,5.85-5,6.71v2.06c4.01-.91,7-4.49,7-8.77c0-4.28-2.99-7.86-7-8.77z", autoMirror = true).also { _volumeUp = it }

private var _volumeUp: ImageVector? = null
