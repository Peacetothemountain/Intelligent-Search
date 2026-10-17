package com.ngdesigns.intelligentsearch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ngdesigns.intelligentsearch.R

/** The search screen's frame: results grow upward towards the scope chips and the floating search bar. */
@Composable
fun SearchShell(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val query = rememberTextFieldState()
    var scope by remember { mutableIntStateOf(0) }
    Surface(modifier.fillMaxSize(), color = colors.surface) {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Apps", "People", "Files", "Web").forEachIndexed { i, label ->
                    FilterChip(selected = scope == i, onClick = { scope = i }, label = { Text(label) })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(Modifier.weight(1f).height(56.dp), shape = CircleShape, color = colors.surfaceContainerHighest) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                        Box(Modifier.weight(1f).padding(start = 12.dp)) {
                            if (query.text.isEmpty()) {
                                Text("Search", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                            }
                            BasicTextField(
                                state = query,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                                cursorBrush = SolidColor(colors.primary),
                                lineLimits = TextFieldLineLimits.SingleLine,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                FloatingActionButton(onClick = {}, containerColor = colors.primaryContainer) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = "Voice search")
                }
            }
        }
    }
}
