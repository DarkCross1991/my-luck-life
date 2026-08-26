package life.myluck.w124.data

import android.content.Context
import life.myluck.w124.share.ReceiptReader
import life.myluck.w124.share.ShareBus
import life.myluck.w124.sync.GitHubSync
import life.myluck.w124.sync.SettingsStore
import life.myluck.w124.update.UpdateRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsStore(appContext)
    val local = LocalGarageStore(appContext)
    val github = GitHubSync(settings)
    val repository = GarageRepository(local, github, settings)
    val shareBus = ShareBus()
    val receipts = ReceiptReader(appContext)
    val updates = UpdateRepository(appContext, settings)
}
