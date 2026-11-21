"""Набор v3 (20.11): язык корпусов IC2 (продвинутый корпус): рамка 2 px, плиты с тонкими швами, болты 2x2 без бликов,
утопленный крестовой паз с лёгким затенением к правому нижнему краю, как у IC2. Свет только у контактов гнезда.
Палитра = палитра IC2 (корпус cdd7d8, швы b3bbbb). Запуск: python3 gen_ic2.py OUT_DIR"""
import os, sys
from PIL import Image
P  = (205,215,216); G = (179,187,187); R = (195,203,204); R2 = (189,197,198); R3 = (180,185,185)
L = (192,200,201)   # светлый шов для граней машины (тёмный b3bbbb там слишком броский)
BEZ, SOCK = (0x93,0x9a,0x9a), (0x3a,0x42,0x43)
PINS = {(0,0):(0x40,0xa5,0xa6),(1,0):(0xe8,0xc8,0x3a),(0,1):(0xa0,0x4c,0xc0),(1,1):(0x8a,0x92,0x93)}
GR=(0x5a,0x61,0x62)
def dim(c,k=.2): return tuple(round(GR[i]+(c[i]-GR[i])*k) for i in range(3))
CUR=os.path.join(os.path.dirname(os.path.abspath(__file__)),'src32','')   # исходные 32x32 кольца и экранчик (файлы мода до 21.11, из генератора этапа 5а)
class T:
    def __init__(s,bg=P): s.im=Image.new('RGB',(32,32),bg); s.px=s.im.load()
    def put(s,x,y,c):
        if 0<=x<32 and 0<=y<32: s.px[x,y]=c
    def rect(s,x0,y0,x1,y1,c):
        for x in range(x0,x1+1):
            for y in range(y0,y1+1): s.put(x,y,c)
    def box(s,x0,y0,x1,y1,c=G):
        for x in range(x0,x1+1): s.put(x,y0,c); s.put(x,y1,c)
        for y in range(y0,y1+1): s.put(x0,y,c); s.put(x1,y,c)
    def bolt(s,x,y,c=G): s.rect(x,y,x+1,y+1,c)
    def border(s,y0=0,y1=31): s.rect(0,y0,31,y0+1,G); s.rect(0,y1-1,31,y1,G); s.rect(0,y0,1,y1,G); s.rect(30,y0,31,y1,G)
    def lborder(s,y0,y1): s.box(0,y0,31,y1,L)
    def recess(s,x0,y0,x1,y1):   # утопленная площадка: ровная, у правого и нижнего края тень 1 px (как в IC2)
        s.mask_recess({(x,y) for x in range(x0,x1+1) for y in range(y0,y1+1)})
    def soft(s,x0,y0,x1,y1): s.rect(x0,y0,x1,y1,R)
    def mask_recess(s,m):
        for (x,y) in m:
            s.put(x,y, R3 if ((x+1,y) not in m or (x,y+1) not in m) else R)
def socket(t,lit):
    order=[(0,0),(1,0),(0,1),(1,1)]
    for x in range(11,21):
        for y in range(11,21):
            c=SOCK
            if x in (11,20) or y in (11,20): c=BEZ
            if x in (13,14,17,18) and y in (13,14,17,18):
                k=(x>15,y>15); c=PINS[k] if order.index(k)<lit else dim(PINS[k])
            t.put(x,y,c)

