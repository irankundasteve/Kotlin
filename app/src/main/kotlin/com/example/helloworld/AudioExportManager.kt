package com.example.helloworld

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.sciss.jump3r.Main
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AudioExportFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val publicDirectory: String,
    val relativeDirectory: String
) {
    MP3(
        "MP3",
        "mp3",
        "audio/mpeg",
        Environment.DIRECTORY_MUSIC,
        "${Environment.DIRECTORY_MUSIC}/TTS_Reader"
    ),
    WAV(
        "WAV",
        "wav",
        "audio/wav",
        Environment.DIRECTORY_MUSIC,
        "${Environment.DIRECTORY_MUSIC}/TTS_Reader"
    )
}

data class AudioExportResult(
    val uri: Uri,
    val displayName: String
)

object AudioExportManager {

    fun buildFileName(format: AudioExportFormat, now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date(now))
        return "TTS_Audio_${stamp}.${format.extension}"
    }

    suspend fun convertWavToMp3(sourceWav: File, targetMp3: File) = withContext(Dispatchers.IO) {
        require(sourceWav.exists()) { "Source WAV file does not exist." }
        require(sourceWav.length() > 0L) { "Source WAV file is empty." }

        val args = arrayOf(
            "--preset", "standard",
            "-q", "0",
            "-m", "m",
            sourceWav.absolutePath,
            targetMp3.absolutePath
        )
        
        try {
            Main().run(args)
        } catch (e: Exception) {
            throw RuntimeException("MP3 encoding failed: ${e.message}", e)
        }
        
        require(targetMp3.exists() && targetMp3.length() > 0L) { "MP3 encoding did not produce an output file." }
    }

    suspend fun saveToPublicStorage(
        context: Context,
        sourceFile: File,
        format: AudioExportFormat
    ): AudioExportResult = withContext(Dispatchers.IO) {
        val fileName = buildFileName(format)
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (format.mimeType.startsWith("audio/")) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, format.relativeDirectory)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create MediaStore entry. Error code: 101")

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Failed to open output stream for storage. Error code: 102")

                val pendingValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, pendingValues, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            AudioExportResult(uri, fileName)
        } else {
            val rootDirectory = Environment.getExternalStoragePublicDirectory(format.publicDirectory)
            val targetDirectory = File(rootDirectory, "TTS_Reader").apply { mkdirs() }
            val targetFile = File(targetDirectory, fileName)
            sourceFile.copyTo(targetFile, overwrite = true)
            AudioExportResult(Uri.fromFile(targetFile), fileName)
        }
    }
}
