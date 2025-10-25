package com.HZFINGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

public class LAPI {
    static final String TAG = "LAPI";
    //****************************************************************************************************
	static 
    {
        try{
            System.loadLibrary("biofp_e_lapi");
            Log.e("LAPI","loadLibrary OK");
        }
        catch(UnsatisfiedLinkError e) {
            Log.e("LAPI","loadLibrary failed",e);
        }

		try {
			System.loadLibrary("checkLive");
			Log.e("checkLive", "loadLibrary OK");
		} catch (UnsatisfiedLinkError e) {
            Log.e("checkLive", "loadLibrary failed", e);
        }
    }
    //****************************************************************************************************
    public static final int VID = 0x28E9;
    public static final int PID = 0x028F;
    private static HostUsb m_usbHost = null;
    private static int m_hUSB = 0;
    public static boolean bInitNetManager = false;
    public static final int MSG_OPEN_DEVICE = 0x10;
    public static final int MSG_CLOSE_DEVICE = 0x11;
    public static final int MSG_BULK_TRANS_IN = 0x12;
    public static final int MSG_BULK_TRANS_OUT = 0x13;
    //****************************************************************************************************
    public static final int WIDTH  = 256;
    public static final int HEIGHT  = 360;
    public static final int IMAGE_SIZE = WIDTH*HEIGHT;
    //****************************************************************************************************
    public static final int FPINFO_STD_MAX_SIZE = 1024;
    public static final int DEF_FINGER_SCORE = 45;
    public static final int DEF_QUALITY_SCORE = 30;
    public static final int DEF_MATCH_SCORE = 45;
    public static final int FPINFO_SIZE = FPINFO_STD_MAX_SIZE;
    //****************************************************************************************************
    public static final int TRUE = 1;
    public static final int FALSE = 0;
	public static final int FAKEFINGER = -1;
    public static final int NOTCALIBRATED = -2;
    //****************************************************************************************************
    public static final int SCSI_MODE = 1;
    public static final int SPI_MODE = 2;
    public static final int VERSION1 = 1;
    public static final int VERSION2 = 2;
    //public static final int commMode = SCSI_MODE;//SPI_MODE
    public static final int versionNo = VERSION2;
    public static final String Model_FolderPath = "/mnt/sdcard/HZFinger_DNN_Model";
    public static final String ModelName = "HZFinger";
    public static final float LIVECHECK_THESHOLD[] = {0.5f, 0.3f, 0.1f, 0.05f, 0.02f};
    //****************************************************************************************************
    private static Activity m_content = null;

