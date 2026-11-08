#!/usr/bin/env bash
# Collaudo di patch-image.sh su un'immagine finta con lo stesso layout di mkimg.sh
# (MBR: boot FAT, system ext4, vendor, userdata). Non serve l'immagine vera.
set -euo pipefail
PATH="$PATH:/sbin:/usr/sbin"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
W="$(mktemp -d)"; [ -n "${KEEP:-}" ] || trap 'rm -rf "$W"' EXIT
command -v mke2fs >/dev/null && command -v python3 >/dev/null || { echo "servono mke2fs e python3"; exit 1; }

mkimg() {   # mkimg <nome> <prefisso: "" o "system">
  local name="$1" pre="$2" d="$W/tree-$1"
  mkdir -p "$d/${pre:+$pre/}"{priv-app/TrebuchetQuickStep/oat/arm,priv-app/Settings,app/Launcher3Go,media,etc/permissions}
  echo apk > "$d/${pre:+$pre/}priv-app/TrebuchetQuickStep/TrebuchetQuickStep.apk"
  head -c 300000 /dev/urandom > "$d/${pre:+$pre/}priv-app/TrebuchetQuickStep/oat/arm/x.odex"
  echo apk > "$d/${pre:+$pre/}priv-app/Settings/Settings.apk"
  echo apk > "$d/${pre:+$pre/}app/Launcher3Go/Launcher3Go.apk"
  echo old > "$d/${pre:+$pre/}media/bootanimation.zip"
  printf 'ro.build.type=userdebug\nqemu.hw.mainkeys=0' > "$d/${pre:+$pre/}build.prop"
  python3 - "$W/$name.img" <<'PY'
import struct, sys
img = sys.argv[1]; MB = 2048
parts = [(0x0c, 2048, 16*MB), (0x83, 2048+16*MB, 64*MB), (0x83, 2048+80*MB, 8*MB), (0x83, 2048+88*MB, 8*MB)]
mbr = bytearray(512)
for i, (t, s, n) in enumerate(parts):
    mbr[446+16*i+4] = t; mbr[446+16*i+8:446+16*i+16] = struct.pack('<II', s, n)
mbr[510:512] = b'\x55\xaa'
with open(img, 'wb') as f:
    f.truncate((2048 + 96*MB) * 512); f.seek(0); f.write(mbr)
PY
  mke2fs -q -t ext4 -L system -d "$d" -E offset=$((2048*512+16*1048576)) "$W/$name.img" 65536k
  head -c 1000000 /dev/urandom > "$W/fake.apk"
}

for variant in "plain:" "sar:system"; do
  n="${variant%%:*}"; pre="${variant#*:}"
  echo "######## layout: $n"
  mkimg "$n" "$pre"
  "$HERE/patch-image.sh" "$W/$n.img" "$W/fake.apk" "$W/out-$n.img"
  OFF=$((2048*512+16*1048576)); R="${pre:+/$pre}"
  chk() { debugfs -R "ls -p $1" "$W/out-$n.img?offset=$OFF" 2>/dev/null; }
  chk "$R/priv-app" | grep -q "/Settings/"   || { echo "FALLITO: Settings è sparito"; exit 1; }
  chk "$R/priv-app" | grep -q "/LspinTV/"    || { echo "FALLITO: LspinTV assente"; exit 1; }
  chk "$R/priv-app" | grep -q "Trebuchet"    && { echo "FALLITO: Trebuchet presente"; exit 1; }
  chk "$R/app"      | grep -q "Launcher3"    && { echo "FALLITO: Launcher3Go presente"; exit 1; }
  debugfs -R "cat $R/build.prop" "$W/out-$n.img?offset=$OFF" 2>/dev/null | grep -qx "qemu.hw.mainkeys=1" || { echo "FALLITO: mainkeys non impostato"; exit 1; }
  debugfs -R "cat $R/build.prop" "$W/out-$n.img?offset=$OFF" 2>/dev/null | grep -qx "ro.build.type=userdebug" || { echo "FALLITO: build.prop alterato"; exit 1; }
  [ "$(debugfs -R "cat $R/build.prop" "$W/out-$n.img?offset=$OFF" 2>/dev/null | grep -c mainkeys)" = 1 ] || { echo "FALLITO: mainkeys duplicato"; exit 1; }
  echo "######## $n: OK"
  # seconda esecuzione sullo stesso risultato (idempotenza)
  "$HERE/patch-image.sh" "$W/out-$n.img" "$W/fake.apk" "$W/out2-$n.img" >/dev/null || { echo "FALLITO: ri-esecuzione"; exit 1; }
  echo "######## $n: ri-esecuzione OK"
done
echo "TUTTO OK"
