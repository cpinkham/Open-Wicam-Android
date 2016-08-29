/*
 * mini_upnpc.c
 *
 *  Created on: July 20, 2016
 *      Author: yliu
 */

#include "defs.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <glib-object.h>
#include <glib.h>
#include <miniupnpc/miniupnpc.h>
#include <miniupnpc/upnpcommands.h>
#include <miniupnpc/upnpdev.h>

#include "mini_upnpc.h"

struct _MiniUpnpc {
	GObject 			parent_instance;
	struct UPNPDev 		*upnp_dev;
};

G_DEFINE_TYPE (MiniUpnpc, mini_upnpc, G_TYPE_OBJECT)

static void mini_upnpc_init(MiniUpnpc *self) {

}

static void mini_upnpc_finalize(GObject *obj) {
	MiniUpnpc *self = MINI_UPNPC(obj);
	if (self->upnp_dev) freeUPNPDevlist(self->upnp_dev);
	self->upnp_dev = NULL;
}

static void mini_upnpc_class_init(MiniUpnpcClass *klass) {
	GObjectClass *object_class = G_OBJECT_CLASS(klass);
	object_class->finalize = mini_upnpc_finalize;
}

int mini_upnpc_discover(MiniUpnpc *self) {
	int error;
	self->upnp_dev = upnpDiscover(2000,    //timeout in milliseconds
									NULL,    //multicast address, default = "239.255.255.250"
		                            NULL,    //minissdpd socket, default = "/var/run/minissdpd.sock"
		                            0,       //source port, default = 1900
		                            0,       //0 = IPv4, 1 = IPv6
									2,
		                            &error); //error output
	if (!self->upnp_dev || error)
		return UPNPC_NOT_DISCOVERED;
	return UPNPC_OK;
}

int mini_upnpc_get_external_ip (MiniUpnpc *self, gchar *wan_addr) {
	struct UPNPUrls upnp_urls;
	struct IGDdatas upnp_data;
	char lan_addr[64];
	if (!self->upnp_dev) {
		return UPNPC_NOT_DISCOVERED;
	}
	int status = UPNP_GetValidIGD(self->upnp_dev, &upnp_urls, &upnp_data, lan_addr, sizeof(lan_addr));
	if (status != 1) {
		FreeUPNPUrls(&upnp_urls);
		return UPNPC_IGD_NOT_FOUND;
	}
	status = UPNP_GetExternalIPAddress(upnp_urls.controlURL, upnp_data.first.servicetype, wan_addr);
	if (status != 0) {
		FreeUPNPUrls(&upnp_urls);
		return UPNPC_WAN_IP_NOT_FOUND;
	}
	FreeUPNPUrls(&upnp_urls);
	return UPNPC_OK;
}

int mini_upnpc_add_port(MiniUpnpc *self,
		const gchar *lan_address,
		int lan_port,
		int wan_port,
		int duration,
		gchar* wan_address) {
	struct UPNPUrls upnp_urls;
	struct IGDdatas upnp_data;
	char lan_addr[64]; //maximum length of an ipv6 address string
	LOGS();
	if (!self->upnp_dev) {
		return UPNPC_NOT_DISCOVERED;
	}
	LOGS();
	int status = UPNP_GetValidIGD(self->upnp_dev, &upnp_urls, &upnp_data, lan_addr, sizeof(lan_addr));
	if (status != 1) {
		LOGS();
		FreeUPNPUrls(&upnp_urls);
		return UPNPC_IGD_NOT_FOUND;
	}
	status = UPNP_GetExternalIPAddress(upnp_urls.controlURL, upnp_data.first.servicetype, wan_address);
	if (status != 0) {
		LOGS();
		FreeUPNPUrls(&upnp_urls);
		return UPNPC_WAN_IP_NOT_FOUND;
	}
	gchar lan_port_str[64];
	gchar wan_port_str[64];
	gchar duration_str[64];
	sprintf(lan_port_str, "%d", lan_port);
	sprintf(wan_port_str, "%d", wan_port);
	sprintf(duration_str, "%d", duration);
	LOGS();
	int error = UPNP_AddPortMapping (upnp_urls.controlURL,
						upnp_data.first.servicetype,
						wan_port_str,
						lan_port_str,
						lan_address,
						"Wicam UPNPC Port Mapping",
						"TCP",
						NULL,
						duration != 0? duration_str:NULL);
	FreeUPNPUrls(&upnp_urls);
	if (error != UPNPCOMMAND_SUCCESS) {
		LOGS();
		return UPNPC_ADD_PORT_FAILED;
	}
	LOGS();
	return UPNPC_OK;
}

