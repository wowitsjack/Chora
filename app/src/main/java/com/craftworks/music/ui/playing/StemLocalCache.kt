package com.craftworks.music.ui.playing

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun cacheStemFile(
    context: Context,
    songId: String,
    stem: String,
    sourceUrl: String
): Uri = withContext(Dispatchers.IO) {
    val songDirectory = File(
        context.cacheDir,
        "stem-mixer/${sha256(songId).take(20)}"
    ).apply { mkdirs() }
    val destination = File(
        songDirectory,
        "${stem.replace(Regex("[^A-Za-z0-9_-]"), "_")}-${sha256(sourceUrl).take(20)}.mp3"
    )
    if (destination.length() >= MIN_STEM_BYTES) return@withContext Uri.fromFile(destination)

    val temporary = File(destination.parentFile, "${destination.name}.part")
    temporary.delete()
    val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        instanceFollowRedirects = true
        requestMethod = "GET"
    }
    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("Stem download returned HTTP $responseCode")
        }
        connection.inputStream.use { input ->
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
        if (temporary.length() < MIN_STEM_BYTES) {
            throw IOException("Downloaded stem is empty or incomplete")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        Uri.fromFile(destination)
    } catch (error: Exception) {
        temporary.delete()
        throw error
    } finally {
        connection.disconnect()
    }
}

internal fun stemCacheKey(value: String): String = sha256(value).take(20)

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000
private const val COPY_BUFFER_BYTES = 64 * 1024
private const val MIN_STEM_BYTES = 1_024L
