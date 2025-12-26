library;

import 'dart:typed_data';

// Public API - FileSaver class
import 'src/models/conflict_resolution.dart';
import 'src/models/file_type.dart';
import 'src/platform_interface/file_saver_platform.dart';

// Exceptions
export 'src/exceptions/file_saver_exceptions.dart';

// Models
export 'src/models/conflict_resolution.dart';
export 'src/models/file_type.dart';

class FileSaver {
  FileSaver._();

  static final FileSaver instance = FileSaver._();

  FileSaverPlatform get _platform => FileSaverPlatform.instance;

  /// Resources are automatically released on app termination,
  /// but call dispose() for timely cleanup.
  void dispose() {
    _platform.dispose();
  }

  /// Saves file bytes to device storage.
  ///
  /// Returns the [Uri] where the file was saved.
  ///
  /// Throws [FileSaverException] or one of its subtypes on failure:
  /// - [PermissionDeniedException] - Storage permission denied
  /// - [FileExistsException] - File exists with [ConflictResolution.fail] strategy
  /// - [StorageFullException] - Insufficient device storage
  /// - [InvalidFileException] - Invalid file data or filename
  /// - [FileIOException] - File I/O operation failed
  /// - [UnsupportedFormatException] - Format not supported on platform
  /// - [PlatformException] - Generic platform-specific error
  ///
  /// Example:
  /// ```dart
  /// try {
  ///   final uri = await FileSaver.instance.saveBytes(
  ///     bytes: imageBytes,
  ///     fileName: 'photo',
  ///     fileType: ImageType.jpg,
  ///   );
  ///   print('Saved to: $uri');
  /// } on PermissionDeniedException catch (e) {
  ///   print('Permission denied: ${e.message}');
  /// } on FileSaverException catch (e) {
  ///   print('Save failed: ${e.message}');
  /// }
  /// ```
  Future<Uri> saveBytes({
    required Uint8List bytes,
    required FileType fileType,
    required String fileName,
    String? subDir,
    ConflictResolution conflictResolution = ConflictResolution.autoRename,
  }) async {
    return await _platform.saveBytes(
      fileBytes: bytes,
      fileType: fileType,
      fileName: fileName,
      subDir: subDir,
      conflictResolution: conflictResolution,
    );
  }
}