def port(on=True, pins_lit=None):
    if pins_lit is None: pins_lit=4 if on else 0
    t=T(); t.border()
    # четыре угловые плиты (как у продвинутого корпуса IC2): внешний квадрат с внутренним уголком
    for (x0,y0,sx,sy) in [(2,2,1,1),(29,2,-1,1),(2,29,1,-1),(29,29,-1,-1)]:
        xs=sorted([x0,x0+sx*9]); ys=sorted([y0,y0+sy*9])
        # шов-уголок внутри плиты
        xi=x0+sx*4; yi=y0+sy*4
        for k in range(0,7): t.put(xi+sx*k, yi, G); t.put(xi, yi+sy*k, G)
        t.bolt(min(x0+sx*1,x0+sx*2), min(y0+sy*1,y0+sy*2))
    # крестовой паз
    inner={(x,y) for x in range(13,19) for y in range(2,30)}|{(x,y) for x in range(2,30) for y in range(13,19)}
    inner|={(x,y) for x in range(10,22) for y in range(10,22) if min(x-10,21-x)+min(y-10,21-y)>=3}
    outer={(x+dx,y+dy) for (x,y) in inner for dx in (-1,0,1) for dy in (-1,0,1)}
    for (x,y) in outer-inner:
        if 2<=x<=29 and 2<=y<=29: t.put(x,y,G)
    t.mask_recess(inner)
    # болты на концах паза
    for (x,y) in [(15,3),(15,27),(3,15),(27,15)]: t.bolt(x,y)
    socket(t,pins_lit)
    return t.im

def pillars(t,y0,y1):
    for x in (0,31): t.rect(x,y0,x,y1,L)
def front(on=True):
    t=T(); pillars(t,0,21)
    t.lborder(22,31)
    src=Image.open(CUR+'replicator_front.png').convert('RGB')
    t.rect(5,24,26,29,L); t.im.paste(src.crop((6,24,26,30)),(6,24))
    for x in (3,27): t.bolt(x,26,L)
    return t.im
def side_lower(on=True):
    t=T(); pillars(t,0,21)
    t.lborder(22,31)
    t.box(6,24,25,29,L); t.soft(7,25,24,28)
    for x in (3,27): t.bolt(x,26,L)
    return t.im
def side_upper(on=True):
    t=T(); t.lborder(0,7)
    t.box(6,2,25,5,L); t.soft(7,3,24,4)
    for x in (3,27): t.bolt(x,3,L)
    pillars(t,8,31)
    return t.im
def panel(on=True):
    t=T(P)
    for x in (0,31): t.rect(x,0,x,31,L)
    return t.im
def wall(seams, upper):
    """Задняя стенка изнутри: плиты со светлыми швами и болтами, по центру вертикальный сервисный канал (сквозной через оба блока)."""
    t=T(P)
    for y in seams: t.rect(2,y,29,y,L)
    t.rect(13,0,13,31,L); t.rect(18,0,18,31,L); t.rect(14,0,17,31,R)
    for y in range(1 if upper else 3,32,4): t.rect(15,y,16,y,R3)        # скобы-ступеньки в канале
    rows=[y+3 for y in seams]+[y-4 for y in seams]
    for y in rows:
        if 0<=y<=30:
            for x in (5,25): t.bolt(x,y,L)
    return t.im
def wall_a(upper):
    """А. Спокойная: гладкая плита, один горизонтальный шов на стыке, по центру узкий канал без деталей."""
    t=T(P)
    t.rect(14,0,14,31,L); t.rect(17,0,17,31,L); t.rect(15,0,16,31,R)
    if upper: t.rect(2,20,29,20,L)
    else: t.rect(2,10,29,10,L)
    return t.im
def wall_b(upper):
    """Б. Как машины IC2: большая утопленная панель в рамке, болты только по углам панели."""
    t=T(P)
    y0,y1=(9,31) if upper else (0,20)
    t.box(4,y0 if upper else -1,27,y1+1 if upper else y1,L)
    t.soft(5,y0+1 if upper else 0,26,y1 if upper else y1-1)
    return t.im
def wall_c(upper):
    """В. Вертикальные рёбра жёсткости: три широкие полосы-панели, без болтов."""
    t=T(P)
    for x in (10,21): t.rect(x,0,x,31,L)
    t.rect(11,0,20,31,R)
    return t.im