    public void setHostUsb(HostUsb hostUsb){
        m_usbHost = hostUsb ;
    }
    //****************************************************************************************************
    private static int CallBack (int message, int notify, int param, Object data)
    {
        switch (message) {
            case MSG_OPEN_DEVICE:
                if (m_usbHost == null) {
                    m_usbHost = new HostUsb(m_content);

                    if (!m_usbHost.AuthorizeDevice(VID, PID)) {
                        m_usbHost = null;
                        return 0;
                    }
                }

                m_usbHost.WaitForInterfaces();

                m_hUSB = m_usbHost.OpenDeviceInterfaces();
                if (m_hUSB < 0) {
                    m_usbHost = null;
                    return 0;
                }

                return m_hUSB;
            case MSG_CLOSE_DEVICE:
                if (m_usbHost != null) {
                    m_usbHost.CloseDeviceInterface();
                    m_hUSB = -1;
                    m_usbHost = null;
                }
                return 1;
            case MSG_BULK_TRANS_IN:
                if (m_usbHost.USBBulkReceive((byte[])data,notify,param)) return notify;
                return 0;
            case MSG_BULK_TRANS_OUT:
                if (m_usbHost.USBBulkSend((byte[])data,notify,param)) return notify;
                return 0;
        }
        return 0;
    }
    //****************************************************************************************************
    public LAPI(Activity a) {
        m_content = a;
    }
	//****************************************************************************************************
	public void POWER_ON() {
		Intent intent = new Intent();
		intent.setAction("android.intent.action.ChangeHotonReceiver");
		m_content.sendBroadcast(intent);
		intent.setAction("android.intent.action.lightonReceiver");
		m_content.sendBroadcast(intent);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {}
    }
    //****************************************************************************************************
	public void POWER_OFF() {
		Intent intent = new Intent();
		intent.setAction("android.intent.action.ChangeHotoffReceiver");
		m_content.sendBroadcast(intent);
		intent.setAction("android.intent.action.lightoffReceiver");
		m_content.sendBroadcast(intent);
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {}
	}
    //------------------------------------------------------------------------------------------------//
	// Purpose   : This function initializes the Fingerprint Recognition SDK Library and 
    //				connects Fingerprint Collection Module.
    // Function  : OpenDevice
    // Arguments : void
	// Return    : long  
	//			     If successful, return handle of device, else 0. 	
    //------------------------------------------------------------------------------------------------//
	private native long OpenDevice(int commMode, int versionNo);
	public long OpenDeviceEx(int commMode)
    {
		long ret = 0;
		if(m_usbHost == null)
		{
			if (commMode == SCSI_MODE) POWER_ON();
		}

        //load DNN Model for Checking live finger
        if (!bInitNetManager)
        {
            //File sdcard = m_content.getExternalFilesDir(null);
            //String folderPath = sdcard.getAbsolutePath();
            //loadDNNModel(folderPath, ModelName);
            File file = new File(Model_FolderPath, ModelName+".model");
            if (file.exists())
            {
                loadDNNModel(Model_FolderPath, ModelName);
                bInitNetManager = true;
            }
        }

        ret = OpenDevice(commMode, versionNo);
      	if (ret == 0 && commMode == SCSI_MODE) POWER_OFF();
        return ret;
    }
    //------------------------------------------------------------------------------------------------//
	// Purpose   : This function finalizes the Fingerprint Recognition SDK Library and 
    //				disconnects Fingerprint Collection Module.
    // Function  : CloseDevice
	// Arguments : 
	//      (In) : long device : handle returned from function "OpenDevice()"
    // Return    : int
	//			      If successful, return 1, else 0 	
    //------------------------------------------------------------------------------------------------//
	private  native int CloseDevice(long device);
	public int CloseDeviceEx(long device)
    {
        int ret;
        ret = CloseDevice(device);
        POWER_OFF();
        return ret;
    }
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function returns image captured from Fingerprint Collection Module.
    // Function  : GetImage
	// Arguments : 
	//      (In) : long device : handle returned from function "OpenDevice()"
    //  (In/Out) : byte[] image : image captured from this device
    // Return    : int
    //			      If successful, return 1,
	//				  if not calibrated(TCS1/2), return -2,		
	//						else, return  0 	
    //------------------------------------------------------------------------------------------------//
	public native int GetImage(long device, byte[] image);
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function does calibration of this Fingerprint Collection Module.
    //			   This function is used only for TCS1/TCS2 Sensor.
    // Function  : Calibration
	// Arguments : 
	//      (In) : long device : handle returned from function "OpenDevice()"
    //      (In) : int mode : dry/default/wet
	// Return    :  
	//			   int :   If successful, return 1, else 0 	
    //------------------------------------------------------------------------------------------------//
	public native int Calibration(long device, int mode);
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function checks whether finger is on sensor of this device or not.
    // Function  : IsPressFinger
	// Arguments : 
	//      (In) : long device : handle returned from function "OpenDevice()"
    //		(In) : byte[] image : image returned from function "GetImage()"
	// Return    : int 
	//				   return percent value indicating that finger is placed on sensor(0~100). 	
    //------------------------------------------------------------------------------------------------//
	public native int IsPressFinger(long device, byte[] image);
    public int IsPressFingerEx(long device, byte[] image, boolean isCheckLive, float threshold)
    {
        if (!isCheckLive || !bInitNetManager)
        {
            return IsPressFinger(device, image);
        }
        else
        {
            int score = IsPressFinger(device, image);
            if (score <  DEF_FINGER_SCORE) {
                for (int i = 0; i < image.length; i++) image[i] = 0;
                return 0;
            }

            int ret = checkLiveFinger(image, WIDTH, HEIGHT, threshold);
            if (ret == 0) {
                for (int i = 0; i < image.length; i++) image[i] = 0;
                return FAKEFINGER;
            }
            else return score;
        }
    }
    //------------------------------------------------------------------------------------------------//
	// Purpose   : This function creates the ANSI standard template from the uncompressed raw image. 
    // Function  : CreateANSITemplate
	// Arguments : 
	//      (In) : long device : handle returned from function "OpenDevice()"
    //		(In) : byte[] image : image returned from function "GetImage()"
    //	(In/Out) : byte[] itemplate : ANSI standard template created from image.
	// Return    : int : 
	//				   If this function successes, return none-zero, else 0. 	
    //------------------------------------------------------------------------------------------------//
	public native int CreateANSITemplate(long device,byte[] image, byte[] itemplate);
    //------------------------------------------------------------------------------------------------//
	// Purpose   : This function creates the ISO standard template from the uncompressed raw image. 
    // Function  : CreateISOTemplate
    // Arguments : void
	//      (In) : long device : handle returned from function "OpenDevice()"
    //		(In) : byte[] image : image returned from function "GetImage()"
    //  (In/Out) : byte[] itemplate : ISO standard template created from image.
	// Return    : int : 
	//				   If this function successes, return none-zero, else 0. 	
    //------------------------------------------------------------------------------------------------//
	public native int CreateISOTemplate(long device,byte[] image,  byte[] itemplate);
    //------------------------------------------------------------------------------------------------//
	// Purpose   : This function gets the quality value of fingerprint raw image. 
    // Function  : GetImageQuality
	// Arguments : 
	//      (In) : long device : handle returned from function "OpenDevice()"
    //		(In) : byte[] image : image returned from function "GetImage()"
	// Return    : int : 
	//				   return quality value(0~100) of fingerprint raw image. 	
    //------------------------------------------------------------------------------------------------//
	public native int GetImageQuality(long device,byte[] image);
    //------------------------------------------------------------------------------------------------//
	// Purpose   : This function gets the NFI quality value of fingerprint raw image. 
    // Function  : GetNFIQuality
	// Arguments : 
	//      (In) : long device : handle returned from function "OpenDevice()"
    //		(In) : byte[] image : image returned from function "GetImage()"
	// Return    : int : 
	//				   return NFI quality value(1~5) of fingerprint raw image. 	
    //------------------------------------------------------------------------------------------------//
	public native int GetNFIQuality(long device,byte[] image);
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function matches two templates and returns similar match score.
	//             This function is for 1:1 Matching and only used in fingerprint verification. 
    // Function  : CompareTemplates
	// Arguments : 
	//      	(In) : long device : handle returned from function "OpenDevice()"
	//			(In) : byte[] itemplateToMatch : template to match : 
	//                 This template must be used as that is created by function "CreateANSITemplate()"  
    //                 or function "CreateISOTemplate()".
    //			(In) : byte[] itemplateToMatched : template to be matched
	//                 This template must be used as that is created by function "CreateANSITemplate()"  
    //                 or function "CreateISOTemplate()".
	// Return    : int 
    //					return similar match score(0~100) of two fingerprint templates.
    //------------------------------------------------------------------------------------------------//
	public native int CompareTemplates(long device,byte[] itemplateToMatch, byte[] itemplateToMatched);
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function matches the appointed ANSI template against to ANSI template array of DATABASE.
	//             This function is for 1:N Matching and only used in fingerprint identification. 
    // Function  : SearchingANSITemplates
	// Arguments : 
	//      	(In) : long device : handle returned from function "OpenDevice()"
    //			(In) : byte[] itemplateToSearch : template to search
	//                 This template must be used as that is created by function "CreateANSITemplate()".  
    //			(In) : byte[] numberOfDbTemplates : number of templates to be searched.
    //			(In) : byte[] arrayOfDbTemplates : template array to be searched.
	//                 These templates must be used as that is created by function "CreateANSITemplate()".  
	//			(In) : int scoreThreshold : 
    //                 This argument is the threshold of similar match score for 1: N Matching.
	// Return    : int 
	//				   If successful, return index number of template searched inside template array, 
	//				   else -1. 	
    //------------------------------------------------------------------------------------------------//
	public native int SearchingANSITemplates(long device, byte[] itemplateToSearch, 
                                             int numberOfDbTemplates, byte[] arrayOfDbTemplates, int scoreThreshold);
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function matches the appointed ISO template against to ISO template array of DATABASE.
	//             This function is for 1:N Matching and only used in fingerprint identification. 
    // Function  : SearchingISOTemplates
	// Arguments : 
	//      	(In) : long device : handle returned from function "OpenDevice()"
    //			(In) : byte[] itemplateToSearch : template to search
	//                 This template must be used as that is created by function "CreateISOTemplate()".  
    //			(In) : byte[] numberOfDbTemplates : number of templates to be searched.
    //			(In) : byte[] arrayOfDbTemplates : template array to be searched.
	//                 These templates must be used as that is created by function "CreateISOTemplate()".  
	//			(In) : int scoreThreshold : 
    //                 This argument is the threshold of similar match score for 1: N Matching.
	// Return    : int 
	//				   If successful, return index number of template searched inside template array, 
	//				   else -1. 	
    //------------------------------------------------------------------------------------------------//
	public native int SearchingISOTemplates(long device, byte[] itemplateToSearch, 
                                            int numberOfDbTemplates, byte[] arrayOfDbTemplates, int scoreThreshold);
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function compresses raw fingerprint image by WSQ algorithm
    // Function  : CompressToWSQImage
    // Arguments :
    //      (In) : long device : handle returned from function "OpenDevice()"
    //		(In) : byte[] rawImage : fingerprint raw image
    //	(In/Out) : byte[] wsqImage : fingerprint image to be compressed by WSQ algorithm
    // Return    : long
    //					return size of image compressed by WSQ
    //------------------------------------------------------------------------------------------------//
    public native long  CompressToWSQImage (long device, byte[] rawImage, byte[] wsqImage);
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function uncompresses wsq fingerprint image by WSQ algorithm
    // Function  : UnCompressFromWSQImage
    // Arguments :
    //      (In) : long device : handle returned from function "OpenDevice()"
    //		(In) : byte[] wsqImage : compressed fingerprint image
    //		(In) : long wsqSize : compressed image size
    //	(In/Out) : byte[] rawImage : fingerprint image to be uncompressed
    // Return    : long
    //				return size of uncompressed image
    //------------------------------------------------------------------------------------------------//
    public native long  UnCompressFromWSQImage (long device, byte[] wsqImage, long wsqSize, byte[] rawImage);

