package life.myluck.w124.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import life.myluck.w124.core.AppRelease
import life.myluck.w124.core.FuelAnalytics
import life.myluck.w124.core.FuelEntry
import life.myluck.w124.core.FuelReport
import life.myluck.w124.core.GarageState
import life.myluck.w124.core.LogEntry
import life.myluck.w124.core.MissingTool
import life.myluck.w124.core.NodeStatus
import life.myluck.w124.core.NodeView
import life.myluck.w124.core.ParsedReceipt
import life.myluck.w124.core.ReceiptParser
import life.myluck.w124.data.AppContainer
import life.myluck.w124.update.UpdateUi
import java.time.Instant
import java.util.UUID

data class ReceiptDraft(
    val text: String = "",
    val parsed: ParsedReceipt = ParsedReceipt(),
    val previewPath: String? = null,
)

data class GarageUi(
    val garage: GarageState? = null,
    val report: FuelReport = FuelAnalytics.report(emptyList()),
    val nodes: List<NodeView> = emptyList(),
    val analytics: String = "",
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    val hasToken: Boolean = false,
    val receiptDraft: ReceiptDraft? = null,
    val receiptBusy: Boolean = false,
    val openFuel: Boolean = false,
    val lastTripType: String = life.myluck.w124.core.TripType.MIXED,
    val updates: UpdateUi? = null,
    val missingTools: List<MissingTool> = emptyList(),
)

class GarageViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val repository = container.repository
    private val settings = container.settings
    private val _draft = MutableStateFlow<ReceiptDraft?>(null)
    private val _receiptBusy = MutableStateFlow(false)
    private val _openFuel = MutableStateFlow(false)

    val ui: StateFlow<GarageUi> = combine(
        combine(
            repository.state,
            repository.analytics,
            repository.syncing,
            repository.syncMessage,
            repository.tools,
        ) { state, analytics, syncing, message, tools ->
            Penta(state, analytics, syncing, message, tools)
        },
        combine(
            repository.jobs,
            _draft,
            _receiptBusy,
            _openFuel,
            container.updates.ui,
        ) { jobs, draft, busy, open, updates ->
            Penta(jobs, draft, busy, open, updates)
        },
    ) { garage, extra ->
        val state = garage.a
        val tools = garage.e?.tools.orEmpty()
        val jobs = extra.a?.jobs.orEmpty()
        val nodes = state?.let { NodeStatus.views(it, jobs = jobs, tools = tools) }.orEmpty()
        GarageUi(
            garage = state,
            report = FuelAnalytics.report(state?.fuel.orEmpty()),
            nodes = nodes,
            analytics = garage.b,
            syncing = garage.c,
            syncMessage = garage.d,
            hasToken = settings.hasToken,
            receiptDraft = extra.b,
            receiptBusy = extra.c,
            openFuel = extra.d,
            lastTripType = settings.lastTripType,
            updates = extra.e,
            missingTools = NodeStatus.missingTools(nodes),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageUi())

    init {
        viewModelScope.launch { repository.load() }
        viewModelScope.launch { container.updates.refresh() }
        viewModelScope.launch {
            container.shareBus.incoming.collect { share ->
                if (share == null) return@collect
                importShare(share.uri, share.mime, share.text)
                container.shareBus.consume()
            }
        }
    }

    fun sync() {
        viewModelScope.launch { repository.sync() }
    }

    fun consumeMessage() {
        repository.clearMessage()
    }

    fun markFuelOpened() {
        _openFuel.value = false
    }

    fun clearDraft() {
        _draft.value = null
    }

    fun importReceipt(context: Context, uri: Uri, mime: String?) {
        viewModelScope.launch { importShare(uri, mime, null) }
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
        station: String? = null,
        receiptText: String? = null,
    ) {
        val now = now()
        settings.lastTripType = tripType
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
                    station = station,
                    note = note,
                    receiptText = receiptText,
                    source = if (receiptText.isNullOrBlank()) "manual" else "receipt",
                    updatedAt = now,
                ),
            )
            _draft.value = null
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

    fun setToolHave(id: String, have: Boolean) {
        viewModelScope.launch { repository.setToolHave(id, have, now()) }
    }

    fun saveGithub(token: String, owner: String, repo: String, branch: String) {
        settings.token = token
        settings.owner = owner
        settings.repo = repo
        settings.branch = branch
        sync()
        viewModelScope.launch { container.updates.refresh() }
    }

    fun checkUpdates() {
        viewModelScope.launch { container.updates.refresh() }
    }

    fun installRelease(release: AppRelease) {
        viewModelScope.launch { container.updates.downloadAndInstall(release) }
    }

    fun allowInstalls() {
        container.updates.openInstallPermission()
    }

    fun consumeUpdateMessage() {
        container.updates.consumeMessage()
    }

    fun settingsSnapshot(): GithubSettings = GithubSettings(
        token = settings.token,
        owner = settings.owner,
        repo = settings.repo,
        branch = settings.branch,
    )

    private suspend fun importShare(uri: Uri?, mime: String?, text: String?) {
        _receiptBusy.value = true
        _openFuel.value = true
        try {
            val result = container.receipts.read(uri, mime, text)
            val parsed = ReceiptParser.parse(result.text)
            _draft.value = ReceiptDraft(
                text = result.text,
                parsed = parsed,
                previewPath = result.previewPath,
            )
        } catch (e: Exception) {
            _draft.value = ReceiptDraft(
                text = e.message ?: "Не удалось прочитать квитанцию",
                parsed = ParsedReceipt(),
                previewPath = null,
            )
        } finally {
            _receiptBusy.value = false
        }
    }

    private fun now(): String = Instant.now().toString()
    private fun id(): String = UUID.randomUUID().toString()
}

private data class Penta<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

data class GithubSettings(
    val token: String,
    val owner: String,
    val repo: String,
    val branch: String,
)
