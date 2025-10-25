import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'hzfinger_fingerprint_sdk_method_channel.dart';

abstract class HzfingerFingerprintSdkPlatform extends PlatformInterface {
  /// Constructs a HzfingerFingerprintSdkPlatform.
  HzfingerFingerprintSdkPlatform() : super(token: _token);

  static final Object _token = Object();

  static HzfingerFingerprintSdkPlatform _instance = MethodChannelHzfingerFingerprintSdk();

  /// The default instance of [HzfingerFingerprintSdkPlatform] to use.
  ///
  /// Defaults to [MethodChannelHzfingerFingerprintSdk].
  static HzfingerFingerprintSdkPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [HzfingerFingerprintSdkPlatform] when
  /// they register themselves.
  static set instance(HzfingerFingerprintSdkPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
