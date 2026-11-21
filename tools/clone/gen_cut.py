"""5б+: светящийся срез на линии роста. Текстура среза 16x16 в оттенках серого (цвет даёт состояние),
кладётся по миру: 4 текселя на пиксель модели, плитка = 4x4 пикселя модели; по краю коробки — яркая кромка.
Запуск: python3 gen_cut.py OUT_DIR
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen5b2 as g

S = g.S
COLORS = {'grow': (0x5c, 0xc8, 0xc8), 'rollback': (0xdc, 0x46, 0x46), 'pause': (0x96, 0x96, 0x96), 'wait': (0xff, 0xd8, 0x4a)}


def tex_grid():
    """Вариант А: сетка печати — светлые линии через пиксель модели, между ними тёплое поле с лёгким шумом."""
    t = Image.new('L', (16, 16))
    for x in range(16):
        for y in range(16):
            v = 150 + g.noise(x, y, 6)
            if x % 4 == 0 or y % 4 == 0:
                v = 205
            if x % 4 == 0 and y % 4 == 0:
                v = 235
            t.putpixel((x, y), max(0, min(255, v)))
    return t


def tex_hatch():
    """Вариант Б: косая штриховка, как на чертеже разреза."""
    t = Image.new('L', (16, 16))
    for x in range(16):
        for y in range(16):
            v = 210 if (x + y) % 4 == 0 else 140 + g.noise(x, y, 5)
            t.putpixel((x, y), max(0, min(255, v)))
    return t


def cap(vox, boxes, cut, tex, color):
    """Крышка на высоте среза у коробок, через которые проходит линия: кромка 1.0, поле — текстура."""
    for name, box in boxes.items():
        X0, Y0, Z0, W, H, D, U, V = g.box_tex(box, 1)
        if not (Y0 < cut <= Y0 + H):
            continue
        y = cut - 1
        for x in range(X0, X0 + W):
            for z in range(Z0, Z0 + D):
                rim = x in (X0, X0 + W - 1) or z in (Z0, Z0 + D - 1)
                k = 1.0 if rim else tex.getpixel((x % 16, z % 16)) / 255
                vox[(x, y, z)] = tuple(min(255, round(c * k + (255 - c) * 0.25 * k)) for c in color)


def build(tx, stage, frac, tex, state='grow'):
    vox = {}
    cutv = round(32 * S * frac)
    col = COLORS[state]
    full = lambda s: s < stage
    if stage <= 3 or stage == 4:
        g.put(vox, g.SKEL, S, tx['skel'], None if full(1) else cutv)
    if 2 <= stage <= 4:
        g.put(vox, g.SHELL, 1, tx['nerves'], None if full(2) else cutv)
    if 3 <= stage <= 4:
        g.put(vox, g.SHELL, 1, tx['vessels'], None if full(3) else cutv)
    if stage == 4:
        # растут мышцы: нижние слои видны только выше среза, мышцы — ниже
        vox = {p: c for p, c in vox.items() if p[1] >= cutv}
        g.put(vox, g.PLAYER, 4, tx['muscle'], cutv)
    if stage == 5:
        g.put(vox, g.PLAYER, 4, tx['muscle'], None)
        vox = {p: c for p, c in vox.items() if p[1] >= cutv}
        g.put(vox, g.PLAYER, 4, tx['skin'], cutv)
    boxes = {1: g.SKEL, 2: g.SHELL, 3: g.SHELL, 4: g.PLAYER, 5: g.PLAYER}[stage]
    cap(vox, boxes, cutv, tex, col)
    return vox


def main(out):
    """Плитка среза и лист clone_cut_v1 (листу нужен ванильный скелет, см. gen5b2.VANILLA)."""
    os.makedirs(out, exist_ok=True)
    tx = {'skel': g.fresh_skeleton(), 'nerves': g.nerves_atlas(), 'vessels': g.vessels_atlas(),
          'muscle': g.muscles_atlas(), 'skin': g.skin_placeholder()}
    A, B = tex_grid(), tex_hatch()
    A.save(os.path.join(out, 'clone_cut.png'))
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16)
    rows = [('А: скелет 45 %, рост', build(tx, 1, 0.45, A)), ('А: сосуды 60 %, рост', build(tx, 3, 0.6, A)),
            ('А: мышцы 40 %, рост', build(tx, 4, 0.4, A)), ('А: кожа 55 %, рост', build(tx, 5, 0.55, A)),
            ('А: мышцы 40 %, откат', build(tx, 4, 0.4, A, 'rollback')), ('А: мышцы 40 %, пауза', build(tx, 4, 0.4, A, 'pause')),
            ('А: мышцы 40 %, ожидание', build(tx, 4, 0.4, A, 'wait')), ('Б: мышцы 40 %, рост', build(tx, 4, 0.4, B))]
    panels = [(t, g.iso(v, 2)) for t, v in rows]
    pw, ph = max(p[1].width for p in panels), max(p[1].height for p in panels)
    cols = 4
    sheet = Image.new('RGBA', (cols * (pw + 12) + 12, 2 * (ph + 34) + 12 + 120), (0x22, 0x26, 0x2a, 255))
    d = ImageDraw.Draw(sheet)
    for i, (t, im) in enumerate(panels):
        x, y = 12 + (i % cols) * (pw + 12), 12 + (i // cols) * (ph + 34)
        d.text((x, y), t, fill=(0xe6, 0xe6, 0xe6), font=font)
        sheet.alpha_composite(im, (x, y + 24))
    y = 12 + 2 * (ph + 34)
    for j, (name, t) in enumerate((('Текстура А (сетка печати), 16x16, ×6', A), ('Текстура Б (штриховка), 16x16, ×6', B))):
        x = 12 + j * 2 * (pw + 12)
        d.text((x, y), name, fill=(0xe6, 0xe6, 0xe6), font=font)
        tile = Image.merge('RGB', [t.point(lambda v, c=c: round(c * v / 255)) for c in COLORS['grow']]).resize((96, 96), Image.NEAREST)
        sheet.paste(tile, (x, y + 22))
    sheet.save(os.path.join(out, 'clone_cut_v1.png'))


ENTITY = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets',
                      'matterrebirth', 'textures', 'entity')

if __name__ == '__main__':
    if len(sys.argv) > 1:
        main(sys.argv[1])
    else:  # без аргумента — только плитка среза прямо в мод
        tex_grid().save(os.path.join(ENTITY, 'clone_cut.png'))
