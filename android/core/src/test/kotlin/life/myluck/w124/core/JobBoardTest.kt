package life.myluck.w124.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JobBoardTest {
    private val today = LocalDate.of(2026, 8, 26)

    private fun load(): Triple<GarageState, JobBook, ToolInventory> {
        val cl = javaClass.classLoader!!
        val state = GarageJson.decodeState(cl.getResource("seed-state.json")!!.readText())
        val jobs = GarageJson.decodeJobs(cl.getResource("jobs.json")!!.readText())
        val tools = GarageJson.decodeTools(cl.getResource("tools.json")!!.readText())
        return Triple(state, jobs, tools)
    }

    @Test
    fun urgentTasksAreClickablePlansWithStepsAndTools() {
        val (state, jobs, tools) = load()
        val views = NodeStatus.views(state, today, jobs.jobs, tools.tools)
        val urgent = NodeStatus.urgentWork(views)
        assertTrue(urgent.size >= 5)
        urgent.forEach { view ->
            val job = view.job
            assertTrue("${view.node.id} must have a job plan", job != null)
            assertTrue("${view.node.id} must say what to do", job!!.what.isNotBlank())
            assertTrue("${view.node.id} must have steps", job.steps.size >= 2)
            assertTrue("${view.node.id} must list tools", view.required.isNotEmpty())
            assertTrue("${view.node.id} must have hanging text", view.hangingRu.isNotBlank())
        }
        val belt = views.first { it.node.id == "accessory-belt-tensioner" }
        assertEquals(NodeUrgency.URGENT, belt.urgency)
        assertEquals(25, belt.hangingDays)
        assertTrue(belt.hangingRu.contains("25"))
        assertTrue(belt.required.any { it.id == "screwdriver-long" && !it.have })
        assertTrue(belt.required.any { it.id == "flashlight" && !it.have })
    }

    @Test
    fun wiresInTrunkCountAsHaveAndStayGreen() {
        val (state, jobs, tools) = load()
        val views = NodeStatus.views(state, today, jobs.jobs, tools.tools)
        val wires = views.first { it.node.id == "ignition-wires" }
        val part = wires.required.first { it.id == "ignition-wires-new" }
        assertTrue(part.have)
        assertFalse(part.isTool)
        assertTrue(wires.missingParts.isEmpty())
    }

    @Test
    fun homeChecklistOnlyMissingToolsForUrgentWork() {
        val (state, jobs, tools) = load()
        val views = NodeStatus.views(state, today, jobs.jobs, tools.tools)
        val missing = NodeStatus.missingTools(views)
        assertTrue(missing.any { it.tool.id == "flashlight" })
        assertTrue(missing.any { it.tool.id == "spark-socket-16" })
        assertFalse(missing.any { it.tool.id == "phone-camera" })
        assertFalse(missing.any { it.tool.id == "spark-plugs-new" })
        val flashlight = missing.first { it.tool.id == "flashlight" }
        assertTrue(flashlight.neededFor.size >= 2)
    }

    @Test
    fun markingToolHaveDropsItFromChecklist() {
        val (state, jobs, tools) = load()
        val updated = GarageMutations.setToolHave(tools, "flashlight", true, "2026-08-26T15:00:00Z")
        val views = NodeStatus.views(state, today, jobs.jobs, updated.tools)
        val missing = NodeStatus.missingTools(views)
        assertFalse(missing.any { it.tool.id == "flashlight" })
        val belt = views.first { it.node.id == "accessory-belt-tensioner" }
        assertTrue(belt.required.first { it.id == "flashlight" }.have)
    }

    @Test
    fun mergeKeepsNewerHaveFlag() {
        val (_, _, seed) = load()
        val local = GarageMutations.setToolHave(seed, "ratchet", true, "2026-08-26T18:00:00Z")
        val merged = GarageMerge.mergeTools(local, seed)
        assertTrue(merged.tools.first { it.id == "ratchet" }.have)
    }

    @Test
    fun ovpRelayPartNumberIsRecorded() {
        val (state, jobs, tools) = load()
        val ovp = state.nodes.first { it.id == "ovp-relay" }
        assertTrue(ovp.lastDoneNote!!.contains("A2015403245"))
        val part = tools.tools.first { it.id == "ovp-relay" }
        assertTrue(part.have)
        assertFalse(part.isTool)
        assertTrue(part.note!!.contains("A2015403245"))
        val log = state.logbook.first { it.id == "log-ovp-2026-08-27" }
        assertTrue(log.body.contains("A2015403245"))
        assertTrue(jobs.jobs.any { it.nodeId == "ovp-relay" })
        assertFalse(ovp.open)
        assertTrue(state.logbook.any { it.id == "log-ovp-rpm-2026-08-27" && it.body.contains("5000") })
    }

    @Test
    fun diagnosticBoardSplitsRunningSymptoms() {
        val (state, jobs, tools) = load()
        assertTrue(state.deletedIds.contains("running-diagnosis"))
        assertFalse(state.nodes.any { it.id == "running-diagnosis" })
        val ids = listOf("fuel-smell", "idle-valve", "rpm-drive", "abs")
        val views = NodeStatus.views(state, today, jobs.jobs, tools.tools)
        ids.forEach { id ->
            val node = state.nodes.first { it.id == id }
            assertEquals("urgent", node.priority)
            assertTrue(node.open)
            val job = jobs.jobs.first { it.nodeId == id }
            assertTrue(job.what.isNotBlank())
            assertTrue(job.steps.size >= 2)
            assertTrue(job.toolIds.isNotEmpty())
            val view = views.first { it.node.id == id }
            assertEquals(NodeUrgency.URGENT, view.urgency)
            assertTrue(view.required.isNotEmpty())
        }
        assertFalse(state.nodes.first { it.id == "ovp-relay" }.open)
        assertEquals(NodeUrgency.OK, views.first { it.node.id == "ovp-relay" }.urgency)
    }

    @Test
    fun daysRuRussianPlural() {
        assertEquals("1 день", NodeStatus.daysRu(1))
        assertEquals("2 дня", NodeStatus.daysRu(2))
        assertEquals("5 дней", NodeStatus.daysRu(5))
        assertEquals("21 день", NodeStatus.daysRu(21))
        assertEquals("25 дней", NodeStatus.daysRu(25))
    }
}
