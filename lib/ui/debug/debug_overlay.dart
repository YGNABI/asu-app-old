// ignore_for_file: prefer_const_constructors
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'debug_log.dart';
import '../screens/sis_api_service.dart';

class DebugOverlay extends StatefulWidget {
  const DebugOverlay({super.key});

  @override
  State<DebugOverlay> createState() => _DebugOverlayState();
}

class _DebugOverlayState extends State<DebugOverlay> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      right: 8,
      bottom: 8,
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            if (_expanded)
              Container(
                width: MediaQuery.of(context).size.width * 0.92,
                height: MediaQuery.of(context).size.height * 0.55,
                margin: const EdgeInsets.only(bottom: 8),
                clipBehavior: Clip.none,
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.92),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.greenAccent, width: 1),
                ),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Padding(
                          padding: EdgeInsets.all(8.0),
                          child: Text(
                            'Debug Log',
                            style: TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold),
                          ),
                        ),
                        Row(
                          children: [
                            IconButton(
                              icon: const Icon(Icons.download, color: Colors.orange, size: 18),
                              onPressed: () async {
                                DebugLog.log('DUMPING RAW HTML...');
                                final html = await SisApiService.debugFetchRawHtml(SisApiService.pageGrades);
                                await Clipboard.setData(ClipboardData(text: html));
                                DebugLog.log('RAW HTML COPIED TO CLIPBOARD. length=${html.length}');
                              },
                            ),
                            IconButton(
                              icon: const Icon(Icons.copy, color: Colors.white, size: 18),
                              onPressed: () {
                                final text = DebugLog.logs.value.join('\n');
                                Clipboard.setData(ClipboardData(text: text));
                              },
                            ),
                            IconButton(
                              icon: const Icon(Icons.delete, color: Colors.white, size: 18),
                              onPressed: DebugLog.clear,
                            ),
                          ],
                        ),
                      ],
                    ),
                    Expanded(
                      child: ValueListenableBuilder<List<String>>(
                        valueListenable: DebugLog.logs,
                        builder: (context, logs, _) {
                          return ListView.builder(
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            itemCount: logs.length,
                            itemBuilder: (context, index) => SelectableText(
                              logs[index],
                              style: const TextStyle(
                                color: Colors.greenAccent,
                                fontSize: 10,
                                fontFamily: 'monospace',
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            FloatingActionButton.small(
              heroTag: 'debug_overlay_btn',
              backgroundColor: Colors.black87,
              onPressed: () => setState(() => _expanded = !_expanded),
              child: Icon(_expanded ? Icons.close : Icons.bug_report, color: Colors.greenAccent),
            ),
          ],
        ),
      ),
    );
  }
}