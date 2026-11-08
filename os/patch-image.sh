#!/usr/bin/env bash
# LSPIN TV OS - strada veloce (niente compilazione di Android, niente root).
# Prende l'immagine LineageOS 17.1 per Raspberry Pi 3 e ci scrive dentro:
#   - LSPIN TV come unico desktop di sistema (rimuove Trebuchet/Launcher3)
#   - animazione di avvio con il logo LS
#   - permessi di archiviazione già concessi
#   - nessun pulsante di navigazione su schermo (qemu.hw.mainkeys=1 in build.prop)
# Uso:  ./patch-image.sh <immagine .img|.zip|.xz|.gz> <LspinTV.apk> [uscita.img]
# Richiede: bash, e2fsprogs (debugfs, e2fsck), coreutils, unzip (solo per .zip)
set -euo pipefail
PATH="$PATH:/sbin:/usr/sbin"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$HERE/vendor/lspin"
IN="${1:-}"; APK="${2:-}"; OUT="${3:-LSPIN-TV-OS-rpi3.img}"

die() { echo "ERRORE: $*" >&2; exit 1; }
[ -f "$IN" ]  || die "uso: $0 <immagine> <LspinTV.apk> [uscita.img]  (immagine non trovata)"
[ -f "$APK" ] || die "APK non trovato: $APK"
for t in debugfs e2fsck od cmp awk; do command -v "$t" >/dev/null || die "manca '$t' (installa e2fsprogs)"; done
[ -f "$VENDOR/bootanimation/bootanimation.zip" ] || die "manca bootanimation.zip (python3 os/tools/make_bootanimation.py)"

WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT

# ---------- 1. immagine di partenza ----------
echo "==> Preparo l'immagine"
case "$IN" in
  *.zip) command -v unzip >/dev/null || die "manca unzip"
         unzip -o -q "$IN" -d "$WORK/x"; SRC="$(find "$WORK/x" -name '*.img' | head -n1)"
         [ -n "$SRC" ] || die "nessun .img dentro lo zip" ;;
  *.xz)  xz -dc "$IN" > "$WORK/in.img"; SRC="$WORK/in.img" ;;
  *.gz)  gzip -dc "$IN" > "$WORK/in.img"; SRC="$WORK/in.img" ;;
  *)     SRC="$IN" ;;
esac
rm -f "$OUT"
cp --sparse=always "$SRC" "$OUT"
[ "$SRC" = "$IN" ] || rm -rf "$WORK/x" "$WORK/in.img"

# ---------- 2. tabella delle partizioni (MBR) ----------
u32() { od -An -tu4 -j"$2" -N4 "$1" | tr -d ' '; }
[ "$(od -An -tx1 -j510 -N2 "$OUT" | tr -d ' ')" = "55aa" ] || die "tabella MBR non valida: non è un'immagine per Raspberry Pi"
P2_START=$(u32 "$OUT" 470); P2_SECT=$(u32 "$OUT" 474)
OFF=$((P2_START * 512))
echo "    partizione system: offset $OFF, $((P2_SECT * 512 / 1048576)) MB"
[ "$(od -An -tx1 -j$((OFF + 1080)) -N2 "$OUT" | tr -d ' ')" = "53ef" ] || \
  die "la partizione 2 non è ext4: questa immagine non ha il layout atteso (boot/system/vendor/userdata)"

FS="$OUT?offset=$OFF"
dbr() { debugfs -R "$1" "$FS" 2>/dev/null; }          # comando in sola lettura

# nell'immagine la radice è /system oppure contiene direttamente app, priv-app, ...
if   [ -n "$(dbr 'ls -p /priv-app')" ];        then P=""
elif [ -n "$(dbr 'ls -p /system/priv-app')" ]; then P="/system"
else die "non trovo priv-app nella partizione system"; fi
echo "    radice di /system nell'immagine: ${P:-(direttamente la radice)}"

# ---------- 3. spazio libero ----------
STATS="$(dbr stats)"
BS=$(awk -F: '/^Block size/{gsub(/ /,"",$2); print $2; exit}' <<<"$STATS")
FB=$(awk -F: '/^Free blocks count/{gsub(/ /,"",$2); print $2; exit}' <<<"$STATS")
if [ -n "${BS:-}" ] && [ -n "${FB:-}" ]; then
  FREE_MB=$((BS * FB / 1048576)); echo "    spazio libero in system: ${FREE_MB} MB"
  [ "$FREE_MB" -ge 20 ] || die "spazio libero insufficiente in system (${FREE_MB} MB)"
fi

# ---------- 4. comandi debugfs ----------
cp "$APK" "$WORK/LspinTV.apk"
cp "$VENDOR/bootanimation/bootanimation.zip" "$WORK/bootanimation.zip"
cp "$VENDOR/permissions/default-permissions-lspin.xml" "$WORK/default-permissions-lspin.xml"
CMDS="$WORK/cmds"; : > "$CMDS"
LABEL="u:object_r:system_file:s0"     # (l'immagine parte con SELinux permissive, ma etichettiamo comunque)

