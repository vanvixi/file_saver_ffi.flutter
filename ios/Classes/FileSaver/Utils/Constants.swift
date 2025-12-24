import Foundation

enum Constants {
    static let errorInvalidFile = "INVALID_FILE"
    static let errorPermissionDenied = "PERMISSION_DENIED"
    static let errorUnsupportedFormat = "UNSUPPORTED_FORMAT"
    static let errorStorageFull = "STORAGE_FULL"
    static let errorFileExists = "FILE_EXISTS"
    static let errorFileIO = "FILE_IO_ERROR"
    static let errorPlatform = "PLATFORM_ERROR"

    static let chunkSize = 1024 * 1024
    static let maxRenameAttempts = 1000
}
