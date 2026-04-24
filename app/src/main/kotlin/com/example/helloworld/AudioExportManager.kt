package com.example.helloworld

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.sciss.jump3r.lowlevel.LameEncoder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class AudioExportFormat(val mimeType: String, val extension: String) {
    MP3("audio/mpeg", "mp3"),
    WAV("audio/wav", "wav")
}

data class ExportResult(val uri: Uri, val displayName: String)

object AudioExportManager {

    fun convertWavToMp3(wavFile: File, mp3File: File) {
        val inputStream = FileInputStream(wavFile)
        val outputStream = FileOutputStream(mp3File)

        val encoder = LameEncoder(
            wavFile.length().toInt(), 44100, 1, 128, 7
        )

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val mp3Buffer = ByteArray(8192)
            val mp3Bytes = encoder.encodeBuffer(buffer, 0, bytesRead, mp3Buffer)
            if (mp3Bytes > 0) {
                outputStream.write(mp3Buffer, 0, mp3Bytes)
            }
        }
        encoder.flush(buffer)
        outputStream.close()
        inputStream.close()
    }

    fun saveToPublicStorage(context: Context, file: File, format: AudioExportFormat): ExportResult {
        val fileName = "TTS_Audio_${System.currentTimeMillis()}.${format.extension}"
        val resolver = context.contentResolver
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/TTS_Reader")
            }
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create media store entry.")

        resolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(file).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return ExportResult(uri, fileName)
    }
}
