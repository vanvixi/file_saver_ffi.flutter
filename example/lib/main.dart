import 'package:file_saver_ffi/file_saver_ffi.dart';
import 'package:flutter/material.dart';

import 'tabs/tabs.dart';

class AppLifecycleStateObserver extends WidgetsBindingObserver {
  final void Function()? onDetached;

  AppLifecycleStateObserver({this.onDetached});

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.detached:
        onDetached?.call();
      default:
    }
  }
}

void main() {
  final binding = WidgetsFlutterBinding.ensureInitialized();
  binding.addObserver(
    AppLifecycleStateObserver(onDetached: FileSaver.instance.dispose),
  );
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'File Saver FFI Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const FileSaverDemoPage(),
    );
  }
}

class FileSaverDemoPage extends StatelessWidget {
  const FileSaverDemoPage({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('File Saver FFI Demo'),
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
          bottom: TabBar(
            tabs: const [
              Tab(icon: Icon(Icons.image), text: 'Image'),
              Tab(icon: Icon(Icons.video_library), text: 'Video'),
              Tab(icon: Icon(Icons.insert_drive_file), text: 'File'),
            ],
          ),
        ),
        body: TabBarView(
          children: const [ImageTabPage(), VideoTabPage(), FileTabPage()],
        ),
      ),
    );
  }
}
