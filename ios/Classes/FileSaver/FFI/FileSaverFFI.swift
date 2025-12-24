import Foundation

private var instanceCounter: UInt = 0
private var instances: [UInt: FileSaver] = [:]
private let instanceLock = NSLock()

@_cdecl("file_saver_init")
public func fileSaverInit() -> UInt {
    instanceLock.lock()
    defer { instanceLock.unlock() }

    instanceCounter += 1
    let id = instanceCounter
    instances[id] = FileSaver()

    return id
}

@_cdecl("file_saver_save_bytes_async")
public func fileSaverSaveBytesAsync(
    _ instanceId: UInt,
    _ fileData: UnsafePointer<UInt8>,
    _ fileDataLength: Int64,
    _ baseFileName: UnsafePointer<CChar>,
    _ ext: UnsafePointer<CChar>,
    _ mimeType: UnsafePointer<CChar>,
    _ subDir: UnsafePointer<CChar>?,
    _ conflictMode: Int32,
    _ callback: @escaping @convention(c) (UnsafeMutablePointer<FSaveResult>) -> Void
) {
    DispatchQueue.global(qos: .userInitiated).async {
        let result = performSave(
            instanceId: instanceId,
            fileData: fileData,
            fileDataLength: fileDataLength,
            baseFileName: baseFileName,
            ext: ext,
            mimeType: mimeType,
            subDir: subDir,
            conflictMode: conflictMode
        )

        let ffiResult = UnsafeMutablePointer<FSaveResult>.allocate(capacity: 1)
        ffiResult.pointee = result.toFFI()

        callback(ffiResult)
    }
}

@_cdecl("file_saver_free_result")
public func fileSaverFreeResult(_ result: UnsafeMutablePointer<FSaveResult>) {
    if let filePath = result.pointee.filePath {
        filePath.deallocate()
    }
    if let fileUri = result.pointee.fileUri {
        fileUri.deallocate()
    }
    if let errorCode = result.pointee.errorCode {
        errorCode.deallocate()
    }
    if let errorMessage = result.pointee.errorMessage {
        errorMessage.deallocate()
    }

    result.deallocate()
}

@_cdecl("file_saver_dispose")
public func fileSaverDispose(_ instanceId: UInt) {
    instanceLock.lock()
    defer { instanceLock.unlock() }

    instances.removeValue(forKey: instanceId)
}

private func performSave(
    instanceId: UInt,
    fileData: UnsafePointer<UInt8>,
    fileDataLength: Int64,
    baseFileName: UnsafePointer<CChar>,
    ext: UnsafePointer<CChar>,
    mimeType: UnsafePointer<CChar>,
    subDir: UnsafePointer<CChar>?,
    conflictMode: Int32
) -> SaveResult {
    instanceLock.lock()
    guard let saver = instances[instanceId] else {
        instanceLock.unlock()
        return .failure(
            errorCode: Constants.errorPlatform,
            message: "FileSaver instance not found"
        )
    }
    instanceLock.unlock()

    let data = Data(bytes: fileData, count: Int(fileDataLength))
    let fileName = String(cString: baseFileName)
    let extStr = String(cString: ext)
    let mime = String(cString: mimeType)
    let directory = subDir.map { String(cString: $0) }

    return saver.saveBytes(
        fileData: data,
        baseFileName: fileName,
        extension: extStr,
        mimeType: mime,
        subDir: directory,
        conflictMode: Int(conflictMode)
    )
}

extension SaveResult {
    func toFFI() -> FSaveResult {
        switch self {
        case .success(let filePath, let fileUri):
            return FSaveResult(
                success: true,
                filePath: strdup(filePath),
                fileUri: strdup(fileUri),
                errorCode: nil,
                errorMessage: nil
            )
        case .failure(let errorCode, let message):
            return FSaveResult(
                success: false,
                filePath: nil,
                fileUri: nil,
                errorCode: strdup(errorCode),
                errorMessage: strdup(message)
            )
        }
    }
}
