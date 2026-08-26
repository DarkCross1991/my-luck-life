package life.myluck.w124.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ReceiptReader(private val context: Context) {
    data class Result(
        val text: String,
        val previewPath: String?,
    )

    suspend fun read(uri: Uri?, mime: String?, extraText: String?): Result = withContext(Dispatchers.IO) {
        val parts = mutableListOf<String>()
        extraText?.takeIf { it.isNotBlank() }?.let { parts += it }
        var preview: String? = null
        if (uri != null) {
            val copy = copy(uri)
            val kind = (mime ?: context.contentResolver.getType(uri) ?: "").lowercase()
            when {
                kind.contains("pdf") || copy.name.endsWith(".pdf", true) -> {
                    val rendered = renderPdf(copy)
                    preview = rendered.absolutePath
                    parts += ocrFile(rendered, "image/jpeg")
                }
                kind.startsWith("image/") || kind.isBlank() -> {
                    preview = copy.absolutePath
                    parts += ocrFile(copy, kind.ifBlank { "image/jpeg" })
                }
                kind.startsWith("text/") -> {
                    parts += copy.readText()
                }
                else -> {
                    runCatching { parts += ocrFile(copy, kind) }
                    runCatching { parts += copy.readText() }
                }
            }
        }
        Result(text = parts.joinToString("\n").trim(), previewPath = preview)
    }

    private fun copy(uri: Uri): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val name = "in-${System.currentTimeMillis()}"
        val out = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { input.copyTo(it) }
        } ?: error("Не удалось открыть квитанцию")
        return out
    }

    private fun renderPdf(file: File): File {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(
                    (page.width * 2).coerceAtMost(2400),
                    (page.height * 2).coerceAtMost(3200),
                    Bitmap.Config.ARGB_8888,
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                val jpeg = File(file.parentFile, "${file.name}.jpg")
                FileOutputStream(jpeg).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                bitmap.recycle()
                return jpeg
            }
        }
    }

    private suspend fun ocrFile(file: File, mime: String): String {
        val image = if (mime.contains("pdf")) {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        } else {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        }
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            client.process(image).await().text
        } finally {
            client.close()
        }
    }
}
