package life.myluck.w124.core

object GarageMutations {
    fun addFuel(state: GarageState, entry: FuelEntry): GarageState {
        val odometer = bumpOdometer(state.odometer, entry.odometer, entry.date, "fuel", entry.updatedAt)
        return state.copy(
            updatedAt = entry.updatedAt,
            odometer = odometer,
            fuel = state.fuel.filterNot { it.id == entry.id } + entry,
        )
    }

    fun deleteFuel(state: GarageState, id: String, now: String): GarageState {
        return state.copy(
            updatedAt = now,
            fuel = state.fuel.map { if (it.id == id) it.copy(deleted = true, updatedAt = now) else it },
            deletedIds = (state.deletedIds + id).distinct(),
        )
    }

    fun addLog(state: GarageState, entry: LogEntry): GarageState {
        return state.copy(
            updatedAt = entry.updatedAt,
            logbook = state.logbook.filterNot { it.id == entry.id } + entry,
        )
    }

    fun updateOdometer(state: GarageState, km: Int, date: String, now: String): GarageState {
        val odometer = bumpOdometer(state.odometer, km, date, "manual", now)
        return state.copy(updatedAt = now, odometer = odometer)
    }

    fun completeNode(
        state: GarageState,
        id: String,
        date: String,
        km: Int,
        note: String?,
        now: String,
    ): GarageState {
        val odometer = bumpOdometer(state.odometer, km, date, "node", now)
        return state.copy(
            updatedAt = now,
            odometer = odometer,
            nodes = state.nodes.map { node ->
                if (node.id != id) node else node.copy(
                    open = false,
                    lastDoneAt = date,
                    lastDoneKm = km,
                    lastDoneNote = note ?: node.lastDoneNote,
                    updatedAt = now,
                )
            },
        )
    }

    fun reopenNode(state: GarageState, id: String, now: String): GarageState {
        return state.copy(
            updatedAt = now,
            nodes = state.nodes.map { node ->
                if (node.id != id) node else node.copy(open = true, updatedAt = now)
            },
        )
    }

    fun setToolHave(inventory: ToolInventory, id: String, have: Boolean, now: String): ToolInventory {
        return inventory.copy(
            updatedAt = now,
            tools = inventory.tools.map { tool ->
                if (tool.id != id) tool else tool.copy(have = have, updatedAt = now)
            },
        )
    }

    fun addInquiry(
        state: GarageState,
        inbox: InboxBook,
        item: InboxItem,
        log: LogEntry,
    ): Pair<GarageState, InboxBook> {
        val withLog = addLog(state, log)
        val odometer = bumpOdometer(withLog.odometer, item.odometer, item.date, "journal", item.updatedAt)
        val nextState = withLog.copy(updatedAt = item.updatedAt, odometer = odometer)
        val nextInbox = inbox.copy(
            updatedAt = item.updatedAt,
            items = inbox.items.filterNot { it.id == item.id } + item,
        )
        return nextState to nextInbox
    }

    fun answerInbox(inbox: InboxBook, id: String, answer: String, now: String): InboxBook {
        return inbox.copy(
            updatedAt = now,
            items = inbox.items.map { item ->
                if (item.id != id) item else item.copy(
                    status = InboxStatus.ANSWERED,
                    answer = answer,
                    updatedAt = now,
                )
            },
        )
    }

    private fun bumpOdometer(
        current: Odometer,
        km: Int,
        date: String,
        source: String,
        now: String,
    ): Odometer {
        if (km < current.km) return current
        return current.copy(km = km, recordedAt = date, source = source, updatedAt = now)
    }
}
