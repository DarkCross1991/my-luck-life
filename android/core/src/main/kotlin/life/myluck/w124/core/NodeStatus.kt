package life.myluck.w124.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object NodeStatus {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun views(state: GarageState, today: LocalDate = LocalDate.now()): List<NodeView> {
        return state.nodes
            .map { view(it, state.odometer.km, today) }
            .sortedWith(
                compareBy<NodeView> { urgencyRank(it.urgency) }
                    .thenBy { it.dueKm ?: Int.MAX_VALUE }
                    .thenBy { it.node.title },
            )
    }

    fun view(node: NodeItem, odometerKm: Int, today: LocalDate = LocalDate.now()): NodeView {
        val dueKm = node.lastDoneKm?.let { last -> node.intervalKm?.let { last + it } }
        val dueDate = node.lastDoneAt?.let { last ->
            node.intervalMonths?.let { months -> parse(last)?.plusMonths(months.toLong())?.toString() }
        }
        val overdueKm = dueKm != null && odometerKm >= dueKm
        val overdueDate = dueDate?.let { parse(it) }?.let { !it.isAfter(today) } == true
        val soonKm = dueKm != null && !overdueKm && dueKm - odometerKm <= 500
        val soonDate = dueDate?.let { parse(it) }?.let { d ->
            !overdueDate && !d.isAfter(today.plusDays(14))
        } == true

        val urgency = when {
            node.open && node.priority == "urgent" -> NodeUrgency.URGENT
            overdueKm || overdueDate -> NodeUrgency.OVERDUE
            node.open && node.lastDoneAt == null -> NodeUrgency.OVERDUE
            soonKm || soonDate -> NodeUrgency.SOON
            node.open -> NodeUrgency.WATCH
            else -> NodeUrgency.OK
        }
        return NodeView(
            node = node,
            urgency = urgency,
            dueKm = dueKm,
            dueDate = dueDate,
            reasonRu = reason(node, urgency, dueKm, dueDate, odometerKm),
        )
    }

    fun nearest(state: GarageState, today: LocalDate = LocalDate.now(), limit: Int = 4): List<NodeView> {
        return views(state, today)
            .filter { it.urgency != NodeUrgency.OK }
            .take(limit)
    }

    fun urgencyLabelRu(urgency: NodeUrgency): String = when (urgency) {
        NodeUrgency.URGENT -> "Срочно"
        NodeUrgency.OVERDUE -> "Пора"
        NodeUrgency.SOON -> "Скоро"
        NodeUrgency.WATCH -> "Смотреть"
        NodeUrgency.OK -> "Норма"
    }

    private fun reason(
        node: NodeItem,
        urgency: NodeUrgency,
        dueKm: Int?,
        dueDate: String?,
        odometerKm: Int,
    ): String {
        val bits = mutableListOf<String>()
        when (urgency) {
            NodeUrgency.URGENT -> bits += "открыто как срочное"
            NodeUrgency.OVERDUE -> bits += if (node.lastDoneAt == null) "нет даты последнего ТО" else "срок вышел"
            NodeUrgency.SOON -> bits += "приближается срок"
            NodeUrgency.WATCH -> bits += "в очереди"
            NodeUrgency.OK -> bits += "в допуске"
        }
        if (dueKm != null) bits += "до ${formatKm(dueKm)} км (сейчас ${formatKm(odometerKm)})"
        if (dueDate != null) bits += "до $dueDate"
        node.intervalKm?.let { bits += "интервал $it км" }
        node.intervalMonths?.let { bits += "интервал $it мес." }
        return bits.joinToString(" · ")
    }

    fun formatKm(km: Int): String {
        val s = km.toString()
        val sb = StringBuilder()
        s.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) sb.append(' ')
            sb.append(c)
        }
        return sb.reverse().toString()
    }

    private fun urgencyRank(u: NodeUrgency): Int = when (u) {
        NodeUrgency.URGENT -> 0
        NodeUrgency.OVERDUE -> 1
        NodeUrgency.SOON -> 2
        NodeUrgency.WATCH -> 3
        NodeUrgency.OK -> 4
    }

    private fun parse(value: String): LocalDate? = runCatching { LocalDate.parse(value, iso) }.getOrNull()
}
