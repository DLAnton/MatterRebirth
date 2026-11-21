"""Концепт предметов: шприц, образец крови, синхронизатор и синхронизатор в кожаном шлеме (своя графика).

Значки 16x16 как у предметов Minecraft/IC2, плюс вид на голове игрока (воксели) и всплывающие подсказки.
Запуск: python3 gen_items.py OUT_DIR
"""
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'replicator'))  # gen_hollow
import gen_hollow as gh

OUTL = (0x22, 0x24, 0x26)
METAL, METAL_D, METAL_L = (0xa6, 0xae, 0xb0), (0x74, 0x7c, 0x7e), (0xdc, 0xe2, 0xe3)
GLASS, GLASS_L = (0xb8, 0xd4, 0xdc), (0xe8, 0xf6, 0xfa)
BLOOD, BLOOD_L = (0xb0, 0x14, 0x20), (0xe8, 0x48, 0x50)
GEM, GEM_D, GEM_L = (0x8a, 0x2c, 0xb0), (0x4e, 0x10, 0x6a), (0xd8, 0x9c, 0xf0)
CYAN = (0x5e, 0xe0, 0xe0)
LEATHER, LEATHER_D, LEATHER_L = (0x8a, 0x5a, 0x32), (0x5e, 0x3c, 0x20), (0xa8, 0x74, 0x46)
try:
    FONT = ImageFont.truetype('/usr/share/fonts/opentype/unifont/unifont.otf', 16)  # только для листов
except OSError:
    FONT = ImageFont.load_default()


ARM = (0x5a, 0x60, 0x62)
BAND, BAND_D, BAND_L = (0xdc, 0xe2, 0xe3), (0xa6, 0xae, 0xb0), (0xf4, 0xf7, 0xf7)
SLEEVE = (0xf0, 0xf2, 0xf2)
SKIN, SKIN_D, HAIR, EYE = gh.SKIN, gh.SKIN_D, gh.HAIR, gh.EYE
def line3(a, b):
    n = max(abs(b[i] - a[i]) for i in range(3)) or 1
    return [tuple(round(a[i] + (b[i] - a[i]) * t / n) for i in range(3)) for t in range(n + 1)]

