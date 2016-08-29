//
// Created by yliu on 6/15/16.
//


#include <jni.h>
#include "defs.h"
#include <gst/app/gstappsrc.h>
#include "lws_websocket.h"
#include "as_wicam.h"
#include "mini_upnpc.h"


JavaVM* java_vm = NULL;
static pthread_key_t current_jni_env;

// Register this thread with the VM
static JNIEnv *attach_current_thread (void) {
  JNIEnv *env;
  JavaVMAttachArgs args;

  LOGD ("Attaching thread %p", g_thread_self ());
  args.version = JNI_VERSION_1_4;
  args.name = NULL;
  args.group = NULL;

  if ((*java_vm)->AttachCurrentThread (java_vm, &env, &args) < 0) {
    LOGE ("Failed to attach current thread");
    return NULL;
  }

  return env;
}

// Unregister this thread from the VM
static void detach_current_thread (void *env) {
  LOGD ("Detaching thread %p", g_thread_self ());
  (*java_vm)->DetachCurrentThread (java_vm);
}

// Retrieve the JNI environment for this thread
static JNIEnv *get_jni_env (void) {
  JNIEnv *env;

  if ((env = pthread_getspecific (current_jni_env)) == NULL) {
    env = attach_current_thread ();
    pthread_setspecific (current_jni_env, env);
  }

  return env;
}



////////////////////////////////////////////////////////////

#define CWICAMOPENCALLBACK_ONRESULT     "onOpenResult"
#define CWICAMOPENCALLBACK_ONRESULT_SIG "(ZIJ)V"

static void as_wicam_open_callback(AsWicam *self, gboolean succ, gint state, gpointer callback_data) {
    JNIEnv* env = get_jni_env();
    // check if callback_obj is a class ofCWicamOpenCallback
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    // void onResult(boolean, long);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAMOPENCALLBACK_ONRESULT, CWICAMOPENCALLBACK_ONRESULT_SIG);
    if (mid == 0) {
        // method not found? ignore it.
        LOGS();
        return;
    }
    LOGS();
    if (succ == TRUE) {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state, (jlong)self);
    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state, (jlong)(0L));
    }
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    LOGS();
}

