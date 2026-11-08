# LSPIN TV OS (Raspberry Pi 3)

Sistema Android avviabile per Raspberry Pi 3 con **LSPIN TV** come unico desktop:
base **LineageOS 17.1** (Android 10, build di KonstaKANG), animazione di avvio con il logo LS,
poi l'accensione stile TV dell'app. Niente launcher di LineageOS, niente "Sempre/Solo una volta".

```
app/            il launcher (Kotlin, nessuna libreria esterna)
os/
  patch-image.sh   STRADA VELOCE: mette LSPIN TV dentro l'immagine LineageOS già pronta (senza root)
  build-os.sh      STRADA COMPLETA: compila LineageOS 17.1 da sorgente con LSPIN TV incluso
  vendor/lspin/    integrazione Android (Android.mk, lspin.mk, permessi, bootanimation.zip)
  tools/           make_bootanimation.py (rigenera l'animazione), selftest.sh (collaudo dello script)
.github/workflows/build.yml   APK          .github/workflows/os.yml   APK + immagine
```

## Strada veloce (consigliata)
1. Scarica l'immagine **LineageOS 17.1 per Raspberry Pi 3** di KonstaKANG (konstakang.com, pagina Raspberry Pi 3).
2. Ottieni `app-release.apk`: GitHub > Actions > *Build APK* (come prima).
3. Su Linux (anche Kali sul Pi o WSL2, serve `e2fsprogs`):
   ```
   ./os/patch-image.sh lineage-17.1-XXXXXXXX-rpi3.zip app-release.apk LSPIN-TV-OS-rpi3.img
   ```
4. Scrivi `LSPIN-TV-OS-rpi3.img` su una microSD da 8 GB o più (Raspberry Pi Imager > *Usa personalizzata*,
   balenaEtcher, oppure `dd`) e avvia il Pi.

**Da GitHub, senza Linux:** Actions > *Build OS* > Run workflow e incolla il link diretto all'immagine
(per esempio caricata in una Release del tuo repo). Alla fine scarichi `LSPIN-TV-OS-rpi3` (`.img.xz`,
lo legge anche Raspberry Pi Imager).

Lo script non monta nulla: scrive nella partizione `system` con `debugfs`, poi verifica byte per byte
APK, animazione e permessi e controlla il filesystem con `e2fsck`.

## Strada completa
```
./os/build-os.sh app-release.apk
```
PC Linux x86_64, ~300 GB liberi, 16 GB di RAM, diverse ore. I pacchetti necessari sono elencati in cima allo script.
Risultato: `out/LSPIN-TV-OS-rpi3.img`.

## Cosa cambia rispetto all'APK
- È app di sistema (`/system/priv-app`): non si disinstalla e diventa l'unico desktop.
- Permessi di archiviazione già concessi (il file manager funziona subito).
- Nelle impostazioni sparisce *Sgancia dal desktop* (senza altro launcher lo schermo resterebbe vuoto).
- Boot animation `os/vendor/lspin/bootanimation/bootanimation.zip` (anteprima: `os/bootanimation_preview.png`).

## Note
- Android 10 senza Google Apps: le app si installano con `adb install` o F-Droid.
- La chiave TMDB di default è dentro `app/build.gradle.kts`: se il repo è pubblico, mettila nel secret `TMDB_KEY`.
- Tema: `Theme.PRO` in `app/src/main/java/it/lspin/tv/Theme.kt`.

## Stato dei test
- Collaudato: `os/tools/selftest.sh` (immagini finte con lo stesso layout di `mkimg.sh`, due varianti di radice, ri-esecuzione).
  La boot animation è stata generata e controllata, ma non provata su un Pi.
- **Non provato** su un'immagine di KonstaKANG vera, né su un Raspberry Pi; `build-os.sh` e `os.yml` non sono stati eseguiti.
  Il layout atteso (MBR: boot, system ext4, vendor, userdata) è quello di `device/brcm/rpi3/mkimg.sh`; se l'immagine è diversa lo script si ferma con un messaggio.
- Se qualcosa non va dopo la patch, rifai la microSD con l'immagine originale: l'originale non viene mai toccata.
