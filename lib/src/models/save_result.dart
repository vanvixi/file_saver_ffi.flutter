sealed class SaveResult {
  const SaveResult();
}

/// Successful file save operation.
///
/// Contains the file path and URI of the saved file.
final class SaveSuccess extends SaveResult {
  /// Creates a successful save result.
  const SaveSuccess({
    required this.filePath,
    required this.uri,
  });

  /// The absolute file path where the file was saved.
  ///
  /// Example: `/storage/emulated/0/Download/photo.jpg` (Android)
  /// or `/var/mobile/Containers/Data/Application/.../Documents/photo.jpg` (iOS)
  final String filePath;

  /// The URI that can be used to access the file.
  ///
  /// On Android, this may be a content:// URI for MediaStore files.
  /// On iOS, this is typically a file:// URI.
  final String uri;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SaveSuccess &&
          runtimeType == other.runtimeType &&
          filePath == other.filePath &&
          uri == other.uri;

  @override
  int get hashCode => filePath.hashCode ^ uri.hashCode;

  @override
  String toString() => 'SaveSuccess(filePath: $filePath, uri: $uri)';
}

/// Failed file save operation.
///
/// Contains error information including error message and error code.
final class SaveFailure extends SaveResult {
  const SaveFailure({
    required this.error,
    required this.errorCode,
    this.stackTrace,
  });


  final String error;

  /// Example: "PERMISSION_DENIED", "STORAGE_FULL", "FILE_EXISTS"
  final String errorCode;

  /// Optional stack trace for debugging.
  final String? stackTrace;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SaveFailure &&
          runtimeType == other.runtimeType &&
          error == other.error &&
          errorCode == other.errorCode;

  @override
  int get hashCode => error.hashCode ^ errorCode.hashCode;

  @override
  String toString() =>
      'SaveFailure(error: $error, errorCode: $errorCode${stackTrace != null ? ', stackTrace: $stackTrace' : ''})';
}
