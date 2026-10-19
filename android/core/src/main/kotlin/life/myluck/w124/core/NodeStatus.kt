package life.myluck.w124.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object NodeStatus {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val ruDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d.MM.yyyy")

    fun views(
        state: GarageState,
        today: LocalDate = LocalDate.now(),
        jobs: List<JobPlan> = emptyList(),
        tools: List<ToolItem> = emptyList(),
    ): List<NodeView> {
        val jobBy = jobs.associateBy { it.nodeId }
        val toolBy = tools.associateBy { it.id }
        return state.nodes
            .map { view(it, state.odometer.km, today, jobBy[it.id], toolBy, state.vehicle.purchasedAt) }
            .sortedWith(
                compareBy<NodeView> { urgencyRank(it.urgency) }
                    .thenBy { it.dueKm ?: Int.MAX_VALUE }
                    .thenBy { it.node.title },
            )
    }

    fun view(
        node: NodeItem,
        odometerKm: Int,
        today: LocalDate = LocalDate.now(),
        job: JobPlan? = null,
        toolsById: Map<String, ToolItem> = emptyMap(),
        purchasedAt: String? = null,
    ): NodeView {
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
        val openedAt = job?.openedAt ?: node.lastDoneAt ?: purchasedAt
        val hangingDays = openedAt?.let { start ->
            parse(start)?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(0) }
        } ?: 0
        val required = job?.toolIds.orEmpty().mapNotNull { toolsById[it] }
        return NodeView(
            node = node,
            urgency = urgency,
            dueKm = dueKm,
            dueDate = dueDate,
            reasonRu = reason(node, urgency, dueKm, dueDate, odometerKm),
            job = job,
            hangingDays = hangingDays,
            hangingRu = hangingRu(openedAt, hangingDays, purchasedAt),
            required = required,
        )
    }

    fun nearest(state: GarageState, today: LocalDate = LocalDate.now(), limit: Int = 4): List<NodeView> {
        return views(state, today).filter { it.urgency != NodeUrgency.OK }.take(limit)
    }

    fun urgentWork(views: List<NodeView>): List<NodeView> {
        return views.filter { it.urgency == NodeUrgency.URGENT || it.urgency == NodeUrgency.OVERDUE }
    }

    fun missingTools(views: List<NodeView>): List<MissingTool> {
        val current = urgentWork(views)
        val neededFor = linkedMapOf<String, MutableList<String>>()
        val byId = linkedMapOf<String, ToolItem>()
        current.forEach { view ->
            view.required.filter { it.isTool && !it.have }.forEach { tool ->
                byId.putIfAbsent(tool.id, tool)
                neededFor.getOrPut(tool.id) { mutableListOf() }.add(view.node.title)
            }
        }
        return byId.values.map { tool ->
            MissingTool(tool = tool, neededFor = neededFor[tool.id].orEmpty().distinct())
        }.sortedBy { it.tool.name }
    }

    fun urgencyLabelRu(urgency: NodeUrgency): String = when (urgency) {
        NodeUrgency.URGENT -> "Срочно"
        NodeUrgency.OVERDUE -> "Пора"
        NodeUrgency.SOON -> "Скоро"
        NodeUrgency.WATCH -> "Смотреть"
        NodeUrgency.OK -> "Норма"
    }

    fun daysRu(n: Int): String {
        val n10 = n % 10
        val n100 = n % 100
        val word = when {
            n100 in 11..14 -> "дней"
            n10 == 1 -> "день"
            n10 in 2..4 -> "дня"
            else -> "дней"
        }
        return "$n $word"
    }

    private fun hangingRu(openedAt: String?, days: Int, purchasedAt: String?): String {
        val since = openedAt?.let { parse(it)?.format(ruDate) }
        val fromPurchase = openedAt != null && openedAt == purchasedAt
        val prefix = when {
            days <= 0 && since != null -> "Открыта сегодня"
            fromPurchase -> "Висит с покупки · уже ${daysRu(days)}"
            else -> "Висит уже ${daysRu(days)}"
        }
        return if (since != null && days > 0) "$prefix · с $since" else prefix
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
