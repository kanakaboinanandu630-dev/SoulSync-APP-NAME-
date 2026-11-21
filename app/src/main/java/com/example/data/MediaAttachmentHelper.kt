package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

data class PreparedMediaPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val displayName: String
)

/**
 * Bounded LRU cache for decoded decrypted ImageBitmaps so chat & gallery scrolling
 * never re-decodes bitmaps or stalls the UI thread.
 */
object ImageBitmapCache {
    private val cache = LruCache<String, ImageBitmap>(32)

    fun get(key: String): ImageBitmap? = cache.get(key)

    suspend fun getOrDecodeBytes(key: String, bytes: ByteArray): ImageBitmap? {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()?.also { bmp ->
                cache.put(key, bmp)
            }
        }
    }

    suspend fun getOrDecodeBase64(key: String, base64: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()?.also { bmp ->
                cache.put(key, bmp)
            }
        }
    }
}

/**
 * Handles zero-permission Photo Picker image compression, SAF file reading,
 * and real MediaRecorder / MediaPlayer voice note capture & playback.
 */
object MediaAttachmentHelper {

    suspend fun compressPickedImageAsync(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        maxBytes: Int = 200_000
    ): Result<PreparedMediaPayload> = withContext(Dispatchers.IO) {
        compressPickedImage(context, uri, maxDimension, maxBytes)
    }

    suspend fun compressAvatarToBase64Async(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            compressAvatarToBase64(context, uri)
        }

    suspend fun readSmallDocumentAsync(
        context: Context,
        uri: Uri,
        maxBytes: Int = 220_000
    ): Result<PreparedMediaPayload> = withContext(Dispatchers.IO) {
        readSmallDocument(context, uri, maxBytes)
    }

    fun compressPickedImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        maxBytes: Int = 200_000
    ): Result<PreparedMediaPayload> = runCatching {
        val rawStream = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open selected image.")
        val original = rawStream.use { BitmapFactory.decodeStream(it) }
            ?: error("Unsupported image format.")

        val scale = max(original.width, original.height).toFloat() / maxDimension.toFloat()
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width / scale).toInt().coerceAtLeast(1),
                (original.height / scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            original
        }

        var quality = 82
        val baos = ByteArrayOutputStream()
        do {
            baos.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            quality -= 12
        } while (baos.size() > maxBytes && quality >= 28)

        if (scaled !== original) scaled.recycle()
        original.recycle()

        val bytes = baos.toByteArray()
        require(bytes.size <= maxBytes) { "Image is still too large after compression." }
        PreparedMediaPayload(
            bytes = bytes,
            mimeType = "image/jpeg",
            displayName = queryDisplayName(context, uri) ?: "photo.jpg"
        )
    }

    fun compressAvatarToBase64(context: Context, uri: Uri): Result<String> = runCatching {
        val payload = compressPickedImage(context, uri, maxDimension = 220, maxBytes = 60_000).getOrThrow()
        Base64.encodeToString(payload.bytes, Base64.NO_WRAP)
    }

    fun readSmallDocument(context: Context, uri: Uri, maxBytes: Int = 220_000): Result<PreparedMediaPayload> = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(context, uri) ?: "document"
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                total += read
                require(total <= maxBytes) { "File exceeds 220 KB encrypted attachment limit." }
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        } ?: error("Unable to read selected file.")
        require(bytes.isNotEmpty()) { "Selected file is empty." }
        PreparedMediaPayload(bytes = bytes, mimeType = mime, displayName = name)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun startRecording(): Result<Unit> = runCatching {
        stopAndDiscard()
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioEncodingBitRate(32_000)
        mr.setAudioSamplingRate(16_000)
        mr.setOutputFile(file.absolutePath)
        mr.prepare()
        mr.start()
        recorder = mr
        outputFile = file
        startedAtMs = System.currentTimeMillis()
    }

    fun stopAndReadBytes(): Result<Pair<ByteArray, Long>> = runCatching {
        val mr = recorder ?: error("Not currently recording.")
        val file = outputFile ?: error("Recording file missing.")
        val durationSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(1L)
        runCatching { mr.stop() }
        mr.release()
        recorder = null
        val bytes = file.readBytes()
        file.delete()
        outputFile = null
        require(bytes.isNotEmpty()) { "Voice recording was empty." }
        bytes to durationSec
    }

    fun stopAndDiscard() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class VoiceNotePlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    fun playBytes(bytes: ByteArray, onCompleted: () -> Unit): Result<Unit> = runCatching {
        stop()
        val file = File(context.cacheDir, "play_${System.currentTimeMillis()}.m4a")
        file.writeBytes(bytes)
        tempFile = file
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stop()
                onCompleted()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        tempFile?.delete()
        tempFile = null
    }
}
