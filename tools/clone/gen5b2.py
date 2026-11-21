"""Этап 5б, вариант 2 (по референсам Антона 08.11): тонкие нервы и сосуды на оболочках, прижатых к ванильному скелету,
4 текселя на пиксель модели; мышцы заново по референсу, на коробках игрока. Предпросмотр — вид спереди и сзади.
Запуск: python3 gen5b2.py OUT_DIR
"""
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

S = 4                                     # текселей (и вокселей предпросмотра) на пиксель модели
# ванильный скелет нужен только для листа слоёв (main): путь к skeleton.png из minecraft.jar — в переменной MC_SKELETON
VANILLA = os.environ.get('MC_SKELETON', 'skeleton.png')

# --- коробки (пиксели модели; y от ступней) ---
# скелет: ванильная модель и развёртка 64x32
SKEL = {'head': ((-4, 24, -4), (8, 8, 8), (0, 0)), 'body': ((-4, 12, -2), (8, 12, 4), (16, 16)),
        'arm_r': ((-6, 12, -1), (2, 12, 2), (40, 16)), 'arm_l': ((4, 12, -1), (2, 12, 2), (40, 16)),
        'leg_r': ((-3, 0, -1), (2, 12, 2), (0, 16)), 'leg_l': ((1, 0, -1), (2, 12, 2), (0, 16))}
# оболочки нервов и сосудов: чуть больше костей (на 0,25 пикселя), своя развёртка 256x256 (в текселях)
SHELL = {'head': ((-4.25, 23.75, -4.25), (8.5, 8.5, 8.5), (0, 0)),
         'body': ((-4.25, 11.75, -2.25), (8.5, 12.5, 4.5), (0, 68)),
         'arm_r': ((-6.25, 12, -1.25), (2.5, 12, 2.5), (104, 68)), 'arm_l': ((3.75, 12, -1.25), (2.5, 12, 2.5), (144, 68)),
         'leg_r': ((-3.25, 0, -1.25), (2.5, 12, 2.5), (184, 68)), 'leg_l': ((0.75, 0, -1.25), (2.5, 12, 2.5), (0, 136))}
# мышцы и кожа: коробки игрока, развёртка скина x4 (256x256)
PLAYER = {'head': ((-4, 24, -4), (8, 8, 8), (0, 0)), 'body': ((-4, 12, -2), (8, 12, 4), (16, 16)),
          'arm_r': ((-8, 12, -2), (4, 12, 4), (40, 16)), 'arm_l': ((4, 12, -2), (4, 12, 4), (32, 48)),
          'leg_r': ((-4, 0, -2), (4, 12, 4), (0, 16)), 'leg_l': ((0, 0, -2), (4, 12, 4), (16, 48))}


def box_tex(box, uv_scale):
    """Коробка в текселях: (X0, Y0, Z0, W, H, D, U, V); uv_scale — во сколько раз растянуть место в развёртке."""
    (x, y, z), (w, h, d), (u, v) = box
    return (round(x * S), round(y * S), round(z * S), round(w * S), round(h * S), round(d * S),
            round(u * uv_scale), round(v * uv_scale))


def face_rects(W, H, D, U, V):
    return {'nx': (U, V + D, D, H), 'front': (U + D, V + D, W, H), 'px': (U + D + W, V + D, D, H),
            'back': (U + 2 * D + W, V + D, W, H), 'top': (U + D, V, W, D), 'bottom': (U + D + W, V, W, D)}


def surface(X0, Y0, Z0, W, H, D, U, V):
    """Тексель развёртки -> воксель поверхности (перед — +z; лицо: u вправо = +x)."""
    X1, Y1, Z1 = X0 + W, Y0 + H, Z0 + D
    out = {}
    for a in range(D):
        for b in range(H):
            out[(U + a, V + D + b)] = (X0, Y1 - 1 - b, Z0 + a)
            out[(U + D + W + a, V + D + b)] = (X1 - 1, Y1 - 1 - b, Z1 - 1 - a)
    for a in range(W):
        for b in range(H):
            out[(U + D + a, V + D + b)] = (X0 + a, Y1 - 1 - b, Z1 - 1)
            out[(U + 2 * D + W + a, V + D + b)] = (X1 - 1 - a, Y1 - 1 - b, Z0)
        for c in range(D):
            out[(U + D + a, V + c)] = (X0 + a, Y1 - 1, Z0 + c)
            out[(U + D + W + a, V + c)] = (X0 + a, Y0, Z0 + c)
    return out


# --- рисование на грани: координаты в пикселях модели (u вправо, v вниз), толщина в текселях ---

def noise(x, y, k=4):
    return (((x * 7349) ^ (y * 1931) ^ (x * y * 31)) % 5 - 2) * k


def shade(c, d):
    return tuple(max(0, min(255, a + d)) for a in c)


