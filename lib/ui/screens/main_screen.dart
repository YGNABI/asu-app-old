import 'dart:async';
import 'package:flutter/material.dart';
import 'package:sensors_plus/sensors_plus.dart';
import '../../models/student_model.dart';
import '../../models/course_model.dart';
import 'sis_api_service.dart';
import '../../core/cache_service.dart';
import '../../core/theme_service.dart';
import '../widgets/student_dashboard_card.dart';
import 'calendar_tab.dart';
import '../widgets/course_list_item.dart';
import '../debug/debug_log.dart';
import 'app_lang.dart';
import '../../main.dart';
import '../../core/notification_service.dart';
import '../../core/moodle_service.dart';
import 'login_screen.dart';
import 'services_tab.dart';
import '../../core/sos_service.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/schedule_utils.dart';
import '../widgets/liquid_loading.dart';

const String _kAutoReconnectKey = 'auto_refresh_on_reconnect';

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabController;
  final LayerLink _settingsLink = LayerLink();
  OverlayEntry? _settingsOverlay;

  StudentInfo? _student;
  List<CourseModel> _courses = [];
  bool _homeLoading = true;

  List<PlanSection> _planSections = [];
  bool _planLoading = false;
  bool _planFetched = false;

  List<TermGrades> _terms = [];
  bool _gradesLoading = false;
  bool _gradesFetched = false;

  double _balance = 0;
  List<AccountTransaction> _transactions = [];
  List<InstallmentItem> _installments = []; 
  bool _accountLoading = false;
  bool _accountFetched = false;

  double _sensorX = 0.0;
  StreamSubscription<GyroscopeEvent>? _gyroscopeSubscription;
  Timer? _autoRefreshTimer;
  StreamSubscription<List<ConnectivityResult>>? _connectivitySubscription;
  bool _isOffline = false;
  DateTime? _lastUpdateTime;
  Timer? _lectureCheckTimer;
  final List<CourseModel> _lectureAlerts = [];
  final Set<String> _shownAlertKeys = {};

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 6, vsync: this, initialIndex: 3);
    _tabController.addListener(_onTabChanged);
    _loadFromCache();
    _loadLastUpdateTime();
    Connectivity().checkConnectivity().then((r) {
      if (mounted) setState(() => _isOffline = r.contains(ConnectivityResult.none));
    });
    _connectivitySubscription = Connectivity().onConnectivityChanged.listen((results) {
      final offlineNow = results.contains(ConnectivityResult.none);
      final wasOffline = _isOffline;
      if (mounted) setState(() => _isOffline = offlineNow);
      if (wasOffline && !offlineNow) {
        _onReconnected();
      }
    });
    NotificationService.init().then((_) => NotificationService.requestPermissions());
    _lectureCheckTimer = Timer.periodic(const Duration(minutes: 1), (_) => _checkLectureAlerts());
    _checkLectureAlerts();

    // تفعيل الجايروسكوب للخلفية في الشاشة الرئيسية - حركة قوية وواضحة
    _gyroscopeSubscription = gyroscopeEventStream().listen((GyroscopeEvent event) {
      if (mounted) {
        final delta = (event.y * 0.004).clamp(-0.06, 0.06);
        setState(() {
          _sensorX += delta;
          _sensorX = _sensorX.clamp(-0.35, 0.35);
        });
      }
    });
  }

  Future<void> _loadLastUpdateTime() async {
    final prefs = await SharedPreferences.getInstance();
    final ms = prefs.getInt('last_update_time');
    if (ms != null && mounted) setState(() => _lastUpdateTime = DateTime.fromMillisecondsSinceEpoch(ms));
  }

  Future<void> _saveLastUpdateTime() async {
    final now = DateTime.now();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('last_update_time', now.millisecondsSinceEpoch);
    if (mounted) setState(() => _lastUpdateTime = now);
  }

  String _formatLastUpdate() {
    if (_lastUpdateTime == null) return '';
    final t = _lastUpdateTime!;
    final h = t.hour.toString().padLeft(2, '0');
    final m = t.minute.toString().padLeft(2, '0');
    return '${t.day}/${t.month} - $h:$m';
  }

  void _startAutoRefreshIfEnabled() async {
    final enabled = await ThemeService.autoRefreshEnabled();
    _autoRefreshTimer?.cancel();
    if (enabled) {
      _autoRefreshTimer = Timer.periodic(const Duration(hours: 3), (_) {
        _fetchHomeData();
      });
    }
  }

  // يُستدعى فقط عند رجوع الاتصال بعد انقطاع - يحدّث البيانات المفتوحة سابقاً إذا كان المستخدم مفعّل هذا الخيار
  Future<void> _onReconnected() async {
    final prefs = await SharedPreferences.getInstance();
    final autoOnReconnect = prefs.getBool(_kAutoReconnectKey) ?? true;
    if (!autoOnReconnect) return;
    _fetchHomeData();
    if (_planFetched) {
      _planFetched = false;
      _fetchPlanData();
    }
    if (_gradesFetched) {
      _gradesFetched = false;
      _fetchGradesData();
    }
    if (_accountFetched) {
      _accountFetched = false;
      _fetchAccountData();
    }
  }

  Future<void> _logout() async {
    // 1. مسح جميع بيانات الدخول الآمنة (بدلاً من مسح مفاتيح محددة فقط)
    const secureStorage = FlutterSecureStorage();
    await secureStorage.deleteAll();

    // 2. مسح الكاش والبيانات المخزنة (الجدول، الدرجات، الخطة) للطالب القديم
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();

    // 3. مسح كوكيز وجلسات الويب فييو لضمان عدم تداخل الحسابات
    try {
      await SisApiService.webViewController.clearCache();
      await SisApiService.webViewController.clearLocalStorage();
      await SosService.webViewController.clearCache();
    } catch (_) {}

    // تنبيه: هذا الكود لا يمسح المجلدات المحلية، وبالتالي جميع الملفات المحملة 
    // (PDF، Word، إلخ) ستبقى آمنة ولن يتم حذفها.

    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (route) => false,
    );
  }

  void _closeSettingsMenu() {
    _settingsOverlay?.remove();
    _settingsOverlay = null;
  }

  void _toggleSettingsMenu(BuildContext context) {
    if (_settingsOverlay != null) {
      _closeSettingsMenu();
      return;
    }

    final gender = (_student?.gender ?? '').trim();
    final isFemale = gender.contains('أنثى') || gender.contains('انثى') || gender.toUpperCase().contains('FEMALE');

    _settingsOverlay = OverlayEntry(
      builder: (overlayContext) {
        return Stack(
          children: [
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.translucent,
                onTap: _closeSettingsMenu,
              ),
            ),
CompositedTransformFollower(
  link: _settingsLink,
  showWhenUnlinked: false,
  targetAnchor: Alignment.bottomLeft,
  followerAnchor: Alignment.topLeft,
  offset: const Offset(0, 8),
              child: Material(
                elevation: 8,
                borderRadius: BorderRadius.circular(14),
                color: Theme.of(context).cardColor,
                child: _SettingsPanelContent(isFemale: isFemale, onAutoRefreshChanged: _startAutoRefreshIfEnabled, onLogout: _logout),
              ),
            ),
          ],
        );
      },
    );

    Overlay.of(context).insert(_settingsOverlay!);
  }

  void _checkLectureAlerts() {
    final now = DateTime.now();
    for (final course in _courses) {
      final weekdays = ScheduleUtils.parseWeekdays(course.days);
      final time = ScheduleUtils.parseArabicTime(course.startTime);
      if (time == null || !weekdays.contains(now.weekday)) continue;
      final lectureDt = DateTime(now.year, now.month, now.day, time.hour, time.minute);
      final diff = lectureDt.difference(now);
      final key = '${course.code}_${now.year}${now.month}${now.day}';
      if (diff.inMinutes <= 15 && diff.inMinutes >= 0 && !_shownAlertKeys.contains(key)) {
        _shownAlertKeys.add(key);
        if (mounted) setState(() => _lectureAlerts.add(course));
      }
    }
  }

  void _dismissLectureAlert(CourseModel course) {
    setState(() => _lectureAlerts.remove(course));
  }

  @override
  void dispose() {
    _gyroscopeSubscription?.cancel();
    _autoRefreshTimer?.cancel();
    _connectivitySubscription?.cancel();
    _lectureCheckTimer?.cancel();
    _settingsOverlay?.remove();
    _tabController.removeListener(_onTabChanged);
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadFromCache() async {
    final home = await CacheService.loadHomeData();
    final plan = await CacheService.loadPlanData();
    final grades = await CacheService.loadGradesData();
    final account = await CacheService.loadAccountData();

    if (mounted) {
      setState(() {
        if (home != null) {
          _student = home.$1;
          _courses = home.$2;
          _homeLoading = false;
        }
        if (plan.isNotEmpty) {
          _planSections = plan;
          _planFetched = true;
        }
        if (grades.isNotEmpty) {
          _terms = grades;
          _gradesFetched = true;
        }
        if (account != null) {
          _balance = account.$1;
          _transactions = account.$2;
          _installments = account.$3;
          _accountFetched = true;
        }
      });
    }

    if (_student == null) {
      _fetchHomeData();
    } else {
      ThemeService.applyGenderDefault(_student!.gender);
      MoodleService.setSisCourses(_courses);
      MoodleService.ensureMatchCacheBuilt();
      SisApiService.ensureAuthenticated();
    }
    _startAutoRefreshIfEnabled();
  }

  void _onTabChanged() {
    if (_tabController.indexIsChanging) return;
    
    if (_tabController.index == 1 && !_gradesFetched && !_gradesLoading) {
      _fetchGradesData();
    } else if (_tabController.index == 2 && !_planFetched && !_planLoading) {
      _fetchPlanData();
    } else if (_tabController.index == 4 && !_accountFetched && !_accountLoading) {
      _fetchAccountData();
    }
  }

  Future<void> _fetchHomeData() async {
    if (_isOffline) {
      if (mounted) {
        setState(() => _homeLoading = false);
        if (_student != null) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(AppLang.tr('لا يوجد اتصال بالإنترنت، البيانات المعروضة محفوظة مسبقاً'))),
          );
        }
      }
      return;
    }
    setState(() => _homeLoading = true);
    try {
      final authOk = await SisApiService.ensureAuthenticated();
      if (!authOk) {
        DebugLog.log('HOME: could not authenticate, keeping existing cached data');
        if (mounted) {
          setState(() => _homeLoading = false);
          if (_student != null) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(AppLang.tr('تعذر تحديث الجلسة، البيانات المعروضة محفوظة مسبقاً'))),
            );
          }
        }
        return;
      }
      final student = await SisApiService.fetchStudentInfo();
      final courses = await SisApiService.fetchHomeCourses();
      if (student.studentId.trim().isEmpty) {
        DebugLog.log('HOME: fetched empty/invalid student data, discarding');
        if (mounted) setState(() => _homeLoading = false);
        return;
      }
      MoodleService.setSisCourses(courses);
      // نبني كاش مطابقة Moodle مرة واحدة بالخلفية (لا ننتظره) حتى تُفتح الـpopup فوراً لاحقاً
      MoodleService.ensureMatchCacheBuilt();
      SisApiService.prewarmInstructorPhotos(courses);
      SosService.ensureAuthenticated().then((success) {
        if (success) SosService.prefetchData();
      });

      await CacheService.saveHomeData(student, courses);
      _saveLastUpdateTime();
      ThemeService.applyGenderDefault(student.gender);
      NotificationService.cancelAll().then((_) {
        NotificationService.scheduleExamReminders(courses);
        NotificationService.scheduleLectureReminders(courses);
        NotificationService.scheduleInstallmentReminders(academicCalendar);
      });
      if (mounted) {
        setState(() {
          _student = student;
          _courses = courses;
          _homeLoading = false;
        });
      }
      
      if (!_planFetched && !_planLoading) _fetchPlanData();
      if (!_gradesFetched && !_gradesLoading) _fetchGradesData();
      if (!_accountFetched && !_accountLoading) _fetchAccountData();
      
      // التحميل المسبق الصامت لباقي التبويبات لتسريع التطبيق
      if (!_planFetched && !_planLoading) _fetchPlanData();
      if (!_gradesFetched && !_gradesLoading) _fetchGradesData();
      if (!_accountFetched && !_accountLoading) _fetchAccountData();
    } catch (e, st) {
      DebugLog.log('HOME ERROR: $e\n$st');
      if (mounted) setState(() => _homeLoading = false);
    }
  }

  Future<void> _fetchPlanData() async {
    if (_isOffline) {
      if (mounted) {
        setState(() => _planLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLang.tr('لا يوجد اتصال بالإنترنت'))),
        );
      }
      return;
    }
    setState(() => _planLoading = true);
    try {
      if (!await SisApiService.ensureAuthenticated()) {
        if (mounted) setState(() => _planLoading = false);
        return;
      }
      final sections = await SisApiService.fetchPlanSections();
      await CacheService.savePlanData(sections);
      if (mounted) {
        setState(() {
          _planSections = sections;
          _planLoading = false;
          _planFetched = true;
        });
      }
    } catch (e, st) {
      DebugLog.log('PLAN ERROR: $e\n$st');
      if (mounted) setState(() => _planLoading = false);
    }
  }

  Future<void> _fetchGradesData() async {
    if (_isOffline) {
      if (mounted) {
        setState(() => _gradesLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLang.tr('لا يوجد اتصال بالإنترنت'))),
        );
      }
      return;
    }
    setState(() => _gradesLoading = true);
    try {
      if (!await SisApiService.ensureAuthenticated()) {
        if (mounted) setState(() => _gradesLoading = false);
        return;
      }
      final terms = await SisApiService.fetchAllTermsGrades();
      await CacheService.saveGradesData(terms);
      if (mounted) {
        setState(() {
          _terms = terms;
          _gradesLoading = false;
          _gradesFetched = true;
        });
      }
    } catch (e, st) {
      DebugLog.log('GRADES ERROR: $e\n$st');
      if (mounted) setState(() => _gradesLoading = false);
    }
  }

  Future<void> _fetchAccountData() async {
    if (_isOffline) {
      if (mounted) {
        setState(() => _accountLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLang.tr('لا يوجد اتصال بالإنترنت'))),
        );
      }
      return;
    }
    setState(() => _accountLoading = true);
    try {
      if (!await SisApiService.ensureAuthenticated()) {
        if (mounted) setState(() => _accountLoading = false);
        return;
      }
      final balance = await SisApiService.fetchAccountBalance();
      final transactions = await SisApiService.fetchAllAccountTransactions();
      final installments = await SisApiService.fetchInstallments(); 
      if (mounted) {
        setState(() {
          _balance = balance;
          _transactions = transactions;
          _installments = installments;
          _accountLoading = false;
          _accountFetched = true;
        });
      }
    } catch (e, st) {
      DebugLog.log('ACCOUNT ERROR: $e\n$st');
      if (mounted) setState(() => _accountLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final statusBarHeight = MediaQuery.of(context).padding.top;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        children: [
          Positioned(
            left: -2000,
            top: -2000,
            width: 1,
            height: 1,
            child: SizedBox(
              width: 1,
              height: 1,
              child: WebViewWidget(controller: SosService.webViewController),
            ),
          ),
          // خلفية بانورامية تغطي الشاشة كاملة
          AnimatedBuilder(
            animation: _tabController.animation!,
            builder: (context, child) {
              final tabValue = _tabController.animation!.value;
              const homeIndex = 3.0;
              const tabCount = 6.0;
              // تتمركز الصورة تماماً عند تبويب الرئيسية، وتنزاح تدريجياً كل ما ابتعدنا عنه
              final alignmentX = (((homeIndex - tabValue) / (tabCount - 1)) * 2).clamp(-1.0, 1.0) + _sensorX;
              return Positioned.fill(
                child: Image.asset(
                  'assets/images/asu_login_footer.jpg',
                  fit: BoxFit.cover,
                  alignment: Alignment(alignmentX.clamp(-1.0, 1.0), 0.0),
                ),
              );
            },
          ),
          ValueListenableBuilder<double>(
            valueListenable: ThemeService.fieldOpacity,
            builder: (context, opacity, _) => Positioned.fill(
              child: Container(
                color: Theme.of(context).scaffoldBackgroundColor.withValues(alpha: opacity < 0.15 ? 0.0 : 0.35),
              ),
            ),
          ),
          Column(
            children: [
              SizedBox(height: statusBarHeight),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    if (_isOffline)
                      Container(
                        margin: const EdgeInsets.only(right: 4),
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.6),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.wifi_off, size: 16, color: Colors.white),
                            if (_lastUpdateTime != null) ...[
                              const SizedBox(width: 6),
                              Text(_formatLastUpdate(), style: const TextStyle(color: Colors.white, fontSize: 11)),
                            ],
                          ],
                        ),
                      )
                    else
                      const SizedBox.shrink(),
                    CompositedTransformTarget(
                      link: _settingsLink,
                      child: IconButton(
                        icon: const Icon(Icons.settings_outlined),
                        onPressed: () => _toggleSettingsMenu(context),
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                margin: const EdgeInsets.symmetric(horizontal: 4),
                decoration: BoxDecoration(
                  color: Theme.of(context).cardColor.withValues(alpha: 0.75),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: TabBar(
                  controller: _tabController,
                  isScrollable: true,
                  labelColor: Theme.of(context).textTheme.bodyLarge?.color,
                  unselectedLabelColor: Colors.grey,
                  indicatorColor: Theme.of(context).colorScheme.primary,
                  indicatorWeight: 3,
                  tabs: [
                    Tab(
                      icon: Image.asset(
                        'assets/images/calendar_logo.png',
                        height: 44,
                        width: 44,
                        errorBuilder: (ctx, err, stack) => const Icon(Icons.calendar_month_outlined, size: 44),
                      ),
                    ),
                    Tab(text: AppLang.tr('كشف الدرجات')), 
                    Tab(text: AppLang.tr('الخطة الدراسية')), 
                    Tab(text: AppLang.tr('الرئيسية')), 
                    Tab(text: AppLang.tr('كشف الحساب')), 
                    Tab(text: AppLang.tr('نظام الخدمات الطلابية')), 
                  ],
                ),
              ),
              Expanded(
                child: TabBarView(
                  controller: _tabController,
                  children: [
                    const CalendarTab(),
                    _GradesTab(
                      student: _student, 
                      terms: _terms,
                      isLoading: _gradesLoading,
                      isOffline: _isOffline,
                      onRefresh: () async {
                        _gradesFetched = false;
                        await _fetchGradesData();
                      },
                    ),
                    _PlanTab(
                      sections: _planSections,
                      registeredCourses: _courses, 
                      student: _student,
                      isLoading: _planLoading,
                      isOffline: _isOffline,
                      onRefresh: () async {
                        _planFetched = false;
                        await _fetchPlanData();
                      },
                    ),
                    _HomeTab(
                      student: _student,
                      courses: _courses,
                      terms: _terms,
                      isLoading: _homeLoading,
                      isOffline: _isOffline,
                      onRefresh: _fetchHomeData,
                      onCalendarTap: () => _tabController.animateTo(0),
                    ),
                    _AccountTab(
                      balance: _balance,
                      transactions: _transactions,
                      installments: _installments,
                      isLoading: _accountLoading,
                      isOffline: _isOffline,
                      onRefresh: () async {
                        _accountFetched = false;
                        await _fetchAccountData();
                      },
                    ),
                    const ServicesTab(),
                  ],
                ),
              ),
            ],
          ),
          if (_lectureAlerts.isNotEmpty)
            AnimatedBuilder(
              animation: _tabController.animation!,
              builder: (context, child) {
                if ((_tabController.animation!.value - 3.0).abs() > 0.5) return const SizedBox.shrink();
                return Positioned(
                  top: MediaQuery.of(context).size.height * 0.3,
                  left: 0,
                  right: 0,
                  child: Column(
                    children: _lectureAlerts
                        .map((c) => Padding(
                              padding: const EdgeInsets.only(bottom: 12),
                              child: _LectureAlertBanner(
                                course: c,
                                onDismiss: () => _dismissLectureAlert(c),
                              ),
                            ))
                        .toList(),
                  ),
                );
              },
            ),
        ],
      ),
    );
  }
}

