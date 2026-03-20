package com.vanvixi.file_saver_ffi

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.vanvixi.file_saver_ffi.FileSaver.Companion.storagePermissionHandler
import com.vanvixi.file_saver_ffi.core.AudioSaver
import com.vanvixi.file_saver_ffi.core.CustomFileSaver
import com.vanvixi.file_saver_ffi.core.ImageSaver
import com.vanvixi.file_saver_ffi.core.VideoSaver
import com.vanvixi.file_saver_ffi.core.base.BaseFileSaver
import com.vanvixi.file_saver_ffi.core.base.SaveEntryFactory
import com.vanvixi.file_saver_ffi.exception.FileExistsException
import com.vanvixi.file_saver_ffi.models.*
import com.vanvixi.file_saver_ffi.utils.Constants
import com.vanvixi.file_saver_ffi.utils.FileHelper
import com.vanvixi.file_saver_ffi.utils.StoreHelper
import com.vanvixi.file_saver_ffi.utils.StoragePermissionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class FileSaver() {
    private val androidConfigDocUrl =
        "https://pub.dev/packages/file_saver_ffi#:~:text=Android%20Configuration"

    private val context: Context
        get() = appContext ?: error("FileSaver not initialized. FileSaverFfiPlugin must be attached first.")
    private val imageSaver get() = ImageSaver(context)
    private val videoSaver get() = VideoSaver(context)
    private val audioSaver get() = AudioSaver(context)
    private val customFileSaver get() = CustomFileSaver(context)

    // Job tracking for cancellation support
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val operationIdCounter = AtomicLong(0)

    // ─────────────────────────────────────────────────────────────────────────
    // Write session state
    // ─────────────────────────────────────────────────────────────────────────

    private data class WriteSession(
        val outputStream: OutputStream,
        val uri: Uri,
        val entryFactory: SaveEntryFactory,
        val totalSize: Long,                  // -1 if unknown
        val sessionScope: CoroutineScope,     // limited(1) → guarantees chunk ordering
        var bytesWritten: Long = 0L,
    )

    private val writeSessions = ConcurrentHashMap<Long, WriteSession>()
    private val sessionIdCounter = AtomicLong(0)

    companion object {
        /**
         * Application context stored by [FileSaverFfiPlugin.onAttachedToEngine].
         * Must be set before any [FileSaver] instance is used.
         */
        @Volatile
        internal var appContext: Context? = null

        /**
         * Static permission handler set by [FileSaverFfiPlugin] when Activity is available.
         *
         * Bridges [FileSaver] (created via JNI without Context) to the plugin layer
         * (which has Activity access for showing permission dialogs).
         */
        @Volatile
        var storagePermissionHandler: StoragePermissionHandler? = null
    }

    /**
     * Checks whether the file at the given URI is accessible for reading.
     *
     * Supports:
     * - content:// (MediaStore, SAF): tries opening a read-only FileDescriptor via ContentResolver.
     * - file:// (filesystem path): checks file exists and is readable.
     *
     * @param uri URI string (content:// or file://)
     * @return true if the file is readable, false otherwise
     */
    fun canOpenFile(uri: String): Boolean {
        return try {
            val parsedUri = uri.toUri()
            when (parsedUri.scheme) {
                "content" -> context.contentResolver.openFileDescriptor(parsedUri, "r")?.use { true } ?: false
                "file" -> {
                    val path = parsedUri.path ?: return false
                    val file = File(path)
                    if (!file.exists() || !file.canRead()) return false
                    FileInputStream(file).use { true }
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens a saved file with the appropriate system app.
     *
     * Uses Intent.ACTION_VIEW with FLAG_GRANT_READ_URI_PERMISSION, so no additional
     * permissions are required — the app already owns the content URI it created.
     *
     * Supports:
     * - content:// (MediaStore, SAF): opens directly.
     * - file:// (filesystem path): requires the host app to configure a FileProvider.
     *
     * @param uri Content URI or file URI string returned from save operations
     * @param mimeType Optional MIME type.
     *   - If null and uri is content://, queried from ContentResolver automatically.
     *   - If null and uri is file://, inferred from file extension when possible.
     */
    fun openFile(uri: String, mimeType: String?) {
        val parsedUri = uri.toUri()
        val intentUri = when (parsedUri.scheme) {
            "content" -> parsedUri
            "file" -> {
                val path = parsedUri.path
                    ?: throw IllegalArgumentException("INVALID_INPUT: Invalid file URI: $uri")
                val file = File(path)
                if (!file.exists() || !file.canRead()) {
                    throw IllegalArgumentException("FILE_NOT_FOUND: File is not accessible: $path")
                }

                val authority = "${context.packageName}.file_saver_ffi.fileprovider"
                val hasProvider =
                    if (Build.VERSION.SDK_INT >= 33) {
                        context.packageManager.resolveContentProvider(
                            authority,
                            PackageManager.ComponentInfoFlags.of(0),
                        ) != null
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.resolveContentProvider(authority, 0) != null
                    }

                if (!hasProvider) {
                    throw IllegalStateException(
                        "FILE_PROVIDER_NOT_CONFIGURED: This app must configure a FileProvider to open file:// URIs. " +
                            "See Android Configuration: $androidConfigDocUrl",
                    )
                }

                try {
                    FileProvider.getUriForFile(context, authority, file)
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException(
                        "FILE_PROVIDER_PATHS_NOT_COVERED: $path. " +
                            "Update your FileProvider paths XML to include this location. " +
                            "See Android Configuration: $androidConfigDocUrl",
                        e,
                    )
                }
            }
            else -> throw IllegalArgumentException("INVALID_INPUT: Unsupported URI scheme: ${parsedUri.scheme}")
        }

        val resolvedMime =
            mimeType
                ?: context.contentResolver.getType(intentUri)
                ?: run {
                    val ext = MimeTypeMap.getFileExtensionFromUrl(intentUri.toString())
                    if (ext.isNullOrEmpty()) "*/*"
                    else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "*/*"
                }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(intentUri, resolvedMime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "file", intentUri)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw IllegalStateException(
                "NO_APP_FOUND: No app found to open this file (mimeType=$resolvedMime).",
                e
            )
        }
    }

    /**
     * Cancels an ongoing save operation.
     *
     * @param operationId The operation ID returned by saveBytes, saveFile, or saveNetwork
     */
    fun cancelOperation(operationId: Long) {
        activeJobs[operationId]?.cancel()
    }

    /**
     * Saves file data with progress streaming (internal)
     *
     * @param fileData File content as byte array
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param saveLocationIndex Save location index from Dart enum (0-4)
     * @param subDir Optional subdirectory within target location
     * @param conflictMode Conflict resolution mode (0-3)
     * @return Flow of SaveProgressEvent
     */
    internal fun saveBytes(
        fileData: ByteArray,
        baseFileName: String,
        extension: String,
        mimeType: String,
        saveLocationIndex: Int,
        subDir: String?,
        conflictMode: Int,
    ): Flow<SaveProgressEvent> = mediaStoreFlow(
        extension, mimeType, saveLocationIndex, baseFileName, subDir, conflictMode
    ) { saver, entryFactory, conflictResolution ->
        saver.saveBytes(fileData, entryFactory, conflictResolution)
    }

    /**
     * Saves file data with progress callback (for Dart consumption via JNI)
     *
     * @param fileData File content as byte array
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param saveLocationIndex Save location index from Dart enum (0-4)
     * @param subDir Optional subdirectory within target location
     * @param conflictMode Conflict resolution mode (0-3)
     * @param callback Progress callback for events
     * @return Operation ID for cancellation
     */
    fun saveBytes(
        fileData: ByteArray,
        baseFileName: String,
        extension: String,
        mimeType: String,
        saveLocationIndex: Int,
        subDir: String?,
        conflictMode: Int,
        callback: ProgressCallback,
    ): Long = launchWithCallback(
        saveBytes(fileData, baseFileName, extension, mimeType, saveLocationIndex, subDir, conflictMode),
        callback
    )

    /**
     * Saves file from source path with progress streaming (internal)
     *
     * @param filePath Source file path (file:// or content:// URI)
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param saveLocationIndex Save location index from Dart enum (0-4)
     * @param subDir Optional subdirectory within target location
     * @param conflictMode Conflict resolution mode (0-3)
     * @return Flow of SaveProgressEvent
     */
    internal fun saveFile(
        filePath: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        saveLocationIndex: Int,
        subDir: String?,
        conflictMode: Int,
    ): Flow<SaveProgressEvent> = mediaStoreFlow(
        extension, mimeType, saveLocationIndex, baseFileName, subDir, conflictMode
    ) { saver, entryFactory, conflictResolution ->
        saver.saveFile(filePath, entryFactory, conflictResolution)
    }

    /**
     * Saves file from source path with progress callback (for Dart consumption via JNI)
     *
     * @param filePath Source file path (file:// or content:// URI)
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param saveLocationIndex Save location index from Dart enum (0-4)
     * @param subDir Optional subdirectory within target location
     * @param conflictMode Conflict resolution mode (0-3)
     * @param callback Progress callback for events
     * @return Operation ID for cancellation
     */
    fun saveFile(
        filePath: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        saveLocationIndex: Int,
        subDir: String?,
        conflictMode: Int,
        callback: ProgressCallback,
    ): Long = launchWithCallback(
        saveFile(filePath, baseFileName, extension, mimeType, saveLocationIndex, subDir, conflictMode),
        callback
    )

    /**
     * Downloads file from network URL and saves directly to storage (internal)
     *
     * @param url Network URL to download from
     * @param headersJson Optional JSON string of HTTP headers
     * @param timeoutMs Timeout in milliseconds for network connection
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param saveLocationIndex Save location index from Dart enum (0-4)
     * @param subDir Optional subdirectory within target location
     * @param conflictMode Conflict resolution mode (0-3)
     * @return Flow of SaveProgressEvent
     */
    internal fun saveNetwork(
        url: String,
        headersJson: String?,
        timeoutMs: Int,
        baseFileName: String,
        extension: String,
        mimeType: String,
        saveLocationIndex: Int,
        subDir: String?,
        conflictMode: Int,
    ): Flow<SaveProgressEvent> = mediaStoreFlow(
        extension, mimeType, saveLocationIndex, baseFileName, subDir, conflictMode
    ) { saver, entryFactory, conflictResolution ->
        saver.saveNetwork(url, headersJson, timeoutMs, entryFactory, conflictResolution)
    }

    /**
     * Downloads file from network URL and saves directly to storage (for Dart consumption via JNI)
     *
     * @param url Network URL to download from
     * @param headersJson Optional JSON string of HTTP headers
     * @param timeoutMs Timeout in milliseconds for network connection
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param saveLocationIndex Save location index from Dart enum (0-4)
     * @param subDir Optional subdirectory within target location
     * @param conflictMode Conflict resolution mode (0-3)
     * @param callback Progress callback for events
     * @return Operation ID for cancellation
     */
    fun saveNetwork(
        url: String,
        headersJson: String?,
        timeoutMs: Int,
        baseFileName: String,
        extension: String,
        mimeType: String,
        saveLocationIndex: Int,
        subDir: String?,
        conflictMode: Int,
        callback: ProgressCallback,
    ): Long = launchWithCallback(
        saveNetwork(
            url,
            headersJson,
            timeoutMs,
            baseFileName,
            extension,
            mimeType,
            saveLocationIndex,
            subDir,
            conflictMode
        ),
        callback
    )

    /**
     * Saves file data to user-selected directory (internal)
     *
     * @param fileData File content as byte array
     * @param directoryUri Directory URI from SAF picker
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param conflictMode Conflict resolution mode (0-3)
     * @return Flow of SaveProgressEvent
     */
    internal fun saveBytesAs(
        fileData: ByteArray,
        directoryUri: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        conflictMode: Int,
    ): Flow<SaveProgressEvent> {
        val conflictResolution = ConflictResolution.fromInt(conflictMode)
        val entryFactory = SaveEntryFactory.SAF(
            treeUri = directoryUri.toUri(),
            fileType = FileType(extension, mimeType),
            baseFileName = baseFileName
        )
        return customFileSaver.saveBytes(fileData, entryFactory, conflictResolution)
    }

    /**
     * Saves file data to user-selected directory (for Dart consumption via JNI)
     */
    fun saveBytesAs(
        fileData: ByteArray,
        directoryUri: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        conflictMode: Int,
        callback: ProgressCallback,
    ): Long = launchWithCallback(
        saveBytesAs(fileData, directoryUri, baseFileName, extension, mimeType, conflictMode),
        callback
    )

    /**
     * Saves file from source path to user-selected directory (internal)
     *
     * @param filePath Source file path (file:// or content:// URI)
     * @param directoryUri Directory URI from SAF picker
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param conflictMode Conflict resolution mode (0-3)
     * @return Flow of SaveProgressEvent
     */
    internal fun saveFileAs(
        filePath: String,
        directoryUri: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        conflictMode: Int,
    ): Flow<SaveProgressEvent> {
        val conflictResolution = ConflictResolution.fromInt(conflictMode)
        val entryFactory = SaveEntryFactory.SAF(
            treeUri = directoryUri.toUri(),
            fileType = FileType(extension, mimeType),
            baseFileName = baseFileName
        )
        return customFileSaver.saveFile(filePath, entryFactory, conflictResolution)
    }

    /**
     * Saves file from source path to user-selected directory (for Dart consumption via JNI)
     */
    fun saveFileAs(
        filePath: String,
        directoryUri: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        conflictMode: Int,
        callback: ProgressCallback,
    ): Long = launchWithCallback(
        saveFileAs(filePath, directoryUri, baseFileName, extension, mimeType, conflictMode),
        callback
    )

    /**
     * Downloads file from network and saves to user-selected directory (internal)
     *
     * @param url Network URL to download from
     * @param headersJson Optional JSON string of HTTP headers
     * @param timeoutMs Timeout in milliseconds for network connection
     * @param directoryUri Directory URI from SAF picker
     * @param baseFileName File name WITHOUT extension
     * @param extension File extension WITHOUT dot
     * @param mimeType MIME type string (e.g., "image/jpeg")
     * @param conflictMode Conflict resolution mode (0-3)
     * @return Flow of SaveProgressEvent
     */
    internal fun saveNetworkAs(
        url: String,
        headersJson: String?,
        timeoutMs: Int,
        directoryUri: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        conflictMode: Int,
    ): Flow<SaveProgressEvent> {
        val conflictResolution = ConflictResolution.fromInt(conflictMode)
        val entryFactory = SaveEntryFactory.SAF(
            treeUri = directoryUri.toUri(),
            fileType = FileType(extension, mimeType),
            baseFileName = baseFileName
        )
        return customFileSaver.saveNetwork(url, headersJson, timeoutMs, entryFactory, conflictResolution)
    }

    /**
     * Downloads file from network and saves to user-selected directory (for Dart consumption via JNI)
     */
    fun saveNetworkAs(
        url: String,
        headersJson: String?,
        timeoutMs: Int,
        directoryUri: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        conflictMode: Int,
        callback: ProgressCallback,
    ): Long = launchWithCallback(
        saveNetworkAs(url, headersJson, timeoutMs, directoryUri, baseFileName, extension, mimeType, conflictMode),
        callback
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Streaming write session methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens a streaming write session to a MediaStore location.
     *
     * On success: callback(3, 0.0, sessionId, null)
     * On error:   callback(2, 0.0, errorCode, message)
     */
    fun openWriteSession(
        baseFileName: String,
        extension: String,
        mimeType: String,
        saveLocationIndex: Int,
        subDir: String?,
        conflictMode: Int,
        totalSize: Long,
        callback: ProgressCallback,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ensureStoragePermission()
                val fileType = FileType(extension, mimeType)
                val conflictResolution = ConflictResolution.fromInt(conflictMode)
                val saveLocation = SaveLocation.fromInt(saveLocationIndex)

                if (conflictResolution == ConflictResolution.SKIP) {
                    val existingUri = FileHelper.findExistingWriteTargetUriOrNull(
                        context = context,
                        saveLocation = saveLocation,
                        subDir = subDir,
                        baseFileName = baseFileName,
                        extension = extension,
                    )
                    if (existingUri != null) {
                        callback.onEvent(3, 0.0, "0", existingUri.toString())
                        return@launch
                    }
                }
                val entryFactory = SaveEntryFactory.MediaStore(
                    fileType = fileType,
                    baseFileName = baseFileName,
                    saveLocation = saveLocation,
                    subDir = subDir,
                )
                val (uri, outputStream) = entryFactory.createEntryDirect(
                    context, conflictResolution
                )
                val sessionId = sessionIdCounter.incrementAndGet()
                val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
                writeSessions[sessionId] = WriteSession(outputStream, uri, entryFactory, totalSize, scope)
                callback.onEvent(3, 0.0, sessionId.toString(), null)
            } catch (e: FileExistsException) {
                callback.onEvent(2, 0.0, Constants.ERROR_FILE_EXISTS, e.message)
            } catch (e: IOException) {
                callback.onEvent(2, 0.0, Constants.ERROR_FILE_IO, e.message)
            } catch (e: SecurityException) {
                callback.onEvent(2, 0.0, Constants.ERROR_PERMISSION_DENIED, e.message)
            }
        }
    }

    /**
     * Opens a streaming write session to a user-selected SAF directory.
     *
     * On success: callback(3, 0.0, sessionId, null)
     * On error:   callback(2, 0.0, errorCode, message)
     */
    fun openWriteSessionAs(
        directoryUri: String,
        baseFileName: String,
        extension: String,
        mimeType: String,
        conflictMode: Int,
        totalSize: Long,
        callback: ProgressCallback,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileType = FileType(extension, mimeType)
                val conflictResolution = ConflictResolution.fromInt(conflictMode)

                if (conflictResolution == ConflictResolution.SKIP) {
                    val existingUri = FileHelper.findExistingSafFileUriOrNull(
                        context = context,
                        directoryUri = directoryUri,
                        baseFileName = baseFileName,
                        extension = extension,
                    )
                    if (existingUri != null) {
                        callback.onEvent(3, 0.0, "0", existingUri.toString())
                        return@launch
                    }
                }
                val entryFactory = SaveEntryFactory.SAF(
                    treeUri = directoryUri.toUri(),
                    fileType = fileType,
                    baseFileName = baseFileName,
                )
                val (uri, outputStream) = entryFactory.createEntryDirect(
                    context, conflictResolution
                )
                val sessionId = sessionIdCounter.incrementAndGet()
                val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
                writeSessions[sessionId] = WriteSession(outputStream, uri, entryFactory, totalSize, scope)
                callback.onEvent(3, 0.0, sessionId.toString(), null)
            } catch (e: FileExistsException) {
                callback.onEvent(2, 0.0, Constants.ERROR_FILE_EXISTS, e.message)
            } catch (e: IOException) {
                callback.onEvent(2, 0.0, Constants.ERROR_FILE_IO, e.message)
            }
        }
    }

    /**
     * Writes a chunk of data to an open write session.
     *
     * On success: callback(1, bytesWritten, null, null)
     * On error:   callback(2, 0.0, errorCode, message)
     */
    fun writeChunk(sessionId: Long, data: ByteArray, callback: ProgressCallback) {
        val session = writeSessions[sessionId]
            ?: return callback.onEvent(2, 0.0, Constants.ERROR_INVALID_INPUT, "Session not found: $sessionId")

        session.sessionScope.launch {
            try {
                session.outputStream.write(data)
                session.bytesWritten += data.size
                callback.onEvent(1, session.bytesWritten.toDouble(), null, null)
            } catch (e: IOException) {
                callback.onEvent(2, 0.0, Constants.ERROR_FILE_IO, e.message)
            }
        }
    }

    /**
     * Flushes buffered data to storage for an open write session.
     *
     * On success: callback(1, bytesWritten, null, null)
     * On error:   callback(2, 0.0, errorCode, message)
     */
    fun flushSession(sessionId: Long, callback: ProgressCallback) {
        val session = writeSessions[sessionId]
            ?: return callback.onEvent(2, 0.0, Constants.ERROR_INVALID_INPUT, "Session not found: $sessionId")

        session.sessionScope.launch {
            try {
                session.outputStream.flush()
                callback.onEvent(1, session.bytesWritten.toDouble(), null, null)
            } catch (e: IOException) {
                callback.onEvent(2, 0.0, Constants.ERROR_FILE_IO, e.message)
            }
        }
    }

    /**
     * Closes and finalizes an open write session.
     *
     * For MediaStore entries, marks the file as complete (removes IS_PENDING flag).
     * On success: callback(3, 1.0, uri, null)
     * On error:   callback(2, 0.0, errorCode, message)
     */
    fun closeSession(sessionId: Long, callback: ProgressCallback) {
        val session = writeSessions.remove(sessionId)
            ?: return callback.onEvent(2, 0.0, Constants.ERROR_INVALID_INPUT, "Session not found: $sessionId")

        session.sessionScope.launch {
            try {
                session.outputStream.close()
                if (session.entryFactory is SaveEntryFactory.MediaStore) {
                    StoreHelper.markEntryComplete(context, session.uri)
                }
                callback.onEvent(3, 1.0, session.uri.toString(), null)
            } catch (e: IOException) {
                callback.onEvent(2, 0.0, Constants.ERROR_FILE_IO, e.message)
            }
        }
    }

    /**
     * Cancels a write session — closes the stream and deletes the partial file.
     * Fire-and-forget: no callback.
     */
    fun cancelSession(sessionId: Long) {
        val session = writeSessions.remove(sessionId) ?: return
        session.sessionScope.launch {
            try {
                session.outputStream.close()
            } catch (_: Exception) {
            }
            session.entryFactory.deleteEntry(context, session.uri)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a Flow that handles common MediaStore save setup:
     * permission check, type/location parsing, entry factory creation, and error handling.
     *
     * @param save Lambda that performs the actual save using the resolved saver, entry factory,
     *             and conflict resolution. Should return a Flow from BaseFileSaver.
     */
    private fun mediaStoreFlow(
        extension: String,
        mimeType: String,
        saveLocationIndex: Int,
        baseFileName: String,
        subDir: String?,
        conflictMode: Int,
        save: (BaseFileSaver, SaveEntryFactory, ConflictResolution) -> Flow<SaveProgressEvent>,
    ): Flow<SaveProgressEvent> = flow {
        try {
            ensureStoragePermission()

            val fileType = FileType(extension, mimeType)
            val conflictResolution = ConflictResolution.fromInt(conflictMode)
            val saveLocation = SaveLocation.fromInt(saveLocationIndex)

            val saver = getSaverForFileType(fileType)
            val entryFactory = SaveEntryFactory.MediaStore(
                fileType = fileType,
                baseFileName = baseFileName,
                saveLocation = saveLocation,
                subDir = subDir
            )

            save(saver, entryFactory, conflictResolution)
                .collect { event -> emit(event) }
        } catch (e: SecurityException) {
            emit(
                SaveProgressEvent.Error(
                    Constants.ERROR_PERMISSION_DENIED,
                    "Permission denied: ${e.message}",
                )
            )
        } catch (e: Exception) {
            emit(
                SaveProgressEvent.Error(
                    Constants.ERROR_PLATFORM,
                    "Unexpected error: ${e.message ?: "Unknown error"}",
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Ensures WRITE_EXTERNAL_STORAGE permission is granted on API < 29.
     *
     * - API 29+: Returns immediately (scoped storage, no permission needed)
     * - API < 29 with permission granted: Returns immediately
     * - API < 29 without permission: Requests via [storagePermissionHandler]
     *
     * @throws SecurityException if permission is denied or handler is unavailable
     */
    private suspend fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return

        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (status == PackageManager.PERMISSION_GRANTED) return

        val handler = storagePermissionHandler
            ?: throw SecurityException("Storage permission required but no Activity available to request it")

        if (!handler.requestStoragePermission()) {
            throw SecurityException("Storage permission denied by user")
        }
    }

    /**
     * Returns appropriate saver based on file type.
     */
    private fun getSaverForFileType(fileType: FileType) = when {
        fileType.isImage -> imageSaver
        fileType.isVideo -> videoSaver
        fileType.isAudio -> audioSaver
        else -> customFileSaver
    }

    /**
     * Launches a coroutine to collect events from a Flow and forward them to a callback.
     * Handles job tracking for cancellation support.
     */
    private fun launchWithCallback(
        flow: Flow<SaveProgressEvent>,
        callback: ProgressCallback
    ): Long {
        val operationId = operationIdCounter.incrementAndGet()

        val job = CoroutineScope(Dispatchers.IO).launch {
            flow.collect { event ->
                when (event) {
                    is SaveProgressEvent.Started -> callback.onEvent(0, 0.0, null, null)
                    is SaveProgressEvent.Progress -> callback.onEvent(1, event.value, null, null)
                    is SaveProgressEvent.Error -> callback.onEvent(2, 0.0, event.code, event.message)
                    is SaveProgressEvent.Success -> callback.onEvent(3, 1.0, event.uri, null)
                    is SaveProgressEvent.Cancelled -> callback.onEvent(4, 0.0, null, null)
                }
            }
        }

        activeJobs[operationId] = job
        job.invokeOnCompletion { activeJobs.remove(operationId) }

        return operationId
    }

}
