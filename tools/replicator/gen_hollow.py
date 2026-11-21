"""Концепт v5: полый репликатор клонов (2 блока) с объёмной моделью клона внутри, 5 стадий роста.

Вокселный рендер в ортогональной проекции, 1 воксель = 1 пиксель текстуры 32x32 на блок.
Запуск: python3 gen_hollow.py OUT_DIR
"""
import os
import sys
from PIL import Image, ImageDraw

GROOVE = (0xb3, 0xbb, 0xbb)
PANEL = (0xcd, 0xd7, 0xd8)
STRIP = (0xc4, 0xcc, 0xcd)
RIVET = (0xa8, 0xae, 0xae)
CYAN = (0x40, 0xa5, 0xa6)
CYAN_HI = (0x7f, 0xd6, 0xd6)
CYAN_OFF = (0x8a, 0x96, 0x96)
RIM = (0xbe, 0xea, 0xf1)
UU = (0x5a, 0x10, 0x52)
SCREEN = (0x2c, 0x33, 0x34)
BEZEL = (0x93, 0x9a, 0x9a)
HP = (0xd8, 0x3a, 0x30)
LED_UU, LED_EU = (0xc0, 0x5c, 0xd8), (0xe8, 0xc8, 0x3a)

BONE, BONE_D = (0xea, 0xe4, 0xd2), (0xc9, 0xc2, 0xab)
NERVE = (0xf6, 0xe3, 0x6a)
ARTERY, VEIN = (0xd0, 0x34, 0x3c), (0x3f, 0x5f, 0xc9)
MUSCLE, MUSCLE_L = (0xa4, 0x3c, 0x3a), (0xc2, 0x5a, 0x50)
SKIN, SKIN_D = (0xe0, 0xb0, 0x90), (0xc8, 0x96, 0x78)
SUIT, SUIT_D = (0x4e, 0x63, 0x7a), (0x3e, 0x50, 0x63)
HAIR = (0x4a, 0x34, 0x22)
EYE = (0x26, 0x2a, 0x33)

BASE_H, CAP_H = 10, 7
TOP = 64


PIN_DATA, PIN_EU = (0x40, 0xa5, 0xa6), (0xe8, 0xc8, 0x3a)
SOCKET, FLOOR, FLOOR_G = (0x3a, 0x42, 0x43), (0xa9, 0xb2, 0xb3), (0x96, 0x9e, 0x9f)


def casing_flat(x, z):
    c = PANEL
    if x in (0, 1, 30, 31) or z in (0, 1, 30, 31) or x in (12, 19) or z in (12, 19):
        c = GROOVE
    elif 13 <= x <= 18 or 13 <= z <= 18:
        c = STRIP
    return c


PIN_UU, PIN_WASTE = (0xa0, 0x4c, 0xc0), (0x8a, 0x92, 0x93)


def dim(c, k=.2):
    """Погасший контакт: цвет почти растворён в сером гнезда."""
    g = (0x5a, 0x61, 0x62)
    return tuple(round(g[i] + (c[i] - g[i]) * k) for i in range(3))


def socket(x, z, pin=None, off=False):
    """Универсальный порт 10x10: рамка, тёмное гнездо и четыре контакта 2x2:
    голубой — хранилище шаблонов, жёлтый — энергия, фиолетовый — жидкая материя, серый — вывод отходов."""
    if not (11 <= x <= 20 and 11 <= z <= 20):
        return None
    if x in (11, 20) or z in (11, 20):
        return BEZEL
    if x in (13, 14, 17, 18) and z in (13, 14, 17, 18):
        c = {(0, 0): PIN_DATA, (1, 0): PIN_EU, (0, 1): PIN_UU, (1, 1): PIN_WASTE}[(x > 15, z > 15)]
        return dim(c) if off else c
    return SOCKET


def uu_port(u, v):
    """Порт подвода жидкой материи: фланец с болтами и UU в центре (u, v от 0 до 31)."""
    d = abs(u - 15.5) + abs(v - 15.5)
    r = max(abs(u - 15.5), abs(v - 15.5))
    if d < 5.5:
        return UU
    if d < 6.5:
        return RIM
    if r < 9:
        return RIVET if (abs(u - 15.5) > 6.5 and abs(v - 15.5) > 6.5) else BEZEL
    if r < 10:
        return GROOVE
    return None


