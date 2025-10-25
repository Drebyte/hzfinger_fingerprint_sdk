import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:typed_data'; // Added for image data

import 'package:flutter/services.dart';
// v-- Import matches your project
import 'package:hzfinger_fingerprint_sdk/hzfinger_fingerprint_sdk.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Plugin Example App',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'HZFinger Plugin Example'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  String _statusMessage = 'Not Initialized';
  Uint8List? _fingerImage;
  Uint8List? _currentIsoTemplate; // To store the latest template
  Uint8List? _savedIsoTemplate; // To store a template for comparison
  StreamSubscription<FingerprintEvent>? _eventSubscription;
  bool _isMonitoring = false; // Track monitoring state

  @override
  void initState() {
    super.initState();
    initializeScanner();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    HzfingerFingerprintSdk.close(); // Close also stops monitoring
    super.dispose();
  }

  void initializeScanner() async {
    // Request BOTH storage permissions
    var storageStatus = await Permission.storage.request();
    // Only request manageExternalStorage on Android
    var manageStatus = await Permission.manageExternalStorage.request();


    if (storageStatus.isGranted && manageStatus.isGranted) {
      try {
        bool success = await HzfingerFingerprintSdk.init();
        if (success && mounted) {
          print("Scanner Initialized!");
          setState(() {
            _statusMessage = "Scanner Initialized!";
          });

          // Listen to events
          _eventSubscription =
              HzfingerFingerprintSdk.fingerprintEvents.listen((event) {
            if (!mounted) return;
            setState(() {
              // Always update status message if available
              if(event.message != null && event.message!.isNotEmpty){
                 _statusMessage = event.message!;
              }

              // Handle specific event types
              if (event.type == "image") {
                print("Got image with ${event.data?.length} bytes");
                _fingerImage = event.data;
                // Optionally update status only if no specific message came with image
                if(event.message == null || event.message!.isEmpty) {
                   _statusMessage = "Image Captured!";
                }
              } else if (event.type == "iso_template") {
                print("Got ISO template with ${event.data?.length} bytes");
                _currentIsoTemplate = event.data;
                 // Optionally update status only if no specific message came with template
                 if(event.message == null || event.message!.isEmpty) {
                   _statusMessage = 'ISO Template Created';
                 }
                // Optionally clear the image after getting the template
                // _fingerImage = null;
              } else if (event.type == "status") {
                 print("Status: ${event.message}");
                 // Status message is already handled above
              }
            });
          });
        } else {
          print("Failed to initialize scanner (false returned).");
          if (mounted) {
            setState(() {
              _statusMessage = "Failed to initialize scanner (false returned).";
            });
          }
        }
      } catch (e) {
        print("Failed to init device: '$e'.");
        if (mounted) {
          setState(() {
            // Display the specific exception message from init()
             _statusMessage = e.toString();
          });
        }
      }
    } else {
      print("Storage and All Files Access permissions are required.");
      if (mounted) {
        setState(() {
          _statusMessage =
              "Storage and All Files Access permissions are required.";
        });
      }
    }
  }

  // Start/Stop Monitoring
  Future<void> _toggleMonitoring() async {
    try {
      if (_isMonitoring) {
        await HzfingerFingerprintSdk.stopMonitoring();
        if (mounted) {
          setState(() {
            _isMonitoring = false;
            // Status is usually updated by the plugin event,
            // but set a default if needed
             _statusMessage = "Monitoring Stopped";
          });
        }
      } else {
        // Clear previous results before starting
         setState(() {
           _fingerImage = null;
           _currentIsoTemplate = null;
           _statusMessage = "Starting monitoring...";
         });
        await HzfingerFingerprintSdk.startMonitoring();
         if (mounted) {
           setState(() {
            _isMonitoring = true;
            _statusMessage = "Monitoring... Place finger";
           });
         }
      }
    } catch(e) {
       if (mounted) {
          setState(() {
             _statusMessage = "Error toggling monitoring: ${e.toString()}";
             _isMonitoring = false; // Ensure state reflects failure
          });
       }
    }
  }

  // Save Template
  void _saveTemplate() {
    if (_currentIsoTemplate != null) {
      setState(() {
        _savedIsoTemplate = _currentIsoTemplate;
        _statusMessage =
            'Template Saved (${_savedIsoTemplate!.lengthInBytes} bytes)';
      });
    } else {
       setState(() {
         _statusMessage = 'No current template to save';
       });
    }
  }

  // Compare Templates
  Future<void> _compareTemplates() async {
    if (_currentIsoTemplate != null && _savedIsoTemplate != null) {
      try {
        setState(() {
          _statusMessage = 'Comparing templates...';
        });
        int score = await HzfingerFingerprintSdk.compareTemplates(
          template1: _savedIsoTemplate!,
          template2: _currentIsoTemplate!,
        );
        if (mounted) {
          setState(() {
            _statusMessage = 'Comparison Score: $score';
          });
        }
      } catch (e) {
        if (mounted) {
          setState(() {
            _statusMessage = 'Comparison failed: ${e.toString()}';
          });
        }
      }
    } else {
       setState(() {
         _statusMessage = 'Need both a saved and current template to compare';
       });
    }
  }

  // Manual Capture Trigger (Optional)
  Future<void> _manualCapture() async {
     try {
       setState(() {
         _fingerImage = null;
         _currentIsoTemplate = null;
         _statusMessage = 'Manual Capture: Place finger...';
       });
       await HzfingerFingerprintSdk.startCapture();
     } catch(e) {
        if(mounted){
           setState((){
              _statusMessage = 'Manual capture failed: ${e.toString()}';
           });
        }
     }
  }


  @override
  Widget build(BuildContext context) {
    // Helper to display template info
    Widget templateInfo(String title, Uint8List? template) {
      return Expanded( // Use Expanded to divide space
        child: Column(
          children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.bold), textAlign: TextAlign.center,),
            Text(template != null
                ? '${template.lengthInBytes} bytes'
                : 'N/A', textAlign: TextAlign.center,),
            // Optionally display first few bytes:
             Text(template != null ? template.take(8).map((b) => b.toRadixString(16).padLeft(2, '0')).join(' ') : '',
                  style: const TextStyle(fontSize: 10),
                  textAlign: TextAlign.center,
                  overflow: TextOverflow.ellipsis,
             ),
          ],
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: SingleChildScrollView(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                Text(
                  'Status:',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Container( // Container for status to prevent layout shifts
                   height: 40, // Give it a fixed height
                   child: Text(
                     _statusMessage,
                     textAlign: TextAlign.center,
                     style: Theme.of(context).textTheme.bodyLarge,
                   ),
                ),
                const SizedBox(height: 10),
                Container(
                  width: 200,
                  height: 250,
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.grey),
                    borderRadius: BorderRadius.circular(8),
                    color: Colors.black12,
                  ),
                  child: _fingerImage != null
                      ? Image.memory(
                          _fingerImage!,
                          fit: BoxFit.contain,
                          gaplessPlayback: true, // Prevent flicker on update
                        )
                      : const Center(
                          child: Text('Fingerprint Image'),
                        ),
                ),
                const SizedBox(height: 15),
                 // Display Template Info
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 10.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    crossAxisAlignment: CrossAxisAlignment.start, // Align tops
                    children: [
                      templateInfo('Current Template:', _currentIsoTemplate),
                      const SizedBox(width: 10), // Add spacing
                      templateInfo('Saved Template:', _savedIsoTemplate),
                    ],
                  ),
                ),
                const SizedBox(height: 25),
                 // Buttons in Rows for better layout
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                     ElevatedButton(
                       style: ElevatedButton.styleFrom(
                         padding: const EdgeInsets.symmetric(
                             horizontal: 20, vertical: 10),
                         backgroundColor: _isMonitoring ? Colors.redAccent : null,
                       ),
                       onPressed: _toggleMonitoring,
                       child: Text(_isMonitoring ? 'Stop Monitoring' : 'Start Monitoring'),
                     ),
                     const SizedBox(width: 10),
                     ElevatedButton(
                        style: ElevatedButton.styleFrom(
                           padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                        ),
                        onPressed: _manualCapture, // Add manual capture button
                        child: const Text('Manual Capture'),
                     ),
                  ],
                ),
                 const SizedBox(height: 12),
                Row(
                   mainAxisAlignment: MainAxisAlignment.center,
                   children: [
                      ElevatedButton(
                         style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10)),
                         onPressed: _currentIsoTemplate == null ? null : _saveTemplate,
                         child: const Text('Save Current'),
                      ),
                      const SizedBox(width: 10),
                      ElevatedButton(
                         style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10)),
                         onPressed: (_currentIsoTemplate == null || _savedIsoTemplate == null) ? null : _compareTemplates,
                         child: const Text('Compare'),
                      ),
                   ],
                ),
                 const SizedBox(height: 12),
                 // Keep Enroll button if needed
                 ElevatedButton(
                   style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                   ),
                   onPressed: () {
                      setState(() {
                         _statusMessage = 'Starting enrollment for user "test_user"...';
                         _fingerImage = null;
                         _currentIsoTemplate = null;
                      });
                      HzfingerFingerprintSdk.enroll("test_user");
                   },
                   child: const Text('Enroll User "test_user"'),
                 ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}