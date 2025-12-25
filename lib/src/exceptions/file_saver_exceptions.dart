/// Base exception for all file saver errors.
///
/// Uses sealed class for exhaustive exception handling.
sealed class FileSaverException implements Exception {
  const FileSaverException(this.message, [this.code]);

  factory FileSaverException.fromObj(Object e) {
    if (e is! Exception) {
      return PlatformException('Unexpected error: $e', 'PLATFORM_ERROR');
    }

    final msg = e.toString();
    bool contains(String code) {
      return msg.contains(code);
    }

    return switch (msg) {
      _ when contains('PERMISSION_DENIED') => PermissionDeniedException(msg),
      _ when contains('FILE_EXISTS') => FileExistsException(msg),
      _ when contains('INVALID_FILE') => InvalidFileException(msg),
      _ when contains('UNSUPPORTED_FORMAT') => UnsupportedFormatException(msg),
      _ when contains('STORAGE_FULL') => const StorageFullException(),
      _ when contains('FILE_IO_ERROR') => FileIOException(msg),
      _ => PlatformException(msg, 'PLATFORM_ERROR'),
    };
  }

  factory FileSaverException.fromErrorResult(String errorCode, String message) {
    return switch (errorCode) {
      'PERMISSION_DENIED' => PermissionDeniedException(message),
      'FILE_EXISTS' => FileExistsException(message),
      'INVALID_FILE' || 'INVALID_ARGUMENT' => InvalidFileException(message),
      'UNSUPPORTED_FORMAT' => UnsupportedFormatException(message),
      'STORAGE_FULL' => const StorageFullException(),
      'FILE_IO_ERROR' => FileIOException(message),
      _ => PlatformException(message, errorCode),
    };
  }

  /// Human-readable error message.
  final String message;

  /// Machine-readable error code.
  final String? code;

  @override
  String toString() {
    final codeStr = code != null ? ' ($code)' : '';
    return 'FileSaverException: $message$codeStr';
  }
}

/// Permission was denied for storage access.
///
/// This exception is thrown when the user denies storage permission
/// or when permissions are not granted.
final class PermissionDeniedException extends FileSaverException {
  const PermissionDeniedException([String? message])
      : super(message ?? 'Storage permission denied', 'PERMISSION_DENIED');
}

/// File already exists and conflict resolution strategy is set to fail.
///
/// This exception is thrown when:
/// - A file with the same name already exists at the destination
/// - The conflict resolution strategy is [ConflictResolution.fail]
final class FileExistsException extends FileSaverException {
  const FileExistsException(this.fileName)
      : super('File already exists: $fileName', 'FILE_EXISTS');

  /// The name of the existing file.
  final String fileName;

  @override
  String toString() => 'FileExistsException: File already exists: $fileName';
}

final class InvalidFileException extends FileSaverException {
  const InvalidFileException(String reason)
      : super('Invalid file: $reason', 'INVALID_FILE');
}

/// Insufficient storage space available.
///
/// This exception is thrown when there is not enough free space
/// on the device to save the file.
final class StorageFullException extends FileSaverException {
  const StorageFullException()
      : super('Insufficient storage space', 'STORAGE_FULL');
}

/// Platform-specific error occurred.
///
/// This exception wraps platform-specific errors from iOS or Android
/// that don't fit into other exception categories.
final class PlatformException extends FileSaverException {
  const PlatformException(super.message, [super.code]);

  /// Creates a platform exception from native error details.
  factory PlatformException.fromNative({
    required String message,
    String? code,
    String? details,
  }) {
    final fullMessage = details != null ? '$message: $details' : message;
    return PlatformException(fullMessage, code);
  }
}

/// File I/O operation failed.
///
/// This exception is thrown when:
/// - Unable to write file to disk
/// - Unable to create directory
/// - File system error occurred
final class FileIOException extends FileSaverException {
  const FileIOException(String reason)
      : super('File I/O error: $reason', 'FILE_IO_ERROR');
}

/// File format is not supported on this platform or OS version.
///
/// This exception is thrown when:
/// - File format is not supported by the platform (e.g., HEIC/HEIF on some Android versions)
/// - Platform-specific codec is missing
/// - OS version doesn't support the format
final class UnsupportedFormatException extends FileSaverException {
  const UnsupportedFormatException(this.format, [String? details])
      : super(
          'File format "$format" is not supported on this platform${details != null ? ": $details" : ""}',
          'UNSUPPORTED_FORMAT',
        );

  /// The unsupported file format/extension.
  final String format;

  @override
  String toString() =>
      'UnsupportedFormatException: File format "$format" is not supported on this platform';
}
