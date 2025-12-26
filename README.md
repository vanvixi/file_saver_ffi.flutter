<p align="center">
  <img alt="cover" src="https://github.com/user-attachments/assets/e6fdc6d2-abcd-49c2-9537-ec960e4cf082" />
</p>

## File Saver FFI
<p align="left">
  <a href="https://pub.dev/packages/file_saver_ffi"><img src="https://img.shields.io/pub/v/file_saver_ffi.svg" alt="Pub"></a>
  <a href="https://github.com/vanvixi/file_saver_ffi"><img src="https://img.shields.io/badge/platform-Android%20%7C%20iOS-blue.svg" alt="Platform"></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/license-MIT-purple.svg" alt="License: MIT"></a>
</p>

A high-performance Flutter plugin for saving files, images, and videos to device storage using native APIs via FFI (iOS)
and JNI (Android).

## Features

- ⚡ **Native Performance** - FFI (iOS) and JNI (Android) for maximum speed
- 📁 **Universal File Saving** - Save any file type to device storage with a single method
- 🖼️ **Image-Specific Handling** - Format validation and album support
- 🎥 **Video Handling** - Native integration with Photos (iOS) and MediaStore (Android)
- ⚙️ **Conflict Resolution** - Auto-rename, overwrite, skip, or fail on conflicts
- 🎯 **Album/Subdirectory Support** - Organize all file types in albums (iOS) or subdirectories (Android)
- 💾 **Original Quality** - Always saves at original quality, no compression
- 🔒 **Type-Safe API** - Sealed classes and pattern matching for robust code
- 📂 **Smart Location Routing** - Files automatically saved to appropriate directories based on type

If you want to say thank you, star us on GitHub or like us on pub.dev.

## Supported Platforms

| Platform    | Minimum Version        | Notes                               |
|-------------|------------------------|-------------------------------------|
| **Android** | API 21+ (Android 5.0+) | Scoped storage for Android 10+      |
| **iOS**     | 13.0+                  | Photos framework with album support |

## Setup

### Android Configuration

Add to `android/app/src/main/AndroidManifest.xml`:

```xml
<!-- Only required for Android 9 (API 28) and below -->
<uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28"/>
```

**Note:** Android 10+ (API 29+) uses scoped storage automatically and does not require this permission.

### iOS Configuration

Add to `ios/Runner/Info.plist`:

#### For Photos Library Access (Required for images/videos)

```xml

<key>NSPhotoLibraryAddUsageDescription</key>
<string>This app needs permission to save photos and videos to your library</string>

<key>NSPhotoLibraryUsageDescription</key>
<string>This app needs permission to access your photo library</string>
```

#### For Files App Visibility (Optional for custom files)

Files are saved to the Application Documents Directory. To make them visible to users in the Files app, add:

```xml

<key>UIFileSharingEnabled</key>
<true/>

<key>LSSupportsOpeningDocumentsInPlace</key>
<true/>
```

## Quick Start

```dart
import 'package:file_saver_ffi/file_saver_ffi.dart';

try {
  // Save image bytes
  final uri = await FileSaver.instance.saveBytes(
    bytes: imageBytes,
    fileName: 'my_image',
    fileType: ImageType.jpg,
  );
  
    print('Saved to: $uri');
  } on PermissionDeniedException catch (e) {
    print('Permission denied: ${e.message}');
  } on FileSaverException catch (e) {
    print('Save failed: ${e.message}');
}
```

## Resource Management

`FileSaver` uses native resources via FFI (iOS) and JNI (Android). The library provides **automatic cleanup** via `NativeFinalizer`, but you can also manually release resources if needed.

### Manual Disposal

If you want to release native resources immediately (e.g., to free memory sooner), call `dispose()`:

```dart
// Release resources immediately when you're done
FileSaver.instance.dispose();
```

### App Lifecycle Integration (Optional)

