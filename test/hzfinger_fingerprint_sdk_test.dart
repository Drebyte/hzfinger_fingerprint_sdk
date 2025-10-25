import 'package:flutter_test/flutter_test.dart';
import 'package:hzfinger_fingerprint_sdk/hzfinger_fingerprint_sdk.dart';
import 'package:hzfinger_fingerprint_sdk/hzfinger_fingerprint_sdk_platform_interface.dart';
import 'package:hzfinger_fingerprint_sdk/hzfinger_fingerprint_sdk_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockHzfingerFingerprintSdkPlatform
    with MockPlatformInterfaceMixin
    implements HzfingerFingerprintSdkPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final HzfingerFingerprintSdkPlatform initialPlatform = HzfingerFingerprintSdkPlatform.instance;

  test('$MethodChannelHzfingerFingerprintSdk is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelHzfingerFingerprintSdk>());
  });

  test('getPlatformVersion', () async {
    HzfingerFingerprintSdk hzfingerFingerprintSdkPlugin = HzfingerFingerprintSdk();
    MockHzfingerFingerprintSdkPlatform fakePlatform = MockHzfingerFingerprintSdkPlatform();
    HzfingerFingerprintSdkPlatform.instance = fakePlatform;

    expect(await hzfingerFingerprintSdkPlugin.getPlatformVersion(), '42');
  });
}
