package com.vanvixi.file_saver_ffi.core.base

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vanvixi.file_saver_ffi.exception.FileExistsException
import com.vanvixi.file_saver_ffi.models.ConflictResolution
import com.vanvixi.file_saver_ffi.models.FileType
import com.vanvixi.file_saver_ffi.models.SaveLocation
import com.vanvixi.file_saver_ffi.models.SaveProgressEvent
import com.vanvixi.file_saver_ffi.utils.Constants
import com.vanvixi.file_saver_ffi.utils.SAFHelper
import com.vanvixi.file_saver_ffi.utils.StoreHelper
import kotlinx.coroutines.channels.ProducerScope
import java.io.IOException
import java.io.OutputStream

/**
 * Factory for creating save entries (MediaStore or SAF).
 *
 * This sealed class abstracts the differences between saving to MediaStore
 * (gallery, downloads, etc.) and saving to user-selected directories via SAF.
 */
sealed class SaveEntryFactory {
    /**
     * Creates an entry with standardized error handling.
     * Returns null and sends error event if creation fails.
     */
    abstract suspend fun createEntry(
        scope: ProducerScope<SaveProgressEvent>,
        context: Context,
        conflictResolution: ConflictResolution,
    ): Pair<Uri, OutputStream>?

    /**
     * Creates an entry for streaming write sessions.
     * Throws instead of emitting to a ProducerScope — caller handles errors.
     *
     * @throws IOException if I/O error occurs
     * @throws FileExistsException if file already exists and conflict resolution requires it
     */
    abstract suspend fun createEntryDirect(
        context: Context,
        conflictResolution: ConflictResolution,
    ): Pair<Uri, OutputStream>

    /**
     * Deletes an entry on error/cancellation.
     */
    abstract fun deleteEntry(
        context: Context,
        uri: Uri,
    )

    /**
     * Completes save operation (progress 0.9 → 1.0 → Success).
     */
    abstract suspend fun finishSave(
        scope: ProducerScope<SaveProgressEvent>,
        context: Context,
        uri: Uri,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // MediaStore Implementation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Factory for saving to MediaStore (gallery, downloads, music, etc.)
     *
     * @param fileType File type information (extension, mimeType)
     * @param baseFileName File name WITHOUT extension
     * @param saveLocation Target MediaStore location
     * @param subDir Optional subdirectory within target location
     */
    data class MediaStore(
        val fileType: FileType,
        val baseFileName: String,
        val saveLocation: SaveLocation,
        val subDir: String?,
    ) : SaveEntryFactory() {
        override suspend fun createEntry(
            scope: ProducerScope<SaveProgressEvent>,
            context: Context,
            conflictResolution: ConflictResolution,
        ): Pair<Uri, OutputStream>? =
            try {
                StoreHelper.createEntry(
                    context,
                    fileType,
                    baseFileName,
                    saveLocation,
                    subDir,
                    conflictResolution,
                )
            } catch (e: IOException) {
                scope.sendError(Constants.ERROR_FILE_IO, "Failed to create MediaStore entry: ${e.message}")
                null
            } catch (e: FileExistsException) {
                scope.sendError(Constants.ERROR_FILE_EXISTS, e.message ?: "File already exists")
                null
            }

        override suspend fun createEntryDirect(
            context: Context,
            conflictResolution: ConflictResolution,
        ): Pair<Uri, OutputStream> = StoreHelper.createEntry(context, fileType, baseFileName, saveLocation, subDir, conflictResolution)

        override fun deleteEntry(
            context: Context,
            uri: Uri,
        ) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
        }

        override suspend fun finishSave(
            scope: ProducerScope<SaveProgressEvent>,
            context: Context,
            uri: Uri,
        ) {
            scope.sendProgress(0.9)
            try {
                StoreHelper.markEntryComplete(context, uri)
            } catch (_: Exception) {
            }
            scope.sendProgress(1.0)
            scope.sendSuccess(uri.toString())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAF (User-Selected Directory) Implementation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Factory for saving to user-selected directory via Storage Access Framework.
     *
     * @param treeUri Directory URI from ACTION_OPEN_DOCUMENT_TREE
     * @param fileType File type information (extension, mimeType)
     * @param baseFileName File name WITHOUT extension
     */
    data class SAF(
        val treeUri: Uri,
        val fileType: FileType,
        val baseFileName: String,
    ) : SaveEntryFactory() {
        override suspend fun createEntry(
            scope: ProducerScope<SaveProgressEvent>,
            context: Context,
            conflictResolution: ConflictResolution,
        ): Pair<Uri, OutputStream>? =
            try {
                SAFHelper.createFileInDirectory(
                    context,
                    treeUri,
                    fileType,
                    baseFileName,
                    conflictResolution,
                )
            } catch (e: IOException) {
                scope.sendError(Constants.ERROR_FILE_IO, "Failed to create file: ${e.message}")
                null
            } catch (e: FileExistsException) {
                scope.sendError(Constants.ERROR_FILE_EXISTS, e.message ?: "File already exists")
                null
            }

        override suspend fun createEntryDirect(
            context: Context,
            conflictResolution: ConflictResolution,
        ): Pair<Uri, OutputStream> = SAFHelper.createFileInDirectory(context, treeUri, fileType, baseFileName, conflictResolution)

        override fun deleteEntry(
            context: Context,
            uri: Uri,
        ) {
            try {
                DocumentFile.fromSingleUri(context, uri)?.delete()
            } catch (_: Exception) {
            }
        }

        override suspend fun finishSave(
            scope: ProducerScope<SaveProgressEvent>,
            context: Context,
            uri: Uri,
        ) {
            scope.sendProgress(0.9)
            scope.sendProgress(1.0)
            scope.sendSuccess(uri.toString())
        }
    }
}
