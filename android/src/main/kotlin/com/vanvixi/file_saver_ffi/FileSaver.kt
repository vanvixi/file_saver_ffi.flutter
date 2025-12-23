package com.vanvixi.file_saver_ffi

import android.content.Context
import com.vanvixi.file_saver_ffi.models.*
import com.vanvixi.file_saver_ffi.utils.Constants
import com.vanvixi.file_saver_ffi.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileSaver(context: Context) {
    val imageSaver = ImageSaver(context)
    val videoSaver = VideoSaver(context)
    val audioSaver = AudioSaver(context)
    val customFileSaver = CustomFileSaver(context)


    /**
     * Saves file data with automatic MediaStore routing
     *
     * @param fileData File content as byte array
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param subDir Optional subdirectory within target location
     * @param conflictMode Conflict resolution mode (0-3)
     * @return SaveResult with success/failure details
     */
    suspend fun saveBytes(
        fileData: ByteArray,
        baseFileName: String,
        extension: String,
        mimeType: String,
        subDir: String?,
        conflictMode: Int,
    ): SaveResult = withContext(Dispatchers.IO) {
        try {
            val fileType = FileHelper.getFileType(extension, mimeType)
            val conflictResolution = ConflictResolution.fromInt(conflictMode)

            if (fileType.isImage) {
                return@withContext imageSaver.saveImageBytes(
                    fileData, fileType, baseFileName, subDir, conflictResolution
                )
            }

            if (fileType.isVideo) {
                return@withContext videoSaver.saveVideoBytes(
                    fileData, fileType, baseFileName, subDir, conflictResolution
                )
            }

            if (fileType.isAudio) {
                return@withContext audioSaver.saveAudioBytes(
                    fileData, fileType, baseFileName, subDir, conflictResolution
                )
            }

            return@withContext customFileSaver.saveBytes(
                fileData, fileType, baseFileName, subDir, conflictResolution
            )
        } catch (e: Exception) {
            SaveResult.failure(
                Constants.ERROR_PLATFORM,
                "Unexpected error: ${e.message ?: "Unknown error"}"
            )
        }
    }
}
