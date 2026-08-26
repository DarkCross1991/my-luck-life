package life.myluck.w124.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import life.myluck.w124.core.GarageJson
import life.myluck.w124.core.GarageMerge
import life.myluck.w124.core.GarageMutations
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.JobBook
import life.myluck.w124.core.ToolInventory
import life.myluck.w124.sync.GitHubSync
import life.myluck.w124.sync.SettingsStore
import life.myluck.w124.sync.SyncException

class GarageRepository(
    private val local: LocalGarageStore,
    private val github: GitHubSync,
    private val settings: SettingsStore,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<GarageState?>(null)
    private val _analytics = MutableStateFlow("")
    private val _tools = MutableStateFlow<ToolInventory?>(null)
    private val _jobs = MutableStateFlow<JobBook?>(null)
    private val _syncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<GarageState?> = _state
    val analytics: StateFlow<String> = _analytics
    val tools: StateFlow<ToolInventory?> = _tools
    val jobs: StateFlow<JobBook?> = _jobs
    val syncing: StateFlow<Boolean> = _syncing
    val syncMessage: StateFlow<String?> = _syncMessage

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = local.loadOrSeed()
            _analytics.value = local.loadAnalytics()
            _tools.value = local.loadTools()
            _jobs.value = local.loadJobs()
        }
    }

    suspend fun mutate(block: (GarageState) -> GarageState) {
        mutex.withLock {
            val current = _state.value ?: return
            val next = block(current)
            local.save(next)
            _state.value = next
        }
        if (settings.hasToken) {
            runCatching { sync(pushMessage = "w124: обновление с телефона") }
        }
    }

    suspend fun setToolHave(id: String, have: Boolean, now: String) {
        mutex.withLock {
            val current = _tools.value ?: local.loadTools()
            val next = GarageMutations.setToolHave(current, id, have, now)
            local.saveTools(next)
            _tools.value = next
        }
        if (settings.hasToken) {
            runCatching { sync(pushMessage = "w124: инструменты с телефона") }
        }
    }

    suspend fun addFuel(entry: life.myluck.w124.core.FuelEntry) {
        mutate { GarageMutations.addFuel(it, entry) }
    }

    suspend fun deleteFuel(id: String, now: String) {
        mutate { GarageMutations.deleteFuel(it, id, now) }
    }

    suspend fun addLog(entry: life.myluck.w124.core.LogEntry) {
        mutate { GarageMutations.addLog(it, entry) }
    }

    suspend fun updateOdometer(km: Int, date: String, now: String) {
        mutate { GarageMutations.updateOdometer(it, km, date, now) }
    }

    suspend fun completeNode(id: String, date: String, km: Int, note: String?, now: String) {
        mutate { GarageMutations.completeNode(it, id, date, km, note, now) }
    }

    suspend fun reopenNode(id: String, now: String) {
        mutate { GarageMutations.reopenNode(it, id, now) }
    }

    suspend fun sync(pushMessage: String = "w124: синхронизация бортжурнала") = withContext(Dispatchers.IO) {
        if (!settings.hasToken) {
            _syncMessage.value = "Токен GitHub не задан — журнал живёт только на телефоне."
            return@withContext
        }
        mutex.withLock {
            _syncing.value = true
            try {
                val localState = _state.value ?: local.loadOrSeed().also { _state.value = it }
                val localTools = _tools.value ?: local.loadTools().also { _tools.value = it }
                val localJobs = _jobs.value ?: local.loadJobs().also { _jobs.value = it }

                val pushed = syncState(localState, pushMessage) or
                    syncTools(localTools) or
                    syncJobs(localJobs)
                syncAnalytics()

                _syncMessage.value = if (pushed) {
                    "Синхронизировано с GitHub (${settings.branch})."
                } else {
                    "Уже актуально с GitHub."
                }
            } catch (e: SyncException) {
                _syncMessage.value = e.message
            } catch (e: Exception) {
                _syncMessage.value = e.message ?: "Синхронизация не удалась"
            } finally {
                _syncing.value = false
            }
        }
    }

    private fun syncState(localState: GarageState, pushMessage: String): Boolean {
        val remote = github.fetch("data/w124/state.json")
        val merged = if (remote == null) {
            localState
        } else {
            GarageMerge.merge(localState, GarageJson.decodeState(remote.content))
        }
        local.save(merged)
        _state.value = merged
        val encoded = GarageJson.encodeState(merged)
        val needPush = remote == null || remote.content != encoded
        if (needPush) {
            github.put("data/w124/state.json", encoded, remote?.sha, pushMessage)
        }
        return needPush
    }

    private fun syncTools(localTools: ToolInventory): Boolean {
        val remote = github.fetch("data/w124/tools.json")
        val merged = if (remote == null) {
            localTools
        } else {
            GarageMerge.mergeTools(localTools, GarageJson.decodeTools(remote.content))
        }
        local.saveTools(merged)
        _tools.value = merged
        val encoded = GarageJson.encodeTools(merged)
        val needPush = remote == null || remote.content != encoded
        if (needPush) {
            github.put("data/w124/tools.json", encoded, remote?.sha, "w124: список инструментов")
        }
        return needPush
    }

    private fun syncJobs(localJobs: JobBook): Boolean {
        val remote = github.fetch("data/w124/jobs.json")
        val merged = if (remote == null) {
            localJobs
        } else {
            GarageMerge.mergeJobs(localJobs, GarageJson.decodeJobs(remote.content))
        }
        local.saveJobs(merged)
        _jobs.value = merged
        val encoded = GarageJson.encodeJobs(merged)
        val needPush = remote == null || remote.content != encoded
        if (needPush) {
            github.put("data/w124/jobs.json", encoded, remote?.sha, "w124: планы работ")
        }
        return needPush
    }

    private fun syncAnalytics() {
        val analytics = github.fetch("data/w124/analytics.md")
        if (analytics != null) {
            local.saveAnalytics(analytics.content)
            _analytics.value = analytics.content
        }
    }

    fun clearMessage() {
        _syncMessage.value = null
    }
}
