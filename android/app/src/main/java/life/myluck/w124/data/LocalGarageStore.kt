package life.myluck.w124.data

import android.content.Context
import life.myluck.w124.core.GarageJson
import life.myluck.w124.core.GarageState
import java.io.File

class LocalGarageStore(private val context: Context) {
    private val stateFile get() = File(context.filesDir, "state.json")
    private val analyticsFile get() = File(context.filesDir, "analytics.md")

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

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
