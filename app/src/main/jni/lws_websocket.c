/*
 * websocket.c
 *
 *  Created on: Apr 23, 2016
 *      Author: yliu
 */
#include "defs.h"
#include <stdio.h>
#include <glib-object.h>
#include <glib.h>
#include <libwebsockets.h>
#include "lws_websocket.h"

#define MAX_FRAME_PAYLOAD		(1024*8)

// GSource: http://devlib.symbian.slions.net/s3/GUID-7FD05006-09C1-4EF4-A2EB-AD98C2FA8866.html
#define ADDRESS_MAX_LEN			64
struct _LwsWebsocket {
	GObject 							parent_instance;
	gchar 								address[ADDRESS_MAX_LEN];
	gpointer							user;
	lws_onMessage						onMessage;
	lws_onClose							onClose;
	lws_onError							onError;
	struct lws_context_creation_info 	lws_ctx_info;
	struct lws_context 					*lws_ctx;
	struct lws_protocols 				protocols[2];
	struct lws_extension 				lws_exts[3];
	struct lws_client_connect_info 		clt;
	struct lws							*wsi;
	int									wsi_status;
	GQueue								*send_queue;
	guint8								*rcv_buf;
	int									rcv_buf_size;
	int									fatal;
};

enum {
	LWS_PROP_ADDRESS = 1,
	LWS_PROP_USER,
	N_PROPERTIES
};

enum {
	LWS_STATUS_NULL,
	LWS_STATUS_CONNECTING,
	LWS_STATUS_CONNECTED,
	LWS_STATUS_CLOSING,
	LWS_STATUS_CLOSED,
	LWS_STATUS_ERROR
};

enum {
	LWS_SIG_ON_CLOSE,
	LWS_SIG_ON_ERROR,
	LWS_SIG_ON_MESSAGE,
	N_SIGS
};

static int lws_default_protocol_callback(struct lws *wsi, enum lws_callback_reasons reason, void *user, void *in, size_t m);

void lws_websocket_service(LwsWebsocket *self) {
	if (!self->lws_ctx || !self->wsi) return;
	if (!g_queue_is_empty(self->send_queue)) {
		lws_callback_on_writable(self->wsi);
	}
	lws_service(self->lws_ctx, 100);

}

/********** DEFINE Type *********************/
G_DEFINE_TYPE (LwsWebsocket, lws_websocket, G_TYPE_OBJECT)

static guint lws_sigs[N_SIGS] = {0};

static GParamSpec *obj_properties[N_PROPERTIES] = { NULL, };



static int lws_default_protocol_callback(struct lws *wsi, enum lws_callback_reasons reason, void *user, void *in, size_t len) {
	GBytes *msg;
	GBytes	   *buf;
	gsize		size;
	int 		n, len2;
	guint8 *bytes;

	if (!lws_get_protocol(wsi) || !LWS_IS_WEBSOCKET(lws_get_protocol(wsi)->user)) {
		//LOGD("######### Not Websocket ############");
		return 0; // just return
	}
	LwsWebsocket *ws = LWS_WEBSOCKET(lws_get_protocol(wsi)->user);

	if (!ws || ws->wsi_status >= LWS_STATUS_CLOSED) return -1;  // this closes any unknown ws connection.
	if (ws->wsi_status == LWS_STATUS_CLOSING) {
		ws->wsi_status = LWS_STATUS_CLOSED; // user triggered closing event, lets close it.
		LOGD("connection closed by user\n");
		if (ws->onClose) ws->onClose(ws, ws->user);
		ws->wsi = NULL;
		return -1;
	}
	switch(reason) {
	case LWS_CALLBACK_CLIENT_ESTABLISHED:
		ws->wsi_status = LWS_STATUS_CONNECTED;
		LOGD("connected\n");
		break;
	case LWS_CALLBACK_CLOSED:
		LOGD("connection closed\n");
		ws->wsi_status = LWS_STATUS_CLOSED;
		if (ws->onClose) ws->onClose(ws, ws->user);
		ws->wsi = NULL;
		return -1;
	case LWS_CALLBACK_CLIENT_CONNECTION_ERROR:
		LOGD("connection error\n");
		ws->wsi_status = LWS_STATUS_ERROR;
		if (ws->onError) ws->onError(ws, ws->user, LWS_OPEN_FAIL);
		ws->wsi = NULL;
		return -1;
	case LWS_CALLBACK_CLIENT_RECEIVE: {
		int opcode = lws_frame_is_binary(wsi) != 0? LWS_WRITE_BINARY:LWS_WRITE_TEXT;
		int final = lws_is_final_fragment(wsi);
		memcpy(&(ws->rcv_buf[ws->rcv_buf_size]), in, len);
		ws->rcv_buf_size += len;
		if (!final) {
			break;
		}
		// final
		size_t sz = opcode == LWS_WRITE_TEXT? ws->rcv_buf_size + 1 : ws->rcv_buf_size;
		bytes = g_new0(guint8, sz);
		memcpy(bytes, ws->rcv_buf, ws->rcv_buf_size);
		msg = g_bytes_new_take(bytes, sz);
		if (ws->onMessage) ws->onMessage(ws, ws->user, opcode, msg);
		else g_bytes_unref(msg);
		ws->rcv_buf_size = 0;
		break;
	}
	case LWS_CALLBACK_CLIENT_WRITEABLE:

		buf = (GBytes*)g_queue_pop_tail(ws->send_queue);
		if (!buf) break;
		bytes = (unsigned char*)g_bytes_get_data(buf, &size);
		LOGD("message written type=%d, size=%d\n", bytes[0], size);
		if (bytes[0] == LWS_WRITE_TEXT) {
			len2 = strlen(&bytes[1 + LWS_PRE]);
			LOGD("\t message content: %s;%d", &bytes[1 + LWS_PRE], len2);
		} else {
			len2 = size-1-LWS_PRE;
		}
		n = lws_write(ws->wsi, &bytes[1 + LWS_PRE], len2, bytes[0]);
		g_bytes_unref(buf); // should be freed
		if (n < 0 || (n > 0 && n < len2)) {
			ws->wsi_status = LWS_STATUS_ERROR;
			if (ws->onError) ws->onError(ws, ws->user, LWS_SEND_FAIL);
			ws->wsi = NULL;
			LOGE("\t message written is partial %d of %d\n", n, len2);
			return -1; // partial
		}

		break;
	default:
		break;
	}

	return 0;
}

