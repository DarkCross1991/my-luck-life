package life.myluck.w124.data

import android.content.Context
import life.myluck.w124.sync.GitHubSync
import life.myluck.w124.sync.SettingsStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsStore(appContext)
    val local = LocalGarageStore(appContext)
    val github = GitHubSync(settings)
    val repository = GarageRepository(local, github, settings)
}
