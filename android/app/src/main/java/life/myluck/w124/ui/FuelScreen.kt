package life.myluck.w124.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import life.myluck.w124.core.FuelEntry
import life.myluck.w124.core.FuelReport
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.TripType
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted
import java.time.LocalDate

@Composable
fun FuelScreen(garage: GarageState, report: FuelReport, vm: GarageViewModel) {
    var add by remember { mutableStateOf(false) }
    val fills = garage.fuel.filterNot { it.deleted }.sortedByDescending { it.odometer }
    val intervalByFill = report.intervals.associateBy { it.fillIds.lastOrNull() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { add = true }, containerColor = Gold) {
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
                            "Первая заправка задаст точку отсчёта. Расход — после второй полной. " +
                                "Частичные доливки тоже пишите: они войдут в сумму литров.",
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

    if (add) {
        FuelEditorDialog(
            title = "Заправка",
            initialOdometer = garage.odometer.km,
            onDismiss = { add = false },
            onSave = { date, odo, liters, full, trip, price, total, note ->
                vm.addFuel(date, odo, liters, full, trip, price, total, note)
                add = false
            },
        )
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
                        fill.totalCost?.let { append(" · ${money(it)}") }
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
fun FuelEditorDialog(
    title: String,
    initialOdometer: Int,
    onDismiss: () -> Unit,
    onSave: (
        date: String,
        odometer: Int,
        liters: Double,
        full: Boolean,
        tripType: String,
        pricePerLiter: Double?,
        totalCost: Double?,
        note: String?,
    ) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var odo by remember { mutableStateOf(initialOdometer.toString()) }
    var liters by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    var trip by remember { mutableStateOf(TripType.MIXED) }
    var price by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(date) { date = it }
                OutlinedTextField(
                    value = odo,
                    onValueChange = { odo = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Пробег, км") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = liters,
                    onValueChange = { liters = it.replace(',', '.') },
                    label = { Text("Литры") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = full, onCheckedChange = { full = it })
                    Text("Полный бак")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        TripType.MIXED to "Смеш.",
                        TripType.CITY to "Город",
                        TripType.HIGHWAY to "Трасса",
                        TripType.SHORT to "Коротк.",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = trip == value,
                            onClick = { trip = value },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.replace(',', '.') },
                    label = { Text("Цена ₽/л (необяз.)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it.replace(',', '.') },
                    label = { Text("Сумма ₽ (необяз.)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val odoVal = odo.toIntOrNull()
                val litersVal = liters.toDoubleOrNull()
                when {
                    odoVal == null || odoVal < initialOdometer ->
                        error = "Пробег не меньше текущего (${km(initialOdometer)} км)"
                    litersVal == null || litersVal <= 0.0 ->
                        error = "Укажите литры"
                    else -> {
                        val priceVal = price.toDoubleOrNull()
                        val totalVal = total.toDoubleOrNull()
                            ?: if (priceVal != null) priceVal * litersVal else null
                        onSave(
                            date,
                            odoVal,
                            litersVal,
                            full,
                            trip,
                            priceVal,
                            totalVal,
                            note.ifBlank { null },
                        )
                    }
                }
            }) { Text("Записать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
