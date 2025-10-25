# **HZFinger Fingerprint SDK Plugin (hzfinger\_fingerprint\_sdk)**

A Flutter plugin to integrate with HZFinger fingerprint scanners connected via USB on Android devices. This plugin provides functionalities for:

* Initializing the scanner device.  
* Capturing fingerprint images (as displayable PNGs).  
* Generating ISO fingerprint templates.  
* Comparing two ISO templates for matching.  
* Automatic finger detection and capture via monitoring.  
* (Basic support for enrollment \- requires implementing full enrollment logic based on vendor sample).

**Important:** This plugin acts as a wrapper around the native HZFinger Android SDK (provided by the hardware vendor). You **must** include the necessary vendor-provided native libraries (.so files) and model files (.param, .model, etc.) in your application project for this plugin to function.

## **Platform Support**

* **Android:** Yes (Requires USB Host support on the device)  
* **iOS:** No

## **Installation**

1. Add the dependency to your pubspec.yaml file:  
   dependencies:  
     flutter:  
       sdk: flutter  
     hzfinger\_fingerprint\_sdk: ^1.0.0 \# Use the latest version

2. Run flutter pub get.

## **Obtaining Required Vendor Files (IMPORTANT)**

### ⚠️ Important: Native Libraries & Model Files

This plugin **does not bundle** the proprietary **HZFinger native libraries (`.so` files)** or the **DNN model files** required by the SDK.  
You must obtain these files separately before building or running your app.

#### ✅ Recommended Method

Download the **official HZFinger Android SDK** directly from the hardware vendor.  
This ensures you are using the **correct and most recent versions** compatible with your specific HZFinger device.

#### 📂 File References

* **DNN Model Files:** [View on GitHub →](https://github.com/Drebyte/hzfinger_fingerprint_sdk/tree/master/android/src/main/HZFinger_DNN_Model)  
* **Native `.so` Libraries:** [View on GitHub →](https://github.com/Drebyte/hzfinger_fingerprint_sdk/tree/master/android/src/main/jniLibs)

#### ⚖️ Alternative (Check License First)

For development convenience, these files *may* also be available in the plugin’s repository.  
However, **you must verify** that the **HZFinger SDK license agreement** allows redistribution of these files.  
If unsure, always obtain them **directly from the vendor** to stay compliant.

Once you have obtained the SDK:

* Locate the .so files (usually within libs/ subdirectories like arm64-v8a, armeabi-v7a).  
* Locate the HZFinger\_DNN\_Model folder (containing .deploy, .mean, .model, .param files).

## **Setup (Android Only \- CRITICAL)**

Proper setup on the Android side is essential for this plugin to work. You need to configure permissions, USB filtering, and include the vendor's native files (obtained as described above) in your **application project**.

**1\. Android Manifest (android/app/src/main/AndroidManifest.xml)**

Modify your app's main AndroidManifest.xml file to include the following:

\<manifest xmlns:android="\[http://schemas.android.com/apk/res/android\](http://schemas.android.com/apk/res/android)"
    package="your.application.package"\> \<\!-- CHANGE TO YOUR APP'S PACKAGE NAME \--\>

    \<\!-- a) Required Permissions \--\>  
    \<\!-- Necessary because the native SDK attempts to read /mnt/sdcard \--\>  
    \<uses-permission android:name="android.permission.MANAGE\_EXTERNAL\_STORAGE"/\>  
    \<\!-- Standard storage permissions (might still be needed depending on Android version) \--\>  
    \<uses-permission android:name="android.permission.READ\_EXTERNAL\_STORAGE" /\>  
    \<uses-permission android:name="android.permission.WRITE\_EXTERNAL\_STORAGE" /\>

    \<\!-- b) USB Host Feature Declaration \--\>  
    \<uses-feature android:name="android.hardware.usb.host" android:required="true" /\>

    \<application  
        android:label="Your App Name"  
        android:name="${applicationName}"  
        android:icon="@mipmap/ic\_launcher"\>

        \<activity  
            android:name=".MainActivity"  
            \<\!-- ... other attributes ... \--\>  
            android:exported="true"\>  
            \<\!-- ... your existing intent-filters ... \--\>

            \<\!-- c) Intent Filter for USB Device Attachment \--\>  
            \<\!-- Allows your app to be notified or launched when the scanner is plugged in \--\>  
            \<intent-filter\>  
                \<action android:name="android.hardware.usb.action.USB\_DEVICE\_ATTACHED" /\>  
            \</intent-filter\>

            \<\!-- d) Meta-data linking the filter to the device\_filter.xml \--\>  
            \<meta-data android:name="android.hardware.usb.action.USB\_DEVICE\_ATTACHED"  
                android:resource="@xml/device\_filter" /\>

        \</activity\>

        \<\!-- ... other application elements ... \--\>

    \</application\>  
    \<\!-- Required to query activities that can process USB\_DEVICE\_ATTACHED \--\>  
    \<queries\>  
        \<intent\>  
            \<action android:name="android.hardware.usb.action.USB\_DEVICE\_ATTACHED" /\>  
        \</intent\>  
    \</queries\>  
