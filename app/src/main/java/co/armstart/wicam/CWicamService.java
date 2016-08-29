package co.armstart.wicam;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Hashtable;

import org.freedesktop.gstreamer.GStreamer;

// how to generate JNI function: https://blog.nishtahir.com/2015/11/11/setting-up-for-android-ndk-development/
// JNI programming guide: http://journals.ecs.soton.ac.uk/java/tutorial/native1.1/
public class CWicamService extends Service {

    private final Object mSync = new Object();
    private final Hashtable<String, CWicam> mCWicams = new Hashtable<>(); // search by wicam SSID

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (!action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) return;
            NetworkInfo ni = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (ni == null ) return;
            NetworkInfo.DetailedState state = ni.getDetailedState();
            String ssid = ni.getExtraInfo();
            switch (state) {
                case CONNECTED:
                case DISCONNECTED:
            }
        }
    };

    public class CWicam extends CWicamCallback{

        public Bundle mBundle = new Bundle();
        public int mState = WICAM_STATE_NULL;
        public long mWid = 0;
        public Messenger mMessenger = null;

        public static final int MAX_FRAMES_TO_STREAM = 300;
        protected int mMaxFrames = MAX_FRAMES_TO_STREAM;

        protected File media_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);



        public static final int WICAM_STATE_REBOOTING = -6;
        public static final int WICAM_STATE_PIPELINE_FATAL		= -5;
        public static final int WICAM_STATE_UPGRADE_FIRMWARE_FAILED = -4;
        public static final int WICAM_STATE_ENCODING_ERROR = -3;
        public static final int WICAM_STATE_LOGIN_FAILED = -2;
        public static final int WICAM_STATE_CONNECT_FAILED = -1;
        public static final int WICAM_STATE_NULL = 0;
        public static final int WICAM_STATE_CONNECTING = 1;
        public static final int WICAM_STATE_CONNECTED = 2;
        public static final int WICAM_STATE_LOGGED_IN = 3;
        public static final int WICAM_STATE_UPGRADE_FIRMWARE = 4;
        public static final int WICAM_STATE_VIDEO_STREAMING = 5;
        public static final int WICAM_STATE_PICTURE_SHOOT = 6;

        public static final int WICAM_STATE_LOGIN_REQUESTED = 100;
        public static final int WICAM_STATE_VIDEO_SHOOT_REQUESTED = 101;
        public static final int WICAM_STATE_VIDEO_STOP_REQUESTED = 102;

        public static final int H264_QUALITYT_LOW = 0;
        public static final int H264_QUALITYT_MEDIUM = 1;
        public static final int H264_QUALITYT_HIGH = 2;

        public static final int RESOLUTION_VGA = 0;
        public static final int RESOLUTION_XGA = 1;
        public static final int RESOLUTION_UXVGA = 2;

        public static final int STA_SEC_TYPE_OPEN = 0;
        public static final int STA_SEC_TYPE_WEP = 1;
        public static final int STA_SEC_TYPE_WPA2 = 2;
        public static final int STA_SEC_TYPE_WPS_PBC = 3;
        public static final int STA_SEC_TYPE_WPS_PIN = 4;
        public static final int STA_SEC_TYPE_WPA_ENT = 5;


        public CWicam () {
            super();
        }
        public CWicam setMessenger(Messenger msgr) {
            mMessenger = msgr;
            return this;
        }
        public CWicam setWID(long wid) {
            mWid = wid;
            return this;
        }
        public CWicam setState(int state) {
            mState = state;
            return this;
        }
        public CWicam setHomeWifi(String ssid, String pwd, int sec) {
            mBundle.putString(getString(R.string.sta_ssid), ssid);
            mBundle.putString(getString(R.string.sta_pin), pwd);
            mBundle.putInt(getString(R.string.sta_sec), sec);
            return this;
        }
        public CWicam setLogin(String ssid, String pwd) {
            mBundle.putString(getString(R.string.ap_ssid), ssid);
            mBundle.putString(getString(R.string.ap_pin), pwd);
            return this;
        }
        public CWicam setIP(String ip) {
            mBundle.putString(getString(R.string.ip), ip);
            return this;
        }

        public Bundle getProfile() {
            return mBundle;
        }
        @Override
        public void onFrame(boolean succ, int state, byte[] frame, long wid) {
            Message ms;
            mState = state;
            if (succ == false) {
                ms = Message.obtain(null, MSG_ONFRAME, MSG_FAILED, mState);
            } else if (frame[0] != (byte)0xFF || frame[1] != (byte)0xD8) {
                // invalid jpeg
                ms = Message.obtain(null, MSG_ONFRAME, MSG_INVALID_FRAME, mState);
                //mBundle.put
            } else {
                ms = Message.obtain(null, MSG_ONFRAME, MSG_OK, mState);
                Bundle dt = new Bundle();
                dt.putByteArray(getString(R.string.frame), frame);
                ms.setData(dt);
            }
            if (frame != null) Log.d("CWicam", "onFrame size=" + frame.length);
            //mMaxFrames--;
            //if (mMaxFrames == 0 && mState == WICAM_STATE_VIDEO_STREAMING) {
            //    mMaxFrames = MAX_FRAMES_TO_STREAM;
            //    cwicam_stop_media(wid, this);
            //}
            ms.obj = mBundle.getString(getString(R.string.ap_ssid));
            try {
                mMessenger.send(ms);
            }catch (RemoteException e) {}
        }
        @Override
        public void  onOpenResult(boolean success, int state, long wid) {
            Log.d("CWicam", "cwicam_open callback " + success);
            mState = state;
            if (success == false) {
                Message ms = Message.obtain(null, MSG_GET_DEV_INFO, MSG_FAILED, mState);
                ms.obj = mBundle.getString(getString(R.string.ap_ssid)); // ssid to pass
                try {
                    mState = WICAM_STATE_CONNECT_FAILED;
                    mMessenger.send(ms);
                    Log.d("CWicamService", "Message sent ");
                }catch(RemoteException e) { }
                return;
            }
            mWid = wid;
            // TODO: Login to get device info
            Log.d("CWicam", "cwicam_login " + wid);
            cwicam_login(wid,
                    mBundle.getString(getString(R.string.ap_ssid)),
                    mBundle.getString(getString(R.string.ap_pin)),
                    this);
        }
        @Override
        public void onLoginResult(boolean success, int state, byte fw_version,
                                  String ap_ssid, String ap_pin, String sta_ssid, String sta_pin, byte sta_sec) {
            Message ms;
            mState = state;
            if (success == false) {
                ms = Message.obtain(null, MSG_GET_DEV_INFO, MSG_FAILED, mState);
                ms.obj = mBundle.getString(getString(R.string.ap_ssid)); // ssid to pass
                try {
                    mMessenger.send(ms);
                } catch (RemoteException e) {
                    // Activity gone? App gone? Pss, Forget it.
                }
                return;
            }
            // register itself for media callback
            cwicam_set_media_callback(mWid, this);

            mBundle.putByte(getString(R.string.fw_version), fw_version);
            mBundle.putString(getString(R.string.ap_ssid), ap_ssid);
            mBundle.putString(getString(R.string.ap_pin), ap_pin);
            mBundle.putString(getString(R.string.sta_ssid), sta_ssid);
            mBundle.putString(getString(R.string.sta_pin), sta_pin);
            mBundle.putByte(getString(R.string.sta_sec), sta_sec);

            Log.d("CWicam", "Login success. FW_VERSION= " + fw_version);
            ms = Message.obtain(null, MSG_GET_DEV_INFO, MSG_OK, mState);
            ms.obj = mBundle.getString(getString(R.string.ap_ssid)); // ssid to pass
            ms.setData(mBundle);
            try {
                mMessenger.send(ms);
            } catch (RemoteException e) { }

            ////////////////////////////////////////////////////
        }
        @Override
        public void onCloseResult(boolean success, int state) {
            Log.d("CWicamService", "onCloseResult:" + success + " wid=" + mWid);
            Message ms;
            mState = state;
            synchronized (mSync) {
                mCWicams.remove(mBundle.getString(getString(R.string.ap_ssid)));
            }
            if (success == false) {
                ms = Message.obtain(null, MSG_CLOSE, MSG_FAILED, mState);
            } else {
                ms = Message.obtain(null, MSG_CLOSE, MSG_OK, mState);
            }
            ms.obj = mBundle.getString(getString(R.string.ap_ssid));
            try {
                mMessenger.send(ms);
            } catch (RemoteException e) {}

        }
        @Override
        public void onStartVideoResult(boolean success, int state) {
            Message ms;
            mState = state;
            if (success == false) {
                ms = Message.obtain(null, MSG_START_VIDEO, MSG_FAILED, mState);
            } else {
                ms = Message.obtain(null, MSG_START_VIDEO, MSG_OK, mState);
            }
            ms.obj = mBundle.getString(getString(R.string.ap_ssid));
            try {
                mMessenger.send(ms);
            } catch (RemoteException e) {}

        }
        @Override
        public void onStopMediaResult(boolean success, int state) {
            mState = state;
        }
        @Override
        public void onStartPictureResult(boolean success, int state) {
            Message ms;
            mState = state;
            if (success == false) {
                ms = Message.obtain(null, MSG_START_PICTURE, MSG_FAILED, mState);
            } else {
                ms = Message.obtain(null, MSG_START_PICTURE, MSG_OK, mState);
            }
            ms.obj = mBundle.getString(getString(R.string.ap_ssid));
            try {
                mMessenger.send(ms);
            } catch (RemoteException e) {}
        }
        @Override
        public void onFWUpgradeResult(boolean success, int state, int progress) {
            Message ms;
            mState = state;
            if (success == false) {
                Log.d("CWicamService", "onFWUpgradeResult ERROR " + mState);
                ms = Message.obtain(null, MSG_UPDATE_FW, MSG_FAILED, mState);
            } else {
                Log.d("CWicamService", "onFWUpgradeResult SUCCESS. Progress= " + progress);
                ms = Message.obtain(null, MSG_UPDATE_FW, MSG_OK, progress);
            }
            ms.obj = mBundle.getString(getString(R.string.ap_ssid));
            try {
                mMessenger.send(ms);
            } catch (RemoteException e) {}
        }
        @Override
        public void onConfUpdateResult(boolean success, int state, byte fw_version,
                                String ap_ssid, String ap_pin, String sta_ssid, String sta_pin, byte sta_sec) {
            Message ms;
            mState = state;
            if (success == false) {
                ms = Message.obtain(null, MSG_UPDATE_CONF, MSG_FAILED, mState);

            } else {
                String old_ssid = mBundle.getString(getString(R.string.ap_ssid));
                synchronized (mSync) {
                    mBundle.putByte(getString(R.string.fw_version), fw_version);
                    mBundle.putString(getString(R.string.ap_ssid), ap_ssid);
                    mBundle.putString(getString(R.string.ap_pin), ap_pin);
                    mBundle.putString(getString(R.string.sta_ssid), sta_ssid);
                    mBundle.putString(getString(R.string.sta_pin), sta_pin);
                    mBundle.putByte(getString(R.string.sta_sec), sta_sec);
                    if (old_ssid != ap_ssid) {
                        mCWicams.remove(old_ssid);
                        mCWicams.put(ap_ssid, this);
                    }
                }
                ms = Message.obtain(null, MSG_UPDATE_CONF, MSG_OK, mState);
                ms.setData(mBundle);
            }
            ms.obj = ap_ssid;
            try {
                mMessenger.send(ms);
            }catch (RemoteException e) {}
        }
        @Override
        public void onBatteryLevelResult(boolean success, int state, int batt) {
            Message ms;
            if (success == false) {
                ms = Message.obtain(null, MSG_BATTERY_LEVEL, MSG_FAILED, mState);
            } else {
                ms = Message.obtain(null, MSG_BATTERY_LEVEL, MSG_OK, batt);
            }
            ms.obj = mBundle.getString(getString(R.string.ap_ssid));
            try {
                mMessenger.send(ms);
            } catch (RemoteException e) {}
        }
    }

    public static final int MSG_GET_DEV_INFO = 1;
    public static final int MSG_START_VIDEO = 2;
    public static final int MSG_START_PICTURE = 3;
    public static final int MSG_UPDATE_CONF = 4;
    public static final int MSG_GET_CONF = 5;
    public static final int MSG_CLOSE = 6;
    public static final int MSG_UPDATE_FW = 7;
    public static final int MSG_ONFRAME = 8;
    public static final int MSG_STOP_MEDIA = 9;
    public static final int MSG_BATTERY_LEVEL = 10;

    public static final int MSG_BIND_WIFI = 101;
    public static final int MSG_UNBIND_WIFI = 102;



    public static final int MSG_OK             = 0;
    public static final int MSG_FAILED         = -1;
    public static final int MSG_STATE_NOT_RIGHT  = -2;
    public static final int MSG_NOT_FOUND      = -3;
    public static final int MSG_INVALID_FRAME    = -4;





    private HandlerThread mThread;
    private IBinder mBinder = new LocalBinder();
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET_DEV_INFO:
                    handle_MSG_GET_DEV_INFO(msg);
                    break;
                case MSG_START_VIDEO:
                    handle_MSG_START_VIDEO(msg);
                    break;
                case MSG_START_PICTURE:
                    handle_MSG_START_PICTURE(msg);
                    break;
                case MSG_UPDATE_CONF:
                    handle_MSG_UPDATE_CONF(msg);
                    break;
                case MSG_GET_CONF:
                    handle_MSG_GET_CONF(msg);
                    break;
                case MSG_CLOSE:
                    handle_MSG_CLOSE(msg);
                    break;
                case MSG_UPDATE_FW:
                    handle_MSG_UPDATE_FW(msg);
                    break;

                case MSG_BIND_WIFI:
                    handle_MSG_BIND_WIFI(msg);
                    break;
                case MSG_UNBIND_WIFI:
                    handle_MSG_UNBIND_WIFI(msg);
                    break;

                case MSG_STOP_MEDIA:
                    handle_MSG_STOP_MEDIA(msg);
                    break;
                case MSG_BATTERY_LEVEL:
                    handle_MSG_BATTERY_LEVEL(msg);
                    break;
                default:
                    Log.e("CWicamService", "Uknown message");
                    super.handleMessage(msg);
            }
        }
    }

    private void handle_MSG_BATTERY_LEVEL(Message msg) {
        Log.d("CWicamService", "handle_MSG_BATTERY_LEVEL()");
        final Messenger msgr = (Messenger)msg.obj;
        Bundle bd = msg.getData();
        final String ssid = bd.getString(getString(R.string.ap_ssid));
        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(ssid);
            if (wc != null) wc.setMessenger(msgr);
        }

        if (wc == null ) {
            Message ms = Message.obtain(null, MSG_BATTERY_LEVEL, MSG_NOT_FOUND, 0);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else if (wc.mState != CWicam.WICAM_STATE_LOGGED_IN) { // state not right
            Message ms = Message.obtain(null, MSG_BATTERY_LEVEL, MSG_STATE_NOT_RIGHT, wc.mState);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else {
            cwicam_req_battery_level(wc.mWid, wc);
        }
    }

    private void handle_MSG_GET_DEV_INFO(Message msg) {
        Log.d("CWicamService", "CWicamService cwicam_open()");
        final Messenger msgr = (Messenger)msg.obj;
        Bundle bd = msg.getData();
        final String ssid = bd.getString(getString(R.string.ap_ssid));
        final String password = bd.getString(getString(R.string.ap_pin));
        final String ip = bd.getString(getString(R.string.ip));
        // check existence


        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(ssid);
            if (wc == null) {
                wc = new CWicam();
                mCWicams.put(ssid, wc);
            }
        }

        wc.setLogin(ssid, password).setMessenger(msgr);

        long wid = wc.mWid;

        if (wc.mState < CWicam.WICAM_STATE_CONNECTED) { // disconnected, then open!
            // No, then open it!
            cwicam_open(ip, wc); //cwicam_open();

        } else if (wc.mState == CWicam.WICAM_STATE_CONNECTED) {
            cwicam_login(wid, ssid, password, wc);
        } else {
            Message ms = Message.obtain(null, MSG_GET_DEV_INFO, MSG_OK, wc.mState);
            ms.setData(wc.getProfile());
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        }

    }

    private void handle_MSG_STOP_MEDIA(Message msg) {
        Bundle bd = msg.getData();
        final String ssid = bd.getString(getString(R.string.ap_ssid));
        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(ssid);
        }

        if (wc != null && wc.mState >= CWicam.WICAM_STATE_LOGGED_IN) {
            Log.d("CWicamService", "calling cwicam_stop_media");
            cwicam_stop_media(wc.mWid, wc);
        }
    }
    private void handle_MSG_START_VIDEO(Message msg) {
        final Messenger msgr = (Messenger)msg.obj;
        Bundle bd = msg.getData();
        final String ssid = bd.getString(getString(R.string.ap_ssid));
        final String mp4_path = bd.getString(getString(R.string.mp4_path));
        final int h264_quality = bd.getInt(getString(R.string.h264_quality));
        final int resolution = bd.getInt(getString(R.string.resolution));
        // check if wicam is connected,
        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(ssid);
            if (wc != null) wc.setMessenger(msgr);
        }
        if (wc == null ) {
            Message ms = Message.obtain(null, MSG_START_VIDEO, MSG_NOT_FOUND, 0);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else if (wc.mState < CWicam.WICAM_STATE_LOGGED_IN) { // state not right
            Message ms = Message.obtain(null, MSG_START_VIDEO, MSG_STATE_NOT_RIGHT, wc.mState);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else if (wc.mState == CWicam.WICAM_STATE_VIDEO_STREAMING) { // if already in streaming, do nothing.
            // do nothing
        } else if (wc.mState == CWicam.WICAM_STATE_LOGGED_IN) {
            cwicam_start_video(wc.mWid, mp4_path, h264_quality, resolution, wc);
        } else {
            Message ms = Message.obtain(null, MSG_START_VIDEO, MSG_STATE_NOT_RIGHT, wc.mState);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        }
    }
    private void handle_MSG_START_PICTURE(Message msg) {
        final Messenger msgr = (Messenger)msg.obj;
        Bundle bd = msg.getData();
        final String ssid = bd.getString(getString(R.string.ap_ssid));
        final String jpeg_path = bd.getString(getString(R.string.jpeg_path));
        final int resolution = bd.getInt(getString(R.string.resolution));
        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(ssid);
            if (wc != null) wc.setMessenger(msgr);
        }
        if (wc == null ) {
            Message ms = Message.obtain(null, MSG_START_PICTURE, MSG_NOT_FOUND, 0);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else if (wc.mState < CWicam.WICAM_STATE_LOGGED_IN) { // state not right
            Message ms = Message.obtain(null, MSG_START_PICTURE, MSG_STATE_NOT_RIGHT, wc.mState);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else if (wc.mState == CWicam.WICAM_STATE_VIDEO_STREAMING) { // if already in streaming, stop streaming.
            cwicam_stop_media(wc.mWid, new CWicamCallback(wc) {
                @Override
                public void onStopMediaResult(boolean success, int state) {
                    CWicam wc = (CWicam)mAssocate;
                    wc.mState = state;
                    if (success == false) {
                        Message ms = Message.obtain(null, MSG_START_PICTURE, MSG_STATE_NOT_RIGHT, wc.mState);
                        ms.obj = ssid;
                        try {
                            msgr.send(ms);
                        }catch (RemoteException e) {}
                        return;
                    }
                    cwicam_start_picture(wc.mWid, jpeg_path, resolution, wc);
                }
            });
        } else if (wc.mState == CWicam.WICAM_STATE_LOGGED_IN || wc.mState == CWicam.WICAM_STATE_PICTURE_SHOOT) {
            cwicam_start_picture(wc.mWid, jpeg_path, resolution, wc);
        } else {
            Message ms = Message.obtain(null, MSG_START_PICTURE, MSG_STATE_NOT_RIGHT, wc.mState);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        }
    }
    private void handle_MSG_UPDATE_CONF(Message msg) {
        final Messenger msgr = (Messenger)msg.obj;
        Bundle bd = msg.getData();
        final String old_ssid = bd.getString(getString(R.string.old_ssid));
        final String ap_ssid = bd.getString(getString(R.string.ap_ssid));
        final String ap_pin = bd.getString(getString(R.string.ap_pin));
        final String sta_ssid = bd.getString(getString(R.string.sta_ssid));
        final String sta_pin = bd.getString(getString(R.string.sta_pin));
        final byte sta_sec = bd.getByte(getString(R.string.sta_sec));
        Log.d("CWicamService", "handle_MSG_UPDATE_CONF 1");
        Log.d("CWicamService", " ap_ssid:" +ap_ssid+ " ap_pin:" +ap_pin+ " sta_ssid:" +sta_ssid+ " sta_pin:" +sta_pin+ " " );
        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(old_ssid);
            if (wc != null) wc.setMessenger(msgr);
        }
        if (wc == null ) {
            Log.d("CWicamService", "handle_MSG_UPDATE_CONF 2");
            Message ms = Message.obtain(null, MSG_UPDATE_CONF, MSG_NOT_FOUND, 0);
            ms.obj = ap_ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else if (wc.mState == CWicam.WICAM_STATE_VIDEO_STREAMING || wc.mState == CWicam.WICAM_STATE_PICTURE_SHOOT) {
            Log.d("CWicamService", "handle_MSG_UPDATE_CONF 3");
            cwicam_stop_media(wc.mWid, new CWicamCallback(wc) {
                @Override
                public void onStopMediaResult(boolean success, int state) {
                    CWicam wc = (CWicam)mAssocate;
                    wc.mState = state;
                    if (success == false) {
                        Message ms = Message.obtain(null, MSG_UPDATE_CONF, MSG_STATE_NOT_RIGHT, wc.mState);
                        ms.obj = ap_ssid;
                        try {
                            msgr.send(ms);
                        }catch (RemoteException e) {}
                        return;
                    }
                    // update
                    cwicam_update_conf(wc.mWid, (byte)0, ap_ssid, ap_pin, sta_ssid, sta_pin, sta_sec, wc);
                }
            });
        } else if (wc.mState == CWicam.WICAM_STATE_LOGGED_IN) {
            Log.d("CWicamService", "handle_MSG_UPDATE_CONF 4");
            cwicam_update_conf(wc.mWid, (byte)0, ap_ssid, ap_pin, sta_ssid, sta_pin, sta_sec, wc);
        } else {
            Message ms = Message.obtain(null, MSG_UPDATE_CONF, MSG_STATE_NOT_RIGHT, wc.mState);
            ms.obj = ap_ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        }

    }
    private void handle_MSG_GET_CONF(Message msg) {
        final Messenger msgr = (Messenger)msg.obj;
        Bundle bd = msg.getData();
        final String ssid = bd.getString(getString(R.string.ap_ssid));
        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(ssid);
            if (wc != null) wc.setMessenger(msgr);
        }

        if (wc.mState < CWicam.WICAM_STATE_LOGGED_IN) {
            Message ms = Message.obtain(null, MSG_GET_CONF, MSG_STATE_NOT_RIGHT, wc.mState);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else {
            Message ms = Message.obtain(null, MSG_GET_CONF, MSG_OK, wc.mState);
            ms.setData(wc.getProfile());
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        }

    }

    private void handle_MSG_CLOSE(Message msg) {
        final Messenger msgr = (Messenger)msg.obj;
        Bundle bd = msg.getData();
        final String ssid = bd.getString(getString(R.string.ap_ssid));
        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(ssid);
        }

        if (wc != null) {
            wc.setMessenger(msgr);
            cwicam_close(wc.mWid, wc);
        } else {
            Message ms = Message.obtain(null, MSG_CLOSE, MSG_OK, CWicam.WICAM_STATE_NULL);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        }
    }

    private void handle_MSG_UPDATE_FW(Message msg) {
        final Messenger msgr = (Messenger)msg.obj;
        Bundle bd = msg.getData();
        final String ssid = bd.getString(getString(R.string.ap_ssid));
        final byte[] fw = bd.getByteArray(getString(R.string.firmware));
        Log.d("handle_MSG_UPDATE_FW", " = 1 ");
        CWicam wc = null;
        synchronized (mSync) {
            wc = mCWicams.get(ssid);
            if (wc != null) wc.setMessenger(msgr);
        }
        if (wc == null) {
            Message ms = Message.obtain(null, MSG_UPDATE_FW, MSG_NOT_FOUND, 0);
            ms.obj = ssid;
            try {
                msgr.send(ms);
            }catch (RemoteException e) {}
        } else if (wc.mState == CWicam.WICAM_STATE_VIDEO_STREAMING || wc.mState == CWicam.WICAM_STATE_PICTURE_SHOOT) {
            // stop all media first
            Log.d("handle_MSG_UPDATE_FW", " = 2 ");
            cwicam_stop_media(wc.mWid, new CWicamCallback(wc) {
                @Override
                public void onStopMediaResult(boolean success, int state) {
                    CWicam wc = (CWicam)mAssocate;
                    wc.mState = state;
                    if (success == false) {
                        Message ms = Message.obtain(null, MSG_UPDATE_FW, MSG_STATE_NOT_RIGHT, wc.mState);
                        ms.obj = ssid;
                        try {
                            msgr.send(ms);
                        }catch (RemoteException e) {}
                        return;
                    }
                    // update
                    cwicam_fw_upgrade(wc.mWid, fw, wc);
                }
            });
        } else {
            Log.d("handle_MSG_UPDATE_FW", " = 3 ");
            cwicam_fw_upgrade(wc.mWid, fw, wc);
        }

    }


    private void handle_MSG_BIND_WIFI(Message msg) {
        final Messenger msgr = (Messenger)msg.obj;
        NetworkRequest.Builder builder;
        builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        cm.requestNetwork(builder.build(), new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                boolean ret;
                Message ms;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ret = cm.bindProcessToNetwork(network);
                } else {
                    ret = ConnectivityManager.setProcessDefaultNetwork(network);
                }
                if (ret == true) {
                    Log.d("handle_MSG_BIND_WIFI", "success");
                    ms = Message.obtain(null, MSG_BIND_WIFI, MSG_OK, 0);
                } else {
                    Log.d("handle_MSG_BIND_WIFI", "failure");
                    ms = Message.obtain(null, MSG_BIND_WIFI, MSG_FAILED, 0);
                }
                try {
                    msgr.send(ms);
                } catch (RemoteException e) { }
                cm.unregisterNetworkCallback(this);
            }// onAvailable
        });
    }

    private void handle_MSG_UNBIND_WIFI(Message msg) {
        Messenger msgr = (Messenger)msg.obj;
        boolean ret;
        Message ms;
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ret = cm.bindProcessToNetwork(null);
        } else {
            ret = ConnectivityManager.setProcessDefaultNetwork(null);
        }
        if (ret) {
            ms = Message.obtain(null, MSG_UNBIND_WIFI, MSG_OK, 0);
        } else {
            ms = Message.obtain(null, MSG_UNBIND_WIFI, MSG_FAILED, 0);
        }
        try {
            msgr.send(ms);
        } catch (RemoteException e) { }
    }


    public class LocalBinder extends Binder {
        CWicamService getService() {
            return CWicamService.this;
        }
    }

    @Override
    public void onCreate() {
        // initialize gstreamer
        try {
            GStreamer.init(this);
        } catch (Exception e) {
            stopSelf();
            return;
        }
        mThread = new HandlerThread("CWicamHandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        mServiceLooper = mThread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
        registerReceiver(mBroadcastReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));



    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        // check type of command we received
        int what = intent.getIntExtra("what", -1);
        if (what == -1) {
            stopSelf(startId);
            return START_STICKY;
        }

        // prepare a message
        Message msg = mServiceHandler.obtainMessage();
        msg.what = what;
        msg.obj = intent.getParcelableExtra("Messenger");
        msg.arg1 = startId;
        msg.setData(intent.getBundleExtra(getString(R.string.params)));
        // send a message
        mServiceHandler.sendMessage(msg);
        Log.d("CWicamService", "service started. Message Type: " + what);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
        unregisterReceiver(mBroadcastReceiver);
        mThread.quit();
    }

    private InetAddress getBroadcastAddress() throws IOException {
        WifiManager myWifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        DhcpInfo myDhcpInfo = myWifiManager.getDhcpInfo();
        if (myDhcpInfo == null) {
            return null;
        }
        int broadcast = (myDhcpInfo.ipAddress & myDhcpInfo.netmask)
                | ~myDhcpInfo.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    static {
        try {
            System.loadLibrary("gstreamer_android");
            System.loadLibrary("cwicam");
        } catch(UnsatisfiedLinkError e) {
            Log.e("CWicamService", "Warning: could not load" + e.getMessage());
        }
    }

    //javah -d jni -classpath $ANDROID_HOME/platforms/android-23/android.jar:$ANDROID_HOME/extras/android/support/v7/appcompat/libs/android-support-v7-appcompat.jar:$ANDROID_HOME/extras/android/support/v7/appcompat/libs/android-support-v4.jar:../../build/intermediates/classes/debug co.armstart.wicam.CWicamService
    public native void cwicam_open(String address, CWicamCallback callback);
    public native void cwicam_login(long wid, String ssid, String password, CWicamCallback callback);
    public native void cwicam_close(long wid, CWicamCallback cWicamCloseCallback);
    public native void cwicam_set_media_callback (long wid, CWicamCallback callback);
    public native void cwicam_clear_media_callback(long wid);
    public native void cwicam_start_video(long wid, String mp4_path, int h264_quality, int resolution, CWicamCallback callback);
    public native void cwicam_stop_media(long wid, CWicamCallback callback);
    public native void cwicam_start_picture(long wid, String jpeg_path, int resolution, CWicamCallback callback);
    public native void cwicam_fw_upgrade(long wid, byte[] fw_data, CWicamCallback callback);
    public native void cwicam_update_conf(long wid, byte switch_mode, String ap_ssid, String ap_pin, String sta_ssid, String sta_pin, byte sta_sec, CWicamCallback calllback);
    public native void cwicam_req_battery_level(long wid, CWicamCallback callback);
    public static native String upnpc_get_external_ip();
    public static native String upnpc_add_port(String lan_address, int lan_port, int wan_port, int duration);
    public static native boolean upnpc_remove_port(int wan_port);
    public static native String[] upnpc_list_ports();
}
