"""Слои клона v11 (21.11.2026): мышцы, сосуды и нервы по референсам Антона от 08.11
(мышцы, сосуды, нервы).

Раскладка текстур та же, что в моде (client/CloneRenderer), поэтому файлы подменяются без правки кода:
  clone_muscle.png   256x256  развёртка скина игрока x4 (коробки игрока)
  clone_vessels.png  256x256  атлас оболочек вокруг костей (x4)
  clone_nerves.png   256x256  тот же атлас

Как нарисовано
  Мышцы: каждая мышца задана формой (веретено по оси, веер с точкой схождения волокон, пластина), тон зависит
  от расстояния до края (объём) и от направления волокон; сухожилия и фасции кремовые. Голова спокойная: веки закрыты.
  Сосуды и нервы: гладкие линии с убывающей толщиной (ствол 4 px атласа, ветви 2..3, окончания 1) и ветвлением.

Координаты: «a» идёт вокруг коробки (0 = середина переда, минус = правая сторона клона / наружная сторона правой
конечности), y = высота в пикселях клона (0 = подошва, 32 = макушка). Левые конечности зеркальны правым.

Правило Антона (18.11 про сердце, 21.11 про аорту): деталь рисуется там, где она лежит в теле, а не обязательно
на поверхности. Поэтому на оболочках остаётся только то, что идёт по костям и по стенке тела (сосуды и нервы
конечностей и черепа, межрёберные, спинной мозг на позвоночнике), а глубокие стволы туловища сделаны объёмом внутри
скелета: аорта с дугой и ветвями, полые вены, лёгочные и почечные сосуды, подвздошные, поясничное сплетение.
  clone_inner.json          грани вокселей (воксель = 1/4 пикселя клона) для слоёв vessels и nerves:
                            [dir, p, a0, b0, la, lb, c]; dir 0..5 = +x -x +y -y +z -z; p = координата плоскости;
                            (a, b) = (y, z) для x, (x, z) для y, (x, y) для z; c = номер цвета в палитре
  clone_inner_palette.png   16x1: артерии 0..3, вены 4..7, нервы 8..11 (ранги: блик, основной, тонкая ветвь, тень)
В моде объём рисует client/CloneRenderer (как и сердце).

Запуск: python tools/clone/gen_layers11.py           -> прямо в ресурсы мода (textures/entity, models/entity)
        python tools/clone/gen_layers11.py OUT_DIR   -> все файлы в OUT_DIR (для сверки)
"""
import json, math, os, random
from PIL import Image

K = 4
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'matterrebirth')
OUT = os.path.join(ASSETS, 'textures', 'entity')
OUT_JSON = os.path.join(ASSETS, 'models', 'entity')

# ---------- палитра ----------
M = [(0x64, 0x15, 0x12), (0x91, 0x27, 0x1e), (0xb8, 0x3c, 0x2c), (0xd9, 0x5a, 0x40), (0xee, 0x7d, 0x5c), (0xff, 0xab, 0x88)]
T = [(0xc4, 0xab, 0x8e), (0xe9, 0xd9, 0xc0), (0xff, 0xf6, 0xe6)]
def col(t): return M[t] if t < 10 else T[t - 10]

# ---------- геометрия ----------
def seg_d(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / L))
    return math.hypot(px - ax - t * dx, py - ay - t * dy), t

def inside(pts, x, y):
    c = False
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]; x2, y2 = pts[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            c = not c
    return c

def edge_d(pts, x, y, open_x=()):
    d = 1e9
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]; x2, y2 = pts[(i + 1) % n]
        if any(abs(x1 - o) < 1e-6 and abs(x2 - o) < 1e-6 for o in open_x):
            continue        # ребро на оси симметрии: это не край мышцы
        d = min(d, seg_d(x, y, x1, y1, x2, y2)[0])
    return d

def bbox(pts, m=0.0):
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    return (min(xs) - m, min(ys) - m, max(xs) + m, max(ys) + m)

def depth_tone(de, span, hi, edge):
    """de: текселей от края; span: текселей от края до середины"""
    if de < 0.8:
        return edge
    q = (de - 0.8) / max(1.0, span - 0.8)
    return 3 if q < 0.34 else (4 if (q < 0.75 or not hi) else 5)

# ---------- формы мышц ----------
def spindle(axis, hw, peak=.5, pw=.75, tip=0.0, hi=False, edge=2):
    """веретено: ось (ломаная) и полуширина, максимум на доле peak длины"""
    segs = []; L = 0.0
    for i in range(len(axis) - 1):
        (ax, ay), (bx, by) = axis[i], axis[i + 1]
        l = math.hypot(bx - ax, by - ay); segs.append((ax, ay, bx, by, L, l)); L += l
    g = math.log(.5) / math.log(peak)
    bb = bbox(axis, hw + .05)
    def f(x, y):
        if not (bb[0] <= x <= bb[2] and bb[1] <= y <= bb[3]):
            return None
        bd = 1e9; bt = 0.0
        for ax, ay, bx, by, l0, l in segs:
            d, t = seg_d(x, y, ax, ay, bx, by)
            if d < bd:
                bd = d; bt = (l0 + t * l) / L
        if bt <= 0.0 or bt >= 1.0:
            w = hw * tip
        else:
            w = hw * (tip + (1 - tip) * math.sin(math.pi * bt ** g) ** pw)
        if w <= 0 or bd >= w:
            return None
        return depth_tone((w - bd) * K, w * K, hi, edge)
    return f

def fan(pts, focus, hi=True, edge=2, span=5.0, open_x=(), period=3.0, streak=True):
    """веер: многоугольник, волокна сходятся в точке focus"""
    bb = bbox(pts); fx, fy = focus
    rbar = sum(math.hypot(p[0] - fx, p[1] - fy) for p in pts) / len(pts)
    nf = rbar * K / period
    def f(x, y):
        if not (bb[0] <= x <= bb[2] and bb[1] <= y <= bb[3]) or not inside(pts, x, y):
            return None
        t = depth_tone(edge_d(pts, x, y, open_x) * K, span, hi, edge)
        if streak and t >= 3:
            r = math.hypot(x - fx, y - fy)
            if period * r / rbar > 1.6:
                ph = (math.atan2(y - fy, x - fx) * nf) % 1.0
                if ph < 1 / 3:
                    t = min(5, t + 1)
        return t
    return f

def slab(pts, dirv, period=.75, hi=False, edge=2, span=4.0, open_x=(), phase=0.0, streak=True):
    """пластина: многоугольник, волокна параллельны dirv"""
    bb = bbox(pts)
    l = math.hypot(*dirv); nx, ny = -dirv[1] / l, dirv[0] / l
    def f(x, y):
        if not (bb[0] <= x <= bb[2] and bb[1] <= y <= bb[3]) or not inside(pts, x, y):
            return None
        t = depth_tone(edge_d(pts, x, y, open_x) * K, span, hi, edge)
        if streak and t >= 3 and ((x * nx + y * ny) / period + phase) % 1.0 < 1 / 3:
            t = min(5, t + 1)
        return t
    return f

def cream(pts, open_x=(), inner=12, rim=11, rimw=0.8):
    bb = bbox(pts)
    def f(x, y):
        if not (bb[0] <= x <= bb[2] and bb[1] <= y <= bb[3]) or not inside(pts, x, y):
            return None
        return rim if edge_d(pts, x, y, open_x) * K < rimw else inner
    return f

def cline(pts, wt=1.1, tone=11):
    """кремовая (или иная) линия толщиной wt текселей"""
    half = wt / 2 / K
    bb = bbox(pts, half)
    def f(x, y):
        if not (bb[0] <= x <= bb[2] and bb[1] <= y <= bb[3]):
            return None
        for i in range(len(pts) - 1):
            if seg_d(x, y, *pts[i], *pts[i + 1])[0] < half:
                return tone
        return None
    return f

def rect(x0, x1, y0, y1, tone):
    def f(x, y):
        return tone if (x0 <= x < x1 and y0 <= y < y1) else None
    return f

def tile(x0, x1, y0, y1, tones=(3, 4, 5), open_x=()):
    """подушечка: прямоугольник со срезанными углами, светлее к середине"""
    def f(x, y):
        if not (x0 <= x < x1 and y0 <= y < y1):
            return None
        dl = 99 if any(abs(x0 - o) < 1e-6 for o in open_x) else (x - x0) * K
        dr = 99 if any(abs(x1 - o) < 1e-6 for o in open_x) else (x1 - x) * K
        db = (y - y0) * K; dt = (y1 - y) * K
        dxm = min(dl, dr); dym = min(db, dt)
        if dxm < 1 and dym < 1:
            return None            # срезанный угол
        de = min(dxm, dym)
        return tones[0] if de < 1 else (tones[1] if de < 2 else tones[2])
    return f

def pad(x0, x1, y0, y1):
    """кубик пресса: светлее вверху и к середине тела, тень снизу и с наружного края, углы срезаны"""
    def f(x, y):
        if not (x0 <= x < x1 and y0 <= y < y1):
            return None
        i = int((x - x0) * K); n = int(round((x1 - x0) * K)); j = int((y1 - y) * K); m = int(round((y1 - y0) * K))
        if (i in (0, n - 1)) and (j in (0, m - 1)):
            return None
        if j == m - 1 or i == n - 1:
            return 3
        if 1 <= j <= 2 and 1 <= i <= n - 3:
            return 5
        return 4
    return f

