package dlx.matterrebirth.mind;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRConfig;
import dlx.matterrebirth.item.NeuroLink;
import dlx.matterrebirth.network.CloneAlertMessage;
import dlx.matterrebirth.network.CloneListMessage;
import dlx.matterrebirth.network.MRNetwork;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Уведомления о беде клонов через шлем (6д-1, концепт Антона 09.11 и 16.11). Раз в секунду сервер обходит загруженные
 * машины (они всегда держат свой чанк, ReplicatorChunks) и сообщает игроку о бедах клонов его крови (UUID шаблона;
 * кто собрал машину — неважно).
 * <p>
 * Беда (TileReplicator.getTrouble): клон теряет здоровье (нет UU или энергии), откат роста, гибель. Начало беды —
 * сразу, конец — только если её нет QUIET проверок подряд: питание на границе (сыт / голоден через тик) не даёт
 * повторов. Смена причины (нет EU → нет UU) — та же беда.
 * <p>
 * Связь: шлем с нейросвязью на голове (синхронизатор или квантовый нейрошлем); машина в том же измерении или с
 * работающим якорем. Якорь стоит, а машина обесточена — игроку в другом измерении вместо беды «связь прервана».
 * Связь после обесточивания восстанавливается через LINK_QUIET проверок питания — дольше, чем гаснет беда, чтобы
 * вернувшаяся связь не принесла устаревшее «теряет здоровье · нет EU».
 * <p>
 * Что игрок уже знает: для каждого игрока — машина → вид беды, о которой ему сообщено. Каждую секунду сообщается
 * всё, чего он ещё не знает; потерял связь (снял шлем, вышел, ушёл из зоны) — знание стирается, и при новой связи
 * текущие беды приходят одной сводкой (Антон 17.11). Беды нескольких клонов в одну секунду — одна карточка на вид
 * беды. Только серверный поток.
 * <p>
 * Панель «Нейросвязь» (6д-2): тем же обходом игроку в квантовом нейрошлеме уходит список его клонов — в своём
 * измерении все, из других только машины с якорем (якорь без энергии — «нет связи», без состояния клона). Список
 * уходит, только если изменился; снял нейрошлем — пустой список. Вид панели (режим HUD IC2) выбирает клиент.
 */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID)
public final class CloneWatch {

    public static final int NONE = TileReplicator.TROUBLE_NONE;
    public static final int HEALTH = TileReplicator.TROUBLE_HEALTH;
    public static final int ROLLBACK = TileReplicator.TROUBLE_ROLLBACK;
    public static final int DEAD = TileReplicator.TROUBLE_DEAD;
    /** Не беда машины, а беда связи: игрок в другом измерении, якорь машины без энергии. */
    public static final int LINK_LOST = 4;

    /** Причины потери здоровья (биты). */
    public static final int NO_UU = 1;
    public static final int NO_EU = 2;

    /** Проверок (секунд) без беды, после которых она закончилась. */
    private static final int QUIET = 5;
    /** Проверок (секунд) с питанием, после которых связь через якорь вернулась (дольше QUIET, см. описание). */
    private static final int LINK_QUIET = 8;
    private static final int CHECK_TICKS = 20;

    private static final int LINK_NONE = 0;
    private static final int LINK_OK = 1;
    private static final int LINK_DOWN = 2;

    /** Порядок карточек в пачке: самое тяжёлое первым. */
    private static final int[] ORDER = {DEAD, HEALTH, LINK_LOST, ROLLBACK};

    /** Сглаженное состояние каждой машины; машина выгружена или сломана — запись уходит сама. */
    private static final Map<TileReplicator, Watch> MACHINES = new WeakHashMap<>();
    /** Игрок → (машина → вид беды, о которой ему уже сообщено). */
    private static final Map<UUID, Map<String, Integer>> KNOWN = new HashMap<>();
    /** Игроки, у которых на прошлой проверке был шлем с нейросвязью (только для отладочного журнала). */
    private static final Set<UUID> LINKED = new HashSet<>();
    /** Игрок в нейрошлеме → последний отправленный ему список клонов панели. */
    private static final Map<UUID, List<CloneListMessage.Entry>> PANEL_SENT = new HashMap<>();
    /** Игрок → краткая сводка последнего списка (только для отладочного журнала: пишется при смене состава и состояний). */
    private static final Map<UUID, String> PANEL_LOGGED = new HashMap<>();
    /** Порядок списка панели для сравнения с отправленным: по измерению и месту машины. */
    private static final Comparator<CloneListMessage.Entry> PANEL_ORDER = Comparator
            .comparingInt((CloneListMessage.Entry e) -> e.dim).thenComparingInt(e -> e.x).thenComparingInt(e -> e.y)
            .thenComparingInt(e -> e.z);

