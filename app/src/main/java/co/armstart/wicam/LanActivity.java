package co.armstart.wicam;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Set;

public class LanActivity extends AppCompatActivity {

    public final static int SIZEOF_DEV_INFO_T = 69;
    public final static int SSID_LEN_MAX = 32;
    public final static int IP_LEN_MAX = 16;
    public final static byte DEV_INFO_SIG_START = (byte)0xCA;
    public final static byte DEV_INFO_SIG_END = (byte)0x12;
    public WicamDiscoveryTask wdt;

    public static final int     PERMISSION_REQ_DISCOVERY   = 1;
    public static final int     NUM_PERMISSIONS       = 7; // WRITE_EXTERNAL_STORAGE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, INTERNET, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE， ACCESS_COARSE_LOCATION

    protected ListView m_lv;
    protected TextView m_statusView;
    protected String[] mListItems;
    protected Bundle mBundle;

    private AdapterView.OnItemClickListener m_rs_listerner = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mListItems == null || mListItems.length <= position) return;
            String ssid = mListItems[position];
            String ip = mBundle.getString(ssid);
            onWiCamSelected(ssid, ip);
        }
    };

    public void onWiCamSelected(String ssid, String ip) {
        Log.d("LanActivity", "Wicam selected " + ssid + " " + ip);
        Intent intent = new Intent(this, HotspotSigninActivity.class);
        Bundle bd = new Bundle();
        bd.putString(getString(R.string.ap_ssid), ssid);
        bd.putString(getString(R.string.mode), getString(R.string.home));
        bd.putString(getString(R.string.ip), ip);
        intent.putExtra(getString(R.string.params), bd);
        startActivity(intent);
        Log.d("LanActivity", "going to HotspotSigninActivity.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        boolean failed = false;
        if (grantResults.length != NUM_PERMISSIONS) {
            Log.e("LanActivity", "Not equal");
            failed = true;
        }
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                Log.e("LanActivity", "Permission " + permissions[i] + ": " + i + " failed.");
                failed = true;
                break;
            }
        }
        onPermissionJudged(requestCode, failed);
    }

    public void postStartWicamDiscoveryFailed() {
        Toast.makeText(this, "Permissions not granted. Quiting.", Toast.LENGTH_LONG).show();
        finish();
    }

    public void postStartWifiDiscovery() {
        Log.d("LanActivity", "postStartWifiDiscovery");
        if (wdt != null && wdt.isCancelled() == false) {
            wdt.cancel(true);
            wdt = null;
        }
        wdt = new WicamDiscoveryTask();
        wdt.execute();
    }

    public void onPermissionJudged(int requestCode, boolean failed) {
        switch(requestCode) {
            case PERMISSION_REQ_DISCOVERY:
                if (failed) postStartWicamDiscoveryFailed();
                else postStartWifiDiscovery();
        }
    }

    public void ensurePermission(int code) {
        Log.d("LanActivity", "EnsurePermission:" + code);
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
                Log.e("LanActivity", "WRITE EXT STORAGE not permitted");
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
                Log.e("LanActivity", "ACCESS WIFI STATE not permitted");
                // TODO: show dialog
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CHANGE_WIFI_STATE)) {
                Log.e("LanActivity", "CHANGE WIFI STATE not permitted");
                // TODO: show dialog
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.INTERNET)) {
                Log.e("LanActivity", "INTERNET not permitted");
                // TODO: show dialog
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
                // TODO: show dialog
                Log.e("LanActivity", "ACCESS NETWORK STATE not permitted");
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CHANGE_NETWORK_STATE)) {
                // TODO: show dialog
                Log.e("LanActivity", "CHNAGE NETWORK STATE not permitted");
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Log.e("LanActivity", "ACCESS COARSE LOCATION not permitted");
                // TODO: show dialog
            }

            Log.d("LanActivity", "requesting Permission:" + code);
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
    public void preStartWiCamScan () {
        ensurePermission(PERMISSION_REQ_DISCOVERY);

    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lan);

        m_statusView = (TextView) findViewById(R.id.lanactivity_status);
        m_lv = (ListView)findViewById(R.id.lv_discovery_result);

        ProgressBar pb = (ProgressBar) findViewById(R.id.lv_discovery_progress);
        m_lv.setEmptyView(pb);
        m_lv.setOnItemClickListener(m_rs_listerner);
    }

    @Override
    protected void onResume() {
        super.onResume();

        preStartWiCamScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wdt != null && wdt.isCancelled() == false) wdt.cancel(true);
        wdt = null;
    }

    protected void onDiscovered(Bundle bd) {
        if (bd == null) {
            // restart the task.
            wdt = null;
            new CountDownTimer(5000, 1000) {

                public void onTick(long millisUntilFinished) {
                    m_statusView.setText("0 Found. Restarting in "+millisUntilFinished/1000+" sec.");
                }
                public void onFinish() {
                    m_statusView.setText("Searching Home WiCams... ");
                    preStartWiCamScan();
                }
            }.start();
            return;
        }
        wdt = null;
        // propagate.
        Set<String> keys = bd.keySet();
        mListItems = new String[keys.size()];
        int i = 0;
        for (String key:keys) {
            String addr = bd.getString(key);
            mListItems[i++] = key;
        }
        mBundle = bd;
        m_lv.setAdapter(new ArrayAdapter<String>(this, R.layout.lv_tv_item, mListItems));
    }

    public class WicamDiscoveryTask extends AsyncTask<Void, Integer, Bundle> {
        @Override
        protected void onPostExecute(Bundle result) {
            onDiscovered(result);
        }
        @Override
        protected Bundle doInBackground(Void... params) {
            Log.d("WicamDiscoveryTask", "doInBackground");
            Bundle ret = new Bundle();
            // get current IP and mask
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            DhcpInfo dhcp = wm.getDhcpInfo();

            Integer listenPort = 4277;
            Integer sendPort = 4211;
            byte dev_info[] = new byte[SIZEOF_DEV_INFO_T];
            try {
                // construct broadcast listening socket
                DatagramSocket listenSocket = new DatagramSocket(listenPort, InetAddress.getByName("0.0.0.0"));
                listenSocket.setBroadcast(true);
                listenSocket.setSoTimeout(10);
                if (listenSocket.getBroadcast() == false) {
                    Log.e("WicamDiscoveryTask", "doInBackground, not broadcast");
                    return null;
                }
                // Broadcast WiCam
                byte[] wicam = "WiCam".getBytes();
                Log.d("WicamDiscoveryTask", "WiCam Byte array length= " + wicam.length);
                DatagramSocket sendSocket = new DatagramSocket();
                sendSocket.setBroadcast(true);
                // send "WiCam"
                DatagramPacket sp = new DatagramPacket(wicam,
                        wicam.length,
                        InetAddress.getByName("255.255.255.255"),
                        sendPort);
                sendSocket.send(sp);

                // Listen incoming broadcast
                int count = 500;
                while (count-- != 0 && isCancelled() == false) {
                    // listen for incoming data
                    DatagramPacket dp = new DatagramPacket(dev_info, dev_info.length);
                    try {
                        listenSocket.receive(dp);
                    } catch (SocketTimeoutException e) {
                        //Log.d("WicamDiscoveryTask", "Socket Timeout Exception");
                        continue;
                    }
                    Log.d("WicamDiscoveryTask", "Packet received from " + dp.getAddress().getHostAddress());
                    Log.d("WicamDiscoveryTask", "first_byte=" + String.format("%02X", dev_info[0]));
                    Log.d("WicamDiscoveryTask", "last_byte=" + String.format("%02X", dev_info[SIZEOF_DEV_INFO_T - 1]));
                    if (dev_info[0] != (byte)DEV_INFO_SIG_START || dev_info[SIZEOF_DEV_INFO_T - 1] != (byte)DEV_INFO_SIG_END) {
                        Log.e("WicamDiscoveryTask", "Invalid Discovery Packet.");
                        continue;
                    }
                    int i = 0;
                    for (i = 0; i < (IP_LEN_MAX + 1) && dev_info[1 + i] != 0; i++) { }
                    if (i == (IP_LEN_MAX + 1)) {
                        Log.e("WicamDiscoveryTask", "Packet address is invalid");
                        continue;
                    }
                    String rcvdAddress = new String(dev_info, 1, i, "US-ASCII");
                    Log.d("WicamDiscoveryTask", "rcvdAddress=" + rcvdAddress);
                    if (rcvdAddress.equals(dp.getAddress().getHostAddress()) == false) {
                        Log.e("WicamDiscoveryTask", "Packet addresses do not match");
                        continue;
                    }
                    for (i = 0; i < (SSID_LEN_MAX + 1) && dev_info[17 + i] != 0; i++) { }
                    if (i <= 6 || i == (SSID_LEN_MAX + 1)) {
                        Log.e("WicamDiscoveryTask", "Packet's SSID is invalid.");
                        continue;
                    }
                    String ssid = new String(dev_info, 17, i, "US-ASCII");
                    Log.d("WicamDiscoveryTask", "Discovered: ssid=" + ssid + " ip=" + rcvdAddress);
                    ret.putString(ssid, rcvdAddress);
                }
                listenSocket.close();
                sendSocket.close();
            } catch (SocketException e) {
                return null;
            } catch (UnknownHostException e) {
                return null;
            } catch (IOException e) {
                return null;
            }
            Log.d("LanActivity", "Discovery task completed.");
            if (isCancelled() == true) return null;

            return ret;
        }
    }
}
