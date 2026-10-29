package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.AnswerTone
import com.pixel.intelligentsearch.core.ui.AnswerHeader
import com.pixel.intelligentsearch.core.ui.AnswerChips
import com.pixel.intelligentsearch.core.ui.AnswerChip
import com.pixel.intelligentsearch.core.ui.AnswerCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.ai.AiAnswerState
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.AnimatedCopyChip
import com.pixel.intelligentsearch.feature.settings.bouncyClickable

/**
 * A suggested action at the top of the results ("Start 15 min Timer", "Call Mom", "Ask Claude"): its icon on a
 * primary circle, a bold title and one line of detail on the primary container pill, and an arrow, pressing like
 * every result row.
 */
@Composable
internal fun DirectActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    /** Gestures on the pill itself, under the press (the suggestions' swipe to dismiss). */
    gestures: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = DirectActionRowShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), shape)
            .clip(shape)
            .then(gestures)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansFlex
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontFamily = GoogleSansFlex,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The suggested actions' pill, which the keyboard's highlight ring follows too. */
internal val DirectActionRowShape = RoundedCornerShape(32.dp)

/**
 * "Ask Claude": the row that sends [question] to [provider], and only when it is tapped (or chosen with the keyboard).
 * [modifier] goes on the pill itself, under the press (the keyboard's highlight ring).
 */
@Composable
internal fun AskAiRow(provider: AiProvider, question: String, modifier: Modifier = Modifier, onAsk: () -> Unit) {
    DirectActionRow(
        title = "Ask ${provider.displayName}",
        subtitle = question,
        icon = Icons.Filled.AutoAwesome,
        gestures = modifier,
        onClick = onAsk
    )
}

/**
 * The AI answer in the same card as the other instant answers ([AnswerCard], [AnswerHeader], [AnswerChip]): the
 * provider and model over the question, the answer as it streams (with **bold**, `code` and bullet points drawn),
 * then Copy and "Continue in" the assistant's app or site. A failure says what went wrong in plain words, with the way
 * forward beside it: the settings page that fixes it ([onOpenSettings] gets its route key), or Try Again ([onRetry]).
 */
@Composable
internal fun AiAnswerCard(
    state: AiAnswerState,
    model: String,
    onOpenSettings: (String) -> Unit = {},
    onRetry: (() -> Unit)? = null,
    onContinue: () -> Unit
) {
    AnswerCard(Modifier) {
        AnswerHeader(
            icon = Icons.Filled.AutoAwesome,
            label = state.provider.displayName.uppercase(),
            title = state.question,
            subtitle = model,
            tone = AnswerTone.Primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        when (state) {
            is AiAnswerState.Asking -> Text(
                text = "Asking ${state.provider.displayName}…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            is AiAnswerState.Answer -> {
                val colors = MaterialTheme.colorScheme
                val styled = remember(state.text, colors) { markdownLite(state.text, codeBackground = colors.surfaceContainerHighest) }
                Text(
                    text = styled,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { if (state.done) liveRegion = LiveRegionMode.Polite }
                )
                if (state.truncated) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "The answer was cut short. Continue in ${state.provider.displayName} for more.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is AiAnswerState.Failed -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = state.problem.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        AnswerChips {
            if (state is AiAnswerState.Answer && state.done) {
                AnimatedCopyChip(textToCopy = state.text, clipLabel = "${state.provider.displayName} Answer")
            }
            if (state is AiAnswerState.Failed) {
                val page = state.problem.settingsPage
                if (page != null) {
                    AnswerChip(settingsPageName(page), Icons.Outlined.Settings, emphasized = true) { onOpenSettings(page) }
                } else if (state.problem.retryable && onRetry != null) {
                    AnswerChip("Try Again", Icons.Filled.Refresh, emphasized = true, onClick = onRetry)
                }
            }
            val failedWithFix = state is AiAnswerState.Failed &&
                (state.problem.settingsPage != null || (state.problem.retryable && onRetry != null))
            AnswerChip("Continue in ${state.provider.displayName}", Icons.AutoMirrored.Filled.OpenInNew, emphasized = !failedWithFix, onClick = onContinue)
        }
    }
}

/** How far a bullet's wrapped lines hang in: the width of "•  " in the answer's text, so they line up under its words. */
private val BULLET_HANG = 0.94.em

/**
 * The little Markdown answers use, drawn rather than shown as symbols: **bold**, `code`, "#" headings (bold) and
 * "-" or "*" bullet points (•). Anything else is plain text.
 */
internal fun markdownLite(text: String, codeBackground: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    val lines = text.replace("\r\n", "\n").split('\n')
    lines.forEachIndexed { index, rawLine ->
        var line = rawLine
        var heading = false
        var bullet = false
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> {
                heading = true
                line = trimmed.trimStart('#').trimStart()
            }
            isBullet(trimmed) -> {
                bullet = true
                line = "•  " + trimmed.substring(2)
            }
        }
        // A bullet's wrapped lines hang under its text, not under the bullet.
        if (bullet) pushStyle(ParagraphStyle(textIndent = TextIndent(restLine = BULLET_HANG)))
        if (heading) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        var i = 0
        var bold = false
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    if (bold) pop() else pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    bold = !bold
                    i += 2
                }
                line[i] == '`' && line.indexOf('`', i + 1) > i -> {
                    val end = line.indexOf('`', i + 1)
                    pushStyle(SpanStyle(background = codeBackground))
                    append(line.substring(i + 1, end))
                    pop()
                    i = end + 1
                }
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
        if (bold) pop()
        if (heading) pop()
        if (bullet) pop()
        // A bullet is a paragraph of its own, which already starts and ends a line: a line break beside one would
        // add a blank line.
        if (index < lines.lastIndex && !bullet && !isBullet(lines[index + 1])) append('\n')
    }
}

private fun isBullet(line: String): Boolean = line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") }

/** The name a fix-it chip gives the settings page [page] (a route key). */
private fun settingsPageName(page: String): String = when (page) {
    "ai_answers" -> "Open AI Answers"
    "search_sources" -> "Open Search Sources"
    "web_search" -> "Open Web Search"
    else -> "Open Settings"
}
