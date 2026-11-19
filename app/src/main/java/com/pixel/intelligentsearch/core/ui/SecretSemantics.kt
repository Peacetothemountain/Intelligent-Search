package com.pixel.intelligentsearch.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString

/**
 * For a secure text field holding [length] characters while they are hidden: accessibility services are told one
 * bullet per character, as Android's own password field tells them, never the secret. The secure field draws bullets
 * but otherwise hands them the typed text (an API key, a backup passphrase). Applied outside the field's own
 * semantics, so this text is the one they read; [hidden] false (the user chose Show) leaves the field as it is.
 */
internal fun Modifier.secretForAccessibility(length: Int, hidden: Boolean = true): Modifier =
    if (!hidden) this else semantics { editableText = AnnotatedString(SECRET_BULLET.repeat(length)) }

private const val SECRET_BULLET = "•"