int mini_upnpc_remove_port(MiniUpnpc *self, int wan_port) {
	struct UPNPUrls upnp_urls;
	struct IGDdatas upnp_data;
	char lan_address[64]; //maximum length of an ipv6 address string
	if (!self->upnp_dev) {
		return UPNPC_NOT_DISCOVERED;
	}
	int status = UPNP_GetValidIGD(self->upnp_dev, &upnp_urls, &upnp_data, lan_address, sizeof(lan_address));
	if (status != 1) {
		FreeUPNPUrls(&upnp_urls);
		return UPNPC_IGD_NOT_FOUND;
	}
	gchar wan_port_str[64];
	sprintf(wan_port_str, "%d", wan_port);
	int error = UPNP_DeletePortMapping(upnp_urls.controlURL,
			upnp_data.first.servicetype,
			wan_port_str, "TCP", NULL);
	FreeUPNPUrls(&upnp_urls);
	if (error) return UPNPC_REMOVE_PORT_FAILED;
	return UPNPC_OK;
}

int mini_upnpc_remove_port_range(MiniUpnpc *self, int wan_port_start, int wan_port_end) {
	struct UPNPUrls upnp_urls;
	struct IGDdatas upnp_data;
	char lan_address[64]; //maximum length of an ipv6 address string
	if (!self->upnp_dev) {
		return UPNPC_NOT_DISCOVERED;
	}
	int status = UPNP_GetValidIGD(self->upnp_dev, &upnp_urls, &upnp_data, lan_address, sizeof(lan_address));
	if (status != 1) {
		FreeUPNPUrls(&upnp_urls);
		return UPNPC_IGD_NOT_FOUND;
	}
	gchar wan_port_start_str[64];
	gchar wan_port_end_str[64];
	sprintf(wan_port_start_str, "%d", wan_port_start);
	sprintf(wan_port_end_str, "%d", wan_port_end);
	int error = UPNP_DeletePortMappingRange(upnp_urls.controlURL,
			upnp_data.first.servicetype,
			wan_port_start_str, wan_port_end_str, "TCP", NULL);
	FreeUPNPUrls(&upnp_urls);
	if (error) return UPNPC_REMOVE_PORT_FAILED;
	return UPNPC_OK;
}

int mini_upnpc_list_ports(MiniUpnpc *self, port_map_item_t* mappings, gsize max, gsize* size) {
	struct UPNPUrls upnp_urls;
	struct IGDdatas upnp_data;
	char lan_addr[64]; //maximum length of an ipv6 address string
	char wan_address[64];
	if (!self->upnp_dev) {
		return UPNPC_NOT_DISCOVERED;
	}
	int status = UPNP_GetValidIGD(self->upnp_dev, &upnp_urls, &upnp_data, lan_addr, sizeof(lan_addr));
	if (status != 1) {
		FreeUPNPUrls(&upnp_urls);
		return UPNPC_IGD_NOT_FOUND;
	}
	status = UPNP_GetExternalIPAddress(upnp_urls.controlURL, upnp_data.first.servicetype, wan_address);
	if (status != 0) {
		FreeUPNPUrls(&upnp_urls);
		return UPNPC_WAN_IP_NOT_FOUND;
	}
	int error = 0;
	int i = 0;
	char index[8];
	char wan_port[8];
	char lan_port[8];
	char enabled[8];
	char duration[8];
	while(!error && i < max) {
		wan_port[0] = 0;
		lan_port[0] = 0;
		enabled[0] = 0;
		duration[0] = 0;
		sprintf(index, "%d", i);
		error = UPNP_GetGenericPortMappingEntry(
				upnp_urls.controlURL,
				upnp_data.first.servicetype,
				index,
				wan_port,
				mappings[i].lan_address,
				wan_port,
				mappings[i].protocol,
				mappings[i].description,
				enabled,
				mappings[i].remote_address,
				duration);
		if (error) break;
		if (strlen(wan_port)) mappings[i].wan_port = atoi(wan_port);
		else mappings[i].wan_port = -1;
		if (strlen(lan_port)) mappings[i].lan_port = atoi (lan_port);
		else mappings[i].lan_port = -1;
		if (strlen(duration)) mappings[i].duration = atoi(duration);
		else mappings[i].duration = -1;
		strcpy(mappings[i].wan_address, wan_address);
		i++;
	}
	*size = i;
	FreeUPNPUrls(&upnp_urls);
	return UPNPC_OK;
}