\</manifest\>

**Key Changes Explained:**

* **a) Permissions:** MANAGE\_EXTERNAL\_STORAGE is unfortunately required because the underlying native HZFinger library seems hardcoded to access /mnt/sdcard for model files, even though the plugin copies them to the app's private external storage. Standard storage permissions are also included for broader compatibility. Your app will need to request these permissions at runtime using a package like permission\_handler.  
* **b) USB Host Feature:** Declares that your app uses USB Host mode. android:required="true" means the app can only be installed on devices supporting USB Host.  
* **c) Intent Filter:** Allows your MainActivity (or whichever activity is specified) to receive an intent when a USB device matching the filter is attached.  
* **d) Meta-data:** Links the intent filter to the device\_filter.xml file you will create next.

**2\. USB Device Filter (android/app/src/main/res/xml/device\_filter.xml)**

Create this directory and file if they don't exist. This file tells Android which specific USB device(s) your app is interested in.

* **Path:** android/app/src/main/res/xml/device\_filter.xml  
* **Content:**  
  \<?xml version="1.0" encoding="utf-8"?\>  
  \<resources\>  
      \<\!-- HZFinger Devices \--\>  
      \<usb-device vendor-id="10473" product-id="655" /\>  \<\!-- Identified from logs \--\>  
      \<usb-device vendor-id="1155"  product-id="22288"/\> \<\!-- Added from user input \--\>  
      \<usb-device vendor-id="1155"  product-id="22304"/\> \<\!-- Added from user input \--\>  
      \<\!-- Add other vendor/product IDs if your hardware varies \--\>  
  \</resources\>

  *(Verify the vendor-id and product-id values match your specific HZFinger hardware model(s). You can find these using tools on your development machine or sometimes via Android system logs when plugging in the device).*

**3\. Native Libraries (.so files)**

Using the .so files you obtained from the vendor:

Copy libbiofp\_e\_lapi.so, libcheckLive.so, and libFingerILA.so into your **application's** jniLibs directory, creating subdirectories for each required ABI (Android Binary Interface). At a minimum, you'll likely need arm64-v8a and armeabi-v7a:

* android/app/src/main/jniLibs/arm64-v8a/libbiofp\_e\_lapi.so  
* android/app/src/main/jniLibs/arm64-v8a/libcheckLive.so  
* android/app/src/main/jniLibs/arm64-v8a/libFingerILA.so  
* android/app/src/main/jniLibs/armeabi-v7a/libbiofp\_e\_lapi.so  
* android/app/src/main/jniLibs/armeabi-v7a/libcheckLive.so  
* android/app/src/main/jniLibs/armeabi-v7a/libFingerILA.so

*(Add other ABIs like x86\_64 or x86 if you need emulator support or target those architectures).*

**4\. Model Files (assets)**

Using the HZFinger\_DNN\_Model folder you obtained from the vendor:

Copy the **entire HZFinger\_DNN\_Model folder** (containing .deploy, .mean, .model, .param files) into your **application's** assets directory:

* Create the assets folder if it doesn't exist: android/app/src/main/assets  
* Place the model folder inside: android/app/src/main/assets/HZFinger\_DNN\_Model/

The plugin's Java code will automatically copy these files from your app's assets into the app's private storage during the init() call, where the native library expects to find them.

## **Usage**

**1\. Import:**

import 'package:hzfinger\_fingerprint\_sdk/hzfinger\_fingerprint\_sdk.dart';  
import 'package:permission\_handler/permission\_handler.dart'; // For requesting permissions  
import 'dart:typed\_data';

**2\. Request Permissions & Initialize:**

You must request storage permissions *before* initializing the plugin.

Future\<void\> initializeScanner() async {  
  // Request necessary permissions  
  var storageStatus \= await Permission.storage.request();  
  var manageStatus \= await Permission.manageExternalStorage.request();

  if (storageStatus.isGranted && manageStatus.isGranted) {  
    try {  
      bool success \= await HzfingerFingerprintSdk.init();  
      if (success) {  
        print("Scanner Initialized\!");  
        // Now safe to listen to events or start monitoring/capture  
        \_listenToEvents();  
      } else {  
        print("Failed to initialize scanner (plugin returned false).");  
      }  
    } catch (e) {  
      print("Failed to initialize scanner: $e");  
    }  
  } else {  
    print("Storage and All Files Access permissions are required.");  
  }  
}

**3\. Listen to Events:**

The plugin communicates results and status updates via a Stream.

StreamSubscription\<FingerprintEvent\>? eventSubscription;  
Uint8List? latestImage;  
Uint8List? latestTemplate;

