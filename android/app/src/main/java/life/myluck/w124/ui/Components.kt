package life.myluck.w124.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import life.myluck.w124.core.FuelInterval
import life.myluck.w124.core.FuelReport
import life.myluck.w124.core.NodeUrgency
import life.myluck.w124.core.ThirstVerdict
import life.myluck.w124.ui.theme.Danger
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted
import life.myluck.w124.ui.theme.Ok
import life.myluck.w124.ui.theme.Surface2
import life.myluck.w124.ui.theme.Warn

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), content = { content() })
    }
}

@Composable
fun ConsumptionStrip(report: FuelReport) {
    val accent = when (report.verdict) {
        ThirstVerdict.RISING, ThirstVerdict.HIGH -> Danger
        ThirstVerdict.SHORT_TRIPS -> Warn
        ThirstVerdict.NORMAL -> Ok
        ThirstVerdict.INSUFFICIENT_DATA -> Gold
    }
    Panel {
        Text("Расход топлива", color = Muted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = report.last?.let { l100(it) } ?: "—,—",
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Text(
                "  л/100 км",
                modifier = Modifier.padding(bottom = 8.dp),
                color = Muted,
            )
        }
        val average = report.average
        if (average != null) {
            Text(
                "средний ${l100(average)} · ${report.intervals.size} интерв.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        ConsumptionChart(
            intervals = report.intervals,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(report.summaryRu, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ConsumptionChart(intervals: List<FuelInterval>, modifier: Modifier = Modifier) {
    val values = intervals.map { it.litersPer100km.toFloat() }
    Box(
        modifier = modifier.background(Surface2, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (values.size < 2) {
            Text("линия появится после двух интервалов", color = Muted, fontSize = 12.sp)
            return@Box
        }
        Canvas(Modifier.matchParentSize().padding(12.dp)) {
            val min = minOf(values.min() - 0.6f, 8f)
            val max = maxOf(values.max() + 0.6f, 14f)
            val span = (max - min).coerceAtLeast(0.5f)
            fun x(i: Int) = size.width * i / (values.lastIndex).toFloat()
            fun y(v: Float) = size.height - (v - min) / span * size.height
            val mid = 11.5f
            drawLine(
                color = Muted.copy(alpha = 0.35f),
                start = Offset(0f, y(mid)),
                end = Offset(size.width, y(mid)),
                strokeWidth = 2f,
            )
            val path = Path()
            values.forEachIndexed { i, v ->
                val p = Offset(x(i), y(v))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, Gold, style = Stroke(width = 5f, cap = StrokeCap.Round))
            values.forEachIndexed { i, v ->
                drawCircle(Gold, radius = 6f, center = Offset(x(i), y(v)))
            }
        }
    }
}

@Composable
fun UrgencyChip(urgency: NodeUrgency) {
    val (bg, fg) = when (urgency) {
        NodeUrgency.URGENT -> Danger.copy(alpha = 0.18f) to Danger
        NodeUrgency.OVERDUE -> Warn.copy(alpha = 0.16f) to Warn
        NodeUrgency.SOON -> Gold.copy(alpha = 0.16f) to Gold
        NodeUrgency.WATCH -> Surface2 to Muted
        NodeUrgency.OK -> Ok.copy(alpha = 0.16f) to Ok
    }
    Text(
        text = life.myluck.w124.core.NodeStatus.urgencyLabelRu(urgency),
        color = fg,
        fontSize = 12.sp,
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = Muted)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
