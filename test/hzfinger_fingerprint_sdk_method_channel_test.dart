import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hzfinger_fingerprint_sdk/hzfinger_fingerprint_sdk_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelHzfingerFingerprintSdk platform = MethodChannelHzfingerFingerprintSdk();
  const MethodChannel channel = MethodChannel('hzfinger_fingerprint_sdk');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
