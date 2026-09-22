import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:device_calendar/device_calendar.dart';
import 'package:timezone/timezone.dart' as tz;
import '../ui/debug/debug_log.dart';
import '../ui/screens/sis_api_service.dart';
import '../models/course_model.dart';
import '../models/moodle_model.dart';

enum MoodleCalendarInsertResult { added, duplicate, error }

class MoodleService {
  static const String baseUrl = 'https://elearning.asu.edu.bh';
  static bool _loggedIn = false;

  static Future<void> _lock = Future.value();
  static Future<T> _serialized<T>(Future<T> Function() task) {
    final completer = Completer<T>();
    _lock = _lock.then((_) async {
      try {
        completer.complete(await task());
      } catch (e, st) {
        completer.completeError(e, st);
      }
    });
    return completer.future;
  }

  static WebViewController get _controller => SisApiService.webViewController;

  static Future<void> _navigateAndWait(String url) async {
    final controller = _controller;
    final done = Completer<void>();
    controller.setNavigationDelegate(
      NavigationDelegate(
        onNavigationRequest: (request) {
          if (request.url.startsWith('http://')) {
            controller.loadRequest(Uri.parse(request.url.replaceFirst('http://', 'https://')));
            return NavigationDecision.prevent;
          }
          return NavigationDecision.navigate;
        },
        onPageFinished: (_) {
          if (!done.isCompleted) done.complete();
        },
      ),
    );
    await controller.loadRequest(Uri.parse(url));
    try {
      await done.future.timeout(const Duration(seconds: 20));
    } catch (_) {}
    await _waitForStableHtml();
  }

  static Future<String> _waitForStableHtml() async {
    final controller = _controller;
    String previous = '';
    int stableCount = 0;
    for (var i = 0; i < 20; i++) {
      await Future.delayed(const Duration(milliseconds: 500));
      final raw = await controller.runJavaScriptReturningResult('document.documentElement.outerHTML') as String;
      final current = _decodeJsString(raw);
      if (current == previous && current.length > 500) {
        stableCount++;
        if (stableCount >= 2) return current;
      } else {
        stableCount = 0;
      }
      previous = current;
    }
    return previous;
  }

