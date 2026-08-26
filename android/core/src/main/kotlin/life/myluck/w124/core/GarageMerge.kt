package life.myluck.w124.core

object GarageMerge {
    fun merge(local: GarageState, remote: GarageState): GarageState {
        val deleted = (local.deletedIds + remote.deletedIds).distinct().sorted()
        val fuel = mergeById(local.fuel, remote.fuel)
            .filterNot { it.id in deleted || it.deleted }
        val nodes = mergeById(local.nodes, remote.nodes)
            .filterNot { it.id in deleted }
        val logbook = mergeById(local.logbook, remote.logbook)
            .filterNot { it.id in deleted || it.deleted }
        val odometer = listOf(local.odometer, remote.odometer).maxWith(
            compareBy<Odometer> { it.km }.thenBy { it.updatedAt },
        )
        val vehicle = if (local.vehicle.updatedAt >= remote.vehicle.updatedAt) local.vehicle else remote.vehicle
        val updatedAt = listOf(local.updatedAt, remote.updatedAt, odometer.updatedAt).maxOrNull()
            ?: local.updatedAt
        return GarageState(
            schemaVersion = maxOf(local.schemaVersion, remote.schemaVersion),
            updatedAt = updatedAt,
            vehicle = vehicle,
            odometer = odometer,
            fuel = fuel.sortedWith(compareBy({ it.date }, { it.odometer })),
            nodes = nodes.sortedBy { it.title },
            logbook = logbook.sortedWith(compareByDescending<LogEntry> { it.date }.thenByDescending { it.updatedAt }),
            deletedIds = deleted,
        )
    }

    private fun <T : DatedId> mergeById(a: List<T>, b: List<T>): List<T> {
        val map = LinkedHashMap<String, T>()
        (a + b).forEach { item ->
            val prev = map[item.id]
            if (prev == null || item.updatedAt >= prev.updatedAt) {
                map[item.id] = item
            }
        }
        return map.values.toList()
    }
}
