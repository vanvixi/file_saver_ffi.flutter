/// Strategy for handling file name conflicts when saving files.
///
/// Defines how to handle the case when a file with the same name
/// already exists at the destination.
enum ConflictResolution {
  /// Automatically rename the file by appending (1), (2), etc.
  ///
  /// Example:
  /// - photo.jpg exists
  /// - Saves as photo (1).jpg
  /// - If photo (1).jpg exists, saves as photo (2).jpg
  ///
  /// This is the default and recommended strategy for most use cases.
  autoRename,

  /// Overwrite the existing file with the new file.
  ///
  /// **Warning**: The existing file will be permanently deleted.
  /// Use with caution.
  overwrite,

  /// Fail the save operation if a file with the same name exists.
  ///
  /// Returns [SaveFailure] with error code "FILE_EXISTS".
  fail,

  /// Skip the save operation if a file with the same name exists.
  ///
  /// Returns [SaveSuccess] with the path of the existing file.
  /// No actual file writing occurs.
  skip,
}
