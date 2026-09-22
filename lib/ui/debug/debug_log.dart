import 'package:flutter/foundation.dart';

class DebugLog {
  static final ValueNotifier<List<String>> logs = ValueNotifier<List<String>>([]);

  static void log(String message) {
    final time = DateTime.now().toIso8601String().substring(11, 19);
    final entry = '[$time] $message';
    logs.value = [...logs.value, entry];
    // ignore: avoid_print
    print(entry);
  }

  static void clear() {
    logs.value = [];
  }
}