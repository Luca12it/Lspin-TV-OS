LOCAL_PATH := $(call my-dir)

# LSPIN TV come desktop di sistema (sostituisce Trebuchet/Launcher3)
include $(CLEAR_VARS)
LOCAL_MODULE := LspinTV
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := prebuilt/LspinTV.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_PRIVILEGED_MODULE := true
LOCAL_DEX_PREOPT := false
LOCAL_OVERRIDES_PACKAGES := Home Launcher2 Launcher3 Launcher3Go Launcher3QuickStep Trebuchet TrebuchetGo TrebuchetQuickStep
include $(BUILD_PREBUILT)
