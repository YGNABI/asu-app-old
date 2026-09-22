import 'dart:async';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:html/parser.dart' as html_parser;
import '../ui/debug/debug_log.dart';

class SosRequestModel {
  final String id;
  final String category;
  final String date;
  final String details;
  final String remarks;
  final String status;

  SosRequestModel({
    required this.id,
    required this.category,
    required this.date,
    required this.details,
    required this.remarks,
    required this.status,
  });
}

// نموذج جديد لتمثيل كل خدمة على حدة كما هي في النظام الفعلي (طلب، اقتراح، شكوى، الخ)
class SosServiceModel {
  final String title;
  final String url;
  SosServiceModel(this.title, this.url);
}

class SosService {
  static const String baseUrl = 'https://sos.asu.edu.bh/ords/r/asudss/sos';
  static const String loginUrl = '$baseUrl/login_desktop';
  
  static final WebViewController webViewController = WebViewController()
    ..setJavaScriptMode(JavaScriptMode.unrestricted);

  static bool isAuthenticated = false;
  static String sessionId = '';
  static const _secureStorage = FlutterSecureStorage();
  
  // الكاش الداخلي للبيانات لضمان العرض الفوري
  static List<SosRequestModel> currentRequests = [];
  static List<SosRequestModel> closedRequests = [];
  static List<SosServiceModel> availableServices = []; // قائمة الخدمات الفعلية المنفصلة
  static bool dataFetched = false;

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

  /// 1. تسجيل الدخول الصامت والمبكر
  static Future<bool> ensureAuthenticated() {
    return _serialized(() => _loginInternal());
  }

  static Future<bool> _loginInternal() async {
    if (isAuthenticated && sessionId.isNotEmpty) return true;

    final savedUser = await _secureStorage.read(key: 'saved_username');
    final savedPass = await _secureStorage.read(key: 'saved_password');
    if (savedUser == null || savedPass == null) return false;

    final done = Completer<void>();
    webViewController.setNavigationDelegate(NavigationDelegate(
      onPageFinished: (url) {
        if (!done.isCompleted) done.complete();
      },
    ));

    await webViewController.loadRequest(Uri.parse(loginUrl));
    try { await done.future.timeout(const Duration(seconds: 15)); } catch (_) {}

    final escapedUser = _jsEscape(savedUser);
    final escapedPass = _jsEscape(savedPass);

    await webViewController.runJavaScript('''
      (function() {
        var u = document.querySelector('input[type="text"]');
        var p = document.querySelector('input[type="password"]');
        if(u) u.value = "$escapedUser";
        if(p) p.value = "$escapedPass";
        
        var loginBtn = document.querySelector('button.t-Button[type="button"]');
        if (window.apex && apex.submit) {
          try { apex.submit('LOGIN'); } catch(e) {}
        }
        if (loginBtn) {
          loginBtn.click();
        } else {
          var form = document.getElementById('wwvFlowForm') || document.forms[0];
          if (form) { form.submit(); }
        }
      })();
    ''');

    await Future.delayed(const Duration(seconds: 5));
    final url = await webViewController.currentUrl() ?? '';
    _extractSessionId(url);

    isAuthenticated = !url.contains('login');
    return isAuthenticated;
  }

  /// 2. جلب البيانات المسبق (Prefetch)
  static Future<void> prefetchData({bool force = false}) async {
    // لا يعيد التحميل نهائياً عند التنقل بين التبويبات، التحميل يتم فقط عند السحب للتحديث يدوياً (force = true)
    if (!force && dataFetched && (availableServices.isNotEmpty || currentRequests.isNotEmpty)) {
      return; 
    }

    if (!isAuthenticated) await ensureAuthenticated();
    if (!isAuthenticated) return;

    try {
      await _fetchDashboardAndRequests();
      dataFetched = true;
      DebugLog.log('SOS: Pre-fetched ${availableServices.length} services, ${currentRequests.length} current, ${closedRequests.length} closed');
    } catch (e) {
      DebugLog.log('SOS Prefetch Error: $e');
    }
  }

