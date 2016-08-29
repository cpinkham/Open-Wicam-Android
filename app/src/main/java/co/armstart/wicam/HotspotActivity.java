package co.armstart.wicam;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;

public class HotspotActivity extends AppCompatActivity {

    public static final int INTENT_CODE_NID = 0;

    private View mRootView;

    private int m_orientation;
    private boolean m_is_restore = false;
    private File m_pic_dir = null;
    private File m_mov_dir = null;
    private boolean m_is_wifi = false;
    private boolean m_is_mobi = false;

    private boolean m_wsr_registered = false;
    private WifiManager m_wm;
    private ListView m_lv;
    private List<ScanResult> m_lsr = null;

    private TextView    m_statusView;

    private boolean m_scanCalled = false;
    private BroadcastReceiver m_scan_rcver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            WifiManager wm = (WifiManager) HotspotActivity.this.getSystemService(Context.WIFI_SERVICE);
            List<ScanResult> lsr = wm.getScanResults();
            Log.d("HotspotActivity","Scan results:" + lsr.size());
            int wicam_size = 0;
            for (int i = 0; i < lsr.size(); i++) {
                if (lsr.get(i).SSID.toLowerCase().contains("wicam") && lsr.get(i).capabilities.toLowerCase().contains("wpa2")) {
                    wicam_size++;
                }
            }
            Log.d("HotspotActivity", "Found " + wicam_size + " devices");
            if (wicam_size == 0) {
                m_statusView.setText("0 Found. Restarting in 5 sec.");
                new CountDownTimer(5000, 1000) {

                    public void onTick(long millisUntilFinished) {
                        m_statusView.setText("0 Found. Restarting in "+millisUntilFinished/1000+" sec.");
                    }
                    public void onFinish() {
                        m_statusView.setText("Searching WiCam Hotspot...");
                        m_scanCalled = false;
                        if (m_wsr_registered == true)
                            HotspotActivity.this.preStartWifiScan();
                    }
                }.start();
                return;
            }
            m_lsr = new ArrayList<ScanResult>(wicam_size);
            String[] rs = new String[wicam_size];
            int j = 0;
            for (int i = 0; i < lsr.size(); i++) {
                if (lsr.get(i).SSID.toLowerCase().contains("wicam") && lsr.get(i).capabilities.toLowerCase().contains("wpa2")) {
                    rs[j++] = "Wicam: SSID=" + lsr.get(i).SSID + " caps=" + lsr.get(i).capabilities;
                    m_lsr.add(lsr.get(i));
                }
            }

