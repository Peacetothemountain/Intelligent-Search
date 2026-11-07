package com.pixel.intelligentsearch.core.ui.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.pixel.intelligentsearch.core.ui.icons.icon

// The `Icons.Rounded` icons the app uses, as `material-icons-extended` draws them; see [icon].

val Icons.Rounded.Pause: ImageVector
    get() = _pause ?: icon("Rounded.Pause", "M8,19c1.1,0,2-.9,2-2L10,7c0-1.1-.9-2-2-2s-2,.9-2,2v10c0,1.1.9,2,2,2zM14,7v10c0,1.1.9,2,2,2s2-.9,2-2L18,7c0-1.1-.9-2-2-2s-2,.9-2,2z").also { _pause = it }

private var _pause: ImageVector? = null

val Icons.Rounded.PlayArrow: ImageVector
    get() = _playArrow ?: icon("Rounded.PlayArrow", "M8,6.82v10.36c0,.79.87,1.27,1.54.84l8.14-5.18c.62-.39.62-1.29,0-1.69L9.54,5.98C8.87,5.55,8,6.03,8,6.82z").also { _playArrow = it }

private var _playArrow: ImageVector? = null

val Icons.Rounded.SkipNext: ImageVector
    get() = _skipNext ?: icon("Rounded.SkipNext", "M7.58,16.89l5.77-4.07c.56-.4.56-1.24,0-1.63L7.58,7.11C6.91,6.65,6,7.12,6,7.93v8.14c0,.81.91,1.28,1.58.82zM16,7v10c0,.55.45,1,1,1s1-.45,1-1V7c0-.55-.45-1-1-1s-1,.45-1,1z").also { _skipNext = it }

private var _skipNext: ImageVector? = null

val Icons.Rounded.SkipPrevious: ImageVector
    get() = _skipPrevious ?: icon("Rounded.SkipPrevious", "M7,6c.55,0,1,.45,1,1v10c0,.55-.45,1-1,1s-1-.45-1-1L6,7c0-.55.45-1,1-1zM10.66,12.82l5.77,4.07c.66.47,1.58-.01,1.58-.82L18.01,7.93c0-.81-.91-1.28-1.58-.82l-5.77,4.07c-.57.4-.57,1.24,0,1.64z").also { _skipPrevious = it }

private var _skipPrevious: ImageVector? = null