For explicit cleanup when the app terminates, you can use `WidgetsBindingObserver`:

```dart
import 'package:file_saver_ffi/file_saver_ffi.dart';
import 'package:flutter/material.dart';

class AppLifecycleObserver extends WidgetsBindingObserver {
  final VoidCallback? onDetached;

  AppLifecycleObserver({this.onDetached});

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.detached) {
      onDetached?.call();
    }
  }
}

void main() {
  final binding = WidgetsFlutterBinding.ensureInitialized();
  
  binding.addObserver(
    AppLifecycleObserver(
      onDetached: FileSaver.instance.dispose,
    ),
  );
  
  runApp(const MyApp());
}
```

> **Note:** `AppLifecycleState.detached` is not guaranteed to be called on all platforms when the app is force-killed. However, the OS will automatically reclaim all memory when the process terminates, so this is primarily for explicit cleanup in normal shutdown scenarios.
```

## Supported File Types

### Images (12 formats)

`PNG`, `JPG`, `JPEG`, `GIF`, `WebP`, `BMP`, `HEIC`, `HEIF`, `TIFF`, `TIF`, `ICO`, `DNG`

```dart
ImageType.png
ImageType.jpg
ImageType.gif
ImageType.webp
// ... and more
```

### Videos (12 formats)

`MP4`, `3GP`, `WebM`, `M4V`, `MKV`, `MOV`, `AVI`, `FLV`, `WMV`, `HEVC`, `VP9`, `AV1`

```dart
VideoType.mp4
VideoType.mov
VideoType.mkv
// ... and more
```

### Audio (11 formats)

`MP3`, `AAC`, `WAV`, `AMR`, `3GP`, `M4A`, `OGG`, `FLAC`, `Opus`, `AIFF`, `CAF`

```dart
AudioType.mp3
AudioType.aac
AudioType.wav
// ... and more
```

### Custom File Types

Support any file format by specifying extension and MIME type:

```dart
CustomFileType(
  ext: 'pdf',
  mimeType: 'application/pdf'
)
CustomFileType(
  ext: 'docx', 
  mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
)
```

## Conflict Resolution Strategies

Control what happens when a file with the same name already exists:

| Strategy               | Behavior                                       | Use Case                 |
|------------------------|------------------------------------------------|--------------------------|
| `autoRename` (default) | Appends (1), (2), etc. to filename             | Safe, prevents data loss |
| `overwrite`            | Replaces existing file                         | Update existing files    |
| `fail`                 | Returns `SaveFailure` with "FILE_EXISTS" error | Strict validation        |
| `skip`                 | Returns `SaveSuccess` with existing file path  | Idempotent saves         |

### Example

```dart
try {
  final uri = await FileSaver.instance.saveBytes(
    bytes: fileBytes,
    fileName: 'document',
    fileType: CustomFileType(ext: 'pdf', mimeType: 'application/pdf'),
    conflictResolution: ConflictResolution.autoRename,
  );
  
    // If "document.pdf" exists, saves as "document (1).pdf"
    print('Saved to: $uri');
  } on FileSaverException catch (e) {
    print('Error: ${e.message}');
}
```

## Advanced Usage

### Save with Subdirectory/Album

```dart
try {
  final uri = await FileSaver.instance.saveBytes(
    bytes: videoBytes,
    fileName: 'vacation_video',
    fileType: VideoType.mp4,
    subDir: 'My Vacations', // Creates album on iOS, folder on Android
  );
  
    print('Video saved to: $uri');
  } on FileSaverException catch (e) {
    print('Error: ${e.message}');
}
```

### Complete Example with Error Handling

```dart
try {
  final uri = await FileSaver.instance.saveBytes(
    bytes: pdfBytes,
    fileName: 'invoice_${DateTime.now().millisecondsSinceEpoch}',
    fileType: CustomFileType(ext: 'pdf', mimeType: 'application/pdf'),
    subDir: 'Invoices',
    conflictResolution: ConflictResolution.autoRename,
  );
  
    print('✅ Saved successfully!');
    print('URI: $uri');
  
} on PermissionDeniedException catch (e) {
  print('❌ Permission denied: ${e.message}');
  // Request permissions

} on FileExistsException catch (e) {
  print('❌ File already exists: ${e.fileName}');
  // Handle conflict

} on StorageFullException catch (e) {
  print('❌ Storage full: ${e.message}');
  // Show storage full message

} on InvalidFileException catch (e) {
  print('❌ Invalid file: ${e.message}');
  // Validate file data

} on FileSaverException catch (e) {
  print('❌ Save failed: ${e.message}');
  // Generic error handling
}
```

## Platform-Specific Behavior

### File Storage Locations

#### Android

Files are saved to MediaStore collections based on type:

| File Type    | Location              |
|--------------|-----------------------|
| Images       | `Pictures/[subDir]/`  |
| Videos       | `Movies/[subDir]/`    |
| Audio        | `Music/[subDir]/`     |
| Custom Files | `Downloads/[subDir]/` |

**URI Format:** `content://media/external/...`