LAMP=(0xf2,0xf6,0xf6)   # огоньки задней стенки из исходной версии (всегда белые)
def wall_v2(upper):
    """Задняя стенка v2: соосна с портом на обороте. Крестовой паз и ступица точно напротив паза и гнезда порта (x/y 13..18, ступица 10..21),
    в ступице четыре белых огонька напротив контактов; из исходной версии — столбцы белых огоньков (x 8 и 23, шаг 4) и швы x 3 / 28."""
    t=T(P)
    for x in (3,28): t.rect(x,0,x,31,L)
    for y in range(3,32,4):
        for x in (8,23): t.put(x,y,LAMP)
    inner={(x,y) for x in range(13,19) for y in range(0,32)}|{(x,y) for x in range(4,28) for y in range(13,19)}
    inner|={(x,y) for x in range(10,22) for y in range(10,22) if min(x-10,21-x)+min(y-10,21-y)>=3}
    outer={(x+dx,y+dy) for (x,y) in inner for dx in (-1,0,1) for dy in (-1,0,1)}
    for (x,y) in outer-inner:
        if 0<=x<=31 and 0<=y<=31: t.put(x,y,L)
    for (x,y) in inner: t.put(x,y,R)
    for x0 in (13,17):
        for y0 in (13,17): t.rect(x0,y0,x0+1,y0+1,LAMP)
    return t.im
def wall_v3(upper):
    """Задняя стенка v3: по образцу пола/излучателя (одобрены 20.11) — плита, рамка (здесь светлым швом), крестовой паз со ступицей,
    болты по углам; в ступице четыре белых огонька точно напротив контактов порта на обороте."""
    im=chamber_base(False); t=T(); t.im=im; t.px=im.load()
    for i in range(32):
        for j in range(3):
            for (x,y) in [(i,j),(i,31-j),(j,i),(31-j,i)]:
                if t.px[x,y]==G: t.put(x,y,P)
    for i in range(32): t.put(i,2,L); t.put(i,29,L)
    for x0 in (13,17):
        for y0 in (13,17): t.rect(x0,y0,x0+1,y0+1,LAMP)
    return t.im
UUC=(0x8a,0x4c,0xa6); UUD=(0x6e,0x3a,0x88); P2=(0xc6,0xce,0xcf)
def wall_gauge(upper, level=1.0):
    """Г1. Рёбра + уровнемер: вся стенка в мелких вертикальных рёбрах (как радиатор Б, мягко), по центру сквозное смотровое окно уровня UU в баке."""
    t=T(P)
    for x in range(2,30,2): t.rect(x,0,x,31,P2)
    t.rect(12,0,12,31,L); t.rect(19,0,19,31,L); t.rect(13,0,18,31,R)
    t.rect(14,0,17,31,(0xd8,0xe0,0xe2))       # стекло окна
    # уровень: окно идёт через оба блока (низ: строки 0..21 видимы, верх: 8..31); уровень level 0..1 по всей высоте
    H0=5/16; H1=(16+12.5)/16                 # видимая высота окна в блоках
    for y in range(32):
        yy=(1+ (31-y)/32) if upper else ((31-y)/32)   # высота пикселя в блоках от пола нижнего блока
        f=(yy-H0)/(H1-H0)
        if f<=level:
            for x in range(14,18): t.put(x,y,UUC if x in (15,16) else UUD)
    for y in range(1,32,4): t.put(13,y,L); t.put(18,y,L)   # деления шкалы
    return t.im
GRID=(1,5,9,13,17,21,25,29)   # одна решётка отверстий 2x2 с шагом 4 на всю камеру: симметрична от центра блока и не сбивается на стыках блоков
def holes(t, xs, ys, skip=lambda x,y:False):
    for x in xs:
        for y in ys:
            if not skip(x,y): t.put(x+1,y+1,L)   # 20.11 v20: точка 1 px (2x2 «громоздко»)
def wall_perf(upper):
    """Перфорация без рамок и линий (20.11: разделения «съедают» камеру): сплошная решётка GRID, у бортика и ниши крышки
    решётка просто кончается с отступом; огоньки напротив контактов — светящиеся ячейки той же решётки."""
    t=T(P)
    # v20: решётка по всей стенке без полей (у бортика и ниши крышки её обрезает геометрия)
    holes(t,GRID,GRID)
    for x0 in (13,17):
        for y0 in (13,17): t.put(x0+1,y0+1,LAMP)
    return t.im
