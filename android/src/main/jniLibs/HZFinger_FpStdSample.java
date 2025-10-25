package com.HZFinger_FpStdSample;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.HZFINGER.HostUsb;
import com.HZFINGER.HAPI;
import com.HZFINGER.LAPI;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Bitmap.Config;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

public class HZFinger_FpStdSample extends Activity {
    /** Called when the activity is first created. */
    //for LAPI
    private TextView tvLAPImsg;
    protected Button btnOpen;
    protected Button btnClose;
    private Button btnGetImage;
    private Button btnOnVideo;
    private Button btnGetImageQuality;
    private Button btnGetNFIQuality;
    private Button btnCreateAnsiTemp;
    private Button btnCreateIsoTemp;
    private Button btnCompareTemp;
    private TextView tvANSITemp;
    private TextView tvISOTemp;
    private CheckBox chkCheckLive;
    private TextView txtSecLevel;

    private LAPI m_cLAPI = null;
    private HostUsb mHostUSb = null;

	private long m_hDevice = 0;
    private byte[] m_image = new byte[LAPI.WIDTH*LAPI.HEIGHT];
    private byte[] m_ansi_template = new byte[LAPI.FPINFO_STD_MAX_SIZE];
    private byte[] m_iso_template = new byte[LAPI.FPINFO_STD_MAX_SIZE];
    private byte[] bfwsq = new byte[512*512];
    private int[] RGBbits = new int[256 * 360];

    //for HAPI
    private TextView tvHAPImsg;
    private TextView txtRegID;
    private CheckBox chkCommMode;
    private CheckBox chkColorMode;
    private Button btnEnroll;
    private Button btnVerify;
    private Button btnSearch;
    private Button btnDBList;
    private Button btnDBClear;
    private Button btnDBDelete;
    private ImageView viewFinger;
    private ListView viewRecord;
    private RadioButton rdoANSI;
    private RadioButton rdoISO;
    private boolean formatFlag = true;		//ISO-true, ANSI-false
    private boolean commFlag = true;		//USB-true, SPI-false
    private boolean colorFlag = true;		//Black-true, Red-false
    private int store_cnt = 0;
	
    private String[] mListString = null;
    private String[] mFiletString = null;

    public static final int MESSAGE_SET_ID = 100;
    public static final int MESSAGE_SHOW_TEXT = 101;
    public static final int MESSAGE_VIEW_ANSI_TEMPLATE = 103;
    public static final int MESSAGE_VIEW_ISO_TEMPLATE = 104;
    public static final int MESSAGE_SHOW_IMAGE = 200;
    public static final int MESSAGE_ENABLE_BTN = 300;
    public static final int MESSAGE_SHOW_BITMAP = 303;
    public static final int MESSAGE_LIST_START = 400;
    public static final int MESSAGE_LIST_NEXT = 401;
    public static final int MESSAGE_LIST_END = 402;
    public static final int MESSAGE_ID_ENABLED = 403;
    public static final int MESSAGE_ID_SETTEXT= 404;
    public static final int MESSAGE_CHKLIVE_DISABLE= 500;

    private static final int TRANSPARENT_GRAY_THRESHOLD = 150;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private HAPI m_cHAPI = null;

    private boolean DEBUG = true;
    private volatile boolean bContinue = false;
    Activity myThis;

    private Context mContext;
    private ScreenBroadcastReceiver mScreenReceiver;
 	
    @Override
	protected void onResume() 
    {
        super.onResume();
    }
    @Override
	protected void onStart() 
    {
        super.onStart();
    }
    @Override
	protected void onPause() 
    {
        m_cHAPI.DoCancel();
        bContinue = false;
        super.onPause();
    }
    @Override
    public void onBackPressed()
    {
        super.onBackPressed();
    }
    @Override
	protected void onDestroy() 
    {
		/*m_hDevice != 0 || mHostUSb.hasDeviceOpen() != null*/
        if (btnClose.isEnabled()) {
            CLOSE_DEVICE();
        }
        super.onDestroy();
    }

    private void registerListener() {
        if (mContext != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            //filter.addAction(HostUsb.ACTION_USB_PERMISSION);
            mContext.registerReceiver(mScreenReceiver, filter);
        }
    }
 
    private class ScreenBroadcastReceiver extends BroadcastReceiver {
        private String action = null;
 
