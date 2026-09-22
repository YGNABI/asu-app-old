import 'package:flutter/material.dart';

/// يغلّف محتوى تبويب واحد بثلاث قدرات:
/// 1) تحديد اتجاه السحب (أفقي مقابل عمودي) لمنع التعارض بين
///    التنقل بين التبويبات والتمرير العادي للمحتوى.
/// 2) سحب للتحديث (Pull-to-Refresh) بتأثير مطاطي (Rubber-band)،
///    يُفعّل فقط عند وجود المستخدم في أعلى نقطة من الصفحة.
/// 3) تنقّل أفقي بين التبويبات بنفس دالة تبديل التبويب المستخدمة
///    عند الضغط المباشر على شريط التبويبات.
class SwipeableTabContent extends StatefulWidget {
  final Widget child; // المحتوى (يفضّل أن يكون ListView/CustomScrollView)
  final ScrollController scrollController;
  final Future<void> Function() onRefresh;
  final void Function(int direction) onTabSwipe; // 1 = التالي، -1 = السابق
  final double refreshThreshold;
  final double tabSwipeThreshold;

  const SwipeableTabContent({
    super.key,
    required this.child,
    required this.scrollController,
    required this.onRefresh,
    required this.onTabSwipe,
    this.refreshThreshold = 70,
    this.tabSwipeThreshold = 60,
  });

  @override
  State<SwipeableTabContent> createState() => _SwipeableTabContentState();
}

class _SwipeableTabContentState extends State<SwipeableTabContent>
    with SingleTickerProviderStateMixin {
  double _pullDistance = 0; // مقدار السحب المطاطي الحالي (بكسل)
  bool _refreshing = false;
  bool _armedForRefresh = false; // تجاوز حدّ التحديث فعلاً

  Offset? _panStartGlobal;
  double _horizontalDelta = 0;
  bool _horizontalLocked = false;
  bool _verticalLocked = false;

  late final AnimationController _snapController;
  Animation<double>? _snapAnimation;

  static const double _slop = 10; // أدنى مسافة لتحديد نية السحب

  @override
  void initState() {
    super.initState();
    _snapController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 220),
    )..addListener(() {
        if (_snapAnimation != null) {
          setState(() => _pullDistance = _snapAnimation!.value);
        }
      });
  }

  @override
  void dispose() {
    _snapController.dispose();
    super.dispose();
  }

  double _rubberBand(double dy) {
    const maxDrag = 140.0;
    final t = dy / maxDrag;
    return maxDrag * (1 - (1 / (t + 1)));
  }

  void _animatePullTo(double target, {VoidCallback? onDone}) {
    _snapAnimation = Tween<double>(begin: _pullDistance, end: target)
        .animate(CurvedAnimation(parent: _snapController, curve: Curves.easeOut));
    _snapController.forward(from: 0).whenComplete(() {
      if (onDone != null) onDone();
    });
  }

  Future<void> _triggerRefresh() async {
    setState(() {
      _refreshing = true;
      _armedForRefresh = false;
    });
    _animatePullTo(56);
    try {
      await widget.onRefresh();
    } finally {
      if (mounted) {
        _animatePullTo(0, onDone: () {
          if (mounted) setState(() => _refreshing = false);
        });
      }
    }
  }

  void _onPanStart(DragStartDetails details) {
    _panStartGlobal = details.globalPosition;
    _horizontalDelta = 0;
    _horizontalLocked = false;
    _verticalLocked = false;
  }

  void _onPanUpdate(DragUpdateDetails details) {
    if (_panStartGlobal == null) return;

    final dx = details.globalPosition.dx - _panStartGlobal!.dx;
    final dy = details.globalPosition.dy - _panStartGlobal!.dy;

    if (!_horizontalLocked && !_verticalLocked) {
      if (dx.abs() < _slop && dy.abs() < _slop) return;
      if (dx.abs() > dy.abs()) {
        _horizontalLocked = true;
      } else {
        _verticalLocked = true;
      }
    }

    if (_horizontalLocked) {
      _horizontalDelta = dx;
      return;
    }

    if (_verticalLocked) {
      final atTop = !widget.scrollController.hasClients ||
          widget.scrollController.offset <= 0;
      if (atTop && dy > 0 && !_refreshing) {
        final pull = _rubberBand(dy);
        setState(() {
          _pullDistance = pull;
          _armedForRefresh = pull >= widget.refreshThreshold;
        });
      }
    }
  }

  void _onPanEnd(DragEndDetails details) {
    if (_horizontalLocked) {
      if (_horizontalDelta.abs() > widget.tabSwipeThreshold) {
        widget.onTabSwipe(_horizontalDelta > 0 ? -1 : 1);
      }
    } else if (_verticalLocked) {
      if (_armedForRefresh && !_refreshing) {
        _triggerRefresh();
      } else if (!_refreshing) {
        _animatePullTo(0);
      }
    }

    _panStartGlobal = null;
    _horizontalLocked = false;
    _verticalLocked = false;
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onPanStart: _onPanStart,
      onPanUpdate: _onPanUpdate,
      onPanEnd: _onPanEnd,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            height: _pullDistance,
            child: Center(
              child: Opacity(
                opacity: (_pullDistance / widget.refreshThreshold).clamp(0, 1),
                child: _refreshing
                    ? const SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(strokeWidth: 2.4),
                      )
                    : Icon(
                        _armedForRefresh
                            ? Icons.arrow_upward
                            : Icons.arrow_downward,
                        size: 20,
                      ),
              ),
            ),
          ),
          Transform.translate(
            offset: Offset(0, _pullDistance),
            child: widget.child,
          ),
        ],
      ),
    );
  }
}