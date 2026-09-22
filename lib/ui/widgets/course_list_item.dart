import 'package:flutter/material.dart';
import '../../models/course_model.dart';
import '../../core/moodle_service.dart';
import '../screens/moodle_course_screen.dart'; // تأكد أن المسار صحيح لشاشة المودل
import 'status_colors.dart';
import 'course_detail_dialog.dart';

class CourseListItem extends StatefulWidget {
  final CourseModel course;

  const CourseListItem({super.key, required this.course});

  @override
  State<CourseListItem> createState() => _CourseListItemState();
}

class _CourseListItemState extends State<CourseListItem> {
  bool _isLoadingMoodle = false;

  void _openMoodle(BuildContext context) async {
    setState(() => _isLoadingMoodle = true);
    
    // سحب الرابط من الكاش الذي تبنيه خدمة MoodleService
    final moodleId = await MoodleService.resolveMoodleId(widget.course.code);
    
    if (!mounted) return;
    setState(() => _isLoadingMoodle = false);

    if (moodleId != null && moodleId.isNotEmpty) {
      // فتح شاشة المودل (تأكد أنك تملك MoodleCourseScreen)
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => MoodleCourseScreen(
            courseTitle: widget.course.nameEn,
            moodleId: moodleId,
          ),
        ),
      );
    } else {
      // إذا فشلت المطابقة أو لم يتوفر رابط
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('عذراً، لم نتمكن من العثور على رابط هذه المادة في مودل تلقائياً.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = StatusColors.attendanceColor(widget.course.attendancePercent);
    final pctText = widget.course.attendancePercent != null
        ? '${widget.course.attendancePercent!.toStringAsFixed(0)}%'
        : '-';

    return ListTile(
      onTap: () => showCourseDetailDialog(context, widget.course),
      leading: CircleAvatar(
        radius: 20,
        backgroundColor: Colors.white.withValues(alpha: 0.85),
        child: Text(
          pctText,
          style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: color),
        ),
      ),
      title: Text(
        widget.course.nameEn,
        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
      ),
      subtitle: Text(
        '${widget.course.code}  •  ${widget.course.days} ${widget.course.startTime}-${widget.course.endTime}  •  ${widget.course.hall}',
        style: const TextStyle(fontSize: 12),
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // إضافة زر المودل هنا
          IconButton(
            icon: _isLoadingMoodle
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Image.asset(
                    'assets/images/moodle.png', // تأكد أن صورة الأيقونة موجودة لديك، أو استبدلها بـ Icon(Icons.school)
                    width: 24,
                    height: 24,
                    errorBuilder: (ctx, err, stack) => const Icon(Icons.school, color: Colors.orange),
                  ),
            onPressed: () => _openMoodle(context),
            tooltip: 'فتح المادة في Moodle',
          ),
          const Icon(Icons.chevron_left),
        ],
      ),
    );
  }
}