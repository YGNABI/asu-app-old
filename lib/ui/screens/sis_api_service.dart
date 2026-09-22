import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:html/dom.dart' as dom;
import '../debug/debug_log.dart';
import '../../models/student_model.dart';
import '../../models/course_model.dart';

class InstallmentItem {
  final String title;
  final double amount;
  final bool isPaid;

  InstallmentItem({
    required this.title,
    required this.amount,
    required this.isPaid,
  });

  Map<String, dynamic> toJson() => {
        'title': title,
        'amount': amount,
        'isPaid': isPaid,
      };

  factory InstallmentItem.fromJson(Map<String, dynamic> json) => InstallmentItem(
        title: json['title'] ?? '',
        amount: (json['amount'] as num?)?.toDouble() ?? 0,
        isPaid: json['isPaid'] ?? false,
      );
}

class SisApiService {
  static const String baseSession = '3865404415206';
  static const String loginPageUrl = 'https://sis.asu.edu.bh/ords/f?p=2020:101';

  // النطاقات المسموح للتطبيق ينتقل لها داخل الـ WebView.
  // sis.asu.edu.bh: نظام SIS نفسه. asu.edu.bh: الموقع الرئيسي (لصور الدكاترة لاحقاً).
  // أضف أي نطاق إضافي هنا بمجرد تأكيده.
  static const List<String> allowedHosts = [
    'sis.asu.edu.bh',
    'asu.edu.bh',
    'sos.asu.edu.bh',
    'elearning.asu.edu.bh',
  ];

  static bool isAllowedHost(String url) {
    final host = Uri.tryParse(url)?.host ?? '';
    if (host.isEmpty) return false;
    return allowedHosts.any((allowed) => host == allowed || host.endsWith('.$allowed'));
  }

  static const String pageHome = '1';
  static const String pagePlan = '3';
  static const String pageAccount = '7';
  static const String pageGrades = '8';
  static const String pageRegInfo = '6';
  static const String pagePayment = '18'; // صفحة الدفع الإلكتروني لسحب الأقساط

  static WebViewController? _controller;
  static bool _controllerReady = false;
  static String _sessionId = '';

  // تُحفظ بالذاكرة فقط (لا تُكتب على القرص) لاستخدامها بتسجيل دخول Moodle لاحقاً بنفس الجلسة
  static String? sessionUsername;
  static String? sessionPassword;
  static bool _hasAuthenticatedThisRun = false;
  static const _secureStorage = FlutterSecureStorage();

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

  static Completer<String>? _photoCompleter;

  static void attachController(WebViewController controller) {
    _controller = controller;
    _controllerReady = true;
    controller.addJavaScriptChannel(
      'PhotoChannel',
      onMessageReceived: (message) {
        if (_photoCompleter != null && !_photoCompleter!.isCompleted) {
          _photoCompleter!.complete(message.message);
        }
      },
    );
  }

  static WebViewController get _requireController {
    if (!_controllerReady || _controller == null) {
      throw StateError('SisApiService: WebViewController not attached yet.');
    }
    return _controller!;
  }

  static WebViewController get webViewController => _requireController;

  static String _buildPageUrl(String pageId) {
    if (_sessionId.isEmpty) {
      return 'https://sis.asu.edu.bh/ords/f?p=2020:$pageId';
    }
    return 'https://sis.asu.edu.bh/ords/f?p=2020:$pageId:$_sessionId';
  }

  static void _tryExtractSessionId(String url) {
    final match = RegExp(r'p=\d+:\d+:(\d+)').firstMatch(url);
    if (match != null && match.group(1)!.isNotEmpty) {
      _sessionId = match.group(1)!;
    }
  }

  static void _tryExtractSessionIdFromHtml(dom.Document doc) {
    final instanceInput = doc.querySelector('input[name="p_instance"]');
    if (instanceInput != null && (instanceInput.attributes['value']?.isNotEmpty ?? false)) {
      _sessionId = instanceInput.attributes['value']!;
    }
  }

  static Future<bool> login(String username, String password) {
    return _serialized(() => _loginInternal(username, password));
  }

  static Future<bool> _loginInternal(String username, String password) async {
    final controller = _requireController;
    final firstLoadDone = Completer<void>();
    final navigationSettled = Completer<void>();
    Timer? quietTimer;
    bool submitted = false;

    void armQuietTimer() {
      quietTimer?.cancel();
      quietTimer = Timer(const Duration(milliseconds: 900), () {
        if (!navigationSettled.isCompleted) navigationSettled.complete();
      });
    }

    controller.setNavigationDelegate(
      NavigationDelegate(
        onNavigationRequest: (request) {
          if (request.url.startsWith('http://')) {
            controller.loadRequest(Uri.parse(request.url.replaceFirst('http://', 'https://')));
            return NavigationDecision.prevent;
          }
          return NavigationDecision.navigate;
        },
        onPageFinished: (url) {
          _tryExtractSessionId(url);
          if (!firstLoadDone.isCompleted) {
            firstLoadDone.complete();
            return;
          }
          if (submitted) armQuietTimer();
        },
      ),
    );

    await controller.loadRequest(Uri.parse(loginPageUrl));
    await firstLoadDone.future;

    final escapedUser = _jsEscape(username);
    final escapedPass = _jsEscape(password);
    submitted = true;
    armQuietTimer();

    await controller.runJavaScript('''
      (function() {
        var u = document.querySelector('input[name="P101_USERNAME"]');
        var p = document.querySelector('input[name="P101_PASSWORD"]');
        if (u) { u.value = "$escapedUser"; }
        if (p) { p.value = "$escapedPass"; }
        if (window.apex && apex.submit) {
          apex.submit('P101_LOGIN');
        } else {
          var form = document.getElementById('wwvFlowForm') || document.forms[0];
          if (form) { form.submit(); }
        }
      })();
    ''');

    await navigationSettled.future.timeout(
      const Duration(seconds: 20),
      onTimeout: () => throw TimeoutException('Login page did not respond in time'),
    );

    final html = await _waitForStableHtml();
    final doc = html_parser.parse(html);
    _tryExtractSessionIdFromHtml(doc);

    final stillOnLoginPage = doc.querySelector('input[name="P101_PASSWORD"]') != null ||
        doc.querySelector('#P101_PASSWORD') != null;
    DebugLog.log('LOGIN: len=${html.length} stillOnLogin=$stillOnLoginPage session=$_sessionId');
    if (!stillOnLoginPage) {
      sessionUsername = username;
      sessionPassword = password;
      _hasAuthenticatedThisRun = true;
    }
    return !stillOnLoginPage;
  }

