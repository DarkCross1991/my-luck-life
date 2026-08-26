package life.myluck.w124.sync

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GitHubSync(private val settings: SettingsStore) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    data class RemoteFile(val content: String, val sha: String)

    fun fetch(path: String): RemoteFile? {
        val response = execute(request(path, forGet = true).get().build())
        if (response.code == 404) return null
        if (!response.ok) throw SyncException(messageRu(response))
        val body = json.decodeFromString(ContentResponse.serializer(), response.body)
        val encoded = body.content ?: return null
        val decoded = String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
        val sha = body.sha ?: throw SyncException("GitHub не вернул sha файла $path")
        return RemoteFile(decoded, sha)
    }

    fun put(path: String, content: String, sha: String?, message: String) {
        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val payload = PutBody(
            message = message,
            content = encoded,
            branch = settings.branch,
            sha = sha,
        )
        val body = json.encodeToString(PutBody.serializer(), payload)
            .toRequestBody(JSON)
        val response = execute(request(path, forGet = false).put(body).build())
        if (!response.ok) throw SyncException(messageRu(response))
    }

    private fun request(path: String, forGet: Boolean): Request.Builder {
        if (!settings.hasToken) throw SyncException("Сначала вставьте GitHub-токен в настройках.")
        val encodedPath = path.split("/").joinToString("/") { android.net.Uri.encode(it) }
        val base = "https://api.github.com/repos/${settings.owner}/${settings.repo}/contents/$encodedPath"
        val url = if (forGet) {
            "$base?ref=${android.net.Uri.encode(settings.branch)}"
        } else {
            base
        }
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.token}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "w124-bortzhurnal")
    }

    private fun execute(request: Request): Raw {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return Raw(response.code, body)
        }
    }

    private fun messageRu(response: Raw): String {
        val api = runCatching {
            json.decodeFromString(ContentResponse.serializer(), response.body).message
        }.getOrNull()
        return when (response.code) {
            401, 403 -> "GitHub отказал в доступе. Проверьте токен (contents: read/write)."
            404 -> "Файл или репозиторий не найден."
            409 -> "Конфликт записи, повторите синхронизацию."
            else -> api ?: "GitHub HTTP ${response.code}"
        }
    }

    private data class Raw(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    @Serializable
    private data class ContentResponse(
        val sha: String? = null,
        val content: String? = null,
        val encoding: String? = null,
        val message: String? = null,
    )

    @Serializable
    private data class PutBody(
        val message: String,
        val content: String,
        val branch: String,
        val sha: String? = null,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class SyncException(message: String) : RuntimeException(message)