def ell(cx, cy, rx, ry, bands):
    """эллипс; bands = [(rho_max, тон), ...] по возрастанию rho"""
    def f(x, y):
        rho = math.hypot((x - cx) / rx, (y - cy) / ry)
        for rm, tone in bands:
            if rho < rm:
                return tone
        return None
    return f

def paint(shapes, x, y, base):
    t = base
    for s in shapes:
        v = s(x, y)
        if v is not None:
            t = v
    return t

# ---------- мышцы: туловище (h = |a|: перед 0..4, бок 4..8, спина 8..12; y 12..24) ----------
def torso_shapes():
    S = []; O = (0.0, 12.0)
    # бок и низ: наружная косая, волокна вниз к середине живота
    S.append(slab([(2.25, 19.4), (3.4, 19.6), (5.2, 19.2), (8.3, 18.4), (8.5, 14.5), (6.0, 14.25), (4.0, 15.9), (2.25, 14.15)],
                  (1, 1), period=.75))
    # сгибатели бедра под паховой связкой
    S.append(slab([(4.0, 15.6), (0.5, 12.0), (4.0, 12.0)], (0.1, 1), period=.75, span=3))
    # средняя ягодичная сбоку
    S.append(fan([(4.0, 14.4), (6.0, 14.25), (8.0, 14.4), (8.0, 12.0), (4.0, 12.0)], (6.0, 10.2), hi=False))
    # зубчатые мышцы: три зубца у края грудной
    for i in range(3):
        S.append(spindle([(5.0, 20.9 - .75 * i), (3.5, 20.2 - .75 * i)], .34, tip=.25))
    # верх: трапеция над ключицей
    S.append(slab([(0.5, 24.0), (4.0, 24.0), (4.0, 23.8), (0.5, 23.5)], (1, .25), period=.75, span=2))
    # большая грудная: веер к плечу
    S.append(fan([(0.25, 23.25), (1.6, 23.4), (3.9, 23.6), (4.0, 23.0), (4.0, 21.2), (3.4, 20.2), (2.3, 19.65), (1.0, 19.7), (0.25, 20.1)],
                 (4.9, 22.5), span=6))
    # влагалище прямой мышцы живота (крем) и её кубики
    S.append(cream([(0.0, 19.75), (2.0, 19.75), (2.25, 19.4), (2.25, 14.0), (0.25, 12.0), (0.0, 12.0)], open_x=O, inner=12, rim=12))
    for y1 in (19.5, 18.0, 16.5):
        S.append(pad(0.25, 2.0, y1 - 1.25, y1))
    S.append(fan([(0.25, 15.0), (2.0, 15.0), (2.0, 14.25), (0.75, 13.0), (0.25, 12.75)], (0.6, 9.0), span=3, streak=False))
    # грудина и ключица
    S.append(rect(0.0, 0.25, 19.75, 23.5, 12))
    S.append(rect(0.0, 0.5, 22.75, 23.5, 12))
    S.append(cline([(0.3, 23.375), (2.0, 23.5), (4.0, 23.7)], wt=1.2, tone=12))
    # паховая связка
    S.append(lambda x, y: 12 if (.25 <= x < 4.0 and -.01 <= (y - x - 11.75) < .49) else None)
    # ---- спина ----
    # ягодичная у крестца
    S.append(fan([(8.0, 14.3), (9.3, 14.4), (10.8, 14.8), (11.2, 13.6), (12.0, 12.4), (12.0, 12.0), (8.0, 12.0)], (6.6, 10.6), open_x=O, hi=False))
    # подостная и круглая
    S.append(fan([(8.0, 22.8), (8.9, 22.3), (10.2, 20.9), (10.6, 20.3), (9.4, 20.5), (8.0, 21.3)], (7.3, 22.4), hi=False, span=3))
    S.append(fan([(8.0, 21.2), (9.4, 20.45), (10.6, 20.2), (10.9, 19.8), (10.1, 19.7), (8.0, 20.6)], (7.3, 21.6), hi=False, span=3))
    # широчайшая: веер к подмышке
    S.append(fan([(5.8, 19.5), (7.0, 20.3), (8.0, 20.55), (10.1, 19.65), (10.9, 19.75), (11.3, 19.25), (11.75, 18.0), (11.5, 16.5), (10.8, 14.9), (9.2, 14.5), (7.2, 14.7), (6.0, 16.4)],
                 (6.9, 22.0), span=6))
    # трапеция: веер к плечу
    S.append(fan([(12.0, 24.0), (8.0, 24.0), (8.0, 23.0), (8.9, 22.4), (10.2, 21.0), (11.3, 19.3), (12.0, 17.9)], (7.4, 23.3), open_x=O, span=6))
    # поясничная фасция
    S.append(cream([(12.0, 18.2), (11.75, 18.0), (11.5, 16.5), (10.8, 14.9), (11.2, 13.6), (12.0, 12.4)], open_x=O))
    # гребень таза
    # ромб у седьмого шейного позвонка и точки остистых отростков
    S.append(cream([(12.0, 23.6), (11.5, 22.75), (12.0, 21.9)], open_x=O, inner=12, rim=11, rimw=.6))
    def dots(x, y):
        if x >= 11.75 and 12.5 <= y < 21.75 and int((24 - y) * K) % 3 == 0:
            return 11
        return None
    S.append(dots)
    return S

# ---------- мышцы: голова (h = |a|: лицо 0..4, бок 4..12, затылок 12..16; y 24..32) ----------
def head_shapes():
    S = []; O = (0.0, 16.0)
    def cap_y(h):
        pts = [(0, 31.5), (4, 31.5), (6, 31.0), (9, 30.75), (12, 30.25), (13, 29.75), (16, 29.5)]
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            if x1 <= h <= x2:
                return y1 + (y2 - y1) * (h - x1) / (x2 - x1)
        return 29.5
    # --- затылок и шея: под верхней выйной линией ---
    S.append(spindle([(15.0, 28.7), (14.9, 24.0)], .85, tip=.55, peak=.45, edge=2))
    S.append(spindle([(13.2, 28.8), (12.9, 24.0)], .95, tip=.55, peak=.45, edge=2))
    S.append(spindle([(11.4, 28.9), (11.0, 24.0)], .8, tip=.5, peak=.45, edge=2))
    # затылочное брюшко под краем шлема и выйная линия
    S.append(slab([(12.7, 30.2), (15.75, 29.6), (15.75, 28.8), (12.9, 29.0)], (0.05, 1), period=.75, span=2, edge=2))
    S.append(cline([(12.6, 28.85), (16.0, 28.7)], wt=1.0, tone=11))
    # грудино-ключично-сосцевидная
    S.append(spindle([(10.4, 27.0), (8.4, 25.4), (6.8, 24.0)], .6, tip=.5, edge=2))
    # жевательная: огибает угол челюсти
    S.append(spindle([(4.6, 27.2), (4.85, 24.5)], 1.25, tip=.5, peak=.45, hi=True, edge=2))
    # височная: веер
    S.append(fan([(4.4, 30.9), (6.5, 30.8), (9.0, 30.5), (10.9, 29.9), (11.1, 28.4), (9.2, 27.8), (6.0, 27.8), (4.4, 28.6)], (6.3, 26.6), span=5))
    # --- лицо ---
    # лобная: широкая пластина с редкими волокнами вверх к шлему; нижний край ложится на дугу глазницы
    S.append(slab([(0.0, 28.3), (0.0, 31.5), (2.1, 31.5), (3.3, 31.3), (3.9, 30.6), (3.9, 28.3)],
                  (0, 1), period=1.0, edge=3, span=4, open_x=O))
    S.append(spindle([(0.0, 29.6), (0.0, 28.3)], .42, tip=.5, edge=3))                                   # мышца гордецов
    # круговая мышца глаза: кольца волокон, веки закрыты
    S.append(ell(2.0, 27.5, 1.6, 1.25, [(.46, 4), (.68, 3), (.94, 4), (1.0, 3)]))
    S.append(rect(1.25, 2.75, 27.5, 27.75, 5))
    S.append(rect(1.25, 2.75, 27.25, 27.5, 1))
    # щека: щёчная (волокна поперёк), поднимающая губу, большая скуловая с тенью под ней
    S.append(slab([(2.3, 25.9), (3.3, 26.3), (3.35, 24.9), (2.35, 24.95)], (1, 0.05), period=.5, span=2, edge=3))
    S.append(spindle([(1.45, 26.65), (1.15, 25.95)], .42, tip=.35, edge=3))
    S.append(spindle([(3.6, 26.95), (2.0, 25.75)], .5, tip=.3, edge=3))
    S.append(cline([(3.55, 26.55), (2.15, 25.5)], wt=1.0, tone=2))
    # подбородок: подбородочная и опускающая угол рта
    S.append(tile(0.0, 0.9, 24.0, 24.85, tones=(3, 4, 5), open_x=O))
    S.append(fan([(1.15, 24.0), (2.75, 24.0), (2.45, 24.75), (2.0, 25.3), (1.55, 24.8)], (1.95, 25.9), hi=False, edge=3, span=3, period=2.5))
    # круговая мышца рта, губы сомкнуты
    S.append(ell(0.0, 25.5, 1.95, 1.0, [(.55, 4), (.8, 3), (1.0, 4)]))
    S.append(rect(0.0, 1.25, 25.375, 25.625, 0))
    S.append(rect(0.0, 0.75, 25.125, 25.375, 5))
    # нос: хрящ, крылья и ноздри
    S.append(rect(0.0, 0.25, 26.75, 28.5, 12))
    S.append(rect(0.25, 0.5, 26.5, 27.25, 11))
    S.append(rect(0.0, 0.75, 26.25, 26.75, 11)); S.append(rect(0.0, 0.25, 26.25, 26.75, 12))
    S.append(rect(0.25, 0.5, 26.25, 26.5, 1))
    # ухо
    S.append(ell(9.0, 26.9, .6, .95, [(.45, 10), (1.0, 11)]))
    # сухожильный шлем
    def cap(x, y):
        c = cap_y(x)
        if y >= c:
            return 11 if (y - c) * K < 1 else 12
        return None
    S.append(cap)
    # затылочные брюшки на шлеме, выйная связка
    S.append(rect(15.75, 16.01, 24.0, 29.5, 11))
    return S

