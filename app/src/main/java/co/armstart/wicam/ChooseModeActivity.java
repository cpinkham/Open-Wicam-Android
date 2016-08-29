package co.armstart.wicam;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.BoolRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.StringTokenizer;

public class ChooseModeActivity extends AppCompatActivity {

    public static final int WAN_PORT_START = 4557;
    public static final int WAN_PORT_MAX = 6000;

    public final static int APP_FW_VERSION = 2;
    private Bundle mBundle;
    protected Object mSync = new Object();
    protected File media_path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");;

    protected EditText mApSSID;
    protected EditText mAPPIN;
    protected EditText mStaSSID;
    protected EditText mStaPIN;
    protected Button   mEditProfile;
    protected Button   mVideoMode;
    protected Button   mPictureMode;
    protected Button   mFWUpdate;
    protected TextView mTextMessage;
    protected ProgressBar mFWUpdateProgress;
    protected ProgressBar mBatteryLevel;
    protected Switch      mRemoteSW;
    protected LinearLayout mSettingsLayout;


    public void RestartWicam() {
        // start
        Intent it;
        String mode = mBundle.getString(getString(R.string.mode));
        if (mode.equals(getString(R.string.outdoor))) {
            it = new Intent(this, HotspotActivity.class);

        } else if (mode.equals(getString(R.string.home))) {
            it = new Intent(this, LanActivity.class);
        } else if (mode.equals(getString(R.string.cloud))) {
            it = new Intent(this, PeerActivity.class);
        } else {
            it = new Intent();
        }
        it.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        it.putExtra(getString(R.string.params), mBundle);
        startActivity(it);
    }

    private Handler mIncomingHandler = new Handler () {
        @Override
        public void handleMessage(Message msg) {
            Log.d("ChooseModeActivity", "received message");
            switch (msg.what) {
                case CWicamService.MSG_START_PICTURE:
                    handle_MSG_START_PICTURE(msg);
                    break;
                case CWicamService.MSG_UPDATE_CONF:
                    handle_MSG_UPDATE_CONF(msg);
                    break;
                case CWicamService.MSG_UPDATE_FW:
                    handle_MSG_UPDATE_FW(msg);
                    break;
                case CWicamService.MSG_ONFRAME:
                    handle_MSG_ONFRAME(msg);
                    break;
                case CWicamService.MSG_CLOSE:
                    handle_MSG_CLOSE(msg);
                    break;
                case CWicamService.MSG_BATTERY_LEVEL:
                    handle_MSG_BATTERY_LEVEL(msg);
                    break;
            }
        }
    };

    final Messenger mMessenger = new Messenger(mIncomingHandler);

    public void handle_MSG_START_PICTURE(Message msg) {
        if (msg.arg1 != CWicamService.MSG_OK) {
            mPictureMode.setEnabled(true);
            Toast.makeText(this, "Picture mode not started.", Toast.LENGTH_SHORT).show();
            mTextMessage.setText("Picture mode not started.");
            return;
        }
        Toast.makeText(this, "Picture mode started.", Toast.LENGTH_SHORT).show();
    }

    public void handle_MSG_UPDATE_CONF(Message msg) {
        if (msg.arg1 != CWicamService.MSG_OK) {
            Toast.makeText(this, "Failed saving Wicam configuration.", Toast.LENGTH_SHORT).show();
            mTextMessage.setText("Failed saving Wicam configuration. state = " + msg.arg2);
            return;
        }
        mApSSID.setEnabled(false);
        mAPPIN.setEnabled(false);
        mStaSSID.setEnabled(false);
        mStaPIN.setEnabled(false);
        mEditProfile.setText(getString(R.string.edit_wicam_settings));
        propagateNewConf(msg.getData());
    }

    public void handle_MSG_CLOSE(Message msg) {
        // start
        Intent it;
        String mode = mBundle.getString(getString(R.string.mode));
        if (mode.equals(getString(R.string.outdoor))) {
            it = new Intent(this, HotspotActivity.class);

        } else if (mode.equals(getString(R.string.home))) {
            it = new Intent(this, LanActivity.class);
        } else if (mode.equals(getString(R.string.cloud))) {
            it = new Intent(this, PeerActivity.class);
        } else {
            it = new Intent();
        }
        it.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        it.putExtra(getString(R.string.params), mBundle);
        startActivity(it);
    }

