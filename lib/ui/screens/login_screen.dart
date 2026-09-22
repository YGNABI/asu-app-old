import 'dart:async';
import 'package:flutter/material.dart';
import 'package:sensors_plus/sensors_plus.dart';
import 'package:local_auth/local_auth.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'sis_api_service.dart';
import 'main_screen.dart';
import '../../main.dart'; 
import 'package:connectivity_plus/connectivity_plus.dart';

class LoginScreen extends StatefulWidget {
  final VoidCallback? onLoginSuccess;

  const LoginScreen({super.key, this.onLoginSuccess});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLoading = false;
  String _errorMessage = '';

  double _sensorX = 0.0;
  StreamSubscription<GyroscopeEvent>? _gyroscopeSubscription;

  final LocalAuthentication _localAuth = LocalAuthentication();
  final _secureStorage = const FlutterSecureStorage();
  bool _saveWithBiometric = false;
  bool _biometricAvailable = false;

  @override
  void initState() {
    super.initState();
    // حركة بطيئة وثقيلة جداً للخلفية لتجنب الوصول للبداية أو النهاية
    _gyroscopeSubscription = gyroscopeEventStream().listen((GyroscopeEvent event) {
      if (mounted) {
        final delta = (event.y * 0.0012).clamp(-0.02, 0.02);
        setState(() {
          _sensorX += delta;
          _sensorX = _sensorX.clamp(-0.15, 0.15);
        });
      }
    });
    _initBiometric();
  }

  Future<void> _initBiometric() async {
    try {
      _biometricAvailable = await _localAuth.canCheckBiometrics;
    } catch (_) {
      _biometricAvailable = false;
    }
    final enabled = await _secureStorage.read(key: 'biometric_enabled');
    if (enabled == 'true' && _biometricAvailable) {
      _tryBiometricLogin();
    }
    if (mounted) setState(() {});
  }

