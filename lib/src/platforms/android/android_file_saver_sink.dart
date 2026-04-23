import 'dart:async';

import 'package:jni/jni.dart';

import '../../exceptions/file_saver_exceptions.dart';
import '../../models/file_saver_sink.dart';
import 'bindings.g.dart' as bindings;

/// Android implementation of [FileSaverSink].
///
/// Wraps a JNI write session in `FileSaver.kt` with progress/bytesWritten
/// tracking. Each JNI call uses a one-shot [bindings.ProgressCallback] that is
/// released via [Future.microtask] after the first event fires.
class AndroidFileSaverSink implements FileSaverSink {
  AndroidFileSaverSink({
    required bindings.FileSaver fileSaver,
    required int sessionId,
    int? totalSize,
  }) : _fileSaver = fileSaver,
       _sessionId = sessionId,
       _totalSize = totalSize;

  final bindings.FileSaver _fileSaver;
  final int _sessionId;
  final int? _totalSize;

  int _lastBytesWritten = 0;
  bool _isClosed = false;
  bool _isAddingStream = false;

  final _progressController = StreamController<double>.broadcast();
  final _bytesController = StreamController<int>.broadcast();
  final _resultCompleter = Completer<Uri>();
  final _doneCompleter = Completer<dynamic>();

  // ─────────────────────────────────────────────────────────────────────────
  // FileSaverSink interface
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Stream<double> get progress => _progressController.stream;

  @override
  Stream<int> get bytesWritten => _bytesController.stream;

  @override
  Future<Uri> get result => _resultCompleter.future;

  @override
  Future get done => _doneCompleter.future;

  // ─────────────────────────────────────────────────────────────────────────
  // StreamSink<List<int>>
  // ─────────────────────────────────────────────────────────────────────────

  @override
  void add(List<int> data) {
    if (_isClosed) throw StateError('Cannot add to a closed FileSaverSink');
    if (_isAddingStream) {
      throw StateError('Cannot add while addStream is active');
    }
    _dispatchChunk(data);
  }

  @override
  void addError(Object error, [StackTrace? stackTrace]) {
    if (!_doneCompleter.isCompleted) {
      _doneCompleter.completeError(error, stackTrace);
    }
  }

  @override
  Future addStream(Stream<List<int>> stream) async {
    if (_isAddingStream) throw StateError('Already adding a stream');
    _isAddingStream = true;
    try {
      await for (final chunk in stream) {
        if (_isClosed) break;
        await _writeChunkAwaited(chunk);
      }
    } finally {
      _isAddingStream = false;
    }
  }

  @override
  Future<void> flush() {
    final completer = Completer<void>();
    bindings.ProgressCallback? callback;
    callback = bindings.ProgressCallback.implement(
      bindings.$ProgressCallback(
        onEvent: (eventType, progress, jStr1, jStr2) {
          switch (eventType) {
            case 1:
              if (!completer.isCompleted) completer.complete();
            case 2:
              final code =
                  jStr1?.toDartString(releaseOriginal: true) ?? 'UNKNOWN';
              final msg = jStr2?.toDartString(releaseOriginal: true) ?? '';
              if (!completer.isCompleted) {
                completer.completeError(
                  FileSaverException.fromErrorResult(code, msg),
                );
              }
            default:
              jStr1?.release();
              jStr2?.release();
          }
          Future.microtask(() => callback?.release());
        },
        onEvent$async: true,
      ),
    );
    _fileSaver.flushSession(_sessionId, callback);
    return completer.future;
  }