static void cwicam_func_call_finalized(AsWicam *self, jobject callback_data) {
    JNIEnv* env = get_jni_env();
    (*env)->DeleteGlobalRef(env, callback_data);
    LOGS();
}
/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_open
 * Signature: (Ljava/lang/String;Lco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_open (JNIEnv *env, jobject service, jstring addr, jobject callback_obj) {
    // create a new Wicam
    const char *caddr = (*env)->GetStringUTFChars(env, addr, NULL);
    AsWicam *self = g_object_new(AS_WICAM_TYPE, NULL); //
    // Then open the websocket connection
    as_wicam_open(self, caddr, as_wicam_open_callback, (*env)->NewGlobalRef(env, callback_obj), cwicam_func_call_finalized);
    (*env)->ReleaseStringUTFChars(env, addr, caddr);
    LOGD("cwicam_open Source sent as_wicam=%p", self);
    return;
}


////////////////////////////////////////////////////////////

static void cwicam_close_callback(AsWicam *self, gboolean succ, gint state, gpointer callback_data);

#define CWICAMLOGINCALLBACK_ONRESULT        "onLoginResult"
#define CWICAMLOGINCALLBACK_ONRESULT_SIG    "(ZIBLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;B)V"
static void cwicam_login_callback(AsWicam *self, gboolean succ, gint state, main_conf_t *main_conf, gpointer callback_data) {
    LOGD("cwicam_login_callback start as_wicam=%p", self);
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    // void onResult(boolean succ, long wid, byte fw_version, String ap_ssid, String ap_pin, String sta_ssid, String sta_pin, byte sta_sec);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAMLOGINCALLBACK_ONRESULT, CWICAMLOGINCALLBACK_ONRESULT_SIG);
    if (mid == 0) {
        // not found? ignore it
        return;
    }
    if (succ == TRUE) {
        jbyte fw_version = main_conf->version;
        jstring ap_ssid = (*env)->NewStringUTF(env, main_conf->ap_ssid);
        jstring ap_pin = (*env)->NewStringUTF(env, main_conf->ap_pin);
        jstring sta_ssid = (*env)->NewStringUTF(env, main_conf->sta_ssid);
        jstring sta_pin = (*env)->NewStringUTF(env, main_conf->sta_pin);
        jbyte sta_sec = main_conf->sta_sec;
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state, fw_version, ap_ssid, ap_pin, sta_ssid, sta_pin, sta_sec);
        (*env)->DeleteLocalRef(env, ap_ssid);
        (*env)->DeleteLocalRef(env, ap_pin);
        (*env)->DeleteLocalRef(env, sta_ssid);
        (*env)->DeleteLocalRef(env, sta_pin);
    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state, 0, 0, 0, 0, 0, 0);
        // TODO: ahtough login failed, but connection was established, so close connection here.
        as_wicam_close(self,
                cwicam_close_callback,
                (*env)->NewGlobalRef(env, callback_data),
                cwicam_func_call_finalized);
    }
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    LOGD("cwicam_login_callback end as_wicam=%p", self);
}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_login
 * Signature: (JLjava/lang/String;Ljava/lang/String;Lco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_login (JNIEnv *env, jobject service, jlong wid, jstring ssid, jstring pwd, jobject callback_obj) {
    const char *cssid = (*env)->GetStringUTFChars(env, ssid, NULL);
    const char *cpwd = (*env)->GetStringUTFChars(env, pwd, NULL);
    AsWicam *self = (AsWicam*)wid;
    LOGD("cwicam_login start as_wicam=%p", self);
    as_wicam_login(self, cssid, cpwd, cwicam_login_callback, (*env)->NewGlobalRef(env, callback_obj), cwicam_func_call_finalized);
    (*env)->ReleaseStringUTFChars(env, ssid, cssid);
    (*env)->ReleaseStringUTFChars(env, pwd, cpwd);
    LOGD("cwicam_login end as_wicam=%p", self);
}

////////////////////////////////////////////////////////////

#define CWICAMCLOSECALLBACK_ONRESULT        "onCloseResult"
#define CWICAMCLOSECALLBACK_ONRESULT_SIG    "(ZI)V"
static void cwicam_close_callback(AsWicam *self, gboolean succ, gint state, gpointer callback_data) {
    LOGD("cwicam_close_callback start as_wicam=%p", self);
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    // void onResult(boolean, long);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAMCLOSECALLBACK_ONRESULT, CWICAMCLOSECALLBACK_ONRESULT_SIG);
    if (mid == 0) {
        return;
    }
    if (succ == TRUE) {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state);
    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state);
    }
    LOGD("cwicam_close_callback start as_wicam=%p", self);
}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_close
 * Signature: (JLco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_close (JNIEnv *env, jobject service, jlong wid, jobject callback_obj) {
    AsWicam *self = (AsWicam*)wid;
    as_wicam_close(self, cwicam_close_callback, (*env)->NewGlobalRef(env, callback_obj), cwicam_func_call_finalized);
}

#define CWICAMMEDIACALLBACK_ONRESULT        "onFrame"
#define CWICAMMEDIACALLBACK_ONRESULT_SIG    "(ZI[BJ)V"
void cwicam_media_callback(AsWicam *self, gboolean succ, gint state, guint8 *buf, gsize size, gpointer callback_data) {
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAMMEDIACALLBACK_ONRESULT, CWICAMMEDIACALLBACK_ONRESULT_SIG);
    (*env)->DeleteLocalRef(env, callback_clz);
    if (mid == 0) {
        return;
    }
    if (succ == FALSE) {
        // TODO: error happened! it could be: connection faied, closed, or login faild.
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state, NULL, (jlong)self);
        if (state < WICAM_STATE_LOGGED_IN) {
            as_wicam_close(self,
                cwicam_close_callback,
                (*env)->NewGlobalRef(env, callback_data),
                cwicam_func_call_finalized);
            }
        return;
    }
    jbyteArray ba = (*env)->NewByteArray(env, size);
    if (ba == NULL) {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state, NULL, (jlong)self);
        // memory issue, always close.
        as_wicam_close(self,
            cwicam_close_callback,
            (*env)->NewGlobalRef(env, callback_data),
            cwicam_func_call_finalized);
        return;
    }
    (*env)->SetByteArrayRegion(env, ba, 0, (jsize)size, (jbyte*)buf);
    if (buf[0] != 0xFF || buf[1] != 0xD8) {
        LOGD("CWicam Invalid JPEG");
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state, NULL, (jlong)self);
        if (state < WICAM_STATE_LOGGED_IN) {
            as_wicam_close(self,
                cwicam_close_callback,
                (*env)->NewGlobalRef(env, callback_data),
                cwicam_func_call_finalized);
        }
        return;
    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state, ba, (jlong)self);
    }
    (*env)->DeleteLocalRef(env, ba);

}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_set_media_callback
 * Signature: (JLco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_set_media_callback (JNIEnv *env, jobject service, jlong wid, jobject callback_obj) {
    AsWicam *self = (AsWicam*)wid;
    as_wicam_set_media_callback(self,
            cwicam_media_callback,
            (*env)->NewGlobalRef(env, callback_obj),
            cwicam_func_call_finalized);
}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_clear_media_callback
 * Signature: (J)V
 */
static void cwicam_clear_media_callback (JNIEnv *env, jobject service, jlong wid) {
    AsWicam *self = (AsWicam*)wid;
    as_wicam_clear_media_callback(self);
}


#define CWICAM_START_VIDEO_CALLBACK_ONRESULT        "onStartVideoResult"
#define CWICAM_START_VIDEO_CALLBACK_ONRESULT_SIG    "(ZI)V"
static void cwicam_start_video_callback(AsWicam *self, gboolean succ, gint state, gpointer callback_data) {
    LOGD("cwicam_start_video_callback as_wicam=%p", self);
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAM_START_VIDEO_CALLBACK_ONRESULT, CWICAM_START_VIDEO_CALLBACK_ONRESULT_SIG);
    (*env)->DeleteLocalRef(env, callback_clz);
    if (mid == 0) {
        return;
    }
    LOGD("cwicam_start_video_callback succ=%d, state=%d", succ, state);
    if (succ == FALSE) {
        // TODO: error happened! it could be: connection faied, closed, or login faild.
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state);
        if (state < WICAM_STATE_LOGGED_IN) {
            as_wicam_close(self,
                    cwicam_close_callback,
                    (*env)->NewGlobalRef(env, callback_data),
                    cwicam_func_call_finalized);
        }
    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state);
    }
}
/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_start_video
 * Signature: (JLjava/lang/String;IILco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_start_video (JNIEnv *env, jobject service,
        jlong wid,
        jstring mp4_path,
        jint h264_quality,
        jint resolution,
        jobject callback_obj) {
    AsWicam *self = (AsWicam*)wid;
    if (mp4_path == NULL) {
        LOGS();
        as_wicam_start_video(self, NULL,
                h264_quality,
                resolution,
                cwicam_start_video_callback,
                (*env)->NewGlobalRef(env, callback_obj),
                cwicam_func_call_finalized);
        LOGS();
    } else {
        LOGS();
        const char *cmp4_path = (*env)->GetStringUTFChars(env, mp4_path, NULL);
        as_wicam_start_video(self, cmp4_path,
                h264_quality,
                resolution,
                cwicam_start_video_callback,
                (*env)->NewGlobalRef(env, callback_obj),
                cwicam_func_call_finalized);
        (*env)->ReleaseStringUTFChars(env, mp4_path, cmp4_path);
        LOGS();
    }
}

#define CWICAM_STOP_VIDEO_CALLBACK_ONRESULT        "onStopMediaResult"
#define CWICAM_STOP_VIDEO_CALLBACK_ONRESULT_SIG    "(ZI)V"
static void cwicam_stop_media_callback(AsWicam *self, gboolean succ, gint state, gpointer callback_data) {
    LOGD("cwicam_stop_media_callback as_wicam=%p", self);
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAM_STOP_VIDEO_CALLBACK_ONRESULT, CWICAM_STOP_VIDEO_CALLBACK_ONRESULT_SIG);
    (*env)->DeleteLocalRef(env, callback_clz);
    if (mid == 0) {
        return;
    }
    LOGD("cwicam_stop_media_callback succ=%d, state=%d", succ, state);
    if (succ == FALSE) {
        // TODO: error happened! it could be: connection faied, closed, or login faild.
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state);
        if (state < WICAM_STATE_LOGGED_IN) {
            as_wicam_close(self,
                cwicam_close_callback,
                (*env)->NewGlobalRef(env, callback_data),
                cwicam_func_call_finalized);
        }
    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state);
    }
}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_stop_video
 * Signature: (JLco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_stop_media(JNIEnv *env, jobject service, jlong wid, jobject callback_obj) {
    AsWicam *self = (AsWicam*)wid;
    as_wicam_stop_media(self,
            cwicam_stop_media_callback,
            (*env)->NewGlobalRef(env, callback_obj),
            cwicam_func_call_finalized);
}


#define CWICAM_START_PICTURE_CALLBACK_ONRESULT        "onStartPictureResult"
#define CWICAM_START_PICTURE_CALLBACK_ONRESULT_SIG    "(ZI)V"
static void cwicam_start_picture_callback(AsWicam *self, gboolean succ, gint state, gpointer callback_data) {
    LOGD("cwicam_start_picture_callback as_wicam=%p", self);
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAM_START_PICTURE_CALLBACK_ONRESULT, CWICAM_START_PICTURE_CALLBACK_ONRESULT_SIG);
    (*env)->DeleteLocalRef(env, callback_clz);
    if (mid == 0) {
        return;
    }
    LOGD("cwicam_start_picture_callback succ=%d, state=%d", succ, state);
    if (succ == FALSE) {
        // TODO: error happened! it could be: connection faied, closed, or login faild.
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state);
        if (state < WICAM_STATE_LOGGED_IN) {
            as_wicam_close(self,
                        cwicam_close_callback,
                        (*env)->NewGlobalRef(env, callback_data),
                        cwicam_func_call_finalized);
        }
    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state);
    }
}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_start_picture
 * Signature: (JLjava/lang/String;ILco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_start_picture (JNIEnv *env, jobject service,
        jlong wid,
        jstring jpeg_path,
        jint resolution,
        jobject callback_obj) {
    AsWicam *self = (AsWicam*)wid;
    if (jpeg_path == NULL) {
        LOGS();
        as_wicam_take_picture(self, NULL,
                resolution,
                cwicam_start_picture_callback,
                (*env)->NewGlobalRef(env, callback_obj),
                cwicam_func_call_finalized);
        LOGS();
    } else {
        LOGS();
        const char *cjpeg_path = (*env)->GetStringUTFChars(env, jpeg_path, NULL);
        as_wicam_take_picture(self, cjpeg_path,
                resolution,
                cwicam_start_picture_callback,
                (*env)->NewGlobalRef(env, callback_obj),
                cwicam_func_call_finalized);
        (*env)->ReleaseStringUTFChars(env, jpeg_path, cjpeg_path);
        LOGS();
    }
}

#define CWICAM_FW_UPGRADE_CALLBACK_ONRESULT         "onFWUpgradeResult"
#define CWICAM_FW_UPGRADE_CALLBACK_ONRESULT_SIG     "(ZII)V"
static void cwicam_fw_upgrade_callback(AsWicam *self,
        gboolean succ,
        gint state,
        gint progress,
        gpointer callback_data) {
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAM_FW_UPGRADE_CALLBACK_ONRESULT, CWICAM_FW_UPGRADE_CALLBACK_ONRESULT_SIG);
    (*env)->DeleteLocalRef(env, callback_clz);
    if (mid == 0) {
        return;
    }
    LOGD("cwicam_fw_upgrade_callback succ=%d, state=%d, progress=%d", succ, state, progress);
    if (succ == FALSE) {
        // TODO: error happened! it could be: connection faied, closed, or login faild.
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state, progress);
        if (state < WICAM_STATE_LOGGED_IN) {
            as_wicam_close(self,
                    cwicam_close_callback,
                    (*env)->NewGlobalRef(env, callback_data),
                    cwicam_func_call_finalized);
        }
    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state, progress);
    }
}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_fw_upgrade
 * Signature: (J[BLco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_fw_upgrade (JNIEnv *env, jobject service, jlong wid, jbyteArray buf, jobject callback_obj) {
    AsWicam *self = (AsWicam*)wid;
    jbyte* buff = (*env)->GetByteArrayElements(env, buf, NULL);
    jsize  size = (*env)->GetArrayLength(env, buf);
    as_wicam_upgrade_fw(self, buff, size,
            cwicam_fw_upgrade_callback,
            (*env)->NewGlobalRef(env, callback_obj),
            cwicam_func_call_finalized);
    (*env)->ReleaseByteArrayElements(env, buf, buff, JNI_ABORT);
}

#define CWICAM_CONF_UPDATE_CALLBACK_ONRESULT    "onConfUpdateResult"
#define CWICAM_CONF_UPDATE_CALLBACK_ONRESULT_SIG    "(ZIBLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;B)V"
static void cwicam_update_conf_callback(AsWicam *self,
        gboolean succ,
        gint state,
        main_conf_t *main_conf,
        gpointer callback_data) {
    LOGD("cwicam_update_conf_callback as_wicam=%p", self);
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz, CWICAM_CONF_UPDATE_CALLBACK_ONRESULT, CWICAM_CONF_UPDATE_CALLBACK_ONRESULT_SIG);
    (*env)->DeleteLocalRef(env, callback_clz);
    if (mid == 0) {
        return;
    }
    LOGD("cwicam_update_conf_callback succ=%d, state=%d", succ, state);
    if (succ == FALSE) {
        // TODO: error happened! it could be: connection faied, closed, or login faild.
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state, 0, NULL, NULL, NULL, NULL, 0);
        if (state < WICAM_STATE_LOGGED_IN) {
            as_wicam_close(self,
                    cwicam_close_callback,
                    (*env)->NewGlobalRef(env, callback_data),
                    cwicam_func_call_finalized);
        }
    } else {
        jstring ap_ssid = (*env)->NewStringUTF(env, main_conf->ap_ssid);
        jstring ap_pin = (*env)->NewStringUTF(env, main_conf->ap_pin);
        jstring sta_ssid = (*env)->NewStringUTF(env, main_conf->sta_ssid);
        jstring sta_pin = (*env)->NewStringUTF(env, main_conf->sta_pin);
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid,
                    JNI_TRUE,
                    state,
                    main_conf->version,
                    ap_ssid,
                    ap_pin,
                    sta_ssid,
                    sta_pin,
                    main_conf->sta_sec);
        (*env)->DeleteLocalRef(env, ap_ssid);
        (*env)->DeleteLocalRef(env, ap_pin);
        (*env)->DeleteLocalRef(env, sta_ssid);
        (*env)->DeleteLocalRef(env, sta_pin);
    }
}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_update_conf
 * Signature: (JBLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;BLco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_update_conf(JNIEnv *env, jobject service,
        jlong wid,
        jbyte switch_mode,
        jstring ap_ssid,
        jstring ap_pin,
        jstring sta_ssid,
        jstring sta_pin,
        jbyte sta_sec,
        jobject callback_obj) {
    LOGS();
    AsWicam *self = (AsWicam*)wid;
    main_conf_t main_conf;
    memset(&main_conf, 0, sizeof(main_conf));
    main_conf.switch_mode = switch_mode;

    const char *cap_ssid = (*env)->GetStringUTFChars(env, ap_ssid, NULL);
    strcpy(main_conf.ap_ssid, cap_ssid);
    (*env)->ReleaseStringUTFChars(env, ap_ssid, cap_ssid);

    const char *cap_pin = (*env)->GetStringUTFChars(env, ap_pin, NULL);
    strcpy(main_conf.ap_pin, cap_pin);
    (*env)->ReleaseStringUTFChars(env, ap_pin, cap_pin);

    const char *csta_ssid = (*env)->GetStringUTFChars(env, sta_ssid, NULL);
    strcpy(main_conf.sta_ssid, csta_ssid);
    (*env)->ReleaseStringUTFChars(env, sta_ssid, csta_ssid);

    const char *csta_pin = (*env)->GetStringUTFChars(env, sta_pin, NULL);
    strcpy(main_conf.sta_pin, csta_pin);
    (*env)->ReleaseStringUTFChars(env, sta_pin, csta_pin);

    main_conf.sta_sec = sta_sec;
    LOGS();
    as_wicam_update_main_conf(self, &main_conf,
            cwicam_update_conf_callback,
            (*env)->NewGlobalRef(env, callback_obj),
            cwicam_func_call_finalized);
}

#define CWICAM_REQ_BATTERY_LEVEL_CALLBACK_ONRESULT    "onBatteryLevelResult"
#define CWICAM_REQ_BATTERY_LEVEL_CALLBACK_ONRESULT_SIG    "(ZII)V"
static void cwicam_req_battery_level_callback(AsWicam *self,
                                gboolean succ,
                                gint state,
                                int volt,
                                gpointer callback_data) {
    JNIEnv* env = get_jni_env();
    jclass callback_clz = (*env)->GetObjectClass(env, (jobject)callback_data);
    jmethodID mid = (*env)->GetMethodID(env, callback_clz,
                                    CWICAM_REQ_BATTERY_LEVEL_CALLBACK_ONRESULT,
                                    CWICAM_REQ_BATTERY_LEVEL_CALLBACK_ONRESULT_SIG);
    (*env)->DeleteLocalRef(env, callback_clz);
    if (mid == 0) {
        return;
    }
    LOGD("cwicam_req_battery_level_callback succ=%d, state=%d, progress=%d", succ, state, volt);
    if (succ == FALSE) {
        // TODO: error happened! it could be: connection faied, closed, or login faild.
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_FALSE, state, 0);
        if (state < WICAM_STATE_LOGGED_IN) {
            as_wicam_close(self,
                    cwicam_close_callback,
                    (*env)->NewGlobalRef(env, callback_data),
                    cwicam_func_call_finalized);
        }

    } else {
        (*env)->CallVoidMethod(env, (jobject)callback_data, mid, JNI_TRUE, state, volt);
    }
}


