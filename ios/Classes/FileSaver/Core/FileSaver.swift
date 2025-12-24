import Foundation

class FileSaver {
    let imageSaver = ImageSaver()
    let videoSaver = VideoSaver()
    let audioSaver = AudioSaver()
    let customFileSaver = CustomFileSaver()

    func saveBytes(
        fileData: Data,
        baseFileName: String,
        extension ext: String,
        mimeType: String,
        subDir: String?,
        conflictMode: Int
    ) -> SaveResult {
        do {
            let fileType = FileHelper.getFileType(ext: ext, mimeType: mimeType)

            guard let conflictResolution = ConflictResolution(rawValue: conflictMode) else {
                return .failure(
                    errorCode: Constants.errorPlatform,
                    message: "Invalid conflict resolution mode: \(conflictMode)"
                )
            }

            if fileType.category == .image {
                return try imageSaver.saveBytes(
                    fileData: fileData,
                    fileType: fileType,
                    baseFileName: baseFileName,
                    subDir: subDir,
                    conflictResolution: conflictResolution
                )
            }

            if fileType.category == .video {
                return try videoSaver.saveBytes(
                    fileData: fileData,
                    fileType: fileType,
                    baseFileName: baseFileName,
                    subDir: subDir,
                    conflictResolution: conflictResolution
                )
            }

            if fileType.category == .audio {
                return try audioSaver.saveBytes(
                    fileData: fileData,
                    fileType: fileType,
                    baseFileName: baseFileName,
                    subDir: subDir,
                    conflictResolution: conflictResolution
                )
            }

            return try customFileSaver.saveBytes(
                fileData: fileData,
                fileType: fileType,
                baseFileName: baseFileName,
                subDir: subDir,
                conflictResolution: conflictResolution
            )
        } catch let error as FileSaverError {
            return .failure(errorCode: error.code, message: error.message)
        } catch {
            return .failure(
                errorCode: Constants.errorPlatform,
                message: "Unexpected error: \(error.localizedDescription)"
            )
        }
    }
}
