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

class AudioSaver(context: Context) : BaseFileSaver(context) {

    suspend fun saveAudioBytes(
        audioData: ByteArray,
        audioType: FileType,
        baseFileName: String,
        subDir: String?,
        conflictResolution: ConflictResolution,
    ): SaveResult = withContext(Dispatchers.IO) {
        try {
            FormatValidator.validateAudioFormat(audioType)
        } catch (e: UnsupportedFormatException) {
            return@withContext SaveResult.failure(
                Constants.ERROR_UNSUPPORTED_FORMAT,
                e.message ?: "Unsupported format: ${audioType.ext}"
            )
        }

        return@withContext super.saveBytes(
            audioData,
            audioType,
            baseFileName,
            subDir,
            conflictResolution,
        )
    }
}
