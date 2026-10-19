package life.myluck.w124.core

object SyncPolicy {
    const val DATA_BRANCH = "cursor/w124-android-logbook-c56e"

    fun missingGarageHint(branch: String): String? {
        val name = branch.trim().ifBlank { "master" }
        if (name == "master") {
            return "На ветке master нет бортжурнала — там фото тетрадки. " +
                "В настройках укажите ветку $DATA_BRANCH и нажмите «Сохранить и синхронизировать»."
        }
        return null
    }
}
