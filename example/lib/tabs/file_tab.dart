import 'dart:io';

import 'package:file_saver_ffi/file_saver_ffi.dart';
import 'package:flutter/material.dart';

import '../core/core.dart';

class FileTabPage extends StatefulWidget {
  const FileTabPage({super.key});

  @override
  State<FileTabPage> createState() => _FileTabPageState();
}

class _FileTabPageState extends State<FileTabPage>
    with AutomaticKeepAliveClientMixin, MediaSaverStateMixin {
  static const String _pdfUrl =
      'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf';

  @override
  bool get wantKeepAlive => true;

  Future<void> _downloadAndSaveFile() async {
    resetState();

    try {
      if (await isGrantedPermissionWriteExternalStorage() == false) {
        showError('Storage permission denied');
        return;
      }

      final fileBytes = await downloadFromUrl(_pdfUrl);

      setState(() {
        mediaSize = fileBytes.length;
      });

      final fileName = 'document_${DateTime.now().millisecondsSinceEpoch}';

      await runSaveCatching(
        () => FileSaver.instance.saveBytes(
          bytes: fileBytes,
          fileName: fileName,
          fileType: CustomFileType(ext: 'pdf', mimeType: 'application/pdf'),
          subDir: Platform.isIOS ? 'PDF' : 'FileSaverFFI Demo',
          conflictResolution: ConflictResolution.autoRename,
        ),
      );
    } catch (e) {
      showError(e);
    } finally {
      finishLoading();
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    return buildPageLayout(
      title: 'File Download Demo',
      description:
          'Downloads a sample PDF file and saves it to Downloads directory.',
      url: _pdfUrl,
      buttonLabel: 'Download & Save PDF',
      onDownload: _downloadAndSaveFile,
    );
  }
}
