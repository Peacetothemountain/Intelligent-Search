package com.ngdesigns.intelligentsearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.ngdesigns.intelligentsearch.ui.theme.Mono
import com.ngdesigns.intelligentsearch.ui.theme.Theme

/** The search screen's frame: results grow upward from the input line at the bottom. */
@Composable
fun SearchShell(modifier: Modifier = Modifier) {
    val palette = Theme.palette
    val type = Theme.type
    val metrics = Theme.metrics
    val query = rememberTextFieldState()
    Column(
        modifier
            .fillMaxSize()
            .background(palette.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth())
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = metrics.gutter, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf("ALL", "APPS", "PEOPLE", "FILES", "WEB").forEachIndexed { i, scope ->
                Text(scope, type.label, if (i == 0) palette.text else palette.dim)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = metrics.gutter, end = metrics.gutter, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("›", type.query.copy(fontFamily = Mono), palette.accent)
            Spacer(Modifier.padding(start = 10.dp))
            Box(Modifier.weight(1f)) {
                if (query.text.isEmpty()) Text("Search or type a command", type.query, palette.dim, maxLines = 1)
                BasicTextField(
                    state = query,
                    textStyle = type.query.copy(color = palette.text),
                    cursorBrush = SolidColor(palette.accent),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.padding(start = 10.dp))
            Box(
                Modifier
                    .border(metrics.hairline, palette.line, RoundedCornerShape(metrics.corner))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            ) { Text("GOOGLE", type.key, palette.text) }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(Theme.metrics.hairline).background(Theme.palette.line))
}
