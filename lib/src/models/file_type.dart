sealed class FileType {
  const FileType({required this.ext, required this.mimeType});

  final String ext;
  final String mimeType;
}

enum ImageType implements FileType {
  png('png', 'image/png'),
  jpg('jpg', 'image/jpeg'),
  jpeg('jpeg', 'image/jpeg'),
  gif('gif', 'image/gif'),
  webp('webp', 'image/webp'),
  bmp('bmp', 'image/bmp'),
  heic('heic', 'image/heic'),
  heif('heif', 'image/heif'),
  tiff('tiff', 'image/tiff'),
  tif('tif', 'image/tiff'),
  ico('ico', 'image/x-icon'),
  dng('dng', 'image/x-adobe-dng');

  const ImageType(this.ext, this.mimeType);

  @override
  final String ext;

  @override
  final String mimeType;
}

enum VideoType implements FileType {
  mp4('mp4', 'video/mp4'),
  threeGp('3gp', 'video/3gpp'),
  webm('webm', 'video/webm'),
  m4v('m4v', 'video/x-m4v'),
  mkv('mkv', 'video/x-matroska'),
  mov('mov', 'video/quicktime'),
  avi('avi', 'video/x-msvideo'),
  flv('flv', 'video/x-flv'),
  wmv('wmv', 'video/x-ms-wmv'),
  hevc('hevc', 'video/hevc'),
  vp9('vp9', 'video/x-vnd.on2.vp9'),
  av1('av1', 'video/av01');

  const VideoType(this.ext, this.mimeType);

  @override
  final String ext;

  @override
  final String mimeType;
}

enum AudioType implements FileType {
  mp3('mp3', 'audio/mpeg'),
  aac('aac', 'audio/aac'),
  wav('wav', 'audio/wav'),
  amr('amr', 'audio/amr'),
  threeGp('3gp', 'audio/3gpp'),
  m4a('m4a', 'audio/mp4'),
  ogg('ogg', 'audio/ogg'),
  flac('flac', 'audio/flac'),
  opus('opus', 'audio/opus'),
  aiff('aiff', 'audio/aiff'),
  caf('caf', 'audio/x-caf');

  const AudioType(this.ext, this.mimeType);

  @override
  final String ext;

  @override
  final String mimeType;
}

class CustomFileType implements FileType {
  const CustomFileType({required this.ext, required this.mimeType});

  @override
  final String ext;

  @override
  final String mimeType;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CustomFileType &&
          runtimeType == other.runtimeType &&
          ext == other.ext &&
          mimeType == other.mimeType;

  @override
  int get hashCode => ext.hashCode ^ mimeType.hashCode;

  @override
  String toString() => 'CustomFileType(ext: $ext, mimeType: $mimeType)';
}
