package co.armstart.wicam;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class VideoPlayActivity extends AppCompatActivity {

    protected File media_path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");;

    protected Bundle mBundle;

    protected String mMP4 = null;
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private Handler mIncomingHandler = new Handler () {
        @Override
        public void handleMessage(Message msg) {
            //Log.d("VideoPlayActivity", "received message");
            switch (msg.what) {
                case CWicamService.MSG_START_VIDEO:
                    handle_MSG_START_VIDEO(msg);
                    break;
                case CWicamService.MSG_ONFRAME:
                    handle_MSG_ONFRAME(msg);
                    break;
                case CWicamService.MSG_CLOSE:
                    handle_MSG_CLOSE(msg);
            }
        }
    };

    final Messenger mMessenger = new Messenger(mIncomingHandler);

    protected ImageView mJpegView;

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

    public void handle_MSG_CLOSE(Message msg) {
        RestartWicam();
    }
    public void handle_MSG_START_VIDEO(Message msg) {
        if (msg.arg1 != CWicamService.MSG_OK) {
            Toast.makeText(this, "Video mode not started.", Toast.LENGTH_LONG).show();
            if (msg.arg2 < CWicamService.CWicam.WICAM_STATE_LOGGED_IN) {
                RestartWicam();
            }
            return;
        }
        Toast.makeText(this, "Video mode started.", Toast.LENGTH_SHORT).show();
    }

    public void handle_MSG_ONFRAME (Message msg) {
        if (msg.arg1 != CWicamService.MSG_OK) {
            Toast.makeText(this, "Invalid Video Frame", Toast.LENGTH_SHORT).show();
            if (msg.arg2 < CWicamService.CWicam.WICAM_STATE_LOGGED_IN) {
                RestartWicam();
            }
            return;
        }
        Bundle dt = msg.getData();
        if (dt == null) {
            Toast.makeText(this, "Invalid Video Frame.", Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] frame = dt.getByteArray(getString(R.string.frame));
        if (frame == null || frame.length < (800)) {
            Toast.makeText(this, "Invalid Video Frame.", Toast.LENGTH_SHORT).show();
            return;
        }

        // display it
        int finalHeight, finalWidth;
        Bitmap bm = BitmapFactory.decodeByteArray(frame, 0, frame.length);
        if (bm == null) {
            Log.e("VideoPlayActivity", "Invalid JPEG data.");
            return;
        }
        int bm_w = bm.getWidth();
        int bm_h = bm.getHeight();
        if (bm_w <=0 || bm_h <=0) {
            Log.e("VideoPlayActivity", "Invalid JPEG size.");
            return;
        }
        Log.d("VideoPlayActivity", "Bitmap width=" + bm_w + " height=" + bm_h);
        Log.d("VideoPlayActivity", "ImageView width=" + mJpegView.getWidth() + " height=" + mJpegView.getHeight());
        if (mJpegView.getWidth() <= 0) {
            Log.e("VideoPlayActivity", "ImageView not attached to view?");
            return;
        }
        mJpegView.setImageBitmap(Bitmap.createScaledBitmap(bm, mJpegView.getWidth(), (bm_h*mJpegView.getWidth())/bm_w, false));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_play);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);
        mJpegView = (ImageView)findViewById(R.id.jpegView);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.stop_video_btn).setOnTouchListener(mDelayHideTouchListener);

        Intent it = getIntent();
        mBundle = it.getBundleExtra(getString(R.string.params));
        long time= System.currentTimeMillis();
        media_path.mkdirs();
        File file = new File(media_path, "WiCam-"+time+".mp4");
        mMP4 = file.getAbsolutePath();
        Intent intent2 = new Intent(this, CWicamService.class);
        mBundle.putInt(getString(R.string.h264_quality), CWicamService.CWicam.H264_QUALITYT_MEDIUM);
        mBundle.putInt(getString(R.string.resolution), CWicamService.CWicam.RESOLUTION_VGA);
        mBundle.putString(getString(R.string.mp4_path), mMP4);
        intent2.putExtra("Messenger", mMessenger);
        intent2.putExtra("what", CWicamService.MSG_START_VIDEO);
        intent2.putExtra(getString(R.string.params), mBundle);
        startService(intent2);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Intent intent2 = new Intent(this, CWicamService.class);
        intent2.putExtra("what", CWicamService.MSG_STOP_MEDIA);
        intent2.putExtra(getString(R.string.params), mBundle);
        startService(intent2);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public void btnStopVideoClicked(View view) {
        Intent intent2 = new Intent(this, CWicamService.class);
        intent2.putExtra("what", CWicamService.MSG_STOP_MEDIA);
        intent2.putExtra(getString(R.string.params), mBundle);
        startService(intent2);
    }
}