def floor_color(x, z, stage):
    """Пол камеры: решётчатый настил, круглая площадка под клоном с бирюзовым кольцом, сливы по углам."""
    r = ((x - 15.5) ** 2 + (z - 15.5) ** 2) ** .5
    if r < 7:
        return STRIP
    if r < 8:
        return CYAN if stage else CYAN_OFF
    if r < 9:
        return BEZEL
    if (x in (5, 6, 25, 26)) and (z in (5, 6, 25, 26)):
        return SOCKET
    return FLOOR_G if (x % 4 == 0 or z % 4 == 0) else FLOOR


def ceiling_color(x, z, stage):
    """Потолок камеры: излучатель материи, ромб UU в обойме, четыре форсунки."""
    d = abs(x - 15.5) + abs(z - 15.5)
    if d < 3.5:
        return UU
    if d < 4.5:
        return RIM
    if d < 7.5:
        return CYAN_HI if (stage and d > 6.5 and (abs(x - 15.5) < 1 or abs(z - 15.5) < 1)) else BEZEL
    return FLOOR_G if (x % 4 == 0 or z % 4 == 0) else FLOOR


def chamber(vox, stage):
    """Сплошная задняя стенка, пол и потолок камеры."""
    for x in range(32):
        for y in range(BASE_H, TOP - CAP_H):
            for z in range(3):
                c = PANEL
                if z == 2:   # внутренняя сторона стенки, видна сквозь стекло
                    if x in (3, 28) or y in (BASE_H, TOP - CAP_H - 1):
                        c = GROOVE
                    elif x in (8, 23) and (y - BASE_H) % 4 == 2:
                        c = CYAN if stage else CYAN_OFF
                    elif 13 <= x <= 18:
                        c = STRIP
                elif z == 0 and (x in (0, 31) or y in (BASE_H, TOP - CAP_H - 1)):
                    c = GROOVE   # наружная рамка задней стенки
                vox[(x, y, z)] = c
    for x in range(32):
        for z in range(32):
            vox[(x, BASE_H - 1, z)] = floor_color(x, z, stage) if (3 <= x <= 28 and 3 <= z <= 28) else vox[(x, BASE_H - 1, z)]
    # излучатель свисает с потолка на 1 воксель
    for x in range(32):
        for z in range(32):
            d = abs(x - 15.5) + abs(z - 15.5)
            if d < 7.5:
                vox[(x, TOP - CAP_H - 1, z)] = ceiling_color(x, z, stage)
    # задняя грань (z = 0): у каждого из двух блоков своя грань корпуса с универсальным портом; v сверху вниз
    for x in range(32):
        for v in range(32):
            c = socket(31 - x, v) or casing_flat(31 - x, v)
            vox[(x, 31 - v, 0)] = c
            vox[(x, 63 - v, 0)] = c


def flat_faces(vox, stage):
    """Плоские текстуры 32x32: верх, низ, зад, пол и потолок камеры."""
    def mk(fn):
        im = Image.new('RGBA', (32, 32))
        for u in range(32):
            for v in range(32):
                im.putpixel((u, v), fn(u, v) + (255,))
        return im
    return {
        'top': mk(lambda u, v: socket(u, v, PIN_DATA) or casing_flat(u, v)),
        'bottom': mk(lambda u, v: socket(u, v, PIN_EU) or casing_flat(u, v)),
        'back': mk(lambda u, v: socket(u, v) or casing_flat(u, v)),
        'floor': mk(lambda u, v: floor_color(u, v, stage) if (3 <= u <= 28 and 3 <= v <= 28) else GROOVE),
        'ceiling': mk(lambda u, v: ceiling_color(u, v, stage) if (3 <= u <= 28 and 3 <= v <= 28) else GROOVE),
    }


def rear(vox):
    """Поворот на 180 градусов вокруг вертикали: вид сзади-слева."""
    return {(31 - x, y, 31 - z): c for (x, y, z), c in vox.items()}


