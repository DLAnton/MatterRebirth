"""Интерьер капсулы репликатора (20.11, пересмотр): варианты перфорации, нарисованные по ВИДИМОЙ геометрии камеры,
а не по плитке 32x32. Корпус снаружи (порт, фасад, бока, стойки) по-прежнему в gen_ic2.py.

Что видно в игре (модели replicator_lower/upper.json):
  задняя стенка  — одна поверхность от пола до потолка, 56 строк: верхний блок строки 4..31, нижний 0..27.
                   Из них стенка над бортиком: нижний блок 0..21, верхний 7..31; за бортиком пола (колодец) 22..27, в нише крышки 4..6.
                   По ширине между рейками проекторов свободны столбцы 5..26.
  бортик пола    — полоса высотой 6 px (строки 22..27 текстуры well), ниша крышки — 3 px (строки 4..6), видны столбцы 3..28.
  пол / потолок  — дно 26x26 (строки и столбцы 3..28), кромка 3 px = верх бортиков.

Решётка: отверстие 1 px, шаг 3, столбцы 5,8,...,26 (симметрично от оси порта, поля по 2 px от стенок колодца).
Ряды стенки идут ровно через шов блоков (нижний блок 1,4,...,19; верхний 9,12,...,30); в зоне колодца ряды 23 и 26
(поля 1-2-1 по высоте бортика), в нише крышки ряд 5 (поля 1-1): те же ряды на боковых бортиках, поэтому в углах всё сходится.
Только два серых: корпус cdd7d8 и шов c0c8c9 (решение 20.11); огоньки белые; кольцо и излучатель берутся из текстур мода.

Запуск: python3 gen_capsule.py OUT_DIR  -> текстуры каждого варианта и развёртка capsule_flat.png"""
import os, sys
from PIL import Image, ImageDraw, ImageFont
P=(205,215,216); L=(192,200,201); LAMP=(0xf2,0xf6,0xf6)
CUR=os.path.join(os.path.dirname(os.path.abspath(__file__)),'src32','')   # исходные 32x32 кольца и экранчик (файлы мода до 21.11, из генератора этапа 5а)
OLDBG={(0xb3,0xbb,0xbb),(0xa9,0xb2,0xb3),(0x96,0x9e,0x9f),(0x3a,0x42,0x43)}   # фон старых текстур пола/потолка
DISC=(0xc4,0xcc,0xcd)                                                           # диск площадки внутри кольца в текстуре мода
C8=(5,8,11,14,17,20,23,26)             # столбцы решётки (и строки на полу/потолке)
BAND=(5,8,11,20,23,26)                 # «Дорожки»: по три столбца у каждой рейки
WL=(1,4,7,10,13,16,19)                 # ряды стенки, нижний блок (над бортиком)
WU=(9,12,15,18,21,24,27,30)            # ряды стенки, верхний блок (под нишей)
WELL=(23,26); NICHE=(5,)               # ряды в зоне колодца (нижний блок) и в нише крышки (верхний блок)
LAMPS_LO=(13,16); LAMPS_UP=(15,18)     # четыре отверстия решётки напротив гнезда порта светятся белым (столбцы 14 и 17)

def blank(): return Image.new('RGB',(32,32),P)
def _mask(kind, only=None):
    ref=Image.open(CUR+'replicator_%s_idle.png'%kind).convert('RGB')
    return {(x,y) for x in range(32) for y in range(32) if (ref.getpixel((x,y))==only if only else ref.getpixel((x,y)) not in OLDBG)}
RM=_mask('floor'); EM=_mask('ceiling'); DM=_mask('floor',DISC)
def near(mask,x,y,d=1): return any((x+dx,y+dy) in mask for dx in range(-d,d+1) for dy in range(-d,d+1))

