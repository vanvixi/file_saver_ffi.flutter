package com.vanvixi.file_saver_ffi.utils

import com.vanvixi.file_saver_ffi.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

object FileHelper {
    /**
     * Determines file type based on extension and MIME type
     ***/
    fun getFileType(
        ext: String?,
        mimeType: String?
    ): FileType {
        val normalizedExt = ext?.lowercase()?.replace(".", "")?.trim()
        val safeMime = mimeType?.lowercase()

        fun <T : FileType> find(
            values: Array<T>
        ): T? = values.firstOrNull {
            safeMime != null && it.mimeType == safeMime ||
                    normalizedExt != null && it.ext == normalizedExt
        }

        find(ImageType.entries.toTypedArray())?.let { return it }
        find(VideoType.entries.toTypedArray())?.let { return it }
        find(AudioType.entries.toTypedArray())?.let { return it }

        return CustomFileType(
            ext = normalizedExt ?: "bin",
            mimeType = safeMime ?: "application/octet-stream"
        )
    }

    /**
     * Ensures directory exists, creating it if necessary
     *
     * @param directory Directory to ensure exists
     * @return Result with success or error
     *
     */
    suspend fun ensureDirectoryExists(directory: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            when {
                // Directory already exists
                directory.exists() && directory.isDirectory -> {
                    Result.success(Unit)
                }
                // Path exists but is a file (not directory)
                directory.exists() && !directory.isDirectory -> {
                    Result.failure(
                        IllegalStateException("Path exists but is not a directory: ${directory.absolutePath}")
                    )
                }
                // Directory doesn't exist - create it
                else -> {
                    val created = directory.mkdirs()
                    if (created || directory.exists()) {
                        Result.success(Unit)
                    } else {
                        Result.failure(
                            IllegalStateException("Failed to create directory: ${directory.absolutePath}")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Builds full file name with extension
     *
     * @param fileName File name without extension
     * @param extension File extension
     * @return Full file name
     *
     * Examples:
     * - ("video", "mp4") → "video.mp4"
     * - ("video", ".mp4") → "video.mp4"
     * - ("video.backup", "mp4") → "video.backup.mp4"
     */
    fun buildFileName(fileName: String, extension: String): String {
        val ext = extension.removePrefix(".").trim()
        return if (ext.isNotEmpty()) {
            "$fileName.$ext"
        } else {
            fileName
        }
    }

    /**
     * Writes data to output stream
     *
     * @param outputStream Output stream from MediaStore
     * @param data Video data to write
     */
    suspend fun writeStream(
        outputStream: OutputStream,
        data: ByteArray,
    ) = withContext(Dispatchers.IO) {
        outputStream.use { stream ->
            var bytesWritten = 0L
            val chunkSize = Constants.CHUNK_SIZE

            while (bytesWritten < data.size) {
                // Calculate chunk size
                val remainingBytes = data.size - bytesWritten.toInt()
                val currentChunkSize = minOf(remainingBytes, chunkSize)

                // Write chunk
                stream.write(data, bytesWritten.toInt(), currentChunkSize)

                // Update progress
                bytesWritten += currentChunkSize
            }

            // Flush stream
            stream.flush()
        }
    }
}
