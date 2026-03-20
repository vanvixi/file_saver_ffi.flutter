package com.vanvixi.file_saver_ffi.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream

internal object FileOpener {
    private val androidConfigDocUrl =
        "https://pub.dev/packages/file_saver_ffi#:~:text=Android%20Configuration"

    /**
     * Checks whether the file at the given URI is accessible for reading.
     *
     * Supports:
     * - content:// (MediaStore, SAF): tries opening a read-only FileDescriptor via ContentResolver.
     * - file:// (filesystem path): checks file exists and is readable.
     *
     * @param context Application context
     * @param uri URI string (content:// or file://)
     * @return true if the file is readable, false otherwise
     */
    fun canOpenFile(
        context: Context,
        uri: String,
    ): Boolean {
        return try {
            val parsedUri = uri.toUri()
            when (parsedUri.scheme) {
                "content" -> {
                    context.contentResolver.openFileDescriptor(parsedUri, "r")?.use { true } ?: false
                }

                "file" -> {
                    val path = parsedUri.path ?: return false
                    val file = File(path)
                    if (!file.exists() || !file.canRead()) return false
                    FileInputStream(file).use { true }
                }

                else -> {
                    false
                }
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
     * @param context Application context
     * @param uri Content URI or file URI string returned from save operations
     * @param mimeType Optional MIME type.
     *   - If null and uri is content://, queried from ContentResolver automatically.
     *   - If null and uri is file://, inferred from file extension when possible.
     */
    fun openFile(
        context: Context,
        uri: String,
        mimeType: String?,
    ) {
        val parsedUri = uri.toUri()
        val intentUri =
            when (parsedUri.scheme) {
                "content" -> parsedUri
                "file" -> resolveFileUri(context, parsedUri, uri)
                else -> throw IllegalArgumentException("INVALID_INPUT: Unsupported URI scheme: ${parsedUri.scheme}")
            }

        val resolvedMime =
            mimeType
                ?: context.contentResolver.getType(intentUri)
                ?: run {
                    val ext = MimeTypeMap.getFileExtensionFromUrl(intentUri.toString())
                    if (ext.isNullOrEmpty()) {
                        "*/*"
                    } else {
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "*/*"
                    }
                }

        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(intentUri, resolvedMime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "file", intentUri)
            }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw IllegalStateException(
                "NO_APP_FOUND: No app found to open this file (mimeType=$resolvedMime).",
                e,
            )
        }
    }

    private fun resolveFileUri(
        context: Context,
        parsedUri: Uri,
        originalUri: String,
    ): Uri {
        val path =
            parsedUri.path
                ?: throw IllegalArgumentException("INVALID_INPUT: Invalid file URI: $originalUri")
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

        return try {
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
}
