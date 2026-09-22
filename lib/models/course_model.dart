class CourseModel {
  final String code;
  final String nameAr;
  final String nameEn;
  final String section;
  final String instructor;
  final String days;
  final String startTime;
  final String endTime;
  final String hall;
  final double? attendancePercent;
  final double? midGrade;
  final double? courseworkGrade;
  final double? finalGrade;
  final double? totalGrade;
  final String gradeCase;
  final String? midExamDate;
  final String? midExamTime;
  final String? finalExamDate;
  final String? finalExamTime;

  const CourseModel({
    required this.code,
    required this.nameAr,
    required this.nameEn,
    required this.section,
    required this.instructor,
    required this.days,
    required this.startTime,
    required this.endTime,
    required this.hall,
    this.attendancePercent,
    this.midGrade,
    this.courseworkGrade,
    this.finalGrade,
    this.totalGrade,
    this.gradeCase = '-',
    this.midExamDate,
    this.midExamTime,
    this.finalExamDate,
    this.finalExamTime,
  });

  CourseModel copyWith({
    double? attendancePercent,
    double? midGrade,
    double? courseworkGrade,
    double? finalGrade,
    double? totalGrade,
    String? gradeCase,
  }) {
    return CourseModel(
      code: code,
      nameAr: nameAr,
      nameEn: nameEn,
      section: section,
      instructor: instructor,
      days: days,
      startTime: startTime,
      endTime: endTime,
      hall: hall,
      attendancePercent: attendancePercent ?? this.attendancePercent,
      midGrade: midGrade ?? this.midGrade,
      courseworkGrade: courseworkGrade ?? this.courseworkGrade,
      finalGrade: finalGrade ?? this.finalGrade,
      totalGrade: totalGrade ?? this.totalGrade,
      gradeCase: gradeCase ?? this.gradeCase,
      midExamDate: midExamDate,
      midExamTime: midExamTime,
      finalExamDate: finalExamDate,
      finalExamTime: finalExamTime,
    );
  }

  Map<String, dynamic> toJson() => {
        'code': code,
        'nameAr': nameAr,
        'nameEn': nameEn,
        'section': section,
        'instructor': instructor,
        'days': days,
        'startTime': startTime,
        'endTime': endTime,
        'hall': hall,
        'attendancePercent': attendancePercent,
        'midGrade': midGrade,
        'courseworkGrade': courseworkGrade,
        'finalGrade': finalGrade,
        'totalGrade': totalGrade,
        'gradeCase': gradeCase,
        'midExamDate': midExamDate,
        'midExamTime': midExamTime,
        'finalExamDate': finalExamDate,
        'finalExamTime': finalExamTime,
      };

  factory CourseModel.fromJson(Map<String, dynamic> json) => CourseModel(
        code: json['code'] ?? '',
        nameAr: json['nameAr'] ?? '',
        nameEn: json['nameEn'] ?? '',
        section: json['section'] ?? '',
        instructor: json['instructor'] ?? '',
        days: json['days'] ?? '',
        startTime: json['startTime'] ?? '',
        endTime: json['endTime'] ?? '',
        hall: json['hall'] ?? '',
        attendancePercent: (json['attendancePercent'] as num?)?.toDouble(),
        midGrade: (json['midGrade'] as num?)?.toDouble(),
        courseworkGrade: (json['courseworkGrade'] as num?)?.toDouble(),
        finalGrade: (json['finalGrade'] as num?)?.toDouble(),
        totalGrade: (json['totalGrade'] as num?)?.toDouble(),
        gradeCase: json['gradeCase'] ?? '-',
        midExamDate: json['midExamDate'],
        midExamTime: json['midExamTime'],
        finalExamDate: json['finalExamDate'],
        finalExamTime: json['finalExamTime'],
      );
}

class TermGrades {
  final String termLabel;
  final double termAverage;
  final double gpa;
  final List<CourseModel> courses;

  const TermGrades({
    required this.termLabel,
    required this.termAverage,
    required this.gpa,
    required this.courses,
  });

  Map<String, dynamic> toJson() => {
        'termLabel': termLabel,
        'termAverage': termAverage,
        'gpa': gpa,
        'courses': courses.map((c) => c.toJson()).toList(),
      };

