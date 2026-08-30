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
        val ids = listOf("idle-valve", "rpm-drive", "abs")
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
        assertFalse(state.nodes.first { it.id == "fuel-smell" }.open)
        assertEquals(NodeUrgency.OK, views.first { it.node.id == "fuel-smell" }.urgency)
        assertFalse(state.nodes.first { it.id == "ovp-relay" }.open)
        assertEquals(NodeUrgency.OK, views.first { it.node.id == "ovp-relay" }.urgency)
        assertEquals("Педаль в Drive", state.nodes.first { it.id == "rpm-drive" }.title)
        assertTrue(state.logbook.any { it.id == "log-hot-drive-2026-08-28" })
        assertTrue(state.logbook.any { it.id == "log-exhaust-smell-2026-08-28" })
        val beltJob = jobs.jobs.first { it.nodeId == "accessory-belt-tensioner" }
        assertTrue(beltJob.what.contains("завален"))
        assertTrue(beltJob.steps.any { it.contains("Febi 06418") })
        assertTrue(beltJob.steps.any { it.contains("6PK1885") })
    }

    @Test
    fun airAndCabinFiltersAreRecorded() {
        val (state, jobs, tools) = load()
        val air = tools.tools.first { it.id == "air-filter" }
        assertFalse(air.have)
        assertFalse(air.isTool)
        assertTrue(air.note!!.contains("LX 61"))
        assertTrue(air.note!!.contains("A003 094 38 04"))
        val cabin = tools.tools.first { it.id == "cabin-filter" }
        assertFalse(cabin.have)
        assertTrue(cabin.note!!.contains("нет"))
        assertTrue(cabin.note!!.contains("A124 830 00 18"))
        assertTrue(state.logbook.any { it.id == "log-filters-2026-08-29" })
        val job = jobs.jobs.first { it.nodeId == "air-filter" }
        assertTrue(job.steps.size >= 2)
        assertTrue(job.toolIds.contains("air-filter"))
        assertTrue(job.steps.any { it.contains("не покупать") })
        val node = state.nodes.first { it.id == "air-filter" }
        assertTrue(node.open)
        val views = NodeStatus.views(state, today, jobs.jobs, tools.tools)
        assertEquals(NodeUrgency.OVERDUE, views.first { it.node.id == "air-filter" }.urgency)
    }

    @Test
    fun tpsTestIsRecordedAsOhms() {
        val (state, jobs, tools) = load()
        val log = state.logbook.first { it.id == "log-tps-2026-08-30" }
        assertTrue(log.body.contains("1,3"))
        assertTrue(log.body.contains("кОм"))
        assertTrue(log.body.contains("A000 074 02 36"))
        val pot = tools.tools.first { it.id == "airflow-pot" }
        assertFalse(pot.have)
        assertTrue(pot.note!!.contains("F 026 T03 021"))
        assertTrue(pot.note!!.contains("006 153 86 28"))
        val idleJob = jobs.jobs.first { it.nodeId == "idle-valve" }
        assertTrue(idleJob.toolIds.contains("airflow-pot"))
        assertTrue(idleJob.steps.any { it.contains("1,3") })
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
