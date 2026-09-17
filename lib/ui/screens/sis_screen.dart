import 'package:flutter/material.dart';

class SisScreen extends StatefulWidget {
  const SisScreen({super.key});

  @override
  State<SisScreen> createState() => _SisScreenState();
}

class _SisScreenState extends State<SisScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('النظام الأكاديمي (SIS)'),
      ),
      body: const Center(
        child: Text('جاري إعداد بيئة SIS...'),
      ),
    );
  }
}