# Генераторы графики Matter Rebirth

Вся графика мода своя и рисуется этими скриптами (Python 3 + Pillow). Без аргумента скрипт пишет прямо в
`src/main/resources/assets/matterrebirth`, с аргументом `OUT_DIR` — в указанную папку (для сверки).
Каждый файл мода ниже сверен попиксельно с выводом своего скрипта (21.11.2026).

| Скрипт | Что рисует в моде |
|---|---|
| `gen_textures.py` | предметы (шприц, образец крови, смесь, кристалл, якорь), пустой слой брони синхронизатора |
| `gen_gui_texture.py` | окно машины `gui/replicator.png` (аргумент — файл, а не папка) |
| `replicator/build_replicator.py` | репликатор: корпус 32x32 (`gen_ic2.py`), интерьер капсулы, пол и излучатель 64x64 во всех состояниях колец (`gen_capsule2.py` на основе `gen_capsule.py`), стекло камеры (`gen_textures5a.py`), конструкционный блок `replicator_casing` (порт с погашенными контактами) |
| `clone/gen_layers11.py` | слои клона: мышцы, сосуды, нервы, объём туловища `models/entity/clone_inner.json` и его палитра |
| `clone/gen_cut.py` | плитка светящегося среза `entity/clone_cut.png` (с `OUT_DIR` — ещё лист вариантов, ему нужен ванильный `skeleton.png` в `MC_SKELETON`) |
| `neuro/gen_core_atlas.py` | ядро панели «Нейросвязь» `gui/core_states.png` и тревога ядра `gui/core_alarm.png` (`gen_core.py`, `gen_core_states.py`) |
| `sync/gen_sync_icons.py` | значки нейросинхронизатора (обычный и накладка кожаного шлема) |
| `sync/gen_sync_boxes.py` | синхронизатор на голове `models/armor/neuro_synchronizer.boxes` (`worn_concept.py`, `sync_fit.py`) |

Вспомогательные модули: `replicator/gen_hollow.py` (геометрия и палитра машины этапа 5а), `replicator/gen_surfaces.py`,
`clone/gen5b2.py` (слои клона v9, на них опирается срез).

`replicator/src32/` — исходные 32x32 текстуры экранчика и колец пола и излучателя этапа 5а (вывод
`gen_textures5a.py`): из них `build_replicator.py` берёт рисунок колец и экранчика. `gen_textures5a.py` запускать
только с `OUT_DIR`: он рисует и прежний корпус 32x32, который в мод больше не идёт.
