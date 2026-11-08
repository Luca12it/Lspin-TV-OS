#!/usr/bin/env bash
# LSPIN TV OS - strada completa: compila LineageOS 17.1 (Android 10) per Raspberry Pi 3
# con LSPIN TV integrato come desktop di sistema. Genera un'immagine .img da scrivere su microSD.
#
# Richiede: Ubuntu 20.04/22.04 x86_64 (anche in VM), ~300 GB liberi, 16 GB RAM (+ swap), molte ore.
# Pacchetti (una volta sola):
#   sudo apt install bc bison build-essential ccache curl flex g++-multilib gcc-multilib git gnupg gperf \
#     imagemagick lib32ncurses5-dev lib32readline-dev lib32z1-dev liblz4-tool libncurses5 libncurses5-dev \
#     libsdl1.2-dev libssl-dev libxml2 libxml2-utils lzop pngcrush rsync schedtool squashfs-tools xsltproc \
#     zip zlib1g-dev python-is-python3 kpartx dosfstools repo
#
# Uso: ./build-os.sh <LspinTV.apk> [cartella-sorgenti]      (default: ~/lspin-os)
#      SKIP_SYNC=1 ./build-os.sh ...   per rifare solo la compilazione
set -euo pipefail

die() { echo "ERRORE: $*" >&2; exit 1; }
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$(realpath "${1:?uso: $0 <LspinTV.apk> [cartella-sorgenti]}")"
SRC="${2:-$HOME/lspin-os}"
[ -f "$APK" ] || die "APK non trovato"
[ "$(uname -m)" = "x86_64" ] || die "serve un PC Linux x86_64 (non un Raspberry Pi)"
for t in git curl repo make bc kpartx mkfs.fat python3; do command -v "$t" >/dev/null || die "manca '$t' (vedi elenco pacchetti in cima allo script)"; done
mkdir -p "$SRC"
AVAIL=$(df -BG --output=avail "$SRC" | tail -n1 | tr -dc 0-9)
[ "$AVAIL" -ge 250 ] || die "servono almeno 250 GB liberi in $SRC (ora: ${AVAIL} GB)"
git config --global user.email >/dev/null 2>&1 || git config --global user.email "lspin@localhost"
git config --global user.name  >/dev/null 2>&1 || git config --global user.name  "LSPIN"

cd "$SRC"
if [ -z "${SKIP_SYNC:-}" ]; then
  echo "==> Scarico i sorgenti (decine di GB, ci vuole un po')"
  [ -d .repo ] || repo init -u https://github.com/LineageOS/android.git -b lineage-17.1 --depth=1
  curl --create-dirs -fL -o .repo/local_manifests/manifest_brcm_rpi.xml \
    https://raw.githubusercontent.com/lineage-rpi/android_local_manifest/lineage-17.1/manifest_brcm_rpi.xml
  repo sync -c -j8 --no-tags --no-clone-bundle --force-sync
fi

echo "==> Integro LSPIN TV"
mkdir -p vendor/lspin
cp -r "$HERE/vendor/lspin/." vendor/lspin/
mkdir -p vendor/lspin/prebuilt && cp "$APK" vendor/lspin/prebuilt/LspinTV.apk
HOOK='$(call inherit-product, vendor/lspin/lspin.mk)'
grep -qF "vendor/lspin/lspin.mk" device/brcm/rpi3/device.mk || printf '\n# LSPIN TV OS\n%s\n' "$HOOK" >> device/brcm/rpi3/device.mk

echo "==> Compilo (ore)"
set +u
export USE_CCACHE=1 CCACHE_EXEC="$(command -v ccache || true)"
# shellcheck disable=SC1091
source build/envsetup.sh
lunch lineage_rpi3-userdebug
mka bootimage systemimage vendorimage
OUTD="$ANDROID_PRODUCT_OUT"
[ -f "$OUTD/ramdisk.img" ] || mka ramdisk
[ -f "$OUTD/obj/KERNEL_OBJ/arch/arm/boot/zImage" ] || mka kernel
set -u
for f in system.img vendor.img ramdisk.img obj/KERNEL_OBJ/arch/arm/boot/zImage; do
  [ -f "$OUTD/$f" ] || die "manca $OUTD/$f"
done

echo "==> Creo l'immagine (serve sudo)"
cd device/brcm/rpi3
rm -f lineage-17.1-*-rpi3.img
sudo ANDROID_PRODUCT_OUT="$OUTD" ./mkimg.sh
IMG="$(ls -t lineage-17.1-*-rpi3.img | head -n1)"
mkdir -p "$HERE/../out"
mv "$IMG" "$HERE/../out/LSPIN-TV-OS-rpi3.img"
echo
echo "FATTO: $(realpath "$HERE/../out/LSPIN-TV-OS-rpi3.img")"
echo "Scrivila su microSD (>= 8 GB):  sudo dd if=out/LSPIN-TV-OS-rpi3.img of=/dev/sdX bs=4M status=progress conv=fsync"
