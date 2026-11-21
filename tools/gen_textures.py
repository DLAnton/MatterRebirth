"""Генератор текстур Matter Rebirth (своя графика).

Запуск: python tools/gen_textures.py  (нужен Pillow). Пишет PNG в src/main/resources/assets/matterrebirth/textures.
Предметы 16x16, как у IC2 2.8.
Здесь — предметы (кроме значков синхронизатора). Остальное — см. tools/README.md:
конструкционный блок — replicator/build_replicator.py (порт машины с погашенными контактами, 20.11),
значки синхронизатора — sync/gen_sync_icons.py (перерисованы 14.11),
репликатор — replicator/build_replicator.py, слои клона — clone/gen_layers11.py.
"""
import math
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'matterrebirth', 'textures')


# ---------- шприц и нейросинхронизатор ----------
OUTL = (0x22, 0x24, 0x26)
METAL, METAL_D, METAL_L = (0xa6, 0xae, 0xb0), (0x74, 0x7c, 0x7e), (0xdc, 0xe2, 0xe3)
GLASS, GLASS_L = (0xb8, 0xd4, 0xdc), (0xe8, 0xf6, 0xfa)
BLOOD, BLOOD_L = (0xb0, 0x14, 0x20), (0xe8, 0x48, 0x50)


def icon():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def syringe(level):
    """Вакуумный шприц для забора крови (референс Антона, своя прорисовка): прозрачная пробирка с круглым дном,
    белая этикетка с красной полосой, упор, колпачок с красной пробкой, цветная втулка и игла. level — доля крови."""
    im = icon()
    ox, oy = 0.6, 15.4
    LABEL, STRIPE, FLANGE, CAP, STOP, HUB = (0xf2, 0xf2, 0xee), (0xd8, 0x30, 0x30), (0xd4, 0xdc, 0xde), \
        (0xc8, 0xd2, 0xd4), (0xc8, 0x22, 0x2a), (0x3a, 0xb0, 0x4a)
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - ox, y + .5 - oy
            u = (dx - dy) / math.sqrt(2)
            v = (dx + dy) / math.sqrt(2)
            c = None
            tube = 1.0 < u < 10.6 and abs(v) < 2.0 and (u > 3 or v * v + (u - 3) ** 2 < 4)
            if tube:
                rim = abs(v) > 1.35 or (u < 3 and v * v + (u - 3) ** 2 > 2.0)
                if rim:
                    c = OUTL
                elif 5.2 < u < 8.8:
                    c = STRIPE if 7.2 < u < 7.9 else LABEL
                else:
                    c = (BLOOD_L if v < 0 else BLOOD) if level else (GLASS_L if v < 0 else GLASS)
            elif 10.6 <= u < 11.6 and abs(v) < 2.9:
                c = METAL_D if abs(v) > 2.1 else FLANGE                  # упор
            elif 11.6 <= u < 14.6 and abs(v) < 1.6:
                c = OUTL if abs(v) > 1.0 else (STOP if v > -0.3 else CAP)  # колпачок с пробкой
            elif 14.6 <= u < 15.6 and abs(v) < 1.0:
                c = HUB                                                  # втулка иглы
            elif 15.6 <= u < 20.5 and abs(v) < .55:
                c = METAL_L if u > 17 else METAL                         # игла
            if c:
                im.putpixel((x, y), c + (255,))
    return im


# ---------- резонирующая смесь ----------
MIX_SHAPE = [   # привычная форма кучки пыли, как у пылей IC2
    '................',
    '................',
    '................',
    '.......##.......',
    '......####......',
    '.....######.....',
    '....########....',
    '...##########...',
    '..############..',
    '..############..',
    '..############..',
    '...##########...',
    '....########....',
    '......####......',
    '................',
    '................']
TEAL = (0x3a, 0xc4, 0xa8); PURP = (0x7a, 0x3c, 0xb0); RED = (0xe8, 0x2a, 0x34); OUT = (0x1e, 0x0c, 0x2a)


