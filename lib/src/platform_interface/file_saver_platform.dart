import 'dart:io';
import 'dart:typed_data';

import '../models/conflict_resolution.dart';
import '../models/file_type.dart';
import '../platforms/android/file_saver_android.dart';
import '../platforms/ios/file_saver_ios.dart';

/// Platform interface for file saver implementations.
///
/// This abstract class defines the contract that platform-specific
/// implementations (iOS and Android) must implement.
///
/// Platform implementations:
/// - iOS: Uses FFI to call Objective-C code
/// - Android: Uses JNI to call Kotlin code
abstract class FileSaverPlatform {
  /// Get the appropriate platform instance based on the current platform.
  ///
  /// Returns:
  /// - [FileSaverIos] if running on iOS
  /// - [FileSaverAndroid] if running on Android
  /// - Throws [UnsupportedError] if running on unsupported platform
  static FileSaverPlatform get instance {
    if (Platform.isAndroid) {
      return FileSaverAndroid();
    } else if (Platform.isIOS) {
      return FileSaverIos();
    } else {
      throw UnsupportedError(
        'Platform not supported: ${Platform.operatingSystem}',
      );
    }
  }

  /// Disposes resources
  void dispose();

  /// Saves a file bytes to device storage.
  ///
  /// Parameters:
  /// - [fileBytes]: The file data to save
  /// - [fileType]: The type of file being saved (determines extension and MIME type)
  /// - [fileName]: The name of the file (without extension, extension is determined by [fileType])
  /// - [conflictResolution]: How to handle filename conflicts
  /// - [subDir]: Optional subdirectory within the standard save location
  ///
  /// Returns:
  /// - [SaveSuccess] with file path and URI if successful
  /// - [SaveFailure] with error details if failed
  ///
  Future<Uri> saveBytes({
    required Uint8List fileBytes,
    required FileType fileType,
    required String fileName,
    String? subDir,
    ConflictResolution conflictResolution = ConflictResolution.autoRename,
  });
}
