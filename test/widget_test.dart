import 'package:flutter_test/flutter_test.dart';
import 'package:asu_app/main.dart';

void main() {
  testWidgets('App loads test', (WidgetTester tester) async {
    // بناء التطبيق لتجاوز الاختبار بنجاح
    await tester.pumpWidget(const AsuApp());
  });
}