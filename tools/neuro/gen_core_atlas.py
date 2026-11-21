# Атлас ядра в цветах состояний для панели «Нейросвязь» (6д-2): колонки по 16 px — состояния, строки — 64 кадра.
# Колонки: 0 рост (свои цвета), 1 пауза, 2 ожидание (жёлтый), 3 откат, 4 красный (беда, перегрузка, гибель),
# 5 готов (зелёный), 6 нет связи (серое). Цвета и переливы — gen_core_states.py (концепт 16.11).
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_core as g
import gen_core_states as s
from PIL import Image

COLS = [None, (225, 225, 235), (0xFF, 0xD8, 0x4A), 'rollback', (0xFF, 0, 0), (0x5A, 0xDC, 0x6E), 'grey']

def grey(i):
    im = g.frame(i)
    px = im.load()
    for y in range(16):
        for x in range(16):
            r, gg, b, a = px[x, y]
            if a == 0:
                continue
            l = int(round((0.3 * r + 0.59 * gg + 0.11 * b) * 0.8))
            px[x, y] = (l, l, l, a)
    return im

atlas = Image.new('RGBA', (16 * len(COLS), 16 * g.N), (0, 0, 0, 0))
for c, col in enumerate(COLS):
    for i in range(g.N):
        im = grey(i) if col == 'grey' else s.frame(i, col)
        atlas.alpha_composite(im, (16 * c, 16 * i))
GUI = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'matterrebirth', 'textures', 'gui')
atlas.save(sys.argv[1] if len(sys.argv) > 1 else os.path.join(GUI, 'core_states.png'))
# тревога ядра (уведомления 6д-1): полоса кадров как у кристалла, в тревожных цветах
g.strip(True).save(sys.argv[2] if len(sys.argv) > 2 else os.path.join(GUI, 'core_alarm.png'))
print(atlas.size)
