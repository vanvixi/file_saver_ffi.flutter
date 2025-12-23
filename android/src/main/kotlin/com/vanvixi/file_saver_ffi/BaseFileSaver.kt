package com.vanvixi.file_saver_ffi

import android.content.Context
import com.vanvixi.file_saver_ffi.models.FileType
import com.vanvixi.file_saver_ffi.models.SaveResult
import com.vanvixi.file_saver_ffi.utils.Constants
import com.vanvixi.file_saver_ffi.exception.FileExistsException
import com.vanvixi.file_saver_ffi.models.ConflictResolution
import com.vanvixi.file_saver_ffi.utils.FileHelper
import com.vanvixi.file_saver_ffi.utils.StoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

abstract class BaseFileSaver(private val context: Context) {
    /**
     * Saves file to MediaStore
     *
     * @param fileData File content as byte array
     * @param baseFileName File name WITHOUT extension
     * @param fileType File type (e.g., ImageType, VideoType, AudioType, CustomFileType)
     * @param subDir Optional album name (null → Pictures folder)
     *              Example: "MyAlbum" → Pictures/MyAlbum
     * @param conflictResolution Conflict resolution mode (IGNORED for MediaStore)
     *                     MediaStore automatically handles conflicts by appending numbers
     *                     photo.jpg → photo(1).jpg → photo(2).jpg
     * @return SaveResult with success/failure details
     *
     */
    suspend fun saveBytes(
        fileData: ByteArray,
        fileType: FileType,
        baseFileName: String,
        subDir: String?,
        conflictResolution: ConflictResolution,
    ): SaveResult = withContext(Dispatchers.IO) {
        try {
            if (fileData.isEmpty()) {
                return@withContext SaveResult.failure(
                    Constants.ERROR_INVALID_FILE,
                    "File data cannot be empty"
                )
            }

            val (uri, outputStream) = try {
                StoreHelper.createEntry(
                    context, fileType, baseFileName, subDir, conflictResolution,
                )
            } catch (e: IOException) {
                return@withContext SaveResult.failure(
                    Constants.ERROR_FILE_IO,
                    "Failed to create MediaStore entry: ${e.message}"
                )
            } catch (e: FileExistsException) {
                return@withContext SaveResult.failure(
                    Constants.ERROR_FILE_EXISTS,
                    e.message ?: "File already exists"
                )
            }

            try {
                FileHelper.writeStream(outputStream, fileData)
            } catch (e: IOException) {
                // If write fails, try to delete the MediaStore entry
                // to avoid leaving incomplete entries
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                    // Ignore delete errors
                }
                return@withContext SaveResult.failure(
                    Constants.ERROR_FILE_IO,
                    "Failed to write image data: ${e.message}"
                )
            }

            try {
                StoreHelper.markEntryComplete(context, uri)
            } catch (_: Exception) {
                // If marking complete fails, image is still saved
                // but may not appear in Gallery until device restart
                // Log warning but don't fail the operation
            }

            // Convert content:// URI to file path (best-effort)
            // May return null on some devices/Android versions
            val filePath = try {
                StoreHelper.uriToFilePath(context, uri)
            } catch (_: Exception) {
                null
            }

            SaveResult.success(
                filePath = filePath ?: "MediaStore",
                uri = uri.toString()
            )
        } catch (e: SecurityException) {
            SaveResult.failure(
                Constants.ERROR_PERMISSION_DENIED,
                "Permission denied: ${e.message}"
            )
        } catch (e: Exception) {
            SaveResult.failure(
                Constants.ERROR_PLATFORM,
                "Unexpected error: ${e.message ?: "Unknown error"}"
            )
        }
    }
}