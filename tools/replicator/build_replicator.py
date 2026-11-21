"""Текстуры репликатора (корпус и интерьер капсулы, раскадровка v25, утверждено Антоном 20.11).

Корпус 32x32 — gen_ic2.py (язык корпусов IC2): front, side_lower, side_upper, panel, port (= port_top, port_bottom);
конструкционный блок replicator_casing — порт с погашенными контактами.
Интерьер 64x64 — gen_capsule2.py, вариант d1 «Дорожки, мелкая сетка»: wall_lower, wall_upper, well,
пол и излучатель во всех состояниях колец (off, idle, grow, ok, wait, alert, rollback).
Кольца и излучатель берутся из исходных 32x32 текстур состояний в src32/ (файлы мода до 21.11, этап 5а) и кладутся
на новый фон пикселями 2x2; откат — покадрово (60 кадров, .mcmeta рядом с текстурой в моде не меняется).
src32/ — вывод gen_textures5a.py (сверено попиксельно 21.11). Стекло glass_lower/upper тоже берётся из gen_textures5a.

Запуск: python tools/replicator/build_replicator.py           -> прямо в textures/blocks мода
        python tools/replicator/build_replicator.py OUT_DIR   -> в OUT_DIR (для сверки)
"""
import os
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_ic2 as GS  # noqa: E402
import gen_capsule as GC  # noqa: E402
import gen_capsule2 as C2  # noqa: E402

SRC = os.path.join(HERE, 'src32', '')
BLOCKS = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'matterrebirth', 'textures', 'blocks')
STATES = ('off', 'idle', 'grow', 'ok', 'wait', 'alert')


def main(out):
    os.makedirs(out, exist_ok=True)
    save = lambda im, n: im.save(os.path.join(out, 'replicator_%s.png' % n))
    for k in ('front', 'side_lower', 'side_upper', 'panel'):
        save(GS.SET[k](True), k)
    port = GS.port(True)
    for n in ('port', 'port_top', 'port_bottom'):
        save(port, n)
    # конструкционный блок — тот же порт с погашенными контактами, одна текстура на все стороны (Антон 20.11)
    save(GS.port(False), 'casing')
    v = C2.variants()['d1']
    for t in ('wall_lower', 'wall_upper', 'well'):
        save(v[t], t)
    for kind in ('floor', 'ceiling'):
        bg = v[kind + '_bg']
        for st in STATES:
            save(C2.chamber(bg, kind + '_' + st), kind + '_' + st)
        # откат: chamber рисует один кадр 32x32, поэтому каждый кадр исходника — отдельным файлом во временной папке
        src = Image.open(SRC + 'replicator_%s_rollback.png' % kind).convert('RGB')
        n = src.height // 32
        sheet = Image.new('RGB', (64, 64 * n))
        with tempfile.TemporaryDirectory() as tmp:
            for s in ('idle', 'off'):
                Image.open(SRC + 'replicator_%s_%s.png' % (kind, s)).save(os.path.join(tmp, 'replicator_%s_%s.png' % (kind, s)))
            GC.CUR = os.path.join(tmp, '')
            try:
                for f in range(n):
                    src.crop((0, 32 * f, 32, 32 * f + 32)).save(os.path.join(tmp, 'replicator_%s_frame.png' % kind))
                    sheet.paste(C2.chamber(bg, kind + '_frame'), (0, 64 * f))
            finally:
                GC.CUR = SRC
        save(sheet, kind + '_rollback')
    # стекло камеры — из генератора этапа 5а (остальной его вывод — прежний корпус, в мод не идёт)
    import gen_textures5a as T5
    with tempfile.TemporaryDirectory() as tmp:
        T5.main(tmp)
        for n in ('glass_lower', 'glass_upper'):
            save(Image.open(os.path.join(tmp, 'replicator_%s.png' % n)), n)
    print('ok:', os.path.abspath(out))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else BLOCKS)
