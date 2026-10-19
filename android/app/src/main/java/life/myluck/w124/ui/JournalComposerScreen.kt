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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.InboxItem
import life.myluck.w124.ui.theme.Bg
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted
import java.time.LocalDate

@Composable
fun JournalComposerScreen(
    garage: GarageState,
    ui: GarageUi,
    vm: GarageViewModel,
    onClose: () -> Unit,
) {
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            JournalNoteForm(
                currentOdometer = garage.odometer.km,
                submitted = ui.inbox.firstOrNull { it.id == ui.activeInquiryId },
                syncing = ui.syncing,
                onSubmit = { date, km, body -> vm.submitInquiry(date, km, body) },
                onSync = { vm.sync() },
                onNewNote = { vm.startNewInquiry() },
                showClose = true,
                onClose = onClose,
            )
        }
    }
}

@Composable
fun JournalNoteForm(
    currentOdometer: Int,
    submitted: InboxItem?,
    syncing: Boolean,
    onSubmit: (date: String, km: Int, body: String) -> Unit,
    onSync: () -> Unit,
    onNewNote: () -> Unit,
    showClose: Boolean = false,
    onClose: () -> Unit = {},
) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var kmText by remember(currentOdometer) { mutableStateOf(currentOdometer.toString()) }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var previousId by remember { mutableStateOf(submitted?.id) }
    val locked = submitted != null
    LaunchedEffect(submitted?.id) {
        val prev = previousId
        previousId = submitted?.id
        if (submitted == null && prev != null) {
            date = LocalDate.now().toString()
            kmText = currentOdometer.toString()
            description = ""
            error = null
        }
    }
    val answer = submitted?.answer.orEmpty()
    val analysis = when {
        answer.isNotBlank() -> answer
        locked -> "Жду разбор из git…"
        else -> ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Запись", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        DateField(date) { if (!locked) date = it }
        Text("Пробег на момент записи:", color = Muted, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = if (locked) submitted!!.odometer.toString() else kmText,
            onValueChange = { kmText = it.filter { ch -> ch.isDigit() } },
            enabled = !locked,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Описание:", color = Muted, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = if (locked) submitted!!.body else description,
            onValueChange = { description = it },
            enabled = !locked,
            minLines = 5,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        Text("Разбор проблематики от тебя:", color = Gold, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = analysis,
            onValueChange = {},
            enabled = false,
            minLines = 5,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!locked) {
            Button(
                onClick = {
                    val kmVal = kmText.toIntOrNull()
                    when {
                        kmVal == null -> error = "Укажите пробег"
                        kmVal < currentOdometer -> error = "Пробег не может быть меньше текущего"
                        description.isBlank() -> error = "Напишите описание"
                        else -> {
                            error = null
                            onSubmit(date, kmVal, description.trim())
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Отправить") }
        } else {
            Text(
                if (answer.isBlank()) {
                    "Ушло в git. Разбор появится в этом поле и во вкладке Журнал."
                } else {
                    "Разбор уже в журнале."
                },
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (showClose) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Закрыть") }
            }
            if (locked && answer.isBlank()) {
                OutlinedButton(
                    onClick = onSync,
                    enabled = !syncing,
                    modifier = Modifier.weight(1f),
                ) { Text(if (syncing) "Синхронизация…" else "Проверить разбор") }
            }
            if (locked) {
                OutlinedButton(onClick = onNewNote, modifier = Modifier.weight(1f)) {
                    Text("Новая запись")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
