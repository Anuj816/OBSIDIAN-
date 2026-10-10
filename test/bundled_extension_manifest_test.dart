import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('bundled extension manifest has unique IDs and SHA-256 digests', () async {
    final decoded = jsonDecode(
      await rootBundle.loadString('assets/bundled_extensions/manifest.json'),
    ) as Map<String, dynamic>;
    final extensions = decoded['extensions'] as List<dynamic>;

    expect(extensions, isNotEmpty);
    final ids = <String>{};
    for (final raw in extensions) {
      final entry = raw as Map<String, dynamic>;
      final id = entry['id'] as String;
      expect(id, isNotEmpty);
      expect(ids.add(id), isTrue, reason: 'duplicate extension ID: $id');
      expect(entry['filename'], endsWith('.sflx'));
      expect(entry['sha256'], matches(RegExp(r'^[a-f0-9]{64}$')));
    }
  });
}
