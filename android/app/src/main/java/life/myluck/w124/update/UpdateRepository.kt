package life.myluck.w124.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
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
            _ui.value = _ui.value.copy(
                checking = false,
                latest = latest,
                history = releases.sortedByDescending { it.versionCode },
                canInstallPackages = canInstall(),
                message = when {
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
            _ui.value = _ui.value.copy(
                downloading = false,
                message = e.message ?: "Не удалось скачать обновление",
            )
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

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
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
                val parsedName = AppVersion.parseApkName(asset.name)
                val versionCode = parsedName?.first ?: return@mapNotNull null
                val versionName = parsedName.second
                AppRelease(
                    versionCode = versionCode,
                    versionName = versionName,
                    tag = rel.tag_name,
                    notes = rel.body.orEmpty().trim(),
                    apkUrl = asset.browser_download_url,
                    apkName = asset.name,
                    publishedAt = rel.published_at.orEmpty(),
                )
            }
        }
    }

    private fun download(release: AppRelease): File {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val out = File(dir, release.apkName)
        if (out.exists() && out.length() > 10_000) return out
        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", "w124-bortzhurnal")
            .header("Accept", "application/octet-stream")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Скачивание APK HTTP ${response.code}")
            val body = response.body ?: error("Пустой APK")
            out.outputStream().use { body.byteStream().copyTo(it) }
        }
        if (out.length() < 10_000) {
            out.delete()
            error("Файл обновления слишком маленький")
        }
        dir.listFiles()
            ?.filter { it.extension == "apk" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }
        return out
    }

    private fun install(apk: File) {
        if (!canInstall()) {
            openInstallPermission()
            error("Разрешите установку из этого приложения и нажмите ещё раз.")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun canInstall(): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
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
    val browser_download_url: String,
    val size: Long = 0,
)
