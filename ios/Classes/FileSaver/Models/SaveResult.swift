import Foundation

enum SaveResult {
    case success(filePath: String, fileUri: String)
    case failure(errorCode: String, message: String)
}
