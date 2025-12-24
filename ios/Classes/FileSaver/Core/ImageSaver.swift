import Foundation
import Photos

class ImageSaver: BaseFileSaver {
    func saveBytes(
        fileData: Data,
        fileType: FileType,
        baseFileName: String,
        subDir: String?,
        conflictResolution: ConflictResolution
    ) throws -> SaveResult {
        try FormatValidator.validateImageFormat(fileType)
        try validateFileData(fileData)

        let hasReadAccess = try requestPhotosPermission()

        let fileName = buildFileName(base: baseFileName, extension: fileType.ext)

        if hasReadAccess {
            if conflictResolution == .skip || conflictResolution == .fail {
                if let existing = PhotosConflictResolver.findExistingAsset(fileName: fileName, inAlbum: subDir) {
                    if conflictResolution == .fail {
                        throw FileSaverError.fileExists(fileName)
                    }
                    return .success(filePath: existing.localIdentifier, fileUri: "ph://\(existing.localIdentifier)")
                }
            }

            if conflictResolution == .overwrite {
                if let existing = PhotosConflictResolver.findExistingAsset(fileName: fileName, inAlbum: subDir) {
                    try PhotosConflictResolver.overwriteAsset(existing)
                }
            }
        }

        return try saveToPhotosLibrary(imageData: fileData, fileName: fileName, albumName: hasReadAccess ? subDir : nil)
    }

    private func saveToPhotosLibrary(imageData: Data, fileName: String, albumName: String?) throws -> SaveResult {
        let album = try albumName.map { try findOrCreateAlbum(name: $0) }

        var assetId: String?

        do {
            try PHPhotoLibrary.shared().performChangesAndWait {
                let request = PHAssetCreationRequest.forAsset()
                let options = PHAssetResourceCreationOptions()
                options.originalFilename = fileName
                request.addResource(with: .photo, data: imageData, options: options)

                if let album = album {
                    if let placeholder = request.placeholderForCreatedAsset {
                        let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
                        albumChangeRequest?.addAssets([placeholder] as NSArray)
                    }
                }

                assetId = request.placeholderForCreatedAsset?.localIdentifier
            }
        } catch {
            throw FileSaverError.fileIO("Failed to save image: \(error.localizedDescription)")
        }

        guard let assetId = assetId else {
            throw FileSaverError.fileIO("Failed to save image to Photos library")
        }

        return .success(filePath: assetId, fileUri: "ph://\(assetId)")
    }

    private func findOrCreateAlbum(name: String) throws -> PHAssetCollection {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "title = %@", name)
        let collections = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: options)

        if let existing = collections.firstObject {
            return existing
        }

        var albumId: String?
        try PHPhotoLibrary.shared().performChangesAndWait {
            let request = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
            albumId = request.placeholderForCreatedAssetCollection.localIdentifier
        }

        guard let albumId = albumId,
              let album = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [albumId], options: nil).firstObject else {
            throw FileSaverError.fileIO("Failed to create album: \(name)")
        }

        return album
    }
}
