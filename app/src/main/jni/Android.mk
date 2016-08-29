# get current directory
LOCAL_PATH := $(call my-dir)

GSTREAMER_ROOT_ANDROID := $(TARGET_ARCH_ABI)

# libz.a
#
include $(CLEAR_VARS)
LOCAL_MODULE := z
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libz.a
include $(PREBUILT_STATIC_LIBRARY)

# libssl.a
#
include $(CLEAR_VARS)
LOCAL_MODULE := ssl
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libssl.a
include $(PREBUILT_STATIC_LIBRARY)

# libcrypto.a
#
include $(CLEAR_VARS)
LOCAL_MODULE := crypto
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libcrypto.a
include $(PREBUILT_STATIC_LIBRARY)

# libffi
#
include $(CLEAR_VARS)
LOCAL_MODULE := ffi
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libffi.a
include $(PREBUILT_STATIC_LIBRARY)

# libiconv
#
include $(CLEAR_VARS)
LOCAL_MODULE := iconv
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libiconv.a
include $(PREBUILT_STATIC_LIBRARY)

# libintl
#
include $(CLEAR_VARS)
LOCAL_MODULE := intl
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libintl.a
include $(PREBUILT_STATIC_LIBRARY)

# libgobject-2.0
#
include $(CLEAR_VARS)
LOCAL_MODULE := gobject-2.0
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libgobject-2.0.a
include $(PREBUILT_STATIC_LIBRARY)

# libglib-2.0
#
include $(CLEAR_VARS)
LOCAL_MODULE := glib-2.0
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libglib-2.0.a
include $(PREBUILT_STATIC_LIBRARY)

# libgmodule-2.0
#
include $(CLEAR_VARS)
LOCAL_MODULE := gmodule-2.0
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libgmodule-2.0.a
include $(PREBUILT_STATIC_LIBRARY)

# libgio-2.0
#
include $(CLEAR_VARS)
LOCAL_MODULE := gio-2.0
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libgio-2.0.a
include $(PREBUILT_STATIC_LIBRARY)

# libwebsockets.a
#
include $(CLEAR_VARS)
LOCAL_MODULE := websockets
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libwebsockets.a
include $(PREBUILT_STATIC_LIBRARY)

# libminiupnpc.a
#
include $(CLEAR_VARS)
LOCAL_MODULE := miniupnpc
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/lib/libminiupnpc.a
include $(PREBUILT_STATIC_LIBRARY)

# libcwicam.so
#
include $(CLEAR_VARS)
LOCAL_DISABLE_FATAL_LINKER_WARNINGS := true
LOCAL_MODULE := cwicam
LOCAL_SRC_FILES := as_wicam.c mini_upnpc.c lws_websocket.c cwicam.c
LOCAL_C_INCLUDES := $(LOCAL_PATH) $(TARGET_ARCH_ABI)/include $(TARGET_ARCH_ABI)/lib/glib-2.0/include $(TARGET_ARCH_ABI)/include/glib-2.0 $(TARGET_ARCH_ABI)/include/gstreamer-1.0 $(TARGET_ARCH_ABI)/lib/gstreamer-1.0/include
LOCAL_STATIC_LIBRARIES := websockets z ssl crypto miniupnpc
LOCAL_SHARED_LIBRARIES := gstreamer_android
LOCAL_LDLIBS := -llog -landroid
include $(BUILD_SHARED_LIBRARY)

ifndef GSTREAMER_ROOT
ifndef GSTREAMER_ROOT_ANDROID
$(error GSTREAMER_ROOT_ANDROID is not defined!)
endif
GSTREAMER_ROOT        := $(GSTREAMER_ROOT_ANDROID)
endif
GSTREAMER_NDK_BUILD_PATH  := $(GSTREAMER_ROOT)/share/gst-android/ndk-build/
GSTREAMER_PLUGINS         := coreelements app jpeg isomp4 x264 openh264
GSTREAMER_JAVA_SRC_DIR    := $(LOCAL_PATH)/../java/
include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer-1.0.mk