import 'package:flutter_local_notifications/flutter_local_notifications.dart';
// ignore: unused_import
import 'package:timezone/timezone.dart' as tz;
import 'package:timezone/data/latest_all.dart' as tzdata;
import '../models/course_model.dart';
import 'schedule_utils.dart';
import '../ui/screens/calendar_tab.dart';

class NotificationService {
  static final FlutterLocalNotificationsPlugin _plugin = FlutterLocalNotificationsPlugin();
  static bool _initialized = false;

  static Future<void> init() async {
    if (_initialized) return;
    tzdata.initializeTimeZones();
    const androidInit = AndroidInitializationSettings('@mipmap/ic_launcher');
    const initSettings = InitializationSettings(android: androidInit);
    await _plugin.initialize(initSettings);
    _initialized = true;
  }

  static Future<void> requestPermissions() async {
    final androidImpl = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
    await androidImpl?.requestNotificationsPermission();
    await androidImpl?.requestExactAlarmsPermission();
  }

  static tz.TZDateTime _toTz(DateTime dt) => tz.TZDateTime.from(dt, tz.local);

  static const NotificationDetails _examDetails = NotificationDetails(
    android: AndroidNotificationDetails(
      'exam_reminders',
      'تذكير الامتحانات',
      channelDescription: 'تذكير يومي بموعد الامتحانات القادمة',
      importance: Importance.high,
      priority: Priority.high,
      sound: RawResourceAndroidNotificationSound('asu_smart_notification'),
      playSound: true,
    ),
  );

  static const NotificationDetails _feeDetails = NotificationDetails(
    android: AndroidNotificationDetails(
      'fee_reminders',
      'تذكير الرسوم والأقساط',
      channelDescription: 'تذكير بمواعيد دفع رسوم الجامعة',
      importance: Importance.high,
      priority: Priority.high,
      sound: RawResourceAndroidNotificationSound('asu_smart_notification'),
      playSound: true,
    ),
  );

  static const NotificationDetails _lectureDetails = NotificationDetails(
    android: AndroidNotificationDetails(
      'lecture_reminders',
      'تذكير المحاضرات',
      channelDescription: 'تذكير قبل بدء المحاضرة بـ15 دقيقة',
      importance: Importance.high,
      priority: Priority.high,
      sound: RawResourceAndroidNotificationSound('asu_smart_notification'),
      playSound: true,
    ),
  );

  static Future<void> cancelAll() async {
    await _plugin.cancelAll();
  }

  /// إشعار يومي تنازلي (10 أيام حتى يوم الامتحان) لكل امتحان منتصف/نهائي بكل مادة مسجلة
  static Future<void> scheduleExamReminders(List<CourseModel> courses) async {
    final now = DateTime.now();
    int id = 1000;
    for (final course in courses) {
      final entries = [
        (course.midExamDate, course.midExamTime, 'امتحان المنتصف'),
        (course.finalExamDate, course.finalExamTime, 'الامتحان النهائي'),
      ];
      for (final entry in entries) {
        final dateStr = entry.$1;
        final timeStr = entry.$2;
        final label = entry.$3;
        if (dateStr == null || dateStr.isEmpty) continue;
        final time = (timeStr != null && timeStr.isNotEmpty) ? ScheduleUtils.parseArabicTime(timeStr) : null;
        final examDate = ScheduleUtils.parseFlexibleDate(dateStr, time: time);
        if (examDate == null) continue;

        for (int daysBefore = 10; daysBefore >= 0; daysBefore--) {
          final notifyDate = DateTime(examDate.year, examDate.month, examDate.day,13, 0)
              .subtract(Duration(days: daysBefore));
          if (notifyDate.isBefore(now)) continue;
          final courseName = course.nameAr.isNotEmpty ? course.nameAr : course.nameEn;
          final title = daysBefore == 0
              ? 'اليوم: $label - $courseName'
              : 'باقي $daysBefore ${daysBefore == 1 ? "يوم" : "أيام"} على $label';
          await _plugin.zonedSchedule(
            id++,
            title,
            '$courseName • قاعة ${course.hall}',
            _toTz(notifyDate),
            _examDetails,
            androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
            uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
          );
        }
      }
    }
  }

  /// إشعار قبل 7 أيام من بداية كل فصل واختبارات المنتصف والنهائي (كمواعيد للأقساط)
  static Future<void> scheduleInstallmentReminders(List<CalendarTermInfo> academicCalendar) async {
    final now = DateTime.now();
    int id = 5000;
    const triggers = ['بدء الدراسة', 'اختبارات منتصف الفصل الدراسي', 'فترة الامتحانات النهائية'];

    for (final term in academicCalendar) {
      for (final event in term.events) {
        final matches = triggers.any((t) => event.title.contains(t));
        if (!matches) continue;
        final eventDate = ScheduleUtils.parseFlexibleDate(event.dateFrom.replaceAll('/', '-'));
        if (eventDate == null) continue;
        final notifyDate = DateTime(eventDate.year, eventDate.month, eventDate.day,13, 0)
            .subtract(const Duration(days: 7));
        if (notifyDate.isBefore(now)) continue;
        await _plugin.zonedSchedule(
          id++,
          'تذكير: قسط الجامعة',
          'القسط القادم بعد 7 أيام ("${event.title}"). يرجى سداده أو التواصل مع إدارة الشؤون المالية.',
          _toTz(notifyDate),
          _feeDetails,
          androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
          uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
        );
      }
    }
  }

  /// إشعار قبل بدء كل محاضرة بـ15 دقيقة، متكرر أسبوعياً لمدة 12 أسبوع قادمة
  static Future<void> scheduleLectureReminders(List<CourseModel> courses) async {
    final now = DateTime.now();
    int id = 9000;
    for (final course in courses) {
      final weekdays = ScheduleUtils.parseWeekdays(course.days);
      final time = ScheduleUtils.parseArabicTime(course.startTime);
      if (weekdays.isEmpty || time == null) continue;

      for (final weekday in weekdays) {
        for (int weekOffset = 0; weekOffset < 12; weekOffset++) {
          final day = ScheduleUtils.nextOccurrenceOfWeekday(now, weekday, weekOffset);
          final lectureDt = DateTime(day.year, day.month, day.day, time.hour, time.minute);
          final notifyDt = lectureDt.subtract(const Duration(minutes: 15));
          if (notifyDt.isBefore(now)) continue;
          final courseName = course.nameAr.isNotEmpty ? course.nameAr : course.nameEn;
          await _plugin.zonedSchedule(
            id++,
            'محاضرة بعد 15 دقيقة',
            '$courseName • قاعة ${course.hall}',
            _toTz(notifyDt),
            _lectureDetails,
            androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
            uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
          );
        }
      }
    }
  }
}