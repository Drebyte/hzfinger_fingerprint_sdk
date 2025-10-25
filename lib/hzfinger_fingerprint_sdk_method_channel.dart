import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'hzfinger_fingerprint_sdk_platform_interface.dart';

/// An implementation of [HzfingerFingerprintSdkPlatform] that uses method channels.
class MethodChannelHzfingerFingerprintSdk extends HzfingerFingerprintSdkPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('hzfinger_fingerprint_sdk');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