class _LectureAlertBanner extends StatefulWidget {
  final CourseModel course;
  final VoidCallback onDismiss;

  const _LectureAlertBanner({required this.course, required this.onDismiss});

  @override
  State<_LectureAlertBanner> createState() => _LectureAlertBannerState();
}

class _LectureAlertBannerState extends State<_LectureAlertBanner> {
  double _dragDx = 0;

  @override
  Widget build(BuildContext context) {
    return Transform.translate(
      offset: Offset(_dragDx, 0),
      child: Opacity(
        opacity: (1 - (_dragDx.abs() / 200)).clamp(0.0, 1.0),
        child: GestureDetector(
          onHorizontalDragUpdate: (d) => setState(() => _dragDx += d.delta.dx),
          onHorizontalDragEnd: (d) {
            if (_dragDx.abs() > 90) {
              widget.onDismiss();
            } else {
              setState(() => _dragDx = 0);
            }
          },
          child: Container(
            margin: const EdgeInsets.symmetric(horizontal: 24),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: const LinearGradient(colors: [Color(0xFFFFD700), Color(0xFFB8860B)]),
              borderRadius: BorderRadius.circular(16),
              boxShadow: [BoxShadow(color: Colors.amber.withValues(alpha: 0.6), blurRadius: 20, spreadRadius: 2)],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.access_time_filled, color: Colors.black87, size: 28),
                const SizedBox(height: 8),
                Text(
                  widget.course.nameAr.isNotEmpty ? widget.course.nameAr : widget.course.nameEn,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.black87),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 4),
                Text(
                  'قاعة ${widget.course.hall} • ${widget.course.instructor}',
                  // تم تكبير الخط هنا من 12 إلى 15
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: Colors.black87),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SettingsPanelContent extends StatefulWidget {
  final bool isFemale;
  final VoidCallback onAutoRefreshChanged;
  final VoidCallback onLogout;

  const _SettingsPanelContent({required this.isFemale, required this.onAutoRefreshChanged, required this.onLogout});

  @override
  State<_SettingsPanelContent> createState() => _SettingsPanelContentState();
}

class _SettingsPanelContentState extends State<_SettingsPanelContent> {
  bool? _autoRefresh;
  bool? _autoRefreshOnReconnect;

  @override
  void initState() {
    super.initState();
    ThemeService.autoRefreshEnabled().then((v) {
      if (mounted) setState(() => _autoRefresh = v);
    });
    SharedPreferences.getInstance().then((prefs) {
      if (mounted) setState(() => _autoRefreshOnReconnect = prefs.getBool(_kAutoReconnectKey) ?? true);
    });
  }

  Widget _langChip(BuildContext context, String label, String code, Locale current) {
    final active = current.languageCode == code;
    return GestureDetector(
      onTap: () => appLanguage.value = Locale(code),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: active ? Theme.of(context).colorScheme.primary : Colors.transparent,
          border: Border.all(color: Theme.of(context).dividerColor),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(label, style: TextStyle(color: active ? Colors.white : null, fontSize: 12, fontWeight: FontWeight.w600)),
      ),
    );
  }

  Widget _themeChip(BuildContext context, String label, AppThemeChoice value, AppThemeChoice current) {
    final active = current == value;
    return GestureDetector(
      onTap: () => ThemeService.setChoice(value),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: active ? Theme.of(context).colorScheme.primary : Colors.transparent,
          border: Border.all(color: Theme.of(context).dividerColor),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(label, style: TextStyle(color: active ? Colors.white : null, fontSize: 12, fontWeight: FontWeight.w600)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 260,
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(AppLang.tr('اللغة'), style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
          const SizedBox(height: 6),
          ValueListenableBuilder<Locale>(
            valueListenable: appLanguage,
            builder: (context, locale, _) => Row(
              children: [
                Expanded(child: _langChip(context, 'العربية', 'ar', locale)),
                const SizedBox(width: 6),
                Expanded(child: _langChip(context, 'English', 'en', locale)),
              ],
            ),
          ),
          const SizedBox(height: 14),

          Text(AppLang.tr('الثيم'), style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
          const SizedBox(height: 6),
          ValueListenableBuilder<AppThemeChoice>(
            valueListenable: ThemeService.choice,
            builder: (context, current, _) => Row(
              children: [
                Expanded(child: _themeChip(context, AppLang.tr('فاتح'), AppThemeChoice.light, current)),
                const SizedBox(width: 6),
                Expanded(child: _themeChip(context, AppLang.tr('داكن'), AppThemeChoice.dark, current)),
                if (widget.isFemale) ...[
                  const SizedBox(width: 6),
                  Expanded(child: _themeChip(context, AppLang.tr('وردي'), AppThemeChoice.pink, current)),
                ],
              ],
            ),
          ),
          const SizedBox(height: 14),

          Text(AppLang.tr('شفافية الحقول'), style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
          ValueListenableBuilder<double>(
            valueListenable: ThemeService.fieldOpacity,
            builder: (context, value, _) => Slider(
              value: value,
              min: 0.3,
              max: 1.0,
              onChanged: (v) => ThemeService.setFieldOpacity(v),
            ),
          ),
          const SizedBox(height: 6),

          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(child: Text(AppLang.tr('تحديث كل 3 ساعات'), style: const TextStyle(fontSize: 12))),
              Switch(
                value: _autoRefresh ?? true,
                onChanged: (v) async {
                  await ThemeService.setAutoRefreshEnabled(v);
                  widget.onAutoRefreshChanged();
                  setState(() => _autoRefresh = v);
                },
              ),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(child: Text(AppLang.tr('تحديث تلقائي عند عودة الإنترنت'), style: const TextStyle(fontSize: 12))),
              Switch(
                value: _autoRefreshOnReconnect ?? true,
                onChanged: (v) async {
                  final prefs = await SharedPreferences.getInstance();
                  await prefs.setBool(_kAutoReconnectKey, v);
                  setState(() => _autoRefreshOnReconnect = v);
                },
              ),
            ],
          ),
          const SizedBox(height: 10),
          TextButton.icon(
            onPressed: widget.onLogout,
            icon: const Icon(Icons.logout, size: 18, color: Colors.redAccent),
            label: Text(AppLang.tr('تسجيل الدخول من جديد'), style: const TextStyle(color: Colors.redAccent, fontSize: 12)),
          ),
        ],
      ),
    );
  }
}

Color _fieldColor(BuildContext context, double opacity) => Theme.of(context).cardColor.withValues(alpha: opacity);

TextStyle _adaptiveText(BuildContext context, double opacity, {double size = 13, FontWeight weight = FontWeight.normal, bool secondary = false}) {
  final low = opacity < 0.55;
  final color = low
      ? (secondary ? Colors.white70 : Colors.white)
      : (secondary ? Colors.grey : Theme.of(context).textTheme.bodyLarge?.color);
  return TextStyle(
    fontSize: size,
    fontWeight: weight,
    color: color,
    shadows: low ? const [Shadow(color: Colors.black87, blurRadius: 6)] : null,
  );
}

Widget _offlineNotice(BuildContext context, double opacity) {
  return Container(
    margin: const EdgeInsets.only(bottom: 12),
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: Colors.orange.withValues(alpha: 0.15),
      borderRadius: BorderRadius.circular(10),
      border: Border.all(color: Colors.orange.withValues(alpha: 0.4)),
    ),
    child: Row(
      children: [
        const Icon(Icons.wifi_off, color: Colors.orange, size: 20),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            AppLang.tr('لا يوجد اتصال بالإنترنت، اسحب للأسفل للتحديث عند توفر الاتصال'),
            style: _adaptiveText(context, opacity, size: 12),
          ),
        ),
      ],
    ),
  );
}



