import 'package:flutter/material.dart';
import 'ui/screens/app_root.dart';
import 'core/theme_service.dart';

// متغير عام لحفظ اللغة الحالية (الافتراضي: عربي) لتحديث التطبيق بالكامل فوراً
final ValueNotifier<Locale> appLanguage = ValueNotifier<Locale>(const Locale('ar'));

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await ThemeService.init();
  runApp(const AsuApp());
}

class AsuApp extends StatelessWidget {
  const AsuApp({super.key});

  @override
  Widget build(BuildContext context) {
    // تغليف التطبيق بـ ValueListenableBuilder ليتحدث فور تغيير اللغة أو الثيم
    return ValueListenableBuilder<Locale>(
      valueListenable: appLanguage,
      builder: (context, locale, child) {
        return ValueListenableBuilder<AppThemeChoice>(
          valueListenable: ThemeService.choice,
          builder: (context, themeChoice, _) {
            ThemeData? forcedTheme;
            ThemeMode themeMode = ThemeMode.system;

            switch (themeChoice) {
              case AppThemeChoice.pink:
                forcedTheme = ThemeService.pinkTheme;
                break;
              case AppThemeChoice.light:
                forcedTheme = ThemeService.lightTheme;
                break;
              case AppThemeChoice.dark:
                forcedTheme = ThemeService.darkTheme;
                break;
              case AppThemeChoice.auto:
                themeMode = ThemeMode.system;
                break;
            }

            return MaterialApp(
              title: 'asu_app',
              debugShowCheckedModeBanner: false,
              locale: locale,
              // تحديد اتجاه التطبيق (من اليمين لليسار أو العكس) بناءً على اللغة المختارة
              builder: (context, child) {
                return Directionality(
                  textDirection: locale.languageCode == 'ar' 
                      ? TextDirection.rtl 
                      : TextDirection.ltr,
                  child: child!,
                );
              },
              theme: forcedTheme ?? ThemeService.lightTheme,
              darkTheme: forcedTheme ?? ThemeService.darkTheme,
              themeMode: forcedTheme != null ? ThemeMode.light : themeMode,
              home: const AppRoot(),
            );
          },
        );
      },
    );
  }
}