  static Future<bool> isSessionValid() async {
    try {
      if (!_hasAuthenticatedThisRun) return false;
      final controller = _requireController;
      final currentUrl = await controller.currentUrl();
      if (currentUrl != null && currentUrl.contains('f?p=2020:101')) return false;
      final result = await controller.runJavaScriptReturningResult(
        'document.querySelector("input[name=\\"P101_PASSWORD\\"]") !== null',
      );
      if (result.toString() == 'true') return false;
      final htmlLen = await controller.runJavaScriptReturningResult('document.documentElement.outerHTML.length');
      if (htmlLen is num && htmlLen < 3000) return false;
      return true;
    } catch (_) {
      return false;
    }
  }

  // يتأكد من جلسة صالحة فعلياً؛ لو منتهية يسجّل دخول صامت ببيانات محفوظة مسبقاً
  static Future<bool> ensureAuthenticated() async {
    if (_hasAuthenticatedThisRun && await isSessionValid()) return true;
    try {
      final savedUser = await _secureStorage.read(key: 'saved_username');
      final savedPass = await _secureStorage.read(key: 'saved_password');
      if (savedUser == null || savedPass == null) {
        DebugLog.log('AUTH: no saved credentials for silent login');
        return _hasAuthenticatedThisRun;
      }
      final ok = await login(savedUser, savedPass);
      DebugLog.log('AUTH: silent re-login result=$ok');
      return ok;
    } catch (e) {
      DebugLog.log('AUTH: silent re-login error: $e');
      return false;
    }
  }

  static Future<dom.Document> _fetchPage(String pageId) {
    return _serialized(() => _fetchPageInternal(pageId));
  }

