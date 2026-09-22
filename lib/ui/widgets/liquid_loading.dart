import 'package:flutter/material.dart';

class LiquidLoadingIndicator extends StatefulWidget {
  final double size;
  const LiquidLoadingIndicator({super.key, this.size = 56});

  @override
  State<LiquidLoadingIndicator> createState() => _LiquidLoadingIndicatorState();
}

class _LiquidLoadingIndicatorState extends State<LiquidLoadingIndicator> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 3))..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return SizedBox(
      width: widget.size,
      height: widget.size,
      child: ClipOval(
        child: Stack(
          fit: StackFit.expand,
          children: [
            Container(color: Colors.grey.withValues(alpha: 0.15)),
            Opacity(
              opacity: 0.5,
              child: Image.asset(
                'assets/images/app_logo.jpg',
                fit: BoxFit.cover,
                errorBuilder: (c, e, s) => Container(color: primary.withValues(alpha: 0.15)),
              ),
            ),
            AnimatedBuilder(
              animation: _controller,
              builder: (context, child) {
                // يرتفع ويهبط بشكل سائل مستمر
                final t = _controller.value;
                final fill = 0.15 + 0.7 * (0.5 - 0.5 * (t < 0.5 ? (1 - 2 * t) : (2 * t - 1)).abs() * 2).clamp(0.0, 1.0);
                return Align(
                  alignment: Alignment.bottomCenter,
                  child: FractionallySizedBox(
                    heightFactor: fill.clamp(0.1, 0.9),
                    widthFactor: 1,
                    child: Container(color: primary.withValues(alpha: 0.55)),
                  ),
                );
              },
            ),
            Container(
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: primary, width: 2),
              ),
            ),
          ],
        ),
      ),
    );
  }
}