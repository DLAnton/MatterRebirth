"""Этап 5а: текстуры корпуса репликатора для мода (32x32 на грань блока), по листу surfaces_v4 (утверждён Антоном 08.11).
Каждая текстура — проекция грани блока: u слева направо, v сверху вниз, 2 текселя на единицу модели.
Нижний блок: основание 0..5 (строки 22..31), камера 5..16. Верхний: камера 0..12.5, крышка 12.5..16 (строки 0..6).
Экранчик рисуется тёмным: лампочки, деления стадий и здоровье поверх рисует отрисовщик машины (RenderReplicator).
Запуск: python tools/replicator/gen_textures5a.py OUT_DIR  (только в отдельную папку: он пишет и прежний корпус 32x32;
в мод из него идёт только стекло — его копирует build_replicator.py; кольца и экранчик этапа 5а лежат в src32/)
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import gen_hollow as G
import gen_surfaces as S

DARK = (0x3e, 0x4c, 0x4d)
WHITE_LED = (0xf2, 0xf6, 0xf6)
STATES = ('off', 'idle', 'grow', 'wait', 'alert', 'ok')
# кольца отката и распада (Антон 14.11): перелив красный ↔ фиолетовый, по кадру на тик; формула — client/Shimmer.java
SHIMMER_TICKS = 60
SHIMMER_RED, SHIMMER_PURPLE = 0xFF0000, 0xC05CD8  # чистый красный — проба Антона 14.11


def shimmer_rgb(k):
    import math
    b = (1 - math.cos(2 * math.pi * k / SHIMMER_TICKS)) / 2
    ch = lambda s: (SHIMMER_RED >> s & 255, SHIMMER_PURPLE >> s & 255)
    return tuple(max(0, min(255, int(a + (c - a) * b + 0.5))) for a, c in (ch(16), ch(8), ch(0)))
LIGHT_KEY = {'off': 'off', 'idle': 'idle', 'grow': 'grow', 'wait': 'wait', 'alert': 'back', 'ok': 'ok'}


def img(fn, w=32, h=32):
    im = Image.new('RGBA', (w, h))
    for u in range(w):
        for v in range(h):
            c = fn(u, v)
            im.putpixel((u, v), c if len(c) == 4 else tuple(c) + (255,))
    return im


def base_vox():
    vox = {}
    G.base_and_cap(vox, 0)
    # экранчик (правка 08.11): рамка 6..25, экран 7..24, все огни тёмные — живые рисует отрисовщик
    for x in range(6, 26):
        for y in range(2, 8):
            vox[(x, y, 31)] = G.BEZEL if (x in (6, 25) or y in (2, 7)) else G.SCREEN
    for x in range(9, 23):
        vox[(x, 5, 31)] = DARK
    vox[(9, 4, 31)] = DARK
    vox[(11, 4, 31)] = DARK
    for i in range(5):
        vox[(14 + i * 2, 4, 31)] = DARK
    # гнёзда планок якоря по бокам экрана, столбцы 7 и 24, строки 3..6 (5г, Антон 13.11)
    for y in range(3, 7):
        vox[(7, y, 31)] = DARK
        vox[(24, y, 31)] = DARK
    return vox


def main(out):
    os.makedirs(out, exist_ok=True)
    vox = base_vox()
    save = lambda im, name: im.save(os.path.join(out, name + '.png'))
    # лицо нижнего блока: стойки (строки 0..21) и основание с экранчиком (22..31)
    save(img(lambda u, v: vox[(u, 31 - v, 31)] if v >= 22 else G.PANEL), 'replicator_front')
    # бока нижнего блока: основание с заклёпками; стойки и торцы задней стенки — панель
    save(img(lambda u, v: vox[(31, 31 - v, 31 - u)] if v >= 22 else G.PANEL), 'replicator_side_lower')
    # бока и лицо верхнего блока: крышка (строки 0..6) и стойки
    save(img(lambda u, v: vox[(31, 63 - v, 31 - u)] if v <= 6 else G.PANEL), 'replicator_side_upper')
    # порты: зад каждого блока, верх крышки (шаблоны), низ основания (энергия)
    save(img(lambda u, v: G.socket(u, v) or G.casing_flat(u, v)), 'replicator_port')
    save(img(lambda u, v: G.socket(u, v, G.PIN_DATA) or G.casing_flat(u, v)), 'replicator_port_top')
    save(img(lambda u, v: G.socket(u, v, G.PIN_EU) or G.casing_flat(u, v)), 'replicator_port_bottom')
    # внутренние грани стоек
    save(img(lambda u, v: G.PANEL), 'replicator_panel')

    # внутренняя сторона задней стенки, огоньки всегда белые (Антон 08.11); общая картинка на два блока 32x64
    def wall(u, gv):
        y = 63 - gv
        if not (G.BASE_H <= y < G.TOP - G.CAP_H):
            return G.PANEL
        if u in (3, 28) or y in (G.BASE_H, G.TOP - G.CAP_H - 1):
            return G.GROOVE
        if u in (8, 23) and (y - G.BASE_H) % 4 == 2:
            return WHITE_LED
        if 13 <= u <= 18:
            return G.STRIP
        return G.PANEL
    save(img(lambda u, v: wall(u, v)), 'replicator_wall_upper')
    save(img(lambda u, v: wall(u, v + 32)), 'replicator_wall_lower')

    # стекло: картинка на два блока, окно 3..28 по ширине и строки 7..53 (камера); вне окна прозрачно
    def glass(uu):
        full = Image.new('RGBA', (32, 64), (0, 0, 0, 0))
        pane = S.glass_tex(uu, 47)
        full.paste(pane, (3, 7))
        return full
    for uu in (False, True):
        g = glass(uu)
        suf = '_uu' if uu else ''
        save(g.crop((0, 0, 32, 32)), 'replicator_glass_upper' + suf)
        save(g.crop((0, 32, 32, 64)), 'replicator_glass_lower' + suf)

    # пол камеры и излучатель на потолке по шести состояниям света
    for st in STATES:
        k = LIGHT_KEY[st]
        save(img(lambda u, v: S.floor_color(u, v, k) if (3 <= u <= 28 and 3 <= v <= 28) else G.GROOVE), 'replicator_floor_' + st)
        save(img(lambda u, v: S.ceiling_color(u, v, k) if (3 <= u <= 28 and 3 <= v <= 28) else G.GROOVE), 'replicator_ceiling_' + st)
    # седьмое — откат и распад: анимация из SHIMMER_TICKS кадров (кадр выбирает ShimmerSprite по времени мира)
    for kind, fn in (('floor', S.floor_color), ('ceiling', S.ceiling_color)):
        strip = Image.new('RGBA', (32, 32 * SHIMMER_TICKS))
        for f in range(SHIMMER_TICKS):
            c = shimmer_rgb(f)
            S.LIGHT['shimmer'] = (c, S._hi(c))
            strip.paste(img(lambda u, v: fn(u, v, 'shimmer') if (3 <= u <= 28 and 3 <= v <= 28) else G.GROOVE), (0, 32 * f))
        name = 'replicator_%s_rollback' % kind
        save(strip, name)
        with open(os.path.join(out, name + '.png.mcmeta'), 'w') as meta:
            meta.write('{\n  "animation": {\n    "frametime": 1\n  }\n}\n')


if __name__ == '__main__':
    main(sys.argv[1])
