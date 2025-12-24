import Foundation

struct FileType {
    let ext: String
    let mimeType: String

    enum Category {
        case image
        case video
        case audio
        case custom
    }

    var category: Category {
        if mimeType.starts(with: "image/") {
            return .image
        } else if mimeType.starts(with: "video/") {
            return .video
        } else if mimeType.starts(with: "audio/") {
            return .audio
        } else {
            return .custom
        }
    }
}