  factory TermGrades.fromJson(Map<String, dynamic> json) => TermGrades(
        termLabel: json['termLabel'] ?? '',
        termAverage: (json['termAverage'] as num?)?.toDouble() ?? 0,
        gpa: (json['gpa'] as num?)?.toDouble() ?? 0,
        courses: (json['courses'] as List<dynamic>? ?? [])
            .map((c) => CourseModel.fromJson(c as Map<String, dynamic>))
            .toList(),
      );
}

class PlanSummaryRow {
  final String planItem;
  final int groupNo;
  final int itemHrs;
  final int studiedHrs;
  final int passedHrs;

  const PlanSummaryRow({
    required this.planItem,
    required this.groupNo,
    required this.itemHrs,
    required this.studiedHrs,
    required this.passedHrs,
  });

  Map<String, dynamic> toJson() => {
        'planItem': planItem,
        'groupNo': groupNo,
        'itemHrs': itemHrs,
        'studiedHrs': studiedHrs,
        'passedHrs': passedHrs,
      };

  factory PlanSummaryRow.fromJson(Map<String, dynamic> json) => PlanSummaryRow(
        planItem: json['planItem'] ?? '',
        groupNo: json['groupNo'] ?? 0,
        itemHrs: json['itemHrs'] ?? 0,
        studiedHrs: json['studiedHrs'] ?? 0,
        passedHrs: json['passedHrs'] ?? 0,
      );
}

class PlanDetailRow {
  final String courseName;
  final String courseCode;
  final int hours;
  final bool taken;
  final bool passed;
  final String prereq;
  final String planItem;

  const PlanDetailRow({
    required this.courseName,
    required this.courseCode,
    required this.hours,
    required this.taken,
    required this.passed,
    required this.prereq,
    this.planItem = '',
  });

  Map<String, dynamic> toJson() => {
        'courseName': courseName,
        'courseCode': courseCode,
        'hours': hours,
        'taken': taken,
        'passed': passed,
        'prereq': prereq,
        'planItem': planItem,
      };

  factory PlanDetailRow.fromJson(Map<String, dynamic> json) => PlanDetailRow(
        courseName: json['courseName'] ?? '',
        courseCode: json['courseCode'] ?? '',
        hours: json['hours'] ?? 0,
        taken: json['taken'] ?? false,
        passed: json['passed'] ?? false,
        prereq: json['prereq'] ?? '',
        planItem: json['planItem'] ?? '',
      );
}

class PlanSection {
  final PlanSummaryRow summary;
  final List<PlanDetailRow> courses;

  const PlanSection({required this.summary, required this.courses});

  Map<String, dynamic> toJson() => {
        'summary': summary.toJson(),
        'courses': courses.map((c) => c.toJson()).toList(),
      };

  factory PlanSection.fromJson(Map<String, dynamic> json) => PlanSection(
        summary: PlanSummaryRow.fromJson(json['summary'] as Map<String, dynamic>),
        courses: (json['courses'] as List<dynamic>? ?? [])
            .map((c) => PlanDetailRow.fromJson(c as Map<String, dynamic>))
            .toList(),
      );
}

class AccountTransaction {
  final String date;
  final String docNumber;
  final String docType;
  final double? fees;
  final double? paid;
  final double balance;
  final String claimType;
  final String paymentMethod;
  final String notes;

  const AccountTransaction({
    required this.date,
    required this.docNumber,
    required this.docType,
    this.fees,
    this.paid,
    required this.balance,
    required this.claimType,
    required this.paymentMethod,
    required this.notes,
  });

  Map<String, dynamic> toJson() => {
        'date': date,
        'docNumber': docNumber,
        'docType': docType,
        'fees': fees,
        'paid': paid,
        'balance': balance,
        'claimType': claimType,
        'paymentMethod': paymentMethod,
        'notes': notes,
      };

  factory AccountTransaction.fromJson(Map<String, dynamic> json) => AccountTransaction(
        date: json['date'] ?? '',
        docNumber: json['docNumber'] ?? '',
        docType: json['docType'] ?? '',
        fees: (json['fees'] as num?)?.toDouble(),
        paid: (json['paid'] as num?)?.toDouble(),
        balance: (json['balance'] as num?)?.toDouble() ?? 0,
        claimType: json['claimType'] ?? '',
        paymentMethod: json['paymentMethod'] ?? '',
        notes: json['notes'] ?? '',
      );
}