    private static int ticks;

    private CloneWatch() {
    }

    /** Новый мир (одиночная игра или запуск сервера): ничего не известно. */
    public static void reset() {
        MACHINES.clear();
        KNOWN.clear();
        LINKED.clear();
        PANEL_SENT.clear();
        PANEL_LOGGED.clear();
        ticks = 0;
    }

    /** Вышел из игры — при входе текущие беды придут сводкой (иначе в одиночной игре знание переживало бы выход). */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        KNOWN.remove(event.player.getUniqueID());
        LINKED.remove(event.player.getUniqueID());
        PANEL_SENT.remove(event.player.getUniqueID());
        PANEL_LOGGED.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < CHECK_TICKS) {
            return;
        }
        ticks = 0;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            check(server);
        }
    }

    private static void check(MinecraftServer server) {
        Map<UUID, List<Seen>> byOwner = new HashMap<>();
        for (WorldServer world : DimensionManager.getWorlds()) {
            int dim = world.provider.getDimension();
            String dimName = world.provider.getDimensionType().getName();
            for (TileEntity te : world.loadedTileEntityList) {
                if (!(te instanceof TileReplicator) || te.isInvalid()) {
                    continue;
                }
                TileReplicator machine = (TileReplicator) te;
                Watch watch = MACHINES.get(machine);
                if (watch == null) {
                    watch = new Watch();
                    MACHINES.put(machine, watch);
                }
                watch.update(machine);
                // Гибель — не длящаяся беда, а итог: о ней сообщается один раз и не повторяется ни в сводках при смене
                // измерения, ни после снятия шлема, перезахода или загрузки сохранения (Антон 17.11: «Клон погиб»
                // приходил при каждом переходе между мирами, потом — после загрузки сейва). Отметку хранит машина.
                // Гибель закончилась (новый рост, корзина, другой шаблон) — следующая снова новость.
                if (watch.kind != DEAD) {
                    machine.setDeathTold(false);
                }
                UUID owner = machine.getPatternOwner();
                if (owner != null) {
                    Seen seen = new Seen(machine, watch, dim, dimName);
                    byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(seen);
                }
            }
        }
        Set<UUID> online = new HashSet<>();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUniqueID();
            online.add(id);
            Map<String, Integer> known = KNOWN.get(id);
            Map<String, Integer> now = new HashMap<>();
            List<Seen> told = new ArrayList<>();
            ItemStack head = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
            boolean linked = NeuroLink.linksMind(head);
            if (linked ? LINKED.add(id) : LINKED.remove(id)) {
                // по журналу видно, почему пришла сводка: шлем сняли и надели (проверка Антона 17.11)
                if (MRConfig.GENERAL.debugLog) {
                    MatterRebirth.LOG.info("[MR-debug] neuro link of {}: {}", player.getName(), linked ? "on" : "off");
                }
            }
            if (linked) {
                for (Seen s : byOwner.getOrDefault(id, Collections.emptyList())) {
                    int kind = s.kindFor(player.dimension);
                    if (kind == NONE) {
                        continue;
                    }
                    if (kind == DEAD) {
                        if (s.machine.isDeathTold()) {
                            continue; // об этой гибели уже сообщено
                        }
                        s.machine.setDeathTold(true);
                    }
                    now.put(s.key, kind);
                    Integer was = known == null ? null : known.get(s.key);
                    if (was == null || was != kind) {
                        s.told = kind;
                        told.add(s);
                    }
                }
            }
            if (now.isEmpty()) {
                KNOWN.remove(id);
            } else {
                KNOWN.put(id, now);
            }
            if (!told.isEmpty()) {
                send(player, told);
            }
            updatePanel(player, NeuroLink.isNeuroHelmet(head), byOwner.getOrDefault(id, Collections.emptyList()));
        }
        KNOWN.keySet().retainAll(online);
        LINKED.retainAll(online);
        PANEL_SENT.keySet().retainAll(online);
        PANEL_LOGGED.keySet().retainAll(online);
    }

    /** Список клонов панели игроку в нейрошлеме — если изменился; снял нейрошлем — один пустой список. */
    private static void updatePanel(EntityPlayerMP player, boolean neuroHelmet, List<Seen> seen) {
        UUID id = player.getUniqueID();
        if (!neuroHelmet) {
            if (PANEL_SENT.remove(id) != null) {
                MRNetwork.CHANNEL.sendTo(new CloneListMessage(new ArrayList<>()), player);
                PANEL_LOGGED.remove(id);
                debugPanel(player, "off");
            }
            return;
        }
        List<CloneListMessage.Entry> list = new ArrayList<>();
        for (Seen s : seen) {
            CloneListMessage.Entry e = s.panelEntry(player.dimension);
            if (e != null) {
                list.add(e);
            }
        }
        list.sort(PANEL_ORDER);
        if (list.size() > CloneListMessage.MAX_CLONES) {
            list = new ArrayList<>(list.subList(0, CloneListMessage.MAX_CLONES));
        }
        if (list.equals(PANEL_SENT.get(id))) {
            return;
        }
        PANEL_SENT.put(id, list);
        MRNetwork.CHANNEL.sendTo(new CloneListMessage(list), player);
        if (MRConfig.GENERAL.debugLog) {
            // проценты роста меняются часто — в журнал только состав и состояния
            StringBuilder b = new StringBuilder().append(list.size()).append(" clone(s)");
            for (CloneListMessage.Entry e : list) {
                b.append(", ").append(e.status == CloneListMessage.NO_LINK ? "NO_LINK"
                        : e.dead ? "DEAD" : TileReplicator.Status.values()[e.status].name())
                        .append(" stage ").append(e.stage).append(" at ").append(e.x).append(' ').append(e.y)
                        .append(' ').append(e.z).append(" (").append(e.dimName).append(')');
            }
            String summary = b.toString();
            if (!summary.equals(PANEL_LOGGED.put(id, summary))) {
                debugPanel(player, summary);
            }
        }
    }

    private static void debugPanel(EntityPlayerMP player, String text) {
        if (MRConfig.GENERAL.debugLog) {
            MatterRebirth.LOG.info("[MR-debug] neuro panel of {}: {}", player.getName(), text);
        }
    }

    /** Одна карточка на вид беды: число клонов, причины вместе, место ближайшего к игроку (своё измерение первым). */
    private static void send(EntityPlayerMP player, List<Seen> told) {
        List<CloneAlertMessage.Card> cards = new ArrayList<>();
        for (int kind : ORDER) {
            Seen nearest = null;
            double best = Double.MAX_VALUE;
            int count = 0;
            int reason = 0;
            for (Seen s : told) {
                if (s.told != kind) {
                    continue;
                }
                count++;
                if (kind == HEALTH) {
                    reason |= s.watch.reason;
                }
                BlockPos pos = s.machine.getPos();
                double d = s.dim == player.dimension ? player.getDistanceSq(pos) : Double.MAX_VALUE / 2;
                if (nearest == null || d < best) {
                    nearest = s;
                    best = d;
                }
            }
            if (nearest == null) {
                continue;
            }
            BlockPos pos = nearest.machine.getPos();
            cards.add(new CloneAlertMessage.Card(kind, reason, count, nearest.dimName, pos.getX(), pos.getY(), pos.getZ()));
            MatterRebirth.LOG.info("Clone alert to {}: {}{}, {} clone(s), nearest at {} {} {} ({})", player.getName(),
                    kindName(kind), kind == HEALTH ? reasonName(reason) : "", count, pos.getX(), pos.getY(), pos.getZ(),
                    nearest.dimName);
        }
        if (!cards.isEmpty()) {
            MRNetwork.CHANNEL.sendTo(new CloneAlertMessage(cards), player);
        }
    }

    private static String kindName(int kind) {
        switch (kind) {
            case HEALTH:
                return "losing health";
            case ROLLBACK:
                return "rollback";
            case DEAD:
                return "died";
            case LINK_LOST:
                return "link lost";
            default:
                return "none";
        }
    }

    private static String reasonName(int reason) {
        return reason == NO_UU ? " (no UU)" : reason == NO_EU ? " (no EU)" : " (no UU and EU)";
    }

    /** Машина клона этого игрока на этой проверке. */
    private static final class Seen {
        final TileReplicator machine;
        final Watch watch;
        final int dim;
        final String dimName;
        final String key;
        int told;

        Seen(TileReplicator machine, Watch watch, int dim, String dimName) {
            this.machine = machine;
            this.watch = watch;
            this.dim = dim;
            this.dimName = dimName;
            this.key = dim + ":" + machine.getPos().toLong();
        }

        /**
         * Строка панели для игрока в этом измерении; null — машина не показывается (в камере пусто или машина в
         * другом измерении без якоря). Якорь без энергии — «нет связи»: состояние клона не передаётся.
         */
        CloneListMessage.Entry panelEntry(int playerDim) {
            if (!machine.holdsClone()) {
                return null;
            }
            BlockPos pos = machine.getPos();
            boolean anchor = machine.hasAnchor();
            if (dim != playerDim) {
                if (!anchor) {
                    return null;
                }
                if (watch.link != LINK_OK) {
                    return new CloneListMessage.Entry(CloneListMessage.NO_LINK, false, 0, 0, 0, 0, true, dim, dimName,
                            pos.getX(), pos.getY(), pos.getZ());
                }
            }
            int stage = machine.getStage();
            return new CloneListMessage.Entry(machine.getStatus().ordinal(), machine.isDead(), stage,
                    stage >= TileReplicator.STAGES ? 100 : (int) Math.floor(machine.getStageProgress() * 100),
                    (int) Math.round(machine.getTotalGrowth() * 1000), machine.getHealthShown(), anchor, dim, dimName,
                    pos.getX(), pos.getY(), pos.getZ());
        }

        /** О какой беде этой машины должен знать игрок в этом измерении; NONE — сообщать нечего или нет связи. */
        int kindFor(int playerDim) {
            if (dim == playerDim) {
                return watch.kind;
            }
            if (watch.link == LINK_OK) {
                return watch.kind;
            }
            // погибшему клону связь уже не нужна: «связь прервана» о нём не сообщается
            return watch.link == LINK_DOWN && watch.kind != DEAD && machine.holdsClone() ? LINK_LOST : NONE;
        }
    }

    /** Сглаженная беда и связь одной машины. */
    private static final class Watch {
        int kind = NONE;
        int quiet;
        int reason = NO_EU;
        int link = LINK_NONE;
        int linkQuiet;

        void update(TileReplicator machine) {
            int raw = machine.getTrouble();
            if (raw != NONE) {
                if (raw != kind) {
                    debug(machine, "trouble " + kindName(raw) + " started" + (kind != NONE ? " (was " + kindName(kind) + ")" : ""));
                    kind = raw;
                }
                quiet = 0;
                if (raw == HEALTH) {
                    reason = machine.isTroubleNoUu() ? NO_UU : NO_EU;
                }
            } else if (kind != NONE && ++quiet >= QUIET) {
                debug(machine, "trouble " + kindName(kind) + " ended");
                kind = NONE;
                quiet = 0;
            }
            if (!machine.hasAnchor()) {
                link = LINK_NONE;
            } else if (!machine.isPowered()) {
                if (link != LINK_DOWN) {
                    debug(machine, "anchor link down: no power");
                }
                link = LINK_DOWN;
                linkQuiet = 0;
            } else if (link == LINK_DOWN) {
                if (++linkQuiet >= LINK_QUIET) {
                    debug(machine, "anchor link restored");
                    link = LINK_OK;
                }
            } else {
                link = LINK_OK;
            }
        }

        private static void debug(TileReplicator machine, String what) {
            if (MRConfig.GENERAL.debugLog) {
                BlockPos pos = machine.getPos();
                MatterRebirth.LOG.info("[MR-debug {} {} {}] {}", pos.getX(), pos.getY(), pos.getZ(), what);
            }
        }
    }
}