    public void handle_MSG_BATTERY_LEVEL(Message msg) {
        if (msg.arg1 != CWicamService.MSG_OK) {
            mTextMessage.setText("No battery level available");
            return;
        }
        int batt_level = msg.arg2;
        double volt = (batt_level * 4.2)/ 633.623;
        mTextMessage.setText("Battery Voltage=" + String.format("%.2f", volt));
        //Toast.makeText(this, "Battery ADC=" + batt_level, Toast.LENGTH_SHORT).show();
        volt -= 2.95;
        int percent = (int)((volt/1.25)*100);
        Log.d("Battery", "Battery Progess=" + percent);
        percent = percent > 100? 100:percent;

        mBatteryLevel.setProgress(percent);
    }

    public void handle_MSG_UPDATE_FW(Message msg) {
        if (msg.arg1 != CWicamService.MSG_OK) {
            Toast.makeText(this, "Firmware Upgrade failed.", Toast.LENGTH_SHORT).show();
            mTextMessage.setText("Firmware Upgrade failed. state = " + msg.arg2);
            return;
        }
        int progress = msg.arg2;
        mFWUpdateProgress.setProgress(progress);
        if (progress != 100) return;
        Toast.makeText(this, "Firmware Upgrade success! WiCam is rebooting.", Toast.LENGTH_SHORT).show();
        mTextMessage.setText("Firmware Upgrade success! WiCam is rebooting.");
    }
    public void handle_MSG_ONFRAME(Message msg) {
        mPictureMode.setEnabled(true);
        if (msg.arg1 != CWicamService.MSG_OK) {
            switch(msg.arg1) {
                case CWicamService.MSG_FAILED:
                    Toast.makeText(this, "Picture failed. We might lost the connection.", Toast.LENGTH_SHORT).show();
                    mTextMessage.setText("Picture failed. We might lost the connection.");
                    break;
                case CWicamService.MSG_INVALID_FRAME:
                    Toast.makeText(this, "Invalid data received. Try again or restart app and Wicam.", Toast.LENGTH_SHORT).show();
                    mTextMessage.setText("Invalid data received. Try again or restart app and Wicam.");
                    break;
            }
            return;
        }
        Bundle dt = msg.getData();
        if (dt == null) {
            Toast.makeText(this, "Picture failed. Unexpected exception.", Toast.LENGTH_SHORT).show();
            mTextMessage.setText("Picture failed. Unexpected exception.");
            return;
        }
        byte[] frame = dt.getByteArray(getString(R.string.frame));

        String jpeg_path = mBundle.getString(getString(R.string.jpeg_path));
        File file = new File(jpeg_path);
        if (file.exists() == false) {
            Toast.makeText(this, "Unexpected error. Picture failed saving on your phone.", Toast.LENGTH_SHORT).show();
            mTextMessage.setText("Unexpected error. Picture failed saving on your phone.");
            return;
        }
        Intent it = new Intent(Intent.ACTION_VIEW);
        it.setDataAndType(Uri.fromFile(file), "image/jpeg");
        startActivity(it);
        mTextMessage.setText("");

    }

    private TextView.OnEditorActionListener mApSSID_listener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
            String dvalue = mBundle.getString(getString(R.string.ap_ssid));
            if (id == EditorInfo.IME_ACTION_NEXT) {
                validateWiCamName();
                return false;
            } else {
                mApSSID.setError("Unexpected error");
                mApSSID.requestFocus();
                mApSSID.setText(dvalue);
            }

