import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/student_model.dart';
import '../models/course_model.dart';
import '../ui/screens/sis_api_service.dart';

class CacheService {
  static const _keyStudent = 'cache_student';
  static const _keyCourses = 'cache_courses';
  static const _keyPlan = 'cache_plan';
  static const _keyGrades = 'cache_grades';
  static const _keyBalance = 'cache_balance';
  static const _keyTransactions = 'cache_transactions';
  static const _keyInstallments = 'cache_installments';

  static Future<void> saveHomeData(StudentInfo student, List<CourseModel> courses) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyStudent, jsonEncode(student.toJson()));
    await prefs.setString(_keyCourses, jsonEncode(courses.map((c) => c.toJson()).toList()));
  }

  static Future<(StudentInfo, List<CourseModel>)?> loadHomeData() async {
    final prefs = await SharedPreferences.getInstance();
    final studentRaw = prefs.getString(_keyStudent);
    if (studentRaw == null) return null;
    try {
      final student = StudentInfo.fromJson(jsonDecode(studentRaw));
      final coursesRaw = prefs.getString(_keyCourses);
      final courses = coursesRaw == null
          ? <CourseModel>[]
          : (jsonDecode(coursesRaw) as List<dynamic>)
              .map((c) => CourseModel.fromJson(c as Map<String, dynamic>))
              .toList();
      return (student, courses);
    } catch (_) {
      return null;
    }
  }

  static Future<void> savePlanData(List<PlanSection> sections) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyPlan, jsonEncode(sections.map((s) => s.toJson()).toList()));
  }

  static Future<List<PlanSection>> loadPlanData() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_keyPlan);
    if (raw == null) return [];
    try {
      return (jsonDecode(raw) as List<dynamic>)
          .map((s) => PlanSection.fromJson(s as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return [];
    }
  }

  static Future<void> saveGradesData(List<TermGrades> terms) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyGrades, jsonEncode(terms.map((t) => t.toJson()).toList()));
  }

  static Future<List<TermGrades>> loadGradesData() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_keyGrades);
    if (raw == null) return [];
    try {
      return (jsonDecode(raw) as List<dynamic>)
          .map((t) => TermGrades.fromJson(t as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return [];
    }
  }

  static Future<void> saveAccountData(
      double balance, List<AccountTransaction> transactions, List<InstallmentItem> installments) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_keyBalance, balance);
    await prefs.setString(_keyTransactions, jsonEncode(transactions.map((t) => t.toJson()).toList()));
    await prefs.setString(_keyInstallments, jsonEncode(installments.map((i) => i.toJson()).toList()));
  }

  static Future<(double, List<AccountTransaction>, List<InstallmentItem>)?> loadAccountData() async {
    final prefs = await SharedPreferences.getInstance();
    final txRaw = prefs.getString(_keyTransactions);
    if (txRaw == null) return null;
    try {
      final balance = prefs.getDouble(_keyBalance) ?? 0;
      final transactions = (jsonDecode(txRaw) as List<dynamic>)
          .map((t) => AccountTransaction.fromJson(t as Map<String, dynamic>))
          .toList();
      final instRaw = prefs.getString(_keyInstallments);
      final installments = instRaw == null
          ? <InstallmentItem>[]
          : (jsonDecode(instRaw) as List<dynamic>)
              .map((i) => InstallmentItem.fromJson(i as Map<String, dynamic>))
              .toList();
      return (balance, transactions, installments);
    } catch (_) {
      return null;
    }
  }
}