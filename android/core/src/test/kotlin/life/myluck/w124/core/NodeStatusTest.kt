package life.myluck.w124.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NodeStatusTest {
    @Test
    fun seedUrgentNodesComeFirst() {
        val state = GarageJson.decodeState(
            javaClass.classLoader!!.getResource("seed-state.json")!!.readText(),
        )
        val views = NodeStatus.views(state, LocalDate.of(2026, 8, 26))
        assertEquals(NodeUrgency.URGENT, views.first().urgency)
        assertTrue(views.any { it.node.id == "accessory-belt-tensioner" && it.urgency == NodeUrgency.URGENT })
        assertTrue(views.any { it.node.id == "engine-oil" && it.urgency == NodeUrgency.OVERDUE })
    }

    @Test
    fun completingNodeClearsUrgentOpenFlag() {
        val state = GarageJson.decodeState(
            javaClass.classLoader!!.getResource("seed-state.json")!!.readText(),
        )
        val done = GarageMutations.completeNode(
            state,
            id = "spark-plugs",
            date = "2026-08-26",
            km = 322200,
            note = "NGK BPR6ES",
            now = "2026-08-26T12:00:00Z",
        )
        val view = NodeStatus.view(
            done.nodes.first { it.id == "spark-plugs" },
            done.odometer.km,
            LocalDate.of(2026, 8, 26),
        )
        assertEquals(false, view.node.open)
        assertEquals(NodeUrgency.OK, view.urgency)
    }

    @Test
    fun mergePrefersNewerFuelAndHigherOdometer() {
        val base = GarageJson.decodeState(
            javaClass.classLoader!!.getResource("seed-state.json")!!.readText(),
        )
        val local = GarageMutations.addFuel(
            base,
            FuelEntry("local", "2026-08-26", 322300, 20.0, updatedAt = "2026-08-26T10:00:00Z"),
        )
        val remote = GarageMutations.updateOdometer(base, 322800, "2026-08-26", "2026-08-26T11:00:00Z")
        val merged = GarageMerge.merge(local, remote)
        assertEquals(322800, merged.odometer.km)
        assertTrue(merged.fuel.any { it.id == "local" })
    }
}
