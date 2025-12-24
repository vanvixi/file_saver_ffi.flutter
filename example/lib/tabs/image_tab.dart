import 'dart:typed_data';

import 'package:file_saver_ffi/file_saver_ffi.dart';
import 'package:flutter/material.dart';

import '../core/core.dart';

class ImageTabPage extends StatefulWidget {
  const ImageTabPage({super.key});

  @override
  State<ImageTabPage> createState() => _ImageTabPageState();
}

class _ImageTabPageState extends State<ImageTabPage>
    with AutomaticKeepAliveClientMixin, MediaSaverStateMixin {
  Uint8List? _downloadedImageBytes;
  static const String _imageUrl = 'https://picsum.photos/800/1200';

  @override
  bool get wantKeepAlive => true;

  Future<void> _downloadAndSaveImage() async {
    resetState();
    _downloadedImageBytes = null;

    try {
      if (await isGrantedPermissionWritePhotos() == false) {
        showError('Photos permission denied');
        return;
      }

      final imageBytes = await downloadFromUrl(_imageUrl);

      setState(() {
        _downloadedImageBytes = imageBytes;
        mediaSize = imageBytes.length;
      });

      final fileName = 'image_${DateTime.now().millisecondsSinceEpoch}';

      await runSaveCatching(
        () => FileSaver.instance.saveBytes(
          bytes: imageBytes,
          fileName: fileName,
          fileType: ImageType.jpg,
          subDir: 'FileSaverFFI Demo',
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
      title: 'Image Download Demo',
      description:
          'Downloads a random image and saves it to Photos library with album support.',
      url: _imageUrl,
      buttonLabel: 'Download & Save Image',
      onDownload: _downloadAndSaveImage,
      previewWidget: _downloadedImageBytes != null
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Preview:',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.memory(
                    _downloadedImageBytes!,
                    fit: BoxFit.cover,
                    height: 300,
                    width: double.infinity,
                  ),
                ),
              ],
            )
          : null,
    );
  }
}