def wall_bus(upper):
    """Г3. Коллектор: из ступицы с огоньками четыре тонких канала (по одному от каждого контакта) уходят к полу и к излучателю."""
    t=T(P)
    for x in (3,28): t.rect(x,0,x,31,L)
    for x in (9,13,18,22):
        t.rect(x,0,x+1,31,(0xb9,0xc1,0xc2)); t.rect(x-1,0,x-1,31,L); t.rect(x+2,0,x+2,31,L)
    for x in range(9,23):
        for y in range(9,23):
            k=min(x-9,22-x)+min(y-9,22-y)
            if k>=3: t.put(x,y,L if k==3 or x in (9,22) or y in (9,22) else R)
    for x0 in (13,17):
        for y0 in (13,17): t.rect(x0,y0,x0+1,y0+1,LAMP)
    return t.im
WALL='perf'   # выбранный вариант задней стенки
def wall_lower(on=True): return {'a':wall_a,'b':wall_b,'c':wall_c,'v2':wall_v2,'v3':wall_v3,'gauge':lambda u:wall_gauge(u,.6),'perf':wall_perf,'bus':wall_bus}[WALL](False)
def wall_upper(on=True): return {'a':wall_a,'b':wall_b,'c':wall_c,'v2':wall_v2,'v3':wall_v3,'gauge':lambda u:wall_gauge(u,.6),'perf':wall_perf,'bus':wall_bus}[WALL](True)
def chamber_base(ceiling):
    """Пол/потолок камеры в том же языке, что порт и задняя стенка: рамка, плиты со светлыми швами,
    крестовой паз x/y 13..18 со ступицей (в ступице кольцо или излучатель), уголки на плитах как у IC2."""
    t=T(P)
    for i in range(32): t.put(i,2,L); t.put(i,29,L); t.put(2,i,L); t.put(29,i,L)
    inner={(x,y) for x in range(13,19) for y in range(3,29)}|{(x,y) for x in range(3,29) for y in range(13,19)}
    inner|={(x,y) for x in range(8,24) for y in range(8,24) if min(x-8,23-x)+min(y-8,23-y)>=4}
    outer={(x+dx,y+dy) for (x,y) in inner for dx in (-1,0,1) for dy in (-1,0,1)}
    for (x,y) in outer-inner:
        if 3<=x<=28 and 3<=y<=28: t.put(x,y,L)
    for (x,y) in inner: t.put(x,y,R)
    for (x0,y0,sx,sy) in [(4,4,1,1),(27,4,-1,1),(4,27,1,-1),(27,27,-1,-1)]:
        t.bolt(x0 if sx>0 else x0-1, y0 if sy>0 else y0-1, L)
    return t.im
OLDBG={(0xb3,0xbb,0xbb),(0xa9,0xb2,0xb3),(0x96,0x9e,0x9f),(0x3a,0x42,0x43)}
HOLE=(0xa9,0xb2,0xb3)
SYM=(5,8,11,14,17,20,23,26)   # отверстия симметрично от центра (15.5 ± 1.5, 4.5, 7.5, 10.5)
def chamber_perf(ceiling):
    """Пол/потолок: та же решётка GRID, без рамки и кругового шва; у кольца/излучателя решётка просто уступает место."""
    t=T(P)
    holes(t,GRID,GRID,lambda x,y:(x+1-15.5)**2+(y+1-15.5)**2<9.5**2)
    return t.im
