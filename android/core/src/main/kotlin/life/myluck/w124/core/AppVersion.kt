package life.myluck.w124.core

data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val publishedAt: String,
    val apkApiUrl: String = "",
    val apkSize: Long = 0,
)

object AppVersion {
    private val apkName = Regex("""bortzhurnal-(\d+)-(.+)\.apk""", RegexOption.IGNORE_CASE)

    fun parseApkName(name: String): Pair<Int, String>? {
        val m = apkName.matchEntire(name.trim()) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2]
    }

    fun apkFileName(versionCode: Int, versionName: String): String =
        "bortzhurnal-$versionCode-$versionName.apk"

    fun looksLikeApk(head: ByteArray): Boolean {
        return head.size >= 2 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
    }
}
