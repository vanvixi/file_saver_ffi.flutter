import 'dart:async';
import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart';

import '../../exceptions/file_saver_exceptions.dart';
import '../../models/file_saver_sink.dart';
import 'web_utils.dart';

/// Web implementation of [FileSaverSink].
///
/// Two modes:
/// - **FSA mode**: Uses [FileSystemWritableFileStream] for zero-RAM streaming
///   (Chrome/Edge 86+). Each [add] is written directly to disk.
/// - **Buffer mode**: Accumulates all bytes in memory, triggers an anchor
///   download on [close] (Firefox / Safari / browsers without FSA).
class WebFileSaverSink implements FileSaverSink {
  /// Creates an FSA-backed sink that writes directly to [writable].
  WebFileSaverSink.fsa({
    required FileSystemWritableFileStream writable,
    required String resolvedName,
    required int? totalSize,
  }) : _writable = writable,
       _resolvedName = resolvedName,
       _totalSize = totalSize,
       _isFsa = true,
       _buffer = null,
       _mimeType = null;

  /// Creates an in-memory buffer sink that downloads on [close].
  WebFileSaverSink.buffer({
    required String resolvedName,
    required String mimeType,
    required int? totalSize,
  }) : _writable = null,
       _resolvedName = resolvedName,
       _totalSize = totalSize,
       _isFsa = false,
       _buffer = [],
       _mimeType = mimeType;

  final FileSystemWritableFileStream? _writable;
  final List<int>? _buffer;
  final String _resolvedName;
  final String? _mimeType;
  final int? _totalSize;
  final bool _isFsa;

  int _bytesWritten = 0;
  bool _isClosed = false;
  bool _isAddingStream = false;

  // FSA writes are chained on this future to ensure ordering.
  Future<void> _writeQueue = Future.value();

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
    if (_isFsa) {
      // Chain writes sequentially via promise queue.
      final bytes = Uint8List.fromList(data);
      _writeQueue = _writeQueue
          .then((_) async {
            await _writable!
                .write(bytes.toJS as FileSystemWriteChunkType)
                .toDart;
            _trackBytes(bytes.length);
          })
          .catchError((Object e, StackTrace st) {
            if (!_doneCompleter.isCompleted)
              _doneCompleter.completeError(e, st);
          });
    } else {
      _buffer!.addAll(data);
      _trackBytes(data.length);
    }
  }

  void _trackBytes(int count) {
    _bytesWritten += count;
    _bytesController.add(_bytesWritten);
    if (_totalSize != null && _totalSize > 0) {
      _progressController.add(_bytesWritten / _totalSize);
    }
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
        add(chunk);
      }
      // Wait for any queued FSA writes to finish.
      if (_isFsa) await _writeQueue;
    } finally {
      _isAddingStream = false;
    }
  }

  @override
  Future<void> flush() async {
    // FSA has no explicit flush — drain the write queue.
    if (_isFsa) await _writeQueue;
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

  Future<void> _doClose() async {
    try {
      if (_isFsa) {
        await _writeQueue;
        await _writable!.close().toDart;
        _complete(Uri(scheme: 'web-directory', path: _resolvedName));
      } else {
        final bytes = Uint8List.fromList(_buffer!);
        WebUtils.triggerBytesDownload(bytes, _resolvedName, _mimeType!);
        _complete(Uri(scheme: 'browser-download', path: _resolvedName));
      }
    } catch (e, st) {
      _progressController.close().ignore();
      _bytesController.close().ignore();
      if (!_resultCompleter.isCompleted) _resultCompleter.completeError(e, st);
      if (!_doneCompleter.isCompleted) _doneCompleter.completeError(e, st);
    }
  }

  void _complete(Uri uri) {
    _progressController.close().ignore();
    _bytesController.close().ignore();
    if (!_resultCompleter.isCompleted) _resultCompleter.complete(uri);
    if (!_doneCompleter.isCompleted) _doneCompleter.complete(uri);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // cancel
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Future<void> cancel() async {
    if (_isClosed) return;
    _isClosed = true;
    if (_isFsa) {
      try {
        await _writable!.abort().toDart;
      } catch (_) {}
    } else {
      _buffer!.clear();
    }
    _progressController.close().ignore();
    _bytesController.close().ignore();
    const error = CancelledException();
    if (!_resultCompleter.isCompleted) {
      _resultCompleter.completeError(error);
      _resultCompleter.future.ignore();
    }
    if (!_doneCompleter.isCompleted) _doneCompleter.complete();
  }
}