CHAMBER_STYLE='perf'
def chamber(name, shade=None):
    """Перекрашивает replicator_<name>.png: пиксели кольца/излучателя остаются, фон заменяется. Маска — по состоянию idle."""
    ceiling=name.startswith('ceiling')
    ref=Image.open(CUR+'replicator_%s_idle.png'%('ceiling' if ceiling else 'floor')).convert('RGB')
    mask=[(x,y) for x in range(32) for y in range(32) if ref.getpixel((x,y)) not in OLDBG]
    off=Image.open(CUR+'replicator_%s_off.png'%('ceiling' if ceiling else 'floor')).convert('RGB')
    glow={(x,y) for (x,y) in mask if off.getpixel((x,y))!=ref.getpixel((x,y))}   # светится только то, что меняется по состояниям (кольцо)
    src=Image.open(CUR+'replicator_%s.png'%name).convert('RGBA')
    base=(chamber_perf if CHAMBER_STYLE=='perf' else chamber_base)(ceiling).convert('RGBA')
    out=Image.new('RGBA',src.size)
    for f in range(src.height//32):
        fr=base.copy()
        if shade:   # для сцены: фон темнее (светится только кольцо), как свет на соседних гранях
            px=fr.load()
            for x in range(32):
                for y in range(32):
                    r,g,b,a=px[x,y]; px[x,y]=(round(r*shade),round(g*shade),round(b*shade),a)
        for (x,y) in mask:
            c=src.getpixel((x,y+f*32))
            if (x,y) not in glow and max(c[:3])-min(c[:3])<=12 and 140<=sum(c[:3])/3<=225:   # два оттенка: неподсвеченные серые кольца/излучателя
                q=L if sum(c[:3])/3<sum(P)/3 else P; c=q+(c[3],)
            if shade and (x,y) not in glow: c=(round(c[0]*shade),round(c[1]*shade),round(c[2]*shade),c[3])
            fr.putpixel((x,y),c)
        out.paste(fr,(0,f*32))
    return out
def well(on=True):
    """Стенки колодца (бортик пола y 2..5 -> строки 22..27, ниша крышки y 12.5..14 -> строки 4..7): светлая плита,
    светлые швы по кромкам, узкий паз посередине — продолжение языка стенки и пола."""
    t=T(P)
    # 20.11: та же решётка, что на стенке: ряд ячеек 2x2 в бортике (строки 25..26) и в нише крышки (5..6)
    # v21: стенки колодца по чётной высоте (низ строки 22..27, верх 4..7) — щель 1x2 ровно посередине
    for x in GRID:
        for y0 in (24,5): t.rect(x+1,y0,x+1,y0+1,L)
    return t.im
def two_tone(im, keep=()):
    """Сводит все нейтральные серые корпуса к двум: P (корпус) и L (шов). keep — прямоугольники, которые не трогаем (гнездо, экран)."""
    im=im.convert('RGB'); px=im.load(); pa=sum(P)/3
    for x in range(32):
        for y in range(im.height):
            if any(x0<=x<=x1 and y0<=y%32<=y1 for (x0,y0,x1,y1) in keep): continue
            c=px[x,y]; a=sum(c)/3
            if max(c)-min(c)<=12 and 140<=a<=225 and c!=P: px[x,y]=L if a<pa else P
    return im
SET={'port':port,'well':well,'wall_lower':wall_lower,'wall_upper':wall_upper,'front':front,'side_lower':side_lower,'side_upper':side_upper,'panel':panel}
_RAW=dict(SET)
_KEEP={'port':[(11,11,20,20)],'front':[(5,24,26,29)]}
for _k in list(SET):
    SET[_k]=(lambda f,k: (lambda on=True: two_tone(f(on), _KEEP.get(k,()))))(_RAW[_k],_k)
_port_raw=port
def port(on=True, pins_lit=None): return two_tone(_port_raw(on,pins_lit),[(11,11,20,20)])
SET['port']=port
_chamber_perf_raw=chamber_perf
def chamber_perf(ceiling): return two_tone(_chamber_perf_raw(ceiling))
def main(out):
    os.makedirs(out,exist_ok=True)
    for k,fn in SET.items(): fn(True).save(os.path.join(out,'v3_%s.png'%k))
    port(False).save(os.path.join(out,'v3_port_off.png'))
    ims=[(k,Image.open(os.path.join(out,'v3_%s.png'%k))) for k in SET]   # лист набора (без текстур IC2: их в репозитории нет)
    S=8;W=32*S
    o=Image.new('RGB',((W+10)*len(ims),W),(30,30,30))
    for i,(k,im) in enumerate(ims): o.paste(im.resize((W,W),Image.NEAREST),(i*(W+10),0))
    o.save(os.path.join(out,'set_v3.png'))
if __name__=='__main__': main(sys.argv[1] if len(sys.argv)>1 else 'out')
