import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'sis_api_service.dart';
import 'login_screen.dart';
import 'main_screen.dart';
import '../debug/debug_overlay.dart';

class AppRoot extends StatefulWidget {
  const AppRoot({super.key});

  @override
  State<AppRoot> createState() => _AppRootState();
}

class _AppRootState extends State<AppRoot> {
  late final WebViewController _webViewController;
  bool _isLoggedIn = false;

  @override
  void initState() {
    super.initState();
    _webViewController = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted);
    SisApiService.attachController(_webViewController);
  }

  void _onLoginSuccess() {
    setState(() => _isLoggedIn = true);
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        _isLoggedIn ? const MainScreen() : LoginScreen(onLoginSuccess: _onLoginSuccess),
        Positioned(
          left: -1000,
          top: -1000,
          width: 1,
          height: 1,
          child: WebViewWidget(controller: _webViewController),
        ),
        const DebugOverlay(),
      ],
    );
  }
}