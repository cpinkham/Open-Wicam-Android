package co.armstart.wicam;

/**
 * Created by yliu on 7/9/16.
 */
public class CWicamCallback {
    public CWicamCallback mAssocate;
    public CWicamCallback() {}
    public CWicamCallback(CWicamCallback associate) {
        mAssocate = associate;
    }
    void onFrame(boolean success, int state, byte[] frame, long wid) {};
    void onOpenResult(boolean success, int state, long wid) {};
    void onLoginResult(boolean success, int state, byte fw_version,
                       String ap_ssid, String ap_pin, String sta_ssid, String sta_pin, byte sta_sec) {};
    void onCloseResult(boolean success, int state) {};

    void onStartVideoResult(boolean success, int state) {};
    void onStopMediaResult(boolean success, int state) {};
    void onStartPictureResult(boolean success, int state) {};
    void onFWUpgradeResult(boolean success, int state, int progress) {};
    void onConfUpdateResult(boolean success, int state, byte fw_version,
                            String ap_ssid, String ap_pin, String sta_ssid, String sta_pin, byte sta_sec) {};
    void onBatteryLevelResult(boolean success, int state, int batt) {};
}
