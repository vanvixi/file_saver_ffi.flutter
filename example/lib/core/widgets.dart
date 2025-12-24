import 'package:flutter/material.dart';

import 'utils.dart';

/// Info card displaying title, description and URL
class InfoCard extends StatelessWidget {
  const InfoCard({
    super.key,
    required this.title,
    required this.description,
    required this.url,
  });

  final String title;
  final String description;
  final String url;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 8),
            Text(description),
            const SizedBox(height: 8),
            Text(
              'URL: $url',
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: Colors.grey),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}

/// Download button with loading state
class DownloadButton extends StatelessWidget {
  const DownloadButton({
    super.key,
    required this.isLoading,
    required this.onPressed,
    required this.label,
    this.loadingLabel = 'Downloading...',
  });

  final bool isLoading;
  final VoidCallback? onPressed;
  final String label;
  final String loadingLabel;

  @override
  Widget build(BuildContext context) {
    return ElevatedButton.icon(
      onPressed: isLoading ? null : onPressed,
      icon: isLoading
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.download),
      label: Text(isLoading ? loadingLabel : label),
      style: ElevatedButton.styleFrom(
        padding: const EdgeInsets.symmetric(vertical: 16),
      ),
    );
  }
}

/// Progress indicator section
class ProgressSection extends StatelessWidget {
  const ProgressSection({
    super.key,
    required this.progress,
    this.label = 'Saving',
  });

  final double progress;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const SizedBox(height: 16),
        LinearProgressIndicator(value: progress / 100),
        const SizedBox(height: 8),
        Text(
          '$label: ${progress.toStringAsFixed(1)}%',
          textAlign: TextAlign.center,
        ),
      ],
    );
  }
}

/// Card showing file/media size info
class FileSizeCard extends StatelessWidget {
  const FileSizeCard({super.key, required this.sizeInBytes, this.label});

  final int sizeInBytes;
  final String? label;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.blue.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(Icons.info, color: Colors.blue.shade700),
            const SizedBox(width: 8),
            Text(
              '${label ?? 'Size'}: ${formatBytes(sizeInBytes)}',
              style: TextStyle(color: Colors.blue.shade700),
            ),
          ],
        ),
      ),
    );
  }
}

/// Card showing successful save path
class SuccessCard extends StatelessWidget {
  const SuccessCard({super.key, required this.savedPath});

  final String savedPath;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.green.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(Icons.check_circle, color: Colors.green.shade700),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                'Saved: $savedPath',
                style: TextStyle(color: Colors.green.shade700),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
