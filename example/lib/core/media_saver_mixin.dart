import 'dart:io';
import 'dart:typed_data';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:file_saver_ffi/file_saver_ffi.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:permission_handler/permission_handler.dart';

import 'utils.dart';
import 'widgets.dart';

/// Mixin providing common state and methods for media saving tabs
mixin MediaSaverStateMixin<T extends StatefulWidget> on State<T> {
  bool isLoading = false;
  String? savedFilePath;
  double progress = 0.0;
  int mediaSize = 0;

  /// Downloads file from URL
  Future<Uint8List> downloadFromUrl(String url) async {
    final response = await http.get(Uri.parse(url));
    if (response.statusCode == 200) {
      return response.bodyBytes;
    } else {
      throw Exception('Failed to download: ${response.statusCode}');
    }
  }

  Future<bool> isGrantedPermissionWritePhotos() async {
    if (Platform.isIOS) {
      // final status = await Permission.photosAddOnly.status;
      // if (!status.isGranted) {
      //   final status = await Permission.photosAddOnly.request();
      //   return status.isGranted;
      // }
      return true;
    }

    if (Platform.isAndroid) {
      return isGrantedPermissionAndroidWriteExternalStorage();
    }

    return false;
  }

  Future<bool> isGrantedPermissionWriteExternalStorage() async {
    if (Platform.isIOS) {
      return true;
    }

    if (Platform.isAndroid) {
      return isGrantedPermissionAndroidWriteExternalStorage();
    }

    return false;
  }

  Future<bool> isGrantedPermissionAndroidWriteExternalStorage() async {
    final androidInfo = await DeviceInfoPlugin().androidInfo;
    if (androidInfo.version.sdkInt > 28) {
      return true;
    }

    final status = await Permission.storage.status;
    if (status.isGranted) {
      return true;
    }

    return await Permission.storage.request().isGranted;
  }

  /// Resets state before starting download
  void resetState() {
    setState(() {
      isLoading = true;
      savedFilePath = null;
      progress = 0.0;
      mediaSize = 0;
    });
  }

  /// Updates progress
  void updateProgress(int current, int total, double percentage) {
    setState(() {
      progress = percentage;
    });
    debugPrint('Save progress: $percentage%');
  }

  Future<void> runSaveCatching(Future<Uri> Function() saveFn) async {
    if (!mounted) return;

    try {
      final uri = await saveFn();

      if (!mounted) return;

      setState(() {
        savedFilePath = uri.toString();
      });

      showAppSnackBar(
        context,
        'Saved successfully!\nURI: $uri',
        isSuccess: true,
      );
    } on PermissionDeniedException catch (e) {
      if (!mounted) return;
      showAppSnackBar(
        context,
        'Permission denied: ${e.message}',
        isSuccess: false,
      );
    } on FileExistsException catch (e) {
      if (!mounted) return;
      showAppSnackBar(
        context,
        'File already exists: ${e.fileName}',
        isSuccess: false,
      );
    } on StorageFullException catch (e) {
      if (!mounted) return;
      showAppSnackBar(context, 'Storage full: ${e.message}', isSuccess: false);
    } on FileSaverException catch (e) {
      if (!mounted) return;
      showAppSnackBar(
        context,
        'Save failed: ${e.message} (${e.code})',
        isSuccess: false,
      );
    }
  }

  /// Shows error message
  void showError(dynamic error) {
    if (mounted) {
      showAppSnackBar(context, 'Error: ${error.toString()}', isSuccess: false);
    }
  }

  /// Finishes loading state
  void finishLoading() {
    setState(() {
      isLoading = false;
    });
  }

  /// Builds the common page layout
  Widget buildPageLayout({
    required String title,
    required String description,
    required String url,
    required String buttonLabel,
    required VoidCallback onDownload,
    Widget? previewWidget,
  }) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InfoCard(title: title, description: description, url: url),
          const SizedBox(height: 16),
          DownloadButton(
            isLoading: isLoading,
            onPressed: onDownload,
            label: buttonLabel,
          ),
          if (isLoading && progress > 0) ProgressSection(progress: progress),
          if (previewWidget != null) ...[
            const SizedBox(height: 24),
            previewWidget,
          ],
          if (mediaSize > 0) ...[
            const SizedBox(height: 16),
            FileSizeCard(sizeInBytes: mediaSize),
          ],
          if (savedFilePath != null) ...[
            const SizedBox(height: 16),
            SuccessCard(savedPath: savedFilePath!),
          ],
        ],
      ),
    );
  }
}
