LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

ifeq ($(TARGET_ARCH),arm)
LOCAL_ARM_MODE := arm
endif
LOCAL_MODULE    := libptp
LOCAL_SRC_FILES := libptp.c usbhost.c

LOCAL_LDLIBS    := -lm -llog -ljnigraphics
LOCAL_STATIC_LIBRARIES := libjpeg cpufeatures
LOCAL_CFLAGS := -I${LOCAL_PATH}/libjpeg-turbo

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_SRC_FILES += yuv2rgb.S.neon
LOCAL_CFLAGS += -DBUILD_NEON
endif

include $(BUILD_SHARED_LIBRARY)

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
include ${LOCAL_PATH}/libjpeg-turbo/Android.mk
endif

$(call import-module,android/cpufeatures)
