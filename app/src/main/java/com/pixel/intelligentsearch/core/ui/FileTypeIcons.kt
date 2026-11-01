package com.pixel.intelligentsearch.core.ui

import com.pixel.intelligentsearch.core.ui.symbols.Symbols
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.pixel.intelligentsearch.core.data.FileTypeGroup

/** The outlined icon for a file type, in File Search settings and on the results' filter chip. */
fun fileTypeIcon(group: FileTypeGroup): ImageVector = when (group) {
    FileTypeGroup.PDF -> Symbols.Outlined.PictureAsPdf
    FileTypeGroup.IMAGES -> Symbols.Outlined.Image
    FileTypeGroup.VIDEO -> Symbols.Outlined.VideoFile
    FileTypeGroup.AUDIO -> Symbols.Outlined.AudioFile
    FileTypeGroup.DOCUMENTS -> Symbols.Outlined.Description
    FileTypeGroup.SPREADSHEETS -> Symbols.Outlined.TableChart
    FileTypeGroup.PRESENTATIONS -> Symbols.Outlined.Slideshow
    FileTypeGroup.ARCHIVES -> Symbols.Outlined.FolderZip
    FileTypeGroup.APKS -> Symbols.Outlined.Android
    FileTypeGroup.OTHER -> Symbols.AutoMirrored.Outlined.InsertDriveFile
}
