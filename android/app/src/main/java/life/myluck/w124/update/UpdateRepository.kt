package life.myluck.w124.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import life.myluck.w124.core.AppRelease
import life.myluck.w124.core.AppVersion
import life.myluck.w124.sync.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateUi(
    val currentCode: Int,
    val currentName: String,
    val latest: AppRelease? = null,
    val history: List<AppRelease> = emptyList(),
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val message: String? = null,
    val canInstallPackages: Boolean = true,
) {
    val updateAvailable: Boolean
        get() = latest != null && latest.versionCode > currentCode
}

class UpdateRepository(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val currentCode = run {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    }
    private val currentName = run {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    }

    private val _ui = MutableStateFlow(
        UpdateUi(currentCode = currentCode, currentName = currentName),
    )
    val ui: StateFlow<UpdateUi> = _ui.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _ui.value = _ui.value.copy(checking = true, message = null)
        try {
            val releases = fetchReleases()
            val latest = releases.maxByOrNull { it.versionCode }
            val installError = settings.lastInstallError
            _ui.value = _ui.value.copy(
                checking = false,
                latest = latest,
                history = releases.sortedByDescending { it.versionCode },
                canInstallPackages = canInstall(),
                message = when {
                    installError != null -> installError
                    latest == null -> "Релизов ещё нет. После сборки CI появится v$currentName."
                    latest.versionCode > currentCode -> "Доступна ${latest.versionName}"
                    else -> "Установлена актуальная версия $currentName"
                },
            )
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(
                checking = false,
                message = e.message ?: "Не удалось проверить обновления",
            )
        }
    }

    suspend fun downloadAndInstall(release: AppRelease) = withContext(Dispatchers.IO) {
        _ui.value = _ui.value.copy(downloading = true, message = "Скачиваю ${release.versionName}…")
        try {
            val apk = download(release)
            _ui.value = _ui.value.copy(downloading = false, message = "Подтвердите установку ${release.versionName}")
            install(apk)
        } catch (e: Exception) {
            val text = e.message ?: "Не удалось обновить"
            settings.lastInstallError = text
            _ui.value = _ui.value.copy(downloading = false, message = text)
        }
    }

    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openReleasePage(release: AppRelease) {
        val url = "https://github.com/${settings.owner}/${settings.repo}/releases/tag/${release.tag}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
        settings.lastInstallError = null
    }

    fun reportInstall(status: Int, systemMessage: String?) {
        val text = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "Установлено."
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Установку отменили."
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            -> SIGNATURE_HINT
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Не хватает места для APK."
            PackageInstaller.STATUS_FAILURE_INVALID -> "Скачанный файл битый. Нажмите обновить ещё раз."
            else -> systemMessage?.takeIf { it.isNotBlank() }?.let { "Установщик: $it" } ?: SIGNATURE_HINT
        }
        settings.lastInstallError = text.takeIf { status != PackageInstaller.STATUS_SUCCESS }
        _ui.value = _ui.value.copy(downloading = false, message = text)
    }

    private fun fetchReleases(): List<AppRelease> {
        val url = "https://api.github.com/repos/${settings.owner}/${settings.repo}/releases?per_page=20"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "w124-bortzhurnal")
        if (settings.hasToken) {
            builder.header("Authorization", "Bearer ${settings.token}")
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("GitHub Releases HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val parsed = json.decodeFromString(ListSerializer(GhRelease.serializer()), body)
            return parsed.mapNotNull { rel ->
                if (rel.draft) return@mapNotNull null
                val asset = rel.assets.firstOrNull { it.name.endsWith(".apk", true) } ?: return@mapNotNull null
                val parsedName = AppVersion.parseApkName(asset.name) ?: return@mapNotNull null
                AppRelease(
                    versionCode = parsedName.first,
                    versionName = parsedName.second,
                    tag = rel.tag_name,
                    notes = rel.body.orEmpty().trim(),
                    apkUrl = asset.browser_download_url,
                    apkApiUrl = asset.url,
                    apkName = asset.name,
                    publishedAt = rel.published_at.orEmpty(),
                    apkSize = asset.size,
                )
            }
        }
    }

    private fun download(release: AppRelease): File {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val out = File(dir, release.apkName)
        if (isValidApk(out, release.apkSize)) return out
        out.delete()
        val urls = listOfNotNull(
            release.apkApiUrl.takeIf { it.isNotBlank() },
            release.apkUrl,
        )
        var lastError = "Не удалось скачать APK"
        for (start in urls) {
            runCatching { fetchToFile(start, out, release.apkSize) }
                .onSuccess { return out }
                .onFailure { lastError = it.message ?: lastError }
            out.delete()
        }
        error(lastError)
    }

    private fun fetchToFile(startUrl: String, out: File, expectedSize: Long) {
        var url = startUrl
        var sendAuth = url.contains("api.github.com")
        repeat(8) {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", "w124-bortzhurnal")
                .header("Accept", "application/octet-stream")
            if (sendAuth && settings.hasToken) {
                builder.header("Authorization", "Bearer ${settings.token}")
            }
            client.newCall(builder.build()).execute().use { response ->
                val location = response.header("Location")
                if (response.code in 300..399 && !location.isNullOrBlank()) {
                    url = if (location.startsWith("http")) location else response.request.url.resolve(location)?.toString() ?: location
                    sendAuth = url.contains("api.github.com")
                    return@use
                }
                if (!response.isSuccessful) error("Скачивание APK HTTP ${response.code}")
                val body = response.body ?: error("Пустой APK")
                out.outputStream().use { body.byteStream().copyTo(it) }
            }
            if (out.exists() && out.length() > 0) {
                if (!isValidApk(out, expectedSize)) {
                    out.delete()
                    error("Скачался не APK (похоже, страница ошибки). Повторите с токеном в настройках.")
                }
                dirCleanup(out.parentFile)
                return
            }
        }
        error("Слишком много редиректов при скачивании APK")
    }

    private fun dirCleanup(dir: File?) {
        dir?.listFiles()
            ?.filter { it.extension == "apk" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }
    }

    private fun isValidApk(file: File, expectedSize: Long): Boolean {
        if (!file.exists()) return false
        if (file.length() < 100_000) return false
        if (expectedSize > 0 && file.length() != expectedSize) return false
        val head = ByteArray(8)
        val read = file.inputStream().use { it.read(head) }
        return read >= 2 && AppVersion.looksLikeApk(head)
    }

    private fun install(apk: File) {
        if (!canInstall()) {
            openInstallPermission()
            error("Разрешите установку из этого приложения и нажмите ещё раз.")
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("apk", 0, apk.length()).use { dest ->
                apk.inputStream().use { it.copyTo(dest) }
                session.fsync(dest)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val callback = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION
            }
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
        }
    }

    private fun canInstall(): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    companion object {
        const val SIGNATURE_HINT =
            "Android не ставит поверх: старые сборки были с одноразовой подписью CI. " +
                "Синхронизируйте журнал, удалите Бортжурнал и поставьте 0.4.0 с GitHub Releases. " +
                "Данные в git. После этого обновления пойдут сами."
    }
}

@Serializable
private data class GhRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String,
    val url: String,
    val browser_download_url: String,
    val size: Long = 0,
)