# ---------- мышцы: правая рука (a: перед -2..2, наружная -6..-2, внутренняя 2..6, зад вокруг +-8; y 12..24) ----------
def sp(axis, hw, **k):
    k.setdefault('edge', 3)
    return spindle(axis, hw, **k)

def arm_shapes():
    F = []; B = []      # B: координата a mod 16 (зад = 6..10, наружная сторона = 10..14)
    # предплечье: у каждой мышцы своё тонкое сухожилие до запястья
    for x0, x1, hw, hi in ((-2.3, -1.6, .85, True), (-0.1, -0.1, .8, False), (1.6, 1.5, .8, False), (3.6, 3.5, .95, True), (5.3, 5.2, .8, False)):
        F.append(cline([(x1 + .01, 15.2), (x1 + .01, 14.0)], wt=1.0, tone=11))
        F.append(sp([(x0, 18.6 if x0 > -2 else 19.0), (x1, 14.7)], hw, peak=.35, hi=hi))
    for x0, x1, hw, hi in ((7.1, 7.2, .85, False), (8.9, 8.8, .85, False), (10.7, 10.6, .9, True), (12.5, 12.6, .9, False)):
        B.append(cline([(x1 + .01, 15.2), (x1 + .01, 14.0)], wt=1.0, tone=11))
        B.append(sp([(x0, 18.4), (x1, 14.7)], hw, peak=.35, hi=hi))
    # плечо
    F.append(sp([(2.4, 22.6), (2.5, 19.0)], .75))
    F.append(sp([(4.2, 23.4), (4.3, 18.8)], 1.2, hi=True))
    F.append(sp([(-2.75, 20.6), (-2.6, 18.2)], 1.1))
    F.append(sp([(0.0, 21.9), (0.1, 18.6)], 1.65, peak=.45, hi=True))
    F.append(cline([(0.13, 18.8), (0.13, 18.0)], wt=1.0, tone=11))
    B.append(sp([(6.6, 23.6), (6.9, 19.2)], 1.1, hi=True))
    B.append(sp([(10.6, 22.4), (10.3, 19.2)], 1.45, hi=True))
    B.append(cream([(7.75, 20.5), (8.25, 20.5), (8.5, 18.75), (8.0, 17.75), (7.5, 18.75)], inner=11, rim=11))
    # дельтовидная: три пучка сходятся на наружной стороне
    fo = (-4.0, 19.6)
    F.append(fan([(-1.9, 24.0), (1.3, 24.0), (1.4, 22.7), (-0.4, 21.6), (-2.4, 20.7), (-4.0, 20.2)], fo, span=5))
    F.append(fan([(-6.1, 24.0), (-1.9, 24.0), (-4.0, 20.2)], fo, span=5))
    F.append(fan([(-7.7, 24.0), (-6.1, 24.0), (-4.0, 20.2), (-5.6, 20.7), (-7.0, 21.7), (-7.8, 22.7)], fo, span=5))
    return F, B

def arm_paint(a, y):
    F, B = ARM
    # кисть и запястье
    if y < 14.0:
        if y >= 13.75:
            return 11                                           # связка запястья
        col_i = int(math.floor(a * K))
        if y < 12.5:                                            # пальцы
            return 2 if (col_i % 4 == 0 and (abs(a) >= 2)) else 4
        if -6 <= a < -2:                                        # тыл кисти: сухожилия разгибателей
            return 11 if (col_i % 4 == 2 and 12.75 <= y < 13.5) else 3
        if 2 <= a < 6:                                          # ладонь
            return 4 if (12.75 <= y < 13.25 and col_i % 8 not in (0, 7)) else 3
        if -2 <= a < 2 and 12.5 <= y < 13.25 and -1.25 <= a < 1.25:
            return 4                                            # возвышение большого пальца
        return 3
    base = 2
    t = paint(F, a, y, base)
    v = paint(B, a % 16, y, None)
    # зад рисуется поверх только там, где перед ничего не дал
    if v is not None and (t == base):
        t = v
    return t

def leg_shapes():
    F = []; B = []
    # ахилл
    B.append(cream([(7.25, 4.75), (8.75, 4.75), (8.5, 2.0), (8.25, 0.0), (7.75, 0.0), (7.5, 2.0)], inner=12, rim=11))
    # голень
    F.append(sp([(-4.0, 5.6), (-4.2, 2.4)], .95, peak=.4, hi=True))
    F.append(sp([(-2.3, 5.3), (-2.0, 2.2)], .68, peak=.4))
    F.append(sp([(-0.8, 5.3), (-0.5, 3.6), (-0.2, 2.0)], .85, peak=.35, hi=True))
    F.append(rect(0.5, 1.0, 1.75, 4.75, 11))
    F.append(sp([(2.1, 4.8), (2.0, 2.0)], .6, peak=.4))
    F.append(sp([(3.9, 5.7), (3.7, 2.6)], 1.1, peak=.4, hi=True))
    B.append(sp([(6.2, 3.9), (6.9, 1.7)], .55))
    B.append(sp([(9.8, 3.9), (9.1, 1.7)], .55))
    B.append(sp([(10.9, 4.8), (10.6, 1.9)], .65))
    B.append(sp([(7.0, 6.1), (7.1, 3.0)], 1.08, peak=.42, hi=True))
    B.append(sp([(9.0, 6.0), (8.9, 3.3)], 1.02, peak=.42, hi=True))
    # бедро спереди
    F.append(sp([(1.6, 12.0), (1.9, 10.2)], .8, tip=.6, peak=.4))
    F.append(sp([(3.4, 11.9), (3.6, 9.5), (3.4, 7.4)], 1.05))
    F.append(sp([(5.2, 11.9), (5.0, 9.0), (4.6, 6.6)], .85))
    F.append(sp([(-2.7, 11.6), (-2.6, 9.0), (-2.1, 6.9)], 1.3, peak=.55, hi=True))
    F.append(sp([(1.9, 9.9), (1.7, 8.0), (1.3, 6.5)], 1.05, peak=.66, hi=True))
    F.append(sp([(-0.3, 12.0), (-0.1, 9.5), (0.0, 7.3)], 1.15, peak=.42, hi=True, tip=.3))
    F.append(sp([(-3.3, 12.0), (-3.7, 10.4)], .75, tip=.5))
    F.append(cline([(-4.125, 10.4), (-4.125, 8.4)], wt=2.1, tone=5))
    F.append(cline([(-4.125, 8.4), (-4.125, 6.4)], wt=2.1, tone=11))
    # портняжная: лента наискось
    F.append(sp([(-2.6, 12.0), (-0.9, 11.2), (0.9, 10.0), (2.2, 8.6), (2.9, 7.2), (3.1, 6.2)], .36, tip=.9, pw=.2, edge=3))
    # колено
    F.append(cream([(-0.5, 7.5), (0.5, 7.5), (0.85, 6.75), (-0.85, 6.75)], inner=11, rim=11))
    F.append(cream([(-0.5, 5.5), (0.5, 5.5), (0.25, 4.75), (-0.25, 4.75)], inner=11, rim=11))
    F.append(ell(0.0, 6.125, .95, .8, [(.55, 12), (1.0, 11)]))
    # бедро сзади
    B.append(sp([(11.1, 10.0), (11.0, 6.6)], .9))
    B.append(sp([(6.9, 9.1), (6.7, 7.4), (6.5, 5.7)], 1.0, peak=.45, hi=True))
    B.append(sp([(9.0, 9.1), (9.3, 7.4), (9.5, 5.8)], 1.0, peak=.45, hi=True))
    B.append(cline([(6.5, 5.8), (6.3, 5.0)], wt=1.1, tone=11))
    B.append(cline([(9.5, 5.9), (9.75, 5.0)], wt=1.1, tone=11))
    # большая ягодичная
    B.append(fan([(6.0, 12.0), (6.0, 9.9), (6.9, 8.9), (8.4, 8.55), (9.9, 8.8), (11.4, 9.6), (12.3, 10.9), (12.4, 12.0)], (12.6, 8.6), span=6))
    return F, B