def wall(cols=C8, slits=False):
    """Задняя стенка: (wall_lower, wall_upper). Строки 22..27 нижней и 4..6 верхней текстуры — продолжение стенки за бортиком и в нише
    (в модели задний бортик берёт их из текстуры стенки, см. заметку в конце файла)."""
    lo=blank(); up=blank(); pl=lo.load(); pu=up.load()
    for x in cols:
        for r in WL: pl[x,r]=L
        for r in WU: pu[x,r]=L
        for r in (range(23,27) if slits else WELL): pl[x,r]=L
        for r in NICHE: pu[x,r]=L
    for x in (14,17):
        for r in LAMPS_LO: pl[x,r]=LAMP
        for r in LAMPS_UP: pu[x,r]=LAMP
    return lo,up
def strips(slits=False):
    """Боковые и передний бортики (текстура well): колодец — два ряда отверстий (или щели 1x4 «радиатор»), ниша крышки — один ряд."""
    t=blank(); px=t.load()
    for x in C8:
        for y in (range(23,27) if slits else WELL): px[x,y]=L
        for y in NICHE: px[x,y]=L
    return t
def floor_bg(cols=C8, nozzle=True):
    """Фон пола: решётка вокруг кольца; nozzle — 12 отверстий-сопел внутри кольца (оттуда идут пузырьки)."""
    b=blank(); px=b.load()
    for x in C8:
        for y in C8:
            if (x,y) in DM:
                if nozzle and not near(RM-DM,x,y): px[x,y]=L
            elif x in cols and not near(RM,x,y): px[x,y]=L
    return b
def ceiling_bg(cols=C8):
    b=blank(); px=b.load()
    for x in cols:
        for y in C8:
            if not near(EM,x,y): px[x,y]=L
    return b

