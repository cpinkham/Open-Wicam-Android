package co.armstart.wicam;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Click event setup for Floating Action Button
        /*
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        */
        // Shared Preference setup
        SharedPreferences pref = getPreferences(this.MODE_PRIVATE);
        Log.d("Wicam", "MainActivity onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("Wicam", "MainActivity onStart");
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.d("Wicam", "MainActivity onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("Wicam", "MainActivity onPause");

    }


    @Override
    protected void onStop() {
        super.onStop();
        Log.d("Wicam", "MainActivity onStop");

    }

    public void btnOutdoorClicked(View view) {
        Intent itnt = new Intent(this, HotspotActivity.class);
        startActivity(itnt);
    }

    public void btnHomeClicked(View view) {
        Intent itnt = new Intent(this, LanActivity.class);
        startActivity(itnt);
    }

    public void btnRemoteClicked(View view) {
        Intent itnt = new Intent(this, PeerActivity.class);
        startActivity(itnt);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