def head_scene(kind, leather=(LEATHER, LEATHER_D, LEATHER_L)):
    """Вид на голове: дуга вокруг затылка, капсулы на висках (кристалл и схема), веер усиков к макушке,
    лбу и затылку, крючок за ухом — по референсам ЭЭГ-гарнитур."""
    v = {}
    n, x0, z0 = 24, 4, 4
    for x in range(x0, x0 + n):
        for y in range(n):
            for z in range(z0, z0 + n):
                if x in (x0, x0 + n - 1) or z in (z0, z0 + n - 1) or y in (0, n - 1):
                    c = SKIN if x < x0 + n // 2 else SKIN_D
                    if y >= n - 6 or (y >= n - 9 and z < z0 + n - 1):
                        c = HAIR
                    if z == z0 + n - 1 and 9 <= y <= 11 and (x0 + 5 <= x <= x0 + 7 or x0 + 16 <= x <= x0 + 18):
                        c = EYE if y < 11 and x in (x0 + 6, x0 + 17) else (0xf2, 0xf2, 0xf2)
                    v[(x, y, z)] = c
    L, LD, LL = leather
    s = 0
    if kind == 'leather':
        for x in range(x0 - 1, x0 + n + 1):
            for z in range(z0 - 1, z0 + n + 1):
                for y in range(n - 9, n + 1):
                    edge = x in (x0 - 1, x0 + n) or z in (z0 - 1, z0 + n) or y == n
                    if edge and not (z == z0 + n and y < n - 4):
                        v[(x, y, z)] = LD if (x * 7 + z * 3 + y * 5) % 11 == 0 else LL if (x * 5 + z * 11 + y) % 13 == 0 else L
        s = 1
    XR = x0 + n + s          # наружная плоскость справа (28 или 29)
    TOP = n + s              # над макушкой
    FR = z0 + n + s          # перед лбом
    mir = lambda p: (2 * x0 + n - 1 - p[0], p[1], p[2])
    PADS = set()

    def put(p, c, both=True):
        v[p] = c
        if both:
            v[mir(p)] = c

    def arm(pts, tip=True):
        path = []
        for a, b in zip(pts, pts[1:]):
            path += line3(a, b)[:-1]
        path.append(pts[-1])
        for p in path[:-2]:
            if p not in PADS:
                put(p, ARM)
        if tip:
            put(path[-2], SLEEVE); put(path[-1], CYAN)
        else:
            put(path[-2], ARM); put(path[-1], ARM)

    # дуга: от капсул назад, чуть поднимаясь, и вокруг затылка
    BZ = z0 + n - 9                     # задний край капсулы
    for z in range(z0 - 1 - s, BZ):
        t = (BZ - z) / (BZ - z0 + 1 + s)
        yb = round(13 + 3 * t)
        for xx in (XR, XR + 1):
            put((xx, yb, z), BAND); put((xx, yb + 1, z), BAND_L)
        if z <= z0 - 1 - s + 1:
            pass
    for x in range(x0 - 1 - s, XR + 2):
        for zz in (z0 - 1 - s, z0 - 2 - s):
            v[(x, 16, zz)] = BAND; v[(x, 17, zz)] = BAND_L
    # капсулы на висках
    for y in range(11, 19):
        for z in range(BZ, FR - 1):
            for xx in (XR, XR + 1):
                edge = y in (11, 18) or z in (BZ, FR - 2)
                put((xx, y, z), BAND_D if edge else BAND)
                PADS.add((xx, y, z)); PADS.add(mir((xx, y, z)))
    cz = (BZ + FR - 2) // 2
    for (dy, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
        c = CYAN if (dy, dz) == (0, 0) else GEM_L if dy > 0 else GEM if dy == 0 else GEM_D
        put((XR + 2, 15 + dy, cz + dz), c)
    for dz, c in ((-1, (0x3a, 0x7a, 0x3a)), (0, (0x2a, 0x5a, 0x2a)), (1, (0xd0, 0x30, 0x30))):
        put((XR + 2, 12, cz + dz), c)
    # усики
    arm([(XR + 1, 18, cz - 1), (XR + 1, TOP, cz - 2), (x0 + n - 7, TOP, cz - 3)])
    arm([(XR + 1, 18, cz + 2), (XR + 1, TOP, FR - 2), (x0 + n - 5, TOP, FR), (x0 + n - 5, n - 4, FR)])
    arm([(XR + 1, 16, FR - 1), (x0 + n - 2, 17, FR), (x0 + n - 3, 17, FR)])
    arm([(XR + 1, 18, BZ + 1), (XR + 1, TOP, BZ - 3), (x0 + n - 9, TOP, BZ - 5)])
    arm([(XR + 1, 17, z0 + 7), (XR + 1, TOP - 2, z0 + 3), (XR + 1, TOP - 2, z0 + 2)])
    arm([(XR + 1, 15, z0 + 3), (XR + 1, TOP, z0 - 1 - s), (x0 + n - 6, TOP, z0 - 1 - s)])
    if kind != 'leather':
        arm([(XR + 1, 11, BZ + 1), (XR + 1, 7, BZ - 1), (XR + 1, 4, BZ - 3)], tip=False)   # крючок за ухом
    return v




def device(kind, s, t=0):
    """Только сам синхронизатор из head_scene концепта 05.11 (без головы и шапки); s — отступ корпусов от головы
    в вокселях, t — на сколько вокселей дальше s лежат усики над макушкой и на лбу (14.11: прилегает к голове)."""
    v = {}
    n, x0, z0 = 24, 4, 4
    XR = x0 + n + s          # наружная плоскость справа (28 или 29)
    TOP = n + s + t          # над макушкой
    FR = z0 + n + s + t      # перед лбом
    mir = lambda p: (2 * x0 + n - 1 - p[0], p[1], p[2])
    PADS = set()

    def put(p, c, both=True):
        v[p] = c
        if both:
            v[mir(p)] = c

    def arm(pts, tip=True):
        path = []
        for a, b in zip(pts, pts[1:]):
            path += line3(a, b)[:-1]
        path.append(pts[-1])
        for p in path[:-2]:
            if p not in PADS:
                put(p, ARM)
        if tip:
            put(path[-2], SLEEVE); put(path[-1], CYAN)
        else:
            put(path[-2], ARM); put(path[-1], ARM)

    # дуга: от капсул назад, чуть поднимаясь, и вокруг затылка
    BZ = z0 + n - 9                     # задний край капсулы
    for z in range(z0 - 1 - s, BZ):
        t = (BZ - z) / (BZ - z0 + 1 + s)
        yb = round(13 + 3 * t)
        for xx in (XR, XR + 1):
            put((xx, yb, z), BAND); put((xx, yb + 1, z), BAND_L)
        if z <= z0 - 1 - s + 1:
            pass
    for x in range(x0 - 1 - s, XR + 2):
        for zz in (z0 - 1 - s, z0 - 2 - s):
            v[(x, 16, zz)] = BAND; v[(x, 17, zz)] = BAND_L
    # капсулы на висках
    for y in range(11, 19):
        for z in range(BZ, FR - 1):
            for xx in (XR, XR + 1):
                edge = y in (11, 18) or z in (BZ, FR - 2)
                put((xx, y, z), BAND_D if edge else BAND)
                PADS.add((xx, y, z)); PADS.add(mir((xx, y, z)))
    cz = (BZ + FR - 2) // 2
    for (dy, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
        c = CYAN if (dy, dz) == (0, 0) else GEM_L if dy > 0 else GEM if dy == 0 else GEM_D
        put((XR + 2, 15 + dy, cz + dz), c)
    for dz, c in ((-1, (0x3a, 0x7a, 0x3a)), (0, (0x2a, 0x5a, 0x2a)), (1, (0xd0, 0x30, 0x30))):
        put((XR + 2, 12, cz + dz), c)
    # усики
    arm([(XR + 1, 18, cz - 1), (XR + 1, TOP, cz - 2), (x0 + n - 7, TOP, cz - 3)])
    arm([(XR + 1, 18, cz + 2), (XR + 1, TOP, FR - 2), (x0 + n - 5, TOP, FR), (x0 + n - 5, n - 4, FR)])
    arm([(XR + 1, 16, FR - 1), (x0 + n - 2, 17, FR), (x0 + n - 3, 17, FR)])
    arm([(XR + 1, 18, BZ + 1), (XR + 1, TOP, BZ - 3), (x0 + n - 9, TOP, BZ - 5)])
    arm([(XR + 1, 17, z0 + 7), (XR + 1, TOP - 2, z0 + 3), (XR + 1, TOP - 2, z0 + 2)])
    arm([(XR + 1, 15, z0 + 3), (XR + 1, TOP, z0 - 1 - s), (x0 + n - 6, TOP, z0 - 1 - s)])
    if kind != 'leather':
        arm([(XR + 1, 11, BZ + 1), (XR + 1, 7, BZ - 1), (XR + 1, 4, BZ - 3)], tip=False)   # крючок за ухом
    return v


