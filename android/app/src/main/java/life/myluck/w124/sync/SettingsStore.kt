package life.myluck.w124.sync

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("w124_settings", Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean(BRANCH_MIGRATED, false)) {
            val stored = prefs.getString(BRANCH, null)
            val edit = prefs.edit().putBoolean(BRANCH_MIGRATED, true)
            if (stored.isNullOrBlank() || stored == "master") {
                edit.putString(BRANCH, life.myluck.w124.core.SyncPolicy.DATA_BRANCH)
            }
            edit.commit()
        }
    }

    var token: String
        get() = prefs.getString(TOKEN, "").orEmpty()
        set(value) { prefs.edit().putString(TOKEN, value.trim()).commit() }

    var owner: String
        get() = prefs.getString(OWNER, "DarkCross1991").orEmpty().ifBlank { "DarkCross1991" }
        set(value) { prefs.edit().putString(OWNER, value.trim()).commit() }

    var repo: String
        get() = prefs.getString(REPO, "my-luck-life").orEmpty().ifBlank { "my-luck-life" }
        set(value) { prefs.edit().putString(REPO, value.trim()).commit() }

    var branch: String
        get() = prefs.getString(BRANCH, life.myluck.w124.core.SyncPolicy.DATA_BRANCH)
            .orEmpty()
            .ifBlank { life.myluck.w124.core.SyncPolicy.DATA_BRANCH }
        set(value) { prefs.edit().putString(BRANCH, value.trim()).commit() }

    var lastTripType: String
        get() = prefs.getString(TRIP, life.myluck.w124.core.TripType.MIXED).orEmpty()
            .ifBlank { life.myluck.w124.core.TripType.MIXED }
        set(value) { prefs.edit().putString(TRIP, value).apply() }

    var lastInstallError: String?
        get() = prefs.getString(INSTALL_ERROR, null)
        set(value) {
            prefs.edit().putString(INSTALL_ERROR, value).apply()
        }

    var lastSyncMessage: String?
        get() = prefs.getString(SYNC_MESSAGE, null)
        set(value) {
            prefs.edit().putString(SYNC_MESSAGE, value).commit()
        }

    val hasToken: Boolean get() = token.isNotBlank()

    private companion object {
        const val TOKEN = "token"
        const val OWNER = "owner"
        const val REPO = "repo"
        const val BRANCH = "branch"
        const val BRANCH_MIGRATED = "branch_migrated_v6"
        const val TRIP = "trip"
        const val INSTALL_ERROR = "install_error"
        const val SYNC_MESSAGE = "sync_message"
    }
}
