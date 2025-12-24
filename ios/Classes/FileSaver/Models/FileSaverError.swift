import Foundation

enum FileSaverError: Error {
    case permissionDenied(String)
    case fileExists(String)
    case invalidFile(String)
    case unsupportedFormat(String, details: String? = nil)
    case storageFull(String)
    case fileIO(String)
    case platformError(String)

    var code: String {
        switch self {
        case .permissionDenied: return Constants.errorPermissionDenied
        case .fileExists: return Constants.errorFileExists
        case .invalidFile: return Constants.errorInvalidFile
        case .unsupportedFormat: return Constants.errorUnsupportedFormat
        case .storageFull: return Constants.errorStorageFull
        case .fileIO: return Constants.errorFileIO
        case .platformError: return Constants.errorPlatform
        }
    }

    var message: String {
        switch self {
        case .permissionDenied(let msg): return msg
        case .fileExists(let fileName): return "File already exists: \(fileName)"
        case .invalidFile(let reason): return "Invalid file: \(reason)"
        case .unsupportedFormat(let format, let details):
            if let details = details {
                return "Unsupported format: \(format) - \(details)"
            }
            return "Unsupported format: \(format)"
        case .storageFull(let msg): return msg
        case .fileIO(let msg): return msg
        case .platformError(let msg): return msg
        }
    }
}