/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_req_battery_level
 * Signature: (JLco/armstart/wicam/CWicamCallback;)V
 */
static void cwicam_req_battery_level(JNIEnv *env, jobject service,
        jlong wid,
        jobject callback_obj) {
    LOGS();
    AsWicam *self = (AsWicam*)wid;
    as_wicam_req_battery_level(self,
            cwicam_req_battery_level_callback,
            (*env)->NewGlobalRef(env, callback_obj),
            cwicam_func_call_finalized);
}

/*
 * Class:     co_armstart_wicam_CWicamService
 * Method:    cwicam_update_conf
 * Signature: ()Ljava/lang/String;
 */
static jstring upnpc_get_external_ip (JNIEnv *env, jclass service_class) {
    MiniUpnpc *self = g_object_new (MINI_UPNPC_TYPE, NULL);
    if (mini_upnpc_discover(self) != UPNPC_OK) {
        g_object_unref(self);
        return NULL;
    }
    char wan_addr[54];
    if (mini_upnpc_get_external_ip(self, wan_addr)) {
        g_object_unref(self);
        return NULL;
    }
    jstring ext_ip = (*env)->NewStringUTF(env, wan_addr);
    g_object_unref(self);
    return ext_ip;
}
// (Ljava/lang/String;III)Ljava/lang/String;
static jstring upnpc_add_port(JNIEnv *env, jclass service_class,
        jstring lan_address,
        int lan_port,
        int wan_port,
        int duration) {
    char wan_addr[64];
    LOGS();
    MiniUpnpc *self = g_object_new (MINI_UPNPC_TYPE, NULL);
    if (mini_upnpc_discover(self) != UPNPC_OK) {
        LOGS();
        g_object_unref(self);
        return NULL;
    }
    LOGS();
    const char *lan_addr = (*env)->GetStringUTFChars(env, lan_address, NULL);
    LOGD("cwicam.c upnpc_add_port() Lan address=%s", lan_addr);
    if (mini_upnpc_add_port(self, lan_addr, lan_port, wan_port, duration, wan_addr) != UPNPC_OK) {
        (*env)->ReleaseStringUTFChars(env, lan_address, lan_addr);
        g_object_unref(self);
        LOGS();
        return NULL;
    }
    jstring ext_ip = (*env)->NewStringUTF(env, wan_addr);
    (*env)->ReleaseStringUTFChars(env, lan_address, lan_addr);
    g_object_unref(self);
    return ext_ip;
}

