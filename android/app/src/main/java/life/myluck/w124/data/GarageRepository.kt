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
    private val _syncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<GarageState?> = _state
    val analytics: StateFlow<String> = _analytics
    val syncing: StateFlow<Boolean> = _syncing
    val syncMessage: StateFlow<String?> = _syncMessage

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _state.value = local.loadOrSeed()
            _analytics.value = local.loadAnalytics()
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
                val remote = github.fetch("data/w124/state.json")
                val merged = if (remote == null) {
                    localState
                } else {
                    GarageMerge.merge(localState, GarageJson.decodeState(remote.content))
                }
                local.save(merged)
                _state.value = merged
                val needPush = remote == null || remote.content != GarageJson.encodeState(merged)
                if (needPush) {
                    github.put(
                        path = "data/w124/state.json",
                        content = GarageJson.encodeState(merged),
                        sha = remote?.sha,
                        message = pushMessage,
                    )
                }
                val analytics = github.fetch("data/w124/analytics.md")
                if (analytics != null) {
                    local.saveAnalytics(analytics.content)
                    _analytics.value = analytics.content
                }
                _syncMessage.value = if (needPush) {
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

    fun clearMessage() {
        _syncMessage.value = null
    }
}
