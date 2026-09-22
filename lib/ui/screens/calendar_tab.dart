import 'package:flutter/material.dart';
import 'app_lang.dart';

class CalendarEventInfo {
  final String title;
  final String dateFrom;
  final String dateTo;
  final String dayLabel;
  final bool isHoliday;

  const CalendarEventInfo({
    required this.title,
    required this.dateFrom,
    this.dateTo = '',
    required this.dayLabel,
    this.isHoliday = false,
  });
}

class CalendarTermInfo {
  final String title;
  final List<CalendarEventInfo> events;

  const CalendarTermInfo({required this.title, required this.events});
}

const List<CalendarTermInfo> academicCalendar = [
  CalendarTermInfo(
    title: 'الفصل الدراسي الأول 2026 / 2027',
    events: [
      CalendarEventInfo(title: 'بدء دوام أعضاء هيئة التدريس', dateFrom: '2026/08/30', dayLabel: 'الأحد'),
      CalendarEventInfo(title: 'الإرشاد والتسجيل والسحب والإضافة', dateFrom: '2026/09/01', dateTo: '2026/09/05', dayLabel: 'الثلاثاء - السبت'),
      CalendarEventInfo(title: 'بدء الدراسة', dateFrom: '2026/09/06', dayLabel: 'الأحد'),
      CalendarEventInfo(title: 'التسجيل المتأخر والسحب والإضافة', dateFrom: '2026/09/06', dateTo: '2026/09/10', dayLabel: 'الأحد - الخميس'),
      CalendarEventInfo(title: 'يوم التهيئة للطلبة الجدد', dateFrom: '2026/09/14', dayLabel: 'الاثنين'),
      CalendarEventInfo(title: 'اختبارات منتصف الفصل الدراسي', dateFrom: '2026/10/17', dateTo: '2026/10/31', dayLabel: 'السبت - السبت'),
      CalendarEventInfo(title: 'نهاية فترة الانسحاب من المقررات الدراسية', dateFrom: '2026/10/31', dayLabel: 'السبت'),
      CalendarEventInfo(title: 'الإرشاد والتسجيل المبكر للفصل الدراسي الثاني', dateFrom: '2026/11/08', dateTo: '2026/11/12', dayLabel: 'الأحد - الخميس'),
      CalendarEventInfo(title: 'فترة الامتحانات النهائية', dateFrom: '2026/12/08', dateTo: '2026/12/26', dayLabel: 'الثلاثاء - السبت'),
      CalendarEventInfo(title: 'عطلة العيد الوطني', dateFrom: '2026/12/16', dateTo: '2026/12/17', dayLabel: 'الأربعاء - الخميس', isHoliday: true),
      CalendarEventInfo(title: 'بدء اجازة الطلبة', dateFrom: '2026/12/27', dayLabel: 'الأحد'),
    ],
  ),
  CalendarTermInfo(
    title: 'الفصل الدراسي الثاني 2026 / 2027',
    events: [
      CalendarEventInfo(title: 'الإرشاد والتسجيل والانسحاب والإضافة', dateFrom: '2027/01/05', dateTo: '2027/01/09', dayLabel: 'الثلاثاء - السبت'),
      CalendarEventInfo(title: 'بدء الدراسة', dateFrom: '2027/01/10', dayLabel: 'الأحد'),
      CalendarEventInfo(title: 'التسجيل المتأخر والسحب والإضافة', dateFrom: '2027/01/10', dateTo: '2027/01/14', dayLabel: 'الأحد - الخميس'),
      CalendarEventInfo(title: 'يوم التهيئة للطلبة الجدد', dateFrom: '2027/01/18', dayLabel: 'الاثنين'),
      CalendarEventInfo(title: 'اختبارات منتصف الفصل الدراسي', dateFrom: '2027/02/20', dateTo: '2027/03/06', dayLabel: 'السبت - السبت'),
      CalendarEventInfo(title: 'نهاية فترة الانسحاب من المقررات الدراسية', dateFrom: '2027/03/06', dayLabel: 'السبت'),
      CalendarEventInfo(title: 'عطلة عيد الفطر المبارك *', dateFrom: '2027/03/09', dateTo: '2027/03/11', dayLabel: 'الثلاثاء - الخميس', isHoliday: true),
      CalendarEventInfo(title: 'الإرشاد والتسجيل المبكر للفصل الدراسي الصيفي', dateFrom: '2027/03/14', dateTo: '2027/03/18', dayLabel: 'الأحد - الخميس'),
      CalendarEventInfo(title: 'فترة الامتحانات النهائية', dateFrom: '2027/04/15', dateTo: '2027/04/29', dayLabel: 'الخميس - الخميس'),
      CalendarEventInfo(title: 'بدء اجازة الطلبة', dateFrom: '2027/04/30', dayLabel: 'الجمعة'),
    ],
  ),
  CalendarTermInfo(
    title: 'الفصل الدراسي الصيفي 2026 / 2027',
    events: [
      CalendarEventInfo(title: 'الإرشاد والتسجيل والانسحاب والإضافة', dateFrom: '2027/05/06', dateTo: '2027/05/08', dayLabel: 'الخميس - السبت'),
      CalendarEventInfo(title: 'بدء الدراسة', dateFrom: '2027/05/09', dayLabel: 'الأحد'),
      CalendarEventInfo(title: 'التسجيل المتأخر والسحب والإضافة للفصل الصيفي', dateFrom: '2027/05/09', dateTo: '2027/05/11', dayLabel: 'الأحد - الثلاثاء'),
      CalendarEventInfo(title: 'التسجيل المتأخر والسحب والإضافة للفصل الصيفي الممتد', dateFrom: '2027/05/09', dateTo: '2027/05/13', dayLabel: 'الأحد - الخميس'),
      CalendarEventInfo(title: 'عطلة عيد الأضحى المبارك *', dateFrom: '2027/05/15', dateTo: '2027/05/18', dayLabel: 'السبت - الثلاثاء', isHoliday: true),
      CalendarEventInfo(title: 'اختبارات منتصف الفصل الدراسي الصيفي', dateFrom: '2027/05/29', dateTo: '2027/06/07', dayLabel: 'السبت - الاثنين'),
      CalendarEventInfo(title: 'انتهاء فترة الانسحاب من المقررات الدراسية للفصل الصيفي', dateFrom: '2027/06/05', dayLabel: 'السبت'),
      CalendarEventInfo(title: 'عطلة رأس السنة الهجرية *', dateFrom: '2027/06/06', dayLabel: 'الأحد', isHoliday: true),
      CalendarEventInfo(title: 'اختبارات منتصف الفصل الصيفي الممتد', dateFrom: '2027/06/08', dateTo: '2027/06/14', dayLabel: 'الثلاثاء - الاثنين'),
      CalendarEventInfo(title: 'عطلة عاشوراء *', dateFrom: '2027/06/15', dateTo: '2027/06/16', dayLabel: 'الثلاثاء - الأربعاء', isHoliday: true),
      CalendarEventInfo(title: 'الإرشاد والتسجيل المبكر للفصل الدراسي الأول 2027/2028', dateFrom: '2027/06/20', dateTo: '2027/06/23', dayLabel: 'الأحد - الأربعاء'),
      CalendarEventInfo(title: 'انتهاء فترة الانسحاب من المقررات الدراسية للفصل الصيفي الممتد', dateFrom: '2027/06/26', dayLabel: 'السبت'),
      CalendarEventInfo(title: 'فترة الامتحانات النهائية للفصل الصيفي', dateFrom: '2027/06/27', dateTo: '2027/07/05', dayLabel: 'الأحد - الاثنين'),
      CalendarEventInfo(title: 'بدء إجازة الطلبة للفصل الصيفي', dateFrom: '2027/07/06', dayLabel: 'الثلاثاء'),
      CalendarEventInfo(title: 'فترة الامتحانات النهائية للفصل الصيفي الممتد', dateFrom: '2027/08/08', dateTo: '2027/08/14', dayLabel: 'الأحد - السبت'),
      CalendarEventInfo(title: 'بدء إجازة الطلبة للفصل الصيفي الممتد', dateFrom: '2027/08/15', dayLabel: 'الأحد'),
    ],
  ),
  CalendarTermInfo(
    title: 'الفصل الدراسي الأول 2027 / 2028',
    events: [
      CalendarEventInfo(title: 'بدء دوام أعضاء هيئة التدريس', dateFrom: '2027/08/29', dayLabel: 'الأحد'),
      CalendarEventInfo(title: 'الإرشاد والتسجيل والسحب والإضافة', dateFrom: '2027/08/31', dateTo: '2027/09/04', dayLabel: 'الثلاثاء - السبت'),
      CalendarEventInfo(title: 'بدء الدراسة', dateFrom: '2027/09/05', dayLabel: 'الأحد'),
    ],
  ),
];

