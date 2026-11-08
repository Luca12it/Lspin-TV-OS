#!/usr/bin/env python3
"""Genera os/vendor/lspin/bootanimation/bootanimation.zip: logo LS bianco su nero (stessa geometria di
BootView.kt) con la scritta "LspinTV OS" sotto. part0 = comparsa del logo e della scritta (1 volta),
part1 = la scritta "respira" appena (loop fino a fine avvio)."""
import io, os, sys, zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H, FPS = 1280, 720, 30
BG = (0, 0, 0)             # nero puro, come l'immagine di riferimento
ACC = (47, 107, 255)       # Theme.BOOT_ACCENT0
TINT = (255, 255, 255)     # bianco puro
FONT = '/usr/share/fonts/opentype/inter/Inter-Medium.otf'
TITLE = 'LspinTV OS'
SS = 3                     # supersampling

def cubic(p0, p1, p2, p3, n=24):
    out = []
    for i in range(n + 1):
        t = i / n; u = 1 - t
        out.append((u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0],
                    u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1]))
    return out

# --- logo: preso dall'immagine di riferimento (logo_ref.gif), ingrandito e rifinito
# (si ingrandisce 16x, si leviga e si ritaglia a soglia: bordi netti anche a risoluzione alta)
LOGO_H = 224                                  # altezza del logo nel video (px)
def mark_mask():
    ref = Image.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'logo_ref.gif')).convert('L')
    box = ref.point(lambda v: 255 if v > 100 else 0).getbbox()
    ref = ref.crop((box[0]-2, box[1]-2, box[2]+2, box[3]+2))
    big = ref.resize((ref.width*16, ref.height*16), Image.BICUBIC).filter(ImageFilter.GaussianBlur(9))
    big = big.point(lambda v: 255 if v > 128 else 0)
    k = LOGO_H / big.height
    return big.resize((round(big.width*k), LOGO_H), Image.LANCZOS)

M = mark_mask()
bw, bh = M.size
Marr = np.asarray(M, dtype=np.float32) / 255.0

from PIL import ImageFont
bg = np.zeros((H, W, 3), np.float32); bg[:] = BG

# scritta: spaziatura larga, Inter Medium
fnt = ImageFont.truetype(FONT, 46)
TRACK = 9
def text_mask():
    widths = [fnt.getlength(ch) for ch in TITLE]
    tw = int(sum(widths) + TRACK * (len(TITLE) - 1)) + 8
    img = Image.new('L', (tw, 80), 0); d = ImageDraw.Draw(img)
    x = 4.0
    for ch, wd in zip(TITLE, widths):
        d.text((x, 10), ch, font=fnt, fill=255); x += wd + TRACK
    bb = img.getbbox()
    return np.asarray(img.crop(bb), dtype=np.float32) / 255.0
T = text_mask(); th_, tw_ = T.shape
GAP = 56
block_h = bh + GAP + th_
top = (H - block_h) // 2 - 6
cx, cy = (W - bw)//2, top
tx, ty = (W - tw_)//2, cy + bh + GAP

def frame(reveal, text_a=0.0, flicker=1.0):
    f = bg.copy()
    xs = (np.arange(bw, dtype=np.float32) / bw)
    wipe = np.clip((reveal*1.25 - xs) / 0.25, 0, 1)[None, :]
    m = Marr * wipe * flicker
    reg = f[cy:cy+bh, cx:cx+bw, :]
    f[cy:cy+bh, cx:cx+bw, :] = reg * (1 - m[:, :, None]) + np.array(TINT, np.float32)[None, None, :] * m[:, :, None]
    if text_a > 0:
        a = (T * text_a)[:, :, None]
        r = f[ty:ty+th_, tx:tx+tw_, :]
        f[ty:ty+th_, tx:tx+tw_, :] = r * (1 - a) + 255.0 * a
    img = Image.fromarray(np.clip(f, 0, 255).astype(np.uint8), 'RGB')
    return img.convert('P', palette=Image.ADAPTIVE, colors=64)

def ease(t): return 1 - (1 - t)**3

def build(out):
    import math
    frames0, frames1 = [], []
    n0 = 54
    for i in range(n0):
        t = i / (n0 - 1)
        rv = ease(min(1, t / 0.55))
        fl = 0.6 if (0.06 < t < 0.30 and i % 4 == 1) else 1.0      # sfarfallio iniziale
        ta = ease(max(0, (t - 0.55) / 0.40))                        # la scritta compare dopo il logo
        frames0.append(frame(rv, ta, fl))
    n1 = 60
    for i in range(n1):
        frames1.append(frame(1.0, 0.82 + 0.18 * math.sin(2*math.pi*i/n1)))
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_STORED) as z:
        z.writestr('desc.txt', f"{W} {H} {FPS}\np 1 0 part0\np 0 0 part1\n")
        for part, fr in (('part0', frames0), ('part1', frames1)):
            for i, im in enumerate(fr):
                b = io.BytesIO(); im.save(b, 'PNG', optimize=True)
                z.writestr(f"{part}/{i:05d}.png", b.getvalue())
    return frames0, frames1

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else 'bootanimation.zip'
    f0, f1 = build(out)
    f0[-1].convert('RGB').save(os.path.splitext(out)[0] + '_preview.png')
    print(out, os.path.getsize(out)//1024, 'KB')
