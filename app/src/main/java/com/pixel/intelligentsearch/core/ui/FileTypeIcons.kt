package com.pixel.intelligentsearch.core.ui

import com.pixel.intelligentsearch.core.ui.symbols.Symbols
import androidx.compose.material.icons.Icons
import com.pixel.intelligentsearch.core.ui.icons.automirrored.outlined.InsertDriveFile
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
