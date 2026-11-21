"""Этап 5а: итоговые поверхности корпуса репликатора (32x32 на блок) и вид машины в трёх состояниях.
Геометрия и палитра из концепта v5 (ic2-clone-addon/gen_hollow.py); правки Антона 08.11:
стекло без UU светлое, с UU фиолетовое; кольца пола и излучателя: выкл / рост / откат (красные, со свечением).
Запуск: python3 gen_surfaces.py OUT.png
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import gen_hollow as G

RED, RED_HI = (0xd8, 0x3a, 0x30), (0xff, 0x7a, 0x60)
GLASS_CLEAR = (0xbe, 0xea, 0xf1)

YEL, YEL_HI = (0xe8, 0xc8, 0x3a), (0xff, 0xe8, 0x8a)
GRN, GRN_HI = (0x4c, 0xc0, 0x5c), (0x9a, 0xee, 0xa4)
# цвет колец и огоньков = цвет строки состояния окна (Антон 08.11): рост бирюзовый, жёлтый — ждёт, красный — беда, зелёный — клон здоров
WHT, WHT_HI = (0xe6, 0xee, 0xf0), (0xff, 0xff, 0xff)
# idle — белый: пауза и пустая машина без шаблона (Антон 08.11); off — погас, только без энергии
LIGHT = {'off': (G.CYAN_OFF, G.BEZEL), 'idle': (WHT, WHT_HI), 'grow': (G.CYAN, G.CYAN_HI), 'wait': (YEL, YEL_HI), 'back': (RED, RED_HI), 'ok': (GRN, GRN_HI)}

# 14.11 (Антон): кольца в одной палитре с окном машины — основа колец точь-в-точь цвет состояния в окне,
# светлая часть — тот же цвет на 45 % к белому (как кромка среза клона); откат — свой фиолетовый.
def _hex(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)

def _hi(c, t=0.45):
    return tuple(round(x + (255 - x) * t) for x in c)

PALETTE = {'grow': 0x5CC8C8, 'wait': 0xFFD84A, 'back': 0xFF0000, 'ok': 0x5ADC6E}  # тревога — чистый красный (Антон 14.11)
for _k, _c in PALETTE.items():
    LIGHT[_k] = (_hex(_c), _hi(_hex(_c)))

def ring(state):
    return LIGHT[state][0]

def ring_hi(state):
    return LIGHT[state][1]

def floor_color(x, z, state):
    r = ((x - 15.5) ** 2 + (z - 15.5) ** 2) ** .5
    if r < 7:
        return G.STRIP
    if r < 8:
        return ring(state)
    if r < 9:
        return G.BEZEL
    if (x in (5, 6, 25, 26)) and (z in (5, 6, 25, 26)):
        return G.SOCKET
    return G.FLOOR_G if (x % 4 == 0 or z % 4 == 0) else G.FLOOR

def ceiling_color(x, z, state):
    d = abs(x - 15.5) + abs(z - 15.5)
    if d < 3.5:
        return G.UU
    if d < 4.5:
        return G.RIM
    if d < 7.5:
        if d > 6.5:
            return ring_hi(state) if (abs(x - 15.5) < 1 or abs(z - 15.5) < 1) else ring(state)
        return G.BEZEL
    return G.FLOOR_G if (x % 4 == 0 or z % 4 == 0) else G.FLOOR

def build(state, stage):
    vox = {}
    G.base_and_cap(vox, stage)
    G.chamber(vox, stage)
    # экранчик основания (правка 08.11: деления стадий вылезали за рамку): рамка 6..25, экран 7..24,
    # полоса здоровья, лампочки UU и EU, пять делений по 1 пикселю через 1
    for x in range(6, 26):
        for y in range(2, 8):
            vox[(x, y, 31)] = G.BEZEL if (x in (6, 25) or y in (2, 7)) else G.SCREEN
    for x in range(9, 23):
        vox[(x, 5, 31)] = G.HP
    vox[(9, 4, 31)] = G.LED_UU if state != 'off' else (0x3e, 0x4c, 0x4d)
    vox[(11, 4, 31)] = G.LED_EU if state != 'off' else (0x3e, 0x4c, 0x4d)
    for i in range(5):
        vox[(14 + i * 2, 4, 31)] = G.CYAN_HI if i < stage else (0x3e, 0x4c, 0x4d)
    for x in range(32):
        for z in range(32):
            if 3 <= x <= 28 and 3 <= z <= 28:
                vox[(x, G.BASE_H - 1, z)] = floor_color(x, z, state)
            if abs(x - 15.5) + abs(z - 15.5) < 7.5:
                vox[(x, G.TOP - G.CAP_H - 1, z)] = ceiling_color(x, z, state)
    for k, c in list(vox.items()):
        if c in (G.CYAN, G.CYAN_OFF) and G.BASE_H <= k[1] < G.TOP - G.CAP_H and k[2] >= 1:
            # правка Антона 08.11: на стойках огоньков нет, огоньки задней стенки всегда белые
            vox[k] = (0xf2, 0xf6, 0xf6) if k[2] == 2 else G.PANEL
    if stage:
        G.clone(vox, stage)
    return vox

def render(vox, uu, state):
    G.UU_SAVE = G.UU
    im = G.render(vox, glass=False)
    # стекло: с UU — фиолетовое с искрами (как в концепте), без UU — светлое, почти прозрачное, только блики
    ov = Image.new('RGBA', im.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(ov)
    W = 32 * (G.AX - G.BX) + 8
    ox, oy = -32 * G.BX + 4, G.TOP * G.S + 4
    P = lambda X, Y, Z: G.proj(X, Y, Z, ox, oy)
    y0, y1 = G.BASE_H, G.TOP - G.CAP_H
    col, a1, a2 = (G.UU, 145, 170) if uu else (GLASS_CLEAR, 45, 60)
    g.polygon([P(3, y0, 32), P(29, y0, 32), P(29, y1, 32), P(3, y1, 32)], fill=col + (a1,))
    g.polygon([P(32, y0, 3), P(32, y0, 29), P(32, y1, 29), P(32, y1, 3)], fill=col + (a2,))
    for (a, b) in (((5, y0 + 2, 32), (5, y1 - 2, 32)), ((8, y0 + 2, 32), (8, y1 - 8, 32)), ((32, y0 + 2, 6), (32, y1 - 2, 6))):
        g.line([P(*a), P(*b)], fill=(255, 255, 255, 70 if uu else 110), width=2)
    if uu:
        seed = 7
        for i in range(70):
            seed = (seed * 1103515245 + 12345) & 0x7fffffff
            u = 4 + seed % 24
            seed = (seed * 1103515245 + 12345) & 0x7fffffff
            v = y0 + 1 + seed % (y1 - y0 - 2)
            pt = P(u, v, 32) if i % 2 else P(32, v, u)
            g.rectangle([pt[0], pt[1], pt[0] + 2, pt[1] + 2], fill=G.RIM + (170,))
    return Image.alpha_composite(im, ov)

def flat(w, h, fn):
    im = Image.new('RGBA', (w, h))
    for u in range(w):
        for v in range(h):
            im.putpixel((u, v), tuple(fn(u, v)) + (255,) if len(fn(u, v)) == 3 else fn(u, v))
    return im

def glass_tex(uu, h=44):
    """Стекло 26x44 (между стойками, по высоте камеры): рамка-кромка и блики; с UU — фиолетовое и искры."""
    w = 26
    im = Image.new('RGBA', (w, h))
    base = G.UU + (190,) if uu else GLASS_CLEAR + (70,)
    for u in range(w):
        for v in range(h):
            c = base
            if u in (0, w - 1) or v in (0, h - 1):
                c = (G.RIM + (200,)) if uu else (0xd6, 0xf2, 0xf6, 150)
            elif (u == 2 and 2 <= v <= h - 3) or (u == 5 and 2 <= v <= h - 9):
                c = (255, 255, 255, 90 if uu else 140)
            im.putpixel((u, v), c)
    if uu:
        seed = 11
        for i in range(26):
            seed = (seed * 1103515245 + 12345) & 0x7fffffff
            u = 2 + seed % (w - 4)
            seed = (seed * 1103515245 + 12345) & 0x7fffffff
            v = 2 + seed % (h - 4)
            im.putpixel((u, v), G.RIM + (230,))
    return im

def surfaces(stage=3):
    vox = build('grow', stage)
    S = {}
    S['Верх крышки (порт)'] = flat(32, 32, lambda u, v: G.socket(u, v, G.PIN_DATA) or G.casing_flat(u, v))
    S['Низ основания (порт)'] = flat(32, 32, lambda u, v: G.socket(u, v, G.PIN_EU) or G.casing_flat(u, v))
    S['Задняя стенка, каждый блок'] = flat(32, 32, lambda u, v: G.socket(u, v) or G.casing_flat(u, v))
    S['Лицо основания (экранчик)'] = flat(32, G.BASE_H, lambda u, v: vox.get((u, G.BASE_H - 1 - v, 31), G.PANEL))
    S['Бок основания'] = flat(32, G.BASE_H, lambda u, v: vox.get((31, G.BASE_H - 1 - v, 31 - u), G.PANEL))
    S['Бок крышки'] = flat(32, G.CAP_H, lambda u, v: vox.get((31, G.TOP - 1 - v, 31 - u), G.PANEL))
    S['Стойка'] = flat(3, 44, lambda u, v: vox.get((29 + u, G.TOP - G.CAP_H - 1 - v, 31), G.PANEL))
    S['Стекло без UU'] = glass_tex(False)
    S['Стекло с UU'] = glass_tex(True)
    NAMES = (('off', 'погас'), ('idle', 'белый'), ('grow', 'рост'), ('wait', 'ждёт'), ('back', 'беда'), ('ok', 'здоров'))
    for st, name in NAMES:
        S['Пол: ' + name] = flat(26, 26, lambda u, v, st=st: floor_color(u + 3, v + 3, st))
    for st, name in NAMES:
        S['Излучатель: ' + name] = flat(16, 16, lambda u, v, st=st: (ceiling_color(u + 8, v + 8, st) if abs(u + 8 - 15.5) + abs(v + 8 - 15.5) < 7.5 else (0, 0, 0, 0)))
    return S

def main(out):
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 18)
    small = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 14)
    views = [('Нет энергии', build('off', 0), False, 'off'),
             ('Пусто / Пауза', build('idle', 0), False, 'idle'),
             ('Рост', build('grow', 3), True, 'grow'),
             ('Нужно UU / Диапауза', build('wait', 3), False, 'wait'),
             ('Откат / Перегрузка', build('back', 2), True, 'back'),
             ('Клон готов и здоров', build('ok', 5), True, 'ok')]
    rimgs = [(t, render(v, uu, st)) for t, v, uu, st in views]
    S = surfaces()
    k = 6
    tiles = [(name, im.resize((im.width * k, im.height * k), Image.NEAREST)) for name, im in S.items()]
    W = 1700
    rows, cur, x, rh = [], [], 0, 0
    probe = ImageDraw.Draw(Image.new('RGBA', (1, 1)))
    for name, im in tiles:
        w = int(max(im.width, probe.textlength(name, font=small)))
        if 20 + x + w > W - 20 and cur:
            rows.append((cur, rh)); cur, x, rh = [], 0, 0
        cur.append((name, im)); x += w + 24; rh = max(rh, im.height)
    rows.append((cur, rh))
    top_h = max(i.height for _, i in rimgs) + 40
    H = top_h + sum(r[1] + 50 for r in rows) + 30
    sheet = Image.new('RGBA', (W, H), (0xdc, 0xdc, 0xdc, 255))
    d = ImageDraw.Draw(sheet)
    x = 20
    for t, im in rimgs:
        d.text((x, 8), t, font=font, fill=(30, 30, 30))
        sheet.alpha_composite(im, (x, 36)); x += im.width + 50
    y = top_h + 10
    for cur, rh in rows:
        x = 20
        for name, im in cur:
            d.text((x, y), name, font=small, fill=(30, 30, 30))
            bg = Image.new('RGBA', im.size, (0x30, 0x30, 0x30, 255))
            sheet.alpha_composite(bg, (x, y + 20)); sheet.alpha_composite(im, (x, y + 20))
            x += int(max(im.width, d.textlength(name, font=small))) + 24
        y += rh + 50
    sheet.save(out)
    print(sheet.size)

if __name__ == '__main__':
    main(sys.argv[1])
