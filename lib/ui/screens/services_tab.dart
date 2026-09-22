import 'package:flutter/material.dart';
import '../../core/sos_service.dart';
import '../../core/theme_service.dart';
import '../widgets/liquid_loading.dart';
import 'app_lang.dart';

class ServicesTab extends StatefulWidget {
  const ServicesTab({super.key});

  @override
  State<ServicesTab> createState() => _ServicesTabState();
}

class _ServicesTabState extends State<ServicesTab> {
  bool _isLoading = false;
  bool _isClosedTab = false;

  @override
  void initState() {
    super.initState();
    _refreshData();
  }

  Future<void> _refreshData() async {
    setState(() => _isLoading = true);
    await SosService.ensureAuthenticated();
    await SosService.prefetchData();
    if (mounted) setState(() => _isLoading = false);
  }

  void _showNewRequestDialog() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => Directionality(
        textDirection: TextDirection.rtl,
        child: Container(
          height: MediaQuery.of(context).size.height * 0.6,
          decoration: BoxDecoration(
            color: Theme.of(context).cardColor,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: Column(
            children: [
              const SizedBox(height: 12),
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.withValues(alpha: 0.4),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 16),
              Text(
                AppLang.tr('الخدمات المتاحة'),
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const Divider(height: 24),
              Expanded(
                child: SosService.availableServices.isEmpty
                    ? Center(child: Text(AppLang.tr('لا توجد خدمات متاحة حالياً')))
                    : ListView.builder(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        itemCount: SosService.availableServices.length,
                        itemBuilder: (context, index) {
                          final service = SosService.availableServices[index];
                          return Card(
                            margin: const EdgeInsets.only(bottom: 8),
                            child: ListTile(
                              leading: const Icon(Icons.miscellaneous_services, color: Colors.blueGrey),
                              title: Text(service.title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                              trailing: const Icon(Icons.arrow_forward_ios, size: 14),
                              onTap: () {
                                Navigator.pop(context);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text('جاري الانتقال إلى: ${service.title}')),
                                );
                              },
                            ),
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showWithdrawDialog(String requestId) {
    final reasonController = TextEditingController();

    showDialog(
      context: context,
      builder: (context) => Directionality(
        textDirection: TextDirection.rtl,
        child: AlertDialog(
          title: Text(AppLang.tr('سحب الطلب / الاقتراح')),
          content: TextField(
            controller: reasonController,
            maxLines: 3,
            decoration: InputDecoration(
              hintText: AppLang.tr('ذكر سبب الانسحاب...'),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(AppLang.tr('إلغاء')),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
              onPressed: () async {
                if (reasonController.text.trim().isEmpty) return;
                Navigator.pop(context);
                setState(() => _isLoading = true);
                await SosService.withdrawRequest(requestId, reasonController.text.trim());
                await _refreshData();
              },
              child: Text(AppLang.tr('تأكيد الانسحاب'), style: const TextStyle(color: Colors.white)),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final currentList = SosService.currentRequests;
    final closedList = SosService.closedRequests;
    final activeList = _isClosedTab ? closedList : currentList;

    return ValueListenableBuilder<double>(
      valueListenable: ThemeService.fieldOpacity,
      builder: (context, opacity, _) {
        final fieldColor = Theme.of(context).cardColor.withValues(alpha: opacity);

        return Scaffold(
          backgroundColor: Colors.transparent,
          floatingActionButton: FloatingActionButton.extended(
            onPressed: _showNewRequestDialog,
            icon: const Icon(Icons.add),
            label: Text(AppLang.tr('طلب جديد')),
          ),
          body: RefreshIndicator(
            onRefresh: _refreshData,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Row(
                  children: [
                    Expanded(
                      child: GestureDetector(
                        onTap: () => setState(() => _isClosedTab = false),
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            color: !_isClosedTab ? Theme.of(context).colorScheme.primary : fieldColor,
                            borderRadius: const BorderRadius.horizontal(right: Radius.circular(10)),
                            border: Border.all(color: Colors.grey.withValues(alpha: 0.3)),
                          ),
                          child: Text(
                            '${AppLang.tr('الطلبات الحالية')} (${currentList.length})',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: !_isClosedTab ? Colors.white : null,
                            ),
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: GestureDetector(
                        onTap: () => setState(() => _isClosedTab = true),
                        child: Container(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          alignment: Alignment.center,
                          decoration: BoxDecoration(
                            color: _isClosedTab ? Theme.of(context).colorScheme.primary : fieldColor,
                            borderRadius: const BorderRadius.horizontal(left: Radius.circular(10)),
                            border: Border.all(color: Colors.grey.withValues(alpha: 0.3)),
                          ),
                          child: Text(
                            '${AppLang.tr('الطلبات المغلقة')} (${closedList.length})',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: _isClosedTab ? Colors.white : null,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                if (_isLoading)
                  const Padding(
                    padding: EdgeInsets.only(top: 40),
                    child: Center(child: LiquidLoadingIndicator()),
                  )
                else if (activeList.isEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 60),
                    child: Center(
                      child: Text(
                        AppLang.tr('لا توجد طلبات في هذه القائمة'),
                        style: const TextStyle(color: Colors.grey, fontSize: 14),
                      ),
                    ),
                  )
                else
                  for (final req in activeList)
                    Container(
                      margin: const EdgeInsets.only(bottom: 12),
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: fieldColor,
                        borderRadius: BorderRadius.circular(12),
                        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 3, offset: Offset(0, 1))],
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(req.category, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: Colors.blue.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Text(req.status, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.blue)),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Text('${AppLang.tr('التاريخ')}: ${req.date}', style: const TextStyle(fontSize: 11, color: Colors.grey)),
                          const Divider(height: 16),
                          Text('${AppLang.tr('التفاصيل')}: ${req.details}', style: const TextStyle(fontSize: 13)),
                          if (req.remarks.isNotEmpty) ...[
                            const SizedBox(height: 4),
                            Text('${AppLang.tr('ملاحظات')}: ${req.remarks}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
                          ],
                          if (!_isClosedTab && req.id.isNotEmpty) ...[
                            const SizedBox(height: 12),
                            Align(
                              alignment: Alignment.centerLeft,
                              child: OutlinedButton.icon(
                                style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                                onPressed: () => _showWithdrawDialog(req.id),
                                icon: const Icon(Icons.cancel_outlined, size: 16),
                                label: Text(AppLang.tr('سحب الطلب')),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
              ],
            ),
          ),
        );
      },
    );
  }
}