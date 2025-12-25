package com.vanvixi.file_saver_ffi.utils

import android.media.MediaCodecList
import android.os.Build
import android.webkit.MimeTypeMap
import com.vanvixi.file_saver_ffi.exception.UnsupportedFormatException
import com.vanvixi.file_saver_ffi.models.FileType

object FormatValidator {
    private val containerToCodecMap = mapOf(
        // VIDEO
        "video/mp4" to listOf("video/avc", "video/hevc", "video/av01", "video/mp4v-es"),
        "video/quicktime" to listOf("video/avc", "video/hevc"), // .mov
        "video/3gpp" to listOf("video/3gpp", "video/mp4v-es"),
        "video/webm" to listOf("video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9", "video/av01"),
        "video/x-matroska" to listOf("video/avc", "video/hevc", "video/x-vnd.on2.vp9"), // .mkv

        // AUDIO
        "audio/mp4" to listOf("audio/mp4a-latm"), // .m4a
        "audio/mpeg" to listOf("audio/mpeg"),    // .mp3
        "audio/aac" to listOf("audio/mp4a-latm", "audio/aac"),
        "audio/ogg" to listOf("audio/opus", "audio/vorbis"),
        "audio/wav" to listOf("audio/raw", "audio/g711-alaw", "audio/g711-mlaw"),
        "audio/x-flac" to listOf("audio/flac")
    )

    private val encodersByType: Map<FileType.Category, Set<String>> by lazy {
        val result = mutableMapOf<FileType.Category, MutableSet<String>>(
            FileType.Category.IMAGE to mutableSetOf(),
            FileType.Category.VIDEO to mutableSetOf(),
            FileType.Category.AUDIO to mutableSetOf()
        )

        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) continue

            for (mime in codecInfo.supportedTypes) {
                when {
                    mime.startsWith("image/") ->
                        result[FileType.Category.IMAGE]?.add(mime)

                    mime.startsWith("video/") ->
                        result[FileType.Category.VIDEO]?.add(mime)

                    mime.startsWith("audio/") ->
                        result[FileType.Category.AUDIO]?.add(mime)

                }
            }
        }
        result
    }

    /**
     * Validates image format support for SAVING/ENCODING.
     */
    fun validateImageFormat(fileType: FileType) {
        if (!fileType.isImage) {
            throw IllegalStateException("Expected image MIME type")
        }

        val ext = fileType.ext

        val alwaysSupported = setOf("png", "jpeg", "jpg", "gif", "bmp")
        if (ext in alwaysSupported) return

        checkMinimumApiForNewImageFormats(ext)

        val mimesToCheck = when (ext) {
            "heic", "heif" -> listOf("image/heic", "image/heif", "image/vnd.android.heic")
            "webp" -> listOf("image/webp")
            "avif" -> listOf("image/avif")
            else -> listOfNotNull(
                fileType.mimeType,
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            )
        }

        val availableEncoders = encodersByType[FileType.Category.IMAGE] ?: emptySet()

        val isSupported =
            availableEncoders.isNotEmpty() && mimesToCheck.any { it in availableEncoders }

        if (!isSupported) {
            throw UnsupportedFormatException(
                format = ext.uppercase(),
                message = "The device does not support an encoder for the $ext format."
            )
        }
    }

    fun validateVideoFormat(videoType: FileType) = validateMedia(videoType, FileType.Category.VIDEO)
    fun validateAudioFormat(audioType: FileType) = validateMedia(audioType, FileType.Category.AUDIO)

    /**
     * Validates VIDEO/AUDIO format support for SAVING/ENCODING.
     */
    private fun validateMedia(fileType: FileType, category: FileType.Category) {
        if (fileType.category != category) {
            throw IllegalStateException("Expected ${category.name.lowercase()} MIME type")
        }
        val ext = fileType.ext
        val mimeType = fileType.mimeType

        val availableEncoders = encodersByType[category] ?: emptySet()

        val codecsToCheck = containerToCodecMap[mimeType] ?: listOf(mimeType)

        val isSupported = codecsToCheck.any { it in availableEncoders }

        if (!isSupported) {
            throw UnsupportedFormatException(
                format = ext.uppercase(),
                message = "The device does not support an encoder for the $ext format."
            )
        }
    }

    private fun checkMinimumApiForNewImageFormats(ext: String) {
        val (minApi, label) = when (ext) {
            "heic", "heif" -> Build.VERSION_CODES.P to "Android 9 (API 28)"
            "avif" -> Build.VERSION_CODES.S to "Android 12 (API 31)"
            else -> null to ""
        }

        if (minApi != null && Build.VERSION.SDK_INT < minApi) {
            throw UnsupportedFormatException(
                format = ext.uppercase(),
                message = "$ext format requires at least the $label."
            )
        }
    }
}