def lerp(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def mixture(seed=0.0, swirl=1.0, vein=0.16):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    m = {(x, y) for y in range(16) for x in range(16) if MIX_SHAPE[y][x] == '#'}
    for (x, y) in m:
        edge = any((x + a, y + b) not in m for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            im.putpixel((x, y), OUT + (255,)); continue
        # мраморный узор
        t = math.sin(0.9 * x + 0.55 * y + swirl * 1.6 * math.sin(0.7 * y + seed) + seed)
        base = lerp(TEAL, PURP, (t + 1) / 2 * 0.75)
        if abs(t - 0.35) < vein: base = lerp(base, RED, 0.85)
        # освещение: свет слева сверху, кучка как полусфера
        cx, cy = 7.5, 10.5
        dx, dy = (x - cx) / 6.5, (y - cy) / 8.0
        nz = max(0.0, 1 - dx * dx - dy * dy) ** 0.5
        light = (-dx * 0.55 - dy * 0.75 + nz * 0.6)
        k = 0.55 + 0.6 * light
        c = tuple(min(255, max(0, v * k)) for v in base)
        # блик
        if (x - 5.5) ** 2 + (y - 6.5) ** 2 < 1.6: c = lerp(c, (255, 255, 255), 0.75)
        elif (x - 6) ** 2 + (y - 7) ** 2 < 4: c = lerp(c, (255, 255, 255), 0.3)
        # нижняя тень
        if y >= 11: c = tuple(v * 0.75 for v in c)
        im.putpixel((x, y), tuple(int(v) for v in c) + (255,))
    return im


# ---------- кристалл, якорь, корпус ----------
def art(rows, pal):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        assert len(r) == 16, (y, r, len(r))
        for x, ch in enumerate(r):
            if ch != '.':
                im.putpixel((x, y), pal[ch] + (255,))
    return im


P = {'a': (0x2e, 0x0a, 0x44), 'd': (0x55, 0x16, 0x78), 'm': (0x7e, 0x26, 0xa6), 'l': (0xa8, 0x4c, 0xd0),
     'h': (0xd2, 0x90, 0xee), 'w': (0xf6, 0xe8, 0xff), 'c': (0x6c, 0xec, 0xe8), 'C': (0x2a, 0x9c, 0xa4), 'k': (0xb8, 0xff, 0xfa),
     'g': (0x8c, 0x94, 0x96), 'G': (0x5c, 0x63, 0x65), 's': (0xc6, 0xcc, 0xcd), 'S': (0xe6, 0xea, 0xea), 'n': (0x3a, 0x40, 0x42),
     'i': (0x4c, 0x52, 0x5c), 'I': (0x6e, 0x76, 0x82), 'j': (0x34, 0x38, 0x40), 'J': (0x90, 0x98, 0xa4)}
MATRIX = [
    '..jjjjjjjjjjjj..',
    '.jJJJJJJJJJJJIj.',
    'jJIIIIahlaIIIIij',
    'jJIjjjamdajjjIij',
    'jJIjCCCccCCCjIij',
    'jJIjCiiCCiiCjIij',
    'jahhCiCkkCiCmmaj',
    'jalmcCkwwkCcmdaj',
    'jalmcCkwwkCcmdaj',
    'jaddCiCkkCiCddaj',
    'jJIjCiiCCiiCjIij',
    'jJIjCCCccCCCjIij',
    'jJIjjjamdajjjIij',
    'jJIIIIaddaIIIIij',
    '.jiiiiiiiiiiiiij',
    '..jjjjjjjjjjjj..']
CORE = [
    '................',
    '................',
    '.....aaaaaa.....',
    '....admmmmda....',
    '...admRrrCmda...',
    '..admRkkrCCmda..',
    '..amRkwkcCrmda..',
    '..amrkkccCrmda..',
    '..amrcccCrrmda..',
    '..amCcCCrrmdda..',
    '..admCrrrrmdda..',
    '...admmmmddda...',
    '....adddddaa....',
    '.....aaaaaa.....',
    '................',
    '................']
MIX_BASE = (78, 166, 132)   # смешение 4:4:1 (126,138,112), сдвинутое к цвету пыли ока Эндера (66,176,131) на 75%: эндер-тон с лёгкой тёплой примесью


def ramp(L, base=MIX_BASE):
    """Оттенок смеси по яркости L (0..1): от тёмного к светлому, тон один и тот же."""
    dark = tuple(c * 0.28 for c in base); light = tuple(c + (255 - c) * 0.8 for c in base)
    if L < .5:
        t = L / .5; return tuple(int(dark[i] + (base[i] - dark[i]) * t) for i in range(3))
    t = (L - .5) / .5; return tuple(int(base[i] + (light[i] - base[i]) * t) for i in range(3))


def recolor(im, box=None, spread=1.0):
    im = im.copy(); px = im.load()
    pts = [(x, y) for y in range(im.height) for x in range(im.width) if px[x, y][3] and (box is None or (box[0] <= x <= box[2] and box[1] <= y <= box[3]))]
    lums = [(0.2126 * px[p][0] + 0.7152 * px[p][1] + 0.0722 * px[p][2]) / 255 for p in pts]
    lo, hi = min(lums), max(lums)
    for p, L in zip(pts, lums):
        n = (L - lo) / (hi - lo or 1)
        px[p] = ramp(0.5 + (n - 0.5) * spread) + (255,)
    return im


def core_orig():
    return art(CORE, dict(P, R=(0xe8, 0x60, 0x68), r=(0xb8, 0x22, 0x2e)))


def core_icon():
    """Ядро: середина между первой версией (фиолетовая оболочка, бирюзово-красное нутро) и эндер-тоном смеси."""
    return Image.blend(core_orig(), recolor(core_orig(), spread=1.0), 0.5)


def anchor_upgrade():
    """Межпространственный якорь в стиле улучшений IC2: серая пластина 12x12 с фаской и знак 10x10."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.putpixel
    hi, fill, sh, sh2 = (170, 170, 170, 255), (150, 150, 150, 255), (112, 112, 112, 255), (138, 136, 136, 255)
    for x in range(2, 14):
        for y in range(2, 14):
            px((x, y), fill)
    for i in range(2, 13):
        px((i, 2), hi); px((2, i), hi)
        px((13, i + 1), sh); px((i + 1, 13), sh)
    px((13, 2), sh2); px((2, 13), sh2)
    pal = {'P': (85, 22, 120), 'p': (126, 38, 166), 'd': (30, 110, 116), 't': (42, 156, 164),
           'L': (108, 236, 232), 'W': (220, 255, 252)}
    glyph = ['....PP....', '....pp....', '...dttd...', '..dtLLtd..', 'PptLWWLtpP',
             'PptLWWLtpP', '..dtLLtd..', '...dttd...', '....pp....', '....PP....']
    for j, row in enumerate(glyph):
        for i, ch in enumerate(row):
            if ch != '.':
                px((3 + i, 3 + j), pal[ch] + (255,))
    return im


def main():
    items = os.path.join(ROOT, 'items')
    os.makedirs(items, exist_ok=True)
    out = {
        'vacuum_syringe': syringe(0),
        'blood_sample': syringe(1),
        'resonant_mixture': mixture(1.3),
        'resonant_crystal': core_icon(),
        'dimensional_anchor': anchor_upgrade(),
    }
    for name, im in out.items():
        im.save(os.path.join(items, name + '.png'))
    # вид синхронизатора на голове пока пустой (прозрачный слой брони 64x32); своя модель — этап «Облик»
    armor = os.path.join(ROOT, 'models', 'armor')
    os.makedirs(armor, exist_ok=True)
    Image.new('RGBA', (64, 32), (0, 0, 0, 0)).save(os.path.join(armor, 'neuro_synchronizer_layer_1.png'))
    print('ok: %d item(s)' % len(out))


if __name__ == '__main__':
    main()
