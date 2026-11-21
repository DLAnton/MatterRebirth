# Ядро в HUD: перелив между своими цветами и цветом состояния машины (идея Антона 16.11).
# Цвета состояний — те же, что у колец машины (этап 5а). Меняются только светящиеся слои
# (бирюза и искры), фиолетовая оболочка и тёмная глубина остаются — ядро узнаётся всегда.
import math, sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_core as g
from PIL import Image, ImageDraw

def pal(c):  # 3 светящихся оттенка от цвета состояния: тёмный, основной, яркий
    return [g.lerp(c, (0, 0, 0), 0.25), c, g.lerp(c, (255, 255, 255), 0.75)]

STATES = [
    ('Рост', None),                       # бирюза = свои цвета ядра
    ('Пауза', (225, 225, 235)),
    ('Диапауза / Нужно UU', (0xFF, 0xD8, 0x4A)),
    ('Откат', 'rollback'),                 # как кольца: перелив #FF0000 <-> #C05CD8
    ('Перегрузка / гибель', (0xFF, 0, 0)),
    ('Готов', (0x5A, 0xDC, 0x6E)),
]

import colorsys
def hlerp(a, b, t):
    # перелив по кругу оттенков, а не по прямой RGB: без серой «грязи» в середине.
    # Бирюза → красный идёт через фиолет/пурпур (родные цвета ядра), белый — просто высветление.
    ha, sa, va = colorsys.rgb_to_hsv(*(k / 255 for k in a))
    hb, sb, vb = colorsys.rgb_to_hsv(*(k / 255 for k in b))
    if sb < 0.15:
        hb = ha
    if 0.1 < hb < 0.2:  # к жёлтому — через светлый тон, без зелени (её путают с «Готов», Антон 16.11)
        mid = (250, 246, 225)
        return g.lerp(a, mid, t * 2) if t < 0.5 else g.lerp(mid, b, t * 2 - 1)
    d = hb - ha
    if d > 0.5: d -= 1
    if d < -0.5: d += 1
    if abs(abs(d) - 0.5) < 0.08 or (hb < 0.05 or hb > 0.9):  # к красному — через пурпур
        d = (hb - ha) % 1.0
    h = (ha + d * t) % 1.0
    r = colorsys.hsv_to_rgb(h, sa + (sb - sa) * t, va + (vb - va) * t)
    return tuple(int(round(k * 255)) for k in r)

def frame(i, col, off=False):
    im = g.frame(i)
    if off:
        return im
    if col is None:
        return im
    t = i / g.N * 2 * math.pi
    a = 0.5 - 0.5 * math.cos(t)        # плавно туда и обратно
    a = 0.35 + 0.65 * a                # цвет состояния держится почти всегда
    if col == 'rollback':
        A, B = pal((0xC0, 0x5C, 0xD8)), pal((0xFF, 0, 0))
    elif col == (0xFF, 0, 0):
        A, B = pal((0x90, 0, 0)), pal((0xFF, 0, 0))   # красный, мягко дышит яркостью
    else:
        A = None
    P = pal(col) if A is None else None
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if c[3] == 0: continue
            rgb = c[:3]
            for k, src in enumerate(g.INNER[3:]):
                if rgb == src:
                    if A:  # откат и перегрузка: только свои цвета, без бирюзы и синего
                        px[x, y] = g.lerp(A[k], B[k], 0.5 - 0.5 * math.cos(t)) + (255,)
                    else:
                        px[x, y] = hlerp(rgb, P[k], a) + (255,)
    return im

if __name__ == '__main__':
    COLS = 12
    W = 16 * COLS
    sheet = Image.new('RGBA', (W + 8, 16 * len(STATES) + 8 + 4 * (len(STATES) - 1)), (139, 139, 139, 255))
    for r, (name, col) in enumerate(STATES):
        for k in range(COLS):
            sheet.alpha_composite(frame(k * g.N // COLS, col), (4 + k * 16, 4 + r * 20))
    sheet = sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST)
    lab = Image.new('RGBA', (sheet.width + 300, sheet.height), (139, 139, 139, 255))
    lab.alpha_composite(sheet, (300, 0))
    d = ImageDraw.Draw(lab)
    try:
        from PIL import ImageFont
        f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 24)
    except Exception:
        f = None
    for r, (name, col) in enumerate(STATES):
        d.text((12, 24 + 6 * (4 + r * 20) + 10), name, fill=(20, 20, 20), font=f)
    lab.save(g.OUT + 'core_states.png')

    def gif(name, scale=12):
        fr = []
        for i in range(g.N):
            f2 = Image.new('RGBA', (16 * len(STATES) + 4 * (len(STATES) + 1), 24), (139, 139, 139, 255))
            for r, (_, col) in enumerate(STATES):
                f2.alpha_composite(frame(i, col), (4 + r * 20, 4))
            fr.append(f2.resize((f2.width * scale, f2.height * scale), Image.NEAREST).convert('P', palette=Image.ADAPTIVE))
        fr[0].save(g.OUT + name, save_all=True, append_images=fr[1:], duration=50, loop=0, disposal=2)
    gif('core_states.gif')
    print('ok')
