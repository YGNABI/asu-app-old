import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:add_2_calendar/add_2_calendar.dart';
import '../../models/course_model.dart';
import '../../core/schedule_utils.dart';
import '../../core/moodle_service.dart';
import '../screens/sis_api_service.dart';
import '../screens/course_materials_screen.dart';
import '../screens/assessments_screen.dart';
import '../screens/app_lang.dart';

void showCourseDetailDialog(BuildContext context, CourseModel course) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => Directionality(
      textDirection: TextDirection.rtl,
      child: DraggableScrollableSheet(
        initialChildSize: 0.55,
        minChildSize: 0.3,
        maxChildSize: 0.9,
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
                Row(
                  children: [
                    Expanded(
                      child: Text(course.nameEn,
                          textAlign: TextAlign.right,
                          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                    ),
                    IconButton(
                      icon: const Icon(Icons.email_outlined, size: 20),
                      tooltip: 'مراسلة الدكتور',
                      onPressed: () => _emailInstructor(course.instructor),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                _instructorRow(course.instructor),
                _row('الشعبة', course.section),
                const Divider(),
                _row('علامة امتحان المنتصف', course.midGrade?.toString() ?? '—'),
                _row('علامة اعمال الفصل', course.courseworkGrade?.toString() ?? '—'),
                _row('علامة الامتحان النهائي', course.finalGrade?.toString() ?? '—'),
                _row('المجموع', course.totalGrade?.toString() ?? '—'),
                const Divider(),
                _examRow(
                  context,
                  'موعد امتحان المنتصف',
                  course.midExamDate,
                  course.midExamTime,
                  course: course,
                  isMid: true,
                ),
                _examRow(
                  context,
                  'موعد الامتحان النهائي',
                  course.finalExamDate,
                  course.finalExamTime,
                  course: course,
                  isMid: false,
                ),
                const Divider(),
                FutureBuilder<String?>(
                  future: MoodleService.resolveMoodleId(course.code),
                  builder: (context, snapshot) {
                    final moodleId = snapshot.data;
                    if (moodleId == null || moodleId.isEmpty) return const SizedBox.shrink();
                    final courseName = course.nameAr.isNotEmpty ? course.nameAr : course.nameEn;
                    return Column(
                      children: [
                        const SizedBox(height: 4),
                        OutlinedButton.icon(
                          style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(44)),
                          onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => CourseMaterialsScreen(moodleId: moodleId, courseName: courseName),
                            ),
                          ),
                          icon: const Icon(Icons.folder_outlined, size: 18),
                          label: Text(AppLang.tr('عرض المادة التعليمية')),
                        ),
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(44)),
                          onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => AssessmentsScreen(moodleId: moodleId, courseName: courseName, sisCourse: course),
                            ),
                          ),
                          icon: const Icon(Icons.assignment_outlined, size: 18),
                          label: Text(AppLang.tr('عرض البحوث والواجبات')),
                        ),
                      ],
                    );
                  },
                ),
              ],
            ),
          );
        },
      ),
    ),
  );
}

Widget _instructorRow(String instructorName) {
  return Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Text(instructorName),
            const SizedBox(width: 8),
            FutureBuilder<String>(
              future: instructorName.trim().isEmpty
                  ? Future.value('')
                  : SisApiService.findInstructorPhoto(instructorName),
              builder: (context, snapshot) {
                final src = snapshot.data ?? '';
                if (src.isEmpty || !src.startsWith('data:image')) {
                  return const SizedBox.shrink();
                }
                try {
                  final bytes = base64Decode(src.substring(src.indexOf(',') + 1));
                  return GestureDetector(
                    onTap: () {
                      showDialog(
                        context: context,
                        builder: (context) => Dismissible(
                          key: UniqueKey(),
                          direction: DismissDirection.vertical,
                          onDismissed: (_) => Navigator.pop(context),
                          child: Directionality(
                            textDirection: TextDirection.rtl,
                            child: Dialog(
                              backgroundColor: Colors.transparent,
                              elevation: 0,
                              insetPadding: EdgeInsets.zero,
                              child: GestureDetector(
                                onTap: () => Navigator.pop(context),
                                child: Container(
                                  color: Colors.black.withValues(alpha: 0.9),
                                  alignment: Alignment.center,
                                  child: InteractiveViewer(
                                    minScale: 0.5,
                                    maxScale: 4.0,
                                    child: Center(
                                      child: ClipOval(
                                        child: SizedBox(
                                          width: 240,
                                          height: 240,
                                          child: Image.memory(bytes, fit: BoxFit.cover),
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      );
                    },
                    child: CircleAvatar(radius: 32, backgroundImage: MemoryImage(bytes)),
                  );
                } catch (_) {
                  return const SizedBox.shrink();
                }
              },
            ),
          ],
        ),
        const Text('المدرس', style: TextStyle(fontWeight: FontWeight.bold)),
      ],
    ),
  );
}

Widget _examRow(BuildContext context, String label, String? date, String? time, {required CourseModel course, required bool isMid}) {
  final hasDate = date != null && date.isNotEmpty;
  return Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Text('${date ?? '—'} ${time ?? ''}'),
            if (hasDate) ...[
              const SizedBox(width: 6),
              GestureDetector(
                onTap: () => _addExamToCalendar(course, date, time, isMid: isMid),
                child: const Icon(Icons.calendar_month_outlined, size: 18),
              ),
            ],
          ],
        ),
        Text(label, style: const TextStyle(fontWeight: FontWeight.bold)),
      ],
    ),
  );
}

void _addExamToCalendar(CourseModel course, String? dateStr, String? timeStr, {required bool isMid}) {
  if (dateStr == null || dateStr.isEmpty) return;
  final time = (timeStr != null && timeStr.isNotEmpty) ? ScheduleUtils.parseArabicTime(timeStr) : null;
  final examDate = ScheduleUtils.parseFlexibleDate(dateStr, time: time);
  if (examDate == null) return;

  final courseName = course.nameAr.isNotEmpty ? course.nameAr : course.nameEn;
  final title = isMid ? 'امتحان منتصف الفصل - $courseName' : 'الامتحان النهائي - $courseName';

  final event = Event(
    title: title,
    description: 'قاعة ${course.hall} • ${course.instructor}',
    location: course.hall,
    startDate: examDate,
    endDate: examDate.add(const Duration(hours: 2)),
  );
  Add2Calendar.addEvent2Cal(event);
}

Future<void> _emailInstructor(String instructorName) async {
  // يحتاج قائمة إيميلات الدكاترة لاحقاً لمطابقة الاسم بإيميل حقيقي - حالياً بدون عنوان محدد
  const to = '';

  final outlookUri = to.isEmpty
      ? Uri.parse('ms-outlook://compose')
      : Uri.parse('ms-outlook://compose?to=$to');
  try {
    final canOpenOutlook = await canLaunchUrl(outlookUri);
    if (canOpenOutlook) {
      await launchUrl(outlookUri);
      return;
    }
  } catch (_) {}

  final mailtoUri = Uri(scheme: 'mailto', path: to.isEmpty ? null : to);
  await launchUrl(mailtoUri);
}

Widget _row(String label, String value) {
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