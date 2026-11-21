# Значки синхронизатора (Антон 14.11: перерисовать под модель на голове; кожаный — на настоящем значке кожаного шлема).
# Обычный: вид спереди — корпуса на висках с окошком кристаллов, обруч за головой, веер усиков с сенсорами, крючки.
# Кожаный: только накладка (слой 2) поверх ванильных minecraft:items/leather_helmet (+ _overlay), сами они не копируются.
import sys
from PIL import Image
PAL = {
    'O': (52, 56, 58), 'B': (184, 184, 184), 'L': (206, 206, 206), 'd': (135, 135, 135),
    'A': (90, 96, 98), 'S': (240, 242, 242), 'C': (94, 224, 224),
    'e': (216, 156, 240), 'f': (138, 44, 176), 'h': (78, 16, 106), 'G': (58, 122, 58), 'R': (208, 48, 48),
}
PLAIN = [
    "................",
    "................",
    "....AAA..AAA....",
    "...A..S..S..A...",
    "..A...C..C...A..",
    "..A.S......S.A..",
    "..A.C......C.A..",
    ".OOO........OOO.",
    ".OLO........OLO.",
    ".OeOOOOOOOOOOeO.",
    ".OfOLLLLLLLLOfO.",
    ".OROOOOOOOOOORO.",
    ".OdO........OdO.",
    ".OOO........OOO.",
    "..A..........A..",
    "................",
]
# Поверх кожаного шлема (он занимает x 3..12, y 3..11): тот же рисунок без обруча (он за шапкой) и без крючков;
# дуга усиков лежит на макушке шапки, сенсоры на ней, корпуса на её боках.
LEATHER = [r if y not in (9, 10, 11, 14) else r[:4] + '........' + r[12:] for y, r in enumerate(PLAIN)]
LEATHER[14] = '.' * 16
def img(rows):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        assert len(r) == 16, (y, r)
        for x, ch in enumerate(r):
            if ch != '.':
                im.putpixel((x, y), PAL[ch] + (255,))
    return im
if __name__ == '__main__':
    # без аргумента — прямо в textures/items мода
    import os
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src',
                                                             'main', 'resources', 'assets', 'matterrebirth', 'textures', 'items')
    img(PLAIN).save(out + '/neuro_synchronizer.png')
    img(LEATHER).save(out + '/neuro_synchronizer_leather_overlay.png')