def leg_paint(a, y):
    F, B = LEG
    ab = a % 16
    if y < 1.5:
        col_i = int(math.floor(a * K))
        if 6.0 <= ab < 10.0:                                    # пятка и ахилл
            if 7.75 <= ab < 8.25 or (y < 1.0 and 7.25 <= ab < 8.75):
                return 11 if y >= .25 else 10
            return 3
        if -2 <= a < 2:                                         # тыл стопы: тонкие сухожилия к пальцам
            if y < .5:
                return 2 if col_i % 4 == 0 else 4
            return 11 if (col_i % 4 == 2 and y < 1.25) else 3
        return 4 if (.25 <= y < 1.0) else 3
    t = paint(F, a, y, 2)
    v = paint(B, ab, y, None)
    if v is not None and t == 2:
        t = v
    return t

TORSO = torso_shapes(); HEAD = head_shapes(); ARM = arm_shapes(); LEG = leg_shapes()

def body_paint(part, a, y):
    if part == 'body':
        return paint(TORSO, abs(a), y, 3)
    if part == 'head':
        return paint(HEAD, abs(a), y, 3)
    if part == 'arm_r':
        return arm_paint(a, y)
    if part == 'arm_l':
        return arm_paint(-a, y)
    if part == 'leg_r':
        return leg_paint(a, y)
    return leg_paint(-a, y)

def cap_paint(part, top, x, z, w, d):
    """верхняя и нижняя грани: x поперёк (минус = правая сторона клона), z вглубь (плюс = перед)"""
    de = min(w / 2 - abs(x), d / 2 - abs(z)) * K
    if part == 'head':
        if top:
            return 11 if de < 1 else 12
        return 2 if de < 2 else 1
    if part == 'body':
        return 3 if top else 1
    if part.startswith('arm'):
        if part == 'arm_l':
            x = -x
        if top:                                                 # дельтовидная сверху
            if de < 1:
                return 2
            ph = (math.atan2(z, x + 0.6) * 2.2) % 1.0
            return 4 if ph < 1 / 3 else 3
        return 1 if int(math.floor((z + 2) * K)) % 4 == 0 else 4   # кончики пальцев
    if top:
        return 2
    if z > 1.25:                                                # пальцы стопы
        return 1 if int(math.floor((x + 2) * K)) % 4 == 0 else 4
    return 3 if de < 1 else (11 if de < 2 else 12)              # подошва

# ---------- части и раскладка ----------
class Part:
    def __init__(s, name, w, h, d, ytop, u, v, k=K):
        s.name, s.w, s.h, s.d, s.ytop, s.u, s.v, s.k = name, w, h, d, ytop, u, v, k
        s.P = 2 * (w + d)
        s.W = int(round(s.P * k)); s.H = int(round(h * k))
        s.x0 = u; s.y0 = v + int(round(d * k))
    def a_of(s, i):
        a = (i + .5) / s.k - s.d - s.w / 2
        return a - s.P if a >= s.P / 2 else a
    def y_of(s, j):
        return s.ytop - (j + .5) / s.k
    def X(s, a):
        return (a + s.d + s.w / 2) * s.k
    def Y(s, y):
        return (s.ytop - y) * s.k

MUS = [Part('head', 8, 8, 8, 32, 0, 0), Part('body', 8, 12, 4, 24, 64, 64),
       Part('arm_r', 4, 12, 4, 24, 160, 64), Part('arm_l', 4, 12, 4, 24, 128, 192),
       Part('leg_r', 4, 12, 4, 12, 0, 64), Part('leg_l', 4, 12, 4, 12, 64, 192)]
SHELL = {p.name: p for p in [Part('head', 8.5, 8.5, 8.5, 32.25, 0, 0), Part('body', 8.5, 12.5, 4.5, 24.25, 0, 68),
         Part('arm_r', 2.5, 12, 2.5, 24, 104, 68), Part('arm_l', 2.5, 12, 2.5, 24, 144, 68),
         Part('leg_r', 2.5, 12, 2.5, 12, 184, 68), Part('leg_l', 2.5, 12, 2.5, 12, 0, 136)]}

def muscle_texture():
    im = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); px = im.load()
    for p in MUS:
        for j in range(p.H):
            y = p.y_of(j)
            for i in range(p.W):
                px[p.x0 + i, p.y0 + j] = col(body_paint(p.name, p.a_of(i), y)) + (255,)
        wk = int(p.w * K); dk = int(p.d * K)
        for top in (True, False):
            ox = p.u + dk + (0 if top else wk)
            for j in range(dk):
                for i in range(wk):
                    x = (i + .5) / K - p.w / 2; z = (j + .5) / K - p.d / 2
                    px[ox + i, p.v + j] = col(cap_paint(p.name, top, x, z, p.w, p.d)) + (255,)
    return im

