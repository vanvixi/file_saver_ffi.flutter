package com.vanvixi.file_saver_ffi.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vanvixi.file_saver_ffi.exception.FileExistsException
import com.vanvixi.file_saver_ffi.models.ConflictResolution
import com.vanvixi.file_saver_ffi.models.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/**
 * Helper for Storage Access Framework (SAF) operations.
 *
 * SAF allows users to select directories via system picker,
 * and apps can then read/write files to those directories.
 */
object SAFHelper {
    /**
     * Creates a file in the user-selected directory.
     *
     * @param context Application context
     * @param treeUri Directory URI from ACTION_OPEN_DOCUMENT_TREE
     * @param fileType File type information (extension, mimeType)
     * @param baseFileName File name WITHOUT extension
     * @param conflictResolution How to handle existing files
     * @return Pair of (Uri, OutputStream) for writing data
     * @throws IOException if file creation fails
     * @throws FileExistsException if file exists and conflictResolution is FAIL
     */
    suspend fun createFileInDirectory(
        context: Context,
        treeUri: Uri,
        fileType: FileType,
        baseFileName: String,
        conflictResolution: ConflictResolution,
    ): Pair<Uri, OutputStream> =
        withContext(Dispatchers.IO) {
            val fileName = FileHelper.buildFileName(baseFileName, fileType.ext)
            val mimeType = fileType.mimeType
            val docDir =
                DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw IOException("Cannot access directory: $treeUri")

            if (!docDir.isDirectory) {
                throw IOException("Not a directory: $treeUri")
            }

            if (!docDir.canWrite()) {
                throw IOException("Cannot write to directory: $treeUri")
            }

            // Check for existing file
            val existingFile = docDir.findFile(fileName)

            if (existingFile != null && existingFile.exists()) {
                when (conflictResolution) {
                    ConflictResolution.AUTO_RENAME -> {
                        // Generate unique name: photo.jpg → photo (1).jpg
                        val uniqueName = generateUniqueName(docDir, fileName)
                        return@withContext createNewFile(context, docDir, uniqueName, mimeType)
                    }

                    ConflictResolution.OVERWRITE -> {
                        // Delete existing and create new
                        existingFile.delete()
                        return@withContext createNewFile(context, docDir, fileName, mimeType)
                    }

                    ConflictResolution.SKIP -> {
                        // Return existing file's URI with a dummy output stream that does nothing
                        val uri = existingFile.uri
                        val outputStream =
                            context.contentResolver.openOutputStream(uri, "w")
                                ?: throw IOException("Cannot open existing file: $fileName")
                        return@withContext Pair(uri, outputStream)
                    }

                    ConflictResolution.FAIL -> {
                        throw FileExistsException("File already exists: $fileName")
                    }
                }
            }

            // No conflict, create new file
            createNewFile(context, docDir, fileName, mimeType)
        }

    /**
     * Creates a new file in the directory.
     */
    private fun createNewFile(
        context: Context,
        docDir: DocumentFile,
        fileName: String,
        mimeType: String,
    ): Pair<Uri, OutputStream> {
        val newFile =
            docDir.createFile(mimeType, fileName)
                ?: throw IOException("Failed to create file: $fileName")

        val outputStream =
            context.contentResolver.openOutputStream(newFile.uri)
                ?: throw IOException("Failed to open output stream: $fileName")

        return Pair(newFile.uri, outputStream)
    }

    /**
     * Generates a unique file name by appending (1), (2), etc.
     */
    private fun generateUniqueName(
        docDir: DocumentFile,
        originalName: String,
    ): String {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        var counter = 1
        var newName: String
        do {
            newName = "$baseName ($counter)$extension"
            counter++
        } while (docDir.findFile(newName)?.exists() == true && counter < 1000)

        if (counter >= 1000) {
            throw IOException("Cannot generate unique name for: $originalName")
        }

        return newName
    }
}
