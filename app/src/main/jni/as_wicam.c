/*
 * as_wicam.c
 *
 *  Created on: Jan 12, 2016
 *      Author: yliu
 */


#include "defs.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <glib-object.h>
#include <glib.h>
#include <gst/app/gstappsrc.h>
#include <pthread.h>
#include "lws_websocket.h"
#include "as_wicam.h"


// https://gstreamer.freedesktop.org/data/doc/gstreamer/head/gst-plugins-base-libs/html/gst-plugins-base-libs-appsrc.html#gst-app-src-get-emit-signals
// https://gstreamer.freedesktop.org/data/doc/gstreamer/head/gst-plugins-base-plugins/html/gst-plugins-base-plugins-appsrc.html#GstAppSrc-need-data


#define SSID_MAX_LEN		36
#define PASSWORD_MAX_LEN	36
#define LOCATION_MAX_LEN	128
#define	FRAME_MAX_LEN		(90*1024)
#define NON_FRAME_MAX_SIZE	800
struct _AsWicam {
	GObject 			parent_instance;
	gchar				address[ADDRESS_MAX_LEN];
	gchar				ssid[SSID_MAX_LEN];
	gchar				password[PASSWORD_MAX_LEN];
	int					width;
	int					height;
	int					fps;
	gchar				location[LOCATION_MAX_LEN];
	int					mode;
	wicam_dev_info_t	dev_info;
	main_conf_t			main_conf;
	gboolean			main_conf_updated;
	int					battery_level;
	int					state;
	as_wicam_onFrame	on_frame;
	as_wicam_onError	on_error;
	as_wicam_onStateChanged on_state_changed;
	pthread_t			thread_id;
	volatile int		thread_on;
	gpointer			user;
	GstElement 			*pipeline, *source, *jpegdec, *enc, *mux, *sink;
	GstState			bus_state;
	GMainContext 		*context;
	GMainLoop 			*main_loop;
	LwsWebsocket		*ws;
	GSource				*media_callback_source;
	int					frame_len;
	guint8				*frame;
};

enum {
	WICAM_THREAD_STOPPED,
	WICAM_THREAD_STARTED,
	WICAM_THREAD_ERROR
};

enum {
	WICAM_PROP_ADDRESS = 1,
	WICAM_PROP_SSID,
	WICAM_PROP_PASSWORD,
	WICAM_PROP_WIDTH,
	WICAM_PROP_HEIGHT,
	WICAM_PROP_FPS,
	WICAM_PROP_LOCATION,
	WICAM_PROP_MODE,
	WICAM_PROP_THREAD_ON,
	WICAM_PROP_USER,
	N_PROPERTIES
};


struct _AsWicamSource{
	GSource parent;
	AsWicam*	self;
	gpointer *callback_data;
	as_wicam_source_Finalized finalized;
};

#define AS_WICAM_LOGIN_STATE_NULL		0
#define AS_WICAM_LOGIN_STATE_SENT		1
#define AS_WICAM_LOGIN_STATE_DONE		2

struct _AsWicamLoginSource{
	AsWicamSource parent;
	int			  state;
};

struct _AsWicamMediaSource{
	AsWicamSource parent;
	gchar	*pic_file;
};

struct _AsWicamMediaReqSource{
	AsWicamSource parent;
	gchar*  file_name;
	int	    h264_quality;
	int		resolution;
	int		new_state;
};




G_DEFINE_TYPE (AsWicam, as_wicam, G_TYPE_OBJECT)

static GParamSpec *obj_properties[N_PROPERTIES] = { NULL, };


static void as_wicam_init(AsWicam *self) {

	memset(self->address, 0, ADDRESS_MAX_LEN);
	memset(self->ssid, 0, SSID_MAX_LEN);
	memset(self->password, 0, PASSWORD_MAX_LEN);
	memset(self->location, 0, LOCATION_MAX_LEN);
	memset(&self->dev_info, 0, sizeof(self->dev_info));
	self->mode = WICAM_MODE_NULL;
	self->thread_on = WICAM_THREAD_STOPPED;
	self->fps = 0;
	self->width = 640;
	self->height = 480;
	self->media_callback_source = NULL;
	self->frame = g_new(guint8, FRAME_MAX_LEN);
	self->frame_len = 0;

}

static void as_wicam_set_property(GObject *obj, guint prop_id, const GValue *value, GParamSpec *spec) {
	AsWicam *self = AS_WICAM(obj);
	//GST_DEBUG("lws_websocket_set_property\n");
	switch (prop_id) {
	case WICAM_PROP_ADDRESS:
		memset(self->address, 0, ADDRESS_MAX_LEN);
		strcpy(self->address, g_value_get_string(value)?g_value_get_string(value):"");
		break;
	case WICAM_PROP_SSID:
		memset(self->ssid, 0, SSID_MAX_LEN);
		strcpy(self->ssid, g_value_get_string(value)?g_value_get_string(value):"");
		break;
	case WICAM_PROP_PASSWORD:
		memset(self->password, 0, PASSWORD_MAX_LEN);
		strcpy(self->password, g_value_get_string(value)?g_value_get_string(value):"");
		break;
	case WICAM_PROP_WIDTH:
		self->width = g_value_get_int(value);
		break;
	case WICAM_PROP_HEIGHT:
		self->height = g_value_get_int(value);
		break;
	case WICAM_PROP_FPS:
		self->fps = g_value_get_int(value);
		break;
	case WICAM_PROP_LOCATION:
		memset(self->location, 0, LOCATION_MAX_LEN);
		strcpy(self->location, g_value_get_string(value)?g_value_get_string(value):"");
		if (self->sink)
			g_object_set(self->sink, "location", self->location, NULL);
		break;
	case WICAM_PROP_MODE:
		self->mode = g_value_get_int(value);
		break;
	case WICAM_PROP_USER:
		break;
	default:
		G_OBJECT_WARN_INVALID_PROPERTY_ID(obj, prop_id, spec);
		break;
	}

}
static void as_wicam_get_property(GObject *obj, guint prop_id, GValue *value, GParamSpec *spec) {
	AsWicam *self = AS_WICAM(obj);
	//GST_DEBUG("lws_websocket_get_property\n");
	switch (prop_id) {
	case WICAM_PROP_ADDRESS:
		g_value_set_string(value, self->address);
		break;
	case WICAM_PROP_SSID:
		g_value_set_string(value, self->ssid);
		break;
	case WICAM_PROP_PASSWORD:
		g_value_set_string(value, self->password);
		break;
	case WICAM_PROP_WIDTH:
		g_value_set_int(value, self->width);
		break;
	case WICAM_PROP_HEIGHT:
		g_value_set_int(value, self->height);
		break;
	case WICAM_PROP_FPS:
		g_value_set_int(value, self->fps);
		break;
	case WICAM_PROP_LOCATION:
		g_value_set_string(value, self->location);
		break;
	case WICAM_PROP_MODE:
		g_value_set_int(value, self->mode);
		break;
	case WICAM_PROP_THREAD_ON:
		g_value_set_int(value, self->thread_on);
		break;
	case WICAM_PROP_USER:
		break;
	default:
		G_OBJECT_WARN_INVALID_PROPERTY_ID(obj, prop_id, spec);
		break;
	}
}

