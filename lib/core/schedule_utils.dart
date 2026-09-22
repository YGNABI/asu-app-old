import 'package:flutter/material.dart';

class ScheduleUtils {
  static const Map<String, int> arabicDayToWeekday = {
    'الأحد': DateTime.sunday,
    'الاثنين': DateTime.monday,
    'الثلاثاء': DateTime.tuesday,
    'الأربعاء': DateTime.wednesday,
    'الخميس': DateTime.thursday,
    'الجمعة': DateTime.friday,
    'السبت': DateTime.saturday,
  };

  /// "الأحد الثلاثاء" -> [7, 2]
  static List<int> parseWeekdays(String daysText) {
    final result = <int>[];
    for (final entry in arabicDayToWeekday.entries) {
      if (daysText.contains(entry.key)) result.add(entry.value);
    }
    return result;
  }

  /// "3:00 م" -> TimeOfDay(hour: 15, minute: 0)
  static TimeOfDay? parseArabicTime(String raw) {
    final match = RegExp(r'(\d{1,2}):(\d{2})\s*(ص|م)').firstMatch(raw.trim());
    if (match == null) return null;
    int h = int.parse(match.group(1)!);
    final m = int.parse(match.group(2)!);
    final suffix = match.group(3);
    if (suffix == 'م' && h != 12) h += 12;
    if (suffix == 'ص' && h == 12) h = 0;
    return TimeOfDay(hour: h, minute: m);
  }

  /// يقبل "22-10-2026" أو "2026-10-22" أو بفواصل / أو .
  static DateTime? parseFlexibleDate(String raw, {TimeOfDay? time}) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty) return null;
    final parts = trimmed.split(RegExp(r'[-/.]'));
    if (parts.length != 3) return null;
    int? day, month, year;
    if (parts[0].length == 4) {
      year = int.tryParse(parts[0]);
      month = int.tryParse(parts[1]);
      day = int.tryParse(parts[2]);
    } else {
      day = int.tryParse(parts[0]);
      month = int.tryParse(parts[1]);
      year = int.tryParse(parts[2]);
    }
    if (day == null || month == null || year == null) return null;
    try {
      return DateTime(year, month, day, time?.hour ?? 9, time?.minute ?? 0);
    } catch (_) {
      return null;
    }
  }

  static DateTime nextOccurrenceOfWeekday(DateTime from, int weekday, int weekOffset) {
    final daysUntil = (weekday - from.weekday + 7) % 7;
    return from.add(Duration(days: daysUntil + 7 * weekOffset));
  }
}