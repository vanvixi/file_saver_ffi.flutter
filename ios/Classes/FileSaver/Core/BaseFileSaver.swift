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

    func requestPhotosPermission() throws -> Bool {
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
    }
}