void \_listenToEvents() {  
  eventSubscription \= HzfingerFingerprintSdk.fingerprintEvents.listen((event) {  
    if (event.type \== "status") {  
      print("Status Update: ${event.message}");  
      // Update UI with event.message  
    } else if (event.type \== "image") {  
      print("Image Received: ${event.data?.length ?? 0} bytes");  
      // Update UI with event.data (this is a PNG)  
      latestImage \= event.data;  
    } else if (event.type \== "iso\_template") {  
      print("ISO Template Received: ${event.data?.length ?? 0} bytes");  
      // Store event.data (this is the raw ISO template)  
      latestTemplate \= event.data;  
    }  
  });  
}

// Remember to cancel the subscription in your dispose method:  
// @override  
// void dispose() {  
//   eventSubscription?.cancel();  
//   HzfingerFingerprintSdk.close(); // Important to close the device  
//   super.dispose();  
// }

**4\. Start/Stop Monitoring (Auto-Capture):**

Future\<void\> startAutoCapture() async {  
  try {  
    await HzfingerFingerprintSdk.startMonitoring();  
    print("Monitoring started. Place finger on scanner.");  
  } catch (e) {  
    print("Failed to start monitoring: $e");  
  }  
}

Future\<void\> stopAutoCapture() async {  
  try {  
    await HzfingerFingerprintSdk.stopMonitoring();  
    print("Monitoring stopped.");  
  } catch (e) {  
    print("Failed to stop monitoring: $e");  
  }  
}

**5\. Manual Capture (Optional):**

If you don't use monitoring, you can trigger a single capture.

Future\<void\> triggerManualCapture() async {  
  try {  
    print("Manual capture requested. Place finger...");  
    await HzfingerFingerprintSdk.startCapture();  
    // Results (image & template) will arrive via the event stream  
  } catch (e) {  
    print("Failed to start manual capture: $e");  
  }  
}

**6\. Compare Templates:**

Uint8List? savedTemplate; // Store a template from a previous capture

Future\<void\> compareCurrentToSaved() async {  
  if (latestTemplate \!= null && savedTemplate \!= null) {  
    try {  
      print("Comparing templates...");  
      int score \= await HzfingerFingerprintSdk.compareTemplates(  
        template1: savedTemplate\!,  
        template2: latestTemplate\!,  
      );  
      print("Comparison Score: $score");  
      // Update UI with the score  
    } catch (e) {  
      print("Comparison failed: $e");  
    }  
  } else {  
    print("Need two templates (one saved, one current) to compare.");  
  }  
}

// Add UI to save the latestTemplate to savedTemplate  
// e.g., on a button press:  
// void saveCurrentTemplate() {  
//   if (latestTemplate \!= null) {  
//     setState(() { savedTemplate \= latestTemplate; });  
//     print("Template saved.");  
//   }  
// }

**7\. Close Device:**

It's important to close the device connection when your app is done with the scanner, usually in your main widget's dispose method.

// In dispose():  
// eventSubscription?.cancel(); // Already shown above  
// HzfingerFingerprintSdk.close();

## **API Overview**

**Methods:**

* Future\<bool\> init(): Initializes the scanner. Requires permissions first.  
* Future\<void\> startMonitoring(): Starts background monitoring for finger placement.  
* Future\<void\> stopMonitoring(): Stops background monitoring.  
* Future\<void\> startCapture(): Initiates a single manual capture sequence.  
* Future\<int\> compareTemplates({required Uint8List template1, required Uint8List template2}): Compares two ISO templates, returns a score (0-100).  
* Future\<void\> enroll(String userId): (Basic) Starts the enrollment process (requires full implementation based on vendor sample).  
* Future\<void\> close(): Closes the device connection and stops monitoring.

**Events (fingerprintEvents Stream):**

Listen for FingerprintEvent objects:

* event.type \== "status": Contains a status message in event.message.  
* event.type \== "image": Contains captured fingerprint image data (PNG format) in event.data.  
* event.type \== "iso\_template": Contains generated ISO template data in event.data. May also include a status event.message.

## **Important Notes**

* **Vendor SDK Dependency:** This plugin *requires* the specific native libraries and model files from the HZFinger vendor. Ensure you have the correct versions compatible with your hardware. See the "Obtaining Required Vendor Files" section.  
* **Android Only:** This plugin is designed exclusively for Android.  
* **USB Host Mode:** The target Android device must support USB Host mode.  
* **Permissions:** Runtime permissions (Storage, ManageExternalStorage) and Manifest declarations (USB\_Host, intent filters) are critical.  
* **Error Handling:** The plugin attempts to relay errors, but native crashes within the vendor's .so files might still occur and can be hard to debug without native tools.  
* **License:** Ensure your use of the HZFinger SDK complies with the vendor's licensing terms. Redistribution of the .so or model files may require permission.
