package com.vanvixi.file_saver_ffi

import android.content.Context
import com.vanvixi.file_saver_ffi.models.FileType
import com.vanvixi.file_saver_ffi.models.SaveResult
import com.vanvixi.file_saver_ffi.utils.Constants
import com.vanvixi.file_saver_ffi.utils.FormatValidator
import com.vanvixi.file_saver_ffi.exception.UnsupportedFormatException
import com.vanvixi.file_saver_ffi.models.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoSaver(context: Context) : BaseFileSaver(context) {


    suspend fun saveVideoBytes(
        videoData: ByteArray,
        videoType: FileType,
        baseFileName: String,
        subDir: String?,
        conflictResolution: ConflictResolution,
    ): SaveResult = withContext(Dispatchers.IO) {
        // FormatValidator checks:
        // - Video format is supported
        // - Codec availability via MediaCodecList
        // Throws UnsupportedFormatException if validation fails
        try {
            FormatValidator.validateVideoFormat(videoType)
        } catch (e: UnsupportedFormatException) {
            return@withContext SaveResult.failure(
                Constants.ERROR_UNSUPPORTED_FORMAT,
                e.message ?: "Unsupported format: ${videoType.ext}"
            )
        }

        return@withContext super.saveBytes(
            videoData,
            videoType,
            baseFileName,
            subDir,
            conflictResolution,
        )
    }
}