static void as_wicam_finalize(GObject *obj) {
	AsWicam *self = AS_WICAM(obj);
	g_free(self->frame);
	LOGS();
}




static void as_wicam_class_init(AsWicamClass *klass) {

	GObjectClass *object_class = G_OBJECT_CLASS(klass);

	object_class->set_property = as_wicam_set_property;
	object_class->get_property = as_wicam_get_property;
	object_class->finalize = as_wicam_finalize;

	obj_properties[WICAM_PROP_ADDRESS] =
			g_param_spec_string("address", "Wicam address", "Wicam address", "", G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	obj_properties[WICAM_PROP_SSID] =
			g_param_spec_string("ssid", "Wicam SSID name", "Wicam SSID name", "", G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	obj_properties[WICAM_PROP_PASSWORD] =
			g_param_spec_string("password", "Wicam password", "Wicam password", "", G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	obj_properties[WICAM_PROP_LOCATION] =
			g_param_spec_string("location", "mp4 file location", "mp4 file location", "", G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	obj_properties[WICAM_PROP_WIDTH] =
			g_param_spec_int("width", "video width", "video width", 320, 1600, 640, G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	obj_properties[WICAM_PROP_HEIGHT] =
			g_param_spec_int("height", "video height", "video height", 240, 1200, 480, G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	obj_properties[WICAM_PROP_FPS] =
			g_param_spec_int("fps", "frame rate per sec", "frame rate per sec", 0, 15, 0, G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	obj_properties[WICAM_PROP_MODE] =
			g_param_spec_int("mode", "wicam mode", "wicam mode", WICAM_MODE_NULL, WICAM_MODE_PICTURE, WICAM_MODE_NULL, G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	obj_properties[WICAM_PROP_THREAD_ON] =
			g_param_spec_int("thread_on", "thread indicator", "thread indicator", 0, 8, 0, G_PARAM_READABLE);
	obj_properties[WICAM_PROP_USER] =
			g_param_spec_pointer("user", "User pointer", " externally managed User pointer", G_PARAM_READWRITE | G_PARAM_CONSTRUCT);
	g_object_class_install_properties(object_class, N_PROPERTIES, obj_properties);

	if (!gst_is_initialized()) {
		gst_init(NULL, NULL);
	}


}



static void bus_error_cb (GstBus *bus,
		GstMessage *msg,
		AsWicam *self) {
	GError *err;
	gchar *debug;
	gst_message_parse_error (msg, &err, &debug);
	LOGE ("Bus Error: %s\n", err->message);
	g_error_free (err);
	g_free (debug);
	self->state = WICAM_STATE_PIPELINE_FATAL;
	// clean up pipeline
	if (self->bus_state != GST_STATE_NULL) {
		gst_app_src_end_of_stream(GST_APP_SRC(self->source));
		gst_element_set_state (GST_ELEMENT(self->pipeline), GST_STATE_NULL);
	}
}

static void bus_eos_cb (GstBus *bus,
		GstMessage *msg,
		AsWicam *self) {
	LOGD("############### pipeline eos ################## \n");
	GstState cs, ps;
	gst_element_get_state(GST_ELEMENT(self->pipeline), &cs, &ps, 5);

	GstStateChangeReturn cr = gst_element_set_state (GST_ELEMENT(self->pipeline), GST_STATE_NULL);
	if (cr == GST_STATE_CHANGE_SUCCESS) {
		LOGD("State changed instantly to NULL");
		self->bus_state = GST_STATE_NULL;
	} else {
		LOGD("State changed is something else %d", cr);
	}

	gst_element_get_state(GST_ELEMENT(self->pipeline), &cs, &ps, 5);
}

static void bus_state_changed_cb (GstBus *bus,
		GstMessage *msg,
		AsWicam *self) {
	GstState old, new;
	gst_message_parse_state_changed(msg, &old, &new, NULL);
	LOGE("Element %s State changed from %s to %s\n",
			GST_MESSAGE_SRC_NAME(msg),
			gst_element_state_get_name(old),
			gst_element_state_get_name(new));
	self->bus_state = new;

}

static void as_wicam_websocket_message(LwsWebsocket *ws, gpointer user, int opcode, GBytes *msg) {
	AsWicam* self = (AsWicam*)user;
	int dont_free = 0;
	if (opcode == LWS_MESSAGE_BINARY) {
		gsize size;
		guint8 *buf = g_bytes_unref_to_data(msg, &size);
		if ((self->state == WICAM_STATE_VIDEO_STREAMING || self->state == WICAM_STATE_PICTURE_SHOOT)
				&& size > NON_FRAME_MAX_SIZE) {
			memcpy(self->frame, buf, size);
			self->frame_len = size;

			guint64 t = gst_app_src_get_current_level_bytes(GST_APP_SRC(self->source)) + size;
			guint64 m = gst_app_src_get_max_bytes(GST_APP_SRC(self->source));
			if (self->state == WICAM_STATE_VIDEO_STREAMING &&
					self->bus_state != GST_STATE_NULL &&
					t < m) {
				GstBuffer *gstbuf = gst_buffer_new_wrapped(buf, size);
				dont_free = 1;
				gst_app_src_push_buffer(GST_APP_SRC(self->source), gstbuf);
			}
		} else if (size == sizeof(main_conf_t)) {
			memcpy(&(self->main_conf), buf, size);
			if (self->state == WICAM_STATE_LOGGED_IN) {
				gst_app_src_end_of_stream(GST_APP_SRC(self->source));
			}
			self->state = WICAM_STATE_LOGGED_IN;

			LOGD("as_wicam.c websocket message=main_conf.");
			self->main_conf_updated = TRUE;
		}
		if (!dont_free) g_free(buf);
	} else { // text message
		gsize size;
		const guint8 *buf = g_bytes_get_data(msg, &size);
		if (buf && !strcmp((const char*)buf, "reboot")) {
			self->state = WICAM_STATE_REBOOTING;
		} else if (buf && !strcmp((const char*)buf, "firmware upgrade failure")) {
			self->state = WICAM_STATE_UPGRADE_FIRMWARE_FAILED;
		} else if (buf && !strncmp((const char*)buf, "b=", 2)) {
			int is_digits = 1;
			int i;
			for (i = 2; i < strlen((const char*)buf); i++) {
				if (buf[i] >= '0' && buf[i] <= '9') continue;
				is_digits = 0;
				break;
			}
			if (is_digits) {
				int batt = atoi((const char*)&buf[2]);
				LOGD("Battery Level=%d", batt);
				self->battery_level = batt;
			}
		}
		g_bytes_unref(msg);
	}
}

static void as_wicam_websocket_error(LwsWebsocket *ws, gpointer user, int code) {
	AsWicam* self = (AsWicam*)user;
	LOGE("Websocket ERROR!!!!!!!");
	self->state = WICAM_STATE_CONNECT_FAILED;
	if (self->bus_state != GST_STATE_NULL) {
		gst_app_src_end_of_stream(GST_APP_SRC(self->source));
		gst_element_set_state (self->pipeline, GST_STATE_NULL);
	}
}

static void as_wicam_websocket_close(LwsWebsocket *ws, gpointer user) {
	AsWicam* self = (AsWicam*)user;
	LOGS();
	self->state = WICAM_STATE_NULL;
	if (self->bus_state != GST_STATE_NULL) {
		gst_app_src_end_of_stream(GST_APP_SRC(self->source));
		gst_element_set_state (self->pipeline, GST_STATE_NULL);
	}
}


static gpointer as_wicam_run(AsWicam *self)  {

	GstBus *bus; GSource *bus_source, *timeout_source;


	g_main_context_push_thread_default(self->context);

	self->pipeline = gst_pipeline_new ("Pipeline");
	self->source = gst_element_factory_make("appsrc", "source");
	self->jpegdec = gst_element_factory_make("jpegdec", "jpegdec");
#if USE_OPENH264
	self->enc = gst_element_factory_make("openh264enc", "enc");
#else
	self->enc = gst_element_factory_make("x264enc", "enc");
#endif
	self->mux = gst_element_factory_make("mp4mux", "mux");
	self->sink = gst_element_factory_make("filesink", "sink");
	if (!self->source || !self->jpegdec || !self->enc || !self->mux || !self->sink) {
		g_critical("NULL: %p, %p, %p, %p, %p\n", self->source, self->jpegdec, self->enc, self->mux, self->sink);
		if (self->sink) gst_object_unref(self->sink);
		if (self->mux) gst_object_unref(self->mux);
		if (self->enc) gst_object_unref(self->enc);
		if (self->jpegdec) gst_object_unref(self->jpegdec);
		if (self->source) gst_object_unref(self->source);
		if (self->pipeline) gst_object_unref(self->pipeline);
		self->enc = NULL;
		self->source = NULL;
		self->jpegdec = NULL;
		self->pipeline = NULL;
		self->mux = NULL;
		self->sink = NULL;
		g_main_context_pop_thread_default(self->context);
		g_main_context_unref(self->context);
		self->context = NULL;
		self->thread_on = WICAM_THREAD_ERROR;
		LOGS();
		return NULL;
	}

	g_object_set(G_OBJECT(self->source),
			"stream-type", GST_APP_STREAM_TYPE_STREAM,
			"format", GST_FORMAT_BUFFERS,
			"is-live", TRUE,
			"do-timestamp", TRUE,
			"block", TRUE,
			"emit-signals", FALSE,
			"caps", gst_caps_new_simple("image/jpeg",
										"width", G_TYPE_INT, self->width,
										"height", G_TYPE_INT, self->height,
										"framerate", GST_TYPE_FRACTION, self->fps, 1, NULL),
			NULL);
	gchar* loc = NULL;

	g_object_get(G_OBJECT(self), "location", &loc, NULL);
	if (loc) {
		g_object_set(G_OBJECT(self->sink), "location", loc, NULL);
		g_free(loc);
		LOGS();
	}

	gst_bin_add_many(GST_BIN(self->pipeline), self->source, self->jpegdec, self->enc, self->mux, self->sink, NULL);
	gst_element_link_many(self->source, self->jpegdec, self->enc, self->mux, self->sink, NULL);
	bus = gst_pipeline_get_bus(GST_PIPELINE(self->pipeline));
	bus_source = gst_bus_create_watch (bus);
	g_source_set_callback (bus_source, (GSourceFunc) gst_bus_async_signal_func, NULL, NULL);
	g_source_attach(bus_source, self->context);
	g_source_unref(bus_source);
	g_signal_connect(G_OBJECT(bus), "message::error", G_CALLBACK(bus_error_cb), self);
	g_signal_connect(G_OBJECT(bus), "message::eos", G_CALLBACK(bus_eos_cb), self);
	g_signal_connect(G_OBJECT(bus), "message::state-changed", G_CALLBACK(bus_state_changed_cb), self);
	gst_object_unref(bus);

	//GST_DEBUG ("Entering WiCam Gstreamer main loop... (Context:%p)\n", self);
	self->main_loop = g_main_loop_new(self->context, FALSE);
	g_object_ref(self);
	self->thread_on = WICAM_THREAD_STARTED;
	self->bus_state = GST_STATE_NULL; // initialize intial state.
	gst_element_set_state (self->pipeline, GST_STATE_NULL);

	LOGS();
	g_main_loop_run(self->main_loop);
	//GST_DEBUG ("Exited WiCam Gstreamer main loop");
	g_main_loop_unref(self->main_loop);
	g_main_context_pop_thread_default(self->context);

	g_main_context_unref(self->context);

	gst_element_set_state (self->pipeline, GST_STATE_NULL);
// exit
	gst_object_unref (self->pipeline);
	self->main_loop = NULL;
	self->context = NULL;
	self->enc = NULL;
	self->source = NULL;
	self->jpegdec = NULL;
	self->pipeline = NULL;
	self->mux = NULL;
	self->sink = NULL;
	self->thread_on = WICAM_THREAD_STOPPED;
	g_object_unref(self);
	return NULL;

}

gpointer as_wicam_get_user(AsWicam *self) {
	return self->user;
}

void as_wicam_set_user(AsWicam *self, gpointer user) {
	self->user = user;
}

int as_wicam_get_thread_on(AsWicam *self) {
	return self->thread_on;
}

static void as_wicam_stop(AsWicam *self) {
	if (self->thread_on == TRUE) {
		g_main_loop_quit(self->main_loop);
		//pthread_join(self->thread_id, NULL);
	}
}

static gboolean as_wicam_start(AsWicam *self) {
	if (self->thread_on == WICAM_THREAD_STARTED) return TRUE;
	self->thread_on = WICAM_THREAD_STOPPED;
	self->context = g_main_context_new();
	if (!pthread_create(&self->thread_id, NULL, as_wicam_run, self)) {
		while (self->thread_on == WICAM_THREAD_STOPPED);
		if (self->thread_on == WICAM_THREAD_ERROR) {
			self->thread_on = WICAM_THREAD_STOPPED;
			LOGS();
			return FALSE;
		}
		while (!g_main_loop_is_running(self->main_loop));
		return TRUE;
	}
	LOGS();
	return FALSE;

}



/////////////////////////////////////////////////////////////////////////
//////                      open API
/////////////////////////////////////////////////////////////////////////

#define API_ASSERT(expr) {if (!(expr)) return;}

static gboolean as_wicam_gsource_prepare(GSource    *source,
        gint       *timeout_) {
	return TRUE;
}

static gboolean as_wicam_gsource_check (GSource *source) {
	return TRUE;
}

static gboolean
as_wicam_gsource_open_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {
	gchar ws_url[64];
	AsWicamSource *gsource = (AsWicamSource*)source;
	AsWicam* self = gsource->self;
	// create websocket
	self->ws = g_object_new(LWS_WEBSOCKET_TYPE, "user", self, NULL);
	self->state = WICAM_STATE_CONNECTING;
	sprintf(ws_url, "ws://%s", self->address);
	gint ret = lws_websocket_open(self->ws, ws_url, as_wicam_websocket_message, as_wicam_websocket_close, as_wicam_websocket_error);
	if (ret) {
		LOGW("Wicam cannot be connected %d", ret);
		self->state = WICAM_STATE_CONNECT_FAILED;
		if (callback) ((as_wicam_open_Callback)callback)(self, FALSE, self->state, gsource->callback_data);
		g_object_unref(self);
		return G_SOURCE_REMOVE;
	}
	self->state = WICAM_STATE_CONNECTED;
	if (callback) ((as_wicam_open_Callback)callback)(self, TRUE, self->state, gsource->callback_data);

	return G_SOURCE_REMOVE;
}

static void as_wicam_gsource_finalize(GSource    *source) {
	AsWicamSource *gsource = (AsWicamSource*)source;
	AsWicam* self = gsource->self;
	if (gsource->finalized)
		gsource->finalized(self, gsource->callback_data);
	g_object_unref(self);
	return;
}

static GSourceFuncs source_funcs_open = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_open_dispatch,
	as_wicam_gsource_finalize
};

// callback_data is a user data that will be passed back when callback() was called;
void as_wicam_open(AsWicam *self, const gchar *address,
		as_wicam_open_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	LOGS();
	as_wicam_start(self); // TODO: deal with start failure
	g_object_ref(self);
	g_object_set(self, "address", address, NULL);
	GSource* gsource = g_source_new(&source_funcs_open, sizeof(AsWicamSource));
	((AsWicamSource*)gsource)->self = self;
	((AsWicamSource*)gsource)->callback_data = callback_data;
	((AsWicamSource*)gsource)->finalized = finalized;
	g_source_set_priority (gsource, G_PRIORITY_DEFAULT);
	g_source_set_callback(gsource, (GSourceFunc)callback, NULL, NULL);
	g_source_attach(gsource, self->context);
	g_source_unref(gsource);
}


/////////////////////////////////////////////////////////////////////////
//////                      login API
/////////////////////////////////////////////////////////////////////////

static gboolean
as_wicam_gsource_login_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {
	char pwd[256];
	AsWicamSource *gsource = (AsWicamSource*)source;
	AsWicamLoginSource *lsource = (AsWicamLoginSource*)source;
	AsWicam* self = gsource->self;

	if (self->state < WICAM_STATE_CONNECTED) {
		if (callback) ((as_wicam_login_Callback)callback)(self, FALSE, self->state, NULL, gsource->callback_data);
		return G_SOURCE_REMOVE;
	}
	if (lsource->state == AS_WICAM_LOGIN_STATE_NULL) {
		sprintf(pwd, "pwd:%s%s", self->ssid, self->password);
		LOGD("sending Login %s", pwd);
		lws_websocket_send_text_message(self->ws, pwd);
		lsource->state = AS_WICAM_LOGIN_STATE_SENT;
		lws_websocket_service(self->ws);
		return G_SOURCE_CONTINUE;
	} else if (lsource->state == AS_WICAM_LOGIN_STATE_SENT) {
		int cnd = 2000;
		while (self->state == WICAM_STATE_CONNECTED && cnd--) {
			lws_websocket_service(self->ws);
		}
		lsource->state = AS_WICAM_LOGIN_STATE_DONE;
		if (self->state != WICAM_STATE_LOGGED_IN) {
			if (callback) ((as_wicam_login_Callback)callback)(self, FALSE, self->state, NULL, gsource->callback_data);
		} else {
			self->main_conf_updated = FALSE;
			LOGD("as_wicam.c Login success, setting main_conf_updated = FALSE");
			if (callback) ((as_wicam_login_Callback)callback)(self, TRUE, self->state, &(self->main_conf), gsource->callback_data);
		}
		return G_SOURCE_REMOVE;
	}

	return G_SOURCE_REMOVE;
}
static GSourceFuncs source_funcs_login = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_login_dispatch,
	as_wicam_gsource_finalize
};

void as_wicam_login(AsWicam *self, const gchar *ssid, const gchar *password,
		as_wicam_login_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	g_object_ref(self);
	g_object_set(self, "ssid", ssid, "password", password, NULL);
	GSource* gsource = g_source_new(&source_funcs_login, sizeof(AsWicamLoginSource));
	((AsWicamSource*)gsource)->self = self;
	((AsWicamSource*)gsource)->callback_data = callback_data;
	((AsWicamSource*)gsource)->finalized = finalized;
	((AsWicamLoginSource*)gsource)->state = AS_WICAM_LOGIN_STATE_NULL;
	g_source_set_priority (gsource, G_PRIORITY_DEFAULT);
	g_source_set_callback(gsource, (GSourceFunc)callback, NULL, NULL);
	g_source_attach(gsource, self->context);
	g_source_unref(gsource);
}

/////////////////////////////////////////////////////////////////////////
//////                      close API
/////////////////////////////////////////////////////////////////////////
static gboolean
as_wicam_gsource_close_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {

	AsWicamSource *gsource = (AsWicamSource*)source;
	AsWicam* self = gsource->self;

	gint ret = lws_websocket_close(self->ws);

	g_object_unref(self->ws);
	self->ws = NULL;
	as_wicam_stop(self);
	self->state = WICAM_STATE_NULL;

	if (callback) ((as_wicam_close_Callback)callback)(self, TRUE, self->state, gsource->callback_data);
	LOGS();
	return G_SOURCE_REMOVE;
}

static GSourceFuncs source_funcs_close = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_close_dispatch,
	as_wicam_gsource_finalize
};
void as_wicam_close(AsWicam *self,
		as_wicam_close_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	//g_object_ref(self);
	GSource* gsource = g_source_new(&source_funcs_close, sizeof(AsWicamSource));
	((AsWicamSource*)gsource)->self = self;
	((AsWicamSource*)gsource)->callback_data = callback_data;
	((AsWicamSource*)gsource)->finalized = finalized;
	g_source_set_priority (gsource, G_PRIORITY_DEFAULT);
	g_source_set_callback(gsource, (GSourceFunc)callback, NULL, NULL);
	g_source_attach(gsource, self->context);
	g_source_unref(gsource);
}

/////////////////////////////////////////////////////////////////////////
//////    start_video, stop_video, start_picture, stop_picture API
/////////////////////////////////////////////////////////////////////////



static gboolean
as_wicam_gsource_media_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {
	AsWicamSource *gsource = (AsWicamSource*)source;
	AsWicamMediaSource *msource = (AsWicamMediaSource*)source;
	AsWicam* self = gsource->self;



	if (self->state < WICAM_STATE_LOGGED_IN ) {
		// TODO: signal lost? out of power? not connected?
		LOGS();
		if (callback)
			((as_wicam_media_Callback)callback)(self, FALSE,
				self->state,
				NULL, 0, gsource->callback_data);
		return G_SOURCE_REMOVE;
	}
	if ((self->state == WICAM_STATE_VIDEO_STREAMING || self->state == WICAM_STATE_PICTURE_SHOOT)
			&& self->frame_len > NON_FRAME_MAX_SIZE) {
		if (self->state == WICAM_STATE_PICTURE_SHOOT && msource->pic_file) {
			FILE *file = fopen(msource->pic_file, "wb");
			if (file) {
				fwrite(self->frame, 1, self->frame_len, file);
				fclose(file);
			}
			g_clear_pointer(&(msource->pic_file), g_free);
		}
		if (callback)
			((as_wicam_media_Callback)callback)(self, TRUE,
				self->state,
				self->frame, self->frame_len, gsource->callback_data);
		if (self->state == WICAM_STATE_PICTURE_SHOOT) {
			self->state = WICAM_STATE_LOGGED_IN;
		}
		self->frame_len = 0;
	} else if (self->state != WICAM_STATE_UPGRADE_FIRMWARE) { // upgrade fw has its own service call.
		lws_websocket_service(self->ws);
	}
	return G_SOURCE_CONTINUE;
}

static void as_wicam_gsource_media_finalize(GSource    *source) {
	AsWicamSource *gsource = (AsWicamSource*)source;
	AsWicam* self = gsource->self;
	if (gsource->finalized)
		gsource->finalized(self, gsource->callback_data);
	self->media_callback_source = NULL;
	g_object_unref(self);
	return;
}



static GSourceFuncs source_funcs_media = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_media_dispatch,
	as_wicam_gsource_media_finalize
};

void as_wicam_set_media_callback(AsWicam *self,
		as_wicam_media_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	g_object_ref(self);
	AsWicamMediaSource* msource = (AsWicamMediaSource*)g_source_new(&source_funcs_media, sizeof(AsWicamMediaSource));
	AsWicamSource *asource = (AsWicamSource*)msource;
	asource->self = self;
	asource->callback_data = callback_data;
	asource->finalized = finalized;
	msource->pic_file = NULL;
	g_source_set_priority ((GSource*)asource, G_PRIORITY_DEFAULT);
	g_source_set_callback((GSource*)asource, (GSourceFunc)callback, NULL, NULL);
	g_source_attach((GSource*)asource, self->context);
	self->media_callback_source = (GSource*)msource;
	g_source_unref((GSource*)asource);
}

void as_wicam_clear_media_callback(AsWicam *self) {
	API_ASSERT(AS_IS_WICAM(self));
	if (!self->media_callback_source) return;
	g_source_destroy(self->media_callback_source);
}

static gboolean
as_wicam_gsource_media_req_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {
	AsWicamMediaReqSource *mqsource = (AsWicamMediaReqSource*)source;
	AsWicamSource *asource = (AsWicamSource*)source;
	AsWicam* self = asource->self;
	gboolean succ = FALSE;
	int new_state = mqsource->new_state;
	guint media_source_id = 0;
	if (self->media_callback_source) media_source_id = g_source_get_id(self->media_callback_source);
	if (self->state < WICAM_STATE_LOGGED_IN
			|| !media_source_id) {
		LOGS();
		if (callback) ((as_wicam_media_req_Callback)callback)(self, FALSE, self->state, asource->callback_data);
		return G_SOURCE_REMOVE;
	}

	switch(new_state) {
	case WICAM_STATE_LOGGED_IN: // stop video request
		if (self->state == WICAM_STATE_VIDEO_STREAMING || self->state == WICAM_STATE_PICTURE_SHOOT) {
			LOGD("as_wicam.c stopping video or picture mode.");
			self->state = WICAM_STATE_LOGGED_IN;
			self->main_conf_updated = FALSE;
			gchar pwd[256];
			sprintf(pwd, "pwd:%s%s", self->ssid, self->password);
			lws_websocket_send_text_message(self->ws, pwd);
			return G_SOURCE_CONTINUE;
		} else if (self->state == WICAM_STATE_LOGGED_IN) {
			if (self->main_conf_updated != TRUE || self->bus_state != GST_STATE_NULL) {
				return G_SOURCE_CONTINUE;
			}
			LOGD("as_wicam.c Streaming Stopped Successfully!!! ");
			self->main_conf_updated = FALSE;
			succ = TRUE;
		}else {
			succ = FALSE;
		}
		break;
	case WICAM_STATE_VIDEO_STREAMING:
		if (self->state == WICAM_STATE_LOGGED_IN || self->state == WICAM_STATE_PICTURE_SHOOT) {
			if (mqsource->file_name && strlen(mqsource->file_name)) { // requires gstreamer pipeline
				if (self->bus_state != GST_STATE_NULL) { // wait for pipeline to be ready.
					return G_SOURCE_CONTINUE;
				} else { // setup file location, quality, resolution and send video command to wicam
					self->state = WICAM_STATE_VIDEO_STREAMING;
					g_object_set(self, "location", mqsource->file_name, NULL);
					gst_element_set_state (GST_ELEMENT (self->pipeline), GST_STATE_PLAYING);
					switch(mqsource->h264_quality) {
					case H264_QUALITY_LOW:
	#if USE_OPENH264
						g_object_set(self->enc, "complexity", 0, NULL);
	#else
						g_object_set(self->enc, "speed-preset", 4, NULL);
	#endif
						break;
					case H264_QUALITY_MEDIUM:
	#if USE_OPENH264
						g_object_set(self->enc, "complexity", 1, NULL);
	#else
						g_object_set(self->enc, "speed-preset", 6, NULL);
	#endif
						break;
					case H264_QUALITY_HIGH:
	#if USE_OPENH264
						g_object_set(self->enc, "complexity", 2, NULL);
	#else
						g_object_set(self->enc, "speed-preset", 8, NULL);
	#endif
						break;
					}

					switch(mqsource->resolution) {
					case RESOLUTION_VGA:
						lws_websocket_send_text_message(self->ws, "vga");
						break;
					case RESOLUTION_XGA:
						lws_websocket_send_text_message(self->ws, "xga");
						break;
					case RESOLUTION_UXVGA:
						lws_websocket_send_text_message(self->ws, "uxvga");
						break;
					}
					lws_websocket_send_text_message(self->ws, "video");
					return G_SOURCE_CONTINUE;
				}
			} else { // live streaming, no local encoding required.
				self->state = WICAM_STATE_VIDEO_STREAMING;
				lws_websocket_send_text_message(self->ws, "video");
				succ = TRUE;
			}
		} else if (self->state == WICAM_STATE_VIDEO_STREAMING) {
			if (self->bus_state < GST_STATE_READY) {
				return G_SOURCE_CONTINUE;
			}
			succ = TRUE;
		} else {
			succ = FALSE;
		}
		break;
	case WICAM_STATE_PICTURE_SHOOT:
		if (self->state != WICAM_STATE_LOGGED_IN && self->state != WICAM_STATE_PICTURE_SHOOT) {
			succ = FALSE;
		} else {
			((AsWicamMediaSource*)(self->media_callback_source))->pic_file = mqsource->file_name;
			mqsource->file_name = NULL;
			switch(mqsource->resolution) {
			case RESOLUTION_VGA:
				lws_websocket_send_text_message(self->ws, "vga");
				break;
			case RESOLUTION_XGA:
				lws_websocket_send_text_message(self->ws, "xga");
				break;
			case RESOLUTION_UXVGA:
				lws_websocket_send_text_message(self->ws, "uxvga");
				break;
			}
			lws_websocket_service(self->ws);
			lws_websocket_send_text_message(self->ws, "picture");
			self->state = WICAM_STATE_PICTURE_SHOOT;
			succ = TRUE;
		}
		break;
	default:
		break;
	}
	if (callback) ((as_wicam_media_req_Callback)callback)(self, succ, self->state, asource->callback_data);
	return G_SOURCE_REMOVE;
}

static void as_wicam_gsource_media_req_finalize(GSource    *source) {
	AsWicamMediaReqSource *mqsource = (AsWicamMediaReqSource*)source;
	AsWicamSource *asource = (AsWicamSource*)source;
	g_clear_pointer(&(mqsource->file_name), g_free);
	AsWicam* self = asource->self;
	if (asource->finalized)
		asource->finalized(self, asource->callback_data);
	g_object_unref(self);
	return;
}

static GSourceFuncs source_funcs_media_req = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_media_req_dispatch,
	as_wicam_gsource_media_req_finalize
};

void as_wicam_start_video(AsWicam *self,
		const gchar* vid_name,
		int h264_quality,
		int resolution,
		as_wicam_media_req_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	if (self->state == WICAM_STATE_VIDEO_STREAMING) {
		callback(self, FALSE, self->state, callback_data);
		finalized(self, callback_data);
		return;
	}
	g_object_ref(self);
	AsWicamMediaReqSource* mqsource = (AsWicamMediaReqSource*)g_source_new(&source_funcs_media_req, sizeof(AsWicamMediaReqSource));
	AsWicamSource *asource = (AsWicamSource*)mqsource;
	mqsource->file_name = g_strdup(vid_name);
	mqsource->h264_quality = h264_quality;
	mqsource->resolution = resolution;
	mqsource->new_state = WICAM_STATE_VIDEO_STREAMING;
	asource->self = self;
	asource->callback_data = callback_data;
	g_source_set_priority ((GSource*)mqsource, G_PRIORITY_DEFAULT);
	g_source_set_callback((GSource*)mqsource, (GSourceFunc)callback, NULL, NULL);
	g_source_attach((GSource*)mqsource, self->context);
	g_source_unref((GSource*)mqsource);
}


void as_wicam_stop_media(AsWicam *self,
		as_wicam_media_req_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	if (self->state == WICAM_STATE_LOGGED_IN || self->state != WICAM_STATE_VIDEO_STREAMING) {
		callback(self, FALSE, self->state, callback_data);
		finalized(self, callback_data);
		return;
	}
	g_object_ref(self);
	AsWicamMediaReqSource* mqsource = (AsWicamMediaReqSource*)g_source_new(&source_funcs_media_req, sizeof(AsWicamMediaReqSource));
	AsWicamSource *asource = (AsWicamSource*)mqsource;
	mqsource->file_name = NULL;
	mqsource->new_state = WICAM_STATE_LOGGED_IN;
	asource->self = self;
	asource->callback_data = callback_data;
	g_source_set_priority ((GSource*)mqsource, G_PRIORITY_DEFAULT);
	g_source_set_callback((GSource*)mqsource, (GSourceFunc)callback, NULL, NULL);
	g_source_attach((GSource*)mqsource, self->context);
	g_source_unref((GSource*)mqsource);

}

void as_wicam_take_picture(AsWicam *self,
		const gchar* pic_name,
		int resolution,
		as_wicam_media_req_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	g_object_ref(self);
	AsWicamMediaReqSource* mqsource = (AsWicamMediaReqSource*)g_source_new(&source_funcs_media_req, sizeof(AsWicamMediaReqSource));
	AsWicamSource *asource = (AsWicamSource*)mqsource;
	mqsource->file_name = g_strdup(pic_name);
	mqsource->resolution = resolution;
	mqsource->new_state = WICAM_STATE_PICTURE_SHOOT;
	asource->self = self;
	asource->callback_data = callback_data;
	g_source_set_priority ((GSource*)mqsource, G_PRIORITY_DEFAULT);
	g_source_set_callback((GSource*)mqsource, (GSourceFunc)callback, NULL, NULL);
	g_source_attach((GSource*)mqsource, self->context);
	g_source_unref((GSource*)mqsource);
}

/////////////////////////////////////////////////////////////
////////              firmware upgrade API
////////////////////////////////////////////////////////////


static gboolean
as_wicam_gsource_fw_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {
	AsWicamSource *gsource = (AsWicamSource*)source;
	AsWicam* self = gsource->self;
	GBytes *fw = (GBytes*)user_data;

	if (self->state != WICAM_STATE_LOGGED_IN) {
		((as_wicam_fw_Callback)callback)(self, FALSE, self->state, 0, gsource->callback_data);
		return G_SOURCE_REMOVE;
	}
	self->state = WICAM_STATE_UPGRADE_FIRMWARE;

	lws_websocket_send_text_message(self->ws, "upgrade:start");
	gsize size;
	const guint8* data = g_bytes_get_data(fw, &size);
	int offset = 0;

	// make sure we only send data in NON_FRAME_MAX_SIZE block max
	while (offset < size) {
		gsize sz = (size-offset) < NON_FRAME_MAX_SIZE? (size-offset):NON_FRAME_MAX_SIZE;
		lws_websocket_send_binary_message(self->ws, &data[offset], sz);
		offset += sz;
	}
	lws_websocket_send_text_message(self->ws, "upgrade:complete");
	guint tt_sz = lws_websocket_send_cache_size(self->ws);
	if (!tt_sz) {
		((as_wicam_fw_Callback)callback)(self, FALSE, self->state, 0, gsource->callback_data);
		return G_SOURCE_REMOVE;
	}
	while (self->state == WICAM_STATE_UPGRADE_FIRMWARE) {
		lws_websocket_service(self->ws);
		gfloat now_sz = (gfloat)lws_websocket_send_cache_size(self->ws);
		((as_wicam_fw_Callback)callback)(self, TRUE, self->state, ((float)(tt_sz - now_sz)/tt_sz)*100, gsource->callback_data);
	}

	if (self->state == WICAM_STATE_REBOOTING
			|| self->state == WICAM_STATE_NULL) {
		((as_wicam_fw_Callback)callback)(self, TRUE, self->state, 100, gsource->callback_data);
	} else {
		((as_wicam_fw_Callback)callback)(self, FALSE, self->state, 100, gsource->callback_data);
	}
	return G_SOURCE_REMOVE;

}

static GSourceFuncs source_funcs_fw = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_fw_dispatch,
	as_wicam_gsource_finalize
};

void as_wicam_upgrade_fw(AsWicam *self, guint8 *buf, gsize size, as_wicam_fw_Callback callback, gpointer callback_data, as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	GBytes *fw = g_bytes_new(buf, size);
	g_object_ref(self);
	GSource* gsource = g_source_new(&source_funcs_fw, sizeof(AsWicamSource));
	((AsWicamSource*)gsource)->self = self;
	((AsWicamSource*)gsource)->callback_data = callback_data;
	((AsWicamSource*)gsource)->finalized = finalized;
	g_source_set_priority (gsource, G_PRIORITY_DEFAULT);
	g_source_set_callback(gsource, (GSourceFunc)callback, fw, (GDestroyNotify)g_bytes_unref);
	g_source_attach(gsource, self->context);
	g_source_unref(gsource);
}


static gboolean
as_wicam_gsource_update_main_conf_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {
	AsWicamSource *asource = (AsWicamSource*)source;
	AsWicam* self = asource->self;
	main_conf_t	*main_conf = (main_conf_t*)user_data;
	as_wicam_main_conf_Callback call = (as_wicam_main_conf_Callback)callback;
	if (self->state != WICAM_STATE_LOGGED_IN) {
		call(self, FALSE, self->state, NULL, asource->callback_data);
		return G_SOURCE_REMOVE;
	}
	self->main_conf_updated = FALSE;
	lws_websocket_send_binary_message(self->ws, main_conf, sizeof(main_conf_t));
	while (self->main_conf_updated == FALSE && self->state == WICAM_STATE_LOGGED_IN) {
		lws_websocket_service(self->ws);
	}
	if (self->main_conf_updated == FALSE) {
		call(self, FALSE, self->state, NULL, asource->callback_data);
	} else {
		call(self, TRUE, self->state, &(self->main_conf), asource->callback_data);
	}
	return G_SOURCE_REMOVE;
}

static GSourceFuncs source_funcs_update_main_conf = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_update_main_conf_dispatch,
	as_wicam_gsource_finalize
};

void as_wicam_update_main_conf(AsWicam *self,
		main_conf_t *main_conf,
		as_wicam_main_conf_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	g_object_ref(self);
	main_conf_t* conf = (main_conf_t*)g_new0(main_conf_t, 1);
	memcpy(conf, main_conf, sizeof(main_conf_t));
	GSource* gsource = g_source_new(&source_funcs_update_main_conf, sizeof(AsWicamSource));
	((AsWicamSource*)gsource)->self = self;
	((AsWicamSource*)gsource)->callback_data = callback_data;
	((AsWicamSource*)gsource)->finalized = finalized;
	g_source_set_priority (gsource, G_PRIORITY_DEFAULT);
	g_source_set_callback(gsource, (GSourceFunc)callback, conf, g_free);
	g_source_attach(gsource, self->context);
	g_source_unref(gsource);
}

static gboolean
as_wicam_gsource_req_battery_level_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {
	AsWicamSource *gsource = (AsWicamSource*)source;
	AsWicam* self = gsource->self;
	if (self->state != WICAM_STATE_LOGGED_IN) {
		((as_wicam_req_battery_level_Callback)callback)(self, FALSE, self->state, 0, gsource->callback_data);
		return G_SOURCE_REMOVE;
	}
	self->battery_level = 0;
	lws_websocket_send_text_message(self->ws, "battery");
	int i = 200;
	while (i && !self->battery_level && self->state == WICAM_STATE_LOGGED_IN) {
		lws_websocket_service(self->ws);
		i--;
	}

	if (!i || self->state != WICAM_STATE_LOGGED_IN) {
		((as_wicam_req_battery_level_Callback)callback)(self, FALSE, self->state, 0, gsource->callback_data);
		return G_SOURCE_REMOVE;
	}
	((as_wicam_req_battery_level_Callback)callback)(self, TRUE, self->state, self->battery_level, gsource->callback_data);
	return G_SOURCE_REMOVE;
}
static GSourceFuncs source_funcs_req_battery_level = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_req_battery_level_dispatch,
	as_wicam_gsource_finalize
};

