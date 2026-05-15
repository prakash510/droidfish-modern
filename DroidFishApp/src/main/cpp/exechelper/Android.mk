LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := exec_engine
LOCAL_SRC_FILES := exechelper.c
LOCAL_CFLAGS    := -fPIE -s
LOCAL_LDFLAGS   := -fPIE -pie -s -Wl,-z,max-page-size=16384
include $(BUILD_EXECUTABLE)
