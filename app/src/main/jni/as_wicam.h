/*
 * as_wicam.h
 *
 *  Created on: Jan 12, 2016
 *      Author: yliu
 */

#ifndef INC_AS_WICAM_H_
#define INC_AS_WICAM_H_

#include <glib.h>

G_BEGIN_DECLS

#define AS_WICAM_TYPE	as_wicam_get_type()

G_DECLARE_FINAL_TYPE(AsWicam, as_wicam, AS, WICAM, GObject)

enum {
	WICAM_MODE_NULL,
	WICAM_MODE_VIDEO,
	WICAM_MODE_PICTURE
};

enum {
	WICAM_MEDIA_TYPE_NULL,
	WICAM_MEDIA_TYPE_TEXT_MESSAGE,
	WICAM_MEDIA_TYPE_BINARY_MESSAGE,
	WICAM_MEDIA_TYPE_JPEG_VIDEO_MODE,
	WICAM_MEDIA_TYPE_JPEG_PICTURE_MODE
};

enum {
	WICAM_VIDEO_H264_QUALITY_LOW,
	WICAM_VIDEO_H264_QUALITY_MEDIUM,
	WICAM_VIDEO_H264_QUALITY_HIGH
};

enum {
	WICAM_STATE_REBOOTING = -6,
	WICAM_STATE_PIPELINE_FATAL		= -5,
	WICAM_STATE_UPGRADE_FIRMWARE_FAILED= -4,
	WICAM_STATE_ENCODING_ERROR = -3,
	WICAM_STATE_LOGIN_FAILED = -2,
	WICAM_STATE_CONNECT_FAILED = -1,
	WICAM_STATE_NULL = 0,
	WICAM_STATE_CONNECTING,
	WICAM_STATE_CONNECTED,
	WICAM_STATE_LOGGED_IN,
	WICAM_STATE_UPGRADE_FIRMWARE,
	WICAM_STATE_VIDEO_STREAMING,
	WICAM_STATE_PICTURE_SHOOT,
};

enum {
	RESOLUTION_VGA,
	RESOLUTION_XGA,
	RESOLUTION_UXVGA,
};

enum {
	H264_QUALITY_LOW,
	H264_QUALITY_MEDIUM,
	H264_QUALITY_HIGH
};

enum {
	WIFI_SEC_OPEN,
	WIFI_SEC_WEP,
	WIFI_SEC_WPA_WPA2,
	WIFI_SEC_WPS_PBC,
	WIFI_SEC_WPS_PIN,
	WIFI_SEC_WPA_ENT
};




#define ADDRESS_MAX_LEN			36
#define	MAC_MAX_LEN			6
#define SSID_LEN_MAX		32
#define MAX_PIN_LEN			12
typedef struct {
	guint8				version; // FW version
	guint8				rsvd;
	guint8				rsvd2;
	gchar				ssid[SSID_LEN_MAX+1];
	gchar				address[ADDRESS_MAX_LEN+1];  // xxx.xxx.xxx.xxx\0

}wicam_dev_info_t;

#define SL_SEC_TYPE_OPEN		0
#define SL_SEC_TYPE_WPA_WPA2	1
#pragma pack(push,1)
typedef struct _main_conf{
    guint8   	version; // FW version
    guint8   	switch_mode; // switch to xxx mode on restart。 0=none, 1=ap, 2=sta_static_ip
    guint16   	rsvd2; // 保留，以后版本使用。
    gchar 		ap_ssid[SSID_LEN_MAX+1]; //没有被配置的话ap_ssid=”WICAM-XXXX”, XXXX为MAC。
    gchar 		ap_pin[MAX_PIN_LEN+1]; //没有被配置的话strcpy(ap_pin, "wicam.cc", sizeof("wicam.cc")); // 空字符
    gchar 		sta_ssid[SSID_LEN_MAX + 1];  //没有被配置的话sta_ssid[0]= 0; 代表记录不存在。
    gchar 		sta_pin[MAX_PIN_LEN+1]; //没有被配置的话sta_pin[0]= 0;
    guint8 		sta_sec; //如：SL_SEC_TYPE_WPA_WPA2
    guint32 	static_ip;  // 4 bytes
    guint8		mac[MAC_MAX_LEN];
    guint8		rsvd3[22];
}main_conf_t;
#pragma pack(pop)


typedef void (*as_wicam_onFrame)(AsWicam *self, GBytes *frame);
typedef void (*as_wicam_onStateChanged)(AsWicam *self, int state);
typedef void (*as_wicam_onError)(AsWicam *self);

typedef struct _AsWicamSource AsWicamSource;
typedef struct _AsWicamLoginSource AsWicamLoginSource;
typedef struct _AsWicamMediaSource AsWicamMediaSource;
typedef struct _AsWicamMediaReqSource AsWicamMediaReqSource;

