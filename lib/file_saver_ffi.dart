library;

import 'dart:typed_data';

// Public API - FileSaver class
import 'src/models/conflict_resolution.dart';
import 'src/models/file_type.dart';
import 'src/models/save_result.dart';
import 'src/platform_interface/file_saver_platform.dart';

// Exceptions
export 'src/exceptions/file_saver_exceptions.dart';
// Models
export 'src/models/conflict_resolution.dart';
export 'src/models/file_type.dart';
export 'src/models/save_result.dart';

class FileSaver {
  FileSaver._();

  static final FileSaver instance = FileSaver._();

  FileSaverPlatform get _platform => FileSaverPlatform.instance;

  Future<SaveResult> saveBytes({
    required Uint8List bytes,
    required String fileName,
    required FileType fileType,
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
