package com.vanvixi.file_saver_ffi.utils

import com.vanvixi.file_saver_ffi.models.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LegacyStoreConflictResolver {
    /**
     * Resolve file conflict using filesystem (Android ≤ 9)
     *
     * @param directory Target directory (must exist)
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param conflictResolution Strategy
     *
     * @return Resolved File or null if FAIL
     */
    suspend fun resolve(
        directory: File,
        baseFileName: String,
        extension: String,
        conflictResolution: ConflictResolution
    ): File? = withContext(Dispatchers.IO) {

        val original = File(
            directory,
            FileHelper.buildFileName(baseFileName, extension)
        )

        if (!original.exists()) {
            return@withContext original
        }

        when (conflictResolution) {
            ConflictResolution.AUTO_RENAME ->
                autoRename(directory, baseFileName, extension)

            //Todo(vanvixi): Will be implemented in future releases
            ConflictResolution.OVERWRITE,
            ConflictResolution.SKIP ->
                original

            ConflictResolution.FAIL ->
                null
        }
    }


    private fun autoRename(
        directory: File,
        baseFileName: String,
        extension: String
    ): File {

        var index = 1
        while (true) {
            val candidate = File(
                directory,
                "$baseFileName ($index).$extension"
            )

            if (!candidate.exists()) {
                return candidate
            }
            index++
        }
    }
}
