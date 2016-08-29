package co.armstart.wicam;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;

public class PeerActivity extends AppCompatActivity {

    protected ListView m_lv;
    protected TextView m_statusView;
    protected String[] mListItems;
    protected ArrayList<Bundle> mBundles = new ArrayList<Bundle>();

    private AdapterView.OnItemClickListener m_rs_listerner = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mListItems == null || mListItems.length <= position) return;
            Bundle bd = mBundles.get(position);
            onWiCamSelected(bd);
        }
    };

    public void onWiCamSelected(Bundle bd) {
        Intent intent = new Intent(this, HotspotSigninActivity.class);
        intent.putExtra(getString(R.string.params), bd);
        startActivity(intent);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peer);

        m_statusView = (TextView) findViewById(R.id.peeractivity_status);
        m_lv = (ListView)findViewById(R.id.peer_discovery_result);
        ProgressBar pb = (ProgressBar) findViewById(R.id.peer_discovery_progress);
        m_lv.setEmptyView(pb);
        m_lv.setOnItemClickListener(m_rs_listerner);
        SharedPreferences sp = getSharedPreferences("Wicam", Context.MODE_PRIVATE);
        Map<String,?> maps = sp.getAll();
        for(Map.Entry<String,?> entry : maps.entrySet()){
            String val = entry.getValue().toString();
            Log.d("PeerActivity", "checking " + val);
            try {
                JSONObject json = new JSONObject(val);
                if (!json.has(getString(R.string.ap_ssid)) || !json.has(getString(R.string.ap_pin))) {
                    continue;
                }
                if (!json.has(getString(R.string.wan_address)) || !json.has(getString(R.string.wan_port))) {
                    continue;
                }
                if (json.getString(getString(R.string.wan_address)).length() < 7 || json.getInt(getString(R.string.wan_port)) == 0) {
                    continue;
                }
                if (!json.getString(getString(R.string.ap_ssid)).startsWith("WiCam-")) {
                    continue;
                }

                Bundle bd = new Bundle();
                bd.putString(getString(R.string.ap_ssid), json.getString(getString(R.string.ap_ssid)));
                bd.putString(getString(R.string.mode), getString(R.string.cloud));
                String ip = String.format("%s:%d", json.getString(getString(R.string.wan_address)), json.getInt(getString(R.string.wan_port)));
                Log.d("PeerActivity", "Valid cloud device " + ip);
                bd.putString(getString(R.string.ip), ip);
                mBundles.add(bd);

            } catch (JSONException e) {}
        }
        Log.d("PeerActivity", "Found " + mBundles.size() + " Cloud devices enabled.");
        mListItems = new String[mBundles.size()];
        for(int i = 0; i < mBundles.size(); i++) {
            mListItems[i] = mBundles.get(i).getString(getString(R.string.ap_ssid));
        }
        m_lv.setAdapter(new ArrayAdapter<String>(this, R.layout.lv_tv_item, mListItems));
    }
}
