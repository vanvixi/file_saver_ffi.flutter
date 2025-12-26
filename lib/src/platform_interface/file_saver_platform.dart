import 'package:flutter/foundation.dart';

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
  static FileSaverPlatform? _instance;

  /// Get the appropriate platform instance based on the current platform.
  static FileSaverPlatform get instance {
    _instance ??= switch (defaultTargetPlatform) {
      TargetPlatform.android => FileSaverAndroid(),
      TargetPlatform.iOS => FileSaverIos(),
      _ =>
        throw UnsupportedError(
          'FileSaver is not supported on ${defaultTargetPlatform.toString()}',
        ),
    };
    return _instance!;
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
