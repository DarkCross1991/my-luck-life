package life.myluck.w124.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import life.myluck.w124.core.GarageState
import life.myluck.w124.ui.theme.Bg
import life.myluck.w124.ui.theme.Muted
import java.time.LocalDate

@Composable
fun JournalComposerScreen(
    garage: GarageState,
    ui: GarageUi,
    vm: GarageViewModel,
    onClose: () -> Unit,
) {
    val active = ui.inbox.firstOrNull { it.id == ui.activeInquiryId }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var kmText by remember { mutableStateOf(garage.odometer.km.toString()) }
    var body by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val submitted = active != null
    val answer = active?.answer.orEmpty()

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Запись в журнал", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Пробег и что увидели/услышали. Уедет в git — разбор появится в третьем поле и во вкладке Журнал.",
                color = Muted,
            )
            DateField(date) { if (!submitted) date = it }
            OutlinedTextField(
                value = if (submitted) active!!.odometer.toString() else kmText,
                onValueChange = { kmText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Пробег, км") },
                singleLine = true,
                enabled = !submitted,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = if (submitted) active.body else body,
                onValueChange = { body = it },
                label = { Text("Что записать") },
                enabled = !submitted,
                minLines = 5,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            OutlinedTextField(
                value = when {
                    answer.isNotBlank() -> answer
                    submitted -> "Жду разбор из git…"
                    else -> ""
                },
                onValueChange = {},
                label = { Text("Разбор") },
                enabled = false,
                minLines = 5,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            if (submitted && answer.isBlank()) {
                Text(
                    "Заметка уже в журнале. Разбор подтянется после того, как агент её обработает (обычно сразу после синхронизации).",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!submitted) {
                Button(
                    onClick = {
                        val kmVal = kmText.toIntOrNull()
                        when {
                            kmVal == null -> error = "Укажите пробег"
                            kmVal < garage.odometer.km -> error = "Пробег не может быть меньше текущего"
                            body.isBlank() -> error = "Напишите, что произошло"
                            else -> {
                                error = null
                                vm.submitInquiry(date, kmVal, body.trim())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Отправить в git") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text("Закрыть")
                }
                if (submitted && answer.isBlank()) {
                    OutlinedButton(
                        onClick = { vm.sync() },
                        enabled = !ui.syncing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (ui.syncing) "Синхронизация…" else "Проверить разбор")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