#### iOS

Files are saved to platform-appropriate locations:

| File Type    | Location                                                   |
|--------------|------------------------------------------------------------|
| Images       | Photos library album `[subDir]`                            |
| Videos       | Photos library album `[subDir]`                            |
| Audio        | Photos library (if supported)                              |
| Custom Files | `Documents/[subDir]/` (visible in Files app if configured) |

**URI Format:** `ph://` for Photos, `file://` for Documents

### SubDir Parameter

- **iOS:** Creates an album in the Photos app with the specified name
- **Android:** Creates a folder in the appropriate MediaStore collection

**Example:**

```dart
// iOS: Creates "My App" album in Photos
// Android: Creates Pictures/My App/ folder
subDir: 'My App'
```

## Error Handling

The library provides specific exception types for different failure scenarios:

| Exception                    | Description                      | Error Code           |
|------------------------------|----------------------------------|----------------------|
| `PermissionDeniedException`  | Storage access denied            | `PERMISSION_DENIED`  |
| `FileExistsException`        | File exists with `fail` strategy | `FILE_EXISTS`        |
| `StorageFullException`       | Insufficient device storage      | `STORAGE_FULL`       |
| `InvalidFileException`       | Empty bytes or invalid filename  | `INVALID_FILE`       |
| `FileIOException`            | File system error                | `FILE_IO`            |
| `UnsupportedFormatException` | Format not supported on platform | `UNSUPPORTED_FORMAT` |
| `PlatformException`          | Generic platform-specific error  | `PLATFORM_ERROR`     |

### Handling Errors

```dart
try {
  final uri = await FileSaver.instance.saveBytes(...);
  print('Saved to: $uri');

} on PermissionDeniedException catch (e) {
    // Request permissions

} on StorageFullException catch (e) {
  // Show storage full message

} on FileExistsException catch (e) {
  // File already exists: ${e.fileName}

} on FileSaverException catch (e) {
  // Generic error handling
}
```

## API Reference

### FileSaver

Singleton API class for saving files.

```dart
Future<Uri> saveBytes({
  required Uint8List bytes,
  required String fileName,
  required FileType fileType,
  String? subDir,
  ConflictResolution conflictResolution = ConflictResolution.autoRename,
})

Throws FileSaverException or subtypes on failure.
```

### ConflictResolution

Enum for conflict resolution strategies:

```dart
enum ConflictResolution {
  autoRename, // Append (1), (2), etc.
  overwrite, // Replace existing file
  fail, // Return error
  skip, // Return existing file path
}
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Future Features
* File Input Methods
* Save from Network URL
* User-Selected Location Android (SAF), iOS (Document Picker)
* Custom Path Support
* Progress Tracking
* MacOS Support
* Windows Support
* Web Support