  static String _decodeJsString(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is String) return decoded;
    } catch (_) {}
    if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
      return raw.substring(1, raw.length - 1);
    }
    return raw;
  }

  static String _jsEscape(String s) => s.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

  // ===== تسجيل الدخول =====
  static Future<bool> ensureLoggedIn() {
    return _serialized(() => _ensureLoggedInInternal());
  }

  static Future<bool> _ensureLoggedInInternal() async {
    await _navigateAndWait('$baseUrl/?redirect=0');
    final html = await _waitForStableHtml();
    final doc = html_parser.parse(html);

    final hasLoginForm = doc.querySelector('#username') != null || doc.querySelector('input[name="username"]') != null;
    if (!hasLoginForm) {
      _loggedIn = true;
      return true;
    }

    String? user = SisApiService.sessionUsername;
    String? pass = SisApiService.sessionPassword;
    if (user == null || pass == null) {
      const secureStorage = FlutterSecureStorage();
      user = await secureStorage.read(key: 'saved_username');
      pass = await secureStorage.read(key: 'saved_password');
    }

    if (user == null || pass == null) {
      DebugLog.log('MOODLE: no cached credentials available for auto-login');
      return false;
    }

    final controller = _controller;
    final done = Completer<void>();
    controller.setNavigationDelegate(
      NavigationDelegate(
        onNavigationRequest: (request) {
          if (request.url.startsWith('http://')) {
            controller.loadRequest(Uri.parse(request.url.replaceFirst('http://', 'https://')));
            return NavigationDecision.prevent;
          }
          return NavigationDecision.navigate;
        },
        onPageFinished: (_) {
          if (!done.isCompleted) done.complete();
        },
      ),
    );

    await controller.runJavaScript('''
      (function() {
        var u = document.querySelector('#username') || document.querySelector('input[name="username"]');
        var p = document.querySelector('#password') || document.querySelector('input[name="password"]');
        if (u) u.value = "${_jsEscape(user)}";
        if (p) p.value = "${_jsEscape(pass)}";
        var btn = document.querySelector('#loginbtn') || document.querySelector('button[type="submit"]');
        var form = document.querySelector('#login') || (u ? u.closest('form') : null);
        if (btn) { btn.click(); } else if (form) { form.submit(); }
      })();
    ''');

    try {
      await done.future.timeout(const Duration(seconds: 20));
    } catch (_) {}
    final afterHtml = await _waitForStableHtml();
    final stillOnLogin = afterHtml.contains('id="username"') || afterHtml.contains('name="username"');
    DebugLog.log('MOODLE LOGIN: stillOnLogin=$stillOnLogin');
    _loggedIn = !stillOnLogin;
    return _loggedIn;
  }

  // ===== قائمة مواد Moodle =====
  static Future<List<MoodleCourseSummary>> fetchMoodleCourses() {
    return _serialized(() => _fetchMoodleCoursesInternal());
  }

  static Future<List<MoodleCourseSummary>> _fetchMoodleCoursesInternal() async {
    final ok = await _ensureLoggedInInternal();
    if (!ok) return [];
    await _navigateAndWait('$baseUrl/?redirect=0');
    await Future.delayed(const Duration(seconds: 4));
    final html = await _waitForStableHtml();
    final doc = html_parser.parse(html);
    
    final tempCourses = <Map<String, String>>[];
    for (final card in doc.querySelectorAll('[data-region="course-content"][data-course-id]')) {
      final id = card.attributes['data-course-id'] ?? '';
      final nameEl = card.querySelector('a.coursename');
      final name = nameEl?.text.trim() ?? '';
      if (id.isNotEmpty && name.isNotEmpty) {
        tempCourses.add({'id': id, 'name': name});
      }
    }

    final result = <MoodleCourseSummary>[];

    for (final c in tempCourses) {
      final id = c['id']!;
      final name = c['name']!;
      String? courseCode;

      try {
        await _navigateAndWait('$baseUrl/course/view.php?id=$id');
        final raw = await _controller.runJavaScriptReturningResult(
          "(function(){var el=document.querySelector('.format-asu-meta span'); return el ? el.textContent.trim() : '';})();",
        ) as String;
        final rawCode = _decodeJsString(raw);
        if (rawCode.isNotEmpty) {
          courseCode = rawCode;
        }
      } catch (e) {
        DebugLog.log('Error fetching course code for $id: $e');
      }

      result.add(MoodleCourseSummary(moodleId: id, name: name, courseCode: courseCode));
      DebugLog.log('MOODLE COURSE: id=$id name="$name" code=$courseCode');
    }

    DebugLog.log('MOODLE: found ${result.length} courses with exact meta codes');
    return result;
  }

  static String _normalizeCode(String code) {
    return code.toUpperCase().replaceAll(RegExp(r'[^A-Z0-9]'), '');
  }

  // المطابقة المرنة الشاملة لتجنب أي فشل بين SIS و Moodle
  static Map<String, String> matchCourses(List<CourseModel> sisCourses, List<MoodleCourseSummary> moodleCourses) {
    if (sisCourses.isEmpty || moodleCourses.isEmpty) return {};

    final result = <String, String>{};
    for (final sisCourse in sisCourses) {
      final sisClean = _normalizeCode(sisCourse.code);
      MoodleCourseSummary? matched;

      for (final m in moodleCourses) {
        if (m.courseCode != null && m.courseCode!.isNotEmpty) {
          final moodleClean = _normalizeCode(m.courseCode!);
          if (moodleClean.contains(sisClean) || sisClean.contains(moodleClean)) {
            matched = m;
            break;
          }
        }
      }

      if (matched != null) {
        result[sisClean] = matched.moodleId;
        DebugLog.log('MOODLE MATCH: SUCCESS SIS="${sisCourse.code}" -> Moodle="${matched.courseCode}"');
      } else {
        DebugLog.log('MOODLE MATCH: FAILED for SIS="${sisCourse.code}"');
      }
    }
    DebugLog.log('MOODLE MATCH: matched ${result.length} of ${sisCourses.length} SIS courses');
    return result;
  }

  static List<CourseModel> _sisCoursesCache = [];
  static Map<String, String>? _matchCache;
  static bool _matchInProgress = false;

  static void setSisCourses(List<CourseModel> courses) {
    _sisCoursesCache = courses;
  }

  static Future<void> _loadMatchCacheFromDisk() async {
    if (_matchCache != null) return;
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString('moodle_match_cache');
    if (raw != null && raw.isNotEmpty) {
      try {
        final decoded = jsonDecode(raw) as Map<String, dynamic>;
        _matchCache = decoded.map((k, v) => MapEntry(k, v.toString()));
      } catch (_) {}
    }
  }

  static Future<void> _saveMatchCacheToDisk() async {
    if (_matchCache == null) return;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('moodle_match_cache', jsonEncode(_matchCache));
  }

  static Future<void> ensureMatchCacheBuilt() async {
    await _loadMatchCacheFromDisk();
    if (_matchCache != null && _matchCache!.isNotEmpty) return;
    if (_matchInProgress) return;
    _matchInProgress = true;
    try {
      final moodleCourses = await fetchMoodleCourses();
      _matchCache = matchCourses(_sisCoursesCache, moodleCourses);
      await _saveMatchCacheToDisk();
    } finally {
      _matchInProgress = false;
    }
  }

  static Future<String?> resolveMoodleId(String sisCode) async {
    await _loadMatchCacheFromDisk();
    if (_matchCache == null || _matchCache!.isEmpty) {
      await ensureMatchCacheBuilt();
    }
    return _matchCache?[_normalizeCode(sisCode)];
  }

  // ===== المواد التعليمية =====
  static const String _weeklyExtractionJs = r'''
    (function() {
      var out = [];
      var sections = document.querySelectorAll('#format-asu-course-content li.section[data-sectionname]');
      sections.forEach(function(sec) {
        var weekName = sec.getAttribute('data-sectionname') || '';
        var groups = sec.querySelectorAll('.asu-learning-group');
        var cats = [];
        groups.forEach(function(g) {
          var headingH3 = g.querySelector('.asu-category-heading-list h3');
          var label = headingH3 ? headingH3.textContent.trim() : '';
          var activityList = g.querySelector('.asu-category-activity-list');
          if (!activityList) return;
          if (activityList.querySelector('.asu-category-empty')) return;
          var files = [];
          var items = activityList.querySelectorAll('li.activity.resource');
          items.forEach(function(li) {
            var a = li.querySelector('a.aalink');
            if (!a) return;
            var nameSpan = li.querySelector('.instancename');
            var name = '';
            if (nameSpan && nameSpan.childNodes.length > 0) {
              name = (nameSpan.childNodes[0].textContent || '').trim();
            }
            if (!name) name = (a.textContent || '').trim();
            var badge = li.querySelector('.activitybadge');
            var type = badge ? badge.textContent.trim() : '';
            files.push({ name: name, url: a.href, type: type });
          });
          if (files.length > 0) cats.push({ label: label, files: files });
        });
        if (cats.length > 0) out.push({ week: weekName, categories: cats });
      });
      return JSON.stringify(out);
    })();
  ''';

  static Future<List<MoodleWeek>> fetchCourseMaterials(String moodleId) {
    return _serialized(() => _fetchCourseMaterialsInternal(moodleId));
  }

  static Future<List<MoodleWeek>> _fetchCourseMaterialsInternal(String moodleId) async {
    final ok = await _ensureLoggedInInternal();
    if (!ok) return [];
    await _navigateAndWait('$baseUrl/course/view.php?id=$moodleId&asupage=weekly');

    final raw = await _controller.runJavaScriptReturningResult(_weeklyExtractionJs) as String;
    final jsonStr = _decodeJsString(raw);
    try {
      final arr = jsonDecode(jsonStr) as List<dynamic>;
      return arr.map((w) {
        final cats = (w['categories'] as List<dynamic>).map((c) {
          final files = (c['files'] as List<dynamic>)
              .map((f) => MoodleFileItem(name: f['name'] ?? '', url: f['url'] ?? '', type: f['type'] ?? ''))
              .toList();
          return MoodleCategory(label: c['label'] ?? '', files: files);
        }).toList();
        return MoodleWeek(week: w['week'] ?? '', categories: cats);
      }).toList();
    } catch (e) {
      DebugLog.log('MOODLE MATERIALS parse error: $e');
      return [];
    }
  }

  // ===== الواجبات والبحوث =====
  static const String _hubExtractionJs = r'''
    (function() {
      var out = [];
      var wanted = { 'assessment-assessments': true, 'assessment-research': true };
      var groups = document.querySelectorAll('.asu-learning-group');
      groups.forEach(function(g) {
        var cat = g.getAttribute('data-asu-category') || '';
        if (!wanted[cat]) return;
        var headingH3 = g.querySelector('.asu-category-heading-list h3');
        var label = headingH3 ? headingH3.textContent.trim() : cat;
        var items = g.querySelectorAll('.asu-category-activity-list li.activity');
        items.forEach(function(li) {
          if (li.className.indexOf('turnitintooltwo') === -1) return;
          var a = li.querySelector('a.aalink');
          if (!a) return;
          var nameSpan = li.querySelector('.instancename');
          var name = '';
          if (nameSpan && nameSpan.childNodes.length > 0) {
            name = (nameSpan.childNodes[0].textContent || '').trim();
          }
          if (!name) name = (a.textContent || '').trim();
          out.push({ name: name, url: a.href, category: label });
        });
      });
      return JSON.stringify(out);
    })();
  ''';

  static const String _itemExtractionJs = r'''
    (function() {
      var dueDate = '';
      var rows = document.querySelectorAll('.mod_turnitintooltwo_part_details tbody tr');
      for (var i = 0; i < rows.length; i++) {
        var cells = rows[i].querySelectorAll('td.data.cell');
        if (cells.length >= 3) { dueDate = cells[2].textContent.trim(); break; }
      }
      var introEl = document.querySelector('.activity-description#intro');
      var instructions = introEl ? (introEl.innerText || introEl.textContent || '').trim() : '';
      return JSON.stringify({ dueDateRaw: dueDate, instructions: instructions });
    })();
  ''';

  static Future<List<AssessmentItem>> fetchAssessments(String moodleId) {
    return _serialized(() => _fetchAssessmentsInternal(moodleId));
  }

  static Future<List<AssessmentItem>> _fetchAssessmentsInternal(String moodleId) async {
    final ok = await _ensureLoggedInInternal();
    if (!ok) return [];

    await _navigateAndWait('$baseUrl/course/view.php?id=$moodleId&asupage=assessments');
    final rawHub = await _controller.runJavaScriptReturningResult(_hubExtractionJs) as String;
    final hubJson = _decodeJsString(rawHub);

    List<dynamic> entries;
    try {
      entries = jsonDecode(hubJson) as List<dynamic>;
    } catch (_) {
      return [];
    }

    final items = <AssessmentItem>[];
    for (final e in entries) {
      final name = e['name'] ?? '';
      final url = e['url'] ?? '';
      final category = e['category'] ?? '';
      if (url.isEmpty) continue;

      await _navigateAndWait(url);
      final rawItem = await _controller.runJavaScriptReturningResult(_itemExtractionJs) as String;
      final itemJson = _decodeJsString(rawItem);
      String dueDateRaw = '';
      String instructions = '';
      try {
        final obj = jsonDecode(itemJson);
        dueDateRaw = obj['dueDateRaw'] ?? '';
        instructions = obj['instructions'] ?? '';
      } catch (_) {}

      items.add(AssessmentItem(name: name, category: category, url: url, dueDateRaw: dueDateRaw, instructionsText: instructions));
    }
    return items;
  }

  // ===== تنزيل الملفات =====
  static String _sanitizeFileName(String name) => name.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_').trim();

  static String _expectedFileName(String name, String type) {
    final ext = type.trim().toLowerCase();
    final clean = _sanitizeFileName(name);
    if (ext.isNotEmpty && !name.toLowerCase().endsWith('.$ext')) return '$clean.$ext';
    return clean;
  }

  static Future<Directory> _localFilesDir(String moodleId) async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/moodle_files/$moodleId');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  static Future<Set<String>> downloadedFileNames(String moodleId) async {
    final dir = await _localFilesDir(moodleId);
    if (!await dir.exists()) return {};
    return dir.listSync().map((f) => f.path.split('/').last).toSet();
  }

  static Future<File?> getOrDownloadFile(String moodleId, MoodleFileItem file) async {
    final dir = await _localFilesDir(moodleId);
    final localFile = File('${dir.path}/${_expectedFileName(file.name, file.type)}');
    
    if (await localFile.exists()) {
      if (await localFile.length() < 5000 && localFile.path.toLowerCase().endsWith('.pdf')) {
        await localFile.delete();
      } else if (await localFile.length() > 0) {
        return localFile;
      }
    }

    try {
      final ok = await _ensureLoggedInInternal();
      if (!ok) return null;

      final jsCode = '''
        window.moodleFileDone = false;
        window.moodleFileB64 = '';
        window.moodleFileErr = '';
        fetch("${_jsEscape(file.url)}")
          .then(r => {
             if (!r.ok) throw new Error("HTTP " + r.status);
             return r.blob();
          })
          .then(blob => {
             var reader = new FileReader();
             reader.onloadend = function() {
                window.moodleFileB64 = reader.result;
                window.moodleFileDone = true;
             };
             reader.onerror = function() {
                window.moodleFileErr = "Reader error";
                window.moodleFileDone = true;
             };
             reader.readAsDataURL(blob);
          })
          .catch(e => {
             window.moodleFileErr = e.message;
             window.moodleFileDone = true;
          });
      ''';

      await _controller.runJavaScript(jsCode);

      String base64Data = '';
      for (int i = 0; i < 60; i++) {
        await Future.delayed(const Duration(milliseconds: 500));
        final doneRaw = await _controller.runJavaScriptReturningResult('window.moodleFileDone');
        if (doneRaw.toString() == 'true') {
          final errRaw = await _controller.runJavaScriptReturningResult('window.moodleFileErr');
          final err = _decodeJsString(errRaw.toString());
          if (err.isNotEmpty) break;
          final b64Raw = await _controller.runJavaScriptReturningResult('window.moodleFileB64');
          base64Data = _decodeJsString(b64Raw.toString());
          break;
        }
      }

      await _controller.runJavaScript('window.moodleFileB64 = ""; window.moodleFileDone = false;');

      if (base64Data.isEmpty) return null;
      if (base64Data.startsWith('data:text/html')) return null;

      if (base64Data.contains(',')) {
        final b64String = base64Data.substring(base64Data.indexOf(',') + 1);
        final bytes = base64Decode(b64String.replaceAll('\\n', '').replaceAll('\\r', '').replaceAll(RegExp(r'\\s+'), ''));
        await localFile.writeAsBytes(bytes);
        return localFile;
      }

      return null;
    } catch (e) {
      DebugLog.log('MOODLE DOWNLOAD error: $e');
      return null;
    }
  }

  // ===== تحليل تاريخ التسليم =====
  static DateTime? parseDueDate(String raw) {
    try {
      final parts = raw.split(' - ');
      if (parts.length != 2) return null;
      final dateParts = parts[0].trim().split(' ');
      if (dateParts.length != 3) return null;
      final day = int.tryParse(dateParts[0].trim());
      if (day == null) return null;
      const months = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];
      final monthRaw = dateParts[1].trim().toLowerCase();
      final monthStr = monthRaw.length >= 3 ? monthRaw.substring(0, 3) : monthRaw;
      final month = months.indexOf(monthStr);
      if (month < 0) return null;
      final year = int.tryParse(dateParts[2].trim());
      if (year == null) return null;
      final timeParts = parts[1].trim().split(':');
      final hour = int.tryParse(timeParts.isNotEmpty ? timeParts[0].trim() : '') ?? 23;
      final minute = int.tryParse(timeParts.length > 1 ? timeParts[1].trim() : '') ?? 59;
      return DateTime(year, month + 1, day, hour, minute);
    } catch (_) {
      return null;
    }
  }

  // ===== مزامنة التقويم =====
  static final DeviceCalendarPlugin _calendarPlugin = DeviceCalendarPlugin();

  static Future<String?> _primaryCalendarId() async {
    final permGranted = await _calendarPlugin.hasPermissions();
    if (permGranted.data != true) {
      final req = await _calendarPlugin.requestPermissions();
      if (req.data != true) return null;
    }
    final calsResult = await _calendarPlugin.retrieveCalendars();
    final cals = calsResult.data;
    if (cals == null || cals.isEmpty) return null;
    final primary = cals.firstWhere((c) => c.isDefault == true, orElse: () => cals.first);
    return primary.id;
  }

  static Future<MoodleCalendarInsertResult> insertCalendarEvent(String title, DateTime start, int reminderDays, {String location = ''}) async {
    try {
      final calId = await _primaryCalendarId();
      if (calId == null) return MoodleCalendarInsertResult.error;

      final existing = await _calendarPlugin.retrieveEvents(
        calId,
        RetrieveEventsParams(startDate: start.subtract(const Duration(minutes: 1)), endDate: start.add(const Duration(minutes: 1))),
      );
      final dup = existing.data?.any((e) => e.title == title) ?? false;
      if (dup) return MoodleCalendarInsertResult.duplicate;

      final event = Event(
        calId,
        title: title,
        start: tz.TZDateTime.from(start, tz.local),
        end: tz.TZDateTime.from(start.add(const Duration(hours: 1)), tz.local),
        location: location,
        reminders: [Reminder(minutes: reminderDays * 24 * 60)],
      );
      final result = await _calendarPlugin.createOrUpdateEvent(event);
      if (result?.isSuccess ?? false) return MoodleCalendarInsertResult.added;
      return MoodleCalendarInsertResult.error;
    } catch (e, st) {
      DebugLog.log('CALENDAR insert exception: $e\n$st');
      return MoodleCalendarInsertResult.error;
    }
  }
}