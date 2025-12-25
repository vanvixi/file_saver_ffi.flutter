import Foundation
import AVFoundation
import ImageIO
import MobileCoreServices

enum FormatValidator {

    // Cache the list of UTIs that ImageIO supports writing
    private static var supportedImageUTIs: [String] = {
        return CGImageDestinationCopyTypeIdentifiers() as? [String] ?? []
    }()

    static func validateImageFormat(_ fileType: FileType) throws {
        guard fileType.isImage else {
            throw FileSaverError.platformError("Expected image MIME type")
        }

        let ext = fileType.ext

        guard let uti = UTTypeCreatePreferredIdentifierForTag(
            kUTTagClassFilenameExtension,
            ext as CFString,
            nil
        )?.takeRetainedValue() else {
            throw FileSaverError.unsupportedFormat(
                ext.uppercased(),
                details: "Cannot resolve UTI from extension"
            )
        }

        let utiString = uti as String
        if !supportedImageUTIs.contains(utiString) {
            throw FileSaverError.unsupportedFormat(
                ext.uppercased(),
                details: "ImageIO cannot encode this format"
            )
        }
    }

    static func validateVideoFormat(_ fileType: FileType) throws {
        try validateMediaFormat(fileType, expected: .video)
    }

    static func validateAudioFormat(_ fileType: FileType) throws {
        try validateMediaFormat(fileType, expected: .audio)
    }

    // Core media validation for Video and Audio
    private static func validateMediaFormat(
        _ fileType: FileType,
        expected: FileType.Category
    ) throws {

        guard fileType.category == expected else {
            throw FileSaverError.platformError(
                "Expected \(expected) MIME type"
            )
        }

        guard let uti = uti(fromExtension: fileType.ext) else {
            throw FileSaverError.unsupportedFormat(
                fileType.ext.uppercased(),
                details: "Cannot resolve UTI from extension"
            )
        }

        let avFileType = AVFileType(uti)

        guard let preferredExt = preferredExtension(fromUTI: uti) else {
            throw FileSaverError.unsupportedFormat(
                fileType.ext.uppercased(),
                details: "Cannot resolve preferred file extension"
            )
        }

        let testURL = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension(preferredExt)

        guard (try? AVAssetWriter(outputURL: testURL, fileType: avFileType)) != nil else {
            throw FileSaverError.unsupportedFormat(
                fileType.ext.uppercased(),
                details: "AVAssetWriter cannot encode this media container"
            )
        }
    }
    

    // MARK: - UTI HELPERS (iOS 13 compatible)
    private static func uti(fromExtension ext: String) -> String? {
        UTTypeCreatePreferredIdentifierForTag(
            kUTTagClassFilenameExtension,
            ext as CFString,
            nil
        )?.takeRetainedValue() as String?
    }

    private static func preferredExtension(fromUTI uti: String) -> String? {
        UTTypeCopyPreferredTagWithClass(
            uti as CFString,
            kUTTagClassFilenameExtension
        )?.takeRetainedValue() as String?
    }
}