void _showGpaChart(BuildContext context, List<TermGrades> terms) {
  if (terms.isEmpty) return;
  showDialog(
    context: context,
    builder: (context) => Directionality(
      textDirection: TextDirection.rtl,
      child: Dialog(
        insetPadding: const EdgeInsets.all(12),
        child: SizedBox(
          width: double.maxFinite,
          height: MediaQuery.of(context).size.height * 0.75,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(12),
                child: Text(AppLang.tr('تطور المعدل التراكمي'), style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              ),
              Expanded(
                child: InteractiveViewer(
                  minScale: 1,
                  maxScale: 4,
                  boundaryMargin: const EdgeInsets.all(40),
                  child: SizedBox(
                    width: (terms.length * 90.0).clamp(320.0, 5000.0),
                    height: double.infinity,
                    child: CustomPaint(
                      painter: _GpaChartPainter(terms: terms),
                    ),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(8),
                child: TextButton(onPressed: () => Navigator.pop(context), child: Text(AppLang.tr('إغلاق'))),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _GpaChartPainter extends CustomPainter {
  final List<TermGrades> terms;
  _GpaChartPainter({required this.terms});

  String _extractYear(String label) {
    final match = RegExp(r'(20\d{2})').firstMatch(label);
    return match?.group(1) ?? '';
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (terms.isEmpty) return;
    const minVal = 10.0;
    const maxVal = 100.0;
    const leftPad = 42.0;
    const bottomPad = 46.0;
    final chartW = size.width - leftPad - 16;
    final chartH = size.height - bottomPad - 16;

    final axisPaint = Paint()
      ..color = Colors.grey.withValues(alpha: 0.3)
      ..strokeWidth = 1;
    for (var i = 0; i <= 9; i++) {
      final y = 12 + chartH * i / 9;
      canvas.drawLine(Offset(leftPad, y), Offset(size.width - 8, y), axisPaint);
      final label = (maxVal - (maxVal - minVal) * i / 9).round().toString();
      final tp = TextPainter(
        text: TextSpan(text: '$label%', style: const TextStyle(fontSize: 9, color: Colors.grey)),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(2, y - 5));
    }

    final n = terms.length;
    final points = <Offset>[];
    for (var i = 0; i < n; i++) {
      final x = leftPad + (n == 1 ? chartW / 2 : chartW * i / (n - 1));
      final v = terms[i].gpa.clamp(minVal, maxVal);
      final y = 12 + chartH * (1 - (v - minVal) / (maxVal - minVal));
      points.add(Offset(x, y));

      final year = _extractYear(terms[i].termLabel);
      final labelText = year.isNotEmpty ? 'ف${i + 1}\n$year' : 'ف${i + 1}';
      final labelTp = TextPainter(
        text: TextSpan(text: labelText, style: const TextStyle(fontSize: 9, color: Colors.grey)),
        textDirection: TextDirection.rtl,
        textAlign: TextAlign.center,
      )..layout(maxWidth: 60);
      labelTp.paint(canvas, Offset(x - 15, size.height - bottomPad + 6));
    }

    for (var i = 0; i < points.length - 1; i++) {
      final rising = points[i + 1].dy <= points[i].dy;
      final segPaint = Paint()
        ..color = rising ? const Color(0xFF2E7D32) : const Color(0xFFC62828)
        ..strokeWidth = 3
        ..style = PaintingStyle.stroke;
      canvas.drawLine(points[i], points[i + 1], segPaint);
    }
    for (final p in points) {
      canvas.drawCircle(p, 4, Paint()..color = Colors.white);
      canvas.drawCircle(p, 4, Paint()
        ..color = Colors.blueGrey
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5);
    }
  }

  @override
  bool shouldRepaint(covariant _GpaChartPainter oldDelegate) => true;
}

class _HomeTab extends StatelessWidget {
  final StudentInfo? student;
  final List<CourseModel> courses;
  final List<TermGrades> terms;
  final bool isLoading;
  final bool isOffline;
  final Future<void> Function() onRefresh;
  final VoidCallback onCalendarTap;

  const _HomeTab({
    required this.student,
    required this.courses,
    required this.terms,
    required this.isLoading,
    required this.isOffline,
    required this.onRefresh,
    required this.onCalendarTap,
  });

  @override
  Widget build(BuildContext context) {
    if (isLoading && student == null) {
      return const Center(child: LiquidLoadingIndicator());
    }
    return ValueListenableBuilder<double>(
      valueListenable: ThemeService.fieldOpacity,
      builder: (context, opacity, _) {
        final fieldColor = _fieldColor(context, opacity);
        return RefreshIndicator(
          onRefresh: onRefresh,
          child: ListView(
            padding: const EdgeInsets.only(top: 16, left: 16, right: 16, bottom: 80),
            children: [
              if (isOffline) _offlineNotice(context, opacity),
              if (student != null)
                StudentDashboardCard(
                  student: student!,
                  terms: terms,
                  onCalendarTap: onCalendarTap,
                  onGpaTap: () => _showGpaChart(context, terms),
                  fieldOpacity: opacity,
                ),
              const SizedBox(height: 16),
              if (courses.isNotEmpty) ...[
                Text(AppLang.tr('المواد المسجلة'), style: _adaptiveText(context, opacity, size: 16, weight: FontWeight.bold)),
                const SizedBox(height: 8),
                for (final course in courses)
                  Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    decoration: BoxDecoration(
                      color: fieldColor,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: CourseListItem(course: course),
                  ),
              ],
            ],
          ),
        );
      },
    );
  }
}

class _PlanTab extends StatefulWidget {
  final List<PlanSection> sections;
  final List<CourseModel> registeredCourses;
  final StudentInfo? student;
  final bool isLoading;
  final bool isOffline;
  final Future<void> Function() onRefresh;

  const _PlanTab({
    required this.sections,
    required this.registeredCourses,
    required this.student,
    required this.isLoading,
    required this.isOffline,
    required this.onRefresh,
  });

  @override
  State<_PlanTab> createState() => _PlanTabState();
}

class _PlanTabState extends State<_PlanTab> {
  bool _showRegisteredOnly = true;

  String _normalizeCourseCode(String code) {
    final cleaned = code.replaceAll(RegExp(r'[^a-zA-Z0-9]'), '').toUpperCase();
    return cleaned.replaceFirst(RegExp(r'^0+(?=[A-Z0-9])'), '');
  }

  bool _isCurrentlyStudying(String code) {
    final normalizedCode = _normalizeCourseCode(code);
    if (normalizedCode.isEmpty) return false;

    return widget.registeredCourses.any((rc) {
      final rcNormalized = _normalizeCourseCode(rc.code);
      if (rcNormalized.isEmpty) return false;
      return rcNormalized == normalizedCode ||
          normalizedCode.contains(rcNormalized) ||
          rcNormalized.contains(normalizedCode);
    });
  }

  Color _courseColor(PlanDetailRow c) {
    if (c.passed) return const Color(0xFF2E7D32);       
    if (_isCurrentlyStudying(c.courseCode)) {
      return const Color(0xFF1565C0);                   
    }
    if (c.taken) return const Color(0xFFC62828);        
    return Colors.grey;                                  
  }

  String _courseStatus(PlanDetailRow c) {
    if (c.passed) return AppLang.tr('مكتملة');
    if (_isCurrentlyStudying(c.courseCode)) {
      return AppLang.tr('قيد الدراسة');
    }
    if (c.taken) return AppLang.tr('لم تُجتز');
    return AppLang.tr('لم تُدرس');
  }

  @override
  Widget build(BuildContext context) {
    if (widget.isLoading && widget.sections.isEmpty) {
      return const Center(child: LiquidLoadingIndicator());
    }

    final remainingCourses = widget.student?.remainingCoursesCount ??
        widget.sections
            .expand((s) => s.courses)
            .where((c) => !c.passed)
            .length;

    return ValueListenableBuilder<double>(
      valueListenable: ThemeService.fieldOpacity,
      builder: (context, opacity, _) {
        final fieldColor = _fieldColor(context, opacity);
        return RefreshIndicator(
          onRefresh: widget.onRefresh,
          child: ListView(
            padding: const EdgeInsets.only(top: 16, left: 16, right: 16, bottom: 80),
            children: [
              if (widget.isOffline) _offlineNotice(context, opacity),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: fieldColor,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(AppLang.tr('متبقي لإنهاء الخطة الدراسية'),
                            style: _adaptiveText(context, opacity, size: 14, weight: FontWeight.w600)),
                        Text(_remainingText(remainingCourses),
                            style: _adaptiveText(context, opacity, size: 16, weight: FontWeight.bold)),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: _toggleButton(AppLang.tr('المسجلة فقط'), _showRegisteredOnly,
                              () => setState(() => _showRegisteredOnly = true)),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: _toggleButton(AppLang.tr('الخطة كاملة'), !_showRegisteredOnly,
                              () => setState(() => _showRegisteredOnly = false)),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 12,
                runSpacing: 6,
                children: [
                  _LegendDot(color: const Color(0xFF2E7D32), label: AppLang.tr('مكتملة')),
                  _LegendDot(color: const Color(0xFF1565C0), label: AppLang.tr('قيد الدراسة')),
                  _LegendDot(color: const Color(0xFFC62828), label: AppLang.tr('لم تُجتز')),
                  _LegendDot(color: Colors.grey, label: AppLang.tr('لم تُدرس')),
                ],
              ),
              const SizedBox(height: 16),
              for (final section in widget.sections) _buildSection(section, fieldColor, opacity),
            ],
          ),
        );
      },
    );
  }

  String _remainingText(int n) {
    if (n == 1) return AppLang.tr('مادة واحدة');
    if (n == 2) return AppLang.tr('مادتان');
    if (n >= 3 && n <= 10) return '$n ${AppLang.tr('مواد')}';
    return '$n ${AppLang.tr('مادة')}';
  }

  String _courseCountText(int n) {
    if (n == 0) return '0';
    if (n == 1) return AppLang.tr('مادة واحدة');
    if (n == 2) return AppLang.tr('مادتان');
    if (n >= 3 && n <= 10) return '$n ${AppLang.tr('مواد')}';
    return '$n ${AppLang.tr('مادة')}';
  }

  Widget _toggleButton(String label, bool active, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: active ? Theme.of(context).colorScheme.primary : Colors.transparent,
          border: Border.all(color: Theme.of(context).dividerColor),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: active ? Colors.white : null,
          ),
        ),
      ),
    );
  }

  Widget _buildSection(PlanSection section, Color fieldColor, double opacity) {
    final courses = _showRegisteredOnly
        ? section.courses.where((c) => c.taken || c.passed || _isCurrentlyStudying(c.courseCode)).toList()
        : section.courses;

    if (_showRegisteredOnly && courses.isEmpty) return const SizedBox.shrink();

    final all = section.courses;
    final requiredHrs = section.summary.itemHrs;
    final avgHrsPerCourse = all.isNotEmpty
        ? all.fold<int>(0, (s, c) => s + (c.hours > 0 ? c.hours : 3)) / all.length
        : 3.0;
    final required = avgHrsPerCourse > 0 ? (requiredHrs / avgHrsPerCourse).round() : 0;
    final passed = all.where((c) => c.passed).length;
    final currentlyStudying = all.where((c) => !c.passed && _isCurrentlyStudying(c.courseCode)).length;
    final failed = all.where((c) => c.taken && !c.passed && !_isCurrentlyStudying(c.courseCode)).length;

    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: fieldColor,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(AppLang.tr(section.summary.planItem),
                    style: _adaptiveText(context, opacity, size: 14, weight: FontWeight.bold)),
                const SizedBox(height: 4),
                Text(
                  '${AppLang.tr('مطلوبة')}: ${_courseCountText(required)} · ${AppLang.tr('مجتازة')}: ${_courseCountText(passed)} · ${AppLang.tr('قيد الدراسة')}: ${_courseCountText(currentlyStudying)} · ${AppLang.tr('لم يجتزها')}: ${_courseCountText(failed)}',
                  style: _adaptiveText(context, opacity, size: 11, secondary: true),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          if (courses.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(AppLang.tr('لا توجد مواد'), style: _adaptiveText(context, opacity, size: 12, secondary: true)),
            ),
          for (final c in courses)
            Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                border: Border(
                  right: AppLang.isAr ? BorderSide(color: _courseColor(c), width: 5) : BorderSide.none,
                  left: !AppLang.isAr ? BorderSide(color: _courseColor(c), width: 5) : BorderSide.none,
                ),
                color: fieldColor,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(
                          text: c.courseName,
                          style: _adaptiveText(context, opacity, size: 15, weight: FontWeight.bold),
                        ),
                        TextSpan(
                          text: '  ${c.courseCode}',
                          style: _adaptiveText(context, opacity, size: 11, secondary: true),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(_courseStatus(c),
                      style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: _courseColor(c),
                          shadows: opacity < 0.55 ? const [Shadow(color: Colors.black87, blurRadius: 6)] : null)),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _LegendDot extends StatelessWidget {
  final Color color;
  final String label;

  const _LegendDot({required this.color, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 10, height: 10, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 5),
        Text(label, style: const TextStyle(fontSize: 11)),
      ],
    );
  }
}

class _GradesTab extends StatefulWidget {
  final StudentInfo? student;
  final List<TermGrades> terms;
  final bool isLoading;
  final bool isOffline;
  final Future<void> Function() onRefresh;

  const _GradesTab({
    this.student,
    required this.terms,
    required this.isLoading,
    required this.isOffline,
    required this.onRefresh,
  });

  @override
  State<_GradesTab> createState() => _GradesTabState();
}

class _GradesTabState extends State<_GradesTab> {
  bool _showAll = true;

  Color _getGradeColor(double? grade, String caseStatus) {
    if (caseStatus.toUpperCase().contains('PASS') || caseStatus == 'ناجح') return const Color(0xFF00BFA5);
    if (grade == null) return Colors.grey;
    if (grade >= 90) return const Color(0xFF00BFA5);
    if (grade >= 80) return const Color(0xFF81C784);
    if (grade >= 70) return const Color(0xFFFFB300);
    if (grade >= 60) return const Color(0xFFE57373);
    if (grade >= 50) return const Color(0xFFD32F2F);
    return Theme.of(context).brightness == Brightness.dark ? Colors.white54 : Colors.black87;
  }

  Widget _buildLegendDot(Color color, String label) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 10, height: 10, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 4),
        Text(label, style: const TextStyle(fontSize: 10)),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    if (widget.isLoading && widget.terms.isEmpty) {
      return const Center(child: LiquidLoadingIndicator());
    }
    
    final s = widget.student;
    final remainingHours = ((s?.planHours ?? 135) - (s?.passedHours ?? 0)).clamp(0, 999).toString();

    return ValueListenableBuilder<double>(
      valueListenable: ThemeService.fieldOpacity,
      builder: (context, opacity, _) {
        final fieldColor = _fieldColor(context, opacity);
        return RefreshIndicator(
          onRefresh: widget.onRefresh,
          child: ListView(
            padding: const EdgeInsets.only(top: 16, left: 16, right: 16, bottom: 80),
            children: [
              if (widget.isOffline) _offlineNotice(context, opacity),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: fieldColor,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4)],
                ),
                child: Column(
                  children: [
                    Text(AppLang.tr('ملخص كشف الدرجات'), style: _adaptiveText(context, opacity, size: 14, weight: FontWeight.bold)),
                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        _buildStat(AppLang.tr('المعدل التراكمي'), s?.gpa.toStringAsFixed(2) ?? '-', opacity),
                        _buildStat(AppLang.tr('الساعات التراكمية'), s?.cumulativeHours.toStringAsFixed(0) ?? '-', opacity),
                        _buildStat(AppLang.tr('ساعات ناجحة'), s?.passedHours.toStringAsFixed(0) ?? '-', opacity),
                        _buildStat(AppLang.tr('متبقي على الخطة'), remainingHours, opacity),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              
              Wrap(
                alignment: WrapAlignment.center,
                spacing: 8,
                runSpacing: 4,
                children: [
                  _buildLegendDot(const Color(0xFF00BFA5), '٩٠-١٠٠'),
                  _buildLegendDot(const Color(0xFF81C784), '٨٠-٨٩'),
                  _buildLegendDot(const Color(0xFFFFB300), '٧٠-٧٩'),
                  _buildLegendDot(const Color(0xFFE57373), '٦٠-٦٩'),
                  _buildLegendDot(const Color(0xFFD32F2F), '٥٠-٥٩'),
                  _buildLegendDot(Theme.of(context).brightness == Brightness.dark ? Colors.white54 : Colors.black87, AppLang.tr('أقل من ٥٠')),
                ],
              ),
              const SizedBox(height: 16),

              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _buildToggleButton(AppLang.tr('الكل'), _showAll, () => setState(() => _showAll = true)),
                  const SizedBox(width: 8),
                  _buildToggleButton(AppLang.tr('حسب الفصل'), !_showAll, () => setState(() => _showAll = false)),
                ],
              ),
              const SizedBox(height: 16),

              for (final term in widget.terms) _buildTerm(term, fieldColor),
            ],
          ),
        );
      },
    );
  }

  Widget _buildStat(String label, String value, double opacity) {
    return Column(
      children: [
        Text(label, style: _adaptiveText(context, opacity, size: 11, secondary: true)),
        const SizedBox(height: 4),
        Text(value, style: _adaptiveText(context, opacity, size: 16, weight: FontWeight.bold)),
      ],
    );
  }

  Widget _buildToggleButton(String label, bool active, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
        decoration: BoxDecoration(
          color: active ? Theme.of(context).colorScheme.primary : Theme.of(context).disabledColor.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.bold,
            color: active ? Colors.white : Theme.of(context).textTheme.bodyMedium?.color,
          ),
        ),
      ),
    );
  }

  Widget _buildTerm(TermGrades term, Color fieldColor) {
    final courseCards = Column(
      children: term.courses.map((c) => _buildCourseCard(c, fieldColor)).toList(),
    );

    if (_showAll) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 4),
        child: courseCards,
      );
    } else {
      return Card(
        margin: const EdgeInsets.only(bottom: 8),
        clipBehavior: Clip.antiAlias,
        color: fieldColor,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        child: ExpansionTile(
          title: Text(term.termLabel, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
          subtitle: Text('GPA: ${term.gpa}', style: const TextStyle(fontSize: 11)),
          children: [
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: courseCards,
            )
          ],
        ),
      );
    }
  }

  Widget _buildCourseCard(CourseModel course, Color fieldColor) {
    final color = _getGradeColor(course.totalGrade, course.gradeCase);
    
    String gradeText = '';
    if (course.totalGrade != null) {
      gradeText = course.totalGrade!.toStringAsFixed(0);
    } else if (course.gradeCase != '-' && course.gradeCase.isNotEmpty) {
      gradeText = course.gradeCase.contains('PASS') ? AppLang.tr('ناجح') : (course.gradeCase.contains('WITH') ? AppLang.tr('منسحب') : 'ع');
    }
    
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => _showGradeDetailDialog(context, course, color, gradeText),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: fieldColor,
          borderRadius: BorderRadius.circular(8),
          border: Border(
            right: AppLang.isAr ? BorderSide(color: color, width: 4) : BorderSide.none,
            left: !AppLang.isAr ? BorderSide(color: color, width: 4) : BorderSide.none,
          ),
          boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 2, offset: Offset(0, 1))],
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                (AppLang.isAr && course.nameAr.trim().isNotEmpty) ? course.nameAr : course.nameEn,
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
              ),
            ),
            const SizedBox(width: 12),
            CircleAvatar(
              backgroundColor: color,
              radius: 20,
              child: Text(
                gradeText,
                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

void _showGradeDetailDialog(BuildContext context, CourseModel course, Color color, String gradeText) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => Directionality(
      textDirection: TextDirection.rtl,
      child: DraggableScrollableSheet(
        initialChildSize: 0.4,
        minChildSize: 0.25,
        maxChildSize: 0.7,
        expand: false,
        builder: (context, scrollController) {
          return Container(
            decoration: BoxDecoration(
              color: Theme.of(context).cardColor,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
            ),
            child: ListView(
              controller: scrollController,
              padding: const EdgeInsets.fromLTRB(20, 10, 20, 24),
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 16),
                    decoration: BoxDecoration(
                      color: Colors.grey.withValues(alpha: 0.4),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                Text(
                  (AppLang.isAr && course.nameAr.trim().isNotEmpty) ? course.nameAr : course.nameEn,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                const SizedBox(height: 4),
                Text(course.code, style: const TextStyle(fontSize: 12, color: Colors.grey)),
                const Divider(height: 24),
                _gradeDetailRow(AppLang.tr('منتصف'), course.midGrade?.toString() ?? '-'),
                _gradeDetailRow(AppLang.tr('أعمال'), course.courseworkGrade?.toString() ?? '-'),
                _gradeDetailRow(AppLang.tr('نهائي'), course.finalGrade?.toString() ?? '-'),
                _gradeDetailRow(AppLang.tr('المجموع'), gradeText.isEmpty ? '-' : gradeText),
              ],
            ),
          );
        },
      ),
    ),
  );
}

