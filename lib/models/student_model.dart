class StudentInfo {
  final String studentId;
  final String nameEn;
  final String college;
  final String major;
  final String academicAdvisor;
  final String supervisor;
  final double gpa;
  final String academicWarningStatus;
  final String gender;
  final String schoolTrack;
  final double highSchoolAverage;
  final String photoUrl;
  final bool expectedGraduation;
  final int remainingCoursesCount;
  final String registrationPeriodFrom;
  final String registrationPeriodTo;
  final String addDropPeriodFrom;
  final String addDropPeriodTo;

  // من صفحة كشف الدرجات (P8)
  final double cumulativeHours;
  final double passedHours;
  final String honorRoll;
  final String academicStatus;
  final double planHours;

  const StudentInfo({
    required this.studentId,
    required this.nameEn,
    required this.college,
    required this.major,
    required this.academicAdvisor,
    required this.supervisor,
    required this.gpa,
    required this.academicWarningStatus,
    required this.gender,
    required this.schoolTrack,
    required this.highSchoolAverage,
    required this.photoUrl,
    required this.expectedGraduation,
    required this.remainingCoursesCount,
    required this.registrationPeriodFrom,
    required this.registrationPeriodTo,
    required this.addDropPeriodFrom,
    required this.addDropPeriodTo,
    required this.cumulativeHours,
    required this.passedHours,
    required this.honorRoll,
    required this.academicStatus,
    required this.planHours,
  });

  Map<String, dynamic> toJson() => {
        'studentId': studentId,
        'nameEn': nameEn,
        'college': college,
        'major': major,
        'academicAdvisor': academicAdvisor,
        'supervisor': supervisor,
        'gpa': gpa,
        'academicWarningStatus': academicWarningStatus,
        'gender': gender,
        'schoolTrack': schoolTrack,
        'highSchoolAverage': highSchoolAverage,
        'photoUrl': photoUrl,
        'expectedGraduation': expectedGraduation,
        'remainingCoursesCount': remainingCoursesCount,
        'registrationPeriodFrom': registrationPeriodFrom,
        'registrationPeriodTo': registrationPeriodTo,
        'addDropPeriodFrom': addDropPeriodFrom,
        'addDropPeriodTo': addDropPeriodTo,
        'cumulativeHours': cumulativeHours,
        'passedHours': passedHours,
        'honorRoll': honorRoll,
        'academicStatus': academicStatus,
        'planHours': planHours,
      };

  factory StudentInfo.fromJson(Map<String, dynamic> json) => StudentInfo(
        studentId: json['studentId'] ?? '',
        nameEn: json['nameEn'] ?? '',
        college: json['college'] ?? '',
        major: json['major'] ?? '',
        academicAdvisor: json['academicAdvisor'] ?? '',
        supervisor: json['supervisor'] ?? '',
        gpa: (json['gpa'] as num?)?.toDouble() ?? 0,
        academicWarningStatus: json['academicWarningStatus'] ?? '-',
        gender: json['gender'] ?? '',
        schoolTrack: json['schoolTrack'] ?? '',
        highSchoolAverage: (json['highSchoolAverage'] as num?)?.toDouble() ?? 0,
        photoUrl: json['photoUrl'] ?? '',
        expectedGraduation: json['expectedGraduation'] ?? false,
        remainingCoursesCount: json['remainingCoursesCount'] ?? 0,
        registrationPeriodFrom: json['registrationPeriodFrom'] ?? '',
        registrationPeriodTo: json['registrationPeriodTo'] ?? '',
        addDropPeriodFrom: json['addDropPeriodFrom'] ?? '',
        addDropPeriodTo: json['addDropPeriodTo'] ?? '',
        cumulativeHours: (json['cumulativeHours'] as num?)?.toDouble() ?? 0,
        passedHours: (json['passedHours'] as num?)?.toDouble() ?? 0,
        honorRoll: json['honorRoll'] ?? '-',
        academicStatus: json['academicStatus'] ?? '-',
        planHours: (json['planHours'] as num?)?.toDouble() ?? 0,
      );
}