class CalendarTab extends StatelessWidget {
  const CalendarTab({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.only(top: 16, left: 16, right: 16, bottom: 80),
      children: [
        for (final term in academicCalendar) _buildTermCard(context, term),
      ],
    );
  }

  Widget _buildTermCard(BuildContext context, CalendarTermInfo term) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(16),
        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 4, offset: Offset(0, 2))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: Theme.of(context).primaryColor.withValues(alpha: 0.12),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
            ),
            child: Text(
              AppLang.tr(term.title),
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: Theme.of(context).primaryColor),
            ),
          ),
          for (int i = 0; i < term.events.length; i++) ...[
            _buildEventRow(context, term.events[i]),
            if (i != term.events.length - 1)
              Divider(height: 1, thickness: 1, color: Colors.grey.withValues(alpha: 0.15), indent: 16, endIndent: 16),
          ],
        ],
      ),
    );
  }

  Widget _buildEventRow(BuildContext context, CalendarEventInfo event) {
    final dateText = event.dateTo.isEmpty ? event.dateFrom : '${event.dateFrom} - ${event.dateTo}';
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            event.isHoliday ? Icons.celebration_outlined : Icons.event_note_outlined,
            size: 18,
            color: event.isHoliday ? const Color(0xFFD32F2F) : Theme.of(context).primaryColor,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(AppLang.tr(event.title), style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                const SizedBox(height: 4),
                Text(dateText, style: const TextStyle(color: Colors.grey, fontSize: 12), textDirection: TextDirection.ltr),
                Text(AppLang.tr(event.dayLabel), style: const TextStyle(color: Colors.grey, fontSize: 11)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}