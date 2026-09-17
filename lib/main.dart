import 'package:flutter/material.dart';
import 'ui/screens/login_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AsuApp());
}

class AsuApp extends StatelessWidget {
  const AsuApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'asu_app',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.light,
        primarySwatch: Colors.blue,
        scaffoldBackgroundColor: Colors.grey.shade100,
        cardColor: Colors.white,
        useMaterial3: true,
      ),
      darkTheme: ThemeData(
        brightness: Brightness.dark,
        primarySwatch: Colors.blue,
        scaffoldBackgroundColor: const Color(0xFF121212),
        cardColor: const Color(0xFF1E1E1E),
        useMaterial3: true,
      ),
      themeMode: ThemeMode.system,
      home: const LoginScreen(),
    );
  }
}