  @override
  Future<void> close() async {
    if (_isClosed) return;
    _isClosed = true;

    while (_isAddingStream) {
      await Future.microtask(() {});
    }

    await _doClose();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // cancel
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<void> cancel() async {
    if (_isClosed) return;
    _isClosed = true;
    _fileSaver.cancelSession(_sessionId);
    _progressController.close().ignore();
    _bytesController.close().ignore();
    const error = CancelledException();
    if (!_resultCompleter.isCompleted) {
      _resultCompleter.completeError(error);
      _resultCompleter.future.ignore();
    }
    if (!_doneCompleter.isCompleted) _doneCompleter.complete();
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Private helpers
  // ─────────────────────────────────────────────────────────────────────────

  /// Fire-and-forget chunk write (used by [add]).
  void _dispatchChunk(List<int> data) {
    final jData = JByteArray.of(data);
    bindings.ProgressCallback? callback;
    callback = bindings.ProgressCallback.implement(
      bindings.$ProgressCallback(
        onEvent: (eventType, progress, jStr1, jStr2) {
          jStr1?.release();
          jStr2?.release();
          switch (eventType) {
            case 1:
              if (!_isClosed) {
                _lastBytesWritten = progress.toInt();
                _bytesController.add(_lastBytesWritten);
                final total = _totalSize;
                if (total != null && total > 0) {
                  _progressController.add(_lastBytesWritten / total);
                }
              }
            case 2:
              if (!_doneCompleter.isCompleted) {
                _doneCompleter.completeError(
                  const WriteSessionException('Write chunk failed'),
                );
              }
          }
          Future.microtask(() => callback?.release());
        },
        onEvent$async: true,
      ),
    );
    _fileSaver.writeChunk(_sessionId, jData, callback);
    jData.release();
  }

  /// Sequential chunk write with back-pressure (used by [addStream]).
  ///
  /// Awaits the JNI ACK (type=1) before returning, ensuring the next chunk
  /// is not sent until the previous one has been written to the stream.
  Future<void> _writeChunkAwaited(List<int> data) {
    final completer = Completer<void>();
    final jData = JByteArray.of(data);
    bindings.ProgressCallback? callback;
    callback = bindings.ProgressCallback.implement(
      bindings.$ProgressCallback(
        onEvent: (eventType, progress, jStr1, jStr2) {
          jStr1?.release();
          jStr2?.release();
          switch (eventType) {
            case 1:
              if (!_isClosed) {
                _lastBytesWritten = progress.toInt();
                _bytesController.add(_lastBytesWritten);
                final total = _totalSize;
                if (total != null && total > 0) {
                  _progressController.add(_lastBytesWritten / total);
                }
              }
              if (!completer.isCompleted) completer.complete();
            case 2:
              if (!completer.isCompleted) {
                completer.completeError(
                  const WriteSessionException('Write chunk failed'),
                );
              }
          }
          Future.microtask(() => callback?.release());
        },
        onEvent$async: true,
      ),
    );
    _fileSaver.writeChunk(_sessionId, jData, callback);
    jData.release();
    return completer.future;
  }

  Future<void> _doClose() async {
    bindings.ProgressCallback? callback;
    final closeCompleter = Completer<Uri>();
    callback = bindings.ProgressCallback.implement(
      bindings.$ProgressCallback(
        onEvent: (eventType, progress, jStr1, jStr2) {
          switch (eventType) {
            case 3:
              final uriStr = jStr1?.toDartString(releaseOriginal: true) ?? '';
              jStr2?.release();
              if (!closeCompleter.isCompleted) {
                closeCompleter.complete(Uri.parse(uriStr));
              }
            case 2:
              final code =
                  jStr1?.toDartString(releaseOriginal: true) ?? 'UNKNOWN';
              final msg = jStr2?.toDartString(releaseOriginal: true) ?? '';
              if (!closeCompleter.isCompleted) {
                closeCompleter.completeError(
                  FileSaverException.fromErrorResult(code, msg),
                );
              }
            default:
              jStr1?.release();
              jStr2?.release();
          }
          Future.microtask(() => callback?.release());
        },
        onEvent$async: true,
      ),
    );
    _fileSaver.closeSession(_sessionId, callback);
    try {
      final uri = await closeCompleter.future;
      _progressController.close().ignore();
      _bytesController.close().ignore();
      if (!_resultCompleter.isCompleted) _resultCompleter.complete(uri);
      if (!_doneCompleter.isCompleted) _doneCompleter.complete(uri);
    } catch (e, st) {
      _progressController.close().ignore();
      _bytesController.close().ignore();
      if (!_resultCompleter.isCompleted) _resultCompleter.completeError(e, st);
      if (!_doneCompleter.isCompleted) _doneCompleter.completeError(e, st);
    }
  }
}