class Face:
    def __init__(self, w_tex, h_tex):
        self.im = Image.new('RGBA', (w_tex, h_tex), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)
        self.w, self.h = w_tex / S, h_tex / S          # размер в пикселях модели

    def P(self, u, v):
        return (u * S, v * S)

    def line(self, pts, c, width=1):
        self.d.line([self.P(u, v) for u, v in pts], fill=c + (255,), width=width)

    def poly(self, pts, c):
        self.d.polygon([self.P(u, v) for u, v in pts], fill=c + (255,))

    def ellipse(self, u0, v0, u1, v1, c):
        self.d.ellipse([self.P(u0, v0), self.P(u1, v1)], fill=c + (255,))

    def branch(self, u, v, ang, length, c, depth=2, width=1, seed=1, spread=35):
        """Ветвящийся тонкий нерв/сосуд с лёгкой извилистостью (детерминированно)."""
        pts = [(u, v)]
        steps = max(2, int(length * 2))
        a = math.radians(ang)
        for i in range(steps):
            seed = (seed * 1103515245 + 12345) & 0x7fffffff
            a += ((seed % 21) - 10) / 90
            u += math.cos(a) * length / steps
            v += math.sin(a) * length / steps
            pts.append((u, v))
            if depth > 0 and i in (steps // 3, 2 * steps // 3):
                side = 1 if (seed >> 3) % 2 else -1
                self.branch(u, v, math.degrees(a) + side * spread, length * 0.45, c, depth - 1, 1, seed + i, spread)
        self.line(pts, c, width)


def draw_face(atlas, rect, fn, mirror=False):
    u, v, w, h = rect
    f = Face(w, h)
    fn(f)
    im = f.im.transpose(Image.FLIP_LEFT_RIGHT) if mirror else f.im
    atlas.alpha_composite(im, (u, v))


# роли граней конечностей: у medial/lateral u = 0 — передний край; у front/back/top/bottom u = 0 — наружный край
ROLES = {'r': {'front': ('front', False), 'back': ('back', True), 'medial': ('px', False), 'lateral': ('nx', True),
               'top': ('top', False), 'bottom': ('bottom', False)},
         'l': {'front': ('front', True), 'back': ('back', False), 'medial': ('nx', True), 'lateral': ('px', False),
               'top': ('top', True), 'bottom': ('bottom', True)}}


def paint_box(atlas, boxes, uv_scale, name, fns):
    """fns: грань или роль -> функция рисования."""
    X0, Y0, Z0, W, H, D, U, V = box_tex(boxes[name], uv_scale)
    rects = face_rects(W, H, D, U, V)
    for key, fn in fns.items():
        if name[:3] in ('arm', 'leg'):
            face, mirror = ROLES[name[-1]][key]
        else:
            face, mirror = key, False
        draw_face(atlas, rects[face], fn, mirror)


# ---------------- нервы ----------------
NV, NV_D, NV_L = (0xf0, 0xd0, 0x40), (0xd4, 0xb0, 0x2a), (0xfa, 0xe8, 0x8a)


def nerves_atlas():
    a = Image.new('RGBA', (256, 256), (0, 0, 0, 0))

    def head_front(f):
        f.ellipse(1.8, 4.0, 2.4, 4.5, NV)                         # зрительные нервы в глазницах
        f.ellipse(6.1, 4.0, 6.7, 4.5, NV)
        for s, u in ((1, 0.2), (-1, 8.3)):                        # лицевой нерв с боков веером
            for k, ang in enumerate((-30, 0, 30)):
                f.branch(u, 5.4, (0 if s > 0 else 180) + s * ang, 1.6, NV_D, 0, 1, 7 + k)
        f.branch(2.1, 3.5, -95, 3, NV, 1, 1, 3)                   # надглазничные вверх по лбу
        f.branch(6.4, 3.5, -85, 3, NV, 1, 1, 5)

    def head_side(f):
        for k, ang in enumerate((-150, -175, 160, 135)):          # лицевой нерв от уха вперёд
            f.branch(5.5, 5.2, ang, 3.5, NV_D, 1, 1, 11 + k)
        f.branch(7.5, 7, -100, 5, NV, 1, 1, 21)                   # большой затылочный вверх
        f.line([(7.6, 8.5), (7.6, 6.5)], NV, 2)                   # шейные корешки

    def body_back(f):
        f.line([(4.25, 0), (4.25, 12.5)], NV, 2)                  # спинной мозг
        for i in range(12):
            v = 0.6 + i * 1.0
            for s in (1, -1):
                f.branch(4.25 + s * 0.3, v, 90 - s * 70, 3.4, NV_D, 0, 1, 30 + i * 2 + (s > 0))
        for s in (1, -1):                                          # плечевое сплетение
            for k in range(3):
                f.line([(4.25, 0.4 + k * 0.5), (4.25 + s * 4.25, 0.6 + k * 0.7)], NV, 1)

    def body_front(f):
        for s in (1, -1):                                          # блуждающие нервы
            f.line([(4.25 + s * 0.9, 0), (4.25 + s * 1.0, 4), (4.25 + s * 0.6, 7.5)], NV, 1)
        for i in range(8):                                         # межрёберные и брюшные нервы дугами с боков
            v = 1.2 + i * 1.2
            for s in (1, -1):
                u0 = 0 if s > 0 else 8.5
                f.branch(u0, v, (12 if s > 0 else 168) + (10 if i > 4 else 0) * s, 2.8, NV_D, 0, 1, 50 + i * 2 + (s > 0), 25)
        for s in (1, -1):                                          # поясничное сплетение и бедренные нервы к ногам
            for k in range(4):
                f.line([(4.25 + s * 0.4, 8.5 + k * 0.4), (4.25 + s * (1.4 + k * 0.5), 12.5)], NV, 1)
        f.line([(3.9, 9.5), (3.4, 12.5)], NV_L, 2)                 # седалищные — толще
        f.line([(4.6, 9.5), (5.1, 12.5)], NV_L, 2)

    def body_side(f):
        for i in range(8):
            f.branch(4.5, 1.2 + i * 1.2, 180, 4.5, NV_D, 0, 1, 70 + i)

    def arm(name):
        return {
            'medial': lambda f: (f.branch(1.4, 0, 90, 10, NV, 2, 1, 81), f.branch(0.6, 4.8, 95, 5, NV_D, 1, 1, 83)),
            'front': lambda f: (f.branch(1.3, 4, 90, 6, NV, 1, 1, 85),
                                [f.line([(1.25, 10.2), (0.3 + k * 0.6, 12)], NV_D) for k in range(4)]),   # пальцевые
            'back': lambda f: f.branch(1.8, 0.2, 110, 6, NV, 1, 1, 87),         # лучевой спиралью
            'lateral': lambda f: f.branch(1.2, 4.5, 92, 7, NV_D, 1, 1, 89),
        }

    def leg(name):
        return {
            'back': lambda f: (f.line([(1.25, 0), (1.25, 5.5)], NV, 2),         # седалищный
                               f.branch(1.3, 5.5, 92, 6.5, NV, 1, 1, 91),        # большеберцовый
                               f.branch(1.0, 5.5, 140, 2, NV_D, 0, 1, 93)),      # малоберцовый уходит наружу
            'front': lambda f: (f.branch(1.6, 0, 100, 4.5, NV, 2, 1, 95, 30), f.branch(1.0, 6.5, 88, 5, NV_D, 1, 1, 97)),
            'lateral': lambda f: f.branch(1.8, 6, 95, 6, NV_D, 1, 1, 99),
            'medial': lambda f: f.branch(1.0, 3, 88, 9, NV_D, 1, 1, 101),       # подкожный
        }

    paint_box(a, SHELL, 1, 'head', {'front': head_front, 'nx': head_side})
    # другой бок головы — зеркало
    X0, Y0, Z0, W, H, D, U, V = box_tex(SHELL['head'], 1)
    draw_face(a, face_rects(W, H, D, U, V)['px'], head_side, mirror=True)
    paint_box(a, SHELL, 1, 'body', {'back': body_back, 'front': body_front, 'nx': body_side})
    X0, Y0, Z0, W, H, D, U, V = box_tex(SHELL['body'], 1)
    draw_face(a, face_rects(W, H, D, U, V)['px'], body_side, mirror=True)
    for n in ('arm_r', 'arm_l'):
        paint_box(a, SHELL, 1, n, arm(n))
    for n in ('leg_r', 'leg_l'):
        paint_box(a, SHELL, 1, n, leg(n))
    return a


# ---------------- сосуды ----------------
AR, AR_D, AR_L = (0xd8, 0x30, 0x34), (0xa8, 0x1c, 0x24), (0xee, 0x60, 0x60)
VE, VE_D, VE_L = (0x3c, 0x64, 0xd8), (0x2a, 0x46, 0xa8), (0x70, 0x98, 0xf0)


def pair(f, pts, off=(0.3, 0), wa=1, wv=1):
    """Артерия и вена рядом."""
    f.line([(u + off[0], v + off[1]) for u, v in pts], VE, wv)
    f.line(pts, AR, wa)


def vessels_atlas():
    a = nerves_atlas()

    def body_front(f):
        f.ellipse(4.4, 2.6, 6.3, 4.8, AR_D)                       # сердце за грудиной, левее середины
        f.ellipse(3.9, 2.8, 4.8, 4.0, VE_D)                       # правое предсердие
        f.line([(4.0, 0), (4.0, 2.9)], VE, 2)                     # верхняя полая вена
        f.line([(4.9, 2.7), (4.9, 1.0), (5.4, 0.6)], AR, 2)       # восходящая аорта и дуга
        for s in (1, -1):                                          # подключичные к плечам, сонные и яремные вверх
            f.line([(4.6, 0.8), (4.25 + s * 4.25, 0.9)], AR, 1)
            f.line([(4.0, 0.4), (4.25 + s * 4.25, 0.4)], VE, 1)
            f.line([(4.25 + s * 0.7, 0.8), (4.25 + s * 0.8, 0)], AR, 1)
            f.line([(4.25 + s * 1.2, 0.4), (4.25 + s * 1.3, 0)], VE, 1)
            f.line([(4.25 + s * 1.1, 1.2), (4.25 + s * 1.1, 7)], AR_L, 1)   # внутренние грудные вдоль грудины
        f.line([(3.7, 6.5), (3.7, 9.6)], VE, 3)                   # нижняя полая вена
        f.line([(4.7, 6.5), (4.7, 9.6)], AR, 2)                   # брюшная аорта
        for s, uo in ((1, 4.7), (-1, 3.7)):                       # подвздошные к ногам
            f.line([(uo, 9.6), (4.25 - s * 1.8, 12.5)], AR if s > 0 else VE, 2)
            f.line([(uo, 9.6), (4.25 + s * 1.8, 12.5)], AR if s > 0 else VE, 2)
        for i in range(7):                                         # межрёберные артерии и вены вдоль рёбер
            v = 1.4 + i * 0.95
            for s in (1, -1):
                u0 = 0 if s > 0 else 8.5
                f.branch(u0, v, 8 if s > 0 else 172, 2.8, AR if i % 2 else VE, 0, 1, 120 + i * 2 + (s > 0))
        for k in range(4):                                         # брыжеечные ветви в животе
            f.branch(4.7, 7.2 + k * 0.6, 15 + k * 20, 1.6, AR_D, 1, 1, 140 + k)
            f.branch(3.7, 7.4 + k * 0.6, 165 - k * 20, 1.4, VE_D, 1, 1, 150 + k)

    def head_front(f):
        for s, u in ((1, 0.3), (-1, 8.2)):
            f.branch(u, 7.6, -60 if s > 0 else -120, 4, AR, 1, 1, 160 + (s > 0))      # лицевая артерия к углу глаза
            f.branch(u + 0.3 * s, 7.8, -55 if s > 0 else -125, 3.6, VE, 1, 1, 162 + (s > 0))
        f.branch(3.4, 3.2, -95, 3, AR_L, 1, 1, 165)               # надблоковые вверх по лбу
        f.branch(5.1, 3.2, -85, 3, VE_L, 1, 1, 166)

    def head_side(f):
        f.line([(6.0, 8.5), (6.0, 6.3)], AR, 1)                   # наружная сонная
        f.line([(6.4, 8.5), (6.4, 6.3)], VE, 1)                   # яремная
        f.branch(6.0, 6.3, -100, 5.5, AR, 2, 1, 171)              # поверхностная височная
        f.branch(6.4, 6.3, -80, 5, VE, 2, 1, 173)
        f.branch(6.0, 7.0, 190, 4.5, AR_D, 1, 1, 175)             # лицевая
        f.branch(7.6, 7.5, -95, 4, AR_D, 1, 1, 177)               # затылочная

    def head_top(f):
        for k in range(4):
            f.branch(0.2 if k % 2 else 8.3, 2 + k * 1.4, 0 if k % 2 else 180, 3.5, AR if k < 2 else VE, 1, 1, 180 + k)

    def arm(name):
        return {
            'medial': lambda f: (pair(f, [(1.0, 0), (1.0, 5)], (0.35, 0), 1, 1),           # плечевые
                                 f.branch(1.0, 5, 95, 5, AR, 1, 1, 191), f.branch(1.4, 5, 80, 5, AR_D, 1, 1, 193),
                                 f.line([(2.2, 0), (2.1, 10)], VE)),                       # базилярная
            'lateral': lambda f: f.line([(1.4, 0), (1.2, 5), (1.5, 10)], VE),              # головная
            'front': lambda f: (f.line([(0.2, 4.6), (2.3, 5.6)], VE_L),                    # срединная вена локтя
                                f.line([(0.3, 10.4), (2.2, 10.4)], AR),                    # ладонная дуга
                                [f.line([(0.3 + k * 0.6, 10.4), (0.3 + k * 0.6, 12)], AR if k % 2 else VE) for k in range(4)]),
        }

    def leg(name):
        return {
            'front': lambda f: (pair(f, [(1.8, 0), (1.6, 4.5)], (0.35, 0), 2, 1),          # бедренные
                                f.branch(1.2, 6.5, 90, 5.3, AR, 1, 1, 201)),               # передняя большеберцовая
            'medial': lambda f: f.line([(1.4, 0), (1.6, 6), (1.3, 12)], VE),               # большая подкожная
            'back': lambda f: (pair(f, [(1.1, 4.5), (1.2, 6.5)], (0.35, 0)),               # подколенные
                               f.branch(1.2, 6.5, 90, 5.3, AR_D, 1, 1, 203), f.line([(1.6, 6.5), (1.7, 12)], VE)),
            'bottom': lambda f: f.line([(0.2, 1.2), (2.3, 1.2)], AR),                      # подошвенная дуга
        }

    paint_box(a, SHELL, 1, 'body', {'front': body_front})
    paint_box(a, SHELL, 1, 'head', {'front': head_front, 'nx': head_side, 'top': head_top})
    X0, Y0, Z0, W, H, D, U, V = box_tex(SHELL['head'], 1)
    draw_face(a, face_rects(W, H, D, U, V)['px'], head_side, mirror=True)
    for n in ('arm_r', 'arm_l'):
        paint_box(a, SHELL, 1, n, arm(n))
    for n in ('leg_r', 'leg_l'):
        paint_box(a, SHELL, 1, n, leg(n))
    return a


# ---------------- мышцы (по референсу Антона) ----------------
MU, MU_D, MU_L, MU_H = (0xc8, 0x52, 0x3a), (0x9c, 0x36, 0x28), (0xde, 0x72, 0x56), (0xec, 0x98, 0x7a)
TE, TE_D = (0xf0, 0xe8, 0xdc), (0xd8, 0xca, 0xb8)
EYE_D = (0x3a, 0x2c, 0x2a)


def fibers(f, pts, ang, base=MU, outline=True):
    """Мышца: многоугольник с волокнами под углом ang (градусы), тёмный контур."""
    mask = Image.new('L', f.im.size, 0)
    ImageDraw.Draw(mask).polygon([f.P(u, v) for u, v in pts], fill=255)
    a = math.radians(ang)
    nx, ny = -math.sin(a), math.cos(a)
    px = f.im.load()
    m = mask.load()
    for x in range(f.im.width):
        for y in range(f.im.height):
            if m[x, y]:
                t = int((x * nx + y * ny) // 1.5) % 4
                c = (base, shade(base, 10), base, shade(base, -8))[t]   # мягкие волокна (Антон 08.11)
                px[x, y] = shade(c, noise(x, y, 3)) + (255,)
    if outline:
        f.d.line([f.P(u, v) for u, v in pts + [pts[0]]], fill=shade(base, -22) + (255,), width=1)   # контур мягче


def tendon(f, pts, c=TE):
    f.poly(pts, c)


def muscles_atlas():
    a = Image.new('RGBA', (256, 256), (0, 0, 0, 0))

    def base(f, ang=90):
        fibers(f, [(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], ang, MU, False)

    def head_front(f):
        base(f, 90)
        fibers(f, [(0.3, 0), (7.7, 0), (7.4, 2.6), (0.6, 2.6)], 90, MU_L)        # лобная мышца
        tendon(f, [(3.6, 0), (4.4, 0), (4.2, 2.4), (3.8, 2.4)], TE_D)
        for cx in (2.1, 5.9):                                                   # круговые мышцы глаза, глаза
            f.ellipse(cx - 1.4, 2.8, cx + 1.4, 5.2, MU_D)
            f.ellipse(cx - 1.0, 3.2, cx + 1.0, 4.8, MU_L)
            f.ellipse(cx - 0.5, 3.6, cx + 0.5, 4.4, EYE_D)
        tendon(f, [(3.5, 3.0), (4.5, 3.0), (4.7, 5.6), (3.3, 5.6)], TE)          # хрящ носа
        fibers(f, [(0.2, 5.0), (2.4, 5.2), (3.0, 6.3), (0.4, 7.2)], 30, MU)     # скуловые
        fibers(f, [(7.8, 5.0), (5.6, 5.2), (5.0, 6.3), (7.6, 7.2)], 150, MU)
        f.ellipse(2.6, 5.9, 5.4, 7.3, MU_D)                                     # круговая мышца рта
        f.ellipse(3.0, 6.3, 5.0, 6.9, MU_L)
        f.line([(3.2, 6.6), (4.8, 6.6)], EYE_D, 1)
        tendon(f, [(3.4, 7.5), (4.6, 7.5), (4.4, 8), (3.6, 8)], TE_D)           # подбородок

    def head_side(f):
        base(f, 90)
        fibers(f, [(1.5, 0.5), (7.5, 0.5), (7.0, 4.5), (3.0, 5.0)], 100, MU_L)  # височная веером
        fibers(f, [(1.0, 5.0), (3.8, 4.8), (3.6, 8.0), (0.8, 8.0)], 80, MU)     # жевательная
        f.ellipse(4.5, 3.6, 6.1, 6.0, TE_D)                                     # ухо (хрящ)
        f.ellipse(4.9, 4.1, 5.7, 5.5, MU_D)
        fibers(f, [(6.5, 5.5), (8, 5.5), (8, 8), (5.8, 8)], 70, MU)             # шея сзади

    def head_top(f):
        tendon(f, [(0, 0), (8, 0), (8, 8), (0, 8)], TE)                          # сухожильный шлем
        fibers(f, [(0, 6.8), (8, 6.8), (8, 8), (0, 8)], 90, MU_L)               # лобная к лицу
        fibers(f, [(0, 0), (8, 0), (8, 1.0), (0, 1.0)], 90, MU)                 # затылочная

    def head_back(f):
        tendon(f, [(0, 0), (8, 0), (8, 3.5), (0, 3.5)], TE)
        fibers(f, [(1.0, 3.5), (3.6, 3.5), (3.4, 5.2), (1.2, 5.2)], 90, MU_L)   # затылочные
        fibers(f, [(4.4, 3.5), (7.0, 3.5), (6.8, 5.2), (4.6, 5.2)], 90, MU_L)
        fibers(f, [(0, 5.2), (8, 5.2), (8, 8), (0, 8)], 80, MU)                 # трапеция на шее
        tendon(f, [(3.7, 5.2), (4.3, 5.2), (4.3, 8), (3.7, 8)], TE_D)

    def body_front(f):
        base(f, 60)
        tendon(f, [(3.7, 0), (4.3, 0), (4.3, 12), (3.7, 12)], TE)               # белая линия и грудина
        fibers(f, [(0.2, 0.3), (3.7, 0.6), (3.7, 4.2), (1.2, 4.6), (0, 3.2)], 20, MU)      # большие грудные
        fibers(f, [(7.8, 0.3), (4.3, 0.6), (4.3, 4.2), (6.8, 4.6), (8, 3.2)], 160, MU)
        for s in (1, -1):                                                       # прямые мышцы живота (кубики)
            for k in range(4):
                v0 = 4.8 + k * 1.45
                u0, u1 = (4.4, 6.4) if s > 0 else (1.6, 3.6)
                fibers(f, [(u0, v0), (u1, v0), (u1, v0 + 1.2), (u0, v0 + 1.2)], 90, MU_L)
        fibers(f, [(0, 4.6), (1.5, 4.8), (1.5, 10.5), (0, 10.0)], 70, MU)       # косые
        fibers(f, [(8, 4.6), (6.5, 4.8), (6.5, 10.5), (8, 10.0)], 110, MU)
        f.line([(0.6, 10.2), (3.9, 12)], TE_D, 2)                               # паховые связки
        f.line([(7.4, 10.2), (4.1, 12)], TE_D, 2)

    def body_back(f):
        base(f, 90)
        fibers(f, [(4, 0), (8, 0.6), (8, 2.6), (4.3, 8.5)], 160, MU)            # трапеция
        fibers(f, [(4, 0), (0, 0.6), (0, 2.6), (3.7, 8.5)], 20, MU)
        fibers(f, [(0, 3.5), (2.4, 4.0), (3.4, 9.5), (0, 10.5)], 60, MU_L)      # широчайшие
        fibers(f, [(8, 3.5), (5.6, 4.0), (4.6, 9.5), (8, 10.5)], 120, MU_L)
        tendon(f, [(4, 3.0), (5.0, 5.0), (4, 7.0), (3.0, 5.0)], TE)             # сухожильный ромб
        tendon(f, [(2.6, 9.0), (5.4, 9.0), (4.6, 11.5), (3.4, 11.5)], TE)       # поясничная фасция
        for k in range(10):                                                     # остистые отростки бусинами
            f.ellipse(3.85, 0.5 + k * 1.1, 4.15, 0.8 + k * 1.1, TE)
        f.line([(0.5, 11.4), (3.6, 11.7)], TE_D, 1)                             # гребень таза
        f.line([(7.5, 11.4), (4.4, 11.7)], TE_D, 1)

    def body_side(f):
        base(f, 70)
        for k in range(5):                                                      # зубчатая мышца
            fibers(f, [(0.3, 1.5 + k * 1.1), (2.6, 1.2 + k * 1.1), (2.6, 2.1 + k * 1.1), (0.3, 2.3 + k * 1.1)], 15, MU_L)
        fibers(f, [(0, 7.5), (4, 7.0), (4, 12), (0, 12)], 60, MU)               # косая

    def arm():
        def front(f):
            base(f, 90)
            fibers(f, [(0, 0), (4, 0), (4, 2.6), (2, 3.2), (0, 2.6)], 90, MU_L)        # дельтовидная
            fibers(f, [(0.8, 2.6), (3.2, 2.6), (3.0, 5.2), (1.0, 5.2)], 90, MU)        # бицепс
            tendon(f, [(1.6, 5.0), (2.4, 5.0), (2.2, 5.8), (1.8, 5.8)], TE_D)
            fibers(f, [(0, 5.6), (2.2, 5.6), (1.6, 9.6), (0.2, 9.6)], 95, MU_L)        # сгибатели предплечья
            fibers(f, [(2.2, 5.6), (4, 5.6), (3.8, 9.6), (2.4, 9.6)], 85, MU)
            tendon(f, [(0, 9.6), (4, 9.6), (4, 10.2), (0, 10.2)], TE)                 # связка запястья
            tendon(f, [(0, 10.2), (4, 10.2), (4, 12), (0, 12)], TE_D)                 # кисть: сухожилия
            for k in range(4):
                f.line([(0.5 + k, 10.3), (0.5 + k, 12)], MU_L, 1)
        def back(f):
            base(f, 90)
            fibers(f, [(0, 0), (4, 0), (4, 2.6), (2, 3.0), (0, 2.6)], 90, MU)          # дельтовидная сзади
            fibers(f, [(0.6, 2.6), (3.4, 2.6), (3.0, 5.4), (1.0, 5.4)], 90, MU_L)      # трицепс
            tendon(f, [(1.4, 3.6), (2.6, 3.6), (2.4, 5.2), (1.6, 5.2)], TE)            # сухожилие трицепса
            fibers(f, [(0, 5.6), (4, 5.6), (3.6, 9.6), (0.4, 9.6)], 92, MU)            # разгибатели
            tendon(f, [(0, 9.6), (4, 9.6), (4, 12), (0, 12)], TE_D)
            for k in range(4):
                f.line([(0.5 + k, 9.8), (0.5 + k, 12)], TE, 1)
        def side(f):
            base(f, 90)
            fibers(f, [(0, 0), (4, 0), (4, 2.8), (0, 2.8)], 90, MU_L)
            tendon(f, [(0, 9.6), (4, 9.6), (4, 12), (0, 12)], TE_D)
        def top(f):
            fibers(f, [(0, 0), (4, 0), (4, 4), (0, 4)], 0, MU_L)
        def bottom(f):
            tendon(f, [(0, 0), (4, 0), (4, 4), (0, 4)], TE_D)
        return {'front': front, 'back': back, 'medial': side, 'lateral': side, 'top': top, 'bottom': bottom}

    def leg():
        def front(f):
            base(f, 90)
            fibers(f, [(0.2, 0), (2.0, 0), (2.4, 5.0), (0.4, 5.0)], 92, MU_L)         # четырёхглавая: латеральная
            fibers(f, [(2.0, 0), (3.8, 0), (3.6, 5.0), (2.4, 5.0)], 88, MU)           # прямая
            f.line([(3.9, 0.2), (2.6, 5.0)], TE_D, 2)                                 # портняжная полосой
            tendon(f, [(1.2, 5.0), (2.8, 5.0), (2.6, 6.6), (1.4, 6.6)], TE)           # надколенник
            fibers(f, [(0.2, 6.6), (3.0, 6.6), (2.6, 10.6), (0.4, 10.6)], 90, MU)     # передняя большеберцовая
            tendon(f, [(3.1, 6.6), (3.7, 6.6), (3.6, 10.6), (3.2, 10.6)], TE_D)       # большеберцовая кость под кожей
            tendon(f, [(0, 10.6), (4, 10.6), (4, 11.2), (0, 11.2)], TE)               # связки голеностопа
            tendon(f, [(0, 11.2), (4, 11.2), (4, 12), (0, 12)], TE_D)                 # стопа
            for k in range(4):
                f.line([(0.5 + k, 11.2), (0.5 + k, 12)], MU_L, 1)
        def back(f):
            base(f, 90)
            fibers(f, [(0, 0), (4, 0), (4, 2.4), (2, 2.8), (0, 2.4)], 20, MU_L)       # ягодичная
            fibers(f, [(0.2, 2.6), (1.9, 2.8), (1.9, 6.0), (0.4, 6.0)], 92, MU)       # двуглавая бедра
            fibers(f, [(2.1, 2.8), (3.8, 2.6), (3.6, 6.0), (2.1, 6.0)], 88, MU_L)     # полусухожильная
            fibers(f, [(0.4, 6.4), (1.9, 6.4), (1.8, 9.0), (0.8, 9.2)], 92, MU_L)     # икроножная, две головки
            fibers(f, [(2.1, 6.4), (3.6, 6.4), (3.2, 9.2), (2.2, 9.0)], 88, MU_L)
            tendon(f, [(1.5, 9.0), (2.5, 9.0), (2.3, 11.6), (1.7, 11.6)], TE)          # ахиллово сухожилие
            tendon(f, [(0, 11.6), (4, 11.6), (4, 12), (0, 12)], TE_D)
        def side(f):
            base(f, 90)
            f.line([(2.0, 0), (2.0, 5.5)], TE_D, 3)                                    # подвздошно-большеберцовый тракт
            tendon(f, [(0, 10.6), (4, 10.6), (4, 12), (0, 12)], TE_D)
        def top(f):
            fibers(f, [(0, 0), (4, 0), (4, 4), (0, 4)], 0, MU)
        def bottom(f):
            tendon(f, [(0, 0), (4, 0), (4, 4), (0, 4)], TE_D)
        return {'front': front, 'back': back, 'medial': side, 'lateral': side, 'top': top, 'bottom': bottom}

    paint_box(a, PLAYER, 4, 'head', {'front': head_front, 'nx': head_side, 'top': head_top, 'back': head_back,
                                     'bottom': lambda f: fibers(f, [(0, 0), (8, 0), (8, 8), (0, 8)], 0, MU)})
    X0, Y0, Z0, W, H, D, U, V = box_tex(PLAYER['head'], 4)
    draw_face(a, face_rects(W, H, D, U, V)['px'], head_side, mirror=True)
    paint_box(a, PLAYER, 4, 'body', {'front': body_front, 'back': body_back, 'nx': body_side,
                                     'top': lambda f: fibers(f, [(0, 0), (8, 0), (8, 4), (0, 4)], 0, MU),
                                     'bottom': lambda f: fibers(f, [(0, 0), (8, 0), (8, 4), (0, 4)], 0, MU)})
    X0, Y0, Z0, W, H, D, U, V = box_tex(PLAYER['body'], 4)
    draw_face(a, face_rects(W, H, D, U, V)['px'], body_side, mirror=True)
    for n in ('arm_r', 'arm_l'):
        paint_box(a, PLAYER, 4, n, arm())
    for n in ('leg_r', 'leg_l'):
        paint_box(a, PLAYER, 4, n, leg())
    return a


# ---------------- скелет и кожа для листа ----------------
FRESH_DARK, FRESH_LO, FRESH_HI = (0x5a, 0x46, 0x3e), (0xd2, 0xbc, 0xa6), (0xfa, 0xf3, 0xe6)


def fresh_skeleton():
    src = Image.open(VANILLA).convert('RGBA')
    out = []
    for r, g, b, al in src.getdata():
        L = 0.299 * r + 0.587 * g + 0.114 * b
        if L < 100:
            out.append(FRESH_DARK + (al,))
        else:
            t = max(0.0, min(1.0, (L - 121) / 95))
            out.append(tuple(round(lo + (hi - lo) * t) for lo, hi in zip(FRESH_LO, FRESH_HI)) + (al,))
    src.putdata(out)
    return src.resize((64 * S, 32 * S), Image.NEAREST)


def skin_placeholder():
    """Только для листа: кожа и комбинезон концепта (в игре — скин владельца)."""
    a = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    SK, SK_D, SUIT, SUIT_D, HAIR, EYE = (0xe0, 0xb0, 0x90), (0xc8, 0x96, 0x78), (0x4e, 0x63, 0x7a), (0x3e, 0x50, 0x63), (0x4a, 0x34, 0x22), (0x26, 0x2a, 0x33)
    fill = lambda c: (lambda f: f.poly([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], c))
    def face(f):
        fill(SK)(f); f.poly([(0, 0), (8, 0), (8, 2), (0, 2)], HAIR)
        f.poly([(1, 4), (2, 4), (2, 5), (1, 5)], EYE); f.poly([(6, 4), (7, 4), (7, 5), (6, 5)], EYE)
        f.poly([(3, 6.5), (5, 6.5), (5, 7), (3, 7)], SK_D)
    hairy = lambda f: (fill(SK_D)(f), f.poly([(0, 0), (f.w, 0), (f.w, 3), (0, 3)], HAIR))
    paint_box(a, PLAYER, 4, 'head', {'front': face, 'nx': hairy, 'px': hairy, 'top': fill(HAIR), 'back': fill(HAIR), 'bottom': fill(SK_D)})
    suit = {k: fill(SUIT if k != 'back' else SUIT_D) for k in ('front', 'back', 'nx', 'px', 'top', 'bottom')}
    paint_box(a, PLAYER, 4, 'body', suit)
    for n in ('arm_r', 'arm_l'):
        paint_box(a, PLAYER, 4, n, {k: (lambda f: (fill(SUIT)(f), f.poly([(0, 9), (f.w, 9), (f.w, 12), (0, 12)], SK_D)))
                                    for k in ('front', 'back', 'medial', 'lateral')})
    for n in ('leg_r', 'leg_l'):
        paint_box(a, PLAYER, 4, n, {k: fill(SUIT_D) for k in ('front', 'back', 'medial', 'lateral', 'top', 'bottom')})
    return a


# ---------------- предпросмотр ----------------

def put(vox, boxes, uv_scale, tex, cut=None, tint=None):
    for name, box in boxes.items():
        X0, Y0, Z0, W, H, D, U, V = box_tex(box, uv_scale)
        for (u, v), p in surface(X0, Y0, Z0, W, H, D, U, V).items():
            if cut is not None and p[1] >= cut:
                continue
            c = tex.getpixel((u, v))
            if c[3] > 127:
                vox[p] = c[:3] if tint is None else tuple(round(x * t) for x, t in zip(c[:3], tint))


def view(vox, back=False, scale=3):
    """Ортогональный вид спереди (+z) или сзади: ближайший воксель в каждой точке, затенение по глубине."""
    xs = [p[0] for p in vox]
    ys = [p[1] for p in vox]
    x0, x1, y1 = -9 * S, 9 * S, 33 * S
    best = {}
    for (x, y, z), c in vox.items():
        k = (x, y)
        zz = -z if back else z
        if k not in best or zz > best[k][0]:
            best[k] = (zz, c)
    im = Image.new('RGBA', ((x1 - x0) * scale, y1 * scale), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for (x, y), (zz, c) in best.items():
        sx = (x1 - 1 - x) if back else (x - x0)
        f = 0.82 + 0.18 * max(-1, min(1, zz / (4.25 * S)))
        d.rectangle([sx * scale, (y1 - 1 - y) * scale, sx * scale + scale - 1, (y1 - 1 - y) * scale + scale - 1],
                    fill=tuple(min(255, round(v * f)) for v in c) + (255,))
    return im


def iso(vox, scale=2.0):
    """Объёмный вид (диметрия, как в концепте): видны верх, бок +x и перед +z; ближние вокселы поверх дальних."""
    AX, AY, BX, BY, SY = 2 * scale, 0.5 * scale, -1 * scale, 1 * scale, 2 * scale
    X0, X1, Y1, Z0, Z1 = -9 * S, 9 * S, 33 * S, -5 * S, 5 * S
    W = int((X1 - X0) * AX - (Z1 - Z0) * BX) + 8
    H = int((X1 - X0) * AY + (Z1 - Z0) * BY + Y1 * SY) + 8
    ox, oy = -(Z1 - Z0) * BX + 4 - X0 * AX - Z0 * BX, Y1 * SY + 4 - X0 * AY - Z0 * BY
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    P = lambda X, Y, Z: (ox + X * AX + Z * BX, oy + X * AY + Z * BY - Y * SY)
    for (x, y, z) in sorted(vox, key=lambda p: (p[0] + p[2], p[1])):
        c = vox[(x, y, z)]
        if (x, y + 1, z) not in vox:
            d.polygon([P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=c)
        if (x + 1, y, z) not in vox:
            d.polygon([P(x + 1, y, z), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x + 1, y + 1, z)], fill=tuple(round(v * .74) for v in c))
        if (x, y, z + 1) not in vox:
            d.polygon([P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)], fill=tuple(round(v * .9) for v in c))
    return im


def slump(vox):
    """Обмякшая поза погибшего клона (для листа): голова свешена вперёд, руки и плечи чуть вперёд."""
    out = {}
    def rot(p, py, pz, ang):
        x, y, z = p
        a = math.radians(ang)
        dy, dz = y - py, z - pz
        return (x, round(py + dy * math.cos(a) - dz * math.sin(a)), round(pz + dy * math.sin(a) + dz * math.cos(a)))
    for (x, y, z), c in vox.items():
        p = (x, y, z)
        if y >= 24 * S:
            p = rot(p, 24 * S, 0, -28)
        elif y >= 12 * S and (x < -4 * S or x >= 4 * S):
            p = rot(p, 23 * S, 0, -12)
        elif y >= 12 * S:
            p = rot(p, 12 * S, 0, -6)
        out[p] = c
    return out


def build(tex, stage, frac=1.0, dead=False):
    vox = {}
    cut = lambda s: None if s < stage or frac >= 1 else round(32 * S * frac)
    if stage <= 3 or (stage == 4 and frac < 1):
        put(vox, SKEL, S, tex['skel'], cut(1))
    if 2 <= stage <= 3 or (stage == 4 and frac < 1):
        put(vox, SHELL, 1, tex['nerves'], cut(2))
    if stage == 3 or (stage == 4 and frac < 1):
        put(vox, SHELL, 1, tex['vessels'], cut(3))
    if stage == 4 or (stage == 5 and frac < 1):
        put(vox, PLAYER, 4, tex['muscle'], cut(4))
    if stage == 5:
        put(vox, PLAYER, 4, tex['skin'], cut(5), tint=(0.62, 0.74, 0.56) if dead else None)
    return slump(vox) if dead else vox


def main(out):
    os.makedirs(out, exist_ok=True)
    tex = {'skel': fresh_skeleton(), 'nerves': nerves_atlas(), 'vessels': vessels_atlas(),
           'muscle': muscles_atlas(), 'skin': skin_placeholder()}
    for k in ('nerves', 'vessels', 'muscle'):
        tex[k].save(os.path.join(out, 'b2_%s.png' % k))
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16)
    rows = [('1. Скелет', build(tex, 1)), ('2. Нервы', build(tex, 2)), ('3. Сосуды', build(tex, 3)),
            ('4. Мышцы', build(tex, 4)), ('5. Кожа: скин владельца', build(tex, 5)), ('Рост сосудов: 60 %', build(tex, 3, 0.6)),
            ('Рост кожи: 40 %', build(tex, 5, 0.4)), ('Погибший клон', build(tex, 5, dead=True))]
    panels = [(t, iso(v, 2)) for t, v in rows]
    pw, ph = max(p[1].width for p in panels), max(p[1].height for p in panels)
    cols = 4
    n = len(panels)
    sheet = Image.new('RGBA', (cols * (pw + 12) + 12, ((n + cols - 1) // cols) * (ph + 34) + 12), (0xee, 0xf1, 0xf1, 255))
    d = ImageDraw.Draw(sheet)
    for i, (t, im) in enumerate(panels):
        x, y = 12 + (i % cols) * (pw + 12), 12 + (i // cols) * (ph + 34)
        d.text((x, y), t, fill=(0x2c, 0x33, 0x34), font=font)
        sheet.alpha_composite(im, (x, y + 24))
    sheet.save(os.path.join(out, 'clone_layers_v9.png'))


if __name__ == '__main__':
    main(sys.argv[1])