has() { [ -n "$(dbr "ls -p $1")" ]; }
rmtree() {                                           # cancella ricorsivamente (figli prima)
  local d="$1" line ino mode uid gid name size
  while IFS= read -r line; do
    IFS='/' read -r _ ino mode uid gid name size _ <<<"$line"
    case "$name" in ""|.|..) continue;; esac
    if [[ "$mode" == 04* ]]; then rmtree "$d/$name"; echo "rmdir $d/$name" >> "$CMDS"
    else echo "rm $d/$name" >> "$CMDS"; fi
  done < <(dbr "ls -p $d")
}
putfile() {                                          # putfile <locale> <dest>
  echo "rm $2" >> "$CMDS"                            # se esiste già (errore ignorato)
  { echo "write $1 $2"; echo "sif $2 mode 0100644"; echo "sif $2 uid 0"; echo "sif $2 gid 0"
    echo "ea_set $2 security.selinux $LABEL"; } >> "$CMDS"
}
mkd() {                                              # mkd <dir> [forza]  (se manca)
  [ -n "${2:-}" ] || ! has "$1" || return 0
  { echo "mkdir $1"; echo "sif $1 mode 040755"; echo "sif $1 uid 0"; echo "sif $1 gid 0"
    echo "ea_set $1 security.selinux $LABEL"; } >> "$CMDS"
}

echo "==> Rimuovo il launcher di LineageOS"
REMOVED=0
for base in "$P/priv-app" "$P/app" "$P/product/priv-app" "$P/product/app" "$P/system_ext/priv-app"; do
  has "$base" || continue
  while IFS= read -r line; do
    IFS='/' read -r _ ino mode uid gid name size _ <<<"$line"
    case "$name" in Trebuchet*|Launcher3*|Launcher2*)
      echo "    - $base/$name"; rmtree "$base/$name"; echo "rmdir $base/$name" >> "$CMDS"; REMOVED=$((REMOVED+1));;
    esac
  done < <(dbr "ls -p $base")
done
[ "$REMOVED" -gt 0 ] || echo "    (nessun launcher trovato: forse già rimosso)"

echo "==> Installo LSPIN TV, animazione e permessi"
if has "$P/priv-app/LspinTV"; then rmtree "$P/priv-app/LspinTV"; echo "rmdir $P/priv-app/LspinTV" >> "$CMDS"; fi
mkd "$P/priv-app/LspinTV" forza
putfile "$WORK/LspinTV.apk" "$P/priv-app/LspinTV/LspinTV.apk"
has "$P/media" || die "manca $P/media nell'immagine"
putfile "$WORK/bootanimation.zip" "$P/media/bootanimation.zip"
mkd "$P/etc/default-permissions"
putfile "$WORK/default-permissions-lspin.xml" "$P/etc/default-permissions/default-permissions-lspin.xml"

# nessun pulsante di navigazione (indietro/home/recenti) su schermo
echo "==> Disattivo i pulsanti di navigazione su schermo"
BP="$P/build.prop"
if dbr "dump $BP $WORK/build.prop" >/dev/null && [ -s "$WORK/build.prop" ]; then
  grep -v '^qemu.hw.mainkeys=' "$WORK/build.prop" > "$WORK/build.prop.new" || true
  [ -z "$(tail -c1 "$WORK/build.prop.new")" ] || echo >> "$WORK/build.prop.new"
  echo "qemu.hw.mainkeys=1" >> "$WORK/build.prop.new"
  putfile "$WORK/build.prop.new" "$BP"
else
  die "non trovo $BP nell'immagine"
fi

debugfs -w -f "$CMDS" "$FS" > "$WORK/debugfs.log" 2>&1 || true

# ---------- 5. verifica ----------
echo "==> Verifica"
fail=0
dbr "dump $P/priv-app/LspinTV/LspinTV.apk $WORK/check.apk" >/dev/null || true
cmp -s "$WORK/check.apk" "$APK" && echo "    APK scritto correttamente" || { echo "    APK NON scritto"; fail=1; }
dbr "dump $P/media/bootanimation.zip $WORK/check.zip" >/dev/null || true
cmp -s "$WORK/check.zip" "$WORK/bootanimation.zip" && echo "    animazione di avvio scritta" || { echo "    animazione NON scritta"; fail=1; }
dbr "dump $P/etc/default-permissions/default-permissions-lspin.xml $WORK/check.xml" >/dev/null || true
cmp -s "$WORK/check.xml" "$WORK/default-permissions-lspin.xml" && echo "    permessi predefiniti scritti" || { echo "    permessi NON scritti"; fail=1; }
dbr "dump $BP $WORK/check.prop" >/dev/null || true
grep -qx 'qemu.hw.mainkeys=1' "$WORK/check.prop" && echo "    pulsanti di navigazione disattivati" || { echo "    pulsanti di navigazione NON disattivati"; fail=1; }
for base in "$P/priv-app" "$P/app" "$P/product/priv-app"; do
  if dbr "ls -p $base" | awk -F/ '$6 ~ /^(Trebuchet|Launcher3)/{f=1} END{exit !f}'; then echo "    launcher ancora presente in $base"; fail=1; fi
done
if e2fsck -fn "$FS" > "$WORK/fsck.log" 2>&1; then echo "    controllo filesystem: OK"
else
  if grep -q "offset" "$WORK/fsck.log" && ! grep -q "Pass 1" "$WORK/fsck.log"; then echo "    (e2fsck non supporta l'offset su questo sistema: controllo saltato)"
  else echo "    controllo filesystem: ERRORI"; tail -n 15 "$WORK/fsck.log"; fail=1; fi
fi
[ "$fail" -eq 0 ] || { echo "Qualcosa non è andato a buon fine. Log debugfs: $WORK/debugfs.log"; trap - EXIT; exit 1; }

echo
echo "FATTO: $OUT ($(du -h --apparent-size "$OUT" | cut -f1))"
echo "Scrivi l'immagine su una microSD da almeno 8 GB (meglio 16 GB):"
echo "  Linux:   sudo dd if=\"$OUT\" of=/dev/sdX bs=4M status=progress conv=fsync"
echo "  Windows: Raspberry Pi Imager > Usa personalizzata  (oppure balenaEtcher)"
