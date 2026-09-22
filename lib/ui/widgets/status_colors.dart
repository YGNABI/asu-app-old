import 'package:flutter/material.dart';

class StatusColors {
  static LinearGradient? gpaCardGradient(double gpa) {
    if (gpa >= 92) {
      return const LinearGradient(
        colors: [Color(0xFFBF953F), Color(0xFFFCF6BA), Color(0xFFB38728)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    if (gpa >= 85) {
      return const LinearGradient(
        colors: [Color(0xFFC0C0C0), Color(0xFFE8E8E8), Color(0xFFA9A9A9)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      );
    }
    return null;
  }

  static Color attendanceColor(double? absencePercent) {
    if (absencePercent == null) return Colors.grey;
    if (absencePercent < 15) return const Color(0xFF2E7D32);
    if (absencePercent <= 25) return const Color(0xFFF9A825);
    if (absencePercent <= 30) return const Color(0xFFC62828);
    return Colors.black;
  }
}