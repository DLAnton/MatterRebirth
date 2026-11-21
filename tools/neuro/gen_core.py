# Ядро (резонирующий кристалл), иконка v3 (Антон 16.11): развитие ПРЕЖНЕЙ иконки — хаотичная внутренняя структура
# (бирюза, фиолет, красно-бурый) в стеклянной фиолетовой сфере; ориентир — орбы пиксельных игр и старых игр SEGA:
# тёмный контур, яркий блик сверху-слева, отражённый свет снизу-справа, внутри клубится «энергия».
# Меньше прежней (сфера 12 px), насыщеннее. Анимация: внутренность медленно закручивается вихрем (центр быстрее края),
# цикл бесшовный. Тревога: всё ядро уходит в красный и обратно.
import math
import os
from PIL import Image

N = 64  # больше кадров — плавнее (Антон 16.11)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out', '')  # листы и gif
SCR = OUT
OUTLINE = (18, 6, 30)
SHELL = [(46, 14, 84), (72, 24, 128), (104, 40, 178)]          # стекло оболочки: тень, середина, свет
RIM = (150, 90, 235)                                              # отражённый свет снизу-справа
INNER = [(38, 10, 70), (96, 22, 120), (176, 52, 72), (0, 168, 178), (70, 240, 228), (210, 255, 248)]
#        глубина       фиолет        красно-бурый   бирюза          яркая бирюза     искра
RED = [(50, 4, 10), (110, 10, 22), (170, 20, 34), (220, 40, 52), (255, 90, 90), (255, 200, 200)]
MAG = [(50, 8, 60), (110, 20, 110), (170, 40, 120), (190, 50, 170), (235, 100, 220), (255, 200, 245)]
PINK = (255, 200, 222)
R, RI = 6.0, 4.1


def h(ix, iy):
    n = (ix * 374761393 + iy * 668265263) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535


def noise(x, y):
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = h(ix, iy), h(ix + 1, iy), h(ix, iy + 1), h(ix + 1, iy + 1)
    return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy


def field(x, y):
    return 0.8 * noise(x, y) + 0.2 * noise(x * 2.1 + 7.3, y * 2.1 + 3.1)


def sat(c, smin=0.65):
    import colorsys
    h_, s_, v_ = colorsys.rgb_to_hsv(*(k / 255 for k in c))
    return tuple(int(k * 255) for k in colorsys.hsv_to_rgb(h_, max(s_, smin), v_))


def lerp(a, b, t):
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3))


def frame(i, alarm=False):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = im.load()
    t = i / N * 2 * math.pi
    al = 0.5 - 0.5 * math.cos(t) if alarm else 0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            d = math.hypot(dx, dy)
            if d > R:
                continue
            if d > R - 1:  # контур не чёрный (Антон 16.11): тёмно-фиолетовый, светлее со стороны света
                c = (92, 50, 150) if dx + dy < -3 else (62, 26, 108) if dx + dy < 3 else (44, 16, 78)
            elif d > RI:  # стекло оболочки
                lit = -dx - dy  # сверху-слева светлее
                c = SHELL[2] if lit > 4 else SHELL[1] if lit > -3 else SHELL[0]
                if dx + dy > 4.5 and d > R - 1.8:
                    c = RIM
            else:  # клубящаяся внутренность: вихрь, центр крутится быстрее края (бесшовно за цикл)
                r = d / RI
                ang = t + (1 - r) * 2 * math.pi * 0  # базовый поворот
                sw = t * (1 if True else 0) + (1 - r) * 1.6 * math.sin(t)
                ca, sa = math.cos(sw), math.sin(sw)
                u, v = dx * ca - dy * sa, dx * sa + dy * ca
                f = field(u * 0.34 + 10, v * 0.34 + 10)
                # ядро-сгусток: светится сильнее сверху-слева от центра (как на прежней иконке), к краю темнее
                nuc = math.exp(-((dx + 0.9) ** 2 + (dy + 0.9) ** 2) / 9.0)
                f = 0.62 * f + 0.6 * nuc + (-dx - dy) * 0.012 - r * 0.05
                k = 0 if f < 0.32 else 1 if f < 0.44 else 2 if f < 0.53 else 3 if f < 0.64 else 4 if f < 0.76 else 5
                c = INNER[k]
                if alarm:
                    if k >= 3:  # бирюза в пурпур шла через синий (Антон 16.11) — светлые слои идут через бледно-розовый
                        c = lerp(c, PINK, al * 2) if al <= 0.5 else lerp(PINK, RED[k], (al - 0.5) * 2)
                    else:
                        c = lerp(c, MAG[k], al * 2) if al <= 0.5 else lerp(MAG[k], RED[k], (al - 0.5) * 2)  # через пурпур: без серого и зелени
            if alarm and d > RI and d <= R - 1:
                c = lerp(c, lerp(RED[1], RED[2], 0.5), al * 0.8)
            p[x, y] = c + (255,)
    # блик стекла (как у орбов SEGA)
    for (bx, by, bc) in ((4, 5, (255, 255, 255)), (5, 4, (255, 255, 255)), (4, 6, (200, 170, 255)), (6, 4, (200, 170, 255))):
        p[bx, by] = bc + (255,)
    return im


def strip(alarm):
    s = Image.new('RGBA', (16, 16 * N), (0, 0, 0, 0))
    for i in range(N):
        s.alpha_composite(frame(i, alarm), (0, 16 * i))
    return s


def gif(alarm, name, scale=16, bg=(139, 139, 139)):
    fr = []
    for i in range(N):
        f = Image.new('RGBA', (16, 16), bg + (255,))
        f.alpha_composite(frame(i, alarm))
        fr.append(f.resize((16 * scale, 16 * scale), Image.NEAREST).convert('P', palette=Image.ADAPTIVE))
    fr[0].save(OUT + name, save_all=True, append_images=fr[1:], duration=50, loop=0, disposal=2)


if __name__ == '__main__':
    strip(False).save(OUT + 'resonant_crystal.png')
    open(OUT + 'resonant_crystal.png.mcmeta', 'w').write('{\n  "animation": {\n    "frametime": 1,\n    "interpolate": true\n  }\n}\n')
    strip(True).save(OUT + 'resonant_crystal_alarm.png')
    gif(False, 'core_inventory.gif')
    gif(True, 'core_alarm.gif')
    os.makedirs(OUT, exist_ok=True)
    old = Image.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets',
                                  'matterrebirth', 'textures', 'items', 'resonant_crystal.png')).convert('RGBA')
    sheet = Image.new('RGBA', (16 * 13 + 8, 16 * 3 + 8), (139, 139, 139, 255))
    sheet.alpha_composite(old, (4, 4))
    for k in range(12):
        sheet.alpha_composite(frame(k * N // 12), (20 + k * 16, 4))
        sheet.alpha_composite(frame(k * N // 12, True), (20 + k * 16, 36))
    sheet = sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST)
    sheet.save(SCR + 'core_sheet.png')
    sheet.save(OUT + 'core_sheet.png')
    print('ok')