// (I)Z
static jboolean upnpc_remove_port(JNIEnv *env, jclass service_class, int wan_port) {
    MiniUpnpc *self = g_object_new (MINI_UPNPC_TYPE, NULL);
    if (mini_upnpc_discover(self) != UPNPC_OK) {
        g_object_unref(self);
        return JNI_FALSE;
    }
    if (mini_upnpc_remove_port(self, wan_port) != UPNPC_OK) {
        g_object_unref(self);
        return JNI_FALSE;
    }
    g_object_unref(self);
    return JNI_TRUE;
}

// ()[Ljava/lang/String;
static jobjectArray upnpc_list_ports(JNIEnv *env, jclass service_class) {
    port_map_item_t maps[32];
    gsize sz;
    MiniUpnpc *self = g_object_new (MINI_UPNPC_TYPE, NULL);
    if (mini_upnpc_discover(self) != UPNPC_OK) {
        g_object_unref(self);
        return NULL;
    }
    if (mini_upnpc_list_ports(self, maps, 32, &sz) != UPNPC_OK) {
        g_object_unref(self);
        return NULL;
    }
    if (sz == 0) {
        g_object_unref(self);
        return NULL;
    }
    gchar data[256];
    jobjectArray ret= (jobjectArray)(*env)->NewObjectArray(env, sz,(*env)->FindClass(env, "java/lang/String"),(*env)->NewStringUTF(env, ""));
    int i = 0;
    for (i = 0; i < sz; i++) {
        memset(data, 0, 256);
        sprintf(data, "%d %s %d %s %d",
                    maps[i].index,
                    maps[i].lan_address,
                    maps[i].lan_port,
                    maps[i].wan_address,
                    maps[i].wan_port);
        (*env)->SetObjectArrayElement(env, ret,i,(*env)->NewStringUTF(env, data));
    }
    return ret;
}


