package life.myluck.w124.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import life.myluck.w124.ui.theme.Bg
import life.myluck.w124.ui.theme.Gold

private enum class Tab(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Outlined.Speed),
    Log("Журнал", Icons.AutoMirrored.Outlined.MenuBook),
    Nodes("Узлы", Icons.Outlined.Handyman),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: GarageViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.Home) }
    var settings by remember { mutableStateOf(false) }
    var fuelComposer by remember { mutableStateOf(false) }
    var journalComposer by remember { mutableStateOf(false) }
    var nodeId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.syncMessage) {
        val message = ui.syncMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.consumeMessage()
    }
    LaunchedEffect(ui.openJournal) {
        if (ui.openJournal) {
            journalComposer = true
            fuelComposer = false
            settings = false
            nodeId = null
        }
    }
    LaunchedEffect(ui.openFuel) {
        if (ui.openFuel) {
            fuelComposer = true
            journalComposer = false
            settings = false
            nodeId = null
            vm.markFuelOpened()
        }
    }

    Box {
        Scaffold(
            containerColor = Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                settings -> "Настройки"
                                nodeId != null -> ui.nodes.firstOrNull { it.node.id == nodeId }?.node?.title ?: "Работа"
                                else -> tab.title
                            },
                        )
                    },
                    navigationIcon = {
                        if (settings || nodeId != null) {
                            IconButton(onClick = {
                                settings = false
                                nodeId = null
                            }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                            }
                        }
                    },
                    actions = {
                        if (!settings) {
                            IconButton(onClick = { vm.sync() }, enabled = !ui.syncing) {
                                if (ui.syncing) {
                                    CircularProgressIndicator(Modifier.padding(8.dp), color = Gold, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.Sync, contentDescription = "Синхронизация")
                                }
                            }
                            IconButton(onClick = { settings = true }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                )
            },
            bottomBar = {
                if (!settings && !fuelComposer && !journalComposer && nodeId == null) {
                    NavigationBar {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.title) },
                                label = { Text(item.title) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val garage = ui.garage
                if (settings) {
                    SettingsScreen(
                        snapshot = vm.settingsSnapshot(),
                        hasToken = ui.hasToken,
                        updates = ui.updates,
                        lastSyncMessage = ui.lastSyncMessage,
                        syncBranch = ui.syncBranch,
                        onBack = { settings = false },
                        onSave = { token, owner, repo, branch ->
                            vm.saveGithub(token, owner, repo, branch)
                            settings = false
                        },
                        onCheckUpdates = { vm.checkUpdates() },
                        onInstall = { vm.installRelease(it) },
                        onAllowInstalls = { vm.allowInstalls() },
                        onOpenRelease = { vm.openReleasePage(it) },
                    )
                } else if (garage == null) {
                    Text("Загрузка журнала…", Modifier.padding(24.dp))
                } else if (nodeId != null) {
                    val view = ui.nodes.firstOrNull { it.node.id == nodeId }
                    if (view != null) {
                        NodeDetailScreen(
                            view = view,
                            odometerKm = garage.odometer.km,
                            vm = vm,
                            onBack = { nodeId = null },
                        )
                    } else {
                        Text("Задача не найдена.", Modifier.padding(24.dp))
                    }
                } else when (tab) {
                    Tab.Home -> HomeScreen(
                        garage = garage,
                        ui = ui,
                        vm = vm,
                        onFuel = { fuelComposer = true },
                        onOpenNode = { nodeId = it },
                    )
                    Tab.Log -> LogbookScreen(garage, ui, onCompose = { vm.openJournal() })
                    Tab.Nodes -> NodesScreen(ui.nodes, onOpen = { nodeId = it })
                }
            }
        }
        val garage = ui.garage
        if (fuelComposer && garage != null) {
            FuelComposerScreen(
                garage = garage,
                ui = ui,
                vm = vm,
                onClose = {
                    fuelComposer = false
                    vm.clearDraft()
                },
            )
        }
        if (journalComposer && garage != null) {
            JournalComposerScreen(
                garage = garage,
                ui = ui,
                vm = vm,
                onClose = {
                    journalComposer = false
                    vm.closeJournal()
                },
            )
        }
    }
}