Widget _gradeDetailRow(String label, String value) {
  return Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(value),
        Text(label, style: const TextStyle(fontWeight: FontWeight.bold)),
      ],
    ),
  );
}

class _AccountTab extends StatefulWidget {
  final double balance;
  final List<AccountTransaction> transactions;
  final List<InstallmentItem> installments;
  final bool isLoading;
  final bool isOffline;
  final Future<void> Function() onRefresh;

  const _AccountTab({
    required this.balance,
    required this.transactions,
    required this.installments,
    required this.isLoading,
    required this.isOffline,
    required this.onRefresh,
  });

  @override
  State<_AccountTab> createState() => _AccountTabState();
}

class _AccountTabState extends State<_AccountTab> {
  bool _showRecentOnly = true;

  DateTime? _parseDate(String d) {
    if (d.isEmpty) return null;
    try {
      return DateTime.parse(d);
    } catch (_) {}
    try {
      final parts = d.split(RegExp(r'[/.-]'));
      if (parts.length >= 3) {
        final monthNames = {'JAN': 1, 'FEB': 2, 'MAR': 3, 'APR': 4, 'MAY': 5, 'JUN': 6, 'JUL': 7, 'AUG': 8, 'SEP': 9, 'OCT': 10, 'NOV': 11, 'DEC': 12};
        int? m = int.tryParse(parts[1]);
        if (m == null && monthNames.containsKey(parts[1].toUpperCase())) {
          m = monthNames[parts[1].toUpperCase()];
        }
        if (m != null) {
          int year = int.parse(parts[2]);
          if (year < 100) year += 2000;
          return DateTime(year, m, int.parse(parts[0]));
        }
      }
    } catch (_) {}
    return null;
  }

