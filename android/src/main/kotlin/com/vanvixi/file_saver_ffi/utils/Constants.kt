package com.vanvixi.file_saver_ffi.utils


object Constants {
    // ===========================================
    // Error Codes
    // ===========================================

    /**
     * File data is empty or invalid
     */
    const val ERROR_INVALID_FILE = "INVALID_FILE"

    /**
     * Storage permission denied by user
     */
    const val ERROR_PERMISSION_DENIED = "PERMISSION_DENIED"

    /**
     * File format not supported on this Android version
     * Example: HEIC/HEIF on Android 9 and below
     */
    const val ERROR_UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"

    /**
     * Insufficient storage space available
     */
    const val ERROR_STORAGE_FULL = "STORAGE_FULL"

    /**
     * File already exists and conflict mode is FAIL
     */
    const val ERROR_FILE_EXISTS = "FILE_EXISTS"

    /**
     * File I/O error (read/write failed)
     */
    const val ERROR_FILE_IO = "FILE_IO_ERROR"

    /**
     * Generic platform error
     */
    const val ERROR_PLATFORM = "PLATFORM_ERROR"

    /**
     * Chunk size for file writing: 1MB
     * Used to split large files into manageable chunks
     */
    const val CHUNK_SIZE = 1024 * 1024 // 1MB

    /**
     * Progress throttle percentage: 5%
     * Report progress every 5% of total size
     */
    const val PROGRESS_THROTTLE_PERCENTAGE = 5 // 5%

    /**
     * Progress throttle bytes: 5MB
     * Report progress every 5MB written
     */
    const val PROGRESS_THROTTLE_BYTES = 5 * 1024 * 1024L // 5MB

    // ===========================================
    // File Naming
    // ===========================================

    /**
     * Maximum number of attempts for auto-rename
     * Pattern: file.txt → file (1).txt → file (2).txt → ... → file (1000).txt
     */
    const val MAX_RENAME_ATTEMPTS = 1000
}
