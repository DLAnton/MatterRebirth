# Геометрия синхронизатора на голове для мода: из концепта 05.11 (worn_concept.device), 3 вокселя на пиксель головы.
# Прилегает к голове (Антон 14.11): корпуса и обруч толщиной 2 вокселя лежат прямо на коже (второй слой скина на 1/2
# пикселя уходит внутрь них), усики в 1 воксель — во внешнем слое (1/3..2/3 пикселя), чтобы волосы скина их не закрывали.
# В кожаном шлеме всё лежит прямо на шапке (она на 1 пиксель = 3 вокселя). Корпус в цветах железной оболочки IC2.
# Выход: строки «x y z dx dy dz RRGGBB» в вокселях, в осях модели головы Minecraft (x вправо от головы, y вниз, перед -z),
# начало — центр низа головы; рядом стоящие воксели одного цвета слиты в коробки.
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import worn_concept as w
from sync_fit import device_fit
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src',
                                                         'main', 'resources', 'assets', 'matterrebirth', 'models', 'armor')
IRON = {w.BAND: (184, 184, 184), w.BAND_D: (135, 135, 135), w.BAND_L: (206, 206, 206)}
def boxes(v):
    left = dict(v); out = []
    for key in sorted(v, key=lambda k: (k[1], k[2], k[0])):
        if key not in left: continue
        c = left[key]; x, y, z = key
        dx = 1
        while (x+dx, y, z) in left and left[(x+dx, y, z)] == c: dx += 1
        dz = 1
        while all(left.get((x+i, y, z+dz)) == c for i in range(dx)): dz += 1
        dy = 1
        while all(left.get((x+i, y+dy, z+k)) == c for i in range(dx) for k in range(dz)): dy += 1
        for i in range(dx):
            for j in range(dy):
                for k in range(dz):
                    del left[(x+i, y+j, z+k)]
        out.append((x, y, z, dx, dy, dz, c))
    return out
def press_tips(v, m):
    """Сенсор на конце усика прижимается к голове (Антон 14.11: «считывающие электроды-сенсоры»): от кончика к коже —
    стебель цвета усика, бирюзовый сенсор у самой кожи (у кожаного — у шапки, m=3: шапка на пиксель шире головы).
    Голова в концепте: x 4..27, y 0..23, z 4..27."""
    lo, hi, top = 4 - m, 27 + m, 23 + m
    tips = [k for k, c in v.items() if c == w.CYAN and any(v.get((k[0]+a, k[1]+b, k[2]+d)) == w.SLEEVE
            for a, b, d in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)))]
    for x, y, z in tips:
        if y > top and lo <= x <= hi and lo <= z <= hi: step, n = (0, -1, 0), y - top - 1
        elif x > hi and 0 <= y <= top and lo <= z <= hi: step, n = (-1, 0, 0), x - hi - 1
        elif x < lo and 0 <= y <= top and lo <= z <= hi: step, n = (1, 0, 0), lo - 1 - x
        elif z > hi and 0 <= y <= top and lo <= x <= hi: step, n = (0, 0, -1), z - hi - 1
        elif z < lo and 0 <= y <= top and lo <= x <= hi: step, n = (0, 0, 1), lo - 1 - z
        else: continue  # кончик не над гранью головы — оставить как в концепте
        if n <= 0: continue
        v[(x, y, z)] = w.ARM
        for i in range(1, n + 1):
            p = (x + step[0]*i, y + step[1]*i, z + step[2]*i)
            v[p] = w.CYAN if i == n else w.ARM
    return v
# 14.11: боковые стойки усиков в кожаном — на внутреннем краю корпуса, вплотную к шапке (sync_fit.device_fit).
# 14.11 (Антон): модель одна на оба варианта — на кожаной шапке мод увеличивает её от центра головы в 10/8 раза
# (шапка на пиксель шире головы во все стороны) и не рисует коробки с меткой hook (крючок за ухом).
# Основа — одобренный кожаный вариант (Антон 14.11): усики тонкие (1/3 пикселя), всё лежит прямо на поверхности —
# боковые стойки на внутреннем краю корпусов, усики сверху и на лбу без зазора. На голове модель как есть,
# на шапке мод сдвигает её на пиксель наружу.
full = device_fit('sync', 0, 0, 0, 1)
nohook = device_fit('leather', 0, 0, 0, 1)          # то же без крючка
assert all(full[k] == c for k, c in nohook.items())
hook = {k: c for k, c in full.items() if k not in nohook}
lines = ['# Matter Rebirth: синхронизатор на голове, %d вокселей (крючок %d), генератор stage5e/gen_sync_boxes.py' % (len(full), len(hook)),
         '# x y z dx dy dz RRGGBB [hook]; 3 вокселя = 1 пиксель; оси модели головы, начало в центре низа головы;',
         '# на кожаной шапке модель увеличивается в 10/8 раза, коробки hook не рисуются']
for part, tag in ((nohook, ''), (hook, ' hook')):
    for x, y, z, dx, dy, dz, c in boxes({k: IRON.get(c, c) for k, c in part.items()}):
        mx = x - 16; my = -(y + dy); mz = -(z + dz - 16)
        lines.append('%d %d %d %d %d %d %02X%02X%02X%s' % (mx, my, mz, dx, dy, dz, *c, tag))
open('%s/neuro_synchronizer.boxes' % OUT, 'w', newline='\n').write('\n'.join(lines) + '\n')
print('neuro_synchronizer', len(full), 'voxels', len(lines) - 3, 'boxes, hook', len(hook))
