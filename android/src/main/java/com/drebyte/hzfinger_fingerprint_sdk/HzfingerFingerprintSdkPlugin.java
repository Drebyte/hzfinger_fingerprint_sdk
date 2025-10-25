package com.drebyte.hzfinger_fingerprint_sdk;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

// Import the vendor's Java classes
import com.HZFINGER.HAPI;
import com.HZFINGER.LAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean; // Added for thread control

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

// --- Message Constants (from HZFinger_FpStdSample.java) ---
interface PluginMessages {
    int MSG_SHOW_TEXT = 1;
    int MSG_SHOW_IMAGE = 2;
    int MSG_SHOW_ERR = 3;
    int MSG_SHOW_STATUS = 4;
    int MSG_SHOW_RAW_IMAGE = 5;
    int MSG_SHOW_WSQ_IMAGE = 6;
    int MSG_SHOW_FPINFO = 7;
    int MSG_OPEN_DEVICE_OK = 8;
    int MSG_OPEN_DEVICE_FAIL = 9;
    int MSG_CLOSE_DEVICE = 10;
    int MSG_USBREQ_SUCCESS = 11;
    int MSG_USBREQ_FAIL = 12;
}

public class HzfingerFingerprintSdkPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler, ActivityAware {

    // --- Channel Names ---
    private static final String METHOD_CHANNEL_NAME = "hzfinger_fingerprint_sdk/method";
    private static final String EVENT_CHANNEL_NAME = "hzfinger_fingerprint_sdk/event";
    private static final String TAG = "HZFingerPlugin";

    private MethodChannel methodChannel;
    private EventChannel eventChannel;
    private EventChannel.EventSink eventSink;

    private Context context;
    private Activity activity;
    private LAPI lapi;
    private HAPI hapi;
    private Handler pluginHandler;

    private long m_hDev = 0;

    // --- Added for Monitoring Thread ---
    private Thread monitoringThread = null;
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    // --- End Monitoring ---

