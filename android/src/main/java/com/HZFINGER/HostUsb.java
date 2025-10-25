package com.HZFINGER;

import java.util.HashMap;
import java.util.Iterator;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

public class HostUsb
{
    private static final String TAG = "OpenHostUsb";
    private static final boolean D = true;

    public static final String ACTION_USB_PERMISSION = "com.HZFINGER.USB_PERMISSION";

    private Context mContext = null;
    private UsbManager mDevManager = null;
    private UsbInterface intf = null;
    private UsbDeviceConnection connection = null;
    private UsbDevice device = null; // Changed from static

    private int m_nEPOutSize = 2048;
    private int m_nEPInSize = 2048;
    private byte[] m_abyTransferBuf = new byte[2048];

    UsbEndpoint endpoint_IN = null;
    UsbEndpoint endpoint_OUT = null;
    UsbEndpoint endpoint_INT = null;
    UsbEndpoint curEndpoint = null;

    private PendingIntent mPermissionIntent; // Moved declaration here

    // --- Added back missing methods and adjusted logic ---

    public HostUsb(Context context) {
        mContext = context;
        mDevManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION),
                                                       android.os.Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED); // Listen for detach
        mContext.registerReceiver(mUsbReceiver, filter);
        Log.e(TAG, "news:mDevManager");
    }

    public void unregisterReceiver() {
        try {
            mContext.unregisterReceiver(mUsbReceiver);
            Log.d(TAG, "HostUsb receiver unregistered.");
        } catch (IllegalArgumentException e) {
            // Receiver was not registered, ignore
            Log.w(TAG, "Receiver not registered or already unregistered.");
        }
    }

    // --- Added AuthorizeDevice ---
    public boolean AuthorizeDevice(int vid, int pid) {
        HashMap<String, UsbDevice> deviceList = mDevManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        boolean bFound = false;

        Log.e(TAG, "AuthorizeDevice:" + String.format(" VID= 0x%04x, PID = 0x%04x", vid, pid));

        // --- Clear previous device state ---
        CloseDeviceInterface(); 
        device = null; 
        // --- End Clear ---

        while (deviceIterator.hasNext()) {
            UsbDevice _device = deviceIterator.next();
            Log.e(TAG, "news:" + _device.toString());

            if ((_device.getVendorId() == vid) && (_device.getProductId() == pid)) {
                device = _device; // Assign found device
                bFound = true;
                break;
            }
        }
        if (!bFound) {
            Log.e(TAG, "Can not find device");
            return false;
        }
        if (mDevManager.hasPermission(device)) {
            Log.e(TAG, "Authorize permission ok!");
            return true;
        } else {
            Log.e(TAG, "Authorize permission request!");
            mDevManager.requestPermission(device, mPermissionIntent);
            // Return false here, WaitForInterfaces will loop until permission is granted or timeout
            return false;
        }
    }

    // --- Added WaitForInterfaces ---
    public boolean WaitForInterfaces() {
        int timeoutMillis = 5000; // Wait up to 5 seconds
        long startTime = System.currentTimeMillis();
        while (device==null || !mDevManager.hasPermission(device)) {
             if (System.currentTimeMillis() - startTime > timeoutMillis) {
                 Log.e(TAG, "WaitForInterfaces timed out waiting for permission.");
                 return false; // Timed out
             }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                 Thread.currentThread().interrupt(); // Re-interrupt thread
                 return false;
            }
        }
        Log.e(TAG, "WaitForInterfaces OK");
        return true;
    }

    // --- Added OpenDeviceInterfaces ---
    // --- MODIFIED: Return File Descriptor ---
    public int OpenDeviceInterfaces() {
        if (device == null) {
            Log.e(TAG, "device is null in OpenDeviceInterfaces");
            return -1;
        }
        // Close existing connection if any before opening a new one
        if (connection != null) {
            CloseDeviceInterface();
        }

        connection = mDevManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Can not connect device (mDevManager.openDevice returned null)");
            // Check if permission was revoked or device disconnected
            if (!mDevManager.hasPermission(device)) {
                 Log.e(TAG, "Permission may have been revoked.");
            }
            return -2;
        }
        if (D) Log.e(TAG, "open connection success!");

        intf = device.getInterface(0);
        if (intf == null) {
             Log.e(TAG, "Could not get interface 0.");
             connection.close(); // Close the connection we just opened
             connection = null;
             return -3;
        }

        if (connection.claimInterface(intf, true)) {
            if (D) Log.e(TAG, "claim interface success!");
        } else {
            Log.e(TAG, "claim interface fail!");
            connection.close(); // Close the connection
            connection = null;
            intf = null; // Nullify interface as well
            return -4;
        }

        // --- Endpoint search logic (same as before) ---
        endpoint_IN = null; // Reset endpoints
        endpoint_OUT = null;
        endpoint_INT = null;
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint ep = intf.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    endpoint_IN = ep;
                    m_nEPInSize = ep.getMaxPacketSize();
                } else {
                    endpoint_OUT = ep;
                    m_nEPOutSize = ep.getMaxPacketSize();
                }
            } else if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                endpoint_INT = ep;
            }
        }

        if (endpoint_IN == null || endpoint_OUT == null) {
             Log.e(TAG, "Bulk endpoints not found.");
             CloseDeviceInterface(); // Use the correct close method
             return -5;
        }

        // --- Return the actual File Descriptor ---
        int fd = connection.getFileDescriptor();
        Log.d(TAG, "OpenDeviceInterfaces returning file descriptor: " + fd);
        return fd; // Return the native file descriptor
    }

    // --- Added CloseDeviceInterface ---
    public void CloseDeviceInterface() {
        if (connection != null) {
            Log.d(TAG, "Closing USB Connection...");
            if (intf != null) {
                try {
                     // Check if interface is claimed before releasing
                     // Note: Android doesn't directly expose an isClaimed() method.
                     // We assume if intf is not null, it might be claimed.
                     connection.releaseInterface(intf);
                     Log.d(TAG, "Interface released.");
                } catch (Exception e) {
                     Log.e(TAG, "Error releasing interface: " + e.getMessage());
                }
                intf = null;
            }
            connection.close();
            Log.d(TAG, "Connection closed.");
        } else {
            // Log.d(TAG, "Connection was already null in CloseDeviceInterface.");
        }
        // device = null; // Don't nullify device here, only the connection state
        connection = null; // Reset connection
        endpoint_IN = null;
        endpoint_OUT = null;
        endpoint_INT = null;
        Log.d(TAG, "CloseDeviceInterface finished."); // Changed level to Debug
    }


    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    // Make sure we check the device from the intent
                    UsbDevice permDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (permDevice != null && permDevice.equals(device)) { // Check if it's for our current device
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.e(TAG, "Authorize permission GRANTED for device: " + device.getDeviceName());
                            // LAPI's CallBack (via WaitForInterfaces) will detect this and proceed
                        } else {
                            Log.e(TAG, "Authorize permission DENIED for device: " + device.getDeviceName());
                            // Maybe inform LAPI or the plugin that permission was denied
                            CloseDeviceInterface(); // Close if permission denied
                        }
                    } else {
                         Log.w(TAG, "Permission result for a different device or null device.");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                 UsbDevice detachedDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                 Log.d(TAG, "Device detached event received for: " + (detachedDevice != null ? detachedDevice.getDeviceName() : "null"));
                 // Check if the detached device is the one we are currently connected to
                 if (detachedDevice != null && detachedDevice.equals(device)) {
                     Log.e(TAG, "Our device detached: " + device.getDeviceName());
                     CloseDeviceInterface();
                 }
            }
        }
    };

    // --- Bulk Transfer methods remain the same ---

    public boolean USBBulkSend(byte[] pBuf, int nLen, int nTimeOut) {
        int i, n, r, w_nRet;

        if (connection == null || endpoint_OUT == null) {
             Log.e(TAG, "USBBulkSend failed: Connection or Endpoint OUT is null.");
             return false;
        }
        if (m_nEPOutSize <= 0) {
             Log.e(TAG, "USBBulkSend failed: Invalid Endpoint OUT size: " + m_nEPOutSize);
             return false; // Safety check
        }


        n = nLen / m_nEPOutSize;
        r = nLen % m_nEPOutSize;

        for(i=0; i<n; i++)
        {
            System.arraycopy(pBuf, i*m_nEPOutSize, m_abyTransferBuf, 0, m_nEPOutSize);
            w_nRet = connection.bulkTransfer(endpoint_OUT, m_abyTransferBuf, m_nEPOutSize, nTimeOut);
            if (w_nRet != m_nEPOutSize) {
                Log.e(TAG, "USBBulkSend bulkTransfer failed (chunk " + i + "): Expected " + m_nEPOutSize + ", got " + w_nRet);
                return false;
            }
        }

        if (r > 0)
        {
            System.arraycopy(pBuf, i*m_nEPOutSize, m_abyTransferBuf, 0, r);
            w_nRet = connection.bulkTransfer(endpoint_OUT, m_abyTransferBuf, r, nTimeOut);
            if (w_nRet != r) {
                 Log.e(TAG, "USBBulkSend bulkTransfer failed (remainder): Expected " + r + ", got " + w_nRet);
                return false;
            }
        }
        // Log.d(TAG, "USBBulkSend successful for " + nLen + " bytes.");
        return true;
    }

    public boolean USBBulkReceive(byte[] pBuf, int nLen, int nTimeOut)
    {
        int i, n, r, w_nRet;

        if (connection == null || endpoint_IN == null) {
            Log.e(TAG, "USBBulkReceive failed: Connection or Endpoint IN is null.");
            return false;
        }
         if (m_nEPInSize <= 0) {
             Log.e(TAG, "USBBulkReceive failed: Invalid Endpoint IN size: " + m_nEPInSize);
             return false; // Safety check
        }


        n = nLen / m_nEPInSize;
        r = nLen % m_nEPInSize;

        for(i=0; i<n; i++)
        {
            // Ensure buffer is large enough for this chunk
            if (m_abyTransferBuf.length < m_nEPInSize) {
                 Log.e(TAG, "USBBulkReceive error: Transfer buffer too small for endpoint size.");
                 return false;
            }
            w_nRet = connection.bulkTransfer(endpoint_IN, m_abyTransferBuf, m_nEPInSize, nTimeOut);
            if (w_nRet != m_nEPInSize) {
                Log.e(TAG, "USBBulkReceive bulkTransfer failed (chunk " + i + "): Expected " + m_nEPInSize + ", got " + w_nRet);
                // Consider adding error details like connection.getLastError() if available
                return false;
            }
             // Ensure pBuf is large enough before copying
            if (pBuf.length < (i*m_nEPInSize + m_nEPInSize)) {
                 Log.e(TAG, "USBBulkReceive error: Destination buffer pBuf too small.");
                 return false;
            }
            System.arraycopy(m_abyTransferBuf, 0, pBuf, i*m_nEPInSize, m_nEPInSize);
        }

        if (r > 0)
        {
             // Ensure buffer is large enough for remainder
            if (m_abyTransferBuf.length < r) {
                 Log.e(TAG, "USBBulkReceive error: Transfer buffer too small for remainder size.");
                 return false;
            }
            w_nRet = connection.bulkTransfer(endpoint_IN, m_abyTransferBuf, r, nTimeOut);
            if (w_nRet != r) {
                 Log.e(TAG, "USBBulkReceive bulkTransfer failed (remainder): Expected " + r + ", got " + w_nRet);
                 return false;
            }
             // Ensure pBuf is large enough before copying
            if (pBuf.length < (i*m_nEPInSize + r)) {
                 Log.e(TAG, "USBBulkReceive error: Destination buffer pBuf too small for remainder.");
                 return false;
            }
            System.arraycopy(m_abyTransferBuf, 0, pBuf, i*m_nEPInSize, r);
        }
         // Log.d(TAG, "USBBulkReceive successful for " + nLen + " bytes.");
        return true;
    }
}

