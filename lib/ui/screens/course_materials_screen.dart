import 'package:flutter/material.dart';
import 'package:open_file/open_file.dart';
import '../../core/moodle_service.dart';
import '../../models/moodle_model.dart';
import 'app_lang.dart';

class CourseMaterialsScreen extends StatefulWidget {
  final String moodleId;
  final String courseName;

  const CourseMaterialsScreen({super.key, required this.moodleId, required this.courseName});

  @override
  State<CourseMaterialsScreen> createState() => _CourseMaterialsScreenState();
}

class _CourseMaterialsScreenState extends State<CourseMaterialsScreen> {
  List<MoodleWeek> _weeks = [];
  Set<String> _downloaded = {};
  bool _loading = true;
  String? _downloadingUrl;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final weeks = await MoodleService.fetchCourseMaterials(widget.moodleId);
    final downloaded = await MoodleService.downloadedFileNames(widget.moodleId);
    if (mounted) {
      setState(() {
        _weeks = weeks;
        _downloaded = downloaded;
        _loading = false;
      });
    }
  }

  Future<void> _openFile(MoodleFileItem file) async {
    setState(() => _downloadingUrl = file.url);

    // إظهار نافذة منبثقة تفاعلية توضح أن عملية التنزيل جارية الآن
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return Directionality(
          textDirection: TextDirection.rtl,
          child: AlertDialog(
            content: Row(
              children: [
                const CircularProgressIndicator(),
                const SizedBox(width: 20),
                Expanded(
                  child: Text(
                    AppLang.tr('جاري تنزيل الملف، يرجى الانتظار...'),
                    style: const TextStyle(fontSize: 14),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );

    final localFile = await MoodleService.getOrDownloadFile(widget.moodleId, file);

    // إغلاق نافذة التحميل فور انتهاء العملية
    if (mounted) {
      Navigator.of(context, rootNavigator: true).pop();
      setState(() => _downloadingUrl = null);
    }

    if (localFile == null) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLang.tr('تعذّر تنزيل الملف، تحقق من اتصالك بالإنترنت'))),
        );
      }
      return;
    }
    
    await OpenFile.open(localFile.path);
    final downloaded = await MoodleService.downloadedFileNames(widget.moodleId);
    if (mounted) setState(() => _downloaded = downloaded);
  }

  bool _isDownloaded(MoodleFileItem file) {
    final ext = file.type.trim().toLowerCase();
    final expected = ext.isNotEmpty && !file.name.toLowerCase().endsWith('.$ext') ? '${file.name}.$ext' : file.name;
    return _downloaded.contains(expected);
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        appBar: AppBar(title: Text(widget.courseName)),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : _weeks.isEmpty
                ? Center(child: Text(AppLang.tr('لا توجد مواد تعليمية مرفوعة لهذه المادة')))
                : RefreshIndicator(
                    onRefresh: _load,
                    child: ListView(
                      padding: const EdgeInsets.all(16),
                      children: [
                        for (final week in _weeks) _buildWeek(week),
                      ],
                    ),
                  ),
      ),
    );
  }

  Widget _buildWeek(MoodleWeek week) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(week.week, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
          ),
          for (final cat in week.categories) ...[
            if (cat.label.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 10, bottom: 6),
                child: Text(cat.label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
              ),
            Container(
              decoration: BoxDecoration(
                color: Theme.of(context).cardColor,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Column(
                children: [
                  for (int i = 0; i < cat.files.length; i++) ...[
                    _buildFileRow(cat.files[i]),
                    if (i != cat.files.length - 1)
                      Divider(height: 1, color: Colors.grey.withValues(alpha: 0.15)),
                  ],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildFileRow(MoodleFileItem file) {
    final isDownloading = _downloadingUrl == file.url;
    return ListTile(
      onTap: isDownloading ? null : () => _openFile(file),
      title: Text(file.name, style: const TextStyle(fontSize: 13)),
      trailing: isDownloading
          ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (_isDownloaded(file))
                  const Padding(
                    padding: EdgeInsets.only(left: 6),
                    child: Icon(Icons.check_circle, size: 16, color: Color(0xFF1D9E75)),
                  ),
                if (file.type.isNotEmpty)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: const Color(0xFF3498DB),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(file.type, style: const TextStyle(color: Colors.white, fontSize: 10)),
                  ),
              ],
            ),
    );
  }
}