def base_and_cap(vox, stage):
    for x in range(32):
        for z in range(32):
            edge = x in (0, 31) or z in (0, 31)
            for y in range(BASE_H):
                c = PANEL
                if y in (0, BASE_H - 1) or ((x in (0, 31)) and (z in (0, 31))):
                    c = GROOVE
                vox[(x, y, z)] = c
            for y in range(TOP - CAP_H, TOP):
                c = PANEL
                if y in (TOP - CAP_H, TOP - 1):
                    c = GROOVE
                vox[(x, y, z)] = c
    # верх крышки: сетка и разъём хранилища шаблонов
    for x in range(32):
        for z in range(32):
            c = PANEL
            if x in (0, 1, 30, 31) or z in (0, 1, 30, 31) or x in (12, 19) or z in (12, 19):
                c = GROOVE
            elif 13 <= x <= 18 or 13 <= z <= 18:
                c = STRIP
            s = socket(x, z, PIN_DATA)
            vox[(x, TOP - 1, z)] = s or c
    # заклёпки на боках основания и крышки
    for p in (3, 15, 27):
        for y in (3, TOP - 4):
            vox[(p, y, 31)] = RIVET; vox[(p + 1, y, 31)] = RIVET
            vox[(31, y, p)] = RIVET; vox[(31, y, p + 1)] = RIVET
            vox[(0, y, p)] = RIVET; vox[(0, y, p + 1)] = RIVET
    # экран на лицевой стороне основания (грань z = 31)
    for x in range(8, 24):
        for y in range(2, 8):
            vox[(x, y, 31)] = BEZEL
    for x in range(9, 23):
        for y in range(3, 7):
            vox[(x, y, 31)] = SCREEN
    for x in range(10, 22):
        vox[(x, 5, 31)] = HP
    vox[(10, 4, 31)] = LED_UU; vox[(12, 4, 31)] = LED_EU
    for i in range(5):
        for j in range(2):
            vox[(14 + i * 2 + j, 4, 31)] = CYAN_HI if i < stage else (0x3e, 0x4c, 0x4d)
    # угловые стойки
    for cx, cz in ((0, 0), (29, 0), (0, 29), (29, 29)):
        for x in range(cx, cx + 3):
            for z in range(cz, cz + 3):
                for y in range(BASE_H, TOP - CAP_H):
                    vox[(x, y, z)] = PANEL if (x, z) != (cx + 1, cz + 1) else STRIP
    # бирюзовые дорожки на стойках
    for y in range(BASE_H + 2, TOP - CAP_H - 2, 4):
        vox[(30, y, 31)] = CYAN if stage else CYAN_OFF
        vox[(31, y, 30)] = CYAN if stage else CYAN_OFF


def player_boxes(k):
    """Коробки модели игрока: (имя, x0, y0, z0, w, h, d) в пикселях модели."""
    return [('head', -4, 24, -4, 8, 8, 8), ('body', -4, 12, -2, 8, 12, 4), ('arm_l', -8, 12, -2, 4, 12, 4),
            ('arm_r', 4, 12, -2, 4, 12, 4), ('leg_l', -4, 0, -2, 4, 12, 4), ('leg_r', 0, 0, -2, 4, 12, 4)]


MUS = [(0x7a, 0x24, 0x26), (0x96, 0x30, 0x30), (0xae, 0x3e, 0x3a), (0xc4, 0x58, 0x4e)]
TENDON, TENDON_D = (0xe6, 0xd8, 0xc6), (0xb8, 0x9a, 0x88)


