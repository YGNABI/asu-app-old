import 'package:http/http.dart' as http;
import 'package:html/parser.dart' as html_parser;

class SisApiService {
  static const String loginPageUrl = 'https://sis.asu.edu.bh/ords/f?p=2020:101';
  static const String submitUrl = 'https://sis.asu.edu.bh/ords/wwv_flow.accept';

  static Map<String, String> cookies = {};

  static void _storeCookiesFromResponse(http.Response response) {
    final rawCookies = response.headers['set-cookie'];
    if (rawCookies == null) return;
    final cookieParts = rawCookies.split(',');
    for (var part in cookieParts) {
      final segment = part.split(';').first.trim();
      final kv = segment.split('=');
      if (kv.length >= 2) {
        cookies[kv[0].trim()] = kv.sublist(1).join('=').trim();
      }
    }
  }

  static String _buildCookieHeader() {
    return cookies.entries.map((e) => '${e.key}=${e.value}').join('; ');
  }

  static Future<Map<String, String>> _fetchLoginTokens() async {
    final response = await http.get(Uri.parse(loginPageUrl));
    _storeCookiesFromResponse(response);

    final document = html_parser.parse(response.body);

    String getValue(String name) {
      final input = document.querySelector('input[name="$name"]');
      return input?.attributes['value'] ?? '';
    }

    return {
      'p_flow_id': getValue('p_flow_id'),
      'p_flow_step_id': getValue('p_flow_step_id'),
      'p_instance': getValue('p_instance'),
      'p_page_submission_id': getValue('p_page_submission_id'),
    };
  }

  static Future<bool> login(String username, String password) async {
    cookies.clear();

    final tokens = await _fetchLoginTokens();

    final body = {
      'p_flow_id': tokens['p_flow_id']!,
      'p_flow_step_id': tokens['p_flow_step_id']!,
      'p_instance': tokens['p_instance']!,
      'p_page_submission_id': tokens['p_page_submission_id']!,
      'p_request': 'P101_LOGIN',
      'p_reload_on_submit': 'A',
      'P101_USERNAME': username,
      'P101_PASSWORD': password,
    };

    final response = await http.post(
      Uri.parse(submitUrl),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Cookie': _buildCookieHeader(),
      },
      body: body,
    );

    _storeCookiesFromResponse(response);

    if (response.body.contains('P101_USERNAME')) {
      return false;
    }

    return true;
  }

  static String buildCookieString() => _buildCookieHeader();
}