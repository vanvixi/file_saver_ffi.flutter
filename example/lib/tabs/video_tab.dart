import 'package:file_saver_ffi/file_saver_ffi.dart';
import 'package:flutter/material.dart';

import '../core/core.dart';

class VideoTabPage extends StatefulWidget {
  const VideoTabPage({super.key});

  @override
  State<VideoTabPage> createState() => _VideoTabPageState();
}

class _VideoTabPageState extends State<VideoTabPage>
    with AutomaticKeepAliveClientMixin, MediaSaverStateMixin {
  static const String _videoUrl =
      'https://download.samplelib.com/mp4/sample-5s.mp4';

  @override
  bool get wantKeepAlive => true;

  Future<void> _downloadAndSaveVideo() async {
    resetState();

    try {
      if (await isGrantedPermissionWritePhotos() == false) {
        showError('Photos permission denied');
        return;
      }

      if (!mounted) return;

      showAppSnackBar(
        context,
        'Downloading video... This may take a while',
        isSuccess: true,
      );

      final videoBytes = await downloadFromUrl(_videoUrl);

      setState(() {
        mediaSize = videoBytes.length;
      });

      final fileName = 'video_${DateTime.now().millisecondsSinceEpoch}';

      await runSaveCatching(
        () => FileSaver.instance.saveBytes(
          bytes: videoBytes,
          fileName: fileName,
          fileType: VideoType.mp4,
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
      title: 'Video Download Demo',
      description: 'Downloads a sample video and saves it to Photos library.',
      url: _videoUrl,
      buttonLabel: 'Download & Save Video',
      onDownload: _downloadAndSaveVideo,
    );
  }
}
