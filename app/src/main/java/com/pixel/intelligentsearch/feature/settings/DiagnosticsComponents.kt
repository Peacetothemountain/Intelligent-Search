package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Space between the diagnostics cards, and between the parts inside one. */
internal val DiagnosticsCardSpacing = 12.dp

/**
 * One diagnostics card: an icon and title with [trailing] chips on the right, over [content]. The header wraps the
 * chips under the title when both don't fit on one line (narrow windows, large text) instead of squeezing either.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiagnosticsCard(
    icon: ImageVector,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(DiagnosticsCardSpacing)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) { trailing() }
            }
            content()
        }
    }
}

/** A small rounded label in the card header, e.g. a health or pressure rating. */
@Composable
internal fun DiagnosticsStatusChip(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** A labelled reading in a card's chip grid; [valueColor] defaults to the normal text colour. */
@Immutable
internal data class DiagnosticsMetric(val label: String, val value: String, val valueColor: Color = Color.Unspecified)

private val MetricLabelSize = 10.sp
private val MetricGap = 8.dp
private val MetricPaddingH = 8.dp

/**
 * The readings under a card, four to a row when they fit, otherwise two (or one): each chip's columns count is
 * chosen from the widest label or value at the current text size, so large fonts and zoomed or narrow screens get
 * fewer, wider chips rather than clipped text.
 */
@Composable
internal fun DiagnosticsMetricGrid(metrics: List<DiagnosticsMetric>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = MetricLabelSize)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widestPx = remember(metrics, labelStyle, valueStyle, density) {
            metrics.maxOfOrNull { m ->
                maxOf(
                    measurer.measure(m.label, labelStyle, softWrap = false, maxLines = 1).size.width,
                    measurer.measure(m.value, valueStyle, softWrap = false, maxLines = 1).size.width
                )
            } ?: 0
        }
        val widest = with(density) { widestPx.toDp() } + MetricPaddingH * 2
        fun fits(columns: Int) = (maxWidth - MetricGap * (columns - 1)) / columns >= widest
        val columns = when {
            metrics.size >= 4 && fits(4) -> 4
            fits(2) -> 2
            else -> 1
        }.coerceAtMost(metrics.size.coerceAtLeast(1))
        Column(verticalArrangement = Arrangement.spacedBy(MetricGap)) {
            metrics.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(MetricGap)
                ) {
                    row.forEach { metric ->
                        MetricChip(metric, labelStyle.fontSize, Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(metric: DiagnosticsMetric, labelSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MetricPaddingH, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = labelSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (metric.valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else metric.valueColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
