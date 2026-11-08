# LSPIN TV OS - da includere nel device.mk del Raspberry Pi 3 (lo fa build-os.sh)
LSPIN_PATH := vendor/lspin

PRODUCT_PACKAGES += LspinTV

# permessi di archiviazione già concessi alla prima accensione
PRODUCT_COPY_FILES += \
    $(LSPIN_PATH)/permissions/default-permissions-lspin.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/default-permissions/default-permissions-lspin.xml

# animazione di avvio con il logo LS (sostituisce quella di LineageOS)
TARGET_BOOTANIMATION := $(LSPIN_PATH)/bootanimation/bootanimation.zip

PRODUCT_PROPERTY_OVERRIDES += ro.lspin.os=1

# nessun pulsante di navigazione su schermo (indietro/home/recenti): mai visibili
PRODUCT_PROPERTY_OVERRIDES += qemu.hw.mainkeys=1
