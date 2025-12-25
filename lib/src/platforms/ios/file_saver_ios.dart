import 'dart:async';
import 'dart:ffi' as ffi;
import 'dart:typed_data';

import 'package:ffi/ffi.dart';

import '../../exceptions/file_saver_exceptions.dart';
import '../../models/conflict_resolution.dart';
import '../../models/file_type.dart';
import '../../platform_interface/file_saver_platform.dart';
import 'bindings.g.dart';

class FileSaverIos extends FileSaverPlatform {
  FileSaverIos() {
    final dylib = ffi.DynamicLibrary.process();
    _bindings = FileSaverFfiBindings(dylib);
    _saverInstance = _bindings.file_saver_init();
  }

  late final FileSaverFfiBindings _bindings;
  late final ffi.Pointer<ffi.Void> _saverInstance;

  @override
  void dispose() {
    _bindings.file_saver_dispose(_saverInstance);
  }

  @override
  Future<Uri> saveBytes({
    required Uint8List fileBytes,
    required String fileName,
    required FileType fileType,
    String? subDir,
    ConflictResolution conflictResolution = ConflictResolution.autoRename,
  }) async {
    _validateInput(fileBytes, fileName);

    final completer = Completer<Uri>();

    void onResult(ffi.Pointer<FSaveResult> resultPtr) {
      try {
        final result = _convertToUriOrThrow(resultPtr.ref);
        _bindings.file_saver_free_result(resultPtr);
        completer.complete(result);
      } catch (e) {
        _bindings.file_saver_free_result(resultPtr);
        completer.completeError(e);
      }
    }

    final callback = ffi.NativeCallable<
      ffi.Void Function(ffi.Pointer<FSaveResult>)
    >.listener(onResult);

    final dataPointer = malloc.allocate<ffi.Uint8>(fileBytes.length);
    final dataList = dataPointer.asTypedList(fileBytes.length);
    dataList.setAll(0, fileBytes);

    final fileNameCStr = fileName.toNativeUtf8();
    final extCStr = fileType.ext.toNativeUtf8();
    final mimeCStr = fileType.mimeType.toNativeUtf8();
    final subDirCStr = subDir?.toNativeUtf8();

    try {
      _bindings.file_saver_save_bytes_async(
        _saverInstance,
        dataPointer,
        fileBytes.length,
        fileNameCStr.cast(),
        extCStr.cast(),
        mimeCStr.cast(),
        subDirCStr?.cast() ?? ffi.nullptr,
        conflictResolution.index,
        callback.nativeFunction,
      );

      return await completer.future;
    } finally {
      malloc.free(dataPointer);
      malloc.free(fileNameCStr);
      malloc.free(extCStr);
      malloc.free(mimeCStr);
      if (subDirCStr != null) malloc.free(subDirCStr);
      callback.close();
    }
  }

  Uri _convertToUriOrThrow(FSaveResult cResult) {
    if (!cResult.success) {
      final errorCode = cResult.errorCode.cast<Utf8>().toDartString();
      final errorMsg = cResult.errorMessage.cast<Utf8>().toDartString();

      throw FileSaverException.fromErrorResult(errorCode, errorMsg);
    }

    final uriString = cResult.fileUri.cast<Utf8>().toDartString();
    return Uri.parse(uriString);
  }

  void _validateInput(Uint8List bytes, String fileName) {
    if (bytes.isEmpty) {
      throw const InvalidFileException('File bytes cannot be empty');
    }
    if (fileName.isEmpty) {
      throw const InvalidFileException('File name cannot be empty');
    }
  }
}
