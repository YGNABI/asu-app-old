import 'package:flutter/material.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('نظام معلومات الطالب - ASU'),
        backgroundColor: Colors.blue,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () {
              // هنا سنقوم بتحديث جلب البيانات لاحقاً
            },
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'أهلاً بك، طالب جامعة العلوم التطبيقية',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 20),
            Expanded(
              child: GridView.count(
                crossAxisCount: MediaQuery.of(context).size.width > 600 ? 3 : 2,
                crossAxisSpacing: 16,
                mainAxisSpacing: 16,
                children: [
                  _buildDashboardCard(
                    context,
                    title: 'الجدول الدراسي',
                    icon: Icons.schedule,
                    color: Colors.orange,
                    onTap: () {
                      // الانتقال لصفحة الجدول
                    },
                  ),
                  _buildDashboardCard(
                    context,
                    title: 'الدرجات والأكاديمي',
                    icon: Icons.grade,
                    color: Colors.green,
                    onTap: () {
                      // الانتقال لصفحة الدرجات
                    },
                  ),
                  _buildDashboardCard(
                    context,
                    title: 'الملف الشخصي',
                    icon: Icons.person,
                    color: Colors.blue,
                    onTap: () {
                      // الانتقال لصفحة الملف
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDashboardCard(BuildContext context, {required String title, required IconData icon, required Color color, required VoidCallback onTap}) {
    return Card(
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircleAvatar(
              radius: 30,
              backgroundColor: color.withValues(alpha: 0.2),
              child: Icon(icon, size: 30, color: color),
            ),
            const SizedBox(height: 12),
            Text(
              title,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}