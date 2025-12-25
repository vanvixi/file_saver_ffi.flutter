import Foundation
import Photos

protocol BaseFileSaver {
    func saveBytes(
        fileData: Data,
        fileType: FileType,
        baseFileName: String,
        subDir: String?,
        conflictResolution: ConflictResolution
    ) throws -> SaveResult
}

extension BaseFileSaver {
    func validateFileData(_ fileData: Data) throws {
        guard !fileData.isEmpty else {
            throw FileSaverError.invalidFile("File data is empty")
        }
    }

    func buildFileName(base: String, extension ext: String) -> String {
        return FileHelper.buildFileName(fileName: base, extension: ext)
    }

    func handleError(_ error: Error) -> SaveResult {
        if let fsError = error as? FileSaverError {
            return .failure(errorCode: fsError.code, message: fsError.message)
        }
        return .failure(errorCode: Constants.errorPlatform, message: error.localizedDescription)
    }

    /// Requests photo library permission from the user.
    ///
    /// On iOS 14+, this requests `.addOnly` permission (scoped access).
    /// On iOS 13, this requests full photo library access (legacy behavior).
    ///
    /// - Returns: `true` if user has full access, `false` if limited access (iOS 14+ only)
    /// - Throws: `FileSaverError.permissionDenied` if permission is denied
    ///
    /// - Note: iOS 13 always returns `true` when permission is granted, as it only supports full access
    func requestPhotosPermission() throws -> Bool {
        if #available(iOS 14, *) {
            // iOS 14+ - Use scoped photo library access with .addOnly permission
            var status = PHPhotoLibrary.authorizationStatus(for: .addOnly)

            if status == .notDetermined {
                var result: PHAuthorizationStatus = .notDetermined
                let semaphore = DispatchSemaphore(value: 0)

                PHPhotoLibrary.requestAuthorization(for: .addOnly) { authStatus in
                    result = authStatus
                    semaphore.signal()
                }

                semaphore.wait()
                status = result

                // Small delay to ensure the status is updated
                Thread.sleep(forTimeInterval: 0.5)
            }

            guard status == .authorized || status == .limited else {
                throw FileSaverError.permissionDenied("Photo library access denied")
            }

            return status == .authorized
        } else {
            // iOS 13 fallback - Use legacy authorization API
            var status = PHPhotoLibrary.authorizationStatus()

            if status == .notDetermined {
                var result: PHAuthorizationStatus = .notDetermined
                let semaphore = DispatchSemaphore(value: 0)

                PHPhotoLibrary.requestAuthorization { authStatus in
                    result = authStatus
                    semaphore.signal()
                }

                semaphore.wait()
                status = result

                // Small delay to ensure the status is updated
                Thread.sleep(forTimeInterval: 0.5)
            }

            // iOS 13 only has .authorized status (no .limited)
            guard status == .authorized else {
                throw FileSaverError.permissionDenied("Photo library access denied")
            }

            // iOS 13 always has full access when authorized
            return true
        }
    }
}
