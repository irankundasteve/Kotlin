// Add timeout mechanism for MP3 encoding with a maximum of 2 minutes
class AudioExportManager {
    private val mp3EncodingTimeout: Long = 2 * 60 * 1000 // 2 minutes
    // Other existing code...

    fun exportToMP3() {
        // Existing MP3 export logic...
        withTimeout(mp3EncodingTimeout) {
            // MP3 encoding logic
        }
    }

    // Enhanced error handling
    private fun handleError(exception: Exception) {
        // Automatic cleanup of partial files
        cleanupPartialFiles()
        // Log the exception with better context
        Log.e("AudioExportManager", "Error occurred during export: ", exception)
    }
}
