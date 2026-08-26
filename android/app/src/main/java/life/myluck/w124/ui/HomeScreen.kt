package life.myluck.w124.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.NodeStatus
import life.myluck.w124.ui.theme.Danger
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted
import life.myluck.w124.ui.theme.Ok
import java.time.LocalDate

@Composable
fun HomeScreen(
    garage: GarageState,
    ui: GarageUi,
    vm: GarageViewModel,
    onFuel: () -> Unit,
    onJournal: () -> Unit,
    onOpenNode: (String) -> Unit,
) {
    var odoDialog by remember { mutableStateOf(false) }
    val urgent = NodeStatus.urgentWork(ui.nodes)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "${garage.vehicle.make} ${garage.vehicle.model} · ${garage.vehicle.chassis} · ${garage.vehicle.year}",
            color = Muted,
        )
        Text(
            "${km(garage.odometer.km)} км",
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
            color = Gold,
        )
        Text(
            "на ${dateRu(garage.odometer.recordedAt)} · ${garage.vehicle.engine} · ${garage.vehicle.transmission}",
            color = Muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onFuel, modifier = Modifier.weight(1f)) { Text("Заправка") }
            OutlinedButton(onClick = onJournal, modifier = Modifier.weight(1f)) { Text("Запись") }
            OutlinedButton(onClick = { odoDialog = true }, modifier = Modifier.weight(1f)) { Text("Пробег") }
        }
        ui.updates?.takeIf { it.updateAvailable }?.latest?.let { latest ->
            Panel {
                Text("Доступна версия ${latest.versionName}", fontWeight = FontWeight.Medium, color = Gold)
                Text("Настройки → Обновления. Можно поставить или откатиться.", color = Muted)
            }
        }
        ConsumptionStrip(ui.report)
        Text("Срочные работы", color = Muted, style = MaterialTheme.typography.labelLarge)
        Text("Нажмите задачу — внутри шаги, срок и инструмент.", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (urgent.isEmpty()) {
            Panel {
                Text("Открытых срочных работ нет.", color = Muted)
            }
        } else {
            urgent.forEach { item ->
                Panel(onClick = { onOpenNode(item.node.id) }) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(item.node.title, fontWeight = FontWeight.Medium)
                            Text(item.hangingRu, color = Muted, style = MaterialTheme.typography.bodySmall)
                            val missing = item.missingTools
                            if (missing.isNotEmpty()) {
                                Text(
                                    "нет: " + missing.joinToString(", ") { it.name },
                                    color = Danger,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (item.required.any { it.isTool }) {
                                Text("инструмент есть", color = Ok, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        UrgencyChip(item.urgency)
                    }
                }
            }
        }
        Panel {
            Text("Не хватает по текущим задачам", color = Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text("Отметьте, если купили или нашли. Зелёный — есть, красный — нет.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (ui.missingTools.isEmpty()) {
                Text("По срочным работам весь инструмент отмечен как есть.", color = Ok)
            } else {
                ui.missingTools.forEach { missing ->
                    ToolHaveRow(
                        tool = missing.tool,
                        onToggle = { vm.setToolHave(missing.tool.id, true) },
                    )
                    Text(
                        "нужен для: " + missing.neededFor.joinToString(", "),
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 32.dp, bottom = 6.dp),
                    )
                }
            }
        }
        Panel {
            Text("Аналитика с компьютера", color = Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                ui.analytics.ifBlank { "Файл analytics.md ещё пуст. После синхронизации здесь появится разбор." },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (odoDialog) {
        OdometerDialog(
            current = garage.odometer.km,
            onDismiss = { odoDialog = false },
            onSave = { value, date ->
                vm.updateOdometer(value, date)
                odoDialog = false
            },
        )
    }
}

@Composable
fun OdometerDialog(
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit,
) {
    var kmText by remember { mutableStateOf(current.toString()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обновить пробег") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Километры") },
                    singleLine = true,
                )
                DateField(date) { date = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = kmText.toIntOrNull()
                if (value == null || value < current) {
                    error = "Пробег не может быть меньше текущего (${km(current)} км)"
                } else {
                    onSave(value, date)
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun DateField(iso: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val parsed = runCatching { LocalDate.parse(iso) }.getOrElse { LocalDate.now() }
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).toString()) },
                parsed.year,
                parsed.monthValue - 1,
                parsed.dayOfMonth,
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Дата: ${dateRu(iso)}")
    }
}