static void lws_websocket_finalize(GObject *gobject) {
	LwsWebsocket *self = LWS_WEBSOCKET(gobject);
	// TODO: free send_queue
	g_queue_free_full(self->send_queue, g_free);
	self->send_queue = NULL;
	g_free(self->rcv_buf);
	self->rcv_buf = NULL;
	self->rcv_buf_size = 0;
	G_OBJECT_CLASS(lws_websocket_parent_class)->finalize(gobject);

}

static void lws_websocket_set_property(GObject *obj, guint prop_id, const GValue *value, GParamSpec *spec) {
	LwsWebsocket *self = LWS_WEBSOCKET(obj);
	LOGD("lws_websocket_set_property\n");
	switch (prop_id) {
	case LWS_PROP_ADDRESS:
		memset(self->address, 0, ADDRESS_MAX_LEN);
		strcpy(self->address, g_value_get_string(value)?g_value_get_string(value):"");
		break;
	case LWS_PROP_USER:
		self->user = g_value_get_pointer(value);
		break;
	default:
		G_OBJECT_WARN_INVALID_PROPERTY_ID(obj, prop_id, spec);
		break;
	}

}

static void lws_websocket_get_property(GObject *obj, guint prop_id, GValue *value, GParamSpec *spec) {
	LwsWebsocket *self = LWS_WEBSOCKET(obj);
	LOGD("lws_websocket_get_property\n");
	switch (prop_id) {
	case LWS_PROP_ADDRESS:
		g_value_set_string(value, self->address);
		break;
	case LWS_PROP_USER:
		g_value_set_pointer(value, self->user);
		break;
	default:
		G_OBJECT_WARN_INVALID_PROPERTY_ID(obj, prop_id, spec);
		break;
	}
}


gint lws_websocket_open(LwsWebsocket *self, const gchar* address, lws_onMessage on_message, lws_onClose on_close, lws_onError on_error) {

	const char *ptc, *p;
	int use_ssl = 0;

	gchar addr[300] = {0};
	gchar path[300] = {0};

	if (self->fatal) {
		LOGS();
		return LWS_INIT_FATAL;
	}

	strcpy(addr, address);

	self->onClose = on_close;
	self->onError = on_error;
	self->onMessage = on_message;

	memset(&(self->clt), 0, sizeof(self->clt));

	if (lws_parse_uri(addr, &ptc, &(self->clt.address), &(self->clt.port), &p) || (g_strcmp0(ptc, "ws") && g_strcmp0(ptc, "wss"))) {
		return LWS_INVALID_ADDRESS;
	}

	LOGD("LWS address=%s:%d context: %p\n", self->clt.address, self->clt.port, self->lws_ctx);
	// set self->address
	strcpy(self->address, address);
	// set self->clt
	path[0] = '/';
	strncpy(path + 1, p, sizeof(path) - 2);
	path[sizeof(path) - 1] = '\0';
	use_ssl = g_strcmp0(ptc, "wss")==0?1:0;

	// set clt

	self->clt.context = self->lws_ctx;
	self->clt.ssl_connection = use_ssl;
	self->clt.path = path;
	self->clt.host = self->clt.address;
	self->clt.origin = self->clt.address;
	self->clt.ietf_version_or_minus_one = -1;
	self->clt.client_exts = self->lws_exts;

	// set connecting and wait till complete
	self->wsi_status = LWS_STATUS_CONNECTING;
	LOGD("create client connect");

	self->wsi = lws_client_connect_via_info(&(self->clt));

	int cd = 2000;
	while (self->wsi_status == LWS_STATUS_CONNECTING && cd--) {
		lws_service(self->lws_ctx, 1);
	}
	if (self->wsi_status != LWS_STATUS_CONNECTED) {
		LOGS();
		return LWS_OPEN_FAIL;
	}

	LOGD("open OK\n");
	return LWS_OK;
}

