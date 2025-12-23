package com.vanvixi.file_saver_ffi.exception

/**
 * Exception thrown when a file format is not supported on the current device
 *
 * @property format The unsupported file format (e.g., "HEIC", "MKV")
 * @property message Detailed error message explaining why the format is not supported
 */
class UnsupportedFormatException(
    val format: String,
    message: String
) : Exception(message)

class FileExistsException(
    message: String
) : Exception(message)