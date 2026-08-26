package life.myluck.w124.data

import android.content.Context
import life.myluck.w124.core.GarageJson
import life.myluck.w124.core.GarageMerge
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.JobBook
import life.myluck.w124.core.ToolInventory
import java.io.File

class LocalGarageStore(private val context: Context) {
    private val stateFile get() = File(context.filesDir, "state.json")
    private val analyticsFile get() = File(context.filesDir, "analytics.md")
    private val toolsFile get() = File(context.filesDir, "tools.json")
    private val jobsFile get() = File(context.filesDir, "jobs.json")

    fun loadOrSeed(): GarageState {
        if (stateFile.exists()) {
            return GarageJson.decodeState(stateFile.readText())
        }
        val seeded = GarageJson.decodeState(readAsset("state.json"))
        save(seeded)
        if (!analyticsFile.exists()) {
            saveAnalytics(readAsset("analytics.md"))
        }
        return seeded
    }

    fun save(state: GarageState) {
        stateFile.writeText(GarageJson.encodeState(state))
    }

    fun loadAnalytics(): String {
        if (analyticsFile.exists()) return analyticsFile.readText()
        return runCatching { readAsset("analytics.md") }.getOrDefault("")
    }

    fun saveAnalytics(text: String) {
        analyticsFile.writeText(text)
    }

    fun loadTools(): ToolInventory {
        val seed = GarageJson.decodeTools(readAsset("tools.json"))
        if (!toolsFile.exists()) {
            saveTools(seed)
            return seed
        }
        val disk = GarageJson.decodeTools(toolsFile.readText())
        val merged = GarageMerge.mergeTools(disk, seed)
        if (merged != disk) saveTools(merged)
        return merged
    }

    fun saveTools(inventory: ToolInventory) {
        toolsFile.writeText(GarageJson.encodeTools(inventory))
    }

    fun loadJobs(): JobBook {
        val seed = GarageJson.decodeJobs(readAsset("jobs.json"))
        if (!jobsFile.exists()) {
            saveJobs(seed)
            return seed
        }
        val disk = GarageJson.decodeJobs(jobsFile.readText())
        val merged = GarageMerge.mergeJobs(disk, seed)
        if (merged != disk) saveJobs(merged)
        return merged
    }

    fun saveJobs(book: JobBook) {
        jobsFile.writeText(GarageJson.encodeJobs(book))
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
