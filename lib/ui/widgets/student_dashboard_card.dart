import 'dart:convert';
import 'package:flutter/material.dart';
import '../../models/student_model.dart';

class StudentDashboardCard extends StatelessWidget {
  final StudentInfo student;
  final List<dynamic>? terms;
  final VoidCallback? onCalendarTap;
  final VoidCallback? onGpaTap;
  final double fieldOpacity;

  const StudentDashboardCard({
    super.key,
    required this.student,
    this.terms,
    this.onCalendarTap,
    this.onGpaTap,
    this.fieldOpacity = 1.0,
  });

  bool get _isMasterForColorOnly => student.planHours > 0 && student.planHours < 135;

  int _getHonorLevel() {
    final isHonor = student.honorRoll.trim();

    if (isHonor.contains('نعم') || isHonor.contains('Yes') || isHonor.contains('أول')) {
      return 2;
    }
    if (isHonor.contains('ثان') || isHonor.contains('Second')) {
      return 1;
    }

    if (terms != null && terms!.length >= 2) {
      dynamic newestTerm;
      dynamic prevTerm;
      
      if (terms!.last.termLabel.compareTo(terms!.first.termLabel) > 0) {
        newestTerm = terms!.last;
        prevTerm = terms![terms!.length - 2];
      } else {
        newestTerm = terms!.first;
        prevTerm = terms![1];
      }

      if (newestTerm.termLabel.contains('الصيفي') || newestTerm.termLabel.contains('Summer')) {
        final goldThreshold = _isMasterForColorOnly ? 94.0 : 92.0;
        final silverThreshold = _isMasterForColorOnly ? 88.0 : 84.0;

        if (prevTerm.gpa >= goldThreshold) return 2;
        if (prevTerm.termAverage >= silverThreshold) return 1;
      }
    }

    return 0;
  }

  LinearGradient _gpaCardGradient(BuildContext context) {
    final level = _getHonorLevel();

    if (level == 2) {
      return const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFFFFE79A), Color(0xFFD4AF37), Color(0xFFB8860B)],
        stops: [0.0, 0.55, 1.0],
      );
    }
    if (level == 1) {
      return const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFFF2F2F2), Color(0xFFC0C0C0), Color(0xFF9A9A9A)],
        stops: [0.0, 0.55, 1.0],
      );
    }
    final base = Theme.of(context).primaryColor;
    return LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [base, Color.lerp(base, Colors.black, 0.35)!],
    );
  }

  bool get _isSpecialCard => _getHonorLevel() > 0;

  Color get _primaryTextColor => _isSpecialCard ? const Color(0xFF1B1B1B) : Colors.white;
  Color get _secondaryTextColor => _isSpecialCard ? const Color(0xFF3A3A3A) : Colors.white70;

  Color _warningColor() {
    final status = student.academicWarningStatus.trim();
    if (status.contains('ثاني') || status.contains('Second')) return Colors.red;
    if (status.contains('أول') || status.contains('First')) return Colors.amber;
    if (status.contains('ثالث') || status.contains('Third')) return Colors.black;
    return Colors.green;
  }

  String _warningMessage() {
    final status = student.academicWarningStatus.trim();
    if (status.contains('ثاني') || status.contains('Second')) return 'يوجد إنذار أكاديمي ثانٍ';
    if (status.contains('أول') || status.contains('First')) return 'يوجد إنذار أكاديمي أول';
    if (status.contains('ثالث') || status.contains('Third')) return 'يوجد إنذار أكاديمي ثالث';
    return 'لا يوجد إنذار أكاديمي';
  }

  void _showWarningDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => Directionality(
        textDirection: TextDirection.rtl,
        child: AlertDialog(
          title: const Text('الحالة الأكاديمية'),
          content: Text(_warningMessage()),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('إغلاق')),
          ],
        ),
      ),
    );
  }

  String _initials() {
    final parts = student.nameEn.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts[0].isNotEmpty ? parts[0][0] : '?';
    return '${parts[0][0]}${parts[1][0]}';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: _gpaCardGradient(context),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: _gpaCardGradient(context).colors.last.withValues(alpha: 0.35),
            blurRadius: 16,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 76,
                height: 96,
                clipBehavior: Clip.antiAlias,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.amber, width: 2),
                  color: Colors.white24,
                ),
                child: _buildAvatar(),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      student.nameEn,
                      style: TextStyle(
                        color: _primaryTextColor,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      student.college,
                      style: TextStyle(color: _secondaryTextColor, fontSize: 13),
                    ),
                  ],
                ),
              ),
              GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => _showWarningDialog(context),
                child: Container(
                  width: 32,
                  height: 32,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _warningColor(),
                  ),
                  child: Text(
                    _initials(),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _infoRow('الرقم الأكاديمي', student.studentId),
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: onGpaTap,
            child: _infoRow('المعدل التراكمي', student.gpa.toStringAsFixed(2)),
          ),
          _infoRow('المرشد الأكاديمي', student.academicAdvisor),
          _infoRow('متوقع تخرجه', student.expectedGraduation ? 'نعم' : 'لا'),
          _infoRow('الوضع الأكاديمي', student.academicStatus),
          _infoRow('متبقي للتخرج', _remainingCoursesText()),
          if (student.registrationPeriodFrom.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.26 * fieldOpacity),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'فترة التسجيل: ${student.registrationPeriodFrom} إلى ${student.registrationPeriodTo}',
                          style: TextStyle(color: _primaryTextColor, fontSize: 12),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'السحب والإضافة: ${student.addDropPeriodFrom} إلى ${student.addDropPeriodTo}',
                          style: TextStyle(color: _primaryTextColor, fontSize: 12),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 10),
                  GestureDetector(
                    onTap: onCalendarTap,
                    child: Image.asset(
                      'assets/images/calendar_logo.png',
                      height: 52,
                      width: 52,
                      errorBuilder: (ctx, err, stack) =>
                          Icon(Icons.calendar_month_outlined, size: 44, color: _primaryTextColor),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  String _remainingCoursesText() {
    final n = student.remainingCoursesCount;
    if (n == 1) return 'مادة واحدة';
    if (n == 2) return 'مادتان';
    if (n >= 3 && n <= 10) return '$n مواد';
    return '$n مادة';
  }

  Widget _buildAvatar() {
    final url = student.photoUrl;
    if (url.startsWith('data:image')) {
      try {
        final base64Str = url.substring(url.indexOf(',') + 1).trim();
        final bytes = base64Decode(base64Str);
        return Image.memory(
          bytes,
          fit: BoxFit.cover,
          width: 76,
          height: 96,
          cacheWidth: 256,
          gaplessPlayback: true,
          errorBuilder: (context, error, stackTrace) {
            debugPrint('AVATAR: render error = $error');
            return const Icon(Icons.person, size: 36, color: Colors.white);
          },
        );
      } catch (e) {
        debugPrint('AVATAR: decode error = $e');
        return const Icon(Icons.person, size: 36, color: Colors.white);
      }
    }
    return const Icon(Icons.person, size: 36, color: Colors.white);
  }

  Widget _infoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: _secondaryTextColor, fontSize: 13)),
          Text(value, style: TextStyle(color: _primaryTextColor, fontSize: 13, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}