static JNINativeMethod native_methods[] = {
    {"cwicam_open", "(Ljava/lang/String;Lco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_open},
    {"cwicam_login", "(JLjava/lang/String;Ljava/lang/String;Lco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_login},
    {"cwicam_close", "(JLco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_close},
    {"cwicam_set_media_callback", "(JLco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_set_media_callback},
    {"cwicam_clear_media_callback", "(J)V", (void*)cwicam_clear_media_callback},
    {"cwicam_start_video", "(JLjava/lang/String;IILco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_start_video},
    {"cwicam_stop_media", "(JLco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_stop_media},
    {"cwicam_start_picture", "(JLjava/lang/String;ILco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_start_picture},
    {"cwicam_fw_upgrade", "(J[BLco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_fw_upgrade},
    {"cwicam_update_conf", "(JBLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;BLco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_update_conf},
    {"cwicam_req_battery_level", "(JLco/armstart/wicam/CWicamCallback;)V", (void*)cwicam_req_battery_level},

    {"upnpc_add_port", "(Ljava/lang/String;III)Ljava/lang/String;", (void*)upnpc_add_port},
    {"upnpc_get_external_ip", "()Ljava/lang/String;", (void*)upnpc_get_external_ip},
    {"upnpc_remove_port", "(I)Z", (void*)upnpc_remove_port},
    {"upnpc_list_ports", "()[Ljava/lang/String;", (void*)upnpc_list_ports}
};


jint JNI_OnLoad(JavaVM* jvm, void* aReserved) {
    java_vm = jvm;
    JNIEnv* env;
    if ((*jvm)->GetEnv(jvm, &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed loading libcwicam.so");
        return -1;
    }
    jclass service_class = (*env)->FindClass(env, "co/armstart/wicam/CWicamService");
    (*env)->RegisterNatives(env, service_class, native_methods, G_N_ELEMENTS(native_methods));
    pthread_key_create (&current_jni_env, detach_current_thread);
    return JNI_VERSION_1_6;
}

