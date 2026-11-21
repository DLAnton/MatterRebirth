"""Интерьер капсулы, текстуры 64x64 (20.11, после отзыва Антона на «Дорожки»: отверстия мельче, их больше, бортики колодца — радиатор).
Три масштаба деталей: чистые плиты; рёбра радиатора (пояс в колодце и короткие рёбра в нише крышки, со всех четырёх сторон);
мелкая перфорация полосами вдоль реек. Геометрия та же, что в gen_capsule.py, координаты удвоены.
Запуск: python3 gen_capsule2.py OUT_DIR"""
import sys, os
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_capsule as GC
P,L,LAMP=GC.P,GC.L,GC.LAMP
N=64
BL=(11,14,17,20,23); BR=tuple(63-x for x in BL)   # столбцы полос, от рейки к середине (слева и зеркально справа)
WL=tuple(range(1,44,3)); WU=tuple(range(14,63,3)) # ряды полос на стенке: нижний блок над бортиком, верхний под нишей (шаг 3 через шов блоков)
FR=tuple(range(9,55,3))                            # ряды полос на полу и потолке (поля по 3 px)
FIN=tuple(range(10,54,6))                          # рёбра радиатора шириной 2 px: столбцы 10-11, 16-17, ..., 52-53
FIN_LO=(46,54); FIN_UP=(10,12)                     # строки рёбер: колодец 46..53 (поля по 2), ниша крышки 10..11 (поля по 2)
LAMPXY=(27,35)                                     # огоньки 2x2 точно по центрам контактов порта (контакты 26..29 и 34..37)
def blank(): return Image.new('RGB',(N,N),P)
def keep(ci,ri,dissolve):
    """«Растворение»: три столбца у рейки полные, четвёртый через ряд, пятый каждый четвёртый ряд."""
    if not dissolve or ci<3: return True
    return ri%2==0 if ci==3 else ri%4==1
def fins(px,rows,col=L):
    for x in FIN:
        for y in range(*rows): px[x,y]=col; px[x+1,y]=col
def wall(dissolve=False):
    lo=blank(); up=blank(); pl=lo.load(); pu=up.load()
    for cols in (BL,BR):
        for ci,x in enumerate(cols):
            for r in WL:
                if keep(ci,(r-1)//3,dissolve): pl[x,r]=L
            for r in WU:
                if keep(ci,(62-r)//3,dissolve): pu[x,r]=L
    fins(pl,FIN_LO); fins(pu,FIN_UP)
    for x in LAMPXY:
        for y in LAMPXY:
            for dx in (0,1):
                for dy in (0,1): pl[x+dx,y+dy]=LAMP; pu[x+dx,y+dy]=LAMP
    return lo,up
def strips():
    t=blank(); px=t.load(); fins(px,FIN_LO); fins(px,FIN_UP); return t
def glow():
    """Маска рёбер радиатора в колодце (белые на прозрачном): накладка, которая светится цветом кольца."""
    t=Image.new('RGBA',(N,N),(0,0,0,0)); fins(t.load(),FIN_LO,(255,255,255,255)); return t
def up2(m): return {(2*x+dx,2*y+dy) for (x,y) in m for dx in (0,1) for dy in (0,1)}
RM=up2(GC.RM); EM=up2(GC.EM)
def near(m,x,y,d=2): return any((x+dx,y+dy) in m for dx in range(-d,d+1) for dy in range(-d,d+1))
def plane(mask,dissolve=False):
    b=blank(); px=b.load()
    for cols in (BL,BR):
        for ci,x in enumerate(cols):
            for ri,y in enumerate(FR):
                if keep(ci,min(ri,len(FR)-1-ri),dissolve) and not near(mask,x,y): px[x,y]=L
    return b
def chamber(bg,name,shade=None):
    """Первый кадр пола/потолка: фон bg 64x64 + кольцо/излучатель из текстуры мода (каждый её пиксель = 2x2)."""
    kind='ceiling' if name.startswith('ceiling') else 'floor'
    ref=Image.open(GC.CUR+'replicator_%s_idle.png'%kind).convert('RGB'); off=Image.open(GC.CUR+'replicator_%s_off.png'%kind).convert('RGB')
    src=Image.open(GC.CUR+'replicator_%s.png'%name).convert('RGB')
    fr=bg.copy(); px=fr.load(); pa=sum(P)/3
    k=lambda c:c if not shade else tuple(round(v*shade) for v in c)
    for X in range(N):
        for Y in range(N):
            x,y=X//2,Y//2; r=ref.getpixel((x,y))
            if r in GC.OLDBG: px[X,Y]=k(px[X,Y]); continue
            c=src.getpixel((x,y))
            if off.getpixel((x,y))!=r: px[X,Y]=c; continue
            if max(c)-min(c)<=12 and 140<=sum(c)/3<=225: c=L if sum(c)/3<pa else P
            px[X,Y]=k(c)
    return fr
def glow_colors():
    """Цвет свечения кольца в каждом состоянии (самый частый светящийся пиксель текстуры пола)."""
    ref=Image.open(GC.CUR+'replicator_floor_idle.png').convert('RGB'); off=Image.open(GC.CUR+'replicator_floor_off.png').convert('RGB')
    out={}
    for st in ('idle','grow','ok'):
        src=Image.open(GC.CUR+'replicator_floor_%s.png'%st).convert('RGB'); cnt={}
        for x in range(32):
            for y in range(32):
                if ref.getpixel((x,y)) not in GC.OLDBG and off.getpixel((x,y))!=ref.getpixel((x,y)):
                    c=src.getpixel((x,y)); cnt[c]=cnt.get(c,0)+1
        out[st]='#%02x%02x%02x'%max(cnt,key=cnt.get)
    return out
def variants():
    def v(title,note,dissolve,glow):
        lo,up=wall(dissolve)
        return {'title':title,'note':note,'wall_lower':lo,'wall_upper':up,'well':strips(),'floor_bg':plane(RM,dissolve),'ceiling_bg':plane(EM,dissolve),'glow':glow}
    # 20.11 Антон выбрал «Мелкую сетку». Отвергнуты: растворение полос к середине (dissolve) и подсветка рёбер радиатора (glow).
    return {'d1':v('Дорожки, мелкая сетка','Полосы мелкой перфорации вдоль реек, в колодце пояс радиатора со всех четырёх сторон.',False,False)}
if __name__=='__main__':
    out=sys.argv[1] if len(sys.argv)>1 else 'out'; os.makedirs(out,exist_ok=True)
    for k,v in variants().items():
        for t in ('wall_lower','wall_upper','well'): v[t].save(os.path.join(out,'capsule64_%s_%s.png'%(k,t)))
        for nm in ('floor_idle','floor_grow','ceiling_idle','ceiling_grow'):
            chamber(v['floor_bg' if nm.startswith('floor') else 'ceiling_bg'],nm).save(os.path.join(out,'capsule64_%s_%s.png'%(k,nm)))
    glow().save(os.path.join(out,'capsule64_wellglow.png')); print(glow_colors())