  /// 3. استخراج الخدمات والطلبات بذكاء من النظام الفعلي (مع كود التشخيص)
  static Future<void> _fetchDashboardAndRequests() async {
    final url = sessionId.isNotEmpty ? '$baseUrl/home?session=$sessionId' : baseUrl;
    var done = Completer<void>();

    webViewController.setNavigationDelegate(NavigationDelegate(
      onPageFinished: (u) {
        _extractSessionId(u);
        if (!done.isCompleted) done.complete();
      },
    ));

    await webViewController.loadRequest(Uri.parse(url));
    try { await done.future.timeout(const Duration(seconds: 15)); } catch (_) {}
    await Future.delayed(const Duration(seconds: 3));

    final html = await webViewController.runJavaScriptReturningResult('document.documentElement.outerHTML') as String;
    final decodedHtml = _decodeJsString(html);

    // طباعة الـ HTML الخاص بـ SOS لمعرفة سبب عدم قراءة البيانات
    DebugLog.log('SOS HTML PREVIEW: ${decodedHtml.length > 500 ? decodedHtml.substring(0, 500) : decodedHtml}');
    if (decodedHtml.contains('login') || decodedHtml.contains('P101_USERNAME')) {
       DebugLog.log('SOS ERROR: التطبيق لا يزال عالقاً في صفحة تسجيل الدخول الخاصة بـ SOS');
    }

    final doc = html_parser.parse(decodedHtml);

    List<SosServiceModel> services = [];
    final items = doc.querySelectorAll('.t-MediaList-item a, .t-Card-wrap a, .t-Button');
    for (final a in items) {
      final title = a.text.replaceAll(RegExp(r'\s+'), ' ').trim();
      final href = a.attributes['href'] ?? '';
      if (title.isNotEmpty && href.isNotEmpty && !href.contains('login') && !href.contains('logout')) {
        if (!services.any((s) => s.title == title)) services.add(SosServiceModel(title, href));
      }
    }
    if (services.isNotEmpty) availableServices = services;

    List<SosRequestModel> current = [];
    List<SosRequestModel> closed = [];
    final tables = doc.querySelectorAll('table.a-IRR-table, table.t-Report-report, table');

    for (final table in tables) {
      for (final row in table.querySelectorAll('tbody tr')) {
        final cells = row.querySelectorAll('td');
        if (cells.length >= 4) {
           final textCells = cells.map((c) => c.text.trim()).toList();
           final a = cells[0].querySelector('a');
           final href = a?.attributes['href'] ?? '';
           final match = RegExp(r'id=(\d+)').firstMatch(href);
           final id = match != null ? match.group(1)! : textCells[0];

           final req = SosRequestModel(
             id: id,
             category: textCells.length > 1 ? textCells[1] : '',
             date: textCells.length > 2 ? textCells[2] : '',
             details: textCells.length > 3 ? textCells[3] : '',
             remarks: textCells.length > 4 ? textCells[4] : '',
             status: textCells.length > 5 ? textCells[5] : textCells.last,
           );

           if (req.status.contains('مغلق') || req.status.contains('Closed') || req.status.contains('منتهي') || req.status.contains('مكتمل')) {
             closed.add(req);
           } else if (req.category.isNotEmpty && !req.category.contains('لا توجد')) {
             current.add(req);
           }
        }
      }
    }

    currentRequests = current;
    closedRequests = closed;
  }

  /// 5. سحب/إلغاء الطلب
  static Future<bool> withdrawRequest(String requestId, String reason) {
    return _serialized(() => _withdrawInternal(requestId, reason));
  }

  static Future<bool> _withdrawInternal(String requestId, String reason) async {
    if (!isAuthenticated) await _loginInternal();
    final url = '$baseUrl/56?p56_new=$requestId&session=$sessionId';
    var done = Completer<void>();
    webViewController.setNavigationDelegate(NavigationDelegate(
      onPageFinished: (_) { if (!done.isCompleted) done.complete(); },
    ));
    await webViewController.loadRequest(Uri.parse(url));
    try { await done.future.timeout(const Duration(seconds: 15)); } catch (_) {}
    await Future.delayed(const Duration(seconds: 2));
    final escapedReason = _jsEscape(reason);
    done = Completer<void>();
    await webViewController.runJavaScript('''
      var q = document.getElementById('P56_QUIT');
      var btn = document.getElementById('B749581403692666574');
      if(q) q.value = "$escapedReason";
      if(btn) btn.click();
    ''');
    try { await done.future.timeout(const Duration(seconds: 10)); } catch (_) {}
    await Future.delayed(const Duration(seconds: 3));
    await prefetchData(force: true);
    return true;
  }

  static void _extractSessionId(String url) {
    final match = RegExp(r'session=(\d+)').firstMatch(url);
    if (match != null && match.group(1)!.isNotEmpty) sessionId = match.group(1)!;
  }

  static String _jsEscape(String input) {
    return input.replaceAll('\\', '\\\\').replaceAll('"', '\\"').replaceAll("'", "\\'").replaceAll('\n', '\\n');
  }

  static String _decodeJsString(String raw) {
    if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
      raw = raw.substring(1, raw.length - 1);
    }
    return raw.replaceAllMapped(RegExp(r'\\u([0-9a-fA-F]{4})'), 
            (m) => String.fromCharCode(int.parse(m.group(1)!, radix: 16)))
        .replaceAll('\\n', '\n').replaceAll('\\t', '\t').replaceAll('\\"', '"').replaceAll('\\/', '/').replaceAll('\\\\', '\\');
  }
}