  static Future<dom.Document> _fetchPageInternal(String pageId) async {
    final controller = _requireController;
    final url = _buildPageUrl(pageId);
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
        onPageFinished: (finishedUrl) {
          _tryExtractSessionId(finishedUrl);
          if (!done.isCompleted) done.complete();
        },
      ),
    );

    await controller.loadRequest(Uri.parse(url));
    try {
      await done.future.timeout(const Duration(seconds: 20));
    } catch (_) {}

    final html = await _waitForStableHtml();
    DebugLog.log('PAGE[$pageId] url=$url session=$_sessionId len=${html.length} onLogin=${html.contains('P101_USERNAME')}');
    final doc = html_parser.parse(html);
    _tryExtractSessionIdFromHtml(doc);
    return doc;
  }

  static Future<String> _waitForStableHtml() async {
    String previous = '';
    int stableCount = 0;
    const maxAttempts = 20;
    const checkInterval = Duration(milliseconds: 500);

    for (var i = 0; i < maxAttempts; i++) {
      await Future.delayed(checkInterval);
      final current = await _getCurrentHtml();
      if (current == previous && current.length > 1000) {
        stableCount++;
        if (stableCount >= 2) return current;
      } else {
        stableCount = 0;
      }
      previous = current;
    }
    return previous;
  }

  static Future<String> debugFetchRawHtml(String pageId) async {
    final doc = await _fetchPage(pageId);
    return doc.outerHtml;
  }

  static Future<String> _getCurrentHtml() async {
    final controller = _requireController;
    final raw = await controller.runJavaScriptReturningResult(
      'document.documentElement.outerHTML',
    ) as String;
    return _decodeJsString(raw);
  }

  static dom.Element? _findTableWithHeader(dom.Document doc, String headerIdentifier) {
    for (final table in doc.querySelectorAll('table')) {
      if (table.querySelector('th[id*="$headerIdentifier"]') != null ||
          table.querySelector('td[headers*="$headerIdentifier"]') != null) {
        return table;
      }
    }
    return null;
  }

  static String _cell(dom.Element row, String header) {
    final el = row.querySelector('td[headers*="$header"]');
    return el?.text.trim() ?? '';
  }

  static Future<StudentInfo> fetchStudentInfo() async {
    final result = await _fetchPageAndPhoto();
    final gradesDoc = await _fetchPage(pageGrades);
    final regInfoDoc = await _fetchPage(pageRegInfo);
    final planSummary = await fetchPlanSummary();
    final remainingHours = planSummary
        .where((r) => r.itemHrs > 0)
        .fold<int>(0, (sum, r) => sum + (r.itemHrs - r.passedHrs));
    final remaining = (remainingHours / 3).round().clamp(0, 999);
    return _parseStudentInfoFromDoc(result.$1, gradesDoc, regInfoDoc, remaining, result.$2);
  }

  static Future<(dom.Document, String)> _fetchPageAndPhoto() {
    return _serialized(() => _fetchHomePageAndPhotoInternal());
  }

  static Future<(dom.Document, String)> _fetchHomePageAndPhotoInternal() async {
    final controller = _requireController;
    final url = _buildPageUrl(pageHome);
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
        onPageFinished: (finishedUrl) {
          _tryExtractSessionId(finishedUrl);
          if (!done.isCompleted) done.complete();
        },
      ),
    );

    await controller.loadRequest(Uri.parse(url));
    try {
      await done.future.timeout(const Duration(seconds: 20));
    } catch (_) {}

    final html = await _waitForStableHtml();
    DebugLog.log('HOME PAGE for photo: len=${html.length}');
    final doc = html_parser.parse(html);
    _tryExtractSessionIdFromHtml(doc);

    final hasImgTag = doc.querySelectorAll('img').any((img) {
      final src = img.attributes['src'] ?? '';
      return src.contains('apex_util.get_blob');
    });
    DebugLog.log('PHOTO: img[apex_util.get_blob] found in parsed doc = $hasImgTag');

    String photoDataUrl = '';
    try {
      _photoCompleter = Completer<String>();
      await controller.runJavaScript('''
        (function() {
          var img = document.querySelector('img[src*="apex_util.get_blob"]');
          if (!img) { PhotoChannel.postMessage("NO_IMG_TAG"); return; }
          function shrink(src) {
            try {
              var max = 512;
              var w = src.naturalWidth, h = src.naturalHeight;
              if (!w || !h) { PhotoChannel.postMessage("NO_DIMENSIONS"); return; }
              var scale = Math.min(1, max / Math.max(w, h));
              var cw = Math.max(1, Math.round(w * scale));
              var ch = Math.max(1, Math.round(h * scale));
              var canvas = document.createElement('canvas');
              canvas.width = cw;
              canvas.height = ch;
              canvas.getContext('2d').drawImage(src, 0, 0, cw, ch);
              PhotoChannel.postMessage(canvas.toDataURL('image/jpeg', 0.85));
            } catch (e) {
              PhotoChannel.postMessage("CANVAS_ERROR_" + e.message);
            }
          }
          if (img.complete && img.naturalWidth > 0) { shrink(img); return; }
          var probe = new Image();
          probe.onload = function() { shrink(probe); };
          probe.onerror = function() { PhotoChannel.postMessage("IMG_LOAD_ERROR"); };
          probe.src = img.src;
        })();
      ''');

      final result = await _photoCompleter!.future.timeout(
        const Duration(seconds: 15),
        onTimeout: () => 'TIMEOUT',
      );
      DebugLog.log('PHOTO: channel result prefix = ${result.length > 60 ? result.substring(0, 60) : result} (len=${result.length})');
      if (result.startsWith('data:image')) {
        photoDataUrl = result;
      } else if (result.startsWith('data:application/octet-stream;base64,') ||
          result.startsWith('data:;base64,')) {
        // السيرفر يرجّع نوع عام بدل image/jpeg، لكن البيانات صورة صحيحة - نصحح الترويسة فقط
        final base64Part = result.substring(result.indexOf(',') + 1);
        photoDataUrl = 'data:image/jpeg;base64,$base64Part';
      } else {
        DebugLog.log('PHOTO: unrecognized data URL, discarding. prefix=${result.length > 40 ? result.substring(0, 40) : result}');
      }
    } catch (e) {
      DebugLog.log('PHOTO: exception while fetching = $e');
    } finally {
      _photoCompleter = null;
    }

    return (doc, photoDataUrl);
  }

  static Future<List<CourseModel>> fetchHomeCourses() async {
    final doc = await _fetchPage(pageHome);
    return _parseHomeCourses(doc);
  }

  static StudentInfo _parseStudentInfoFromDoc(dom.Document doc, dom.Document gradesDoc, dom.Document regInfoDoc, int remainingCoursesCount, String photoDataUrl) {
    final map = <String, String>{};
    for (final dl in doc.querySelectorAll('dl.t-AVPList')) {
      final dts = dl.querySelectorAll('dt.t-AVPList-label');
      final dds = dl.querySelectorAll('dd.t-AVPList-value');
      for (var i = 0; i < dts.length && i < dds.length; i++) {
        map[dts[i].text.trim()] = dds[i].text.trim();
      }
    }

    final gradesMap = <String, String>{};
    for (final dl in gradesDoc.querySelectorAll('dl.t-AVPList')) {
      final dts = dl.querySelectorAll('dt.t-AVPList-label');
      final dds = dl.querySelectorAll('dd.t-AVPList-value');
      for (var i = 0; i < dts.length && i < dds.length; i++) {
        gradesMap[dts[i].text.trim()] = dds[i].text.trim();
      }
    }

    final regInfoMap = <String, String>{};
    for (final dl in regInfoDoc.querySelectorAll('dl.t-AVPList')) {
      final dts = dl.querySelectorAll('dt.t-AVPList-label');
      final dds = dl.querySelectorAll('dd.t-AVPList-value');
      for (var i = 0; i < dts.length && i < dds.length; i++) {
        regInfoMap[dts[i].text.trim()] = dds[i].text.trim();
      }
    }

    final rawLabel = doc.querySelector('#P1_EXCPECTED_GRAD_LABEL')?.text ?? '';
    final expectedGradText = rawLabel
        .replaceAll('هل الطالب متوقع تخرجه؟', '')
        .replaceAll('\u00A0', ' ')
        .trim();

    return StudentInfo(
      studentId: map['رقم الطالب'] ?? map['Student ID'] ?? '',
      nameEn: map['Name'] ?? map['الاسم'] ?? map['اسم الطالب'] ?? '',
      college: map['The College'] ?? map['الكلية'] ?? '',
      major: map['Major'] ?? map['التخصص'] ?? map['البرنامج الاكاديمي'] ?? '',
      academicAdvisor: map['المرشد الاكاديمي'] ?? map['Academic Advisor'] ?? '',
      supervisor: map['المشرف'] ?? map['Supervisor'] ?? '',
      gpa: double.tryParse(map['المعدل التراكمي'] ?? map['GPA'] ?? map['CGPA'] ?? '') ?? 0,
      academicWarningStatus: map['حالة الانذار الاكاديمي'] ?? map['Academic Warning'] ?? '-',
      gender: map['الجنس'] ?? map['Gender'] ?? regInfoMap['الجنس'] ?? regInfoMap['Gender'] ?? '',
      schoolTrack: map['School Track'] ?? map['مسار المدرسة'] ?? '',
      highSchoolAverage: double.tryParse(map['معدل الثانوية'] ?? map['High School Average'] ?? '') ?? 0,
      photoUrl: photoDataUrl,
      expectedGraduation: expectedGradText.contains('نعم') || expectedGradText.contains('Yes'),
      remainingCoursesCount: remainingCoursesCount,
      registrationPeriodFrom: regInfoMap['بداية تاريخ التسجيل'] ?? '',
      registrationPeriodTo: regInfoMap['نهاية تاريخ التسجيل'] ?? '',
      addDropPeriodFrom: regInfoMap['بداية تاريخ السحب و الأضافه'] ?? '',
      addDropPeriodTo: regInfoMap['نهاية تاريخ السحب ولأضافه'] ?? '',
      cumulativeHours: double.tryParse(gradesMap['مجموع الساعات التراكمية'] ?? '') ?? 0,
      passedHours: double.tryParse(gradesMap['الساعات التى نجح بها'] ?? '') ?? 0,
      honorRoll: gradesMap['على لائحة الشرف'] ?? '-',
      academicStatus: regInfoMap['الوضع الأكاديمي'] ?? '-',
      planHours: double.tryParse(gradesMap['ساعات الخطه الدراسية'] ?? '') ?? 0,
    );
  }

  // ===== أدوات ترجمة أيام الأسبوع وتحويل الوقت لنظام 12 ساعة =====
  static const Map<String, String> _dayCodeMap = {
    'U': 'الأحد', 'M': 'الاثنين', 'T': 'الثلاثاء', 'W': 'الأربعاء',
    'H': 'الخميس', 'F': 'الجمعة', 'A': 'السبت',
  };
  static const List<String> _dayOrder = ['U', 'M', 'T', 'W', 'H', 'F', 'A'];

  static String translateDayCode(String raw) {
    final trimmed = raw.trim();
    final buffer = StringBuffer();
    for (var i = 0; i < trimmed.length; i++) {
      final ch = trimmed[i].toUpperCase();
      if (_dayCodeMap.containsKey(ch)) {
        if (buffer.isNotEmpty) buffer.write(' ');
        buffer.write(_dayCodeMap[ch]);
      }
    }
    return buffer.isEmpty ? trimmed : buffer.toString();
  }

  static int _firstDayIndex(String raw) {
    final trimmed = raw.trim().toUpperCase();
    for (var i = 0; i < trimmed.length; i++) {
      final idx = _dayOrder.indexOf(trimmed[i]);
      if (idx != -1) return idx;
    }
    return 99;
  }

  static String to12Hour(String raw) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty) return trimmed;
    final match = RegExp(r'^(\d{1,2}):(\d{2})').firstMatch(trimmed);
    if (match == null) return trimmed;
    int h = int.parse(match.group(1)!);
    final m = match.group(2)!;
    final suffix = h >= 12 ? 'م' : 'ص';
    h = h % 12;
    if (h == 0) h = 12;
    return '$h:$m $suffix';
  }

  static int _timeToMinutes(String raw) {
    final match = RegExp(r'^(\d{1,2}):(\d{2})').firstMatch(raw.trim());
    if (match == null) return 9999;
    return int.parse(match.group(1)!) * 60 + int.parse(match.group(2)!);
  }

  static List<CourseModel> _parseHomeCourses(dom.Document doc) {
    final Map<String, CourseModel> byCode = {};

    final scheduleTable = _findTableWithHeader(doc, 'BV_CRM_START_TIME');
    final gradesTable = _findTableWithHeader(doc, 'MID_GRADE');
    final attendanceTable = _findTableWithHeader(doc, 'absence_pct') ?? _findTableWithHeader(doc, 'CRE_CODE');

    if (scheduleTable != null) {
      for (final row in scheduleTable.querySelectorAll('tbody tr')) {
        final code = _cell(row, 'BV_CRM_LINE_NUMBER');
        if (code.isEmpty) continue;

        String arName = '';
        final cells = row.querySelectorAll('td');
        for (int i = 0; i < cells.length; i++) {
          if (cells[i].text.trim() == code) {
            if (i > 0 && cells[i - 1].text.trim().isNotEmpty) {
              arName = cells[i - 1].text.trim();
            } else if (i + 1 < cells.length && cells[i + 1].text.trim().isNotEmpty) {
              arName = cells[i + 1].text.trim();
            }
            break;
          }
        }
        if (arName.isEmpty) arName = _cell(row, 'BV_CRM_CRE_DSCP');

        byCode[code] = CourseModel(
          code: code,
          nameAr: arName,
          nameEn: arName, 
          section: _cell(row, 'BV_CRM_SECTION'),
          instructor: _cell(row, 'LECTURER_NAME'),
          days: _cell(row, 'الايام'),
          startTime: _cell(row, 'BV_CRM_START_TIME'),
          endTime: _cell(row, 'BV_CRM_END_TIME'),
          hall: _cell(row, 'BV_CRM_HAL_CODE'),
          midExamDate: _cell(row, 'MID_EXAM_DATE'),
          midExamTime: _cell(row, 'MID_EXAM_TIME_FROM'),
          finalExamDate: _cell(row, 'FINAL_EXAM_DATE'),
          finalExamTime: _cell(row, 'FINAL_EXAM_FROM'),
        );
      }
    }

    if (gradesTable != null) {
      for (final row in gradesTable.querySelectorAll('tbody tr')) {
        final code = _cell(row, 'BV_CRM_LINE_NUMBER');
        if (code.isEmpty || !byCode.containsKey(code)) continue;

        String arName = _cell(row, 'BV_CRM_CRE_DSCP');
        if (arName.isEmpty) arName = _cell(row, 'COURSE_NAME');
        if (arName.isEmpty) {
          final cells = row.querySelectorAll('td');
          for (int i = 0; i < cells.length - 1; i++) {
            if (cells[i].text.trim() == code) {
              arName = cells[i + 1].text.trim();
              break;
            }
          }
        }

        final old = byCode[code]!;
        byCode[code] = CourseModel(
          code: old.code,
          nameAr: arName.isNotEmpty ? arName : old.nameAr,
          nameEn: arName.isNotEmpty ? arName : old.nameEn, 
          section: old.section,
          instructor: old.instructor,
          days: old.days,
          startTime: old.startTime,
          endTime: old.endTime,
          hall: old.hall,
          attendancePercent: old.attendancePercent,
          midGrade: _parseGrade(_cell(row, 'MID_GRADE')) ?? old.midGrade,
          courseworkGrade: _parseGrade(_cell(row, 'OTHER_GRADE')) ?? old.courseworkGrade,
          finalGrade: _parseGrade(_cell(row, 'FINAL_GRADE')) ?? old.finalGrade,
          totalGrade: _parseGrade(_cell(row, 'TOTAL_GRADE')) ?? old.totalGrade,
          gradeCase: _cell(row, 'GRADE_CASE').isNotEmpty ? _cell(row, 'GRADE_CASE') : old.gradeCase,
          midExamDate: old.midExamDate,
          midExamTime: old.midExamTime,
          finalExamDate: old.finalExamDate,
          finalExamTime: old.finalExamTime,
        );
      }
    }

    if (attendanceTable != null) {
      for (final row in attendanceTable.querySelectorAll('tbody tr')) {
        final code = _cell(row, 'CRE_CODE');
        if (code.isEmpty || !byCode.containsKey(code)) continue;

        final pctText = _cell(row, 'absence_pct').replaceAll('%', '').trim();
        byCode[code] = byCode[code]!.copyWith(attendancePercent: double.tryParse(pctText));
      }
    }

    // ترجمة رموز الأيام والوقت لصيغة 12 ساعة، وترتيب المواد حسب اليوم ثم الوقت
    final rawList = byCode.values.toList();
    final translated = rawList
        .map((c) => CourseModel(
              code: c.code,
              nameAr: c.nameAr,
              nameEn: c.nameEn,
              section: c.section,
              instructor: c.instructor,
              days: translateDayCode(c.days),
              startTime: to12Hour(c.startTime),
              endTime: to12Hour(c.endTime),
              hall: c.hall,
              attendancePercent: c.attendancePercent,
              midGrade: c.midGrade,
              courseworkGrade: c.courseworkGrade,
              finalGrade: c.finalGrade,
              totalGrade: c.totalGrade,
              gradeCase: c.gradeCase,
              midExamDate: c.midExamDate,
              midExamTime: c.midExamTime != null && c.midExamTime!.isNotEmpty ? to12Hour(c.midExamTime!) : c.midExamTime,
              finalExamDate: c.finalExamDate,
              finalExamTime: c.finalExamTime != null && c.finalExamTime!.isNotEmpty ? to12Hour(c.finalExamTime!) : c.finalExamTime,
            ))
        .toList();

    final byIndex = <int, CourseModel>{};
    for (var i = 0; i < translated.length; i++) {
      byIndex[i] = rawList[i];
    }
    translated.sort((a, b) {
      final rawA = rawList.firstWhere((c) => c.code == a.code);
      final rawB = rawList.firstWhere((c) => c.code == b.code);
      final dayCompare = _firstDayIndex(rawA.days).compareTo(_firstDayIndex(rawB.days));
      if (dayCompare != 0) return dayCompare;
      return _timeToMinutes(rawA.startTime).compareTo(_timeToMinutes(rawB.startTime));
    });

    return translated;
  }

  static double? _parseGrade(String raw) {
    final trimmed = raw.trim();
    if (trimmed.isEmpty || trimmed == '-') return null;
    return double.tryParse(trimmed);
  }

  static Future<List<PlanSummaryRow>> fetchPlanSummary() async {
    final doc = await _fetchPage(pagePlan);
    final table = _findTableWithHeader(doc, 'PLAN_ITEM') ?? _findTableWithHeader(doc, 'ITEM_HRS');
    final rows = <PlanSummaryRow>[];
    if (table == null) return rows;

    for (final row in table.querySelectorAll('tbody tr')) {
      rows.add(PlanSummaryRow(
        planItem: _cell(row, 'PLAN_ITEM'),
        groupNo: int.tryParse(_cell(row, 'GROUP_NO')) ?? 0,
        itemHrs: int.tryParse(_cell(row, 'ITEM_HRS')) ?? 0,
        studiedHrs: int.tryParse(_cell(row, 'ITEM_STUDIED_HRS')) ?? 0,
        passedHrs: int.tryParse(_cell(row, 'ITEM_PASSED_HRS')) ?? 0,
      ));
    }
    return rows;
  }

   static Future<List<PlanSection>> fetchPlanSections() async {
    final links = await _fetchPlanDetailLinks();
    final summary = await fetchPlanSummary();
    final sections = <PlanSection>[];

    for (var i = 0; i < summary.length; i++) {
      if (summary[i].planItem.contains('خارج الخطة')) continue;
      final courses = i < links.length
          ? await _fetchPlanDetailForLink(links[i], summary[i].planItem)
          : <PlanDetailRow>[];
      sections.add(PlanSection(summary: summary[i], courses: courses));
    }
    DebugLog.log('PLAN: sections=${sections.length}, totalCourses=${sections.fold<int>(0, (s, x) => s + x.courses.length)}');
    return sections;
  }

  static Future<List<String>> _fetchPlanDetailLinks() async {
    final doc = await _fetchPage(pagePlan);
    final table = _findTableWithHeader(doc, 'PLAN_ITEM') ?? _findTableWithHeader(doc, 'ITEM_HRS');
    final links = <String>[];
    if (table == null) return links;

    for (final row in table.querySelectorAll('tbody tr')) {
      final a = row.querySelector('a');
      final href = a?.attributes['href'];
      if (href != null && href.isNotEmpty) links.add(href);
    }
    DebugLog.log('PLAN: detail links found=${links.length}');
    return links;
  }

  static Future<List<PlanDetailRow>> _fetchPlanDetailForLink(String href, String planItem) {
    return _serialized(() => _fetchPlanDetailForLinkInternal(href, planItem));
  }

  static Future<List<PlanDetailRow>> _fetchPlanDetailForLinkInternal(String href, String planItem) async {
    final controller = _requireController;
    final normalizedHref = href.startsWith('http') ? href : 'https://sis.asu.edu.bh/ords/$href';
    if (!isAllowedHost(normalizedHref)) {
      final parsedHost = Uri.tryParse(normalizedHref)?.host ?? '';
      DebugLog.log('SECURITY: blocked navigation to untrusted host=$parsedHost from href=$href');
      return <PlanDetailRow>[];
    }
    final url = normalizedHref;
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
        onPageFinished: (finishedUrl) {
          _tryExtractSessionId(finishedUrl);
          if (!done.isCompleted) done.complete();
        },
      ),
    );

    await controller.loadRequest(Uri.parse(url));
    try {
      await done.future.timeout(const Duration(seconds: 20));
    } catch (_) {}

    final html = await _waitForStableHtml();
    final doc = html_parser.parse(html);
    _tryExtractSessionIdFromHtml(doc);

    final rows = <PlanDetailRow>[];
    dom.Element? table;
    for (final t in doc.querySelectorAll('table.t-Report-report')) {
      final label = t.attributes['aria-label'] ?? '';
      if (label.contains('تفاصيل')) { table = t; break; }
    }
    table ??= _findTableWithHeader(doc, 'V_CRE_DSCP') ?? _findTableWithHeader(doc, 'V_CRE_LINE_NUMBER');
    if (table == null) return rows;

    for (final row in table.querySelectorAll('tbody tr')) {
      final cells = row.querySelectorAll('td');
      if (cells.length < 3) continue;
      String at(int i) => cells.length > i ? cells[i].text.trim() : '';

      final takenTxt = at(3);
      final passedTxt = at(4);

      // ترتيب الأعمدة يختلف حسب اتجاه الصفحة، نكتشف أيّهما الرمز فعلياً
      final codePattern = RegExp(r'^[A-Za-z]{2,6}\s*\d{2,4}$');
      final c0 = at(0);
      final c1 = at(1);
      final isC0Code = codePattern.hasMatch(c0.trim());
      final realCode = isC0Code ? c0 : c1;
      final realName = isC0Code ? c1 : c0;

      rows.add(PlanDetailRow(
        courseCode: realCode,
        courseName: realName,
        hours: int.tryParse(at(2)) ?? 0,
        taken: takenTxt.contains('نعم') || takenTxt.contains('Yes'),
        passed: passedTxt.contains('نعم') || passedTxt.contains('Yes'),
        prereq: at(5),
        planItem: planItem,
      ));
    }
    return rows;
  }

  static Future<List<PlanDetailRow>> fetchPlanDetails() async {
    final doc = await _fetchPage(pagePlan);
    final table = _findTableWithHeader(doc, 'V_CRE_DSCP') ?? _findTableWithHeader(doc, 'V_CRE_LINE_NUMBER');
    final rows = <PlanDetailRow>[];
    if (table == null) return rows;

    for (final row in table.querySelectorAll('tbody tr')) {
      rows.add(PlanDetailRow(
        courseName: _cell(row, 'V_CRE_DSCP'),
        courseCode: _cell(row, 'V_CRE_LINE_NUMBER'),
        hours: int.tryParse(_cell(row, 'V_CRE_HRS')) ?? 0,
        taken: _cell(row, 'TAKEN').contains('نعم') || _cell(row, 'TAKEN').contains('Yes'),
        passed: _cell(row, 'PASSED').contains('نعم') || _cell(row, 'PASSED').contains('Yes'),
        prereq: _cell(row, 'prereq'),
      ));
    }
    return rows;
  }

  static Future<double> fetchAccountBalance() async {
    final doc = await _fetchPage(pageAccount);
    final el = doc.querySelector('#P7_BALANCE') ?? doc.querySelector('input[name="P7_BALANCE"]');
    final balText = el?.attributes['value'] ?? el?.text.trim() ?? '0';
    return double.tryParse(balText) ?? 0;
  }

  static Future<List<AccountTransaction>> fetchAllAccountTransactions() async {
    final controller = _requireController;
    var doc = await _fetchPage(pageAccount);
    final rows = <AccountTransaction>[];
    final processedKeys = <String>{};

    while (true) {
      dom.Element? table = _findTableWithHeader(doc, 'DOC_NUMBER') ?? 
                           _findTableWithHeader(doc, 'BALANCE') ?? 
                           _findTableWithHeader(doc, 'الرصيد') ?? 
                           _findTableWithHeader(doc, 'رقم');
      
      if (table == null) break;
      
      for (final row in table.querySelectorAll('tbody tr')) {
        final cells = row.querySelectorAll('td');
        if (cells.length < 6) continue;
        String at(int i) => cells.length > i ? cells[i].text.trim() : '';

        final docNum = at(1);
        if (docNum.isEmpty && at(0).isEmpty) continue;
        
        final uniqueKey = '${at(0)}_${docNum}_${at(5)}'; 

        if (!processedKeys.contains(uniqueKey)) {
          processedKeys.add(uniqueKey);
          rows.add(AccountTransaction(
            date: at(0), 
            docNumber: docNum, 
            docType: at(2), 
            fees: double.tryParse(at(3)),
            paid: double.tryParse(at(4)), 
            balance: double.tryParse(at(5)) ?? 0,
            claimType: at(6), 
            paymentMethod: at(7), 
            notes: at(8),
          ));
        }
      }

      const String jsClickNext = '''
        (function() {
          var paginationDivs = document.querySelectorAll('.t-Report-pagination');
          for (var p = 0; p < paginationDivs.length; p++) {
            var links = paginationDivs[p].querySelectorAll('a');
            for (var i = 0; i < links.length; i++) {
              var text = links[i].innerText.trim();
              var title = links[i].getAttribute('title');
              if (text === 'التالي' || text === 'Next' || text === '>' || title === 'التالي' || title === 'Next') {
                 var style = window.getComputedStyle(links[i]);
                 if (style.display !== 'none' && style.visibility !== 'hidden') {
                   links[i].click();
                   return "CLICKED";
                 }
              }
            }
          }
          return "DONE";
        })();
      ''';

      final jsResult = await controller.runJavaScriptReturningResult(jsClickNext) as String;
      final decodedResult = _decodeJsString(jsResult);

      if (decodedResult == "CLICKED") {
        bool changed = false;
        final oldHtml = table.outerHtml;

        for (int i = 0; i < 25; i++) {
          await Future.delayed(const Duration(milliseconds: 400));
          final currentHtmlRaw = await _getCurrentHtml();
          final currentDoc = html_parser.parse(currentHtmlRaw);
          
           dom.Element? newTable = _findTableWithHeader(currentDoc, 'DOC_NUMBER') ?? 
                                             _findTableWithHeader(currentDoc, 'BALANCE') ?? 
                                                                               _findTableWithHeader(currentDoc, 'الرصيد') ?? 
                                                                                                                 _findTableWithHeader(currentDoc, 'رقم');
                                                                                                                          dom.Element? newTable = _findTableWithHeader(currentDoc, 'DOC_NUMBER') ?? _findTableWithHeader(currentDoc, 'BALANCE');
          if (newTable == null) {
            for (final t in currentDoc.querySelectorAll('table')) {
              if (t.classes.contains('t-Report-report')) { newTable = t; break; }
            }
          }

          if (newTable != null && newTable.outerHtml != oldHtml) {
            doc = currentDoc; 
            changed = true;
            break;
          }
        }
        
        if (!changed) break; 
      } else {
        break; 
      }
    }
    return rows;
  }

  // سحب بيانات الأقساط من صفحة الدفع 18
  static Future<List<InstallmentItem>> fetchInstallments() async {
    final doc = await _fetchPage(pagePayment);
    final results = <InstallmentItem>[];

    final dts = doc.querySelectorAll('dt.t-AVPList-label');
    final dds = doc.querySelectorAll('dd.t-AVPList-value');

    for (int i = 0; i < dts.length; i++) {
      final dtText = dts[i].text.trim();
      final ddText = i < dds.length ? dds[i].text.trim() : '';

      if (dtText.contains('قيمة القسط')) {
        String title = 'القسط';
        if (dtText.contains('الاول') || dtText.contains('الأول')) {
          title = 'القسط الأول';
        } else if (dtText.contains('الثاني')) {
          title = 'القسط الثاني';
        } else if (dtText.contains('الثالث')) {
          title = 'القسط الثالث';
        } else {
          title = dtText;
        }

        double amount = double.tryParse(ddText) ?? 0;
        bool isPaid = false;

        if (i + 1 < dts.length) {
          final nextDt = dts[i + 1].text.trim();
          final nextDd = i + 1 < dds.length ? dds[i + 1].text.trim() : '';
          if (nextDt.contains('تم دفع')) {
            isPaid = nextDd.contains('نعم') || nextDd.toUpperCase().contains('YES');
          }
        }

        results.add(InstallmentItem(title: title, amount: amount, isPaid: isPaid));
      }
    }
    return results;
  }

  static Future<List<TermGrades>> fetchAllTermsGrades() async {
    final doc = await _fetchPage(pageGrades);

    dom.Element? mainTable;
    for (final table in doc.querySelectorAll('table')) {
      if (table.querySelector('td.apex_report_break') != null) {
        mainTable = table; break;
      }
    }

    final terms = <TermGrades>[];
    if (mainTable == null) return terms;

    String? currentTermLabel;
    double currentAvg = 0;
    double currentGpa = 0;
    List<CourseModel> currentCourses = [];

    void flush() {
      final label = currentTermLabel;
      if (label != null) {
        terms.add(TermGrades(
          termLabel: label, termAverage: currentAvg,
          gpa: currentGpa, courses: List.of(currentCourses),
        ));
      }
      currentCourses = [];
    }

    for (final row in mainTable.querySelectorAll('tbody tr')) {
      final breakCell = row.querySelector('td.apex_report_break');
      if (breakCell != null) {
        flush();
        final text = breakCell.text.trim();
        currentTermLabel = text.split('Semester Hours').first.split('ساعات الفصل').first.trim();

        final avgMatch = RegExp(r'(Semester Average|معدل الفصل)\s+([\d.]+)').firstMatch(text);
        final gpaMatch = RegExp(r'(GPA|المعدل التراكمي)\s+([\d.]+)').firstMatch(text);
        currentAvg = double.tryParse(avgMatch?.group(2) ?? '') ?? 0;
        currentGpa = double.tryParse(gpaMatch?.group(2) ?? '') ?? 0;
        continue; 
      }

      final cells = row.querySelectorAll('td');
      if (cells.length < 3) continue;
      String at(int i) => cells.length > i ? cells[i].text.trim() : '';

      currentCourses.add(CourseModel(
        code: at(0), nameAr: '', nameEn: at(1), section: '', instructor: '', days: '',
        startTime: '', endTime: '', hall: '',
        midGrade: _parseGrade(at(3)), courseworkGrade: _parseGrade(at(4)),
        finalGrade: _parseGrade(at(5)), totalGrade: _parseGrade(at(6)),
        gradeCase: at(7),
      ));
    }
    flush();
    return terms;
  }

  // ===== صور أعضاء هيئة التدريس (base64 مباشرة من صفحة الكلية على asu.edu.bh) =====
  // كل كلية لها صفحة "اعضاء الهيئة التدريسية والموظفين" فيها كل الصور base64 مضمّنة بالـ HTML
  static const String lawCollegeStaffPageUrl =
      'https://www.asu.edu.bh/%d9%83%d9%84%d9%8a%d8%a9-%d8%a7%d9%84%d8%ad%d9%82%d9%88%d9%82/%d8%a7%d8%b9%d8%b6%d8%a7%d8%a1-%d8%a7%d9%84%d9%87%d9%8a%d8%a6%d8%a9-%d8%a7%d9%84%d8%aa%d8%af%d8%b1%d9%8a%d8%b3%d9%8a%d8%a9-%d9%88%d8%a7%d9%84%d9%85%d9%88%d8%b8%d9%81%d9%8a%d9%86/?lang=ar';

  // أضف رابط كل كلية جديدة هنا بمجرد تجهيزه
  static const List<String> allFacultyStaffPageUrls = [
    lawCollegeStaffPageUrl,
  ];

  static final Map<String, List<(String, String)>> _facultyPhotoCache = {};
  static final Map<String, String> _instructorPhotoResultCache = {};
  static bool _photosPrewarmed = false;

  static String _sanitizeInstructorFileName(String name) =>
      name.trim().replaceAll(RegExp(r'[\\/:*?"<>|]'), '_').replaceAll(RegExp(r'\s+'), '_');

  static Future<Directory> _instructorPhotosDir() async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/instructor_photos');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  static Future<String> _readLocalInstructorPhoto(String instructorName) async {
    final dir = await _instructorPhotosDir();
    final file = File('${dir.path}/${_sanitizeInstructorFileName(instructorName)}.jpg');
    if (await file.exists() && await file.length() > 0) {
      final bytes = await file.readAsBytes();
      return 'data:image/jpeg;base64,${base64Encode(bytes)}';
    }
    return '';
  }

  static Future<void> _saveLocalInstructorPhoto(String instructorName, String dataUrl) async {
    if (!dataUrl.startsWith('data:image')) return;
    try {
      final bytes = base64Decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
      final dir = await _instructorPhotosDir();
      final file = File('${dir.path}/${_sanitizeInstructorFileName(instructorName)}.jpg');
      await file.writeAsBytes(bytes);
    } catch (e) {
      DebugLog.log('INSTRUCTOR PHOTO save error: $e');
    }
  }

  // يسحب صور كل الدكاترة اللي عندهم مواد بالجدول الحالي، مرة وحدة بالخلفية بعد كل تحديث.
  // النتيجة تُحفظ كملف دائم على القرص، فتضل موجودة حتى بعد إغلاق التطبيق بالكامل.
  static Future<void> prewarmInstructorPhotos(List<CourseModel> courses) async {
    if (_photosPrewarmed) return;
    _photosPrewarmed = true;
    for (final c in courses) {
      if (c.instructor.trim().isEmpty) continue;
      if (_instructorPhotoResultCache.containsKey(c.instructor)) continue;

      final local = await _readLocalInstructorPhoto(c.instructor);
      if (local.isNotEmpty) {
        _instructorPhotoResultCache[c.instructor] = local;
        continue;
      }

      final photo = await findInstructorPhoto(c.instructor);
      _instructorPhotoResultCache[c.instructor] = photo;
      if (photo.isNotEmpty) {
        await _saveLocalInstructorPhoto(c.instructor, photo);
      }
    }
  }

  static Future<dom.Document> _fetchRawPage(String url) {
    return _serialized(() => _fetchRawPageInternal(url));
  }

  static Future<dom.Document> _fetchRawPageInternal(String url) async {
    final controller = _requireController;
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
        onPageFinished: (finishedUrl) {
          if (!done.isCompleted) done.complete();
        },
      ),
    );

    await controller.loadRequest(Uri.parse(url));
    try {
      await done.future.timeout(const Duration(seconds: 20));
    } catch (_) {}

    final html = await _waitForStableHtml();
    DebugLog.log('RAW PAGE: url=$url len=${html.length}');
    return html_parser.parse(html);
  }

  // يرجع قائمة (اسم الدكتور بالعربي، رابط الصورة data:image;base64) من صفحة كلية واحدة
  static Future<List<(String, String)>> _fetchFacultyPhotoList(String pageUrl) async {
    if (_facultyPhotoCache.containsKey(pageUrl)) {
      return _facultyPhotoCache[pageUrl]!;
    }
    final doc = await _fetchRawPage(pageUrl);
    final results = <(String, String)>[];
    for (final block in doc.querySelectorAll('div.name_block')) {
      final nameEl = block.querySelector('h2.name__');
      final imgEl = block.querySelector('img');
      final name = nameEl?.text.trim() ?? '';
      final src = imgEl?.attributes['src'] ?? '';
      if (name.isNotEmpty && src.startsWith('data:image')) {
        results.add((name, src));
      }
    }
    _facultyPhotoCache[pageUrl] = results;
    DebugLog.log('FACULTY PHOTOS: extracted ${results.length} entries from $pageUrl');
    return results;
  }

  // تطبيع الاسم العربي للمطابقة: إزالة التشكيل والألقاب وتوحيد الألف/التاء المربوطة والمسافات
  static String _normalizeArabicName(String raw) {
    var s = raw.trim();
    s = s.replaceAll(RegExp(r'[\u064B-\u0652]'), ''); // تشكيل
    s = s.replaceAll(RegExp(r'^(د\.|دكتور|أ\.د\.|أ\.م\.د\.|الدكتور|أستاذ)\s*'), '');
    s = s.replaceAll(RegExp(r'[إأآا]'), 'ا');
    s = s.replaceAll('ى', 'ي');
    s = s.replaceAll('ة', 'ه');
    s = s.replaceAll(RegExp(r'\s+'), ' ');
    return s.trim();
  }

  // نسبة تطابق بسيطة بالاعتماد على تقاطع الكلمات (Token overlap)، تتحمّل نقص/زيادة كلمة بالاسم
  static double _nameSimilarity(String a, String b) {
    final tokensA = _normalizeArabicName(a).split(' ').where((t) => t.isNotEmpty).toSet();
    final tokensB = _normalizeArabicName(b).split(' ').where((t) => t.isNotEmpty).toSet();
    if (tokensA.isEmpty || tokensB.isEmpty) return 0;
    int matched = 0;
    for (final t in tokensA) {
      if (tokensB.contains(t)) {
        matched++;
      } else if (tokensB.any((x) => x.startsWith(t) || t.startsWith(x))) {
        matched++;
      }
    }
    final union = tokensA.length + tokensB.length - matched;
    return union <= 0 ? 0 : matched / union;
  }

  // البحث عن صورة دكتور باسمه كما ورد بجدول SIS (اسم المدرس)، بمطابقة تقريبية عبر كل صفحات الكليات المجهّزة
  // يرجع data URL (data:image/...;base64,...) أو نص فارغ إذا ما لقى تطابق كافي
  static Future<String> findInstructorPhoto(
    String instructorNameFromSis, {
    List<String> collegePageUrls = allFacultyStaffPageUrls,
    double threshold = 0.5,
  }) async {
    if (instructorNameFromSis.trim().isEmpty) return '';
    if (_instructorPhotoResultCache.containsKey(instructorNameFromSis)) {
      return _instructorPhotoResultCache[instructorNameFromSis]!;
    }
    final local = await _readLocalInstructorPhoto(instructorNameFromSis);
    if (local.isNotEmpty) {
      _instructorPhotoResultCache[instructorNameFromSis] = local;
      return local;
    }

    String bestMatchSrc = '';
    double bestScore = 0;

    for (final pageUrl in collegePageUrls) {
      final list = await _fetchFacultyPhotoList(pageUrl);
      for (final entry in list) {
        final score = _nameSimilarity(instructorNameFromSis, entry.$1);
        if (score > bestScore) {
          bestScore = score;
          bestMatchSrc = entry.$2;
        }
      }
    }

    DebugLog.log(
      'INSTRUCTOR PHOTO MATCH: "$instructorNameFromSis" -> score=${bestScore.toStringAsFixed(2)} found=${bestMatchSrc.isNotEmpty}',
    );
    return bestScore >= threshold ? bestMatchSrc : '';
  }

  static String _jsEscape(String input) {
    return input.replaceAll('\\', '\\\\').replaceAll('"', '\\"').replaceAll("'", "\\'").replaceAll('\n', '\\n');
  }

  static String _decodeJsString(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is String) return decoded;
    } catch (_) {}

    if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
      raw = raw.substring(1, raw.length - 1);
    }
    return raw
        .replaceAllMapped(RegExp(r'\\u([0-9a-fA-F]{4})'),
            (m) => String.fromCharCode(int.parse(m.group(1)!, radix: 16)))
        .replaceAll('\\n', '\n')
        .replaceAll('\\t', '\t')
        .replaceAll('\\"', '"')
        .replaceAll('\\/', '/')
        .replaceAll('\\\\', '\\');
  }
}