gint lws_websocket_close(LwsWebsocket *self) {
	if (self->wsi_status != LWS_STATUS_CONNECTED) {
		if (self->onError) self->onError(self, self->user, LWS_NOT_CONNECTED);
		return LWS_NOT_CONNECTED;
	}
	if (!self->wsi) {
		self->wsi_status = LWS_STATUS_CLOSED;
		return LWS_OK;
	}
	lws_websocket_send_text_message(self, "close");
	while (!lws_websocket_send_cache_empty(self)) {
		lws_websocket_service(self);
	}
	int cd = 500;
	while (self->wsi_status != LWS_STATUS_CLOSED && cd--) {
		lws_websocket_service(self);
	}
	/*
	if (!self->wsi || self->wsi_status == LWS_STATUS_CLOSED) {
		LOGS();
		self->wsi_status = LWS_STATUS_CLOSED;
		return LWS_OK;
	}
	LOGS();
	lws_close_reason(self->wsi, LWS_CLOSE_STATUS_NORMAL, "close", strlen("close"));
	self->wsi_status = LWS_STATUS_CLOSING;
	lws_callback_on_writable(self->wsi); // make callback called
	int cd = 100;
	LOGS();
	while(self->wsi_status == LWS_STATUS_CLOSING && cd--) {
		lws_service(self->lws_ctx, 1);
	}
	LOGS();
	if (self->wsi_status != LWS_STATUS_CLOSED) {
		LOGS();
		return LWS_CLOSE_FAIL;
	}
	*/
	self->wsi = NULL;
	self->wsi_status = LWS_STATUS_NULL;
	memset(self->address, 0, ADDRESS_MAX_LEN);
	memset(&(self->clt), 0, sizeof(self->clt));

	return LWS_OK;
}

gint lws_websocket_send_text_message(LwsWebsocket *self, const gchar *msg) {
	if (self->wsi_status != LWS_STATUS_CONNECTED) {
		if (self->onError) self->onError(self, self->user, LWS_NOT_CONNECTED);
		return LWS_NOT_CONNECTED;
	}
	int len = strlen(msg);
	guint8 *buf = g_new0(guint8, 1 + LWS_PRE + len + 1);
	strcpy(&buf[1 + LWS_PRE], msg);
	buf[0] = LWS_WRITE_TEXT;
	GBytes *buffer = g_bytes_new_take(buf, 1 + LWS_PRE + len + 1);
	g_queue_push_head(self->send_queue, buffer);
	lws_callback_on_writable(self->wsi);
	return LWS_OK;
}

gint lws_websocket_send_binary_message(LwsWebsocket *self, const guint8 *data, gsize len) {
	if (self->wsi_status != LWS_STATUS_CONNECTED) {
		if (self->onError) self->onError(self, self->user, LWS_NOT_CONNECTED);
		return LWS_NOT_CONNECTED;
	}
	guint8 *buf = g_new(guint8, 1 + LWS_PRE + len);
	memcpy(&buf[1 + LWS_PRE], data, len);
	buf[0] = LWS_WRITE_BINARY;
	GBytes *buffer = g_bytes_new_take(buf, 1 + LWS_PRE + len);
	g_queue_push_head(self->send_queue, buffer);
	lws_callback_on_writable(self->wsi);
	return LWS_OK;
}

gboolean lws_websocket_send_cache_empty(LwsWebsocket* self) {
	return g_queue_is_empty(self->send_queue);
}

guint lws_websocket_send_cache_size(LwsWebsocket* self) {
	return g_queue_get_length(self->send_queue);
}