def chamber(bg, name, clean_disc=False, shade=None):
    """Пол/потолок целиком (все кадры): фон bg + кольцо/излучатель из replicator_<name>.png мода.
    Несветящиеся серые сводятся к двум оттенкам; clean_disc — диск площадки цветом корпуса (под сопла);
    shade — затемнение несветящегося (только для сцены раскадровки, где пол и потолок рисуются без затенения)."""
    kind='ceiling' if name.startswith('ceiling') else 'floor'
    ref=Image.open(CUR+'replicator_%s_idle.png'%kind).convert('RGB')
    off=Image.open(CUR+'replicator_%s_off.png'%kind).convert('RGB')
    src=Image.open(CUR+'replicator_%s.png'%name).convert('RGB')
    out=Image.new('RGB',src.size); pa=sum(P)/3
    k=lambda c:c if not shade else tuple(round(v*shade) for v in c)
    for f in range(src.height//32):
        fr=bg.copy(); px=fr.load()
        for x in range(32):
            for y in range(32):
                r=ref.getpixel((x,y))
                if r in OLDBG: px[x,y]=k(px[x,y]); continue
                glow=off.getpixel((x,y))!=r
                c=src.getpixel((x,y+f*32))
                if glow: px[x,y]=c; continue
                if max(c)-min(c)<=12 and 140<=sum(c)/3<=225:
                    if clean_disc and r==DISC: c=px[x,y]
                    else: c=L if sum(c)/3<pa else P
                px[x,y]=k(c)
        out.paste(fr,(0,f*32))
    return out

def _variant(title, note, cols, slits, nozzle):
    lo,up=wall(cols)
    return {'title':title,'note':note,'wall_lower':lo,'wall_upper':up,'well':strips(slits),
            'floor_bg':floor_bg(cols,nozzle),'ceiling_bg':ceiling_bg(cols),'clean_disc':nozzle}
def variants():
    return {
     'v1':_variant('1. Лист','Одна мелкая решётка от пола до потолка. Та же на бортиках, полу и излучателе.',C8,False,True),
     'v2':_variant('2. Дорожки','Решётка двумя полосами вдоль реек, середина чистая. За клоном ровный фон.',BAND,False,False),
     'v3':_variant('3. Лист с радиатором','Как «Лист», но на бортиках колодца вертикальные щели вместо отверстий.',C8,True,True),
    }

# ---------- развёртка для проверки ----------
def _font(sz):
    try: return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',sz)
    except Exception: return ImageFont.load_default()
def flat(v, S=8):
    """Стенка целиком (как видна: 32x56), пол, потолок, бортик колодца и ниши. Без затенения."""
    W=Image.new('RGB',(32,56),P); W.paste(v['wall_upper'].crop((0,4,32,32)),(0,0)); W.paste(v['wall_lower'].crop((0,0,32,28)),(0,28))
    wp=W.load(); D=(120,130,133)
    for Y in range(56):
        for x in range(32):
            if (Y>=50 or Y<3) and (x<3 or x>28): wp[x,Y]=D          # бортики закрывают края
            elif x<1 or x>30: wp[x,Y]=D
    for Y in range(7,46):
        for x in (3,28): wp[x,Y]=(236,242,243)                      # рейки проекторов
    fl=chamber(v['floor_bg'],'floor_grow',v['clean_disc']).crop((0,0,32,32)); ce=chamber(v['ceiling_bg'],'ceiling_grow').crop((0,0,32,32))
    st=v['well'].crop((3,22,29,28)); sn=v['well'].crop((3,4,29,7))
    g=10; o=Image.new('RGB',(32*S+g+32*S+g+26*S,56*S),(22,27,31))
    o.paste(W.resize((32*S,56*S),Image.NEAREST),(0,0))
    x1=32*S+g
    o.paste(ce.resize((24*S,24*S),Image.NEAREST),(x1,0)); o.paste(fl.resize((24*S,24*S),Image.NEAREST),(x1,56*S-24*S))
    x2=x1+24*S+g
    o=o.crop((0,0,x2+26*S,56*S))
    o.paste(sn.resize((26*S,3*S),Image.NEAREST),(x2,0)); o.paste(st.resize((26*S,6*S),Image.NEAREST),(x2,56*S-6*S))
    return o
def main(out):
    os.makedirs(out,exist_ok=True)
    V=variants(); rows=[]
    for k,v in V.items():
        for t in ('wall_lower','wall_upper','well'): v[t].save(os.path.join(out,'capsule_%s_%s.png'%(k,t)))
        for nm in ('floor_off','floor_idle','floor_grow','ceiling_off','ceiling_idle','ceiling_grow'):
            chamber(v['floor_bg' if nm.startswith('floor') else 'ceiling_bg'],nm,v['clean_disc'] and nm.startswith('floor')).save(os.path.join(out,'capsule_%s_%s.png'%(k,nm)))
        rows.append((v['title'],flat(v)))
    w=max(r.width for _,r in rows); h=rows[0][1].height
    o=Image.new('RGB',(len(rows)*(w+24)+24,h+60),(22,27,31)); d=ImageDraw.Draw(o)
    for i,(t,im) in enumerate(rows):
        x=24+i*(w+24); o.paste(im,(x,44)); d.text((x,12),t,font=_font(20),fill=(223,231,234))
    o.save(os.path.join(out,'capsule_flat.png'))
if __name__=='__main__': main(sys.argv[1] if len(sys.argv)>1 else 'out')

# Заметка для кода мода (когда вариант будет выбран): у заднего бортика пола и задней стенки ниши крышки
# грань north сейчас ссылается на #well; для «Дорожек» её нужно перевести на #wall с uv [0,11,16,14] (низ) и [0,2,16,3.5] (верх),
# чтобы стенка была одной текстурой от пола до потолка. Для «Листа» ряды совпадают и модель можно не трогать.
# Боковые бортики в модели берут из well строки 26..31 (низ) и 0..2 (верх), а здесь нарисованы 22..27 и 4..6 (как в сцене
# раскадровки, по координатам блока): при переносе в мод поправить uv бортиков на [.., 11, .., 14] и [.., 2, .., 3.5].