# ---------- линии: сосуды и нервы ----------
def catmull(pts, step=0.03):
    """гладкая кривая через точки: список (x, y, t), t = доля длины"""
    if len(pts) == 2:
        (x0, y0), (x1, y1) = pts
        n = max(2, int(math.hypot(x1 - x0, y1 - y0) / step))
        return [(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, i / n) for i in range(n + 1)]
    P = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        n = max(2, int(math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / step))
        for j in range(n + (1 if i == len(P) - 3 else 0)):
            t = j / n; t2 = t * t; t3 = t2 * t
            out.append(tuple(.5 * ((2 * p1[c]) + (-p0[c] + p2[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2 + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t3) for c in (0, 1)))
    L = [0.0]
    for i in range(1, len(out)):
        L.append(L[-1] + math.hypot(out[i][0] - out[i - 1][0], out[i][1] - out[i - 1][1]))
    tot = L[-1] or 1.0
    return [(x, y, l / tot) for (x, y), l in zip(out, L)]

BRUSH = {
    2: [(0, 0, 1), (1, 0, 1), (0, 1, 1), (1, 1, 1)],
    3: [(0, 0, 0), (-1, 0, 1), (1, 0, 1), (0, -1, 1), (0, 1, 1)],
    4: [(0, 0, 0), (1, 0, 0), (0, 1, 0), (1, 1, 0), (0, -1, 3), (1, -1, 3), (0, 2, 3), (1, 2, 3), (-1, 0, 3), (-1, 1, 3), (2, 0, 3), (2, 1, 3)],
}
# ранги тона: 0 блик, 1 основной, 2 тонкая ветвь, 3 тень по краю ствола

class Lines:
    """один слой линий (класс цвета) на атласе оболочек"""
    def __init__(s):
        s.px = {}            # (часть, i, j) -> ранг
    def put(s, part, i, j, rank):
        p = SHELL[part]
        i %= p.W
        if 0 <= j < p.H:
            k = (part, i, j)
            if rank < s.px.get(k, 9):
                s.px[k] = rank
    def path(s, part, pts, w0=1, w1=None, flip=False):
        """линия по точкам (a, y); толщина от w0 до w1 текселей"""
        p = SHELL[part]
        if w1 is None:
            w1 = w0
        if flip:
            pts = [(-a, y) for a, y in pts]
        thin = []
        for a, y, t in catmull(pts):
            w = int(round(w0 + (w1 - w0) * t))
            X = p.X(a); Y = p.Y(y)
            if w <= 1:
                q = (int(math.floor(X)), int(math.floor(Y)))
                if not thin or thin[-1] != q:
                    thin.append(q)
            else:
                if thin:
                    s._thin(part, thin); thin = []
                bx = int(math.floor(X - (.5 if w in (2, 4) else 0))); by = int(math.floor(Y - (.5 if w in (2, 4) else 0)))
                for dx, dy, r in BRUSH[w]:
                    s.put(part, bx + dx, by + dy, r)
        if thin:
            s._thin(part, thin)
    def _thin(s, part, q):
        # без «уголков»: убрать пиксель, если соседи по пути касаются по диагонали
        i = 1
        while i < len(q) - 1:
            (x0, y0), (x1, y1), (x2, y2) = q[i - 1], q[i], q[i + 1]
            if abs(x2 - x0) == 1 and abs(y2 - y0) == 1 and (x1 == x0 or x1 == x2) and (y1 == y0 or y1 == y2):
                del q[i]
            else:
                i += 1
        for x, y in q:
            s.put(part, x, y, 2)

def tangent(pts, t):
    c = catmull(pts)
    i = min(len(c) - 2, max(0, int(t * (len(c) - 1))))
    dx, dy = c[i + 1][0] - c[i][0], c[i + 1][1] - c[i][1]
    l = math.hypot(dx, dy) or 1.0
    return c[i][0], c[i][1], dx / l, dy / l

def twigs(L, part, pts, n, rnd, length=(.5, 1.0), t0=.2, t1=.92, flip=False, fork=.35, sides=(1, -1)):
    """короткие ветви в стороны от линии: отходят под углом и загибаются вдоль ствола"""
    for k in range(n):
        t = t0 + (t1 - t0) * (k + rnd.uniform(.2, .8)) / n
        x, y, dx, dy = tangent(pts, t)
        sd = sides[k % len(sides)]
        ang = math.atan2(dy, dx) + sd * rnd.uniform(.6, 1.05)
        ln = rnd.uniform(*length)
        mx, my = x + math.cos(ang) * ln * .55, y + math.sin(ang) * ln * .55
        a2 = ang - sd * rnd.uniform(.3, .6)
        ex, ey = mx + math.cos(a2) * ln * .5, my + math.sin(a2) * ln * .5
        tw = [(x, y), (mx, my), (ex, ey)]
        L.path(part, tw, 1, flip=flip)
        if rnd.random() < fork:
            a3 = a2 + sd * rnd.uniform(.7, 1.1)
            L.path(part, [(mx, my), (mx + math.cos(a3) * ln * .45, my + math.sin(a3) * ln * .45)], 1, flip=flip)

def fork(L, part, pts, rnd, n=2, length=(.45, .8), spread=.55, flip=False):
    """окончание: линия расходится на n тонких веточек"""
    _, _, dx, dy = tangent(pts, 1.0)
    x, y = pts[-1]; base = math.atan2(dy, dx)
    for k in range(n):
        ang = base + (spread * (2 * k / (n - 1) - 1) if n > 1 else 0.0) + rnd.uniform(-.12, .12)
        ln = rnd.uniform(*length)
        L.path(part, [(x, y), (x + math.cos(ang) * ln * .5, y + math.sin(ang) * ln * .5),
                      (x + math.cos(ang + rnd.uniform(-.3, .3)) * ln, y + math.sin(ang + rnd.uniform(-.3, .3)) * ln)], 1, flip=flip)

def tree(L, part, root, ang, length, depth, rnd, w=2, spread=.62, shrink=.72, flip=False, box=None):
    """ветвящееся дерево: ствол, на конце две ветви, по пути боковая"""
    x, y = root
    bend = rnd.uniform(-.35, .35)
    mx, my = x + math.cos(ang) * length * .5, y + math.sin(ang) * length * .5
    a2 = ang + bend
    ex, ey = mx + math.cos(a2) * length * .5, my + math.sin(a2) * length * .5
    if box and not (box[0] <= ex <= box[2] and box[1] <= ey <= box[3]):
        ex = min(max(ex, box[0]), box[2]); ey = min(max(ey, box[1]), box[3]); depth = 0
    L.path(part, [(x, y), (mx, my), (ex, ey)], w, max(1, w - 1), flip=flip)
    if depth <= 0:
        return
    for sd in (1, -1):
        tree(L, part, (ex, ey), a2 + sd * spread * rnd.uniform(.7, 1.25), length * shrink * rnd.uniform(.85, 1.1), depth - 1, rnd,
             max(1, w - 1), spread, shrink, flip, box)
    if depth >= 2:
        sd = rnd.choice((1, -1))
        tree(L, part, (mx, my), ang + sd * rnd.uniform(.8, 1.2), length * .5, 0, rnd, 1, spread, shrink, flip, box)

def both(fn):
    """для симметричных частей: нарисовать и зеркально"""
    fn(False); fn(True)

LIMB = {'arm': ('arm_r', 'arm_l'), 'leg': ('leg_r', 'leg_l')}

# ---------- нервы ----------
def nerves():
    L = Lines()
    # --- туловище ---
    L.path('body', [(13, 24.25), (13, 16.5)], 4); L.path('body', [(13, 16.5), (13, 14.0)], 4, 2)   # спинной мозг: на позвоночнике, до таза
    def torso(fl):
        rnd = random.Random(7)
        # межрёберные: короткий толстый корешок у позвоночника, дальше тонкая нить вокруг к груди
        for y0, sag, end in ((23.3, 1.0, 1.6), (22.0, 1.15, 1.3), (20.8, .9, 1.7), (19.5, 1.1, 1.2), (18.2, 1.0, 1.5)):
            pts = [(13, y0), (12.0, y0 - .12 * sag), (10.6, y0 - .35 * sag), (8.75, y0 - .7 * sag), (6.5, y0 - 1.2 * sag), (4.25, y0 - 1.65 * sag), (2.8, y0 - 1.95 * sag), (end, y0 - 2.3 * sag)]
            L.path('body', pts[:2], 3, 2, flip=fl); L.path('body', pts[1:3], 2, 1, flip=fl); L.path('body', pts[2:], 1, flip=fl)
        # плечевое сплетение: толстые пучки от шеи к плечу, спереди и сзади
        for pts, w, w1 in (([(0.7, 24.25), (1.6, 23.7), (3.0, 23.2), (4.25, 22.9), (5.4, 22.75), (6.5, 22.6)], 3, 2),
                           ([(1.0, 24.25), (2.0, 23.3), (3.2, 22.6), (4.25, 22.3), (5.4, 22.4), (6.5, 22.6)], 2, 1),
                           ([(13, 23.9), (11.6, 23.8), (10.0, 23.5), (8.75, 23.1), (7.6, 22.8), (6.5, 22.6)], 3, 2),
                           ([(13, 23.3), (11.4, 23.0), (9.8, 22.7), (8.75, 22.4), (7.6, 22.5), (6.5, 22.6)], 2, 1)):
            L.path('body', pts, w, w1, flip=fl)
        # поясничное и крестцовое сплетения лежат в глубине: они сделаны объёмом, см. inner()
    both(torso)
    # --- голова ---
    L.path('head', [(17, 23.75), (17, 25.6)], 4); L.path('head', [(17, 25.6), (17, 27.2)], 3, 2)
    def head(fl):
        rnd = random.Random(11)
        O = (9.9, 26.5)
        L.path('head', [(17, 25.0), (15.0, 25.3), (12.75, 25.8), (11.2, 26.3), O], 3, 2, flip=fl)     # ствол от мозга к уху
        for pts, n in (([O, (8.6, 28.0), (6.9, 29.6), (5.0, 30.6), (3.4, 31.1)], 2),                  # веер лицевого нерва
                       ([O, (8.0, 27.4), (6.0, 28.1), (4.25, 28.4), (3.5, 28.5)], 2),
                       ([O, (7.8, 26.5), (5.8, 26.2), (4.25, 26.4), (3.4, 26.5)], 2),
                       ([O, (8.0, 25.6), (6.0, 24.9), (4.25, 24.7), (3.2, 24.5)], 2),
                       ([O, (9.4, 25.2), (8.8, 23.75)], 0),
                       ([O, (10.9, 27.3), (11.8, 28.8), (12.1, 30.2)], 2)):
            L.path('head', pts, 2, 1, flip=fl)
            if n:
                fork(L, 'head', pts, rnd, n=n, flip=fl, length=(.5, .9))
        for dx, dy in ((0, 0), (-.25, 0), (0, .25), (-.25, .25)):                 # узел у уха
            L.path('head', [(O[0] + dx, O[1] + dy), (O[0] + dx + .01, O[1] + dy)], 2, flip=fl)
        so = [(2.0, 28.4), (1.8, 29.7), (1.6, 31.0)]                              # надглазничный
        L.path('head', so, 1, flip=fl); fork(L, 'head', so, rnd, flip=fl, length=(.5, .8))
        oc = [(16.3, 24.2), (16.0, 26.0), (15.4, 28.0), (15.0, 30.0)]             # затылочные
        L.path('head', oc, 2, 1, flip=fl); fork(L, 'head', oc, rnd, n=3, flip=fl, length=(.8, 1.4), spread=.7)
        o2 = [(15.7, 26.6), (14.7, 28.2), (14.0, 29.8)]
        L.path('head', o2, 1, flip=fl); fork(L, 'head', o2, rnd, flip=fl, length=(.6, 1.0))
    both(head)
    # --- руки и ноги ---
    for side, fl in ((0, False), (1, True)):
        arm = LIMB['arm'][side]; leg = LIMB['leg'][side]
        rnd = random.Random(21)
        med = [(0.6, 24), (0.4, 22), (0.0, 19.5), (-0.2, 17.6), (0.1, 15.5), (0.0, 13.6)]
        rad = [(4.2, 24), (5.0, 22.6), (5.9, 21.2), (6.9, 19.6), (7.6, 18.0), (8.2, 16.2), (8.4, 13.8)]
        uln = [(2.4, 24), (2.6, 21.5), (3.3, 18.6), (3.0, 16), (2.5, 13.6)]
        for pts in (med, rad, uln):
            L.path(arm, pts[:2], 2, flip=fl); L.path(arm, pts[1:3], 2, 1, flip=fl); L.path(arm, pts[2:], 1, flip=fl)
            twigs(L, arm, pts, 1, rnd, flip=fl, length=(.6, 1.0), t0=.35, t1=.7, fork=0)
            fork(L, arm, pts, rnd, n=3, flip=fl, length=(.9, 1.4), spread=.45)                       # пальцевые ветви
        mc = [(0.2, 23.4), (-0.7, 21.5), (-1.2, 19.5), (-1.0, 17.8)]
        L.path(arm, mc, 1, flip=fl); fork(L, arm, mc, rnd, flip=fl)
        # нога: седалищный сзади, делится над коленом
        L.path(leg, [(5.0, 12), (4.9, 10), (5.0, 8.0), (5.0, 7.0)], 3, 2, flip=fl)
        tib = [(5.0, 7.0), (4.8, 5.5), (4.6, 3.5), (4.1, 1.8), (3.5, 0.9)]
        per = [(5.0, 7.0), (5.6, 6.3), (6.6, 5.6), (7.8, 5.0), (8.6, 4.0), (9.2, 2.6), (9.5, 1.3)]
        fem = [(-0.4, 12), (-0.35, 10.6), (-0.2, 9.0), (-0.3, 7.4)]
        sap = [(-0.3, 11.5), (0.7, 10.4), (1.7, 9.2), (2.4, 7.4), (2.8, 5.2), (2.6, 3.0), (2.2, 1.4)]
        lat = [(-2.2, 12), (-2.7, 10.4), (-2.5, 8.6), (-2.9, 6.8)]
        for pts, w in ((tib, 2), (per, 2), (fem, 2), (sap, 1), (lat, 1)):
            L.path(leg, pts, w, 1, flip=fl)
            twigs(L, leg, pts, 1, rnd, flip=fl, length=(.6, 1.0), t0=.35, t1=.7, fork=0)
            fork(L, leg, pts, rnd, n=3 if w == 2 else 2, flip=fl, length=(.6, 1.1))
        fb = [(-0.35, 10.8), (0.4, 9.9), (0.9, 8.6)]
        L.path(leg, fb, 1, flip=fl); fork(L, leg, fb, rnd, flip=fl)
    return L

# ---------- сосуды ----------
def vessels():
    A = Lines(); V = Lines()
    rnd = random.Random(5)
    # --- туловище: на оболочке только межрёберные (идут по рёбрам, в стенке груди); стволы лежат внутри, см. inner() ---
    TH = [(13, 0.0), (12.0, .12), (10.6, .35), (8.75, .7), (6.5, 1.2), (4.25, 1.65), (2.8, 1.95)]    # провис нервной нити вокруг туловища
    def sag_at(a):
        for (a1, k1), (a2, k2) in zip(TH, TH[1:]):
            if a2 <= a <= a1:
                return k1 + (k2 - k1) * (a1 - a) / (a1 - a2)
        return TH[-1][1]
    for sg in (1, -1):
        # три пары между нервными нитями: выходят у позвоночника, огибают бок и кончаются вилкой
        for y0, sag, end in ((23.3, 1.0, 3.0), (20.8, .9, 5.2), (18.2, 1.0, 6.8)):
            aa = [12.1] + [a for a in (10.6, 8.75, 6.5, 4.25) if a > end + .4] + [end]
            pa = [(sg * a, y0 - .4 - sag * sag_at(a)) for a in aa]
            A.path('body', pa, 1)
            V.path('body', [(x, y - .4) for x, y in pa[1:]], 1)
            fork(A, 'body', pa, rnd, length=(.5, .8))
    # --- голова ---
    def head(fl):
        r2 = random.Random(31)
        ta = [(8.0, 23.75), (8.2, 25.5), (7.9, 27.0), (8.1, 28.4)]
        A.path('head', ta, 3, 2, flip=fl)
        fr = [(8.1, 28.4), (6.8, 29.6), (5.2, 30.5), (4.25, 30.9), (3.2, 31.4)]
        pa = [(8.1, 28.4), (8.9, 29.8), (9.6, 31.2), (9.9, 32.25)]
        A.path('head', fr, 2, 1, flip=fl); twigs(A, 'head', fr, 1, r2, flip=fl, length=(.7, 1.1), fork=0); fork(A, 'head', fr, r2, flip=fl)
        A.path('head', pa, 2, 1, flip=fl); twigs(A, 'head', pa, 1, r2, flip=fl, length=(.7, 1.0), fork=0)
        fa = [(8.1, 25.0), (6.4, 24.6), (4.6, 24.9), (4.25, 25.2), (3.6, 25.9), (3.4, 26.6)]
        A.path('head', fa, 2, 1, flip=fl); fork(A, 'head', fa, r2, flip=fl, length=(.4, .7))
        oc = [(8.4, 25.3), (10.5, 25.4), (12.75, 26.2), (14.2, 27.6), (15.2, 29.4), (15.6, 31.2)]
        A.path('head', oc, 2, 1, flip=fl); twigs(A, 'head', oc, 1, r2, flip=fl, length=(.7, 1.0), t0=.5, fork=0); fork(A, 'head', oc, r2, flip=fl, length=(.6, 1.0))
        tv = [(8.9, 23.75), (9.0, 26.0), (8.8, 28.2), (9.7, 29.6), (10.6, 31.0), (10.8, 32.25)]
        V.path('head', tv, 2, 1, flip=fl); twigs(V, 'head', tv, 1, r2, flip=fl, length=(.7, 1.0), t0=.5, fork=0)
        fv = [(5.6, 23.75), (5.2, 24.8), (4.9, 26.0), (5.2, 27.2)]
        V.path('head', fv, 1, flip=fl)
        V.path('head', [(3.5, 28.9), (3.4, 30.2), (3.0, 31.6), (3.0, 32.25)], 1, flip=fl)
        A.path('head', [(1.5, 28.6), (1.3, 30.2), (1.0, 31.8)], 1, flip=fl)
        ov = [(13.4, 23.75), (13.6, 26.0), (14.6, 28.0), (15.0, 30.2), (16.2, 31.6)]
        V.path('head', ov, 1, flip=fl); fork(V, 'head', ov, r2, flip=fl, length=(.5, .9))
    both(head)
    # --- руки и ноги ---
    for side, fl in ((0, False), (1, True)):
        arm = LIMB['arm'][side]; leg = LIMB['leg'][side]
        r3 = random.Random(41)
        br = [(1.0, 24), (1.2, 21.0), (0.9, 18.6), (0.8, 18.0)]
        A.path(arm, br, 3, 2, flip=fl); twigs(A, arm, br, 1, r3, flip=fl, length=(.6, .9), fork=0)
        ra = [(0.8, 18.0), (-0.4, 16.5), (-0.9, 14.2), (-0.8, 13.2)]
        ul = [(0.8, 18.0), (1.5, 16.5), (1.9, 14.2), (1.7, 13.2)]
        A.path(arm, ra, 2, 1, flip=fl); A.path(arm, ul, 2, 1, flip=fl)
        A.path(arm, [(-0.8, 13.2), (0.4, 12.7), (1.7, 13.2)], 1, flip=fl)                  # ладонная дуга
        for x in (-0.5, 0.4, 1.3):
            A.path(arm, [(x, 12.8), (x, 12.0)], 1, flip=fl)
        dp = [(4.4, 23.4), (5.2, 21.0), (5.0, 18.6), (5.3, 16.0), (4.9, 13.6)]
        A.path(arm, dp, 2, 1, flip=fl); twigs(A, arm, dp, 1, r3, flip=fl, length=(.6, .9), fork=0); fork(A, arm, dp, r3, flip=fl)
        ce = [(-2.6, 24), (-2.9, 21.5), (-2.4, 18.5), (-1.9, 16.0), (-2.2, 13.6), (-2.5, 12.4)]
        V.path(arm, ce, 2, 1, flip=fl); twigs(V, arm, ce, 1, r3, flip=fl, length=(.6, .9), fork=0)
        bv = [(1.7, 24), (1.9, 22.3), (2.9, 20.5), (3.3, 18.6), (3.0, 17.5), (3.4, 15.0), (3.1, 13.4)]
        V.path(arm, bv, 2, 1, flip=fl); fork(V, arm, bv, r3, flip=fl)
        V.path(arm, [(-2.3, 18.3), (-1.0, 17.5), (0.8, 17.3), (3.0, 17.7)], 1, flip=fl)     # перемычка в локтевой ямке
        V.path(arm, [(-2.3, 13.5), (-1.6, 12.9), (-2.9, 12.5), (-3.4, 13.2)], 1, flip=fl)   # сеть на тыле кисти
        # нога
        fa = [(0.5, 12), (0.7, 10.0), (1.2, 8.2), (2.4, 7.0), (3.9, 6.2), (4.9, 5.6)]
        A.path(leg, fa[:3], 4, 3, flip=fl); A.path(leg, fa[2:], 3, 2, flip=fl); twigs(A, leg, fa, 1, r3, flip=fl, length=(.6, 1.0), t1=.5, fork=0)
        pt = [(4.9, 5.6), (4.6, 4.5), (4.1, 2.5), (3.6, 1.0), (3.2, 0.2)]
        at = [(4.9, 5.6), (6.4, 4.9), (8.2, 4.0), (9.6, 2.5), (10.0, 1.1)]
        A.path(leg, pt, 2, 1, flip=fl); twigs(A, leg, pt, 1, r3, flip=fl, length=(.6, .9), fork=0)
        A.path(leg, at, 2, 1, flip=fl); twigs(A, leg, at, 1, r3, flip=fl, length=(.6, .9), fork=0)
        A.path(leg, [(9.3, 0.9), (10.0, 0.6), (10.7, 0.9)], 1, flip=fl)                    # дуга стопы
        for x in (9.4, 10.0, 10.6):
            A.path(leg, [(x, 0.7), (x, 0.0)], 1, flip=fl)
        df = [(0.7, 10.6), (-0.5, 9.6), (-1.6, 8.4), (-2.4, 7.2), (-2.6, 6.0)]
        A.path(leg, df, 2, 1, flip=fl); twigs(A, leg, df, 1, r3, flip=fl, length=(.6, .9), fork=0); fork(A, leg, df, r3, flip=fl, n=3)
        fv = [(1.1, 12), (1.4, 10.0), (2.0, 8.3), (3.1, 7.3), (4.3, 6.5), (5.1, 6.0)]
        V.path(leg, fv[:3], 2, flip=fl); V.path(leg, fv[2:], 1, flip=fl)
        gs = [(1.5, 11.5), (2.5, 9.5), (2.9, 7.0), (2.7, 4.5), (2.3, 2.3), (1.7, 0.9), (1.2, 0.2)]
        V.path(leg, gs, 1, flip=fl); twigs(V, leg, gs, 2, r3, flip=fl, length=(.6, .9), fork=0)
        ss = [(5.1, 6.0), (5.3, 3.5), (5.7, 1.6), (6.2, 0.6)]
        V.path(leg, ss, 1, flip=fl); fork(V, leg, ss, r3, flip=fl)
        lv = [(-2.4, 11.6), (-2.8, 9.0), (-2.5, 6.4), (-2.9, 4.0)]
        V.path(leg, lv, 1, flip=fl); fork(V, leg, lv, r3, flip=fl)
    return A, V

NERVE = [(0xff, 0xf4, 0xa8), (0xff, 0xd8, 0x30), (0xf2, 0xbc, 0x18), (0xc4, 0x90, 0x14)]
ART = [(0xf4, 0x54, 0x46), (0xd6, 0x2a, 0x2e), (0xc6, 0x24, 0x2c), (0x8e, 0x16, 0x22)]
VEIN = [(0x5e, 0x94, 0xf2), (0x30, 0x66, 0xd6), (0x2c, 0x5a, 0xc4), (0x1c, 0x38, 0x88)]

# ---------- объём внутри туловища: глубокие сосуды и нервы трубками из вокселей ----------
VX = 4                       # вокселей на пиксель клона: как тексели оболочек
ART_C, VEIN_C, NERVE_C = 0, 1, 2

def norm3(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return (v[0] / l, v[1] / l, v[2] / l)

def cross3(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

def catmull3(pts, step=0.02):
    """гладкая кривая в объёме: список (x, y, z, t, касательная), t = доля длины"""
    P = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        n = max(2, int(math.dist(p1, p2) / step))
        for j in range(n + (1 if i == len(P) - 3 else 0)):
            u = j / n; u2 = u * u; u3 = u2 * u
            out.append(tuple(.5 * ((2 * p1[c]) + (-p0[c] + p2[c]) * u + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * u2
                                   + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * u3) for c in range(3)))
    L = [0.0]
    for i in range(1, len(out)):
        L.append(L[-1] + math.dist(out[i], out[i - 1]))
    tot = L[-1] or 1.0
    res = []
    for i, p in enumerate(out):
        tg = norm3(tuple(out[min(len(out) - 1, i + 1)][c] - out[max(0, i - 1)][c] for c in range(3)))
        res.append((p[0], p[1], p[2], L[i] / tot, tg))
    return res

# кисти: сечение трубки при протяжке совпадает с кистями линий на оболочках (4: квадрат без углов, 3: крест, 2: 2x2)
B3 = {1: [(0, 0, 0)],
      2: [(i, j, k) for i in (0, 1) for j in (0, 1) for k in (0, 1)],
      3: [(0, 0, 0), (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)],
      4: [(i, j, k) for i in (-1, 0, 1, 2) for j in (-1, 0, 1, 2) for k in (-1, 0, 1, 2) if sum(c in (-1, 2) for c in (i, j, k)) <= 1]}
DIRS = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
YLIM = (12 * VX, 24 * VX)    # воксели только в пределах коробки туловища

class Tubes:
    """один слой трубок: воксель -> (расстояние до оси, толщина, класс цвета, смещение от оси, касательная)"""
    def __init__(s):
        s.v = {}
    def path(s, cls, pts, w0, w1=None):
        if w1 is None:
            w1 = w0
        for x, y, z, tt, tg in catmull3(pts):
            w = int(round(w0 + (w1 - w0) * tt))
            X, Y, Z = x * VX, y * VX, z * VX
            if w in (2, 4):
                b = (math.floor(X - .5), math.floor(Y - .5), math.floor(Z - .5)); c = (b[0] + 1.0, b[1] + 1.0, b[2] + 1.0)
            else:
                b = (math.floor(X), math.floor(Y), math.floor(Z)); c = (b[0] + .5, b[1] + .5, b[2] + .5)
            for o in B3[w]:
                k = (b[0] + o[0], b[1] + o[1], b[2] + o[2])
                if not (YLIM[0] <= k[1] < YLIM[1]):
                    continue
                d2 = (k[0] + .5 - X) ** 2 + (k[1] + .5 - Y) ** 2 + (k[2] + .5 - Z) ** 2
                old = s.v.get(k)
                if old is None or w > old[1] or (w == old[1] and d2 < old[0]):
                    s.v[k] = (d2, w, cls, (k[0] + .5 - c[0], k[1] + .5 - c[1], k[2] + .5 - c[2]), tg)
    def quads(s):
        """видимые грани, склеенные в прямоугольники: [dir, p, a0, b0, la, lb, цвет]"""
        faces = {}
        for (i, j, k), (d2, w, cls, d, tg) in s.v.items():
            for di, n in enumerate(DIRS):
                if (i + n[0], j + n[1], k + n[2]) in s.v:
                    continue
                c = cls * 4 + face_rank(w, d, tg, n)
                if di < 2:
                    p = i + (1 if di == 0 else 0); ab = (j, k)
                elif di < 4:
                    p = j + (1 if di == 2 else 0); ab = (i, k)
                else:
                    p = k + (1 if di == 4 else 0); ab = (i, j)
                faces.setdefault((di, p, c), set()).add(ab)
        out = []
        for (di, p, c), cells in sorted(faces.items()):
            runs = {}
            for a in sorted({q[0] for q in cells}):
                bs = sorted(q[1] for q in cells if q[0] == a)
                st = pr = bs[0]
                for bb in bs[1:] + [None]:
                    if bb is None or bb != pr + 1:
                        runs.setdefault((st, pr - st + 1), []).append(a)
                        st = bb
                    pr = bb
            for (b0, lb), aa in sorted(runs.items()):
                st = pr = aa[0]
                for a in aa[1:] + [None]:
                    if a is None or a != pr + 1:
                        out.append([di, p, st, b0, pr - st + 1, lb, c])
                        st = a
                    pr = a
        return out

def face_rank(w, d, tg, n):
    """тон грани как у линий на оболочках: поперёк толстого ствола тень, блик, блик, тень"""
    if w == 1:
        return 2
    if w == 2:
        return 1
    e = cross3(tg, n)
    l = math.sqrt(e[0] ** 2 + e[1] ** 2 + e[2] ** 2)
    if l < .35:
        return 1                      # торец или ступенька
    sd = abs(d[0] * e[0] + d[1] * e[1] + d[2] * e[2]) / l
    if w == 4:
        return 0 if sd < 1.0 else 3
    return 0 if sd < .5 else 1

def tree3(T, cls, root, dirv, length, depth, rnd, w=2, spread=.7, shrink=.72, box=None):
    """ветвящееся дерево в объёме; ветвится в основном в плоскости груди"""
    d = norm3(dirv)
    mid = tuple(root[c] + d[c] * length * .5 for c in range(3))
    d2 = norm3(tuple(d[c] + rnd.uniform(-.3, .3) for c in range(3)))
    end = tuple(mid[c] + d2[c] * length * .5 for c in range(3))
    if box:
        cl = tuple(min(max(end[c], box[c][0]), box[c][1]) for c in range(3))
        if cl != end:
            end = cl; depth = 0
    T.path(cls, [root, mid, end], w, max(1, w - 1))
    if depth <= 0:
        return
    perp = norm3(cross3(d2, norm3((rnd.uniform(-.4, .4), rnd.uniform(-.4, .4), rnd.choice((1, -1))))))
    for sd in (1, -1):
        k = spread * rnd.uniform(.75, 1.2) * sd
        nd = norm3(tuple(d2[c] * math.cos(k) + perp[c] * math.sin(k) for c in range(3)))
        tree3(T, cls, end, nd, length * shrink * rnd.uniform(.85, 1.1), depth - 1, rnd, max(1, w - 1), spread, shrink, box)

def fork3(T, cls, end, dirv, rnd, n=3, length=(.4, .7), spread=.8):
    """кустик тонких веточек на конце"""
    d = norm3(dirv)
    for k in range(n):
        perp = norm3(cross3(d, norm3((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1)))))
        ang = spread * rnd.uniform(.45, 1.0)
        nd = norm3(tuple(d[c] * math.cos(ang) + perp[c] * math.sin(ang) for c in range(3)))
        ln = rnd.uniform(*length)
        T.path(cls, [end, tuple(end[c] + nd[c] * ln for c in range(3))], 1)

def inner():
    """глубокие стволы туловища в координатах клона: x (плюс = левая сторона клона), y (высота), z (плюс = перёд).
    Коробка туловища: x -4..4, y 12..24, z -2..2; сердце в сцене: центр (1.2, 18.4, -0.2)."""
    TV = Tubes(); TN = Tubes(); A, V, N = ART_C, VEIN_C, NERVE_C
    rnd = random.Random(17)
    # аорта: из сердца вверх, дуга назад к позвоночнику, вниз слева от середины; ниже почек тоньше
    TV.path(A, [(1.5, 19.9, 0.2), (1.5, 21.2, 0.2), (1.15, 22.0, -0.3), (0.7, 22.15, -0.9), (0.5, 21.4, -1.25), (0.5, 19.0, -1.25), (0.5, 16.9, -1.25)], 4)
    TV.path(A, [(0.5, 16.9, -1.25), (0.5, 14.6, -1.25)], 3)
    # полые вены: нижняя справа от позвоночника до сердца, верхняя от сердца к шее
    TV.path(V, [(-0.625, 14.1, -1.125), (-0.625, 18.0, -1.125), (-0.35, 19.2, -0.85), (0.3, 19.9, -0.4)], 3)
    TV.path(V, [(0.5, 20.3, -0.2), (0.0, 21.2, -0.2), (-0.5, 22.0, -0.2), (-0.625, 22.6, -0.2)], 3)
    # ветви дуги: плечеголовной ствол, сонные в основание черепа, подключичные к рукам
    TV.path(A, [(0.75, 22.2, -0.9), (0.1, 22.75, -0.95), (-0.6, 23.1, -0.85)], 3, 2)
    TV.path(A, [(-0.6, 23.1, -0.85), (-1.0, 23.5, -0.7), (-1.05, 24.0, -0.6)], 2)
    TV.path(A, [(-0.6, 23.1, -0.85), (-1.8, 22.8, -0.5), (-3.0, 22.65, 0.3), (-3.75, 22.6, 0.95)], 2)
    TV.path(A, [(1.0, 22.1, -0.45), (1.1, 23.0, -0.55), (1.05, 24.0, -0.6)], 2)
    TV.path(A, [(0.85, 22.15, -0.75), (2.0, 22.5, -0.4), (3.0, 22.65, 0.3), (3.75, 22.6, 0.95)], 2)
    # яремные и подключичные вены: тоньше артерий, лежат спереди от них
    TV.path(V, [(-0.625, 22.6, -0.2), (-1.5, 23.2, 0.0), (-1.9, 24.0, 0.0)], 2, 1)
    TV.path(V, [(-0.625, 22.6, -0.2), (0.4, 22.95, 0.3), (1.5, 23.35, 0.3), (1.9, 24.0, 0.0)], 2, 1)
    TV.path(V, [(-1.2, 23.0, -0.05), (-2.4, 22.4, 0.45), (-3.75, 22.05, 0.6)], 1)
    TV.path(V, [(1.3, 23.3, 0.3), (2.5, 22.45, 0.5), (3.75, 22.05, 0.6)], 1)
    # лёгочные: ветвистые деревья в грудной клетке (артерии синие, вены красные, как в атласах)
    lung_r = ((-3.6, -1.0), (19.1, 23.0), (-1.5, 1.4)); lung_l = ((2.7, 3.6), (17.6, 23.0), (-1.5, 1.4))
    pr = [(0.9, 20.2, 0.6), (-0.2, 20.7, 0.6), (-1.4, 20.9, 0.4)]
    TV.path(V, pr, 2); tree3(TV, V, pr[-1], (-1, .3, -.2), 1.5, 2, rnd, w=2, box=lung_r)
    pl = [(1.2, 20.2, 1.0), (2.2, 20.8, 1.0), (2.9, 21.3, 0.7)]
    TV.path(V, pl, 2); tree3(TV, V, pl[-1], (.5, 1, -.3), 1.1, 1, rnd, w=2, box=lung_l)
    vr = [(0.1, 19.5, 0.5), (-0.9, 19.6, 0.5), (-1.6, 19.4, 0.2)]
    TV.path(A, vr, 2); tree3(TV, A, vr[-1], (-1, -.2, -.3), 1.4, 2, rnd, w=2, box=lung_r)
    vl = [(2.5, 19.0, -0.3), (3.0, 18.9, -0.4)]
    TV.path(A, vl, 2); tree3(TV, A, vl[-1], (.4, -.6, .5), 1.0, 1, rnd, w=1, box=lung_l)
    # почечные: левая артерия прямо, правая позади нижней полой вены; левая вена спереди от аорты
    ra = [(0.75, 16.4, -1.0), (1.6, 16.3, -0.95), (2.5, 16.0, -0.8)]
    TV.path(A, ra, 2, 1); fork3(TV, A, ra[-1], (1, -.3, .2), rnd)
    TV.path(A, [(0.2, 16.4, -1.6), (-0.6, 16.35, -1.625), (-1.05, 16.3, -1.55), (-1.2, 16.25, -1.1)], 1)
    rb = [(-1.2, 16.25, -1.0), (-1.9, 16.1, -0.9), (-2.5, 15.9, -0.8)]
    TV.path(A, rb, 2, 1); fork3(TV, A, rb[-1], (-1, -.3, .2), rnd)
    lv = [(-0.4, 16.0, -0.7), (0.2, 15.95, -0.625), (1.3, 15.85, -0.6), (2.4, 15.7, -0.7)]
    TV.path(V, lv, 1); fork3(TV, V, lv[-1], (1, -.2, .3), rnd, n=2)
    rv = [(-1.0, 16.0, -1.1), (-1.7, 15.85, -0.9), (-2.4, 15.7, -0.7)]
    TV.path(V, rv, 1); fork3(TV, V, rv[-1], (-1, -.2, .3), rnd, n=2)
    # подвздошные: развилка у верхнего края таза, дальше к бёдрам за костью; правая артерия проходит спереди от вен
    TV.path(A, [(0.5, 14.6, -1.25), (1.0, 14.1, -1.1), (1.4, 13.5, -0.6), (1.5, 12.9, -0.1), (1.5, 12.0, 0.9)], 3)
    TV.path(A, [(0.5, 14.6, -1.25), (0.0, 14.3, -0.7), (-0.7, 14.05, -0.5), (-1.2, 13.5, -0.3), (-1.5, 12.8, 0.2), (-1.5, 12.0, 0.9)], 3)
    TV.path(V, [(-0.625, 14.1, -1.125), (-0.9, 13.5, -1.0), (-1.1, 12.8, -0.5), (-0.85, 12.0, 0.9)], 2)
    TV.path(V, [(-0.625, 14.1, -1.125), (0.2, 13.8, -1.5), (0.9, 13.1, -1.4), (1.1, 12.6, -0.6), (0.85, 12.0, 0.9)], 2)
    # нервы: поясничное и крестцовое сплетения выходят из позвоночника и уходят к ногам
    for sg in (1, -1):
        TN.path(N, [(sg * 1.5, 16.0, -1.5), (sg * 1.75, 15.0, -1.3), (sg * 2.15, 13.8, -0.4), (sg * 2.35, 12.0, 0.95)], 2, 1)   # бедренный
        TN.path(N, [(sg * 1.5, 15.0, -1.5), (sg * 1.5, 14.2, -1.5), (sg * 1.9, 13.0, -1.4), (sg * 2.0, 12.0, -1.1)], 2)         # седалищный
        TN.path(N, [(sg * 1.5, 17.4, -1.5), (sg * 2.4, 16.9, -1.2), (sg * 3.2, 15.6, -1.0), (sg * 3.5, 13.4, -0.2)], 1)         # наружный кожный
    return TV, TN

def atlas(layers):
    """layers: [(Lines, палитра)] снизу вверх"""
    im = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); px = im.load()
    for L, pal in layers:
        for (part, i, j), r in L.px.items():
            p = SHELL[part]
            px[p.x0 + i, p.y0 + j] = pal[r] + (255,)
    return im

def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(OUT_JSON, exist_ok=True)
    mus = muscle_texture(); mus.save(os.path.join(OUT, 'clone_muscle.png'))
    N = nerves(); atlas([(N, NERVE)]).save(os.path.join(OUT, 'clone_nerves.png'))
    A, V = vessels(); atlas([(V, VEIN), (A, ART)]).save(os.path.join(OUT, 'clone_vessels.png'))
    TV, TN = inner()
    pal = Image.new('RGBA', (16, 1), (0, 0, 0, 0))
    for i, c in enumerate(ART + VEIN + NERVE):
        pal.putpixel((i, 0), c + (255,))
    pal.save(os.path.join(OUT, 'clone_inner_palette.png'))
    qv, qn = TV.quads(), TN.quads()
    json.dump({'vox': VX, 'vessels': qv, 'nerves': qn}, open(os.path.join(OUT_JSON, 'clone_inner.json'), 'w'), separators=(',', ':'))
    both_ = set(TV.v) & set(TN.v)
    lim = [(min(k[c] for k in T.v) / VX, (max(k[c] for k in T.v) + 1) / VX) for T in (TV, TN) for c in range(3)]
    print('ok', len(N.px), len(A.px), len(V.px), '| воксели', len(TV.v), len(TN.v), 'грани', len(qv), len(qn), 'пересечений слоёв', len(both_))
    print('границы сосудов', lim[:3], 'нервов', lim[3:])

if __name__ == '__main__':
    import sys
    if len(sys.argv) > 1:
        OUT = OUT_JSON = sys.argv[1]
    main()
