"""Генератор текстур Matter Rebirth (своя графика).

Запуск: python tools/gen_textures.py  (нужен Pillow). Пишет PNG в src/main/resources/assets/matterrebirth/textures.
Предметы 16x16, грани машин 32x32, как у IC2 2.8.
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
GEM, GEM_D, GEM_L = (0x8a, 0x2c, 0xb0), (0x4e, 0x10, 0x6a), (0xd8, 0x9c, 0xf0)
CYAN = (0x5e, 0xe0, 0xe0)
LEATHER, LEATHER_D, LEATHER_L = (0x8a, 0x5a, 0x32), (0x5e, 0x3c, 0x20), (0xa8, 0x74, 0x46)


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


POD, POD_D = (0xd8, 0xde, 0xdf), (0x9a, 0xa2, 0xa4)
ARM = (0x5a, 0x60, 0x62)


def bez(p0, p1, p2, n=40):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        pts.append((round(x), round(y)))
    return pts


BAND, BAND_D, BAND_L = (0xdc, 0xe2, 0xe3), (0xa6, 0xae, 0xb0), (0xf4, 0xf7, 0xf7)
SLEEVE = (0xf0, 0xf2, 0xf2)


def synchronizer(leather=None):
    """Синхронизатор по референсам ЭЭГ-гарнитур Антона (05.11): светлая дуга вокруг затылка,
    капсулы на висках с резонирующим кристаллом и схемой, веер гибких усиков с бирюзовыми
    контактами в белых насадках, снизу два крючка за ушами. leather — цвета кожаного шлема или None."""
    im = icon()
    P = lambda x, y, c: im.putpixel((x, y), c + (255,)) if 0 <= x < 16 and 0 <= y < 16 else None
    if leather:
        L, LD, LL = leather
        for y in range(16):
            for x in range(16):
                ex, ey = (x + .5 - 8) / 6.6, (y + .5 - 10) / 8.0
                if (ex * ex + ey * ey < 1 and y <= 11) or (11 < y <= 12 and 1 < x < 14):
                    P(x, y, LL if x < 7 and y < 7 else LD if x > 10 else L)
        base = {(x, y) for y in range(16) for x in range(16) if im.getpixel((x, y))[3]}
        for (x, y) in base:
            if any((x + a, y + b) not in base for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                P(x, y, tuple(v // 2 for v in LD))
        arms = [((3, 7), (3, 3), (6, 2)), ((12, 7), (12, 3), (9, 2)), ((3, 7), (5, 5), (7, 5)), ((12, 7), (10, 5), (8, 5))]
        pod_y = (4, 9)
    else:
        # вид спереди: каркас-шлем. Купол из гибких усиков по форме шлема, сзади дуга-обод,
        # на висках капсулы, на пересечениях бирюзовые контакты
        for x in range(3, 13):                                  # задняя дуга видна сквозь каркас
            P(x, 9, BAND_D)
        dome = [((2, 10), (1, -1), (8, 0)), ((13, 10), (14, -1), (7, 0)),       # внешний контур купола
                ((4, 7), (4, 0), (8, 1)), ((11, 7), (11, 0), (7, 1)),           # внутренние меридианы
                ((7, 1), (7, 4), (7, 7)), ((8, 1), (8, 4), (8, 7))]             # центральный
        pts = set()
        for a_ in dome:
            pts |= set(bez(*a_, n=60))
        pts = {(x, y) for x, y in pts if 0 <= x < 16 and 0 <= y < 16}
        for (x, y) in pts:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in pts and 0 <= q[0] < 16 and 0 <= q[1] < 16 and not im.getpixel(q)[3]:
                    P(*q, OUTL)
        for (x, y) in pts:
            P(x, y, ARM if y > 2 else (0x7a, 0x82, 0x84))
        for q in ((7, 0), (8, 0), (4, 3), (11, 3), (2, 4), (13, 4), (7, 4), (8, 4)):
            P(*q, CYAN)
        for q in ((4, 7), (11, 7), (7, 7), (8, 7)):
            P(*q, SLEEVE)                                       # насадки над лбом
        arms = []
        pod_y = (7, 12)
    for a in arms:
        for (x, y) in bez(*a)[:-6]:
            P(x, y, ARM)
    for a in arms:
        pts = bez(*a)
        x, y = pts[-1]
        sx, sy = pts[-8]
        P(sx, sy, SLEEVE); P(x, y, CYAN)
    # капсулы на висках: кристалл сверху, под ним схема
    y0, y1 = pod_y
    for x0 in (1, 11):
        for y in range(y0, y1 + 1):
            for x in range(x0, x0 + 4):
                edge = x in (x0, x0 + 3) or y in (y0, y1)
                P(x, y, OUTL if edge else BAND if y < y1 - 1 else BAND_D)
        P(x0 + 1, y0 + 1, GEM_L); P(x0 + 2, y0 + 1, GEM); P(x0 + 1, y0 + 2, GEM); P(x0 + 2, y0 + 2, GEM_D)
        P(x0 + 1, y1 - 1, (0x3a, 0x7a, 0x3a)); P(x0 + 2, y1 - 1, (0xd0, 0x30, 0x30))
    if not leather:
        # крючки за ушами
        for (x, y) in ((2, 13), (2, 14), (13, 13), (13, 14)):
            P(x, y, ARM)
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


def face32(kind):
    """Грань корпуса: светлая панель с фаской, канавки, заклёпки, в центре порт с погашенными контактами."""
    PAN, PAN_L, PAN_D, GRV, RIV, RIV_L = (0xcd, 0xd7, 0xd8), (0xe4, 0xea, 0xea), (0x9c, 0xa5, 0xa6), (0xb0, 0xb8, 0xb8), (0x98, 0x9f, 0x9f), (0xee, 0xf2, 0xf2)
    SOCK, SOCK_D, PIN = (0x3a, 0x42, 0x43), (0x26, 0x2c, 0x2d), (0x80, 0x8a, 0x8a)
    im = Image.new('RGBA', (32, 32), PAN + (255,))
    px = im.load()
    for i in range(32):
        px[i, 0] = px[0, i] = PAN_L + (255,)
        px[i, 31] = px[31, i] = PAN_D + (255,)
        px[i, 1] = px[1, i] = PAN_L + (255,) if 0 < i < 31 else px[i, 1]
        px[i, 30] = px[30, i] = PAN_D + (255,) if 0 < i < 31 else px[i, 30]
    for i in range(4, 28):                      # внутренняя канавка
        px[i, 4] = px[4, i] = GRV + (255,); px[i, 27] = px[27, i] = PAN_L + (255,)
    for (x, y) in ((2, 2), (28, 2), (2, 28), (28, 28)):   # заклёпки 2x2
        px[x, y] = RIV_L + (255,); px[x + 1, y] = RIV + (255,); px[x, y + 1] = RIV + (255,); px[x + 1, y + 1] = PAN_D + (255,)
    # порт
    for y in range(10, 22):
        for x in range(10, 22):
            edge = x in (10, 21) or y in (10, 21)
            px[x, y] = (PAN_D if edge else SOCK_D if (x in (11,) or y in (11,)) else SOCK) + (255,)
    for (x, y) in ((13, 13), (18, 13), (13, 18), (18, 18)):
        for dx in (0, 1):
            for dy in (0, 1):
                px[x + dx, y + dy] = (PIN if dx + dy < 2 else (0x60, 0x68, 0x68)) + (255,)
    if kind == 'top':
        for (x, y) in ((15, 15), (16, 15), (15, 16), (16, 16)):
            px[x, y] = (0x4a, 0x52, 0x53, 255)
    return im


def main():
    items = os.path.join(ROOT, 'items')
    blocks = os.path.join(ROOT, 'blocks')
    os.makedirs(items, exist_ok=True)
    os.makedirs(blocks, exist_ok=True)
    out = {
        'vacuum_syringe': syringe(0),
        'blood_sample': syringe(1),
        'neuro_synchronizer': synchronizer(),
        'neuro_synchronizer_leather': synchronizer((LEATHER, LEATHER_D, LEATHER_L)),
        'resonant_mixture': mixture(1.3),
        'resonant_crystal': core_icon(),
        'dimensional_anchor': art(MATRIX, P),
    }
    for name, im in out.items():
        im.save(os.path.join(items, name + '.png'))
    face32('side').save(os.path.join(blocks, 'replicator_casing_side.png'))
    face32('top').save(os.path.join(blocks, 'replicator_casing_top.png'))
    print('ok: %d item(s), 2 block face(s)' % len(out))


if __name__ == '__main__':
    main()