  Future<void> _tryBiometricLogin() async {
    try {
      final didAuth = await _localAuth.authenticate(
        localizedReason: appLanguage.value.languageCode == 'ar'
            ? 'سجّل دخولك بالبصمة'
            : 'Login with fingerprint',
        biometricOnly: true,
      );
      if (!didAuth) return;
      final savedUser = await _secureStorage.read(key: 'saved_username');
      final savedPass = await _secureStorage.read(key: 'saved_password');
      if (savedUser == null || savedPass == null) return;
      _usernameController.text = savedUser;
      _passwordController.text = savedPass;
      
      if (!mounted) return;
      if (widget.onLoginSuccess != null) {
        widget.onLoginSuccess!();
      } else {
        Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const MainScreen()));
      }
    } catch (_) {}
  }
  @override
  void dispose() {
    _gyroscopeSubscription?.cancel();
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    setState(() {
      _isLoading = true;
      _errorMessage = '';
    });

    try {
      final inputUser = _usernameController.text.trim();
      final inputPass = _passwordController.text.trim();

      final connectivityResult = await Connectivity().checkConnectivity();
      final isOffline = connectivityResult.contains(ConnectivityResult.none);

      if (isOffline) {
        final savedUser = await _secureStorage.read(key: 'saved_username');
        final savedPass = await _secureStorage.read(key: 'saved_password');
        if (savedUser == inputUser && savedPass == inputPass) {
          if (!mounted) return;
          if (widget.onLoginSuccess != null) {
            widget.onLoginSuccess!();
          } else {
            Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const MainScreen()));
          }
          return;
        } else {
          setState(() {
            _errorMessage = appLanguage.value.languageCode == 'ar' 
                ? 'لا يوجد اتصال بالإنترنت للتحقق من هذا الحساب' 
                : 'No internet connection to verify this account';
          });
          return;
        }
      }

      final success = await SisApiService.login(inputUser, inputPass);

      if (success) {
        if (_saveWithBiometric) {
          await _secureStorage.write(key: 'saved_username', value: _usernameController.text.trim());
          await _secureStorage.write(key: 'saved_password', value: _passwordController.text.trim());
          await _secureStorage.write(key: 'biometric_enabled', value: 'true');
        }
        if (!mounted) return;
        if (widget.onLoginSuccess != null) {
          widget.onLoginSuccess!();
        } else {
          Navigator.of(context).pushReplacement(
            MaterialPageRoute(builder: (_) => const MainScreen()),
          );
        }
      } else {
        setState(() {
          _errorMessage = appLanguage.value.languageCode == 'ar' 
              ? 'بيانات الدخول غير صحيحة' 
              : 'Invalid login credentials';
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = appLanguage.value.languageCode == 'ar' 
            ? 'حدث خطأ في الاتصال' 
            : 'Connection error occurred';
      });
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isAr = appLanguage.value.languageCode == 'ar';

    return Scaffold(
      body: Stack(
        children: [
          // 1. الخلفية التفاعلية المتحركة ببطء شديد
          Positioned.fill(
            child: Image.asset(
              'assets/images/asu_login_footer.jpg',
              fit: BoxFit.cover,
              alignment: Alignment(_sensorX, 0.0),
            ),
          ),
          // 2. طبقة تظليل خفيفة لوضوح النصوص
          Positioned.fill(
            child: Container(
              color: Theme.of(context).scaffoldBackgroundColor.withValues(alpha: 0.78),
            ),
          ),
          // 3. المحتوى
          SafeArea(
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(24.0),
                  child: Align(
                    alignment: isAr ? Alignment.centerLeft : Alignment.centerRight,
                    child: Container(
                      decoration: BoxDecoration(
                        color: Theme.of(context).cardColor.withValues(alpha: 0.9),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: Colors.grey.withValues(alpha: 0.2)),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          _buildLangBtn('العربية', 'ar', isAr),
                          _buildLangBtn('English', 'en', !isAr),
                        ],
                      ),
                    ),
                  ),
                ),
                
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Text(
                          isAr ? 'تسجيل الدخول' : 'Login',
                          textAlign: TextAlign.center,
                          style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 40),
                        
                        Container(
                          decoration: BoxDecoration(
                            color: Theme.of(context).cardColor.withValues(alpha: 0.9),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: TextField(
                            controller: _usernameController,
                            decoration: InputDecoration(
                              labelText: isAr ? 'الرقم الجامعي' : 'Student ID',
                              prefixIcon: const Icon(Icons.person),
                              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),

                        Container(
                          decoration: BoxDecoration(
                            color: Theme.of(context).cardColor.withValues(alpha: 0.9),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: TextField(
                            controller: _passwordController,
                            obscureText: true,
                            decoration: InputDecoration(
                              labelText: isAr ? 'الرمز السري' : 'Password',
                              prefixIcon: const Icon(Icons.lock),
                              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                            ),
                          ),
                        ),

                        if (_biometricAvailable)
                          Padding(
                            padding: const EdgeInsets.only(top: 8),
                            child: Row(
                              children: [
                                Checkbox(
                                  value: _saveWithBiometric,
                                  onChanged: (v) => setState(() => _saveWithBiometric = v ?? false),
                                ),
                                Expanded(
                                  child: Text(
                                    isAr ? 'حفظ الرمز السري وربطه بالبصمة' : 'Save password & link to fingerprint',
                                    style: const TextStyle(fontSize: 12),
                                  ),
                                ),
                              ],
                            ),
                          ),

                        const SizedBox(height: 8),

                        if (_errorMessage.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 16),
                            child: Text(_errorMessage, style: const TextStyle(color: Colors.red, fontWeight: FontWeight.bold), textAlign: TextAlign.center),
                          ),

                        // زر تسجيل الدخول بالشعار الدائري وخيار البصمة
                        Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              GestureDetector(
                                onTap: _isLoading ? null : _handleLogin,
                                child: _isLoading
                                    ? const CircularProgressIndicator()
                                    : Container(
                                        padding: const EdgeInsets.all(4),
                                        decoration: BoxDecoration(
                                          shape: BoxShape.circle,
                                          color: Theme.of(context).cardColor.withValues(alpha: 0.9),
                                          boxShadow: [
                                            BoxShadow(color: Colors.black.withValues(alpha: 0.3), blurRadius: 12, spreadRadius: 2)
                                          ]
                                        ),
                                        child: ClipOval(
                                          child: Image.asset(
                                            'assets/images/asu_logo.png',
                                            height: 75,
                                            width: 75,
                                            fit: BoxFit.cover,
                                            errorBuilder: (ctx, err, stack) => CircleAvatar(
                                              radius: 35,
                                              backgroundColor: Theme.of(context).primaryColor,
                                              child: const Icon(Icons.arrow_forward, color: Colors.white, size: 30),
                                            ),
                                          ),
                                        ),
                                      ),
                              ),
                              const SizedBox(height: 12),
                              if (_biometricAvailable)
                                TextButton.icon(
                                  onPressed: _isLoading ? null : _tryBiometricLogin,
                                  icon: const Icon(Icons.fingerprint, color: Colors.grey, size: 22),
                                  label: Text(
                                    isAr ? 'الدخول بالبصمة' : 'Fingerprint Login',
                                    style: const TextStyle(color: Colors.grey, fontSize: 12),
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLangBtn(String title, String code, bool isActive) {
    return GestureDetector(
      onTap: () => appLanguage.value = Locale(code),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isActive ? Theme.of(context).primaryColor : Colors.transparent,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          title,
          style: TextStyle(
            color: isActive ? Colors.white : Colors.grey,
            fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
          ),
        ),
      ),
    );
  }
}