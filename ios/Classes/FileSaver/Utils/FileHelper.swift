import Foundation

enum FileHelper {
    static func getFileType(ext: String, mimeType: String) -> FileType {
        let normalizedExt = ext.lowercased().replacingOccurrences(of: ".", with: "").trimmingCharacters(in: .whitespaces)
        return FileType(ext: normalizedExt, mimeType: mimeType)
    }

    static func buildFileName(fileName: String, extension ext: String) -> String {
        let cleanExt = ext.replacingOccurrences(of: ".", with: "").trimmingCharacters(in: .whitespaces)
        return cleanExt.isEmpty ? fileName : "\(fileName).\(cleanExt)"
    }

    static func writeFile(data: Data, to url: URL) throws {
        let fileHandle = try FileHandle(forWritingTo: url)
        defer { try? fileHandle.close() }

        var offset = 0
        let chunkSize = Constants.chunkSize

        while offset < data.count {
            let length = min(chunkSize, data.count - offset)
            let chunk = data.subdata(in: offset..<(offset + length))
            try fileHandle.write(contentsOf: chunk)
            offset += length
        }
    }

    static func ensureDirectoryExists(at url: URL) throws {
        let fileManager = FileManager.default

        if fileManager.fileExists(atPath: url.path) {
            var isDirectory: ObjCBool = false
            fileManager.fileExists(atPath: url.path, isDirectory: &isDirectory)

            if !isDirectory.boolValue {
                throw FileSaverError.fileIO("Path exists but is not a directory: \(url.path)")
            }
        } else {
            try fileManager.createDirectory(at: url, withIntermediateDirectories: true, attributes: nil)
        }
    }
}
