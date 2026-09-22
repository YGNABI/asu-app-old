import 'package:flutter/material.dart';
import '../../core/moodle_service.dart';
import '../../models/moodle_model.dart';
import '../../models/course_model.dart';
import 'app_lang.dart';

class AssessmentsScreen extends StatefulWidget {
  final String moodleId;
  final String courseName;
  final CourseModel? sisCourse; // لأخذ مواعيد الامتحانات منه لو متوفرة

  const AssessmentsScreen({super.key, required this.moodleId, required this.courseName, this.sisCourse});

  @override
  State<AssessmentsScreen> createState() => _AssessmentsScreenState();
}

class _AssessmentsScreenState extends State<AssessmentsScreen> {
  List<AssessmentItem> _items = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final items = await MoodleService.fetchAssessments(widget.moodleId);
    if (mounted) setState(() { _items = items; _loading = false; });
  }

  bool get _hasExamDates =>
      (widget.sisCourse?.midExamDate ?? '').isNotEmpty || (widget.sisCourse?.finalExamDate ?? '').isNotEmpty;

  Future<void> _addSingle(AssessmentItem item) async {
    final due = MoodleService.parseDueDate(item.dueDateRaw);
    if (due == null) return;
    final result = await MoodleService.insertCalendarEvent('${AppLang.tr('تسليم')}: ${item.name}', due, 7);
    _showResult(result);
  }

  void _showResult(MoodleCalendarInsertResult result) {
    if (!mounted) return;
    final msg = switch (result) {
      MoodleCalendarInsertResult.added => AppLang.tr('تمت الإضافة إلى التقويم ✅'),
      MoodleCalendarInsertResult.duplicate => AppLang.tr('الموعد مضاف مسبقاً إلى التقويم'),
      MoodleCalendarInsertResult.error => AppLang.tr('حدث خطأ أثناء الإضافة إلى التقويم'),
    };
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  DateTime? _parseExamDateTime(String? dateStr, String? timeStr) {
    if (dateStr == null || dateStr.isEmpty) return null;
    final parts = dateStr.trim().split('-');
    if (parts.length != 3) return null;
    final day = int.tryParse(parts[0]);
    final month = int.tryParse(parts[1]);
    final year = int.tryParse(parts[2]);
    if (day == null || month == null || year == null) return null;
    int hour = 9, minute = 0;
    if (timeStr != null && timeStr.isNotEmpty) {
      final tp = timeStr.split(':');
      hour = int.tryParse(tp.isNotEmpty ? tp[0] : '') ?? 9;
      minute = int.tryParse(tp.length > 1 ? tp[1] : '') ?? 0;
    }
    return DateTime(year, month, day, hour, minute);
  }

  Future<void> _addAll() async {
    int added = 0, dup = 0, failed = 0;
    void tally(MoodleCalendarInsertResult r) {
      switch (r) {
        case MoodleCalendarInsertResult.added:
          added++;
        case MoodleCalendarInsertResult.duplicate:
          dup++;
        case MoodleCalendarInsertResult.error:
          failed++;
      }
    }

    final course = widget.sisCourse;
    if (course != null) {
      final mid = _parseExamDateTime(course.midExamDate, course.midExamTime);
      if (mid != null) {
        tally(await MoodleService.insertCalendarEvent('${AppLang.tr('امتحان المنتصف')}: ${widget.courseName}', mid, 10, location: course.hall));
      }
      final fin = _parseExamDateTime(course.finalExamDate, course.finalExamTime);
      if (fin != null) {
        tally(await MoodleService.insertCalendarEvent('${AppLang.tr('الامتحان النهائي')}: ${widget.courseName}', fin, 10, location: course.hall));
      }
    }

    for (final item in _items) {
      final due = MoodleService.parseDueDate(item.dueDateRaw);
      if (due == null) continue;
      tally(await MoodleService.insertCalendarEvent('${AppLang.tr('تسليم')}: ${item.name}', due, 7));
    }

    if (!mounted) return;
    String msg;
    if (added == 0 && dup == 0 && failed == 0) {
      msg = AppLang.tr('ما فيه مواعيد متوفرة لهذه المادة');
    } else if (failed > 0) {
      msg = '${AppLang.tr('أُضيف')} $added، ${AppLang.tr('مكرر')} $dup، ${AppLang.tr('فشل')} $failed';
    } else if (dup > 0) {
      msg = '${AppLang.tr('أُضيف')} $added، ${AppLang.tr('مضاف مسبقاً')} $dup';
    } else {
      msg = '${AppLang.tr('أُضيفت جميع المواعيد')} ✅ ($added)';
    }
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        appBar: AppBar(title: Text('${widget.courseName} — ${AppLang.tr('الواجبات والبحوث')}')),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : RefreshIndicator(
                onRefresh: _load,
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    if (_items.isNotEmpty || _hasExamDates)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 14),
                        child: ElevatedButton(
                          onPressed: _addAll,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color.fromARGB(162, 8, 9, 8),
                            padding: const EdgeInsets.symmetric(vertical: 14),
                          ),
                          child: Text(
                            AppLang.tr('اضافة مواعيد التسليم للتقويم)'),
                            textAlign: TextAlign.center,
                            style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.bold),
                          ),
                        ),
                      ),
                    if (_items.isEmpty)
                      Center(child: Text(AppLang.tr('لا توجد واجبات أو بحوث مضافة لهذه المادة')))
                    else
                      for (final item in _items) _buildCard(item),
                  ],
                ),
              ),
      ),
    );
  }

  Widget _buildCard(AssessmentItem item) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(12),
        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4, offset: Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 2),
            decoration: BoxDecoration(color: const Color(0xFF8E44AD), borderRadius: BorderRadius.circular(10)),
            child: Text(item.category, style: const TextStyle(color: Colors.white, fontSize: 10)),
          ),
          const SizedBox(height: 8),
          Text(item.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
          const SizedBox(height: 6),
          Text(
            item.dueDateRaw.isNotEmpty
                ? '${AppLang.tr('آخر موعد للتسليم')}: ${item.dueDateRaw}'
                : AppLang.tr('لا يوجد موعد محدد'),
            style: TextStyle(fontSize: 12, color: item.dueDateRaw.isNotEmpty ? const Color(0xFFA32D2D) : Colors.grey),
          ),
          const SizedBox(height: 8),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: Colors.grey.withValues(alpha: 0.08), borderRadius: BorderRadius.circular(8)),
            child: Text(
              item.instructionsText.isNotEmpty ? item.instructionsText : AppLang.tr('لم يُضف المدرّس تعليمات لهذا الواجب/البحث'),
              style: TextStyle(fontSize: 12, color: item.instructionsText.isNotEmpty ? null : Colors.grey),
            ),
          ),
          if (item.dueDateRaw.isNotEmpty) ...[
            const SizedBox(height: 10),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: () => _addSingle(item),
                icon: const Icon(Icons.calendar_month_outlined, size: 18),
                label: Text(AppLang.tr('أضف للتقويم')),
              ),
            ),
          ],
        ],
      ),
    );
  }
}