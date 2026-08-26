package life.myluck.w124.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import life.myluck.w124.core.NodeView
import life.myluck.w124.core.ToolItem
import life.myluck.w124.ui.theme.Danger
import life.myluck.w124.ui.theme.Muted
import life.myluck.w124.ui.theme.Ok

@Composable
fun NodeDetailScreen(
    view: NodeView,
    odometerKm: Int,
    vm: GarageViewModel,
    onBack: () -> Unit,
) {
    var complete by remember { mutableStateOf(false) }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                view.node.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            UrgencyChip(view.urgency)
        }

        Panel {
            Text("Что сделать", color = Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(view.job?.what ?: view.node.lastDoneNote ?: view.node.howTo ?: "План работы ещё не расписан.")
            view.node.lastDoneNote?.takeIf { !view.job?.what.isNullOrBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        Panel {
            Text("Срок и срочность", color = Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(view.hangingRu, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Сейчас: ${life.myluck.w124.core.NodeStatus.urgencyLabelRu(view.urgency)} · ${view.reasonRu}", color = Muted)
        }

        Panel {
            Text("Инструмент и расходники", color = Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            if (view.required.isEmpty()) {
                Text("Для этой работы отдельный список не задан.", color = Muted)
            } else {
                view.required.forEach { tool ->
                    ToolHaveRow(tool = tool, onToggle = { vm.setToolHave(tool.id, !tool.have) })
                }
            }
        }

        Panel {
            Text("Последовательность", color = Muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            val steps = view.job?.steps.orEmpty()
            if (steps.isEmpty()) {
                Text(view.node.howTo ?: "Шаги появятся после следующего разбора.", color = Muted)
            } else {
                steps.forEachIndexed { index, step ->
                    Row(
                        Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "${index + 1}.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(step, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (view.node.open || view.urgency != life.myluck.w124.core.NodeUrgency.OK) {
                TextButton(onClick = { complete = true }) { Text("Сделано") }
            } else {
                TextButton(onClick = { vm.reopenNode(view.node.id) }) { Text("Вернуть в очередь") }
            }
            TextButton(onClick = onBack) { Text("К списку") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (complete) {
        CompleteNodeDialog(
            title = view.node.title,
            odometerKm = odometerKm,
            onDismiss = { complete = false },
            onSave = { date, km, note ->
                vm.completeNode(view.node.id, date, km, note)
                complete = false
                onBack()
            },
        )
    }
}

@Composable
fun ToolHaveRow(tool: ToolItem, onToggle: () -> Unit) {
    val color = if (tool.have) Ok else Danger
    val mark = if (tool.have) "есть" else "нет"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (tool.have) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = mark,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(tool.name, color = color, fontWeight = FontWeight.Medium)
            val kind = if (tool.isTool) "инструмент" else "расходник"
            Text(
                "$kind · $mark" + (tool.note?.let { " · $it" } ?: ""),
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
