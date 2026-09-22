import '../../../main.dart'; 

class AppLang {
  static bool get isAr => appLanguage.value.languageCode == 'ar';

  static final Map<String, String> _dict = {
    // التبويبات
    'الرئيسية': 'Home',
    'الخطة الدراسية': 'Study Plan',
    'كشف الدرجات': 'Academic Transcript',
    'كشف الحساب': 'Transaction History',
    
    // صفحة الرئيسية
    'المواد المسجلة': 'Registered Courses',
    'متوقع تخرجه': 'Expected to graduate',
    'الوضع الأكاديمي': 'Academic Status',
    'المرشد الأكاديمي': 'Academic Advisor',
    'الرقم الأكاديمي': 'Academic Number',
    'متوقع تخرجه؟': 'Expected to graduate?',
    'نعم': 'Yes',
    'لا': 'No',
    'فترة التسجيل': 'Registration Period',
    'السحب والاضافة': 'Add/Drop Period',
    'إلى': 'To',
    'يدرس': 'Active',

    // الخطة الدراسية
    'متبقي لإنهاء الخطة الدراسية': 'Remaining to Complete Study Plan',
    'مادة واحدة': '1 Course',
    'مادتان': '2 Courses',
    'مواد': 'Courses',
    'مادة': 'Course',
    'المسجلة فقط': 'Registered Only',
    'الخطة كاملة': 'Full Plan',
    'مكتملة': 'Completed',
    'قيد الدراسة': 'In Progress',
    'لم تُجتز': 'Failed',
    'لم تُدرس': 'Not Taken',
    'جامعة اجبارية': 'University Compulsory',
    'كلية اجبارية': 'College Compulsory',
    'قسم اجبارية': 'Department Compulsory',
    'جامعة اختيارية': 'University Elective',
    'كلية اختيارية': 'College Elective',
    'قسم اختيارية': 'Department Elective',
    'خارج الخطة': 'Out of Plan',
    'مطلوبة': 'Required',
    'مجتازة': 'Passed',
    'لم يجتزها': 'Failed',
    'لا توجد مواد': 'No Courses',

    // كشف الدرجات
    'ملخص كشف الدرجات': 'Transcript Summary',
    'المعدل التراكمي': 'GPA',
    'الساعات التراكمية': 'Cumulative Hours',
    'ساعات ناجحة': 'Passed Hours',
    'متبقي على الخطة': 'Remaining Hours',
    'الكل': 'All',
    'حسب الفصل': 'By Term',
    'منتصف': 'Mid',
    'أعمال': 'Coursework',
    'نهائي': 'Final',
    'ناجح': 'Pass',
    'منسحب': 'Withdrawn',
    'أقل من ٥٠': 'Less than 50',

    // كشف الحساب
    'ملخص الحساب': 'Account Summary',
    'الرصيد المطلوب': 'Required Balance',
    'القسط القادم': 'Next Installment',
    'القسط الأول': '1st Installment',
    'القسط الثاني': '2nd Installment',
    'القسط الثالث': '3rd Installment',
    'غير مدفوع': 'Unpaid',
    'آخر 3 أشهر': 'Last 3 Months',
    'الكشف الكامل': 'Full Statement',
    'لا توجد معاملات في هذه الفترة': 'No transactions in this period',
    'دفعة': 'Payment',
    'رسوم': 'Fees',
  };

  static String tr(String arText) {
    if (isAr) return arText; 

    if (_dict.containsKey(arText)) {
      return _dict[arText]!;
    }

    String result = arText;
    _dict.forEach((ar, en) {
      if (result.contains(ar)) {
        result = result.replaceAll(ar, en);
      }
    });

    return result;
  }
}