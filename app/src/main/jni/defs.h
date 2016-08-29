/*
 * defs.h
 *
 *  Created on: Jun 11, 2016
 *      Author: yliu
 */

#ifndef INC_DEFS_H_
#define INC_DEFS_H_

#undef G_LOG_DOMAIN
#define G_LOG_DOMAIN	"Native-WiCam"

// Android
#ifdef __ANDROID__
#include <android/log.h>

#define  LOG_TAG    "Native-WiCam"

#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGS()		LOGD("%s %d", __FUNCTION__, __LINE__)
#else
#define  LOGE(...)  g_error(__VA_ARGS__)
#define  LOGW(...)  g_warning(__VA_ARGS__)
#define  LOGD(...)  g_debug(__VA_ARGS__)
#define  LOGI(...)  g_info(__VA_ARGS__)
#endif

#endif /* INC_DEFS_H_ */