static void lws_websocket_class_init(LwsWebsocketClass *klass) {
	GObjectClass *object_class = G_OBJECT_CLASS(klass);
	object_class->set_property = lws_websocket_set_property;
	object_class->get_property = lws_websocket_get_property;
	object_class->finalize = lws_websocket_finalize;
	obj_properties[LWS_PROP_ADDRESS] =
			g_param_spec_string("address", "websocket address", "remote Address", "", G_PARAM_READWRITE);
	obj_properties[LWS_PROP_USER] =
			g_param_spec_pointer("user", "websocket user associated context", "user associated data", G_PARAM_READWRITE);
	g_object_class_install_properties(object_class, N_PROPERTIES, obj_properties);


	// install signals
	// void on_close(LwsWebsocket *ws);
	lws_sigs[LWS_SIG_ON_CLOSE] = g_signal_new("onClose", G_TYPE_FROM_CLASS(klass),
			G_SIGNAL_RUN_LAST | G_SIGNAL_NO_RECURSE | G_SIGNAL_NO_HOOKS, 0, NULL, NULL, g_cclosure_marshal_VOID__VOID, G_TYPE_NONE, 0);
	// void on_error(LwsWebsocket *ws, gchar *err);
	lws_sigs[LWS_SIG_ON_ERROR] = g_signal_new("onError", G_TYPE_FROM_CLASS(klass),
			G_SIGNAL_RUN_LAST | G_SIGNAL_NO_RECURSE | G_SIGNAL_NO_HOOKS, 0, NULL, NULL, g_cclosure_marshal_VOID__STRING, G_TYPE_NONE, 1, G_TYPE_STRING);
	// void on_message(LwsWebsocket *ws, int opcode, GBytes *bytes);
	lws_sigs[LWS_SIG_ON_MESSAGE] = g_signal_new("onMessage", G_TYPE_FROM_CLASS(klass),
			G_SIGNAL_RUN_LAST | G_SIGNAL_NO_RECURSE | G_SIGNAL_NO_HOOKS, 0, NULL, NULL, g_cclosure_marshal_VOID__BOXED, G_TYPE_NONE, 1, G_TYPE_BYTES);


}

// https://developer.gnome.org/gobject/stable/chapter-gobject.html#gobject-construction-table
// https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#GObject
static void lws_websocket_init(LwsWebsocket *self) {
	self->protocols[0].name = "";
	self->protocols[0].callback = lws_default_protocol_callback;
	self->protocols[0].per_session_data_size = 0;
	self->protocols[0].rx_buffer_size = MAX_FRAME_PAYLOAD;
	self->protocols[0].id = 0;
	self->protocols[0].user = self;
	self->protocols[1].callback = NULL;
	self->protocols[1].id = 0;
	self->protocols[1].name = NULL;
	self->protocols[1].per_session_data_size = 0;
	self->protocols[1].rx_buffer_size = 0;
	self->protocols[1].user = NULL;

	self->lws_exts[0].name = "permessage-deflate";
	self->lws_exts[0].callback = lws_extension_callback_pm_deflate;
	self->lws_exts[0].client_offer = "permessage-deflate; client_max_window_bits";
	self->lws_exts[1].name = "deflate-frame";
	self->lws_exts[1].callback = lws_extension_callback_pm_deflate;
	self->lws_exts[1].client_offer = "deflate_frame";
	self->lws_exts[2].name = NULL;
	self->lws_exts[2].callback = NULL;
	self->lws_exts[2].client_offer = NULL;
	// intialize lws_context_creation_info
	self->lws_ctx_info.port = CONTEXT_PORT_NO_LISTEN;
	self->lws_ctx_info.protocols = self->protocols;
	self->lws_ctx_info.gid = -1;
	self->lws_ctx_info.uid = -1;

	// TODO: add ssl cert, key, ca settings
	//lws_ctx_info.ssl_cert_filepath = "";
	//lws_ctx_info.ssl_private_key_filepath = "";
	//lws_ctx_info.ssl_ca_filepath = "";
	LOGD("We are in init\n");
	self->fatal = 0;
	self->lws_ctx = lws_create_context(&self->lws_ctx_info);
	if (!self->lws_ctx) {
		self->fatal = 1;
		LOGE("Fatal creating context, memory issue\n");
		return;
	}
	// address = NULL
	memset(self->address, 0, ADDRESS_MAX_LEN);
	// zerolize clt structure
	memset(&(self->clt), 0, sizeof(self->clt));
	// create send queue
	self->send_queue = g_queue_new();

	// initialize receive buffer
	self->rcv_buf = g_new(guint8, 1024*90);
	self->rcv_buf_size = 0;
	// set wsi status to NULL
	self->wsi_status = LWS_STATUS_NULL;
	LOGD("We are in init exit\n");
}







