"""Слоты и экран окна репликатора клонов 176x196 в текстуре 256x256 (своя графика), фон прозрачный.
Панель с рамкой, резервуар, стрелка подачи и молния заряда в игре рисуются из текстур самого IC2 (common.png, guireplicator.png),
поэтому выглядят так же, как в машинах IC2. Раскладка по сетке слотов: верхний блок 7..78 (4 слота), нижний ряд 82..99, затем зазор 12 под кнопки сортировки, инвентарь игрока с 113.
Справа от окна (x 180+) значки экрана: рамки кнопок режима, их значки и стрелки выбора шаблона.
Запуск: python tools/gen_gui_texture.py [OUT.png]  (без аргумента — прямо в textures/gui/replicator.png мода)"""
import os
import sys
from PIL import Image, ImageDraw

BG, HI, SH, OUT = (198, 198, 198), (255, 255, 255), (85, 85, 85), (0, 0, 0)
SLOT, SLOT_SH = (139, 139, 139), (55, 55, 55)
im = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(im)


def rect(x, y, w, h, c):
    if w > 0 and h > 0:
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)


def panel(x, y, w, h):
    rect(x + 1, y, w - 2, h, OUT); rect(x, y + 1, w, h - 2, OUT)
    rect(x + 1, y + 1, w - 2, h - 2, BG)
    rect(x + 1, y + 1, w - 3, 2, HI); rect(x + 1, y + 1, 2, h - 3, HI)
    rect(x + 2, y + h - 3, w - 3, 2, SH); rect(x + w - 3, y + 2, 2, h - 3, SH)
    rect(x + 2, y + 2, 1, 1, BG); rect(x + w - 3, y + h - 3, 1, 1, BG)


def inset(x, y, w, h, fill=SLOT):
    rect(x, y, w, h, fill)
    rect(x, y, w - 1, 1, SLOT_SH); rect(x, y, 1, h - 1, SLOT_SH)
    rect(x + 1, y + h - 1, w - 1, 1, HI); rect(x + w - 1, y + 1, 1, h - 1, HI)


# слева: капсула UU, пустая капсула (между ними стрелка IC2), резерв (рядом молния IC2); справа внизу гнилая плоть
for y in (7, 61, 82):
    inset(7, y, 18, 18)
# экран: от верхнего ряда слотов до низа нижнего ряда (7..99)
inset(50, 7, 98, 93, (55, 55, 55))
rect(51, 8, 96, 91, (0, 0, 0))
rect(51, 19, 96, 1, (40, 40, 40))
# справа: 4 улучшения и резерв
for i in range(4):
    inset(151, 7 + i * 18, 18, 18)
inset(151, 82, 18, 18)
# инвентарь игрока
for r in range(3):
    for c in range(9):
        inset(7 + c * 18, 113 + r * 18, 18, 18)
for c in range(9):
    inset(7 + c * 18, 171, 18, 18)

# --- справа от окна (x 180+): значки экрана, тоже своя графика ---
# кнопки режима 12x12 на чёрном поле экрана, как у сканера IC2: тёмная рамка, у включённой светлее
for i, frame in enumerate(((60, 60, 60), (150, 150, 150), (95, 95, 95))):  # обычная, включённая, под мышью
    x = 180 + i * 12
    rect(x, 0, 12, 12, frame)
    rect(x + 1, 1, 10, 10, (0, 0, 0))
# значки 8x8 по центру кнопки (внутри рамки 10x10, отступ 2): корзина (вместо «Стоп», решение Антона 06.11),
# продолжить, растить заново, пауза; ряд y=12 серые, ряд y=20 светлые (включённая кнопка или корзина под мышью)
GLYPHS = [
    ["..XXXX..", "XXXXXXXX", ".XXXXXX.", ".X.XX.X.", ".X.XX.X.", ".X.XX.X.", ".X.XX.X.", "..XXXX.."],
    ["..XX....", "..XXX...", "..XXXX..", "..XXXXX.", "..XXXXX.", "..XXXX..", "..XXX...", "..XX...."],
    ["...XXX.X", ".XXXXXXX", ".XX..XXX", "XX..XXXX", "XX......", "XX....XX", ".XXXXXX.", "..XXXX.."],
    [".XX..XX."] * 8,  # пауза (решение Антона 06.11)
]
for row, col in ((12, (165, 165, 165)), (20, (255, 255, 255))):
    for g, rows in enumerate(GLYPHS):
        for yy, line in enumerate(rows):
            for xx, ch in enumerate(line):
                if ch == 'X':
                    im.putpixel((180 + g * 8 + xx, row + yy), col + (255,))
# «растить заново» включён (решение Антона 06.11): зелёная рамка (216,0) и зелёный значок (212,12)
rect(216, 0, 12, 12, (40, 150, 60))
rect(217, 1, 10, 10, (0, 0, 0))
for yy, line in enumerate(GLYPHS[2]):
    for xx, ch in enumerate(line):
        if ch == 'X':
            im.putpixel((212 + xx, 12 + yy), (90, 220, 110, 255))
# стрелки выбора шаблона 4x7 в шапке экрана: серые (y=28) и под мышью белые (y=35); левая x=180, правая x=184
ARROW_L = ["...X", "..XX", ".XXX", "XXXX", ".XXX", "..XX", "...X"]
for row, col in ((28, (130, 130, 130)), (35, (255, 255, 255))):
    for yy, line in enumerate(ARROW_L):
        for xx, ch in enumerate(line):
            if ch == 'X':
                im.putpixel((180 + xx, row + yy), col + (255,))
                im.putpixel((184 + 3 - xx, row + yy), col + (255,))
# машина без заряда (решение Антона 06.11): экран выключен, значки тёмно-серые (y=44), стрелки тёмно-серые (y=52)
DARK = (58, 58, 58, 255)
for g, rows in enumerate(GLYPHS):
    for yy, line in enumerate(rows):
        for xx, ch in enumerate(line):
            if ch == 'X':
                im.putpixel((180 + g * 8 + xx, 44 + yy), DARK)
for yy, line in enumerate(ARROW_L):
    for xx, ch in enumerate(line):
        if ch == 'X':
            im.putpixel((180 + xx, 52 + yy), DARK)
            im.putpixel((184 + 3 - xx, 52 + yy), DARK)
im.save(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'matterrebirth', 'textures', 'gui', 'replicator.png'))