def muscle(name, x, y, z, X0, X1, Y0, Y1, Z0, Z1, mx, R):
    """Мышцы: продольные волокна, сухожилия у суставов, грудные и пресс на торсе, мимические на лице."""
    front = z == Z1 - 1
    fib = (x * 3 + z * 5 + (y // 3)) % 4          # волокна идут вдоль конечности
    c = MUS[1 + (fib == 0) + (fib == 3 and y % 2 == 0)]
    if name.startswith(('arm', 'leg')):
        h = y - Y0
        if h < 2:                                  # сухожилия у запястья и лодыжки
            return TENDON if (x + z) % 2 else TENDON_D
        if name.startswith('leg') and h in (R(6), R(6) + 1) and front and abs(x - mx) < 1.6:
            return TENDON_D                        # колено
        if name.startswith('arm') and h == R(6) and front:
            return MUS[0]                          # локтевой изгиб
        mid = abs(h - (Y1 - Y0) * .6) < (Y1 - Y0) * .25
        if mid and fib != 0:                       # брюшко мышцы светлее
            c = MUS[3] if front else MUS[2]
        return c
    if name == 'body':
        h = Y1 - 1 - y
        if front:
            if abs(x - mx) < .6:
                return TENDON_D                    # белая линия по центру
            if h < R(5):                           # грудные
                return MUS[0] if h == R(5) - 1 else MUS[3] if h < R(2) else MUS[2]
            if abs(x - mx) > 3:
                return MUS[1] if fib else MUS[0]   # косые мышцы по бокам
            if (h - R(5)) % 3 == 2:
                return TENDON_D                    # перемычки пресса
            return MUS[2] if fib else MUS[3]
        if y >= Y1 - 2:
            return MUS[3]                          # трапеции
        return c
    if name == 'head':
        h = Y1 - 1 - y
        if front:
            ex = x in (X0 + R(2), X1 - 1 - R(2))
            if y == Y0 + R(4) and ex:
                return EYE
            if y in (Y0 + R(4) - 1, Y0 + R(4) + 1) and ex:
                return MUS[0]                      # круговая мышца глаза
            if y == Y0 + R(1) + 1 and X0 + R(2) < x < X1 - 1 - R(2):
                return TENDON                      # зубы
            if y == Y0 + R(2) and abs(x - mx) < 2:
                return MUS[0]
        if h < 2:
            return TENDON_D if (x + z) % 3 else TENDON  # сухожильный шлем на темени
        return c
    return c


NERVE_D, NERVE_L = (0xc8, 0xa8, 0x38), (0xff, 0xf6, 0xb8)
ART_D, ART_L = (0x8a, 0x1a, 0x22), (0xf0, 0x6a, 0x6a)
VEIN_D, VEIN_L = (0x26, 0x3c, 0x8a), (0x7a, 0x98, 0xe8)
CAPIL = (0xb8, 0x6a, 0xa8)


def _seg(p, a, b):
    """Точка p на отрезке a-b (в клетках грани)?"""
    (u, v), (u0, v0), (u1, v1) = p, a, b
    n = max(abs(u1 - u0), abs(v1 - v0)) or 1
    return any(round(u0 + (u1 - u0) * t / n) == u and round(v0 + (v1 - v0) * t / n) == v for t in range(n + 1))


def nerve(name, x, y, z, X0, X1, Y0, Y1, Z0, Z1, mx, R):
    """Нервная система: ствол с волнистым ходом и веточками, сплетения у плеч и таза, межрёберные нервы,
    ганглии яркими точками, окончания на кистях и стопах, веер лицевого нерва."""
    front, side = z == Z1 - 1, x in (X0, X1 - 1)
    if name.startswith(('arm', 'leg')):
        h = y - Y0
        if front:
            u = x - X0
            if u == 2 and h >= 1:
                return NERVE_L if h % 5 == 2 else NERVE      # ствол посередине, ганглии ярче
            d = 1 if (h // 4) % 2 else -1
            if h % 4 == 0 and h > 2 and u == 2 + d:
                return NERVE_D                               # веточки поочерёдно в стороны
            if h == 0 and u % 2 == 0:
                return NERVE_L                               # окончания на кисти и стопе
        return None
    if name == 'body':
        u, hb, W = x - X0, Y1 - 1 - y, X1 - X0
        rib = (y - Y0) % 3 == 0 and y > Y0 + R(5)
        if front:
            if abs(x - mx) <= .6:
                return NERVE_L if hb % 3 == 1 else NERVE      # спинной мозг сквозь грудь
            if _seg((u, hb), (W // 2 - 1, 1), (0, 3)) or _seg((u, hb), (W // 2, 1), (W - 1, 3)):
                return NERVE                                   # плечевое сплетение
            if _seg((u, hb), (W // 2 - 1, R(12) - 3), (1, R(12) - 1)) or _seg((u, hb), (W // 2, R(12) - 3), (W - 2, R(12) - 1)):
                return NERVE                                   # пояснично-крестцовое сплетение
            if not rib and (y - Y0) % 6 == 1 and y > Y0 + R(5) and 1.5 <= abs(x - mx) <= 3.5:
                return NERVE_L if abs(x - mx) == 1.5 else NERVE_D   # межрёберные нервы, ганглии у позвоночника
        return None
    if name == 'head':
        if side:
            w, hy = z - Z0, y - Y0
            ox, oy = 2, R(3)
            for ty in (oy + 3, oy + 1, oy - 1):
                if _seg((w, hy), (ox, oy), (Z1 - Z0 - 1, ty)):
                    return NERVE_L if (w, hy) == (ox, oy) else NERVE
        if front:
            ex = x in (X0 + R(2), X1 - 1 - R(2))
            if ex and y == Y0 + R(4) - 2:
                return NERVE                                   # подглазничные
            if y == Y0 + 1 and x in (X0 + R(3), X1 - 1 - R(3)):
                return NERVE_L                                 # подбородочные
    return None


def vessel(name, x, y, z, X0, X1, Y0, Y1, Z0, Z1, mx, R):
    """Кровеносная система: сердце, дуга и нисходящая аорта, полые вены, подключичные и подвздошные ветви,
    парные артерия и вена по конечностям с веточками, капиллярная сеть на кистях и стопах, сонная артерия."""
    front, side = z == Z1 - 1, x in (X0, X1 - 1)
    if name.startswith(('arm', 'leg')):
        h = y - Y0
        if h < 2 and (front or side):
            return (ARTERY, CAPIL, VEIN, ART_L)[(x * 3 + y + z * 2) % 4]   # капилляры
        if front:
            u = x - X0
            if u == 1:
                return ART_L if h % 4 == 0 else ARTERY        # артерия рядом с нервом
            if u == 3:
                return VEIN_L if h % 4 == 2 else VEIN         # вена с другой стороны
            if u == 0 and h % 4 == 1:
                return ART_D                                  # веточки
            if u == 4 and h % 4 == 3:
                return VEIN_D
        return None
    if name == 'body':
        u, hb, W = x - X0, Y1 - 1 - y, X1 - X0
        if front:
            hx, hy = W // 2 + 1, 4                             # сердце чуть левее центра
            if abs(u - hx) <= 1 and abs(hb - hy) <= 1:
                return ART_D if (u, hb) == (hx, hy) else ART_L if hb == hy - 1 else ARTERY
            if u == W // 2 - 1 and 1 <= hb <= R(12) - 4:
                return ARTERY                                  # аорта
            if hb == 1 and 0 <= u <= W // 2 - 1:
                return ARTERY                                  # дуга и подключичная артерия
            if u == W // 2 + 2 and (hb <= hy - 2 or hy + 2 <= hb <= R(12) - 4):
                return VEIN                                    # полые вены
            if hb == 0 and u >= W // 2 + 2:
                return VEIN                                    # подключичная вена
            if _seg((u, hb), (W // 2 - 1, R(12) - 4), (1, R(12) - 1)) or _seg((u, hb), (W // 2 - 1, R(12) - 4), (W // 2 - 2, R(12) - 1)):
                return ARTERY                                  # подвздошные артерии
            if _seg((u, hb), (W // 2 + 2, R(12) - 4), (W - 2, R(12) - 1)) or _seg((u, hb), (W // 2 + 2, R(12) - 4), (W // 2 + 1, R(12) - 1)):
                return VEIN
        return None
    if name == 'head':
        if side:
            w, hy = z - Z0, y - Y0
            if w == Z1 - Z0 - 2 and hy <= R(5):
                return ARTERY                                  # сонная и височная артерии
            if _seg((w, hy), (Z1 - Z0 - 2, R(5)), (Z1 - Z0 - 4, R(7))):
                return ART_L
            if w == 1 and hy <= R(6):
                return VEIN                                    # яремная вена
        if front and y == Y0 + R(4) - 1 and x in (X0 + R(3), X1 - 1 - R(3)):
            return ART_L                                       # угловые сосуды у глаз
    return None


def clone(vox, stage, k=1.25, cx=16, cz=16, y0=BASE_H):
    R = lambda a: round(a * k)
    for name, bx, by, bz, w, h, d in player_boxes(k):
        X0, X1 = cx + R(bx), cx + R(bx + w)
        Y0, Y1 = y0 + R(by), y0 + R(by + h)
        Z0, Z1 = cz + R(bz), cz + R(bz + d)
        mx, mz = (X0 + X1 - 1) / 2, (Z0 + Z1 - 1) / 2
        for x in range(X0, X1):
            for y in range(Y0, Y1):
                for z in range(Z0, Z1):
                    surf = x in (X0, X1 - 1) or z in (Z0, Z1 - 1) or y in (Y0, Y1 - 1)
                    core = abs(x - mx) <= .6 and abs(z - mz) <= .6
                    c = None
                    # 1. кости
                    if name == 'head':
                        if surf and stage <= 3:
                            c = BONE if (x + y) % 2 else BONE_D       # череп остаётся до мышечной стадии
                        if stage <= 3 and z == Z1 - 1 and y == Y0 + R(4) and x in (X0 + R(2), X1 - 1 - R(2)):
                            c = EYE
                    elif name == 'body':
                        if abs(x - mx) <= .6 and z == Z0 + 1:
                            c = BONE
                        elif surf and z == Z1 - 1 and (y - Y0) % 3 == 0 and y > Y0 + R(5):
                            c = BONE_D
                        elif y in (Y0, Y0 + 1) and abs(z - mz) < 1.5:
                            c = BONE_D
                    else:
                        if core:
                            c = BONE
                    if stage in (2, 3) and surf and (c is None or name == 'head') and c != EYE:
                        n = nerve(name, x, y, z, X0, X1, Y0, Y1, Z0, Z1, mx, R)
                        if n:
                            c = n
                    if stage == 3 and surf and c != EYE:
                        v = vessel(name, x, y, z, X0, X1, Y0, Y1, Z0, Z1, mx, R)
                        if v:
                            c = v
                    if stage == 4:
                        c = muscle(name, x, y, z, X0, X1, Y0, Y1, Z0, Z1, mx, R)
                    if stage >= 5:
                        if name == 'head':
                            c = SKIN if x < mx + .5 else SKIN_D
                            if y >= Y1 - R(2):
                                c = HAIR
                            if z == Z1 - 1 and y == Y0 + R(4) and x in (X0 + R(2), X1 - 1 - R(2)):
                                c = EYE
                        elif name.startswith('arm') and y < Y0 + R(3):
                            c = SKIN_D
                        else:
                            c = SUIT if (x + z) % 7 else SUIT_D
                    if c is not None:
                        vox[(x, y, z)] = c


# Диметрия с поворотом: лицевая грань (z = 31) видна шире боковой (x = 31), чтобы угловая стойка не закрывала клона.
AX, AY, BX, BY, S = 4, 1, -2, 2, 4


def proj(X, Y, Z, ox, oy):
    return (ox + X * AX + Z * BX, oy + X * AY + Z * BY - Y * S)


def shade(c, f):
    return tuple(min(255, round(v * f)) for v in c)


def render(vox, glass=True, glass_front=True, spans=None, top=TOP):
    """spans: участки стекла по высоте [(y0, y1)], по умолчанию вся камера; top: высота сцены в вокселях."""
    W = 32 * (AX - BX) + 8
    H = 32 * (AY + BY) + top * S + 8
    ox, oy = -32 * BX + 4, top * S + 4
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for (x, y, z) in sorted(vox, key=lambda p: (p[0] + p[2], p[1])):
        c = vox[(x, y, z)]
        P = lambda X, Y, Z: proj(X, Y, Z, ox, oy)
        if (x, y + 1, z) not in vox:
            d.polygon([P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=shade(c, 1.0))
        if (x + 1, y, z) not in vox:
            d.polygon([P(x + 1, y, z), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x + 1, y + 1, z)], fill=shade(c, .72))
        if (x, y, z + 1) not in vox:
            d.polygon([P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=shade(c, .88))
    if glass:
        ov = Image.new('RGBA', im.size, (0, 0, 0, 0))
        g = ImageDraw.Draw(ov)
        P = lambda X, Y, Z: proj(X, Y, Z, ox, oy)
        for y0, y1 in (spans or [(BASE_H, TOP - CAP_H)]):
            if glass_front:
                g.polygon([P(3, y0, 32), P(29, y0, 32), P(29, y1, 32), P(3, y1, 32)], fill=UU + (145,))
            g.polygon([P(32, y0, 3), P(32, y0, 29), P(32, y1, 29), P(32, y1, 3)], fill=UU + (170,))
            lines = (((5, y0 + 2, 32), (5, y1 - 2, 32)), ((8, y0 + 2, 32), (8, y1 - 8, 32))) if glass_front else ()
            for (a, b) in lines + (((32, y0 + 2, 6), (32, y1 - 2, 6)),):
                g.line([P(*a), P(*b)], fill=(255, 255, 255, 70), width=2)
            seed = 7
            for i in range(70):
                seed = (seed * 1103515245 + 12345) & 0x7fffffff
                u = 4 + seed % 24
                seed = (seed * 1103515245 + 12345) & 0x7fffffff
                v = y0 + 1 + seed % (y1 - y0 - 2)
                pt = P(u, v, 32) if (i % 2 and glass_front) else P(32, v, u)
                g.rectangle([pt[0], pt[1], pt[0] + 2, pt[1] + 2], fill=RIM + (170,))
        im = Image.alpha_composite(im, ov)
    return im


def frame_block(vox, oy=0):
    """Конструкционный блок репликатора: куб корпуса IC2, на каждой грани порт с погасшими контактами."""
    for x in range(32):
        for y in range(32):
            for z in range(32):
                if x in (0, 31) or y in (0, 31) or z in (0, 31):
                    if y == 31 or y == 0:
                        c = socket(x, z, off=True) or casing_flat(x, z)
                    elif z == 31 or z == 0:
                        c = socket(x, 31 - y, off=True) or casing_flat(x, 31 - y)
                    else:
                        c = socket(z, 31 - y, off=True) or casing_flat(z, 31 - y)
                    vox[(x, y + oy, z)] = c


def assembly():
    """Один конструкционный блок, два блока друг на друге до сборки."""
    one, two = {}, {}
    frame_block(one)
    frame_block(two); frame_block(two, 32)
    return {'frame': render(one, glass=False, top=32), 'stack': render(two, glass=False)}


def parts(vox, gap=12):
    """Составляющие мультиблока: нижний блок (y 0..31), верхний (y 32..63) и разнесённый вид."""
    lower = {k: c for k, c in vox.items() if k[1] < 32}
    upper = {(x, y - 32, z): c for (x, y, z), c in vox.items() if y >= 32}
    exploded = dict(lower)
    exploded.update({(x, y + 32 + gap, z): c for (x, y, z), c in upper.items()})
    return {
        'lower': render(lower, spans=[(BASE_H, 32)], top=32),
        'upper': render(upper, spans=[(0, 32 - CAP_H)], top=32),
        'exploded': render(exploded, spans=[(BASE_H, 32), (32 + gap, TOP - CAP_H + gap)], top=TOP + gap),
    }


STAGES = [(1, 'Опорная система'), (2, 'Нервная система'), (3, 'Венозная система'), (4, 'Мышечная система'), (5, 'Кожа')]

if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for st, _ in [(0, '')] + STAGES:
        vox = {}
        base_and_cap(vox, st)
        chamber(vox, st)
        if st:
            clone(vox, st)
        render(vox).save(os.path.join(out, 'stage%d.png' % st))
        if st == 5:
            render(vox, glass=False).save(os.path.join(out, 'stage5_noglass.png'))
            render(rear(vox), glass_front=False).save(os.path.join(out, 'stage5_rear.png'))
            empty = {}
            base_and_cap(empty, st); chamber(empty, st)
            for k, im in list(parts(empty).items()) + list(assembly().items()):
                im.save(os.path.join(out, 'part_%s.png' % k))
            for k, im in flat_faces(vox, st).items():
                im.save(os.path.join(out, 'face_%s.png' % k))
    print('ok')