typedef void (*as_wicam_source_Finalized)(AsWicam *self, gpointer callback_data);



typedef void (*as_wicam_open_Callback)(AsWicam *self, gboolean succ, gint state, gpointer callback_data);
typedef void (*as_wicam_close_Callback)(AsWicam *self, gboolean succ, gint state, gpointer callback_data);

typedef void (*as_wicam_login_Callback)(AsWicam *self, gboolean succ, gint state, main_conf_t *main_conf, gpointer callback_data);


typedef void (*as_wicam_media_Callback)(AsWicam *self, gboolean succ, gint state, guint8 *buf, gsize size, gpointer callback_data);

typedef void (*as_wicam_media_req_Callback)(AsWicam *self, gboolean succ, gint state, gpointer callback_data);

typedef void (*as_wicam_fw_Callback)(AsWicam *self, gboolean succ, gint state, gint progress, gpointer callback_data);

typedef void (*as_wicam_main_conf_Callback)(AsWicam *self, gboolean succ, gint state, main_conf_t *main_conf, gpointer callback_data);

typedef void (*as_wicam_req_battery_level_Callback)(AsWicam *self, gboolean succ, gint state, int volt, gpointer callback_data);

typedef void (*as_wicam_txt_cmd_Callback)(AsWicam *self, gboolean succ, gint state, const gchar* cmd, gpointer callback_data);

////////////////////////// API ///////////////////////////////



/*********************************************
 * @brief(as_wicam_close): asynchronously open as_wicam on as_wicam's internal thread.
 *
 * @param(const gchar *address): IP:port address, user managed memory
 *
 * @param(as_wicam_open_Callback callback): callback function when open operation
 * was asynchronously processed.
 *
 * @param(gpointer callback_data): User specified external data to pass back to
 * user's callback function
 *
 * @param(as_wicam_source_Finalized finalized): after open operation was processed,
 * this function will be called. User can use this function to manage memory freeing
 * or other post operations, for example, freeing callback_data.
 ***************************************************/
void as_wicam_open(AsWicam *self, const gchar *address,
		as_wicam_open_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);


/******************************************************
 * @brief(as_wicam_login): asynchronously send login info to wicam device.
 *
 * @param(const gchar *ssid): ssid string. user managed memory.
 *
 * @param(const gchar *password): password for ssid. user managed memory.
 *
 * @param(as_wicam_login_Callback callback): callback function. called when open
 * operation was asynchronously processed.
 *
 * @param(as_wicam_source_Finalized finalized): after login operation was processed,
 * this function will be called. User can use this function to manage memory freeing
 * or other post operations, for example, freeing callback_data.
 */
void as_wicam_login(AsWicam *self, const gchar *ssid, const gchar *password,
		as_wicam_login_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

/********************************************
 * @brief(as_wicam_close): close the wicam device
 * @param(AsWicam *self):
 *
 * @param(as_wicam_close_Callback callback):
 *
 * @param(gpointer callback_data):
 *
 * @param(as_wicam_source_Finalized finalized):
 *
 * @example():
 *
 * @note:Each successfully opened wicam using as_wicam_open() must have a corresponding as_wicam_close()
 * in order to free the AsWicam object.
 *
 ********************************************/
void as_wicam_close(AsWicam *self,
		as_wicam_close_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

/********************************************
 * @brief(as_wicam_set_media_callback): set media callback
 * @param(AsWicam *self):
 *
 * @param(gpointer callback_data): user data, that will be passed back in callback
 *
 * @param(as_wicam_source_Finalized finalized):
 *
 * @example():
 *
 *
 ********************************************/
void as_wicam_set_media_callback(AsWicam *self,
		as_wicam_media_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);


void as_wicam_clear_media_callback(AsWicam *self);

void as_wicam_start_video(AsWicam *self,
		const gchar* vid_name,
		int h264_quality,
		int resolution,
		as_wicam_media_req_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

void as_wicam_stop_media(AsWicam *self,
		as_wicam_media_req_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

void as_wicam_take_picture(AsWicam *self,
		const gchar* pic_name,
		int resolution,
		as_wicam_media_req_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

void as_wicam_upgrade_fw(AsWicam *self,
		guint8 *buf,
		gsize size,
		as_wicam_fw_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

void as_wicam_update_main_conf(AsWicam *self,
		main_conf_t *main_conf,
		as_wicam_main_conf_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

void as_wicam_req_battery_level (AsWicam *self,
		as_wicam_req_battery_level_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

void as_wicam_send_text_cmd(AsWicam *self,
		const gchar *cmd,
		as_wicam_txt_cmd_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized);

G_END_DECLS


#endif /* INC_AS_WICAM_H_ */
