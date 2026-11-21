# Синхронизатор на голове для мода (14.11, после проверок Антона): геометрия концепта 05.11 (worn_concept.device)
# с тремя слоями посадки: s — слой корпусов (0 — на коже, 3 — на кожаной шапке, она на пиксель шире головы);
# усики над макушкой и на лбу — на слое s + t; боковые стойки усиков — на слое s + side.
# Антон 14.11: в кожаном боковые стойки шли по наружному краю корпуса и между ними и шапкой была щель — стойки
# переносим на внутренний край корпуса, вплотную к шапке (side = 0). У обычного стойки во внешнем слое корпуса
# (side = 1), но толщиной 2 вокселя к голове (thick = 2): так они лежат на коже, как и в кожаном, а волосы второго
# слоя скина (на 1/2 пикселя) остаются внутри усиков и их не закрывают (Антон 14.11: у обычного та же щель).
# Сенсоры — как на утверждённом листе v3: втулка и бирюзовый сенсор на конце усика (вариант «под усиком» отклонён).
import worn_concept as w
line3 = w.line3

def device_fit(kind, s, t, side, thick=1):
    v = {}
    n, x0, z0 = 24, 4, 4
    XR = x0 + n + s          # корпуса: слои s, s+1
    XA = x0 + n + s + side   # боковые стойки усиков
    TOP = n + s + t          # над макушкой
    FR = z0 + n + s + t      # перед лбом
    mir = lambda p: (2 * x0 + n - 1 - p[0], p[1], p[2])
    PADS = set()

    def put(p, c, both=True):
        v[p] = c
        if both:
            v[mir(p)] = c

    def inward(p):
        """Шаг к голове по оси, где точка дальше всего за пределами головы (x0..x0+n-1, 0..n-1, z0..z0+n-1)."""
        best, step = 0, None
        for i, lo, hi in ((0, x0, x0 + n - 1), (1, -10**6, n - 1), (2, z0, z0 + n - 1)):
            d = p[i] - hi if p[i] > hi else lo - p[i] if p[i] < lo else 0
            if d > best:
                best, step = d, tuple((-1 if p[i] > hi else 1) if j == i else 0 for j in range(3))
        return step

    def put_arm(p, c):
        """Воксель усика; при thick > 1 усик утолщён к голове, чтобы лечь на кожу (не глубже её поверхности)."""
        put(p, c)
        d = inward(p)
        q = p
        for _ in range(thick - 1):
            if d is None:
                break
            q = (q[0] + d[0], q[1] + d[1], q[2] + d[2])
            if inward(q) is None or q in PADS:
                break
            put(q, c)

    def arm(pts, tip=True):
        path = []
        for a, b in zip(pts, pts[1:]):
            path += line3(a, b)[:-1]
        path.append(pts[-1])
        for p in path[:-2]:
            if p not in PADS:
                put_arm(p, w.ARM)
        if tip:
            put_arm(path[-2], w.SLEEVE); put_arm(path[-1], w.CYAN)
        else:
            put(path[-2], w.ARM); put(path[-1], w.ARM)

    BZ = z0 + n - 9
    for z in range(z0 - 1 - s, BZ):
        tt = (BZ - z) / (BZ - z0 + 1 + s)
        yb = round(13 + 3 * tt)
        for xx in (XR, XR + 1):
            put((xx, yb, z), w.BAND); put((xx, yb + 1, z), w.BAND_L)
    for x in range(x0 - 1 - s, XR + 2):
        for zz in (z0 - 1 - s, z0 - 2 - s):
            v[(x, 16, zz)] = w.BAND; v[(x, 17, zz)] = w.BAND_L
    for y in range(11, 19):
        for z in range(BZ, FR - 1):
            for xx in (XR, XR + 1):
                edge = y in (11, 18) or z in (BZ, FR - 2)
                put((xx, y, z), w.BAND_D if edge else w.BAND)
                PADS.add((xx, y, z)); PADS.add(mir((xx, y, z)))
    cz = (BZ + FR - 2) // 2
    for (dy, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
        c = w.CYAN if (dy, dz) == (0, 0) else w.GEM_L if dy > 0 else w.GEM if dy == 0 else w.GEM_D
        put((XR + 2, 15 + dy, cz + dz), c)
    for dz, c in ((-1, (0x3a, 0x7a, 0x3a)), (0, (0x2a, 0x5a, 0x2a)), (1, (0xd0, 0x30, 0x30))):
        put((XR + 2, 12, cz + dz), c)
    arm([(XA, 18, cz - 1), (XA, TOP, cz - 2), (x0 + n - 7, TOP, cz - 3)])
    arm([(XA, 18, cz + 2), (XA, TOP, FR - 2), (x0 + n - 5, TOP, FR), (x0 + n - 5, n - 4, FR)])
    arm([(XA, 16, FR - 1), (x0 + n - 2, 17, FR), (x0 + n - 3, 17, FR)])
    arm([(XA, 18, BZ + 1), (XA, TOP, BZ - 3), (x0 + n - 9, TOP, BZ - 5)])
    arm([(XA, 17, z0 + 7), (XA, TOP - 2, z0 + 3), (XA, TOP - 2, z0 + 2)])
    arm([(XA, 15, z0 + 3), (XA, TOP, z0 - 1 - s), (x0 + n - 6, TOP, z0 - 1 - s)])
    if kind != 'leather':
        arm([(XR + 1, 11, BZ + 1), (XR + 1, 7, BZ - 1), (XR + 1, 4, BZ - 3)], tip=False)   # крючок за ухом
    return v