    // --- HELPER FUNCTION FOR REFLECTION (SIMPLIFIED) ---
    static void setFinalStatic(Field field, Object newValue) throws Exception {
       field.setAccessible(true);
       field.set(null, newValue);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.context = flutterPluginBinding.getApplicationContext();

        methodChannel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), METHOD_CHANNEL_NAME);
        methodChannel.setMethodCallHandler(this);

        eventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), EVENT_CHANNEL_NAME);
        eventChannel.setStreamHandler(this);

        pluginHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                // This handler is now mostly used by HAPI (enrollment),
                // but we keep it for general status messages.
                // Capture results are sent directly via sendEvent.
                if (eventSink == null) return;

                Map<String, Object> event = new HashMap<>();
                switch (msg.what) {
                    case PluginMessages.MSG_SHOW_TEXT:
                    case PluginMessages.MSG_SHOW_ERR:
                    case PluginMessages.MSG_SHOW_STATUS:
                        event.put("type", "status");
                        event.put("message", (String) msg.obj);
                        eventSink.success(event);
                        break;
                    // MSG_SHOW_IMAGE is now handled directly in capture/monitoring logic
                    case PluginMessages.MSG_OPEN_DEVICE_OK:
                        event.put("type", "status");
                        event.put("message", "Device opened successfully");
                        eventSink.success(event);
                        break;
                    case PluginMessages.MSG_OPEN_DEVICE_FAIL:
                        event.put("type", "status");
                        event.put("message", "Failed to open device");
                        eventSink.success(event);
                        break;
                    // ... handle other messages if needed
                }
            }
        };
    }

    // This helper function sends events back to Flutter from any thread.
    private void sendEvent(String type, String message, byte[] data) {
        if (eventSink == null || activity == null) return;

        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        if (message != null) {
            event.put("message", message);
        }
        if (data != null) {
            event.put("data", data);
        }

        // Post to the main thread to send the event safely
        activity.runOnUiThread(() -> {
            if (eventSink != null) {
                eventSink.success(event);
            }
        });
    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (activity == null || lapi == null || hapi == null) {
            result.error("NOT_ATTACHED", "Plugin is not attached to an activity or SDKs are not initialized.", null);
            return;
        }
        // Ensure device is initialized for methods other than init/close
        if (!call.method.equals("init") && !call.method.equals("close") && m_hDev == 0) {
             result.error("NOT_INITIALIZED", "Device not initialized. Call init() first.", null);
             return;
        }


        switch (call.method) {
            case "init":
                // Run init on a background thread
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // 1. Copy assets
                        copyAssetsToStorage();

                        // 2. REFLECTION FIX
                        File modelDir = context.getExternalFilesDir(null);
                        if (modelDir == null) {
                            activity.runOnUiThread(() -> result.error("STORAGE_ERROR", "Cannot access external files directory", null));
                            return;
                        }

                        try {
                            String correctModelPath = modelDir.getAbsolutePath();
                            Field pathField = LAPI.class.getDeclaredField("Model_FolderPath");
                            setFinalStatic(pathField, correctModelPath);
                            Log.d(TAG, "Successfully overrode Model_FolderPath to: " + correctModelPath);

                        } catch (Exception e) {
                            Log.e(TAG, "Reflection failed to override Model_FolderPath", e);
                            activity.runOnUiThread(() -> result.error("REFLECTION_FAILED", "Could not override LAPI.Model_FolderPath", e.getMessage()));
                            return;
                        }

                        // 3. Init LAPI
                        long hDev = lapi.OpenDeviceEx(LAPI.SCSI_MODE);
                        m_hDev = hDev; // Store the handle

                        if (hDev != 0) {
                            activity.runOnUiThread(() -> result.success(true));
                        } else {
                            activity.runOnUiThread(() -> result.error("INIT_FAILED", "Failed to open LAPI device. Check USB permission.", null));
                        }
                    }
                }).start();
                // Return immediately; the thread will send the result
                break;
            case "capture": // Keep manual capture for testing/specific needs
                startCaptureInternal(); // Use the internal method
                result.success(null); // Acknowledge the call
                break;

            // --- ADDED: Start Monitoring ---
            case "startMonitoring":
                if (monitoringThread != null && monitoringThread.isAlive()) {
                    Log.w(TAG, "Monitoring already running.");
                    result.success(null); // Already running, report success
                    return;
                }
                isMonitoring.set(true);
                monitoringThread = new Thread(this::monitoringLoop);
                monitoringThread.start();
                sendEvent("status", "Monitoring started", null);
                result.success(null);
                break;

            // --- ADDED: Stop Monitoring ---
            case "stopMonitoring":
                if (isMonitoring.compareAndSet(true, false)) {
                     // Wait briefly for the thread to finish cleanly
                     if (monitoringThread != null) {
                        try {
                            monitoringThread.join(500); // Wait max 500ms
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            Log.e(TAG, "Interrupted while stopping monitoring thread", e);
                        }
                        monitoringThread = null;
                     }
                    sendEvent("status", "Monitoring stopped", null);
                } else {
                     Log.w(TAG, "Monitoring was not running.");
                }
                result.success(null);
                break;

            case "compareTemplates":
                try {
                    byte[] template1 = call.argument("template1");
                    byte[] template2 = call.argument("template2");

                    if (template1 == null || template2 == null) {
                        result.error("BAD_ARGS", "One or both templates are null", null);
                        return;
                    }

                    // Pad templates back to full size if needed
                    byte[] t1_padded = new byte[LAPI.FPINFO_SIZE];
                    byte[] t2_padded = new byte[LAPI.FPINFO_SIZE];

                    System.arraycopy(template1, 0, t1_padded, 0, Math.min(template1.length, t1_padded.length));
                    System.arraycopy(template2, 0, t2_padded, 0, Math.min(template2.length, t2_padded.length));


                    int score = lapi.CompareTemplates(m_hDev, t1_padded, t2_padded);

                    result.success(score);

                } catch (Exception e) {
                    result.error("COMPARE_FAILED", e.getMessage(), null);
                }
                break;

            case "enroll":
                final String userId = call.argument("userId");
                if (userId == null) {
                    result.error("BAD_ARGS", "Missing 'userId'", null);
                    return;
                }
                pluginHandler.obtainMessage(PluginMessages.MSG_SHOW_TEXT, "Enrollment started for " + userId).sendToTarget();

                // TODO: Start your enrollment logic thread here

                result.success(null);
                break;
            case "close":
                 // --- Stop monitoring if running ---
                if (isMonitoring.compareAndSet(true, false)) {
                    if (monitoringThread != null) {
                        try {
                            monitoringThread.join(500); // Wait max 500ms
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        monitoringThread = null;
                    }
                }
                // --- End Stop Monitoring ---

                if (lapi != null && m_hDev != 0) {
                    lapi.CloseDeviceEx(m_hDev);
                    m_hDev = 0; // Set handle to 0 so loops stop
                }
                result.success(true);
                break;
            default:
                result.notImplemented();
        }
    }

    // --- ADDED: Monitoring Loop ---
    private void monitoringLoop() {
        Log.d(TAG, "Monitoring thread started.");
        byte[] img = new byte[LAPI.IMAGE_SIZE];
        while (isMonitoring.get()) {
            if (m_hDev == 0) {
                Log.w(TAG, "Device handle is 0, stopping monitoring loop.");
                isMonitoring.set(false);
                break; // Exit if device closed
            }

            // GetImage might block briefly, which is fine for a background thread
            int getImageResult = lapi.GetImage(m_hDev, img);

            if (!isMonitoring.get()) break; // Check again after potentially blocking call

            if (getImageResult == LAPI.TRUE) {
                // Potential finger image captured, check if it's actually a finger
                 // According to LAPI.java IsPressFingerEx checks liveness
                int pressScore = lapi.IsPressFingerEx(m_hDev, img, true, LAPI.LIVECHECK_THESHOLD[2]); // Using threshold index 2 (0.1f) as example

                if (!isMonitoring.get()) break;

                if (pressScore >= LAPI.DEF_FINGER_SCORE) {
                    // Finger detected! Process it.
                    Log.d(TAG, "Finger detected with score: " + pressScore);
                    processCapturedImage(img); // Process the image (PNG + ISO)

                    // Optional: Pause monitoring briefly after successful capture
                    // to avoid immediate re-captures of the same placement.
                    try {
                        Thread.sleep(500); // Pause for 500ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break; // Exit loop if interrupted
                    }

                } else if (pressScore == LAPI.FAKEFINGER) {
                     sendEvent("status", "Fake finger detected", null);
                     // Optionally clear the image buffer here if needed
                }
                 // If score is low or fake, just loop again without processing

            } else if (getImageResult == LAPI.NOTCALIBRATED) {
                 sendEvent("status", "Device not calibrated", null);
                 isMonitoring.set(false); // Stop monitoring if calibration needed
                 break;
            } else {
                 // No finger or error during GetImage (result is FALSE or other error)
                 // Do nothing, just loop again after a short delay
            }

             // Brief pause to prevent high CPU usage when no finger is present
            if (getImageResult != LAPI.TRUE) { // Only sleep if we didn't just process an image
                try {
                    Thread.sleep(100); // Check every 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // Exit loop if interrupted
                }
            }
        }
        Log.d(TAG, "Monitoring thread finished.");
    }

    // --- ADDED: Internal Manual Capture Trigger ---
    private void startCaptureInternal() {
         new Thread(() -> {
             byte[] img = new byte[LAPI.IMAGE_SIZE];
             sendEvent("status", "Place finger on scanner...", null);

             // Loop until GetImage returns 1 (LAPI.TRUE)
             while (lapi.GetImage(m_hDev, img) != LAPI.TRUE) {
                 if (m_hDev == 0) {
                     Log.d(TAG, "Device closed, stopping capture loop.");
                     return;
                 }
                 try {
                     Thread.sleep(100);
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     return;
                 }
             }
             if (m_hDev == 0) return;

             // Check liveness after successful GetImage
             int pressScore = lapi.IsPressFingerEx(m_hDev, img, true, LAPI.LIVECHECK_THESHOLD[2]);
             if (pressScore >= LAPI.DEF_FINGER_SCORE) {
                 processCapturedImage(img);
             } else if (pressScore == LAPI.FAKEFINGER) {
                 sendEvent("status", "Fake finger detected during capture", null);
             } else {
                 sendEvent("status", "Finger lifted too quickly during capture", null);
             }
         }).start();
    }


    // --- ADDED: Helper to process image (PNG + ISO) ---
    private void processCapturedImage(byte[] img) {
        // --- Create PNG ---
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Bitmap bitmap = null;
        try {
            int width = LAPI.WIDTH;
            int height = LAPI.HEIGHT;

            int[] rgbBits = new int[width * height];
            for (int i = 0; i < width * height; i++) {
                int v = img[i] & 0xff;
                rgbBits[i] = Color.rgb(v, v, v);
            }

            bitmap = Bitmap.createBitmap(rgbBits, width, height, Bitmap.Config.ARGB_8888);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] pngData = stream.toByteArray();

            // Send PNG data back via helper
            sendEvent("image", null, pngData);

        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // --- Create ISO Template ---
        byte[] isoTemplateBuffer = new byte[LAPI.FPINFO_SIZE];
        // Ensure device is still valid before native call
        if (m_hDev == 0) {
             Log.w(TAG,"Device closed before creating ISO template.");
             return;
        }
        int templateSize = lapi.CreateISOTemplate(m_hDev, img, isoTemplateBuffer);

        if (templateSize > 0) {
            byte[] finalTemplate = new byte[templateSize];
            System.arraycopy(isoTemplateBuffer, 0, finalTemplate, 0, templateSize);
            sendEvent("iso_template", "ISO Template Created", finalTemplate);
        } else {
            // Log the error code if possible, or just a generic message
             Log.e(TAG, "CreateISOTemplate failed with result: " + templateSize);
            sendEvent("status", "Failed to create ISO template (Error code: " + templateSize + ")", null);
        }
    }


    // --- EventChannel.StreamHandler Implementation ---
    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        this.eventSink = events;
    }

    @Override
    public void onCancel(Object arguments) {
        this.eventSink = null;
    }

    // --- Utility Functions ---
    private void copyAssetsToStorage() {
        AssetManager assetManager = context.getAssets();
        String modelDir = "HZFinger_DNN_Model";
        File targetDir = context.getExternalFilesDir(null);
        if (targetDir == null) {
            sendEvent("status", "Cannot access external files directory", null);
            return;
        }

        try {
            String[] modelFiles = assetManager.list(modelDir);
            if (modelFiles != null) {
                for (String filename : modelFiles) {
                    File targetFile = new File(targetDir, filename);
                    if (!targetFile.exists()) {
                        try (InputStream in = assetManager.open(modelDir + "/" + filename);
                             OutputStream out = new FileOutputStream(targetFile)) {
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            sendEvent("status", "Could not copy model files", null);
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
        // Stop monitoring when engine detaches
        if (isMonitoring.compareAndSet(true, false)) {
            if (monitoringThread != null) {
                monitoringThread.interrupt(); // Interrupt sleep/wait
                monitoringThread = null;
            }
        }
    }

    // --- ActivityAware Implementation ---
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        lapi = new LAPI(this.activity);
        hapi = new HAPI(this.activity, pluginHandler);
    }

    @Override
    public void onDetachedFromActivity() {
         // Stop monitoring when activity detaches
        if (isMonitoring.compareAndSet(true, false)) {
            if (monitoringThread != null) {
                monitoringThread.interrupt(); // Interrupt sleep/wait
                 // Don't wait with join here, cleanup needs to be fast
                monitoringThread = null;
            }
        }
        // Close device
        if (lapi != null && m_hDev != 0) {
            lapi.CloseDeviceEx(m_hDev);
            m_hDev = 0;
        }
        lapi = null;
        hapi = null;
        this.activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding); // Re-attach
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity(); // Detach
    }
}

