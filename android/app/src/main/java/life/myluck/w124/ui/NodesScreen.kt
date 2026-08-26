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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import life.myluck.w124.core.NodeUrgency
import life.myluck.w124.core.NodeView
import life.myluck.w124.ui.theme.Muted
import java.time.LocalDate

private enum class NodeFilter { ALL, URGENT, SERVICE }

@Composable
fun NodesScreen(nodes: List<NodeView>, odometerKm: Int, vm: GarageViewModel) {
    var filter by remember { mutableStateOf(NodeFilter.ALL) }
    var completeId by remember { mutableStateOf<String?>(null) }
    val visible = nodes.filter { view ->
        when (filter) {
            NodeFilter.ALL -> true
            NodeFilter.URGENT -> view.urgency == NodeUrgency.URGENT || view.urgency == NodeUrgency.OVERDUE
            NodeFilter.SERVICE -> view.node.kind == "service"
        }
    }

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == NodeFilter.ALL, onClick = { filter = NodeFilter.ALL }, label = { Text("Все") })
                FilterChip(selected = filter == NodeFilter.URGENT, onClick = { filter = NodeFilter.URGENT }, label = { Text("Срочно") })
                FilterChip(selected = filter == NodeFilter.SERVICE, onClick = { filter = NodeFilter.SERVICE }, label = { Text("Регламент") })
            }
        }
        items(visible, key = { it.node.id }) { view ->
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(view.node.title, fontWeight = FontWeight.SemiBold)
                        Text(view.reasonRu, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    UrgencyChip(view.urgency)
                }
                view.node.lastDoneNote?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                view.node.howTo?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (view.node.open || view.urgency != NodeUrgency.OK) {
                        TextButton(onClick = { completeId = view.node.id }) { Text("Сделано") }
                    } else {
                        TextButton(onClick = { vm.reopenNode(view.node.id) }) { Text("Вернуть в очередь") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    val editing = nodes.firstOrNull { it.node.id == completeId }
    if (editing != null) {
        CompleteNodeDialog(
            title = editing.node.title,
            odometerKm = odometerKm,
            onDismiss = { completeId = null },
            onSave = { date, km, note ->
                vm.completeNode(editing.node.id, date, km, note)
                completeId = null
            },
        )
    }
}

@Composable
private fun CompleteNodeDialog(
    title: String,
    odometerKm: Int,
    onDismiss: () -> Unit,
    onSave: (date: String, km: Int, note: String?) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var kmText by remember { mutableStateOf(odometerKm.toString()) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(date) { date = it }
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Пробег, км") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Что сделали") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kmVal = kmText.toIntOrNull()
                if (kmVal == null) error = "Укажите пробег" else onSave(date, kmVal, note.ifBlank { null })
            }) { Text("Закрыть работу") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