    //******************** for anti-fake finger **********************************************************
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function loads DNN Model of checking liveness of finger
    // Function  : loadDNNModel
    // Arguments :
    //      (In) : String folderPath : the pathname of the folder for DNN Model
    //      (In) : String modelName : the name of DNN Model("HZFinger")
    // Return    : void
    //------------------------------------------------------------------------------------------------//
    public native void loadDNNModel(String folderPath, String modelName);
    //------------------------------------------------------------------------------------------------//
    // Purpose   : This function checks the liveness of fingerprint image
    // Function  : checkLiveFinger
    // Arguments :
    //      (In) : byte[] rawImage : fingerprint image, raw format
    //      (In) : int width : width of image
    //      (In) : int height : height of image
    //      (In) : float threshold : threshold for checking liveness of finger[0.5~0.1], 0.5 : security level 1, 0.1 : 5
    // Return    : int
    //		-1 : bad image, -100 : invalid image, 0 - fake finger, 1 - live finger
    //------------------------------------------------------------------------------------------------//
    public native int checkLiveFinger(byte[] rawImage, int width, int height, float threshold);

    //****************************************************************************************************
    public long LoadAsFile(String filename, byte[] buffer) {
        long ret = 0;
        // File extStorageDirectory = Environment.getExternalStorageDirectory();
        // File Dir = new File(extStorageDirectory, "Android");
        File Dir = m_content.getExternalFilesDir(null);
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
    //****************************************************************************************************
    public static boolean SaveAsFile(String filename, byte[] buffer, int len) {
        boolean ret = true;
        //File extStorageDirectory = Environment.getExternalStorageDirectory();
        //File Dir = new File(extStorageDirectory, "Android");
        File Dir = m_content.getExternalFilesDir(null);
        File file = new File(Dir, filename);
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(buffer, 0, len);
            out.close();
        } catch (Exception e) {
            ret = false;
        }
 
        return ret;
    }
}
