package life.myluck.w124.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import life.myluck.w124.core.FuelEntry
import life.myluck.w124.core.FuelReport
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.TripType
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted

@Composable
fun FuelScreen(
    garage: GarageState,
    report: FuelReport,
    vm: GarageViewModel,
    onAdd: () -> Unit,
) {
    val fills = garage.fuel.filterNot { it.deleted }.sortedByDescending { it.odometer }
    val intervalByFill = report.intervals.associateBy { it.fillIds.lastOrNull() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = Gold) {
                Icon(Icons.Outlined.Add, contentDescription = "Добавить заправку")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item { ConsumptionStrip(report) }
            if (fills.isEmpty()) {
                item {
                    Panel {
                        Text(
                            "Оплатили с телефона — «Поделиться» квитанцией в Бортжурнал и впишите пробег. " +
                                "Расход появится после второй полной заправки.",
                        )
                    }
                }
            }
            items(fills, key = { it.id }) { fill ->
                FuelRow(
                    fill = fill,
                    intervalL100 = intervalByFill[fill.id]?.litersPer100km,
                    onDelete = { vm.deleteFuel(fill.id) },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun FuelRow(fill: FuelEntry, intervalL100: Double?, onDelete: () -> Unit) {
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
