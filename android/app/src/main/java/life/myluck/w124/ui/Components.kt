package life.myluck.w124.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import life.myluck.w124.core.FuelEntry
import life.myluck.w124.core.NodeUrgency
import life.myluck.w124.core.TripType
import life.myluck.w124.ui.theme.Danger
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted
import life.myluck.w124.ui.theme.Ok
import life.myluck.w124.ui.theme.Surface2
import life.myluck.w124.ui.theme.Warn

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), content = { content() })
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) { body() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) { body() }
    }
}

@Composable
fun FuelRow(fill: FuelEntry, intervalL100: Double?, onDelete: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${dateRu(fill.date)} · ${km(fill.odometer)} км", fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        append("${liters(fill.liters)} л")
                        append(if (fill.full) " · полный бак" else " · долив")
                        append(" · ${TripType.labelRu(fill.tripType)}")
                        fill.station?.let { append(" · $it") }
                        fill.totalCost?.let { append(" · ${money(it)}") }
                        if (fill.source == "receipt") append(" · квитанция")
                    },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                fill.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (intervalL100 != null) {
                    Text("${l100(intervalL100)}", color = Gold, fontWeight = FontWeight.SemiBold)
                    Text("л/100", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                }
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
