/*
 * lws_websocket.h
 *
 *  Created on: Apr 23, 2016
 *      Author: yliu
 */

#ifndef INC_LWS_WEBSOCKET_H_
#define INC_LWS_WEBSOCKET_H_

#include <glib.h>

G_BEGIN_DECLS

#define LWS_WEBSOCKET_TYPE	lws_websocket_get_type()

G_DECLARE_FINAL_TYPE(LwsWebsocket, lws_websocket, LWS, WEBSOCKET, GObject)

/******************************
 * LWS websocket API function call error codes
 ******************************/
enum {
	LWS_OK,
	LWS_INVALID_ADDRESS,
	LWS_INIT_FATAL,
	LWS_NOT_CONNECTED,
	LWS_OPEN_FAIL,
	LWS_CLOSE_FAIL,
	LWS_SEND_FAIL,
};

enum {
	LWS_MESSAGE_TEXT,
	LWS_MESSAGE_BINARY
};

typedef void (*lws_onClose)(LwsWebsocket *ws, gpointer user);
typedef void (*lws_onError)(LwsWebsocket *ws, gpointer user, int code);
typedef void (*lws_onMessage)(LwsWebsocket *ws, gpointer user, int opcode, GBytes *msg);

gint lws_websocket_open(LwsWebsocket *ws, const gchar* address, lws_onMessage on_message, lws_onClose on_close, lws_onError on_error);

gint lws_websocket_close(LwsWebsocket *ws);

gint lws_websocket_send_text_message(LwsWebsocket *ws, const gchar *msg);

gint lws_websocket_send_binary_message(LwsWebsocket *ws, const guint8 *data, gsize len);

void lws_websocket_service(LwsWebsocket *ws);

gboolean lws_websocket_send_cache_empty(LwsWebsocket* self);

guint lws_websocket_send_cache_size(LwsWebsocket* self) ;

G_END_DECLS


#endif /* INC_LWS_WEBSOCKET_H_ */