            return false;

        } // on EditorAction
    };

    private TextView.OnEditorActionListener mStaPIN_listener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
            if (id == EditorInfo.IME_ACTION_NEXT) {
                validateWiCamPin();
                return false;
            }

            return false;

        } // on EditorAction
    };

    private CompoundButton.OnCheckedChangeListener mRemoteSW_Listenser = new CompoundButton.OnCheckedChangeListener () {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            onRemoteSWChanged(isChecked);
        }
    };

    private OnClickListener mBatt_Listener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            onBatteryLevelRequest();
        }
    };

    private void onBatteryLevelRequest() {
        if (!mBundle.getString(getString(R.string.mode)).equals(getString(R.string.cloud))) {
            mTextMessage.setText("Getting battery level...");
            Intent intent2 = new Intent(this, CWicamService.class);
            intent2.putExtra("Messenger", mMessenger);
            intent2.putExtra("what", CWicamService.MSG_BATTERY_LEVEL);
            intent2.putExtra(getString(R.string.params), mBundle);
            startService(intent2);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_mode);

        mSettingsLayout = (LinearLayout)findViewById(R.id.settings_layout);
        mApSSID = (EditText)findViewById(R.id.ap_ssid_edit);
        mAPPIN = (EditText)findViewById(R.id.ap_pin_edit);
        mStaSSID = (EditText)findViewById(R.id.sta_ssid_edit);
        mStaPIN = (EditText)findViewById(R.id.sta_pin_edit);
        mPictureMode = (Button)findViewById(R.id.btn_picture_mode);
        mFWUpdate = (Button)findViewById(R.id.update_firmware_btn);
        mEditProfile = (Button)findViewById(R.id.btn_edit_profile);
        mEditProfile.setText(getString(R.string.edit_wicam_settings));

        mTextMessage = (TextView)findViewById(R.id.msg_text);
        mTextMessage.setTextColor(ContextCompat.getColor(this, R.color.errorText));

        mFWUpdateProgress = (ProgressBar)findViewById(R.id.fw_update_progress);
        mBatteryLevel = (ProgressBar)findViewById(R.id.battery_level);

        mBatteryLevel.setOnClickListener(mBatt_Listener);

        mRemoteSW = (Switch)findViewById(R.id.remote_sw);

        mRemoteSW.setChecked(false);

        mApSSID.setEnabled(false);
        mAPPIN.setEnabled(false);
        mStaSSID.setEnabled(false);
        mStaPIN.setEnabled(false);

        mApSSID.setOnEditorActionListener(mApSSID_listener);
        mStaPIN.setOnEditorActionListener(mStaPIN_listener);

        Intent intent = getIntent();
        if (intent == null ||
                intent.getBundleExtra(getString(R.string.params)) == null) {
            finish();
            return;
        }
        mBundle = intent.getBundleExtra(getString(R.string.params));
        // currently only WPA/WPA2 supported.
        mBundle.putByte(getString(R.string.sta_sec), (byte)CWicamService.CWicam.STA_SEC_TYPE_WPA2);

        // check
        String ms = getSharedPreferences("Wicam", Context.MODE_PRIVATE).getString(mBundle.getString(getString(R.string.ap_ssid)), null);
        Log.d("ChooseModeActivity", "getPreferences: " + ms);
        mApSSID.setText(mBundle.getString(getString(R.string.ap_ssid)));
        mAPPIN.setText(mBundle.getString(getString(R.string.ap_pin)));
        mStaSSID.setText(mBundle.getString(getString(R.string.sta_ssid)));
        mStaPIN.setText(mBundle.getString(getString(R.string.sta_pin)));

        if (!mBundle.getString(getString(R.string.mode)).equals(getString(R.string.home))) {
            mRemoteSW.setVisibility(View.GONE);
        } else if (mBundle.containsKey(getString(R.string.lan_address)) &&
                mBundle.containsKey(getString(R.string.wan_address)) &&
                mBundle.containsKey(getString(R.string.wan_port)) &&
                mBundle.getString(getString(R.string.lan_address)).length() > 0 &&
                mBundle.getString(getString(R.string.wan_address)).length() > 0 &&
                mBundle.getInt(getString(R.string.wan_port)) != 0) {
            mRemoteSW.setChecked(true);
        }
        mRemoteSW.setOnCheckedChangeListener(mRemoteSW_Listenser);


        Log.d("Compare", mBundle.getByte(getString(R.string.fw_version)) + " vs " + APP_FW_VERSION);
        if (mBundle.getString(getString(R.string.mode)).equals(getString(R.string.cloud))) {
            mFWUpdate.setVisibility(View.GONE);
            mFWUpdateProgress.setVisibility(View.GONE);
        } else if (mBundle.getByte(getString(R.string.fw_version)) < (byte)APP_FW_VERSION) {
            Log.d("Firmware", "need upgrade");
            mFWUpdate.setEnabled(true);
            mFWUpdate.setVisibility(View.VISIBLE);
        } else {
            Log.d("Firmware", "no need upgrade");
            mFWUpdate.setEnabled(false);
            mFWUpdate.setVisibility(View.INVISIBLE);
        }

        mVideoMode = (Button)findViewById(R.id.btn_video_mode);
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        if (mBundle.getString(getString(R.string.mode)).equals(getString(R.string.cloud))) {
            mEditProfile.setVisibility(View.GONE);
            mSettingsLayout.setVisibility(View.GONE);
        }

        Log.d("#######", "BBBBBBBBBBBBBBB");
        onBatteryLevelRequest(); // ask for battery level
    }



    public void onRemoteSWChanged(boolean isChecked) {
        if (isChecked) {
            new UpnpcAddPortTask().execute(mBundle);
        } else {
            int wan_port = mBundle.getInt(getString(R.string.wan_port));
            if (wan_port == 0) return;
            new UpnpcRemovePortTask().execute(mBundle);
        }
    }

    public void btnVideoModeClicked(View view) {
        Intent intent2 = new Intent(this, VideoPlayActivity.class);
        intent2.putExtra(getString(R.string.params), mBundle);
        startActivity(intent2);
    }

    public void btnPictureModeClicked(View view) {
        long time= System.currentTimeMillis();
        media_path.mkdirs();
        File file = new File(media_path, "WiCam-"+time+".jpeg");
        mBundle.putString(getString(R.string.jpeg_path), file.getAbsolutePath());
        mBundle.putInt(getString(R.string.resolution), CWicamService.CWicam.RESOLUTION_XGA);
        Intent intent2 = new Intent(this, CWicamService.class);
        intent2.putExtra("Messenger", mMessenger);
        intent2.putExtra("what", CWicamService.MSG_START_PICTURE);
        intent2.putExtra(getString(R.string.params), mBundle);
        startService(intent2);
        mPictureMode.setEnabled(false);

    }

    public boolean validateWiCamName () {
        String dvalue = mBundle.getString(getString(R.string.ap_ssid));
        String value = mApSSID.getText().toString();
        if (value.length() < 6) {
            mApSSID.setError(getString(R.string.field_length_must_6_or_more));
            mApSSID.requestFocus();
            mApSSID.setText(dvalue);
            return false;
        }
        boolean b = value.substring(0, new String("WiCam-").length()).toLowerCase().equals("wicam-");
        if (b == false) {
            mApSSID.setError(getString(R.string.ssid_must_begin_with_wicam));
            mApSSID.requestFocus();
            mApSSID.setText("WiCam-" + value);
            return false;
        }

        mApSSID.setText("WiCam-" + value.substring(new String("WiCam-").length()));
        mApSSID.setError(null);
        return true;
    }

    public boolean validateWiCamPin() {
        String dvalue = mBundle.getString(getString(R.string.ap_pin));
        if (mAPPIN.getText().length() < 8) {
            mAPPIN.setError(getString(R.string.error_pwd_length_less_8));
            mAPPIN.requestFocus();
            mAPPIN.setText(dvalue);
            return false;
        }
        mAPPIN.setError(null);
        return true;
    }

    public void saveWiCamProfile () {
        // validate fields
        if (validateWiCamName() == false) {
            return;
        }
        if (validateWiCamPin() == false) {
            return;
        }
        mBundle.putString(getString(R.string.old_ssid), mBundle.getString(getString(R.string.ap_ssid)));
        mBundle.putString(getString(R.string.ap_ssid), mApSSID.getText().toString());
        mBundle.putString(getString(R.string.ap_pin), mAPPIN.getText().toString());
        mBundle.putString(getString(R.string.sta_ssid), mStaSSID.getText().toString());
        mBundle.putString(getString(R.string.sta_pin), mStaPIN.getText().toString());
        Intent intent2 = new Intent(this, CWicamService.class);
        intent2.putExtra("Messenger", mMessenger);
        intent2.putExtra("what", CWicamService.MSG_UPDATE_CONF);
        intent2.putExtra(getString(R.string.params), mBundle);
        Log.d("saveWiCamProfile", "sendig intent");
        startService(intent2);
    }

    public void propagateNewConf(Bundle bd) {
        mBundle = bd;
        JSONObject json = new JSONObject();
        try {
            Log.d("New Conf", bd.getString(getString(R.string.ap_ssid)));
            Log.d("New Conf", bd.getString(getString(R.string.ap_pin)));
            Log.d("New Conf", bd.getString(getString(R.string.sta_ssid)));
            Log.d("New Conf", bd.getString(getString(R.string.sta_pin)));
            json.put(getString(R.string.ap_ssid), bd.getString(getString(R.string.ap_ssid)));
            json.put(getString(R.string.ap_pin), bd.getString(getString(R.string.ap_pin)));
            json.put(getString(R.string.sta_ssid), bd.getString(getString(R.string.sta_ssid)));
            json.put(getString(R.string.sta_pin), bd.getString(getString(R.string.sta_pin)));
            json.put(getString(R.string.sta_sec), bd.getByte(getString(R.string.sta_sec)));
            if (bd.containsKey(getString(R.string.lan_address))) {
                json.put(getString(R.string.lan_address), bd.getString(getString(R.string.lan_address)));
            }
            if (bd.containsKey(getString(R.string.wan_address))) {
                json.put(getString(R.string.wan_address), bd.getString(getString(R.string.wan_address)));
            }
            if (bd.containsKey(getString(R.string.wan_port))) {
                json.put(getString(R.string.wan_port), bd.getInt(getString(R.string.wan_port)));
            }
            SharedPreferences sp = getSharedPreferences("Wicam", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(json.getString(getString(R.string.ap_ssid)), json.toString());
            SharedPreferences.Editor editor2 = sp.edit();
            editor2.putString("why", json.toString());
            editor2.commit();
            boolean s = editor.commit();
            Log.d("propagateNewConf", "save success?" + s + " saved m_conf: " + json.toString());

            String conf_str = getSharedPreferences("Wicam", Context.MODE_PRIVATE).getString(bd.getString(getString(R.string.ap_ssid)), null);
            Log.d("propagateNewConf", "Verify saved: " + conf_str);
        } catch (JSONException ex) {
            Log.e("propagateNewConf", "JSONException");

        }
    }

    public void btnEditSettingClicked(View view) {

        //Log.d("Compare", mEditProfile.getText().toString().toUpperCase() + " vs " + getString(R.string.edit_wicam_settings).toUpperCase());
        if (mEditProfile.getText().toString().toUpperCase().equals(getString(R.string.edit_wicam_settings).toUpperCase())) {
            // enable app textedit fields
            mEditProfile.setText(R.string.save_changes);
            mApSSID.setEnabled(true);
            mAPPIN.setEnabled(true);
            mStaSSID.setEnabled(true);
            mStaPIN.setEnabled(true);
        } else { // save changes
            Log.d("btnEditSettingClicked", "save");
            saveWiCamProfile ();
        }

    }

    public void btnFWUpdateClicked(View view) {
        // update_firmware_btn
        if (!mFWUpdate.isEnabled()) return;
        if (mBundle.getByte(getString(R.string.fw_version)) >= (byte)APP_FW_VERSION) {
            return;
        }
        InputStream is = getResources().openRawResource(R.raw.wicam);
        byte[] buffer = new byte[1024*90];
        int sz;
        try {
            sz = is.read(buffer);
        }catch (IOException ioe) { return; }

        mFWUpdate.setEnabled(false);
        mFWUpdateProgress.setProgress(0);
        mFWUpdateProgress.setMax(100);
        mFWUpdateProgress.setVisibility(View.VISIBLE);
        Intent intent2 = new Intent(this, CWicamService.class);
        intent2.putExtra("Messenger", mMessenger);
        intent2.putExtra("what", CWicamService.MSG_UPDATE_FW);
        Bundle bd = new Bundle();
        bd.putString(getString(R.string.ap_ssid), mBundle.getString(getString(R.string.ap_ssid)));
        bd.putByteArray(getString(R.string.firmware), buffer);
        intent2.putExtra(getString(R.string.params), bd);
        startService(intent2);
        Log.d("ChooseModeActivity", "FW upgrade intent sent! fw size=" + sz);

    }


    public void onAddPortResult(Bundle bd) {
        if (bd == null) {
            mRemoteSW.setChecked(false);
        }
        propagateNewConf(mBundle);
    }

    public void onRemovePortResult(Boolean result) {
        // TODO: save to shared instance
        propagateNewConf(mBundle);
    }



    public class UpnpcAddPortTask extends AsyncTask<Bundle, Void, Bundle> {
        ProgressDialog progress = new ProgressDialog(ChooseModeActivity.this);
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progress.setTitle("Processing");
            progress.setCancelable(true);
            progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    onCancelled(null);
                }
            });
            progress.setMessage("Wait while configuring remote access for this Wicam...");
            progress.show();
        }
        @Override
        protected void onPostExecute(Bundle result) {
            progress.hide();
            onAddPortResult(result);
        }
        @Override
        protected void onCancelled(Bundle result) {
            progress.hide();
            cancel(true);
            onAddPortResult(result);
        }
        @Override
        protected Bundle doInBackground(Bundle... params) {
            Log.d("UpnpcAddPortTask", "Starting");
            if (params.length != 1) return null;
            Bundle bd = params[0];
            String lan_address = bd.getString(getString(R.string.ip));
            String old_lan_address = bd.getString(getString(R.string.lan_address));
            int lan_port = 80;
            int old_wan_port = bd.getInt(getString(R.string.wan_port));
            if (old_wan_port != 0) {
                CWicamService.upnpc_remove_port(old_wan_port);
                synchronized (mSync) {
                    bd.remove(getString(R.string.wan_port));
                    bd.remove(getString(R.string.wan_address));
                    bd.remove(getString(R.string.lan_address));
                }
            }
            int wan_port = old_wan_port != 0? old_wan_port:WAN_PORT_START;
            boolean succ = false;
            while (wan_port <= WAN_PORT_MAX && isCancelled() == false) {
                Log.d("UpnpcAddPortTask", "Trying port=" + wan_port);
                String wan_address = CWicamService.upnpc_add_port(lan_address, lan_port, wan_port, 0);
                if (wan_address == null) {
                    Log.d("UpnpcAddPortTask", "Port=" + wan_port + " failed.");
                    wan_port++;
                    continue;
                }
                synchronized (mSync) {
                    bd.putInt(getString(R.string.wan_port), wan_port);
                    bd.putString(getString(R.string.wan_address), wan_address);
                    bd.putString(getString(R.string.lan_address), lan_address);
                }
                succ = true;
                break;
            }
            if (wan_port > WAN_PORT_MAX) return null;
            if (isCancelled() == true) {
                if (succ == true) {
                    CWicamService.upnpc_remove_port(wan_port);
                    synchronized (mSync) {
                        bd.remove(getString(R.string.wan_port));
                        bd.remove(getString(R.string.wan_address));
                        bd.remove(getString(R.string.lan_address));
                    }
                }
                return null;
            }

            return bd;
        }
    }
    public class UpnpcRemovePortTask extends AsyncTask<Bundle, Void, Boolean> {
        ProgressDialog progress = new ProgressDialog(ChooseModeActivity.this);
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progress.setTitle("Processing");
            progress.setMessage("Wait while configuring remote access for this Wicam...");
            progress.show();
        }
        @Override
        protected void onPostExecute(Boolean result) {
            progress.hide();
            onRemovePortResult(result);
        }
        @Override
        protected void onCancelled(Boolean result) {
            progress.hide();
            onRemovePortResult(result);
        }
        @Override
        protected Boolean doInBackground(Bundle... params) {
            if (params.length != 1) return null;
            Bundle bd = params[0];
            int wan_port = bd.getInt(getString(R.string.wan_port));
            if (wan_port == 0) return true;
            boolean succ = CWicamService.upnpc_remove_port(wan_port);
            synchronized (mSync) {
                bd.remove(getString(R.string.wan_port));
                bd.remove(getString(R.string.wan_address));
                bd.remove(getString(R.string.lan_address));
            }
            return succ;
        }
    }

}