  String _formatAmount(double amt) {
    String s = amt.toStringAsFixed(2);
    if (s.endsWith('.00')) return s.substring(0, s.length - 3);
    if (s.endsWith('0')) return s.substring(0, s.length - 1);
    return s;
  }

  @override
  Widget build(BuildContext context) {
    if (widget.isLoading && widget.transactions.isEmpty) {
      return const Center(child: LiquidLoadingIndicator());
    }

    final threeMonthsAgo = DateTime.now().subtract(const Duration(days: 90));
    final filteredTransactions = widget.transactions.where((tx) {
      if (!_showRecentOnly) return true;
      final txDate = _parseDate(tx.date);
      if (txDate == null) return true;
      return txDate.isAfter(threeMonthsAgo);
    }).toList();

    double nextInstAmountNum = 0.0;
    for (final i in widget.installments) {
      if (!i.isPaid) {
        nextInstAmountNum = i.amount;
        break;
      }
    }
    final nextInstAmount = nextInstAmountNum.toStringAsFixed(2);

    return ValueListenableBuilder<double>(
      valueListenable: ThemeService.fieldOpacity,
      builder: (context, opacity, _) {
        final fieldColor = _fieldColor(context, opacity);
        final primary = Theme.of(context).colorScheme.primary;
        return RefreshIndicator(
          onRefresh: widget.onRefresh,
          child: ListView(
            padding: const EdgeInsets.only(top: 16, left: 16, right: 16, bottom: 80),
            children: [
              if (widget.isOffline) _offlineNotice(context, opacity),
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: fieldColor,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4, offset: Offset(0, 2))],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(AppLang.tr('ملخص الحساب'), style: _adaptiveText(context, opacity, size: 13, weight: FontWeight.bold, secondary: true)),
                    const SizedBox(height: 20),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(AppLang.tr('الرصيد المطلوب'), style: _adaptiveText(context, opacity, size: 12, secondary: true)),
                            const SizedBox(height: 4),
                            Text(
                              widget.balance.toStringAsFixed(2),
                              style: const TextStyle(color: Color(0xFFD32F2F), fontSize: 18, fontWeight: FontWeight.bold),
                            ),
                          ],
                        ),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(AppLang.tr('القسط القادم'), style: _adaptiveText(context, opacity, size: 12, secondary: true)),
                            const SizedBox(height: 4),
                            Text(
                              nextInstAmount, 
                              style: _adaptiveText(context, opacity, size: 18, weight: FontWeight.bold),
                            ),
                          ],
                        ),
                      ],
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      child: Divider(height: 1, thickness: 1, color: Colors.grey.withValues(alpha: 0.2)),
                    ),
                    if (widget.installments.isNotEmpty)
                      for (final inst in widget.installments)
                        _buildInstallmentRow(
                          AppLang.tr(inst.title), 
                          '${inst.amount} - ${inst.isPaid ? (AppLang.isAr ? 'مدفوع' : 'Paid') : AppLang.tr('غير مدفوع')}'
                        )
                    else
                      Center(child: Text(AppLang.isAr ? 'لا توجد أقساط مسجلة' : 'No installments found', style: const TextStyle(color: Colors.grey, fontSize: 12))),
                    Padding(
                      padding: const EdgeInsets.only(top: 16),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                        decoration: BoxDecoration(
                          color: primary.withValues(alpha: 0.10),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Row(
                          children: [
                            Container(width: 10, height: 10, decoration: const BoxDecoration(color: Color(0xFF27AE60), shape: BoxShape.circle)),
                            const SizedBox(width: 6),
                            Expanded(child: Text(AppLang.tr('دفعة قمت بسدادها'), style: const TextStyle(fontSize: 12))),
                            const SizedBox(width: 12),
                            Container(width: 10, height: 10, decoration: const BoxDecoration(color: Color(0xFFE74C3C), shape: BoxShape.circle)),
                            const SizedBox(width: 6),
                            Expanded(child: Text(AppLang.tr('رسوم مستحقة عليك'), style: const TextStyle(fontSize: 12))),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _buildToggleBtn(AppLang.tr('آخر 3 أشهر'), _showRecentOnly, () => setState(() => _showRecentOnly = true)),
                  const SizedBox(width: 12),
                  _buildToggleBtn(AppLang.tr('الكشف الكامل'), !_showRecentOnly, () => setState(() => _showRecentOnly = false)),
                ],
              ),
              const SizedBox(height: 20),

              if (filteredTransactions.isEmpty)
                Center(child: Text(AppLang.tr('لا توجد معاملات في هذه الفترة'), style: const TextStyle(color: Colors.grey))),

              if (filteredTransactions.isNotEmpty)
                Container(
                  decoration: BoxDecoration(
                    color: fieldColor,
                    borderRadius: BorderRadius.circular(16),
                    boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4, offset: Offset(0, 2))],
                  ),
                  child: ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: filteredTransactions.length,
                    separatorBuilder: (context, index) => Divider(height: 1, thickness: 1, color: Colors.grey.withValues(alpha: 0.15)),
                    itemBuilder: (context, index) {
                      final tx = filteredTransactions[index];
                      final title = tx.claimType.isNotEmpty ? tx.claimType : tx.docType;
                      final isPayment = tx.paid != null && tx.paid! > 0;
                      final isFee = tx.fees != null && tx.fees! > 0;
                      final amount = isPayment ? tx.paid! : (isFee ? tx.fees! : tx.balance);
                      
                      return Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(AppLang.tr(title), style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                                const SizedBox(height: 6),
                                Text(tx.date, style: const TextStyle(color: Colors.grey, fontSize: 12)),
                              ],
                            ),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: isPayment ? const Color(0xFF27AE60) : const Color(0xFFE74C3C),
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                  child: Text(
                                    isPayment ? AppLang.tr('دفعة') : AppLang.tr('رسوم'),
                                    style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold),
                                  ),
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  _formatAmount(amount),
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                  textDirection: TextDirection.ltr,
                                ),
                              ],
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ),
              const SizedBox(height: 24),
            ],
          ),
        );
      },
    );
  }

  Widget _buildInstallmentRow(String title, String status) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title, style: TextStyle(fontSize: 13, color: Theme.of(context).textTheme.bodyMedium?.color)),
          Text(status, style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: Theme.of(context).textTheme.bodyMedium?.color)),
        ],
      ),
    );
  }

  Widget _buildToggleBtn(String title, bool isActive, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        decoration: BoxDecoration(
          color: isActive ? const Color(0xFF3498DB) : Theme.of(context).disabledColor.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          title,
          style: TextStyle(
            color: isActive ? Colors.white : Theme.of(context).textTheme.bodyMedium?.color,
            fontWeight: FontWeight.bold,
            fontSize: 13,
          ),
        ),
      ),
    );
  }
}