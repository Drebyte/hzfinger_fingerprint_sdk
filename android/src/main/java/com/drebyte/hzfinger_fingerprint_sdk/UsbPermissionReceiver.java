package com.drebyte.hzfinger_fingerprint_sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// This class is required by the AndroidManifest to handle the
// USB permission broadcast from HostUsb.java
public class UsbPermissionReceiver extends BroadcastReceiver {
    private static final String TAG = "HZFingerPluginReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        // We don't need to do anything here;
        // The LAPI's internal HostUsb class will handle the result.
        Log.d(TAG, "UsbPermissionReceiver onReceive called");
    }
}

