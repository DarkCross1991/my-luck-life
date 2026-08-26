package life.myluck.w124.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import life.myluck.w124.core.FuelAnalytics
import life.myluck.w124.core.FuelEntry
import life.myluck.w124.core.FuelReport
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.LogEntry
import life.myluck.w124.core.NodeStatus
import life.myluck.w124.core.NodeView
import life.myluck.w124.data.GarageRepository
import life.myluck.w124.sync.SettingsStore
import java.time.Instant
import java.util.UUID

data class GarageUi(
    val garage: GarageState? = null,
    val report: FuelReport = FuelAnalytics.report(emptyList()),
    val nodes: List<NodeView> = emptyList(),
    val analytics: String = "",
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    val hasToken: Boolean = false,
)

class GarageViewModel(
    private val repository: GarageRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val ui: StateFlow<GarageUi> = combine(
        repository.state,
        repository.analytics,
        repository.syncing,
        repository.syncMessage,
    ) { state, analytics, syncing, message ->
        GarageUi(
            garage = state,
            report = FuelAnalytics.report(state?.fuel.orEmpty()),
            nodes = state?.let { NodeStatus.views(it) }.orEmpty(),
            analytics = analytics,
            syncing = syncing,
            syncMessage = message,
            hasToken = settings.hasToken,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageUi())

    init {
        viewModelScope.launch { repository.load() }
    }

    fun sync() {
        viewModelScope.launch { repository.sync() }
    }

    fun consumeMessage() {
        repository.clearMessage()
    }

    fun addFuel(
        date: String,
        odometer: Int,
        liters: Double,
        full: Boolean,
        tripType: String,
        pricePerLiter: Double?,
        totalCost: Double?,
        note: String?,
    ) {
        val now = now()
        viewModelScope.launch {
            repository.addFuel(
                FuelEntry(
                    id = id(),
                    date = date,
                    odometer = odometer,
                    liters = liters,
                    full = full,
                    tripType = tripType,
                    pricePerLiter = pricePerLiter,
                    totalCost = totalCost,
                    note = note,
                    updatedAt = now,
                ),
            )
        }
    }

    fun deleteFuel(id: String) {
        viewModelScope.launch { repository.deleteFuel(id, now()) }
    }

    fun addLog(date: String, title: String, body: String, tags: List<String>) {
        val now = now()
        viewModelScope.launch {
            repository.addLog(
                LogEntry(
                    id = id(),
                    date = date,
                    title = title,
                    body = body,
                    tags = tags,
                    updatedAt = now,
                ),
            )
        }
    }

    fun updateOdometer(km: Int, date: String) {
        viewModelScope.launch { repository.updateOdometer(km, date, now()) }
    }

    fun completeNode(id: String, date: String, km: Int, note: String?) {
        viewModelScope.launch { repository.completeNode(id, date, km, note, now()) }
    }

    fun reopenNode(id: String) {
        viewModelScope.launch { repository.reopenNode(id, now()) }
    }

    fun saveGithub(token: String, owner: String, repo: String, branch: String) {
        settings.token = token
        settings.owner = owner
        settings.repo = repo
        settings.branch = branch
        sync()
    }

    fun settingsSnapshot(): GithubSettings = GithubSettings(
        token = settings.token,
        owner = settings.owner,
        repo = settings.repo,
        branch = settings.branch,
    )

    private fun now(): String = Instant.now().toString()
    private fun id(): String = UUID.randomUUID().toString()
}

data class GithubSettings(
    val token: String,
    val owner: String,
    val repo: String,
    val branch: String,
)
