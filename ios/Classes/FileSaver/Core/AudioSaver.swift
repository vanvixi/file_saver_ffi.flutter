import Foundation

class AudioSaver: BaseFileSaver {
    private let baseDirectory = "Audio"

    func saveBytes(
        fileData: Data,
        fileType: FileType,
        baseFileName: String,
        subDir: String?,
        conflictResolution: ConflictResolution
    ) throws -> SaveResult {
        try FormatValidator.validateAudioFormat(fileType)
        try validateFileData(fileData)

        let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        var targetDir = documentsURL.appendingPathComponent(baseDirectory)

        if let subDir = subDir {
            targetDir = targetDir.appendingPathComponent(subDir)
        }

        try FileHelper.ensureDirectoryExists(at: targetDir)

        let fileName = buildFileName(base: baseFileName, extension: fileType.ext)
        let finalURL = try FileManagerConflictResolver.resolveConflict(
            directory: targetDir,
            fileName: fileName,
            conflictResolution: conflictResolution
        )

        try fileData.write(to: finalURL, options: .atomic)

        return .success(filePath: finalURL.path, fileUri: finalURL.absoluteString)
    }
}