        @Override
        public void onReceive(Context context, Intent intent) {
            action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action))
            {
                onDestroy();
                finish();
            }
            else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))
            {
                UsbDevice newDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (newDevice != null && isFingerDevice(newDevice)) {
                    m_cLAPI.setHostUsb(mHostUSb);
                    if(!mHostUSb.AuthorizeDevice(newDevice)){
                        Toast.makeText(context,"FingerDevice attached",Toast.LENGTH_LONG).show();
                    }
                }
            }
            else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action))
            {
                UsbDevice oldDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (oldDevice != null && isFingerDevice(oldDevice)) {
                    m_cLAPI.setHostUsb(null);
                    Toast.makeText(context,"FingerDevice detached",Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private boolean isFingerDevice(UsbDevice device){
        int vid = device.getVendorId();
        int pid = device.getProductId();
        if(vid == LAPI.VID && pid == LAPI.PID){
            return true;
        }

        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        tvLAPImsg = (TextView) findViewById(R.id.LAPI_msg);
        chkCommMode = (CheckBox) findViewById(R.id.chkCommMode);
        chkColorMode = (CheckBox) findViewById(R.id.chkColorMode);
        btnOpen = (Button)findViewById(R.id.btnOpenDevice);
        btnClose = (Button)findViewById(R.id.btnCloseDevice);
        btnGetImage = (Button)findViewById(R.id.btnGetImage);
        btnOnVideo = (Button)findViewById(R.id.btnOnVideo);
        btnGetImageQuality = (Button)findViewById(R.id.btnGetImageQuality);
        btnGetNFIQuality = (Button)findViewById(R.id.btnGetNFIQuality);
        btnCreateAnsiTemp = (Button)findViewById(R.id.btnCreateANSITemp);
        btnCreateIsoTemp = (Button)findViewById(R.id.btnCreateISOTemp);
        tvANSITemp = (TextView)findViewById(R.id.tvANSITemp);
        tvISOTemp = (TextView)findViewById(R.id.tvISOTemp);
        btnCompareTemp = (Button)findViewById(R.id.btnCompareTemp);
        txtSecLevel = (TextView) findViewById(R.id.levelText);
        chkCheckLive = (CheckBox) findViewById(R.id.chkCheckLive);

        tvHAPImsg = (TextView) findViewById(R.id.HAPI_msg);
        txtRegID = (TextView) findViewById(R.id.idText);
        btnDBList = (Button) findViewById(R.id.btnDBRefresh);
        btnDBClear = (Button) findViewById(R.id.btnDBClear);
        btnDBDelete = (Button) findViewById(R.id.btnRCDelete);
        btnEnroll = (Button) findViewById(R.id.btnEnroll);
        btnVerify = (Button) findViewById(R.id.btnVerify);
        btnSearch = (Button) findViewById(R.id.btnSearch);
        viewFinger = (ImageView) findViewById(R.id.imageFinger);
        viewRecord = (ListView)findViewById(R.id.idListView);
        rdoANSI = (RadioButton) findViewById(R.id.rdoANSI); 
        rdoISO = (RadioButton) findViewById(R.id.rdoISO); 

        myThis = this;
		
        m_cLAPI = new LAPI(this);
        m_cHAPI = new HAPI(this,m_fpsdkHandle);

        mHostUSb = new HostUsb(this);

        EnableAllButtons(true,false);
        verifyStoragePermissions(myThis);

        mContext = this;
        mScreenReceiver = new ScreenBroadcastReceiver();
        registerListener();
        //txtRegID.setFocusable(true);
        chkCheckLive.setChecked(true);
        txtSecLevel.setText("3");

        viewRecord.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View v, final int arg2, long arg3) {
                Runnable r = new Runnable() {
                    int position = arg2;
                    public void run() {
                        SELECT_LIST_ITEM(position);
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });

        chkCommMode.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (chkCommMode.isChecked()) commFlag = true;
                else commFlag = false;
            }
        });

        chkColorMode.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (chkColorMode.isChecked()) colorFlag = true;
                else colorFlag = false;
            }
        });

        btnOpen.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {

                btnOpen.setEnabled(false);
                Runnable r = new Runnable() {
                    public void run() {
                        OPEN_DEVICE();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });
        
        btnClose.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Runnable r = new Runnable() {
                    public void run() {
                        CLOSE_DEVICE ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });
        
        btnGetImage.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (bContinue) {
                    bContinue = false;
                    //btnGetImage.setText("GetImage");
                    m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,"Canceled"));
                    return;
                }
                btnGetImage.setText("Stop");
                bContinue = true;
                Runnable r = new Runnable() {
                    public void run() {
                        GET_IMAGE ();
                    }
                };
                Thread s = new Thread(r);
				s.start(); 			
            }
        });

        btnOnVideo.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (bContinue) {
                    bContinue = false;
                    //btnOnVideo.setText("Video");
                    m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,"Canceled"));
                    return;
                }
                btnOnVideo.setText("Stop");
                bContinue = true;
                Runnable r = new Runnable() {
                    public void run() {
                        ON_VIDEO ();
                    }
                };
                Thread s = new Thread(r);
				s.start(); 			
            }
        });

        btnGetImageQuality.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Runnable r = new Runnable() {
                    public void run() {
                        GET_IMAGE_QUALITY ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });

        btnGetNFIQuality.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Runnable r = new Runnable() {
                    public void run() {
                        GET_NFI_QUALITY ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });

        btnCreateAnsiTemp.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Runnable r = new Runnable() {
                    public void run() {
                        CREATE_ANSI_TEMP ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });

        btnCreateIsoTemp.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Runnable r = new Runnable() {
                    public void run() {
                        CREATE_ISO_TEMP ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });

        btnCompareTemp.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Runnable r = new Runnable() {
                    public void run() {
                        COMPARE_TEMP ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });
        
        rdoANSI.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                formatFlag = false;
            }
        });
        
        rdoISO.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                formatFlag = true;
            }
        });

        btnEnroll.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (bContinue) {
                    m_cHAPI.DoCancel();
                    bContinue = false;
                    //btnEnroll.setText(String.format("Enroll"));
                    return;
                }
                btnEnroll.setText(String.format("Stop"));
                bContinue = true;
                Runnable r = new Runnable() {
                    public void run() {
                        FINGER_ENROLL ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });
		
        btnVerify.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (bContinue) {
                    m_cHAPI.DoCancel();
                    bContinue = false;
                    //btnVerify.setText(String.format("Verify"));
                    return;
                }
                btnVerify.setText(String.format("Stop"));
                bContinue = true;
                Runnable r = new Runnable() {
                    public void run() {
                        FINGER_VERIFY ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });

        btnSearch.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (bContinue) {
                    m_cHAPI.DoCancel();
                    bContinue = false;
                    //btnSearch.setText(String.format("Search"));
                    return;
                }
                btnSearch.setText(String.format("Stop"));
                bContinue = true;
                m_cHAPI.DBRefresh();
                Runnable r = new Runnable() {
                    public void run() {
                        FINGER_SAERCH ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });
		
        btnDBList.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Runnable r = new Runnable() {
                    public void run() {
                        DB_LIST ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });

        btnDBClear.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(myThis)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle("Clear all records from Database ")
                        .setMessage("Will you clear realy ?")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                Runnable r = new Runnable() {
                                    public void run() {
                                        m_cHAPI.ClearALLRecords();
                                        m_cHAPI.DBRefresh ();
                                        String msg = String.format("Clear DataBase : OK");
                                        m_fpsdkHandle.obtainMessage(HAPI.MSG_SHOW_TEXT, 0, 0, msg).sendToTarget();
                                    }
                                };
                                Thread s = new Thread(r);
                                s.start();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        })
                        .show();
            }
        });

        btnDBDelete.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Runnable r = new Runnable() {
                    public void run() {
                        DB_DELETE ();
                    }
                };
                Thread s = new Thread(r);
                s.start();
            }
        });
		
    }
	
    protected void OPEN_DEVICE() {
        String msg = "OPEN ...";
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, msg));
        UsbDevice dev = mHostUSb.hasDeviceOpen();
        if (dev != null) {
            m_cLAPI.setHostUsb(mHostUSb);
            mHostUSb.AuthorizeDevice(dev);
        }
        if (commFlag) m_hDevice = m_cLAPI.OpenDeviceEx(LAPI.SCSI_MODE);
		else m_hDevice = m_cLAPI.OpenDeviceEx(LAPI.SPI_MODE);
        if (m_hDevice == 0) {
            msg = "Can't open device !";
            EnableAllButtons(true, false);
            //CLOSE_DEVICE();
        } else {
            if (LAPI.bInitNetManager) msg = "OpenDevice() = OK";
            else {
                msg = "OpenDevice() = OK, unable to check live-scan";
                m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_CHKLIVE_DISABLE, 0, 0, 0));
            }
            EnableAllButtons(false, true);
        }
        m_cHAPI.m_hDev = m_hDevice;
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, msg));
    }
	
    protected void CLOSE_DEVICE() {
        if (btnClose.isEnabled()) {
            String msg;
            try {
                EnableAllButtons(true, false);
                m_cHAPI.DoCancel();
                if (m_hDevice != 0) {
                    m_cLAPI.CloseDeviceEx(m_hDevice);
                }
                msg = "CloseDevice() = OK";

                m_hDevice = 0;
                m_cHAPI.m_hDev = 0;

                m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, msg));
            } catch (Exception E) {
                msg = "error:" + E.getMessage();
                m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, msg));
                E.printStackTrace();
            }
        }
    }

    protected void GET_IMAGE() {
        EnableAllButtons(false,false);
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnGetImage, 1));
        //String msg;
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,"Put your finger"));
        m_fEvent.obtainMessage(MESSAGE_SHOW_IMAGE, LAPI.WIDTH, LAPI.HEIGHT, null).sendToTarget();
		int secLevel = Integer.parseInt(txtSecLevel.getText().toString());
		if (secLevel < 1) secLevel = 1; if (secLevel > 5) secLevel = 5;
        while (bContinue) {
            int ret = m_cLAPI.GetImage(m_hDevice, m_image);
            if (ret == LAPI.NOTCALIBRATED) {
                m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, "not Calibrated !").sendToTarget();
                break;
            }
            else if (ret != LAPI.TRUE) {
                m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, "Can't get image !").sendToTarget();
                break;
            }
			ret = m_cLAPI.IsPressFingerEx(m_hDevice, m_image, chkCheckLive.isChecked(), LAPI.LIVECHECK_THESHOLD[secLevel - 1]);
            if (ret >= LAPI.DEF_FINGER_SCORE) {
                m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, "GetImage() = OK").sendToTarget();
                m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_IMAGE, LAPI.WIDTH, LAPI.HEIGHT,m_image));
                break;
            }
            else if (ret == LAPI.FAKEFINGER) {
                m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, "warning : Fake Finger !").sendToTarget();
                SLEEP(500);
                //break;
            }
        }
        bContinue = false;
        m_appHandle.obtainMessage(MESSAGE_ID_SETTEXT, R.id.btnGetImage, R.string.TEXT_GET_IMAGE).sendToTarget();
        if (DEBUG) {
            Date date = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            String filename = dateFormat.format(date) + ".bmp";
            SaveAsBmpFile(m_image, LAPI.WIDTH, LAPI.HEIGHT, filename);

            filename = dateFormat.format(date) + ".png";
            SaveAsPngFile(m_image, LAPI.WIDTH, LAPI.HEIGHT, filename);

            long wsqSize = m_cLAPI.CompressToWSQImage (m_hDevice, m_image, bfwsq);
            if (wsqSize > 0) {
                filename = dateFormat.format(date) + ".wsq";
                SaveAsFile(filename, bfwsq, (int)wsqSize);
            }
        }
        EnableAllButtons(false,true);
    }
	
    protected void ON_VIDEO() {
        EnableAllButtons(false,false);
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnOnVideo, 1));
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,"Put your finger"));
        int secLevel = Integer.parseInt(txtSecLevel.getText().toString());
        if (secLevel < 1) secLevel = 1; if (secLevel > 5) secLevel = 5;
        while (bContinue) {
            int startTime = (int)System.currentTimeMillis();
            int ret = m_cLAPI.GetImage(m_hDevice, m_image);
            if (ret == LAPI.NOTCALIBRATED) {
                m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, "not Calibrated !").sendToTarget();
                break;
            }
            else if (ret != LAPI.TRUE) {
                m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, "Can't get image !").sendToTarget();
                break;
            }
            ret = m_cLAPI.IsPressFingerEx(m_hDevice, m_image, chkCheckLive.isChecked(), LAPI.LIVECHECK_THESHOLD[secLevel - 1]);
            m_fEvent.obtainMessage(MESSAGE_SHOW_IMAGE, LAPI.WIDTH, LAPI.HEIGHT, m_image).sendToTarget();
            if (ret == LAPI.FAKEFINGER) {
                m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0, "warning : Fake Finger !").sendToTarget();
                SLEEP(500);
                continue;
                //break;
            }
            String msg = String.format("GetImage(%d) = OK : %dms", ret, (int)(System.currentTimeMillis() - startTime));
            m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));
        }
        bContinue = false;
        m_appHandle.obtainMessage(MESSAGE_ID_SETTEXT, R.id.btnOnVideo, R.string.TEXT_VIDEO).sendToTarget();
        EnableAllButtons(false,true);
    }

	protected void GET_IMAGE_QUALITY() 
    {
        int qr;
        String msg = "";
        qr = m_cLAPI.GetImageQuality(m_hDevice,m_image);
        msg = String.format("GetImageQuality() = %d", qr);
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));
    }

	protected void GET_NFI_QUALITY() 
    {
        int qr;
        String msg = "";
		String[] degree = {"excellent","very good","good","poor","fair","none"};
        qr = m_cLAPI.GetNFIQuality(m_hDevice,m_image);
		msg = String.format("GetNFIQuality() = %d : %s", qr, degree[qr]);
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));
    }

	protected void CREATE_ANSI_TEMP() 
    {
        int i, ret, templateLen = 0;
        String msg, str;
        ret =m_cLAPI.IsPressFinger(m_hDevice,m_image);
        if (ret==0) {
            msg = "IsPressFinger() = 0";
            m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));
            return;
        }
        templateLen = m_cLAPI.CreateANSITemplate(m_hDevice,m_image, m_ansi_template);
        if (templateLen == 0) msg = "Can't create ANSI template !";
        else msg = "CreateANSITemplate() = OK";
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));

        msg = "";
        for (i=0; i < templateLen; i ++) {
            msg += String.format("%02x", m_ansi_template[i]);
        }
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_VIEW_ANSI_TEMPLATE, 0, 0,msg));
 
        if (DEBUG)
        {
            store_cnt++;
            str = String.format("FMR_%d(ANSI).bin", store_cnt);
            SaveAsFile (str,  m_ansi_template, templateLen);

            str = String.format("FMR_%d(ANSI).txt", store_cnt);
            SaveAsFile (str, msg.getBytes(), templateLen*2);
        }
    }

	protected void CREATE_ISO_TEMP() 
    {
        int i, ret , templateLen = 0;
        String msg, str;
        ret =m_cLAPI.IsPressFinger(m_hDevice,m_image);
        if (ret==0) {
            msg = "IsPressFinger() = 0";
            m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));
            return;
        }
        templateLen = m_cLAPI.CreateISOTemplate(m_hDevice,m_image, m_iso_template);
        if (templateLen == 0) msg = "Can't create ISO template !";
        else msg = "CreateISOTemplate() = OK";
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));

        msg = "";
        for (i=0; i < templateLen; i ++) {
            msg += String.format("%02x", m_iso_template[i]);
        }
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_VIEW_ISO_TEMPLATE, 0, 0,msg));

        if (DEBUG)
        {
            store_cnt++;
            str = String.format("FMR_%d(ISO).bin", store_cnt);
            SaveAsFile (str,  m_iso_template, templateLen);

            str = String.format("FMR_%d(ISO).txt", store_cnt);
            SaveAsFile (str, msg.getBytes(), templateLen*2);
        }
    }

	protected void COMPARE_TEMP() 
    {
        int score;
        String msg;
        score = m_cLAPI.CompareTemplates(m_hDevice,m_ansi_template, m_iso_template);
        msg = String.format("CompareTemplates() = %d", score);
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));
    }

	protected void COMPARE_ISO_TEMP() 
    {
        int score;
        String msg;
        score = m_cLAPI.CompareTemplates(m_hDevice,m_ansi_template, m_iso_template);
        msg = String.format("CompareISOTemplates() = %d", score);
        m_fEvent.sendMessage(m_fEvent.obtainMessage(MESSAGE_SHOW_TEXT, 0, 0,msg));
    }

    protected void FINGER_ENROLL() {
        EnableAllButtons(false,false);
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnEnroll, 1));

        String msg = "";
        Resources res = getResources();
        String regid = txtRegID.getText().toString();
        int secLevel = Integer.parseInt(txtSecLevel.getText().toString());
        if (secLevel < 1) secLevel = 1; if (secLevel > 5) secLevel = 5;
        if ((regid==null) || regid.isEmpty()) {
            msg = res.getString(R.string.Insert_ID);
            m_fpsdkHandle.obtainMessage(HAPI.MSG_SHOW_TEXT, 0, 0, msg).sendToTarget();
            EnableAllButtons(false,true);
            m_appHandle.obtainMessage(MESSAGE_ID_SETTEXT, R.id.btnEnroll, R.string.TEXT_ENROLL).sendToTarget();
            bContinue = false;
            return;
        }
    	
        boolean ret = m_cHAPI.Enroll(regid, formatFlag, chkCheckLive.isChecked(), secLevel);
        if (ret) {
            msg = String.format("Enroll OK (ID=%s)",regid);
            m_cHAPI.DBRefresh ();
        }
        else {
            msg = String.format("Enroll : False : %s",errorMessage(m_cHAPI.GetErrorCode()));
        }
        bContinue = false;
        m_fpsdkHandle.obtainMessage(HAPI.MSG_SHOW_TEXT, 0, 0, msg).sendToTarget();
        m_appHandle.obtainMessage(MESSAGE_ID_SETTEXT, R.id.btnEnroll, R.string.TEXT_ENROLL).sendToTarget();
        EnableAllButtons(false,true);
    }

    protected void FINGER_VERIFY() {
        EnableAllButtons(false,false);
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnVerify, 1));

        int retry;
        String msg = "";
        Resources res = getResources();
        String regid = txtRegID.getText().toString();
		int secLevel = Integer.parseInt(txtSecLevel.getText().toString());
		if (secLevel < 1) secLevel = 1; if (secLevel > 5) secLevel = 5;
    	
        if ((regid==null) || regid.isEmpty()) {
            msg = res.getString(R.string.Insert_ID);
            m_fpsdkHandle.obtainMessage(HAPI.MSG_SHOW_TEXT, 0, 0, msg).sendToTarget();
            m_appHandle.obtainMessage(MESSAGE_ID_SETTEXT, R.id.btnVerify, R.string.TEXT_VERIFY).sendToTarget();
            bContinue = false;
            EnableAllButtons(false,true);
            return;
        }
        for (retry = 0; retry < 10; retry ++  ) {
            boolean ret = m_cHAPI.Verify(regid, formatFlag, chkCheckLive.isChecked(), secLevel);
            if (ret) {
                msg = String.format("Verify OK (ID=%s) : Time(Capture=%dms,Create=%dms,Match=%dms)",
                        regid,m_cHAPI.GetProcessTime(0),m_cHAPI.GetProcessTime(1),m_cHAPI.GetProcessTime(2));
                break;
            }
            else {
                int errCode = m_cHAPI.GetErrorCode();
                if (errCode != HAPI.ERROR_NONE && errCode != HAPI.ERROR_LOW_QUALITY) {
                    msg = String.format("Verify : False : %s",errorMessage(m_cHAPI.GetErrorCode()));
                    break;
                }
            }
            SLEEP(300);
        }

        if (retry == 10) {
            msg = String.format("Verify : False : Exceed retry limit");
        }
        bContinue = false;
        m_fpsdkHandle.obtainMessage(HAPI.MSG_SHOW_TEXT, 0, 0, msg).sendToTarget();
        m_appHandle.obtainMessage(MESSAGE_ID_SETTEXT, R.id.btnVerify, R.string.TEXT_VERIFY).sendToTarget();
        EnableAllButtons(false,true);
    }

    protected void FINGER_SAERCH() {
        EnableAllButtons(false,false);
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnSearch, 1));
        String msg = "";
		int secLevel = Integer.parseInt(txtSecLevel.getText().toString());
		if (secLevel < 1) secLevel = 1; if (secLevel > 5) secLevel = 5;
    	
        while (bContinue) {
            String searched_id = m_cHAPI.Identify(formatFlag, chkCheckLive.isChecked(), secLevel);
            int errCode = m_cHAPI.GetErrorCode();
            if (errCode != HAPI.ERROR_NONE && errCode != HAPI.ERROR_LOW_QUALITY) {
                msg = String.format("Identify : False : %s",errorMessage(m_cHAPI.GetErrorCode()));
                m_fpsdkHandle.obtainMessage(HAPI.MSG_SHOW_TEXT, 0, 0, msg).sendToTarget();
                break;
            }
			if ( searched_id.equals("") == true ) 
                msg = String.format("Identify False : Time(Capture=%dms,Create=%dms,Match=%dms)",
                        m_cHAPI.GetProcessTime(0),m_cHAPI.GetProcessTime(1),m_cHAPI.GetProcessTime(2));
			else 
                msg = String.format("Identify OK (ID=%s) : Time(Capture=%dms,Create=%dms,Match=%dms)",
                        searched_id,m_cHAPI.GetProcessTime(0),m_cHAPI.GetProcessTime(1),m_cHAPI.GetProcessTime(2));
            m_fpsdkHandle.obtainMessage(HAPI.MSG_SHOW_TEXT, 0, 0, msg).sendToTarget();
            SLEEP(600);
        }
        bContinue = false;
        m_appHandle.obtainMessage(MESSAGE_ID_SETTEXT, R.id.btnSearch, R.string.TEXT_SEARCH).sendToTarget();
        EnableAllButtons(false,true);
    }

	static class MyFilter implements FilenameFilter{  
		private String type;  
		public MyFilter(String type){  
		this.type = type;  
		}  
		public boolean accept(File dir,String name){  
		return name.endsWith(type);  
		}  
	} 

    protected void DB_LIST() {
        EnableAllButtons(false,false);
        m_cHAPI.DBRefresh();
        EnableAllButtons(false,true);
    }

    void SELECT_LIST_ITEM(int pos)
    {
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_SET_ID, 0, 0,mFiletString[pos]));
    }

    protected void DB_DELETE() {
        String msg;
        String regid = txtRegID.getText().toString();
        boolean ret = m_cHAPI.DeleteRecord(regid);
    	if (ret==true) 
        {
            msg = String.format("DeleteRecord : OK : ID = %s",regid);
            m_cHAPI.DBRefresh ();
        }
        else msg = String.format("DeleteRecord : False : %s",errorMessage(m_cHAPI.GetErrorCode()));
        m_fpsdkHandle.obtainMessage(HAPI.MSG_SHOW_TEXT, 0, 0, msg).sendToTarget();
    }
	
    protected void SLEEP (int waittime)
    {
        int startTime, passTime = 0;
        startTime = (int)System.currentTimeMillis();
        while (passTime < waittime) {
            passTime = (int)System.currentTimeMillis();
            passTime = passTime - startTime;
        }
    }

    public void EnableAllButtons(boolean bOpen, boolean bOther)
    {
        int iOther;
        if (bOpen) m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnOpenDevice, 1));
        else m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnOpenDevice, 0));
        if (bOther) iOther = 1; else iOther = 0;
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnCloseDevice, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnGetImage, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnOnVideo, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnGetImageQuality, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnGetNFIQuality, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnCreateANSITemp, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnCreateISOTemp, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnCompareTemp, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnEnroll, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnVerify, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnSearch, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnDBRefresh, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnDBClear, iOther));
        m_appHandle.sendMessage(m_appHandle.obtainMessage(MESSAGE_ID_ENABLED, R.id.btnRCDelete, iOther));
    }

    public void UpdateListView() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, mListString);
        viewRecord.setAdapter(adapter);
    }

    private final Handler m_fEvent = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SHOW_TEXT:
                    tvLAPImsg.setText((String)msg.obj);
                    break;
                case MESSAGE_VIEW_ANSI_TEMPLATE:
                    tvANSITemp.setText((String)msg.obj);
                    break;
                case MESSAGE_VIEW_ISO_TEMPLATE:
                    tvISOTemp.setText((String)msg.obj);
                    break;
                case MESSAGE_ID_ENABLED:
                    Button btn = (Button) findViewById(msg.arg1);
                    if (msg.arg2 != 0) btn.setEnabled(true);
                    else btn.setEnabled(false);
                    break;
                case MESSAGE_SHOW_IMAGE:
                    ShowFingerBitmap ((byte[])msg.obj,msg.arg1,msg.arg2);
                    break;
                case MESSAGE_CHKLIVE_DISABLE:
                    chkCheckLive.setChecked(false);
                    chkCheckLive.setEnabled(false);
                    break;
            }
        }
    };

    private final Handler m_appHandle = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SET_ID:
                    txtRegID.setText((String)msg.obj);
                    break;
                case MESSAGE_SHOW_TEXT:
                    tvHAPImsg.setText((String)msg.obj);
                    break;
                case MESSAGE_SHOW_IMAGE:
                    ShowFingerBitmap ((byte[])msg.obj,msg.arg1,msg.arg2);
                    break;
                case MESSAGE_ENABLE_BTN:
                    boolean bEnable = msg.arg1 == 1 ? true : false;
                    boolean bOpen = msg.arg2 == 1 ? true : false;
                    TextView id = (TextView) findViewById(R.id.idText);
                    id.setEnabled(bEnable);
                    Button btn = (Button) findViewById(R.id.btnVerify);
                    btn.setEnabled(bEnable);
                    btn = (Button) findViewById(R.id.btnSearch);
                    btn.setEnabled(bEnable);
                    btn = (Button) findViewById(R.id.btnDBRefresh);
                    btn.setEnabled(bEnable);
                    btn = (Button) findViewById(R.id.btnRCDelete);
                    btn.setEnabled(bEnable);
                    btn = (Button) findViewById(R.id.btnDBClear);
                    btn.setEnabled(bEnable);
                    btn = (Button) findViewById(R.id.btnEnroll);
                    btn.setEnabled(bEnable);
                    btn = (Button) findViewById(R.id.btnOpenDevice);
                    btn.setEnabled(bOpen);
                    break;
                case MESSAGE_SHOW_BITMAP:
                    viewFinger.setImageBitmap((Bitmap)msg.obj);
                    break;
                case MESSAGE_LIST_START:
                    mListString = new String[msg.arg1];
                    mFiletString = new String[msg.arg1];
                    break;
                case MESSAGE_LIST_NEXT:
                    mListString[msg.arg2] = String.format("No = %d : ID = %s",msg.arg2,(String)msg.obj);
                    mFiletString[msg.arg2] = (String)msg.obj;
                    break;
                case MESSAGE_LIST_END:
                    UpdateListView();
                    String txt = String.format("Record Count = %d", msg.arg1);
                    tvHAPImsg.setText(txt);
                    break;
                case MESSAGE_ID_ENABLED:
                    btn = (Button) findViewById(msg.arg1);
                    if (msg.arg2 != 0) btn.setEnabled(true);
                    else btn.setEnabled(false);
                    break;
                case MESSAGE_ID_SETTEXT:
                    btn = (Button) findViewById(msg.arg1);
                    btn.setText(msg.arg2);
                    break;
            }
        }
    };

    private final Handler m_fpsdkHandle = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String str = "";
            Resources res;
            switch (msg.what) {
                case 0xff:
                    break;
                case HAPI.MSG_SHOW_TEXT:
                    tvHAPImsg.setText((String)msg.obj);
                    break;
                case HAPI.MSG_PUT_FINGER:
                    res = getResources();
                    str = res.getString(R.string.Put_your_finger);
                    if (msg.arg1>0) {
                        str += (" ("+String.valueOf(msg.arg1)+"/"+String.valueOf(msg.arg2)+")");
                    }
                    str += " ! ";
                    str += (String)msg.obj;
                    tvHAPImsg.setText(str);
                    break;
                case HAPI.MSG_RETRY_FINGER:
                    res = getResources();
                    str = res.getString(R.string.Retry_your_finger);
                    str += " !";
                    tvHAPImsg.setText(str);
                    break;
                case HAPI.MSG_TAKEOFF_FINGER:
                    res = getResources();
                    str = res.getString(R.string.Takeoff_your_finger);
                    str += " !";
                    tvHAPImsg.setText(str);
                    break;
                case HAPI.MSG_ON_SEARCHING:
                    res = getResources();
                    str = res.getString(R.string.TEXT_ON_SEARCHING);
                    if (msg.arg1>0) {
                        str += (" (quality="+String.valueOf(msg.arg1)+")");
                    }
                    str += "  ...  ";
                    tvHAPImsg.setText(str);
                    break;
                case HAPI.MSG_FINGER_CAPTURED:
                    ShowFingerBitmap ((byte[])msg.obj,msg.arg1,msg.arg2);
                    break;
                case HAPI.MSG_DBRECORD_START:
                    mListString = new String[msg.arg1];
                    mFiletString = new String[msg.arg1];
                    break;
                case HAPI.MSG_DBRECORD_NEXT:
                    mListString[msg.arg2] = String.format("No = %d : ID = %s",msg.arg2,(String)msg.obj);
                    mFiletString[msg.arg2] = (String)msg.obj;
                    break;
                case HAPI.MSG_DBRECORD_END:
                    UpdateListView();
                    String txt = String.format("Record Count = %d", msg.arg1);
                    tvHAPImsg.setText(txt);
                    break;
            }
        }
    };
    public String errorMessage (int errCode) {
        Resources res;
        res = getResources();
        switch (errCode) {
            case HAPI.ERROR_NONE:
                return res.getString(R.string.ERROR_NONE);
            case HAPI.ERROR_ARGUMENTS:
                return res.getString(R.string.ERROR_ARGUMENTS);
            case HAPI.ERROR_LOW_QUALITY:
                return res.getString(R.string.ERROR_LOW_QUALITY);
            case HAPI.ERROR_NEG_ACCESS:
                return res.getString(R.string.ERROR_NEG_ACCESS);
            case HAPI.ERROR_NEG_FIND:
                return res.getString(R.string.ERROR_NEG_FIND);
            case HAPI.ERROR_NEG_DELETE:
                return res.getString(R.string.ERROR_NEG_DELETE);
            case HAPI.ERROR_INITIALIZE:
                return res.getString(R.string.ERROR_INITIALIZE);
            case HAPI.ERROR_CANT_GENERATE:
                return res.getString(R.string.ERROR_CANT_GENERATE);
            case HAPI.ERROR_OVERFLOW_RECORD:
                return res.getString(R.string.ERROR_OVERFLOW_RECORD);
            case HAPI.ERROR_NEG_ADDNEW:
                return res.getString(R.string.ERROR_NEG_ADDNEW);
            case HAPI.ERROR_NEG_CLEAR:
                return res.getString(R.string.ERROR_NEG_CLEAR);
            case HAPI.ERROR_NONE_CAPIMAGE:
                return res.getString(R.string.ERROR_NONE_CAPIMAGE);
			case HAPI.ERROR_FAKE_FINGER:
				return res.getString(R.string.ERROR_FAKE_FINGER);
            case HAPI.ERROR_NOT_CALIBRATED:
                return res.getString(R.string.ERROR_NOT_CALIBRATED);
            case HAPI.ERROR_NONE_DEVICE:
                return res.getString(R.string.ERROR_NONE_DEVICE);
            case HAPI.ERROR_TIMEOUT_OVER:
                return res.getString(R.string.ERROR_TIMEOUT_OVER);
            case HAPI.ERROR_DO_CANCELED:
                return res.getString(R.string.ERROR_DOCANCELED);
            case HAPI.ERROR_EMPTY_DADABASE:
                return res.getString(R.string.ERROR_EMPTY_DADABASE);
            default:
                return String.format("errCode=%d", errCode);
        }
    }

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    private void SaveAsBmpFile(byte[] image, int width, int height, String filename) {
        if (width==0) return;
        if (height==0) return;

        int[] RGBbits = new int[width * height];
        //File extStorageDirectory = Environment.getExternalStorageDirectory();
        //File Dir = new File(extStorageDirectory, "Android");
        File Dir = mContext.getExternalFilesDir(null);
        File file = new File(Dir, filename);

        for (int i = 0; i < width * height; i++ ) {
            int v;
            if (image != null) v = image[i] & 0xff;
            else v = 255;

            if (colorFlag) RGBbits[i] = Color.rgb(v, v, v);
            else {
                if (v < TRANSPARENT_GRAY_THRESHOLD) RGBbits[i] = Color.rgb(255, 0, 0);
                else RGBbits[i] = Color.rgb(255, 255, 255);
            }
        }
        Bitmap bmp = Bitmap.createBitmap(RGBbits, width, height, Config.RGB_565);
        try {
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
        }
    }

    private void SaveAsPngFile(byte[] image, int width, int height, String filename) {
        if (width==0) return;
        if (height==0) return;

        int[] RGBbits = new int[width * height];
        //File extStorageDirectory = Environment.getExternalStorageDirectory();
        //File Dir = new File(extStorageDirectory, "Android");
        File Dir = mContext.getExternalFilesDir(null);
        File file = new File(Dir, filename);

        for (int i = 0; i < width * height; i++ ) {
            int v, alpha = 0;
            if (image != null) v = image[i] & 0xff;
            else v = 255;

            if (v >= TRANSPARENT_GRAY_THRESHOLD) alpha = 0; else alpha = 255;

            if (colorFlag) RGBbits[i] = Color.argb(alpha, v, v, v);
            else {
                if (v < TRANSPARENT_GRAY_THRESHOLD) RGBbits[i] = Color.argb(alpha,255, 0, 0);
                else RGBbits[i] = Color.argb(alpha,255, 255, 255);
            }
        }
        Bitmap bmp = Bitmap.createBitmap(RGBbits, width, height, Config.ARGB_8888);
        try {
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
        }
    }

    public boolean SaveAsFile (String filename, byte[] buffer, int len) {
        boolean ret = true;
        //File extStorageDirectory = Environment.getExternalStorageDirectory();
        //File Dir = new File(extStorageDirectory, "Android");
        File Dir = mContext.getExternalFilesDir(null);
        File file = new File(Dir, filename);
        try { 
            FileOutputStream out = new FileOutputStream(file);                    
            out.write(buffer,0,len);
            out.close();
         } catch (Exception e) { 
            ret = false;
        }
        return ret;
    }

    public long LoadAsFile (String filename, byte[] buffer) {
        long ret = 0;
        //File extStorageDirectory = Environment.getExternalStorageDirectory();
        //File Dir = new File(extStorageDirectory, "Android");
        File Dir = mContext.getExternalFilesDir(null);
        File file = new File(Dir, filename);
        ret = file.length();
        try { 
            FileInputStream out = new FileInputStream(file);                    
            out.read(buffer);
            out.close();
         } catch (Exception e) { 
        }
        return ret;
    }

    private void ShowFingerBitmap(byte[] image, int width, int height) {
        if (width==0) return;
        if (height==0) return;
        for (int i = 0; i < width * height; i++ ) {
            int v;
            if (image != null) v = image[i] & 0xff;
			else v = 255;
			
			if (colorFlag) RGBbits[i] = Color.rgb(v, v, v);
			else {
			if (v < TRANSPARENT_GRAY_THRESHOLD) RGBbits[i] = Color.rgb(255, 0, 0);
			else RGBbits[i] = Color.rgb(255, 255, 255);
        }
        }
        Bitmap bmp = Bitmap.createBitmap(RGBbits, width, height,Config.RGB_565);
        viewFinger.setImageBitmap(bmp);
    }
}