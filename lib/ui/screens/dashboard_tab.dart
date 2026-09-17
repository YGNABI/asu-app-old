import 'package:flutter/material.dart';

class DashboardTab extends StatelessWidget {
  const DashboardTab({super.key});

  // دالة لتحديد لون وشكل البطاقة بناءً على المعدل التراكمي
  BoxDecoration _getCardDecoration(double gpa, BuildContext context) {
    if (gpa >= 92.0) {
      // لائحة شرف الجامعة والكلية (ذهبي لامع)
      return BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFFFD700), Color(0xFFFDB931), Color(0xFFFFD700)],
        ),
        boxShadow: [BoxShadow(color: Colors.amber.withValues(alpha: 0.5), blurRadius: 10)],
      );
    } else if (gpa >= 85.0) {
      // لائحة شرف الكلية (فضي لامع)
      return BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFFE0E0E0)],
        ),
        boxShadow: [BoxShadow(color: Colors.grey.withValues(alpha: 0.5), blurRadius: 10)],
      );
    } else {
      // لون النظام الافتراضي (حسب الـ Dark/Light mode)
      return BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(16),
        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 5)],
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    // قيمة افتراضية مؤقتة للتجربة (سنقوم لاحقاً بسحبها من موقع SIS)
    double currentGpa = 93.5; 
    
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            // بطاقة الطالب
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16.0),
              decoration: _getCardDecoration(currentGpa, context),
              child: Row(
                children: [
                  // صورة الطالب (مؤقتة لحين ربطها برابط الموقع)
                  const CircleAvatar(
                    radius: 40,
                    backgroundColor: Colors.transparent,
                    backgroundImage: NetworkImage('https://via.placeholder.com/150'), 
                  ),
                  const SizedBox(width: 16),
                  // بيانات الطالب
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('اسم الطالب', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: currentGpa >= 85 ? Colors.black : null)),
                        Text('التخصص: Law', style: TextStyle(fontSize: 14, color: currentGpa >= 85 ? Colors.black87 : null)),
                        const SizedBox(height: 8),
                        Text('المعدل التراكمي: $currentGpa', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: currentGpa >= 85 ? Colors.black : null)),
                      ],
                    ),
                  )
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}