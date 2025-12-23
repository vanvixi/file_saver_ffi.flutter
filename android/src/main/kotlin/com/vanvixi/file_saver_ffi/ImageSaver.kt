package com.vanvixi.file_saver_ffi

import android.content.Context
import com.vanvixi.file_saver_ffi.exception.UnsupportedFormatException
import com.vanvixi.file_saver_ffi.models.ConflictResolution
import com.vanvixi.file_saver_ffi.models.FileType
import com.vanvixi.file_saver_ffi.models.SaveResult
import com.vanvixi.file_saver_ffi.utils.Constants
import com.vanvixi.file_saver_ffi.utils.FormatValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageSaver(context: Context) : BaseFileSaver(context) {

    suspend fun saveImageBytes(
        imageData: ByteArray,
        imageType: FileType,
        baseFileName: String,
        subDir: String?,
        conflictResolution: ConflictResolution,
    ): SaveResult = withContext(Dispatchers.IO) {
        // FormatValidator checks:
        // - HEIC/HEIF requires Android 10+ (API 29+)
        // - HEIC/HEIF codec availability via MediaCodecList
        // Throws UnsupportedFormatException if validation fails
        try {
            FormatValidator.validateImageFormat(imageType)
        } catch (e: UnsupportedFormatException) {
            return@withContext SaveResult.failure(
                Constants.ERROR_UNSUPPORTED_FORMAT,
                e.message ?: "Unsupported format: ${imageType.ext}"
            )
        }

        return@withContext super.saveBytes(
            imageData,
            imageType,
            baseFileName,
            subDir,
            conflictResolution,
        )
    }
}
