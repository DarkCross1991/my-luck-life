package life.myluck.w124.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.LogEntry
import life.myluck.w124.ui.theme.Gold
import life.myluck.w124.ui.theme.Muted

@Composable
fun LogbookScreen(
    garage: GarageState,
    ui: GarageUi,
    onCompose: () -> Unit,
) {
    val entries = garage.logbook.filterNot { it.deleted }
        .sortedWith(compareByDescending<LogEntry> { it.date }.thenByDescending { it.updatedAt })

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCompose, containerColor = Gold) {
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
                item { Panel { Text("Пока пусто. Запись — с главной или плюсом здесь: пробег, описание, разбор.") } }
            }
            items(entries, key = { it.id }) { entry ->
                val inquiry = ui.inbox.firstOrNull { it.logId == entry.id }
                Panel {
                    val kmLine = inquiry?.odometer?.let { "${dateRu(entry.date)} · ${km(it)} км" }
                        ?: dateRu(entry.date)
                    Text(kmLine, color = Muted, style = MaterialTheme.typography.labelMedium)
                    if (inquiry == null && entry.title.isNotBlank()) {
                        Text(entry.title, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text("Описание:", color = Muted, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(entry.body)
                    if (inquiry != null) {
                        Spacer(Modifier.height(10.dp))
                        Text("Разбор проблематики от тебя:", color = Gold, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            inquiry.answer?.takeIf { it.isNotBlank() }
                                ?: "Жду разбор из git",
                            color = if (inquiry.answer.isNullOrBlank()) Muted else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}
