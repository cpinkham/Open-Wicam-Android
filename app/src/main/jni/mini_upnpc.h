/*
 * mini_upnpc.h
 *
 *  Created on: July 20, 2016
 *      Author: yliu
 */

#ifndef MINI_UPNPC_H_
#define MINI_UPNPC_H_

#include <glib-object.h>
#include <glib.h>


G_BEGIN_DECLS

#define MINI_UPNPC_TYPE mini_upnpc_get_type()

G_DECLARE_FINAL_TYPE(MiniUpnpc, mini_upnpc, MINI, UPNPC, GObject)

enum {
	UPNPC_REMOVE_PORT_FAILED = -6,
	UPNPC_ADD_PORT_FAILED = -5,
	UPNPC_WAN_IP_NOT_FOUND = -4,
	UPNPC_IGD_NOT_FOUND = -3,
	UPNPC_NOT_DISCOVERED = -2,
	UPNPC_FAILED = -1,
	UPNPC_OK = 0,
};

typedef struct _port_map_item_t{
	int index;
	gchar wan_address[64];
	int wan_port;
	gchar lan_address[64];
	int lan_port;
	gchar protocol[4];
	gchar description[80];
	gboolean enabled;
	gchar remote_address[64];
	int duration;
}port_map_item_t;

int mini_upnpc_discover(MiniUpnpc *self);
int mini_upnpc_get_external_ip (MiniUpnpc *self, gchar *wan_addr);
int mini_upnpc_add_port(MiniUpnpc *self,
		const gchar *lan_address,
		int lan_port,
		int wan_port,
		int duration,
		gchar* wan_address);
int mini_upnpc_remove_port(MiniUpnpc *self, int wan_port);
int mini_upnpc_remove_port_range(MiniUpnpc *self, int wan_port_start, int wan_port_end);
int mini_upnpc_list_ports(MiniUpnpc *self, port_map_item_t* mappings, gsize max, gsize* size);


G_END_DECLS

#endif
