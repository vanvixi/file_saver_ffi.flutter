package com.vanvixi.file_saver_ffi.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.vanvixi.file_saver_ffi.models.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ScopedStoreConflictResolver {
    /**
     * Resolve file name conflict for MediaStore (Android 10+)
     *
     * @param context Application context
     * @param contentUri MediaStore collection (Images / Video / Audio / Files)
     * @param dirPath Relative path (e.g. "Pictures/MyAlbum")
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param conflictResolution Strategy
     *
     * @return Resolved display name (WITH extension) or null if FAIL
     */
    suspend fun resolve(
        context: Context,
        contentUri: Uri,
        dirPath: String,
        baseFileName: String,
        extension: String,
        conflictResolution: ConflictResolution
    ): String? = withContext(Dispatchers.IO) {

        val originalName = FileHelper.buildFileName(baseFileName, extension)

        if (!exists(context, contentUri, dirPath, originalName)) {
            return@withContext originalName
        }

        when (conflictResolution) {
            //MediaStore automatically handles conflicts by appending numbers photo.jpg → photo(1).jpg → photo(2).jpg
            ConflictResolution.AUTO_RENAME ->
                originalName

            //Todo(vanvixi): Will be implemented in future releases
            ConflictResolution.OVERWRITE,
            ConflictResolution.SKIP ->
                originalName

            ConflictResolution.FAIL ->
                null
        }
    }

    private fun exists(
        context: Context,
        contentUri: Uri,
        dirPath: String,
        displayName: String
    ): Boolean {

        val projection = arrayOf(MediaStore.MediaColumns._ID)

        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                    "${MediaStore.MediaColumns.RELATIVE_PATH}=?"

        val selectionArgs = arrayOf(
            displayName,
            normalizeRelativePath(dirPath)
        )

        context.contentResolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }

        return false
    }

    private fun normalizeRelativePath(dirPath: String): String =
        if (dirPath.endsWith("/")) dirPath else "$dirPath/"
}