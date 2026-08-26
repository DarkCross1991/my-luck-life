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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.LogEntry
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted
import java.time.LocalDate

@Composable
fun LogbookScreen(garage: GarageState, ui: GarageUi, vm: GarageViewModel) {
    var add by remember { mutableStateOf(false) }
    val entries = garage.logbook.filterNot { it.deleted }
        .sortedWith(compareByDescending<LogEntry> { it.date }.thenByDescending { it.updatedAt })

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { add = true }, containerColor = Gold) {
                Icon(Icons.Outlined.Add, contentDescription = "Запись")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (entries.isEmpty()) {
                item { Panel { Text("Пока пусто. Пишите осмотры, работы и странности по машине.") } }
            }
            items(entries, key = { it.id }) { entry ->
                val answer = ui.inbox.firstOrNull { it.logId == entry.id }?.answer
                Panel {
                    Text(dateRu(entry.date), color = Muted, style = MaterialTheme.typography.labelMedium)
                    Text(entry.title, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(entry.body)
                    if (!answer.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Разбор", color = Gold, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(answer)
                    } else if (entry.tags.contains("inbox")) {
                        Spacer(Modifier.height(8.dp))
                        Text("Ждёт разбор из git", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (entry.tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(entry.tags.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (add) {
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text("Запись в бортжурнал") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(date) { date = it }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Заголовок") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Что произошло") },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Теги через запятую") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank() || body.isBlank()) {
                        error = "Нужны заголовок и текст"
                    } else {
                        vm.addLog(
                            date,
                            title.trim(),
                            body.trim(),
                            tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        )
                        add = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Отмена") } },
        )
    }
}
