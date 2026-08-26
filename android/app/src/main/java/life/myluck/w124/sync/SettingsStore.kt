package life.myluck.w124.sync

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("w124_settings", Context.MODE_PRIVATE)

    var token: String
        get() = prefs.getString(TOKEN, "").orEmpty()
        set(value) { prefs.edit().putString(TOKEN, value.trim()).apply() }

    var owner: String
        get() = prefs.getString(OWNER, "DarkCross1991").orEmpty().ifBlank { "DarkCross1991" }
        set(value) { prefs.edit().putString(OWNER, value.trim()).apply() }

    var repo: String
        get() = prefs.getString(REPO, "my-luck-life").orEmpty().ifBlank { "my-luck-life" }
        set(value) { prefs.edit().putString(REPO, value.trim()).apply() }

    var branch: String
        get() = prefs.getString(BRANCH, "master").orEmpty().ifBlank { "master" }
        set(value) { prefs.edit().putString(BRANCH, value.trim()).apply() }

    val hasToken: Boolean get() = token.isNotBlank()

    private companion object {
        const val TOKEN = "token"
        const val OWNER = "owner"
        const val REPO = "repo"
        const val BRANCH = "branch"
    }
}
