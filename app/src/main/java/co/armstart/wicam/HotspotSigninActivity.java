package co.armstart.wicam;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 */
public class HotspotSigninActivity extends AppCompatActivity {

    // UI references.
    private TextView mSsidView;
    private EditText mPasswordView;
    private Button mSigninBtn;
    private View mProgressView;
    private View mLoginFormView;

    private JSONObject m_conf = null;
    protected Bundle mBundle;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {

        }
        public void onServiceDisconnected(ComponentName className) {

        }
    };

    private Handler mIncomingHandler = new Handler () {
        @Override
        public void handleMessage(Message ms) {
            if (ms.what == CWicamService.MSG_GET_DEV_INFO) {
                handle_MSG_GET_DEV_INFO (ms);
            } else if (ms.what == CWicamService.MSG_BIND_WIFI) {
                handle_MSG_BIND_WIFI(ms);
            } else if (ms.what == CWicamService.MSG_UNBIND_WIFI) {
                handle_MSG_UNBIND_WIFI(ms);
            }

        }

        private void handle_MSG_GET_DEV_INFO (Message ms) {
            Log.d("HotspotSigninActivity", "handle_MSG_GET_DEV_INFO what:" + ms.what + " arg1:" + ms.arg1);
            if (ms.arg1 != CWicamService.MSG_OK) {
                // failed
                mSsidView.setText("Failed signing into Wicam");
                mPasswordView.setEnabled(true);
                mSigninBtn.setEnabled(true);
                return;
            }

            Bundle bd = ms.getData();
            bd.putString(getString(R.string.ip), mBundle.getString(getString(R.string.ip)));
            bd.putString(getString(R.string.mode), mBundle.getString(getString(R.string.mode)));
            // save bundle to SharedPreference
            try {
                m_conf.put(getString(R.string.ap_ssid), bd.getString(getString(R.string.ap_ssid)));
                m_conf.put(getString(R.string.ap_pin), bd.getString(getString(R.string.ap_pin)));
                m_conf.put(getString(R.string.sta_ssid), bd.getString(getString(R.string.sta_ssid)));
                m_conf.put(getString(R.string.sta_pin), bd.getString(getString(R.string.sta_pin)));
                m_conf.put(getString(R.string.sta_sec), bd.getByte(getString(R.string.sta_sec)));
                saveWicamConfig();

                if (m_conf.has(getString(R.string.lan_address))) {
                    bd.putString(getString(R.string.lan_address),
                            m_conf.getString(getString(R.string.lan_address)));
                }
                if (m_conf.has(getString(R.string.wan_address))) {
                    bd.putString(getString(R.string.wan_address),
                            m_conf.getString(getString(R.string.wan_address)));
                }
                if (m_conf.has(getString(R.string.wan_port))) {
                    bd.putInt(getString(R.string.wan_port),
                            m_conf.getInt(getString(R.string.wan_port)));
                }

                Log.d("HotspotSigninActivity", "saved m_conf: " + m_conf.toString());
            }catch (JSONException ex) {
                Log.e("HotspotSigninActivity", "JSONException in handle_MSG_GET_DEV_INFO");
                // UNLIKELY
            }

            Log.d("HotspotSigninActivity", "sign in successful");
            mSsidView.setText("Sign in success");
            // Start ChooseModeActivity activity
            Intent intent2 = new Intent(HotspotSigninActivity.this, ChooseModeActivity.class);
            intent2.putExtra(getString(R.string.params), bd);
            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            HotspotSigninActivity.this.startActivity(intent2);
            Log.d("HotspotSigninActivity", "start ChooseModeActivity");
            mPasswordView.setEnabled(false);
            mSigninBtn.setEnabled(true);
        }
        private void handle_MSG_BIND_WIFI (Message ms) {
            if (ms.arg1 == CWicamService.MSG_FAILED) {
                Log.e("HotspotSigninActivity", "BINDING WIFI failed");
                mSsidView.setText("BINDING WIFI failed");
                return;
            }
            // 发送 CWicamService.MSG_GET_DEV_INFO 请求
            Intent intent2 = new Intent(HotspotSigninActivity.this, CWicamService.class);
            intent2.putExtra(getString(R.string.Messenger), mMessenger);
            intent2.putExtra(getString(R.string.what), CWicamService.MSG_GET_DEV_INFO);
            Bundle bd = new Bundle();
            try {
                bd.putString(getString(R.string.ap_ssid), m_conf.getString(getString(R.string.ap_ssid)));
                bd.putString(getString(R.string.ip), mBundle.getString(getString(R.string.ip)));
                bd.putString(getString(R.string.ap_pin), m_conf.getString(getString(R.string.ap_pin)));
                intent2.putExtra(getString(R.string.params), bd);
            } catch (JSONException e) {
                // REPORT-BUG：JSON数据不正确，放弃。
                Log.e("HotspotSigninActivity", "JSONException in handle_MSG_BIND_WIFI");
                e.printStackTrace();
                finish();
                return;
            }
            HotspotSigninActivity.this.startService(intent2);
        }
        private void handle_MSG_UNBIND_WIFI (Message ms) {
            if (ms.arg1 == CWicamService.MSG_FAILED) {
                Log.e("HotspotSigninActivity", "UNBINDING WIFI failed");
            }
            mPasswordView.setEnabled(false);
            mSigninBtn.setEnabled(true);
            Log.d("Wicam", "Logging in in cloud mode");
            Intent intent2 = new Intent(HotspotSigninActivity.this, CWicamService.class);
            intent2.putExtra(getString(R.string.Messenger), mMessenger);
            intent2.putExtra(getString(R.string.what), CWicamService.MSG_GET_DEV_INFO);
            Bundle bd = new Bundle();
            try {
                bd.putString(getString(R.string.ap_ssid), m_conf.getString(getString(R.string.ap_ssid)));
                bd.putString(getString(R.string.ip), mBundle.getString(getString(R.string.ip)));
                bd.putString(getString(R.string.ap_pin), m_conf.getString(getString(R.string.ap_pin)));
                intent2.putExtra(getString(R.string.params), bd);
            } catch (JSONException e) {
                e.printStackTrace();
                finish();
                return;
            }
            HotspotSigninActivity.this.startService(intent2);
        }
    }; // class Handler

    final Messenger mMessenger = new Messenger(mIncomingHandler);

    private NetworkInfo.DetailedState m_state = NetworkInfo.DetailedState.IDLE;

    private boolean m_registered = false;
    private BroadcastReceiver m_signin_rcver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (m_conf == null) return;
            final String action = intent.getAction();
            Log.d("HotspotSigninActivity", "##################");
            Log.d("HotspotSigninActivity", "BroadcastReceiver: " + action);



            if (!action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) return;

            NetworkInfo ni = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

            if (ni == null ) return;


            NetworkInfo.DetailedState state = ni.getDetailedState();
            Log.d("HotspotSigninActivity", "NetworkInfo: " + ni);
            try {
                Log.d("HotspotSigninActivity", "Saved SSID: " + m_conf.getString(getString(R.string.ap_ssid)));
                Log.d("HotspotSigninActivity", "Extra Info: " + ni.getExtraInfo());
                if (!ni.getExtraInfo().equals("\"" + m_conf.getString(getString(R.string.ap_ssid)) + "\"")) {
                    Log.e("HotspotSigninActivity", "Not for Wicam");
                    return;
                }
            }catch(JSONException ex) {
                return;
            }

            switch (state) {
                case AUTHENTICATING:
                    Log.d("HotspotSigninActivity", "BroadcastReceiver AUTHENTICATING");
                    mSsidView.setText("AUTHENTICATING");
                    m_state = state;
                    break;
                case OBTAINING_IPADDR:
                    Log.d("HotspotSigninActivity", "BroadcastReceiver OBTAINING_IPADDR");
                    mSsidView.setText("Obtaining IP Address");
                    break;
                case CONNECTED:
                    Log.d("HotspotSigninActivity", "BroadcastReceiver CONNECTED");
                    if (m_state == NetworkInfo.DetailedState.CONNECTED) break;
                    m_state = state;
                    mSsidView.setText("CONNECTED");

                    // save config
                    saveWicamConfig();

                    // MSG_BIND_WIFI
                    Intent intent2 = new Intent(HotspotSigninActivity.this, CWicamService.class);
                    intent2.putExtra(getString(R.string.Messenger), mMessenger);
                    intent2.putExtra(getString(R.string.what), CWicamService.MSG_BIND_WIFI);
                    HotspotSigninActivity.this.startService(intent2);
                    mSsidView.setText("Wicam Authenticating");
                    Log.d("HotspotSigninActivity", "startService MSG_BIND_WIFI");
                    // 注销 Signin's BroadcastReceiver
                    unregisterReceiver(m_signin_rcver);
                    m_registered = false;
                    break;
                case DISCONNECTED:
                    Log.d("HotspotSigninActivity", "BroadcastReceiver DISCONNECTED");
                    if (m_state == NetworkInfo.DetailedState.AUTHENTICATING) {
                        mSsidView.setText("Wrong Password?");
                        mPasswordView.setEnabled(true);
                        mSigninBtn.setEnabled(true);
                    }
                    m_state = state;
                    break;
                case FAILED:
                    Log.d("HotspotSigninActivity", "BroadcastReceiver FAILED");
                    mSsidView.setText("FAILED");
                    m_state = state;
                    mPasswordView.setEnabled(true);
                    mSigninBtn.setEnabled(true);
                    break;
                case BLOCKED:
                    Log.d("HotspotSigninActivity", "BroadcastReceiver BLOCKED");
                    mSsidView.setText("User BLOCKED Wicam");
                    m_state = state;
                    mPasswordView.setEnabled(true);
                    mSigninBtn.setEnabled(true);
                    break;
                case VERIFYING_POOR_LINK:
                    Log.d("HotspotSigninActivity", "BroadcastReceiver VERIFYING_POOR_LINK");
                    mSsidView.setText("VERIFYING POOR LINK");
                    m_state = state;
                    mPasswordView.setEnabled(true);
                    mSigninBtn.setEnabled(true);
                    break;
                default:
                    Log.d("HotspotSigninActivity", "State: " + state);

            }
            Log.d("HotspotSigninActivity", "##################END");

        }
    };

    private TextView.OnEditorActionListener m_pwd_listener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
            try {
                Log.d("HotspotSigninActivity", "keyEvent:" + keyEvent);
                Log.d("HotspotSigninActivity", "is the password field: " + (textView.getId() == R.id.hotspot_password));
                if (id == EditorInfo.IME_ACTION_DONE) {
                    String pwd = mPasswordView.getText().toString();
                    if (pwd.length() < 8) {
                        mPasswordView.setError(getString(R.string.error_pwd_length_less_8));
                        mPasswordView.requestFocus();
                        return false;
                    }
                    m_conf.put(getString(R.string.ap_pin), pwd);
                    mSsidView.setText("Logging in...");
                    if (attemptLogin() == true) {
                        mPasswordView.setEnabled(false);
                        mSigninBtn.setEnabled(false);
                    }
                    return false;
                }
                Log.d("HotspotSigninActivity", "OnEditorActionListener failed: " + id);
                return false;
            } catch(JSONException ex) {
                Log.d("HotspotSigninActivity", "JSONObject.put Password field error");
                return false;
            }// catch
        } // on EditorAction
    };

    private OnClickListener m_btn_listener = new OnClickListener() {
        @Override
        public void onClick(View view) {

            try {
                String pwd = mPasswordView.getText().toString();
                if (pwd.length() < 8) {
                    mPasswordView.setError(getString(R.string.error_pwd_length_less_8));
                    mPasswordView.requestFocus();
                    return;
                }
                m_conf.put(getString(R.string.ap_pin), pwd);
                mSsidView.setText("Logging in...");
                boolean ret = attemptLogin();
                if (ret == true) {
                    mPasswordView.setEnabled(false);
                    mSigninBtn.setEnabled(false);
                }
            } catch(JSONException ex) {
                Log.d("Wicam", "JSONObject.put Password field error");
            }// catch

        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!mSsidView.getText().toString().equals("Sign in success")) return;
        Log.d("HotspotSigninActivity", "Closing " + mBundle.getString(getString(R.string.ap_ssid)));
        Intent intent2 = new Intent(this, CWicamService.class);
        intent2.putExtra(getString(R.string.Messenger), mMessenger);

        intent2.putExtra(getString(R.string.params), mBundle);
        intent2.putExtra(getString(R.string.what), CWicamService.MSG_CLOSE);
        startService(intent2);
        //MSG_CLOSE
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hotspot_signin);


        Intent intent = getIntent();
        mBundle = intent.getBundleExtra(getString(R.string.params));
        if (mBundle == null) {
            finish();
            return;
        }
        final String ssid_name = mBundle.getString(getString(R.string.ap_ssid));
        final String ip = mBundle.getString(getString(R.string.ip));
        final String mode = mBundle.getString(getString(R.string.mode));
        if (ssid_name == null || ip == null || mode == null) {
            finish();
            return;
        }

        Log.d("HotspotSigninActivity", "SSID:" + ssid_name);
        SharedPreferences sp = getSharedPreferences("Wicam", Context.MODE_PRIVATE);
        String conf_str = sp.getString(ssid_name, null);
        String why = sp.getString("why", null);
        Log.d("HotspotSigninActivity", "why:" + why);
        Log.d("HotspotSigninActivity", "conf_str:" + conf_str);

        try {
            if (conf_str == null) {
                m_conf = new JSONObject();
                m_conf.put(getString(R.string.ap_ssid), ssid_name);
                Log.d("HotspotSigninActivity", "m_conf:" + m_conf.toString());
            } else {
                m_conf = new JSONObject(conf_str);

                if (m_conf.has(getString(R.string.ap_ssid)) == false) {
                    removeWicamConfig(ssid_name);
                    m_conf = new JSONObject();
                    m_conf.put(getString(R.string.ap_ssid), ssid_name);
                }
            }
        }catch (JSONException ex) {
            Log.e("Wicam", "JSONException");
            removeWicamConfig(ssid_name);
            finish();
            return;
        }
        // Set up the login form.
        mSsidView = (TextView) findViewById(R.id.ssid);
        mSsidView.setText("Sign into:" + ssid_name);


        mPasswordView = (EditText) findViewById(R.id.hotspot_password);
        mPasswordView.setText("wicam.cc");
        try {
            String pwd = m_conf.getString(getString(R.string.ap_pin));
            if (pwd != null) {
                Log.d("Wicam", "Saved Password: " + pwd);
                mPasswordView.setText(pwd);
            }
        }catch (JSONException ex) {

        }
        mPasswordView.setOnEditorActionListener(m_pwd_listener);// setOnEditorActionListener

        mSigninBtn = (Button) findViewById(R.id.ssid_sign_in_button);
        mSigninBtn.setOnClickListener(m_btn_listener);


        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);



    }

    @Override
    public void onStop() {
        super.onStop();
        if (m_registered == true) {
            unregisterReceiver(m_signin_rcver);
            m_registered = false;
        }

    }

    // http://stackoverflow.com/questions/8818290/how-to-connect-to-a-specific-wi-fi-network-in-android-programmatically
    public boolean attemptLogin () {
        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wi = wm.getConnectionInfo();
        Log.d("Wicam", "Current connection is: " + wi.getSSID());
        // sync
        String json_str = getSharedPreferences("Wicam", Context.MODE_PRIVATE).getString(mBundle.getString(getString(R.string.mode)), null);

        try {
            if (json_str != null) {
                JSONObject json = new JSONObject(json_str);
                m_conf = json;
            }
            if (mBundle.getString(getString(R.string.mode)).equals(getString(R.string.cloud))) {
                // unbind wifi
                mPasswordView.setEnabled(false);
                mSigninBtn.setEnabled(true);
                Intent intent2 = new Intent(HotspotSigninActivity.this, CWicamService.class);
                intent2.putExtra(getString(R.string.Messenger), mMessenger);
                intent2.putExtra(getString(R.string.what), CWicamService.MSG_UNBIND_WIFI);
                startService(intent2);
                mSsidView.setText("Wicam Authenticating");
                return true;
            } else if ( mBundle.getString(getString(R.string.mode)).equals(getString(R.string.home))
                    || wi.getSSID().equals("\"" + m_conf.getString(getString(R.string.ap_ssid)) + "\"")) {
                mPasswordView.setEnabled(false);
                mSigninBtn.setEnabled(true);
                // TODO: Sign into Wicam using password
                Log.d("Wicam", "Already Logged in");

                // 发送 CWicamService.MSG_BIND_WIFI 请求
                // MSG_BIND_WIFI
                Intent intent2 = new Intent(HotspotSigninActivity.this, CWicamService.class);
                intent2.putExtra(getString(R.string.Messenger), mMessenger);
                intent2.putExtra(getString(R.string.what), CWicamService.MSG_BIND_WIFI);
                startService(intent2);
                mSsidView.setText("Wicam Authenticating");
                return true;
            }
        }catch (JSONException ex) {
            finish();
            return false;
        } catch (NullPointerException e) {
            e.printStackTrace();
            finish();
            return false;
        }
        Log.d("HotSpotSigninActivity", "add network");
        try {
            // add to the network and initiate connection
            Log.d("HotSpotSigninActivity", "add network with conf:" + m_conf.toString());
            WifiConfiguration wc = new WifiConfiguration();
            wc.SSID = "\"" + m_conf.getString(getString(R.string.ap_ssid)) + "\"";
            wc.preSharedKey = "\"" + m_conf.getString(getString(R.string.ap_pin)) + "\"";
            wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            int nid = wm.addNetwork(wc);
            if (nid == -1) {
                mSsidView.setText("Login failed 1");
                Toast.makeText(this, "Failed to add Wicam's Network to Android", Toast.LENGTH_SHORT);
                Log.e("HotspotSigninActivity", "Invalid network");
                return false;
            }
            // setup broadcast listener to listen for connection change event
            // to catch the connected state.
            if (m_registered == false) {
                registerReceiver(m_signin_rcver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
                m_registered = true;
            }
            // enable network.
            wm.enableNetwork(nid, true);
        }catch (JSONException ex) {
            mSsidView.setText("Login failed2");
            Toast.makeText(this, "Invalid network", Toast.LENGTH_SHORT);
            Log.e("HotspotSigninActivity", "JSONException:" + ex.getMessage());
            return false;
        }
        return true;
    }

    public boolean saveWicamConfig () {
        try {
            SharedPreferences sp = getSharedPreferences("Wicam", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(m_conf.getString(getString(R.string.ap_ssid)), m_conf.toString());
            editor.commit();
            return true;
        }catch (JSONException ex) {
            return false;
        }
    }

    public boolean removeWicamConfig(String ssid) {
        SharedPreferences sp = getSharedPreferences("Wicam", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.remove(ssid);
        return editor.commit();
    }


}

