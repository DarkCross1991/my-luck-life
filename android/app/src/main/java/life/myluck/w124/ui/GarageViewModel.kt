package life.myluck.w124.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
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
import life.myluck.w124.core.InboxItem
import life.myluck.w124.core.InboxStatus
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
    val lastSyncMessage: String? = null,
    val syncBranch: String = life.myluck.w124.core.SyncPolicy.DATA_BRANCH,
    val missingTools: List<MissingTool> = emptyList(),
    val inbox: List<InboxItem> = emptyList(),
    val openJournal: Boolean = false,
    val activeInquiryId: String? = null,
)

class GarageViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val repository = container.repository
    private val settings = container.settings
    private val _draft = MutableStateFlow<ReceiptDraft?>(null)
    private val _receiptBusy = MutableStateFlow(false)
    private val _openFuel = MutableStateFlow(false)
    private val _openJournal = MutableStateFlow(false)
    private val _activeInquiryId = MutableStateFlow<String?>(null)
    private val _settingsRev = MutableStateFlow(0)

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
            repository.inbox,
            _draft,
            _receiptBusy,
            _openFuel,
        ) { jobs, inbox, draft, busy, open ->
            Penta(jobs, inbox, draft, busy, open)
        },
        combine(_openJournal, _activeInquiryId, container.updates.ui, _settingsRev) { journal, active, updates, _ ->
            Triple(journal, active, updates)
        },
    ) { garage, extra, flags ->
        val state = garage.a
        val tools = garage.e?.tools.orEmpty()
        val jobs = extra.a?.jobs.orEmpty()
        val inbox = extra.b?.items.orEmpty()
        val nodes = state?.let { NodeStatus.views(it, jobs = jobs, tools = tools) }.orEmpty()
        GarageUi(
            garage = state,
            report = FuelAnalytics.report(state?.fuel.orEmpty()),
            nodes = nodes,
            analytics = garage.b,
            syncing = garage.c,
            syncMessage = garage.d,
            hasToken = settings.hasToken,
            receiptDraft = extra.c,
            receiptBusy = extra.d,
            openFuel = extra.e,
            lastTripType = settings.lastTripType,
            updates = flags.third,
            lastSyncMessage = settings.lastSyncMessage ?: garage.d,
            syncBranch = settings.branch,
            missingTools = NodeStatus.missingTools(nodes),
            inbox = inbox,
            openJournal = flags.first,
            activeInquiryId = flags.second,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GarageUi())

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
        viewModelScope.launch {
            repository.sync()
            _settingsRev.value++
        }
    }

    fun consumeMessage() {
        repository.clearMessage()
    }

    fun markFuelOpened() {
        _openFuel.value = false
    }

    fun openJournal() {
        _activeInquiryId.value = null
        _openJournal.value = true
    }

    fun closeJournal() {
        _openJournal.value = false
    }

    fun startNewInquiry() {
        _activeInquiryId.value = null
    }

    fun submitInquiry(date: String, odometer: Int, body: String) {
        val now = now()
        val logId = id()
        val inboxId = id()
        val title = "Заметка · ${life.myluck.w124.core.NodeStatus.formatKm(odometer)} км"
        viewModelScope.launch {
            repository.addInquiry(
                InboxItem(
                    id = inboxId,
                    date = date,
                    odometer = odometer,
                    body = body,
                    status = InboxStatus.PENDING,
                    logId = logId,
                    updatedAt = now,
                ),
                LogEntry(
                    id = logId,
                    date = date,
                    title = title,
                    body = body,
                    tags = listOf("заметка", "inbox"),
                    updatedAt = now,
                ),
            )
            _activeInquiryId.value = inboxId
            repeat(18) {
                delay(20_000)
                val answered = repository.inbox.value?.items
                    ?.firstOrNull { it.id == inboxId }
                    ?.status == InboxStatus.ANSWERED
                if (answered) return@launch
                if (settings.hasToken) {
                    runCatching { repository.sync("w124: проверка разбора") }
                }
            }
        }
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
        _settingsRev.value++
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

    fun openReleasePage(release: AppRelease) {
        container.updates.openReleasePage(release)
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
