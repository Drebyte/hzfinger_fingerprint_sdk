import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/services.dart';

// --- Channel Names ---
const MethodChannel _methodChannel =
    MethodChannel('hzfinger_fingerprint_sdk/method');
const EventChannel _eventChannel =
    EventChannel('hzfinger_fingerprint_sdk/event');

/// Represents a single event from the fingerprint scanner.
/// [type] can be "status", "image", or "iso_template".
/// If "status", [message] will be populated.
/// If "image" or "iso_template", [data] will be populated.
/// "iso_template" events might also have a [message].
class FingerprintEvent {
  final String type;
  final String? message;
  final Uint8List? data;

  FingerprintEvent(this.type, this.message, this.data);
}

class HzfingerFingerprintSdk {
  static Stream<FingerprintEvent>? _fingerprintStream;

  /// A broadcast stream of events from the fingerprint scanner.
  ///
  /// Listen for [FingerprintEvent]s:
  /// * `event.type == "status"`: A status message. Check `event.message`.
  /// * `event.type == "image"`: A captured image (PNG format). Check `event.data`.
  /// * `event.type == "iso_template"`: A captured ISO template. Check `event.data` and potentially `event.message`.
  static Stream<FingerprintEvent> get fingerprintEvents {
    _fingerprintStream ??=
        _eventChannel.receiveBroadcastStream().map((dynamic event) {
      // Ensure the event is a Map before proceeding
      if (event is! Map) {
         // Handle unexpected event format, maybe log or return a default error event
         print("Received unexpected event format: $event");
         return FingerprintEvent("status", "Error: Unexpected event format", null);
      }
      final Map<dynamic, dynamic> map = event;
      final String type = map['type'] as String? ?? 'status'; // Default to status if type is missing
      String? message;
      Uint8List? data;

      message = map['message'] as String?; // Message can exist for any type

      if (type == 'image' || type == 'iso_template') {
        // Handle potential type mismatches for data
        var rawData = map['data'];
        if (rawData is List<int>) {
          data = Uint8List.fromList(rawData);
        } else if (rawData is Uint8List) {
          data = rawData;
        } else if (rawData != null) {
           // Log unexpected data type if necessary
           print("Received unexpected data type for event '$type': ${rawData.runtimeType}");
        }
      }
      return FingerprintEvent(type, message, data);
    });
    return _fingerprintStream!;
  }

  /// Initializes the fingerprint scanner.
  ///
  /// This must be called after granting storage permissions.
  /// Copies model files, sets up USB, and opens the device.
  /// Returns `true` on success, throws an Exception on failure.
  static Future<bool> init() async {
    try {
      final bool? success = await _methodChannel.invokeMethod('init');
      return success ?? false;
    } on PlatformException catch (e) {
      // Pass the native error message to the caller
      throw Exception("Failed to init: ${e.message}");
    } catch (e) {
       // Catch other potential errors
       throw Exception("Failed to init: ${e.toString()}");
    }
  }

  /// Starts a manual fingerprint capture.
  ///
  /// Results (image and template) will be sent as [FingerprintEvent]s
  /// on the [fingerprintEvents] stream.
  static Future<void> startCapture() async {
    try {
      await _methodChannel.invokeMethod('capture');
    } on PlatformException catch (e) {
       throw Exception("Failed startCapture: ${e.message}");
    } catch (e) {
       throw Exception("Failed startCapture: ${e.toString()}");
    }
  }

  /// Starts automatic fingerprint monitoring.
  ///
  /// The plugin will continuously check for a finger. When detected,
  /// it captures the image and template, sending results on the
  /// [fingerprintEvents] stream.
  static Future<void> startMonitoring() async {
     try {
      await _methodChannel.invokeMethod('startMonitoring');
    } on PlatformException catch (e) {
       throw Exception("Failed startMonitoring: ${e.message}");
    } catch (e) {
       throw Exception("Failed startMonitoring: ${e.toString()}");
    }
  }

  /// Stops automatic fingerprint monitoring.
  static Future<void> stopMonitoring() async {
    try {
      await _methodChannel.invokeMethod('stopMonitoring');
    } on PlatformException catch (e) {
       throw Exception("Failed stopMonitoring: ${e.message}");
    } catch (e) {
       throw Exception("Failed stopMonitoring: ${e.toString()}");
    }
  }


  /// Compares two ISO fingerprint templates.
  ///
  /// Returns a similarity score (0-100).
  /// Returns -1 or throws an Exception on failure.
  static Future<int> compareTemplates({
    required Uint8List template1,
    required Uint8List template2,
  }) async {
    try {
      final int? score = await _methodChannel.invokeMethod('compareTemplates', {
        'template1': template1,
        'template2': template2,
      });
      return score ?? -1; // Return -1 if score is null (indicating potential native error)
    } on PlatformException catch (e) {
       print("PlatformException during compareTemplates: ${e.message}");
       throw Exception("Failed to compare: ${e.message}");
    } catch (e) {
       print("Exception during compareTemplates: ${e.toString()}");
       throw Exception("Failed to compare: ${e.toString()}");
    }
  }


  /// Starts an enrollment process for a given [userId].
  ///
  /// Status updates will be sent on the [fingerprintEvents] stream.
  static Future<void> enroll(String userId) async {
     try {
       await _methodChannel.invokeMethod('enroll', {'userId': userId});
     } on PlatformException catch (e) {
       throw Exception("Failed enroll: ${e.message}");
    } catch (e) {
       throw Exception("Failed enroll: ${e.toString()}");
    }
  }

  /// Closes the connection to the fingerprint scanner
  /// and stops monitoring if active.
  static Future<void> close() async {
     try {
       await _methodChannel.invokeMethod('close');
     } on PlatformException catch (e) {
       // Log error but might not need to throw, closing is best-effort
       print("Error during close: ${e.message}");
    } catch (e) {
       print("Error during close: ${e.toString()}");
    }
  }
}

