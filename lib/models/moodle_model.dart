class MoodleCourseSummary {
  final String moodleId;
  final String name;
  final String? courseCode;

  const MoodleCourseSummary({
    required this.moodleId,
    required this.name,
    this.courseCode,
  });
}

class MoodleFileItem {
  final String name;
  final String url;
  final String type;

  const MoodleFileItem({required this.name, required this.url, required this.type});
}

class MoodleCategory {
  final String label;
  final List<MoodleFileItem> files;

  const MoodleCategory({required this.label, required this.files});
}

class MoodleWeek {
  final String week;
  final List<MoodleCategory> categories;

  const MoodleWeek({required this.week, required this.categories});
}

class AssessmentItem {
  final String name;
  final String category;
  final String url;
  final String dueDateRaw;
  final String instructionsText;

  const AssessmentItem({
    required this.name,
    required this.category,
    required this.url,
    required this.dueDateRaw,
    required this.instructionsText,
  });
}