import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum AppThemeChoice { auto, light, dark, pink }

class ThemeService {
  static const _keyChoice = 'theme_choice';
  static const _keyManual = 'theme_manual';
  static const _keyFieldOpacity = 'field_opacity';
  static const _keyAutoRefresh = 'auto_refresh_enabled';

  static final ValueNotifier<AppThemeChoice> choice = ValueNotifier(AppThemeChoice.auto);
  static final ValueNotifier<double> fieldOpacity = ValueNotifier(1.0);

  static Future<void> init() async {
    final prefs = await SharedPreferences.getInstance();
    final manual = prefs.getBool(_keyManual) ?? false;
    if (manual) {
      final saved = prefs.getString(_keyChoice);
      if (saved != null) {
        choice.value = AppThemeChoice.values.firstWhere(
          (c) => c.name == saved,
          orElse: () => AppThemeChoice.auto,
        );
      }
    }
    fieldOpacity.value = (prefs.getDouble(_keyFieldOpacity) ?? 1.0).clamp(0.3, 1.0);
  }

  static Future<void> setFieldOpacity(double value) async {
    value = value.clamp(0.3, 1.0);
    fieldOpacity.value = value;                                                               
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keyFieldOpacity, value);
  }

  static Future<bool> autoRefreshEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyAutoRefresh) ?? true;
  }

  static Future<void> setAutoRefreshEnabled(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyAutoRefresh, value);
  }

  static Future<void> setChoice(AppThemeChoice value) async {
    choice.value = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyChoice, value.name);
    await prefs.setBool(_keyManual, true);
  }

  static Future<void> resetToAuto() async {
    choice.value = AppThemeChoice.auto;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyManual, false);
    await prefs.remove(_keyChoice);
  }

  static Future<void> applyGenderDefault(String gender) async {
    final prefs = await SharedPreferences.getInstance();
    final manual = prefs.getBool(_keyManual) ?? false;
    if (manual) return;

    final g = gender.trim();
    final isFemale = g.contains('أنثى') || g.contains('انثى') || g.toUpperCase().contains('FEMALE') || g.toUpperCase() == 'F';
    choice.value = isFemale ? AppThemeChoice.pink : AppThemeChoice.auto;
  }

  static ThemeData lightTheme = ThemeData(
    brightness: Brightness.light,
    useMaterial3: true,
    scaffoldBackgroundColor: Colors.grey.shade100,
    cardColor: Colors.white,
    primaryColor: const Color(0xFF337AC0),
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xFF337AC0),
      brightness: Brightness.light,
      secondary: const Color(0xFF313B44),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: Color(0xFF337AC0),
      foregroundColor: Colors.white,
    ),
  );

  static ThemeData darkTheme = ThemeData(
    brightness: Brightness.dark,
    useMaterial3: true,
    scaffoldBackgroundColor: const Color(0xFF121212),
    cardColor: const Color(0xFF1E1E1E),
    primaryColor: const Color(0xFF337AC0),
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xFF337AC0),
      brightness: Brightness.dark,
    ),
  );

  static ThemeData pinkTheme = ThemeData(
    brightness: Brightness.light,
    useMaterial3: true,
    scaffoldBackgroundColor: const Color(0xFFFFF0F5),
    cardColor: const Color(0xFFFFE0EC),
    primaryColor: const Color(0xFFD81B60),
    colorScheme: ColorScheme.fromSeed(
      seedColor: const Color(0xFFD81B60),
      brightness: Brightness.light,
      primary: const Color(0xFFD81B60),
      secondary: const Color(0xFFF06292),
      surface: const Color(0xFFFFE0EC),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: Color(0xFFD81B60),
      foregroundColor: Colors.white,
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: const Color(0xFFD81B60)),
    ),
    inputDecorationTheme: const InputDecorationTheme(
      filled: true,
      fillColor: Color(0xFFFFE0EC),
    ),
  );
}