void as_wicam_req_battery_level (AsWicam *self,
		as_wicam_req_battery_level_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	g_object_ref(self);
	GSource* gsource = g_source_new(&source_funcs_req_battery_level, sizeof(AsWicamSource));
	((AsWicamSource*)gsource)->self = self;
	((AsWicamSource*)gsource)->callback_data = callback_data;
	((AsWicamSource*)gsource)->finalized = finalized;
	g_source_set_priority (gsource, G_PRIORITY_DEFAULT);
	g_source_set_callback(gsource, (GSourceFunc)callback, NULL, NULL);
	g_source_attach(gsource, self->context);
	g_source_unref(gsource);
}


static gboolean
as_wicam_gsource_txt_cmd_dispatch (GSource     *source,
                       GSourceFunc  callback,
                       gpointer     user_data) {
	AsWicamSource *asource = (AsWicamSource*)source;
	AsWicam* self = asource->self;
	const gchar	*cmd = (const gchar*)user_data;
	as_wicam_txt_cmd_Callback call = (as_wicam_txt_cmd_Callback)callback;
	if (self->state != WICAM_STATE_LOGGED_IN) {
		call(self, FALSE, self->state, cmd, asource->callback_data);
		return G_SOURCE_REMOVE;
	}
	lws_websocket_send_text_message(self->ws, cmd);
	while (self->state == WICAM_STATE_LOGGED_IN) {
		lws_websocket_service(self->ws);
	}
	call(self, TRUE, self->state, cmd, asource->callback_data);
	return G_SOURCE_REMOVE;
}
static GSourceFuncs source_funcs_txt_cmd = {
	as_wicam_gsource_prepare,
	as_wicam_gsource_check,
	as_wicam_gsource_txt_cmd_dispatch,
	as_wicam_gsource_finalize
};

void as_wicam_send_text_cmd(AsWicam *self,
		const gchar *cmd,
		as_wicam_txt_cmd_Callback callback,
		gpointer callback_data,
		as_wicam_source_Finalized finalized) {
	API_ASSERT(AS_IS_WICAM(self));
	g_object_ref(self);
	GSource* gsource = g_source_new(&source_funcs_txt_cmd, sizeof(AsWicamSource));
	((AsWicamSource*)gsource)->self = self;
	((AsWicamSource*)gsource)->callback_data = callback_data;
	((AsWicamSource*)gsource)->finalized = finalized;
	g_source_set_priority (gsource, G_PRIORITY_DEFAULT);
	g_source_set_callback(gsource, (GSourceFunc)callback, g_strdup(cmd), g_free);
	g_source_attach(gsource, self->context);
	g_source_unref(gsource);
}