            m_lv.setAdapter(new ArrayAdapter<String>(context, R.layout.lv_tv_item, rs));
            //ArrayAdapter<ScanResult> adapter = new ArrayAdapter<ScanResult>(HotspotActivity.this, , lsr.toArray());
        }
    };

    private AdapterView.OnItemClickListener m_rs_listerner = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Log.d("HotspotActivity", "Wicam " + position + " selected:" + m_lsr.get(position).SSID);
            Intent intent = new Intent(HotspotActivity.this, HotspotSigninActivity.class);
            Bundle bd = new Bundle();
            bd.putString(getString(R.string.ap_ssid), m_lsr.get(position).SSID);
            bd.putString(getString(R.string.mode), getString(R.string.outdoor));
            bd.putString(getString(R.string.ip), "192.168.240.1");
            intent.putExtra(getString(R.string.params), bd);
            HotspotActivity.this.startActivity(intent);
            Log.d("HotspotActivity", "going to HotspotSigninActivity");
        }
    };



    public static final int     PERMISSION_REQ_ALL    = 1;
    public static final int     PERMISSION_REQ_SCAN   = 2;
    public static final int     NUM_PERMISSIONS       = 7; // WRITE_EXTERNAL_STORAGE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, INTERNET, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE， ACCESS_COARSE_LOCATION

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hotspot);
        // Get current orientation
        m_orientation = getResources().getConfiguration().orientation;
        // Get Activity creation type: create or restore
        m_is_restore = savedInstanceState == null? false:true;
        m_lv = (ListView) findViewById(R.id.lv_scan_result);

        m_statusView = (TextView) findViewById(R.id.hotspotactivity_status);
        //ProgressBar progressBar = new ProgressBar(this);
        //progressBar.setLayoutParams(new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
        //        ActionBar.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        //progressBar.setIndeterminate(true);
        ProgressBar pb = (ProgressBar) findViewById(R.id.lv_scan_progress);
        m_lv.setEmptyView(pb);

        mRootView = findViewById(android.R.id.content);

        m_lv.setOnItemClickListener(m_rs_listerner);


        //getExternalFilesDir(null);

    }

    public void getConnectivityType () {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
            m_is_wifi = false;
            m_is_mobi = false;
            return;
        }
        m_is_wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
        m_is_mobi = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected();
    }

    public void preStartWifiScan () {

        if (m_scanCalled == true) return;
        m_scanCalled = true;
        m_lv.setAdapter(null);
        ensurePermission(PERMISSION_REQ_SCAN);
    }
    public void postStartWifiScanFailed() {
        Log.d("HotspotActivity", "postStartWifiScanFailed");
    }
    public void postStartWifiScan () {
        Log.d("HotspotActivity", "Wifi scann permission granted, start scanning");

        registerReceiver(m_scan_rcver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        m_wsr_registered = true;
        WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        wm.startScan();
    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return (ni != null && ni.isConnected());
    }


    @Override
    public void onResume() {
        super.onResume();
        Log.d("HotspotActivity", "HotspotActivity onResume");
        preStartWifiScan();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("HotspotActivity", "onPause called");
        if (m_wsr_registered == true) {
            Log.d("HotspotActivity", "onPause unregistering Scanner");
            unregisterReceiver(m_scan_rcver);
            m_wsr_registered = false;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        boolean failed = false;
        if (grantResults.length != NUM_PERMISSIONS) {
            Log.e("HotspotActivity", "Not equal");
            failed = true;
        }
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                Log.e("HotspotActivity", "Permission " + permissions[i] + ": " + i + " failed.");
                failed = true;
                break;
            }
        }
        onPermissionJudged(requestCode, failed);
    }

    public void onPermissionJudged(int requestCode, boolean failed) {
        switch(requestCode) {
            case PERMISSION_REQ_ALL:
                if (failed) postRequestAllFailed();
                else postRequestAll();
                break;
            case PERMISSION_REQ_SCAN:
                if (failed) postStartWifiScanFailed();
                else postStartWifiScan();
        }
    }
    public void postRequestAllFailed() {
        Log.d("HotspotActivity", "postRequestAllFailed");
    }
    public void postRequestAll() {
        Log.d("HotspotActivity", "postRequestAll");
    }

    public void ensurePermission(int code) {
        Log.d("HotspotActivity", "EnsurePermission:" + code);
        if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CHANGE_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // TODO: show dialog
                Log.e("HotspotActivity", "WRITE EXT STORAGE not permitted");
                /*
                Snackbar.make(mRootView, getString(R.string.write_external_storage_permission_required), Snackbar.LENGTH_INDEFINITE)
                        .setAction(android.R.string.ok, new View.OnClickListener() {
                            @Override
                            @TargetApi(Build.VERSION_CODES.M)
                            public void onClick(View v) {

                            }
                        });
                        */
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_WIFI_STATE)) {
                Log.e("HotspotActivity", "ACCESS WIFI STATE not permitted");
                // TODO: show dialog
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CHANGE_WIFI_STATE)) {
                Log.e("HotspotActivity", "CHANGE WIFI STATE not permitted");
                // TODO: show dialog
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.INTERNET)) {
                Log.e("HotspotActivity", "INTERNET not permitted");
                // TODO: show dialog
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
                // TODO: show dialog
                Log.e("HotspotActivity", "ACCESS NETWORK STATE not permitted");
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CHANGE_NETWORK_STATE)) {
                // TODO: show dialog
                Log.e("HotspotActivity", "CHNAGE NETWORK STATE not permitted");
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Log.e("HotspotActivity", "ACCESS COARSE LOCATION not permitted");
                // TODO: show dialog
            }

            Log.d("HotspotActivity", "requesting Permission:" + code);
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.ACCESS_WIFI_STATE,
                            android.Manifest.permission.CHANGE_WIFI_STATE,
                            android.Manifest.permission.INTERNET,
                            android.Manifest.permission.ACCESS_NETWORK_STATE,
                            android.Manifest.permission.CHANGE_NETWORK_STATE,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    code);
            return;
        }
        // if we got all the permissions already
        onPermissionJudged(code, false);
    }


    public boolean ensureExternalStorageExist() {

        String ext_state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(ext_state)) { // if no external storage is present, go back.
            return false;
        }
        // get Public pictures directory for storing picture
        m_pic_dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        // get Public movie directory for saving mp4 files.
        File m_mov_dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        return true;
    }


    public void onAPSelected(View view) {

    }

    @Override
    public  void onSaveInstanceState(Bundle outState) {
        //outState.putStr
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }
}
