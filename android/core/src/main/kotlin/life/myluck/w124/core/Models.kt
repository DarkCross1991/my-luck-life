package life.myluck.w124.core

import kotlinx.serialization.Serializable

interface DatedId {
    val id: String
    val updatedAt: String
}

@Serializable
data class GarageState(
    val schemaVersion: Int = 1,
    val updatedAt: String,
    val vehicle: Vehicle,
    val odometer: Odometer,
    val fuel: List<FuelEntry> = emptyList(),
    val nodes: List<NodeItem> = emptyList(),
    val logbook: List<LogEntry> = emptyList(),
    val deletedIds: List<String> = emptyList(),
)

@Serializable
data class Vehicle(
    override val id: String,
    val make: String,
    val model: String,
    val chassis: String,
    val year: Int,
    val engine: String,
    val engineNote: String? = null,
    val transmission: String,
    val color: String? = null,
    val purchasedAt: String,
    val odometerAtPurchaseKm: Int,
    val tankLiters: Double = 70.0,
    override val updatedAt: String,
) : DatedId

@Serializable
data class Odometer(
    val km: Int,
    val recordedAt: String,
    val source: String,
    override val updatedAt: String,
) : DatedId {
    override val id: String get() = "odometer"
}

@Serializable
data class FuelEntry(
    override val id: String,
    val date: String,
    val odometer: Int,
    val liters: Double,
    val full: Boolean = true,
    val pricePerLiter: Double? = null,
    val totalCost: Double? = null,
    val tripType: String = TripType.MIXED,
    val station: String? = null,
    val note: String? = null,
    val receiptText: String? = null,
    val source: String = "manual",
    val deleted: Boolean = false,
    override val updatedAt: String,
) : DatedId

object TripType {
    const val CITY = "city"
    const val HIGHWAY = "highway"
    const val MIXED = "mixed"
    const val SHORT = "short"

    fun labelRu(value: String): String = when (value) {
        CITY -> "Город"
        HIGHWAY -> "Трасса"
        SHORT -> "Короткие"
        else -> "Смешанный"
    }
}

@Serializable
data class NodeItem(
    override val id: String,
    val title: String,
    val system: String,
    val kind: String,
    val priority: String,
    val open: Boolean = false,
    val intervalKm: Int? = null,
    val intervalMonths: Int? = null,
    val lastDoneAt: String? = null,
    val lastDoneKm: Int? = null,
    val lastDoneNote: String? = null,
    val howTo: String? = null,
    override val updatedAt: String,
) : DatedId

object ToolKind {
    const val TOOL = "tool"
    const val PART = "part"
}

@Serializable
data class ToolItem(
    override val id: String,
    val name: String,
    val kind: String = ToolKind.TOOL,
    val have: Boolean = false,
    val note: String? = null,
    override val updatedAt: String,
) : DatedId {
    val isTool: Boolean get() = kind == ToolKind.TOOL
}

@Serializable
data class ToolInventory(
    val schemaVersion: Int = 1,
    val updatedAt: String,
    val tools: List<ToolItem> = emptyList(),
)

@Serializable
data class JobPlan(
    val nodeId: String,
    val what: String,
    val openedAt: String,
    val steps: List<String> = emptyList(),
    val toolIds: List<String> = emptyList(),
    override val updatedAt: String,
) : DatedId {
    override val id: String get() = nodeId
}

@Serializable
data class JobBook(
    val schemaVersion: Int = 1,
    val updatedAt: String,
    val jobs: List<JobPlan> = emptyList(),
)

@Serializable
data class LogEntry(
    override val id: String,
    val date: String,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val deleted: Boolean = false,
    override val updatedAt: String,
) : DatedId

object InboxStatus {
    const val PENDING = "pending"
    const val ANSWERED = "answered"
}

@Serializable
data class InboxItem(
    override val id: String,
    val date: String,
    val odometer: Int,
    val body: String,
    val status: String = InboxStatus.PENDING,
    val answer: String? = null,
    val logId: String,
    override val updatedAt: String,
) : DatedId

@Serializable
data class InboxBook(
    val schemaVersion: Int = 1,
    val updatedAt: String,
    val items: List<InboxItem> = emptyList(),
)

enum class NodeUrgency {
    URGENT,
    OVERDUE,
    SOON,
    WATCH,
    OK,
}

enum class ThirstVerdict {
    INSUFFICIENT_DATA,
    NORMAL,
    SHORT_TRIPS,
    RISING,
    HIGH,
}

data class FuelInterval(
    val fromOdometer: Int,
    val toOdometer: Int,
    val fromDate: String,
    val toDate: String,
    val km: Int,
    val liters: Double,
    val litersPer100km: Double,
    val tripTypes: List<String>,
    val fillIds: List<String>,
)

data class FuelReport(
    val intervals: List<FuelInterval>,
    val last: Double?,
    val average: Double?,
    val median: Double?,
    val trendPercent: Double?,
    val verdict: ThirstVerdict,
    val summaryRu: String,
)

data class NodeView(
    val node: NodeItem,
    val urgency: NodeUrgency,
    val dueKm: Int?,
    val dueDate: String?,
    val reasonRu: String,
    val job: JobPlan? = null,
    val hangingDays: Int = 0,
    val hangingRu: String = "",
    val required: List<ToolItem> = emptyList(),
) {
    val missingTools: List<ToolItem> get() = required.filter { it.isTool && !it.have }
    val missingParts: List<ToolItem> get() = required.filter { !it.isTool && !it.have }
}

data class MissingTool(
    val tool: ToolItem,
    val neededFor: List<String>,
)
