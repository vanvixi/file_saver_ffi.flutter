package com.vanvixi.file_saver_ffi.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.vanvixi.file_saver_ffi.exception.FileExistsException
import com.vanvixi.file_saver_ffi.models.ConflictResolution
import com.vanvixi.file_saver_ffi.models.FileType
import com.vanvixi.file_saver_ffi.models.SaveLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object StoreHelper {
    /**
     * Creates MediaStore entry
     *
     * @param context Application context
     * @param fileType File type (e.g., ImageType, VideoType, AudioType, CustomFileType)
     * @param baseFileName Base file name WITHOUT extension (e.g., "photo")
     * @param subDir Subdirectory within Pictures (e.g., "MyAlbum"), null for Pictures root
     * @return Pair of (Uri, OutputStream) for writing data
     * @throws IOException if MediaStore entry creation fails
     *
     * Example:
     * ```kotlin
     * val (uri, outputStream) = StoreHelper.createEntry(
     *     context = context,
     *     fileType = fileType,
     *     baseFileName = "photo",
     *     subDir = "MyAlbum"
     * )
     * outputStream.use { it.write(imageData) }
     * StoreHelper.markEntryComplete(context, uri)
     * ```
     */
    suspend fun createEntry(
        context: Context,
        fileType: FileType,
        baseFileName: String,
        saveLocation: SaveLocation,
        subDir: String? = null,
        conflictResolution: ConflictResolution,
    ): Pair<Uri, OutputStream> =
        withContext(Dispatchers.IO) {
            val isVersionQPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            if (isVersionQPlus) {
                return@withContext createEntryForScopedStorage(
                    context,
                    fileType,
                    baseFileName,
                    saveLocation,
                    subDir,
                    conflictResolution,
                )
            } else {
                return@withContext createEntryForLegacyStorage(
                    fileType,
                    baseFileName,
                    saveLocation,
                    subDir,
                    conflictResolution,
                )
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun createEntryForScopedStorage(
        context: Context,
        fileType: FileType,
        baseFileName: String,
        saveLocation: SaveLocation,
        subDir: String? = null,
        conflictResolution: ConflictResolution,
    ): Pair<Uri, OutputStream> =
        withContext(Dispatchers.IO) {
            val (contentUri, dirPath) = FileHelper.scopedStoreTargetFor(saveLocation, subDir)

            val fileName =
                ScopedStoreConflictResolver.resolve(
                    context,
                    contentUri,
                    dirPath,
                    baseFileName,
                    fileType.ext,
                    conflictResolution,
                )

            val resolver = context.contentResolver

            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, fileType.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, dirPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

            val uri =
                resolver.insert(contentUri, contentValues)
                    ?: throw IOException("Failed to create MediaStore entry: $fileName")

            val outputStream =
                resolver.openOutputStream(uri)
                    ?: throw IOException("Failed to open OutputStream: $fileName")

            Pair(uri, outputStream)
        }

    private suspend fun createEntryForLegacyStorage(
        fileType: FileType,
        baseFileName: String,
        saveLocation: SaveLocation,
        subDir: String? = null,
        conflictResolution: ConflictResolution,
    ): Pair<Uri, OutputStream> =
        withContext(Dispatchers.IO) {
            val directory = FileHelper.legacyStoreDirectoryFor(saveLocation, subDir)
            FileHelper.ensureDirectoryExists(directory).getOrElse { error ->
                throw IOException("Failed to create directory: ${error.message}")
            }

            val file =
                LegacyStoreConflictResolver.resolve(
                    directory,
                    baseFileName,
                    fileType.ext,
                    conflictResolution,
                ) ?: throw FileExistsException("File already exists: $baseFileName.${fileType.ext}")

            val uri = Uri.fromFile(file)

            Pair(uri, FileOutputStream(file))
        }

    /**
     * Marks MediaStore entry as complete (removes IS_PENDING flag)
     *
     * @param context Application context
     * @param uri URI of the MediaStore entry
     *
     * Example:
     * ```kotlin
     * val (uri, outputStream) = createImageEntry(...)
     * outputStream.use { it.write(data) }
     * markEntryComplete(context, uri)  // CRITICAL: Makes file visible
     * ```
     */
    suspend fun markEntryComplete(
        context: Context,
        uri: Uri,
    ) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext
        }

        val contentValues =
            ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
        context.contentResolver.update(uri, contentValues, null, null)
    }

    /**
     * Converts content URI to file path
     *
     * Note: On Android 10+, file path may not be accessible directly
     * This is a best-effort attempt
     *
     * @param context Application context
     * @param uri Content URI
     * @return File path if available, empty string otherwise
     */
    suspend fun uriToFilePath(
        context: Context,
        uri: Uri,
    ): String =
        withContext(Dispatchers.IO) {
            try {
                // Try to get DATA column (file path)
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(MediaStore.Images.Media.DATA),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                            if (columnIndex >= 0) {
                                return@withContext cursor.getString(columnIndex) ?: ""
                            }
                        }
                    }
            } catch (_: Exception) {
                // Ignore errors, fall back to empty string
            }

            // Fallback: Return empty string
            // On Android 10+, direct file paths are often not available
            ""
        }
}
