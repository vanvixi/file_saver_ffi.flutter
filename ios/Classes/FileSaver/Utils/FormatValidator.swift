import Foundation
import AVFoundation

enum FormatValidator {
    static func validateImageFormat(_ fileType: FileType) throws {
        let alwaysSupported = ["png", "jpg", "jpeg", "gif", "bmp", "heic", "heif", "tiff", "tif"]
        if alwaysSupported.contains(fileType.ext.lowercased()) {
            return
        }
    }

    static func validateVideoFormat(_ fileType: FileType) throws {
        let commonFormats = ["mp4", "mov", "m4v", "3gp"]
        if commonFormats.contains(fileType.ext.lowercased()) {
            return
        }
    }

    static func validateAudioFormat(_ fileType: FileType) throws {
        let commonFormats = ["mp3", "aac", "wav", "m4a", "caf"]
        if commonFormats.contains(fileType.ext.lowercased()) {
            return
        }
    }
}
