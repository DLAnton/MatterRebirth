package dlx.matterrebirth.tile;

import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.chunk.ReplicatorChunks;
import dlx.matterrebirth.energy.ReplicatorSink;
import dlx.matterrebirth.init.MRConfig;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.inventory.ReplicatorAutomation;
import dlx.matterrebirth.inventory.ReplicatorInventory;
import dlx.matterrebirth.item.ItemBloodSample;
import dlx.matterrebirth.uu.BloodPatterns;
import ic2.api.item.ElectricItem;
import ic2.api.recipe.IPatternStorage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Главная (нижняя) часть репликатора клонов: владелец (игрок, который собрал машину), запас энергии и слоты.
 * Энергию принимают обе части через порты (ReplicatorSink), хранит её эта часть.
 * Улучшения IC2: накопитель (+10000 EU запаса за штуку), трансформатор (+1 уровень напряжения за штуку),
 * ускоритель (рост быстрее в 1/0,7 раза и дороже в 1,6 раза за штуку, как в машинах IC2).
 * Резерв (накопитель 3-го уровня и выше) работает как в машинах IC2: неполный запас машины каждый тик доливается
 * из кристалла; излишек сети сверх полного запаса идёт в кристалл (ReplicatorSink). Кристалл питает всё, включая рост.
 * Бак жидкой UU на 16 вёдер, как у репликатора IC2: наполняется из капсул в слоте и через порты трубами
 * (трубы могут и откачивать). Автоматика работает со слотами только через порты (ReplicatorAutomation).
 * С выталкивателем IC2 машина сама выталкивает пустые капсулы и плоть в соседние хранилища через порты.
 * Рост клона (этап 4б, решения Антона 07.11): пять стадий снизу вверх; стадия стартует, только когда в баке есть
 * вся её UU, и забирает её сразу; без энергии выращенное откатывается (подробности у tickGrowth).
 * Поддержание готового клона (этап 4в, решения Антона 08.11): жизнь стоит 1 mB UU в игровой день и 16 EU/t; без UU
 * или без энергии клон теряет здоровье, с питанием восстанавливает (подробности у tickUpkeep). Гибель даёт 5 плоти.
 */
public class TileReplicator extends TileEntity implements ITickable {

    /**
     * Внутренний запас без улучшений, EU: как энергетический кристалл IC2. Расчёт Антона 07.11: в каждом конструкционном
     * блоке резонирующий кристалл из 4 энергетических пылей (~444 тыс. EU, как 4/9 энергокристалла), блоков два — около
     * миллиона. Запас на рост не тратится, только держит слои: 1 000 000 / 16 EU/t — около 52 минут без питания.
     */
    public static final double BASE_CAPACITY = 1000000;
    /** Прибавка запаса от одного улучшения-накопителя IC2, EU. */
    public static final double STORAGE_PER_UPGRADE = 10000;

    private final ReplicatorSink sink = new ReplicatorSink(this, BASE_CAPACITY, false);
    private final ReplicatorInventory inventory = new ReplicatorInventory(this::onInventoryChanged);
    private final ReplicatorAutomation automation = new ReplicatorAutomation(inventory);

    /** Объём бака UU, mB (как у репликатора IC2). Окно передаёт объём одним short: не больше 32767. */
    public static final int TANK_CAPACITY = 16000;

    private final FluidTank tank = new FluidTank(TANK_CAPACITY) {
        @Override
        public boolean canFillFluidType(FluidStack fluid) {
            return isUu(fluid);
        }

        @Override
        protected void onContentsChanged() {
            markDirty();
        }
    };

    /*
     * Кнопки внизу экрана (решения Антона 06.11): «Стоп» нет — без шаблона машина не растит, а шаблон и выращенное сбрасывает
     * корзина, как результат в сканере IC2; пауза и продолжение — одна кнопка; «растить заново» — отдельный переключатель.
     */

    /** Номера кнопок окна (Container.enchantItem). */
    public static final int BUTTON_PREV = 0, BUTTON_NEXT = 1, BUTTON_TRASH = 2, BUTTON_PAUSE = 3, BUTTON_REPEAT = 4;

    // --- рост: стадии снизу вверх — скелет, нервы, сосуды, мышцы, кожа (числа стартовые, решения Антона 07.11) ---

    public static final int STAGES = 5;
    /** UU на стадию, mB. */
    public static final int[] STAGE_UU = {200, 250, 150, 300, 100};
    /** Время стадии без ускорителей, тики (4, 5, 3, 6 и 2 минуты). */
    public static final int[] STAGE_TICKS = {4800, 6000, 3600, 7200, 2400};
    /** Рост, EU/t без ускорителей. */
    public static final double GROW_EU = 512;
    /** Удержание недоделанных слоёв (пауза, нет UU, мало энергии), EU/t. */
    public static final double HOLD_EU = 16;
    /** Откат без энергии: тиков прогресса за тик (вдвое медленнее роста). */
    public static final double ROLLBACK_SPEED = 0.5;
    private static final double EPS = 1e-6;

    /** Что машина делает сейчас; строка состояния окна. Порядок значений передаётся в окно числом: не менять. */
    // порядок важен: окно получает номер (ordinal); новые значения только в конец
    public enum Status { IDLE, GROWING, NEED_UU, PAUSED, LOW_POWER, ROLLBACK, READY, NO_POWER, CHARGING, OVERLOAD, DEAD }

    // --- поддержание готового клона (этап 4в, решения Антона 08.11) ---

    /** Здоровье клона, как у игрока: 20 единиц (10 сердец). */
    public static final int MAX_HEALTH = 20;
    /** Изменение здоровья за тик: 1 единица в минуту — с полного до гибели без питания 20 минут, один игровой день. */
    public static final double HEALTH_PER_TICK = 1.0 / 1200;
    /** Жизнь: 1 mB UU в игровой день (24000 тиков). */
    public static final double LIFE_UU_PER_TICK = 1.0 / 24000;
    /** У живого клона трубам не отдаются последние 10 mB (10 игровых дней жизни). */
    public static final int PIPE_KEEP_MB = 10;
    /** Гибель клона: гнилая плоть. */
    public static final int DEATH_FLESH = 5;
    /** Тревожный сигнал, пока здоровье падает: раз в 3 секунды. */
    private static final int ALARM_INTERVAL = 60;
    /** Номер события блока «тревога» (сервер → клиенты рядом). */
    public static final int EVENT_ALARM = 1;

    /** Текущая стадия 0..4; STAGES — клон готов. Стадии ниже текущей выращены целиком. */
    private int stage;
    /** Прогресс текущей стадии, тики без ускорителей (0..STAGE_TICKS[stage]). */
    private double progress;
    /**
     * Сколько UU текущей стадии внутри, доля 0..1. Стадия забирает всю UU на старте (1); откат снимает прогресс
     * и столько же «оплаты». Пока доля меньше 1, это долг: перед ростом машина добирает его из бака.
     */
    private double paid;
    /** Текущая стадия была выращена целиком и сейчас откатывается: снятая целиком, она даст плоть. */
    private boolean rollingFull;
    // считаются каждый тик, не сохраняются
    private Status status = Status.IDLE;
    private int uuShort;
    private int secondsLeft;

    /** Здоровье готового клона, 0..MAX_HEALTH. */
    private double health;
    /**
     * UU, уже взятая из бака на жизнь клона, mB (0..1). Клон берёт 1 mB вперёд и расходует его за игровой день;
     * кончился — берёт следующий; в баке пусто — голодает.
     */
    private double lifeUu;
    /** Клон погиб, «растить заново» выключено: экран пишет «Клон погиб», пока плоть не забрали или не начат новый рост. */
    private boolean dead;
    // не сохраняются: здоровье падает (для тревоги), счётчик тревоги, последний сигнал компаратора
    private boolean healthFalling;
    private int alarmTimer;
    private int lastSignal = -1;

    /** Растить заново после каждого воскрешения (иначе один раз). */
    private boolean repeat;
    /** Ручная пауза: рост стоит, выращенное держится на HOLD_EU. */
    private boolean paused;
    /** Выбранный шаблон: копия образца крови из хранилища шаблонов; машина хранит её у себя, как репликатор IC2. */
    private ItemStack pattern = ItemStack.EMPTY;

    /** Билет загрузки чанка проверяется на первом тике машины на сервере. */
    private boolean chunkTicketChecked;

    @Nullable
    private UUID ownerId;
    @Nullable
    private String ownerName;

    public ReplicatorSink getSink() {
        return sink;
    }

    public ReplicatorInventory getInventory() {
        return inventory;
    }

    public FluidTank getTank() {
        return tank;
    }

    /** Бак для труб через порты: у живого клона последние PIPE_KEEP_MB mB не откачиваются (Антон, план 4в). */
    public IFluidHandler getPipeTank() {
        return pipeTank;
    }

    private final IFluidHandler pipeTank = new IFluidHandler() {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            return tank.getTankProperties();
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return tank.fill(resource, doFill);
        }

        @Override
        @Nullable
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            int max = pipeDrainable();
            if (resource == null || max <= 0) {
                return null;
            }
            return tank.drain(new FluidStack(resource, Math.min(resource.amount, max)), doDrain);
        }

        @Override
        @Nullable
        public FluidStack drain(int maxDrain, boolean doDrain) {
            int max = Math.min(maxDrain, pipeDrainable());
            return max > 0 ? tank.drain(max, doDrain) : null;
        }
    };

    private int pipeDrainable() {
        return Math.max(0, tank.getFluidAmount() - (stage >= STAGES ? PIPE_KEEP_MB : 0));
    }

    public IItemHandler getAutomation() {
        return automation;
    }

    public static boolean isUu(@Nullable FluidStack fluid) {
        return fluid != null && fluid.getFluid() != null && ReplicatorInventory.UU_FLUID.equals(fluid.getFluid().getName());
    }

    @Override
    public void update() {
        sink.update(); // в сеть IC2 встаём на первом тике, когда соседи уже загружены
        if (world.isRemote) {
            return;
        }
        if (!chunkTicketChecked) {
            chunkTicketChecked = true;
            // после перезахода билет уже восстановлен Forge (ReplicatorChunks.ticketsLoaded), тогда ничего не делается
            ReplicatorChunks.ensure(world, pos, ownerId);
        }
        // TODO(релиз): ОТЛАДКА — убрать цикл перед выпуском (Антон 07.11). Сеть даёт вход раз в тик, поэтому в ускоренных
        // повторах рост и удержание идут из запаса и кристалла: запас тает в N раз быстрее
        int speed = Math.max(1, MRConfig.GENERAL.debugReplicatorSpeed);
        for (int i = 0; i < speed; i++) {
            topUpFromReserve();
            fillFromContainer(); // до роста: только что залитая капсула оплачивает стадию в этом же тике
            tickGrowth();
            // ещё раз после работы: потраченное за тик кристалл возвращает в запас сразу, и окно видит полный запас,
            // пока кристалл не сел (проверка Антона 07.11: заряд не держался полным)
            topUpFromReserve();
        }
        eject();
        // «Клон погиб» держится, пока гнилую плоть не забрали из слота (Антон 08.11)
        if (dead && inventory.getStackInSlot(ReplicatorInventory.FLESH).isEmpty()) {
            dead = false;
            sync();
        }
        alarm();
        int signal = comparatorSignal();
        if (signal != lastSignal) {
            lastSignal = signal;
            world.updateComparatorOutputLevel(pos, MRContent.replicator);
            world.updateComparatorOutputLevel(pos.up(), MRContent.replicator);
        }
    }

    /** Пока здоровье клона падает, раз в 3 секунды тревожный сигнал IC2 у машины (первый — сразу). */
    private void alarm() {
        if (!healthFalling || stage < STAGES) {
            alarmTimer = ALARM_INTERVAL;
            return;
        }
        if (++alarmTimer >= ALARM_INTERVAL) {
            alarmTimer = 0;
            world.addBlockEvent(pos, MRContent.replicator, EVENT_ALARM, 0);
        }
    }

    /**
     * Событие блока на клиенте: тревога звучит через звуковую систему IC2 (её громкость и дальность из настроек IC2),
     * звуком остановки машин IC2. На сервере событие только подтверждается, чтобы ушло клиентам рядом.
     */
    @Override
    public boolean receiveClientEvent(int id, int type) {
        if (id != EVENT_ALARM) {
            return super.receiveClientEvent(id, type);
        }
        if (world != null && world.isRemote) {
            ic2.core.IC2.audioManager.playOnce(this, ic2.core.audio.PositionSpec.Center, "Machines/InterruptOne.ogg", true,
                    ic2.core.IC2.audioManager.getDefaultVolume());
        }
        return true;
    }

    /**
     * Сигнал компаратора (Антон 08.11): 0 — камера пуста; 1–5 — номер текущей стадии роста; живой клон — 15..10
     * по здоровью (20 — 15, каждые 4 единицы минус 1, 1–3 — 10); 9 — клон погиб.
     */
    public int comparatorSignal() {
        if (dead) {
            return 9;
        }
        if (pattern.isEmpty()) {
            return 0;
        }
        if (stage >= STAGES) {
            return 10 + Math.min(5, getHealthShown() / 4);
        }
        return hasGrowth() ? stage + 1 : 0;
    }

    // --- рост ---

    /**
     * Один тик роста. По порядку:
     * без шаблона машина ждёт; готовый клон держится (поддержание — 4в);
     * меньше HOLD_EU в запасе (и кристалл пуст: он уже долит) — выращенное откатывается;
     * пауза — держит; долг стадии добирается из бака, нет UU — держит; хватает энергии на рост — растёт, иначе держит.
     * Пока в камере пусто, удержание ничего не стоит.
     */
    private void tickGrowth() {
        uuShort = 0;
        secondsLeft = 0;
        // Энергия на шаг роста: пришедшее за тик из сети, затем верхняя половина запаса, затем кристалл резерва.
        // Запас ниже половины на рост не тратится — он держит слои при перебоях. Дошли до половины — «Диапауза», запас
        // копится до полного (charging), потом рост из запаса снова разрешён; пришедшего из сети хватает — растёт и так.
        // Решение Антона 07.11: полная молния значит «растёт», надпись не мигает. Остаток пришедшего — в запас.
        double in = sink.takeTickInput();
        if (pattern.isEmpty()) {
            storeInput(in);
            charging = false; // растить нечего: следующий рост начнётся с обычного правила половины
            status = Status.IDLE; // корзина сбрасывает шаблон вместе с выращенным: без шаблона камера пуста
            return;
        }
        if (stage >= STAGES) {
            charging = false; // клон готов: «растить заново» начнёт с обычного правила половины
            tickUpkeep(in);
            return;
        }
        if (dead) {
            storeInput(in); // камера пуста, шаблон ждёт «Начать рост»
            status = Status.DEAD;
            return;
        }
        int oc = overclockers();
        double eu = growEu(oc);
        double half = sink.getCapacity() / 2;
        // Полнота считается вместе с пришедшим за этот тик: удержание слоёв (16 EU/t) снимается каждый тик после зарядки,
        // и без учёта входа запас в момент проверки всегда на 16 EU ниже полного (проверка Антона 07.11: 999984 / 1000000)
        if (charging && sink.getEnergyStored() + in >= sink.getCapacity() - EPS) {
            charging = false; // запас снова полон
        }
        boolean neverFromStore = false; // рост дороже, чем может дать даже полный запас (много ускорителей)
        if (canGrowNow()) {
            double fromStore = charging ? 0 : Math.max(0, sink.getEnergyStored() - half);
            double need = Math.max(0, eu - in);
            double store = Math.min(need, fromStore);
            if (in + store + reserveCanGive(need - store) >= eu - EPS) {
                payDebt();
                if (in >= eu) {
                    storeInput(in - eu);
                } else {
                    use(store);
                    dischargeReserve(need - store);
                }
                if (sink.getEnergyStored() <= half + EPS && in < eu) {
                    charging = true; // верхняя половина израсходована: дальше копим до полного
                }
                grow(oc);
                return;
            }
            if (!charging) {
                charging = true; // запас у половины, а сети и кристалла мало
            }
            // верхняя половина полного запаса вместе с кристаллом не покроет шаг (с базой 1 000 000 это от 15 ускорителей):
            // зарядка до полного не поможет: «Перегрузка» (Антон 07.11) — убрать ускорители, добавить хранение или поднять вход
            neverFromStore = in + (sink.getCapacity() - half) + reserveCanGive(need) < eu - EPS;
        }
        // запас вместе с пришедшим за тик: удержание берётся сначала из входа (holdFrom), запас трогается только при нехватке
        double stored = Math.min(sink.getCapacity(), sink.getEnergyStored() + in);
        boolean growth = hasGrowth();
        if (stored < HOLD_EU) {
            storeInput(in);
            stored = sink.getEnergyStored();
            if (growth) {
                status = Status.ROLLBACK;
                // остатка меньше удержания не хватает ни на что: он уходит на попытку удержать слои, экран гаснет
                // (иначе несколько EU висели бы в запасе вечно и экран не выключался, проверка Антона 07.11)
                use(stored);
                rollback();
            } else {
                status = stored < 1 ? Status.NO_POWER : paused ? Status.PAUSED : Status.LOW_POWER;
            }
            return;
        }
        if (paused) {
            // на паузе запас тоже может уйти до половины (сеть слабее 16 EU/t удержания): машина сама переходит
            // в «Диапаузу» и копит до полного, потом снова «Пауза» (Антон 07.11). Без выращенного удерживать нечего —
            // шаблон ждёт «Начать рост» как обычно
            if (growth && stored <= half + EPS) {
                charging = true;
            }
            status = growth && charging ? Status.CHARGING : Status.PAUSED;
            holdFrom(in, growth);
            return;
        }
        if (debt() > tank.getFluidAmount()) {
            status = Status.NEED_UU;
            uuShort = debt() - tank.getFluidAmount();
            holdFrom(in, growth);
            return;
        }
        // UU есть, но питания мало и запас у половины: рост остановлен, запас копится до полного, слои держатся из него
        // (отдельное состояние с пояснением — просьба Антона 07.11)
        // камера пуста (стадия ещё не начата): удерживать нечего, «Диапауза» не к месту — «Мало EU», рост начнётся
        // после полной зарядки или от нужного входа (Антон 07.11)
        status = neverFromStore ? Status.OVERLOAD : charging && growth ? Status.CHARGING : Status.LOW_POWER;
        holdFrom(in, growth);
    }

    /**
     * Один тик жизни готового клона (Антон 08.11). Энергия 16 EU/t: из пришедшего, затем из запаса (кристалл уже долил).
     * UU 1 mB на игровой день, берётся вперёд. Есть и то и другое — здоровье растёт на 1 в минуту до 20; нет UU или
     * энергии — падает на 1 в минуту (одновременно обоих — так же), звучит тревога; дошло до нуля — клон погибает.
     * Без энергии UU не тратится.
     */
    private void tickUpkeep(double in) {
        double stored = Math.min(sink.getCapacity(), sink.getEnergyStored() + in);
        boolean fed = false;
        if (stored >= HOLD_EU - EPS) {
            holdFrom(in, true);
            if (lifeUu < LIFE_UU_PER_TICK - EPS && tank.getFluidAmount() >= 1) {
                tank.drain(1, true);
                lifeUu += 1;
            }
            if (lifeUu >= LIFE_UU_PER_TICK - EPS) {
                lifeUu = Math.max(0, lifeUu - LIFE_UU_PER_TICK);
                fed = true;
            }
            status = fed ? Status.READY : Status.NEED_UU;
            uuShort = fed ? 0 : 1;
        } else {
            // остаток меньше удержания ни на что не хватает: уходит на попытку, экран гаснет (как при откате роста)
            storeInput(in);
            use(sink.getEnergyStored());
            status = Status.NO_POWER;
        }
        healthFalling = !fed;
        health = fed ? Math.min(MAX_HEALTH, health + HEALTH_PER_TICK) : Math.max(0, health - HEALTH_PER_TICK);
        if (health <= EPS) {
            die();
            return;
        }
        markDirty();
    }

    /**
     * Гибель клона: 5 гнилой плоти, камера пуста, шаблон остаётся. С «растить заново» рост сразу начинается сначала,
     * без него — «Клон погиб» и пауза, пока игрок не начнёт рост сам (Антон 08.11).
     */
    private void die() {
        giveFlesh(DEATH_FLESH);
        stage = 0;
        progress = 0;
        paid = 0;
        rollingFull = false;
        charging = false;
        health = 0;
        lifeUu = 0;
        healthFalling = false;
        paused = !repeat;
        dead = !repeat && !inventory.getStackInSlot(ReplicatorInventory.FLESH).isEmpty();
        status = dead ? Status.DEAD : Status.IDLE;
        sync();
    }

    /** Здоровье клона для экрана и компаратора: целое, вверх (0 только у погибшего). */
    public int getHealthShown() {
        return stage >= STAGES ? Math.max(0, Math.min(MAX_HEALTH, (int) Math.ceil(health - EPS))) : 0;
    }

    /** Здоровье, 0..1 (шкала слева на экране «Поддержание»). */
    public double getHealthFraction() {
        return stage >= STAGES ? health / MAX_HEALTH : 0;
    }

    /** На сколько игровых дней хватит UU в баке (и уже взятой) клону; начатый день считается целым, 0 — UU нет совсем. */
    public int getUuDays() {
        return stage >= STAGES ? (int) Math.ceil(tank.getFluidAmount() + lifeUu - EPS) : 0;
    }

    public boolean isDead() {
        return dead;
    }

    /** Запас дошёл до половины при нехватке питания: рост из запаса запрещён, пока запас не станет полным. */
    private boolean charging;

    /** Сколько UU не хватает до оплаты текущей стадии (долг после отката или вся цена новой стадии), mB. */
    private int debt() {
        return paid < 1 - EPS ? Math.max(0, (int) Math.ceil((1 - paid) * STAGE_UU[stage] - EPS)) : 0;
    }

    /** Можно расти, если хватит энергии: есть шаблон, стадия не последняя, не пауза, UU на долг есть. */
    private boolean canGrowNow() {
        return !pattern.isEmpty() && stage < STAGES && !paused && debt() <= tank.getFluidAmount();
    }

    /** Сколько EU на рост машина просит у сети сверх места в запасе (ReplicatorSink). 0 — расти сейчас нельзя. */
    public double growDemand() {
        if (canGrowNow()) {
            return growEu(overclockers());
        }
        // расти нельзя, но есть что держать: сеть отдаёт 16 EU/t удержания прямо во вход, запас остаётся полным
        // (иначе запас каждый тик на 16 EU ниже полного, а кристалл тратится на удержание — проверка Антона 07.11)
        return !pattern.isEmpty() && (stage >= STAGES || hasGrowth()) ? HOLD_EU : 0;
    }

    private void payDebt() {
        int debt = debt();
        if (debt > 0) {
            tank.drain(debt, true);
        }
        if (paid < 1) {
            paid = 1;
            markDirty();
        }
    }

    /** Один шаг роста (энергия уже списана). */
    private void grow(int oc) {
        double speed = Math.pow(0.7, -oc);
        double total = STAGE_TICKS[stage];
        progress = Math.min(total, progress + speed);
        status = Status.GROWING;
        secondsLeft = (int) Math.ceil((total - progress) / speed / 20 - EPS);
        if (progress >= total - EPS) {
            stage++;
            progress = 0;
            paid = 0;
            rollingFull = false;
            if (stage >= STAGES) {
                status = Status.READY;
                health = MAX_HEALTH; // готовый клон здоров; первый 1 mB на жизнь берётся следующим тиком
                lifeUu = 0;
            }
            sync(); // новая стадия на клиент (окно, позже модель)
        } else {
            markDirty();
        }
    }

    /** Пришедшее из сети — в запас; что не влезло — в кристалл резерва (остальное возвращать уже некуда). */
    private void storeInput(double amount) {
        if (amount <= 0) {
            return;
        }
        double room = Math.max(0, sink.getCapacity() - sink.getEnergyStored());
        double put = Math.min(room, amount);
        sink.setEnergyStored(sink.getEnergyStored() + put);
        if (amount > put) {
            chargeReserveFromNet(amount - put);
        }
    }

    /** Сколько кристалл резерва может отдать прямо сейчас (проверка без списания). */
    private double reserveCanGive(double amount) {
        ItemStack reserve = inventory.getStackInSlot(ReplicatorInventory.RESERVE);
        if (amount <= 0 || reserve.isEmpty()) {
            return 0;
        }
        return ElectricItem.manager.discharge(reserve, amount, sink.getSinkTier(), true, false, true);
    }

    private void dischargeReserve(double amount) {
        ItemStack reserve = inventory.getStackInSlot(ReplicatorInventory.RESERVE);
        if (amount > 0 && !reserve.isEmpty()) {
            ElectricItem.manager.discharge(reserve, amount, sink.getSinkTier(), true, false, false);
            markDirty();
        }
    }

    /**
     * Откат без энергии, вдвое медленнее роста: снимается прогресс и столько же «оплаты» (потом это долг).
     * Текущая стадия дошла до нуля — неизрасходованная UU, взятая ею под ещё не выращенную часть, возвращается в бак,
     * и откат идёт в предыдущую, выращенную целиком стадию. Стадия, выращенная целиком и снятая до нуля, даёт 1 гнилую плоть;
     * частично выращенная — ничего.
     */
    private void rollback() {
        int cost = STAGE_UU[stage];
        if (progress > EPS) {
            double lost = Math.min(ROLLBACK_SPEED, progress);
            progress -= lost;
            paid = Math.max(0, paid - lost / STAGE_TICKS[stage]);
            if (progress > EPS) {
                markDirty();
                return;
            }
            progress = 0;
            if (rollingFull) {
                rollingFull = false;
                giveFlesh(1);
            }
        }
        refund((int) Math.floor(paid * cost + EPS));
        paid = 0;
        if (stage > 0) {
            stage--;
            progress = STAGE_TICKS[stage];
            paid = 1;
            rollingFull = true;
        }
        sync();
    }

    /** В камере что-то есть: выращенные слои, прогресс или уже оплаченная стадия. */
    public boolean hasGrowth() {
        return stage > 0 || progress > EPS || paid > EPS;
    }

    /**
     * Удержание за тик: сначала из пришедшего из сети, недостающее — из запаса; остаток входа — в запас.
     * Пустая камера ничего не тратит.
     */
    private void holdFrom(double in, boolean growth) {
        double take = growth ? Math.min(in, HOLD_EU) : 0;
        storeInput(in - take);
        if (growth && take < HOLD_EU) {
            use(Math.min(HOLD_EU - take, sink.getEnergyStored()));
        }
    }

    private void use(double eu) {
        if (eu > 0) {
            sink.setEnergyStored(Math.max(0, sink.getEnergyStored() - eu));
        }
    }

    public int overclockers() {
        int n = 0;
        for (int i = 0; i < ReplicatorInventory.UPGRADE_COUNT; i++) {
            ItemStack stack = inventory.getStackInSlot(ReplicatorInventory.UPGRADE_FIRST + i);
            if (ReplicatorInventory.upgradeKind(stack) == ReplicatorInventory.Upgrade.OVERCLOCKER) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Гнилая плоть в свой слот; не влезла — падает рядом, не пропадает. */
    private void giveFlesh(int count) {
        if (count <= 0) {
            return;
        }
        ItemStack rest = inventory.insertInternal(ReplicatorInventory.FLESH, new ItemStack(Items.ROTTEN_FLESH, count), false);
        if (!rest.isEmpty()) {
            InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, rest);
        }
    }

    /** Вернуть UU в бак (что не влезло в полный бак, пропадает). */
    private void refund(int mb) {
        Fluid uu = FluidRegistry.getFluid(ReplicatorInventory.UU_FLUID);
        if (mb > 0 && uu != null) {
            tank.fill(new FluidStack(uu, mb), true);
        }
    }

    public int getStage() {
        return stage;
    }

    /** Прогресс текущей стадии, 0..1. */
    public double getStageProgress() {
        return stage >= STAGES ? 1 : progress / STAGE_TICKS[stage];
    }

    public Status getStatus() {
        return status;
    }

    /** Сколько UU не хватает на стадию (или на долг), mB; при состоянии NEED_UU. */
    public int getUuShort() {
        return uuShort;
    }

    /** Секунд до конца текущей стадии при нынешних ускорителях; при состоянии GROWING. */
    public int getSecondsLeft() {
        return secondsLeft;
    }

    /** Рост, EU/t, с ускорителями n штук (окно показывает в подсказке «мало энергии»). */
    public static double growEu(int overclockers) {
        return GROW_EU * Math.pow(1.6, overclockers);
    }

    /**
     * Капсула (или другой контейнер с UU) из верхнего слота сливается в бак, пустая уходит в нижний слот.
     * Берём по одной и только целиком: пока в баке нет места на всю капсулу или в нижнем слоте нет места
     * для пустой, машина ждёт. Исключение: контейнер лежит в слоте один и в нём больше UU, чем вмещает весь бак
     * (такой целиком не влезет никогда) — тогда сливаем сколько влезет, остаток ждёт в том же слоте.
     */
    private void fillFromContainer() {
        ItemStack in = inventory.getStackInSlot(ReplicatorInventory.UU_IN);
        int room = tank.getCapacity() - tank.getFluidAmount();
        FluidStack contained = in.isEmpty() ? null : FluidUtil.getFluidContained(in);
        if (room <= 0 || !isUu(contained)) {
            return;
        }
        // tryEmptyContainer работает с копией одной штуки, сам слот не трогает
        FluidActionResult test = FluidUtil.tryEmptyContainer(in, tank, room, null, false);
        if (!test.isSuccess()) {
            return; // ведро и подобные сливаются только целиком: ждём, пока освободится место
        }
        if (FluidUtil.getFluidContained(test.getResult()) != null) {
            // слилась бы только часть: ждём места на всё, кроме контейнера больше всего бака, лежащего в слоте одним
            if (in.getCount() != 1 || contained.amount <= tank.getCapacity()) {
                return;
            }
            FluidActionResult done = FluidUtil.tryEmptyContainer(in, tank, room, null, true);
            if (done.isSuccess()) {
                inventory.setStackInSlot(ReplicatorInventory.UU_IN, done.getResult().copy());
            }
            return;
        }
        if (!test.getResult().isEmpty()
                && !inventory.insertInternal(ReplicatorInventory.CELL_OUT, test.getResult(), true).isEmpty()) {
            return; // некуда положить пустую капсулу
        }
        FluidActionResult done = FluidUtil.tryEmptyContainer(in, tank, room, null, true);
        if (!done.isSuccess()) {
            return;
        }
        ItemStack left = in.copy();
        left.shrink(1);
        inventory.setStackInSlot(ReplicatorInventory.UU_IN, left.isEmpty() ? ItemStack.EMPTY : left);
        ItemStack empty = done.getResult();
        if (!empty.isEmpty()) {
            ItemStack rest = inventory.insertInternal(ReplicatorInventory.CELL_OUT, empty, false);
            if (!rest.isEmpty()) {
                // проверка выше это исключает, но предмет не должен пропасть ни при каком раскладе
                InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, rest);
            }
        }
    }

    /** Выталкиватель IC2: пустые капсулы и плоть уходят в соседние хранилища через порты. */
    private void eject() {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != MRContent.replicator) {
            return;
        }
        boolean any = false;
        boolean[] allowed = new boolean[EnumFacing.VALUES.length];
        for (int i = 0; i < ReplicatorInventory.UPGRADE_COUNT; i++) {
            ItemStack upgrade = inventory.getStackInSlot(ReplicatorInventory.UPGRADE_FIRST + i);
            if (ReplicatorInventory.upgradeKind(upgrade) != ReplicatorInventory.Upgrade.EJECTOR) {
                continue;
            }
            any = true;
            EnumFacing dir = ejectorDirection(upgrade);
            if (dir == null) {
                java.util.Arrays.fill(allowed, true);
            } else {
                allowed[dir.getIndex()] = true;
            }
        }
        if (!any) {
            return;
        }
        // порты: задняя грань обеих частей, низ нижней, верх верхней
        EnumFacing back = state.getValue(BlockReplicator.FACING).getOpposite();
        BlockPos upper = pos.up();
        ejectSlot(ReplicatorInventory.CELL_OUT, allowed, back, upper);
        ejectSlot(ReplicatorInventory.FLESH, allowed, back, upper);
    }

    private void ejectSlot(int slot, boolean[] allowed, EnumFacing back, BlockPos upper) {
        if (allowed[back.getIndex()]) {
            ejectTo(slot, pos, back);
            ejectTo(slot, upper, back);
        }
        if (allowed[EnumFacing.DOWN.getIndex()]) {
            ejectTo(slot, pos, EnumFacing.DOWN);
        }
        if (allowed[EnumFacing.UP.getIndex()]) {
            ejectTo(slot, upper, EnumFacing.UP);
        }
    }

    private void ejectTo(int slot, BlockPos from, EnumFacing side) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return;
        }
        BlockPos target = from.offset(side);
        if (!world.isBlockLoaded(target)) {
            return; // не грузим соседний чанк ради выталкивания
        }
        TileEntity te = world.getTileEntity(target);
        if (te == null || !te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite())) {
            return;
        }
        IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite());
        if (handler == null) {
            return;
        }
        // сначала проба, потом ровно столько, сколько влезет: никаких промежуточных потерь
        int fits = stack.getCount() - ItemHandlerHelper.insertItem(handler, stack.copy(), true).getCount();
        if (fits <= 0) {
            return;
        }
        ItemStack taken = inventory.extractItem(slot, fits, false);
        ItemStack rest = ItemHandlerHelper.insertItem(handler, taken, false);
        if (!rest.isEmpty()) {
            // сосед принял меньше, чем обещал: возвращаем в слот, а если некуда, роняем рядом
            ItemStack notReturned = inventory.insertInternal(slot, rest, false);
            if (!notReturned.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, notReturned);
            }
        }
    }

    /** Сторона, выбранная в выталкивателе IC2 (хранится в NBT «dir»: 1..6), или null, если «любая». */
    @Nullable
    private static EnumFacing ejectorDirection(ItemStack upgrade) {
        NBTTagCompound tag = upgrade.getTagCompound();
        int dir = tag != null ? tag.getByte("dir") : 0;
        return dir >= 1 && dir <= 6 ? EnumFacing.VALUES[dir - 1] : null;
    }

    // --- шаблон, пауза и повтор (кнопки окна) ---

    public boolean isRepeat() {
        return repeat;
    }

    public boolean isPaused() {
        return paused;
    }

    public ItemStack getPattern() {
        return pattern;
    }

    /** Ник из выбранного шаблона или null, если шаблон не выбран. */
    @Nullable
    public String getPatternName() {
        return pattern.isEmpty() ? null : ItemBloodSample.getOwnerName(pattern);
    }

    /**
     * Активна ли машина: есть заряд в её запасе или в кристалле резерва, который её поддерживает (решение Антона 06.11).
     * Иначе экран окна выключен и кнопки не работают.
     */
    public boolean isPowered() {
        if (sink.getEnergyStored() >= 1) {
            return true;
        }
        ItemStack reserve = inventory.getStackInSlot(ReplicatorInventory.RESERVE);
        return !reserve.isEmpty() && ElectricItem.manager.getCharge(reserve) >= 1;
    }

    /** Нажатие кнопки в окне (на сервере). Неизвестные номера и нажатия без заряда ничего не делают. */
    public void onButton(int id) {
        if (!isPowered()) {
            return;
        }
        if (id == BUTTON_PREV || id == BUTTON_NEXT) {
            cyclePattern(id == BUTTON_NEXT ? 1 : -1);
        } else if (id == BUTTON_TRASH) {
            reset();
        } else if (id == BUTTON_PAUSE) {
            if (canPause()) {
                paused = !paused;
                if (!paused) {
                    dead = false; // начат новый рост
                }
                sync();
            }
        } else if (id == BUTTON_REPEAT) {
            if (!pattern.isEmpty()) {
                repeat = !repeat;
                sync();
            }
        }
    }

    /*
     * Без шаблона пауза, «растить заново» и корзина не работают, живы только стрелки (решение Антона 07.11).
     * Окно проверяет то же самое по своим данным и рисует такие кнопки тёмными.
     */

    /** Пауза: есть шаблон и клон ещё растёт (у готового клона пауза тёмная). */
    public boolean canPause() {
        // в «Диапаузе» и «Перегрузке» рост и так стоит: пауза неактивна (Антон 07.11); со стоящей паузой статус PAUSED, снять её можно
        return !pattern.isEmpty() && stage < STAGES && status != Status.CHARGING && status != Status.OVERLOAD;
    }

    /** Корзина: есть шаблон. Если в камере что-то есть, окно сначала спрашивает подтверждение (решение Антона 07.11). */
    public boolean canReset() {
        return !pattern.isEmpty();
    }

    /** Сколько гнилой плоти даст корзина сейчас: по 1 за стадию, выращенную целиком (у готового клона — все 5). */
    public int resetFlesh() {
        // стадия, в которую откат перешёл, считается целой, только пока откат её ещё не тронул; частично снятая плоти
        // не даёт, как и при откате (Антон 07.11: иначе стадия «состояла бы из 2 плотей»)
        boolean wholeRolling = rollingFull && stage < STAGES && progress >= STAGE_TICKS[stage] - EPS;
        return Math.min(STAGES, stage + (wholeRolling ? 1 : 0));
    }

    /**
     * Корзина: убрать шаблон и выращенное. Стадии, выращенные целиком, уходят в гнилую плоть (по 1 за стадию,
     * как при откате); неизрасходованная UU текущей стадии (взятая под ещё не выращенную часть) возвращается в бак.
     * Пауза снимается вместе с ростом, «растить заново» — настройка, остаётся.
     */
    private void reset() {
        if (!canReset()) {
            return;
        }
        if (hasGrowth()) {
            if (stage < STAGES) {
                double unbuilt = paid - progress / STAGE_TICKS[stage];
                // вверх: за первые доли секунды роста стадия не теряет 1 mB (проверка Антона 07.11: 999 из 1000);
                // больше заплаченного вернуть нельзя — unbuilt ≤ paid ≤ 1, корзина разовая
                refund((int) Math.ceil(Math.max(0, unbuilt) * STAGE_UU[stage] - EPS));
            }
            giveFlesh(resetFlesh());
        }
        stage = 0;
        progress = 0;
        paid = 0;
        rollingFull = false;
        paused = false;
        charging = false;
        health = 0;
        lifeUu = 0;
        dead = false;
        pattern = ItemStack.EMPTY;
        sync();
    }

    /** Сменить шаблон стрелками можно, только пока камера пуста; иначе сначала корзина. */
    public boolean canChangePattern() {
        return !hasGrowth();
    }

    private void cyclePattern(int dir) {
        if (!canChangePattern()) {
            return;
        }
        List<ItemStack> list = findPatterns();
        if (list.isEmpty()) {
            return;
        }
        UUID current = pattern.isEmpty() ? null : ItemBloodSample.getOwnerId(pattern);
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (ItemBloodSample.getOwnerId(list.get(i)).equals(current)) {
                idx = i;
                break;
            }
        }
        int next = idx < 0 ? (dir > 0 ? 0 : list.size() - 1) : Math.floorMod(idx + dir, list.size());
        ItemStack chosen = ItemBloodSample.cleanCopy(list.get(next));
        if (!ItemStack.areItemStacksEqual(chosen, pattern)) {
            pattern = chosen;
            dead = false;
            // выбранный шаблон ждёт на паузе: стрелками можно спокойно долистать до нужного, рост (и списание UU)
            // начинается кнопкой «начать рост» (замечание Антона 07.11: иначе стадия стартовала сразу и стрелки блокировались)
            paused = true;
            sync();
        }
    }

    /**
     * Образцы крови из хранилищ шаблонов IC2, подключённых к портам (как штатный репликатор берёт шаблоны у соседнего хранилища).
     * Один шаблон на владельца: одинаковые образцы из разных хранилищ не повторяются.
     */
    public List<ItemStack> findPatterns() {
        List<ItemStack> result = new ArrayList<>();
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != MRContent.replicator) {
            return result;
        }
        EnumFacing back = state.getValue(BlockReplicator.FACING).getOpposite();
        BlockPos upper = pos.up();
        BlockPos[] neighbours = {pos.offset(back), pos.down(), upper.offset(back), upper.up()};
        List<UUID> seen = new ArrayList<>();
        for (BlockPos n : neighbours) {
            if (!world.isBlockLoaded(n)) {
                continue;
            }
            TileEntity te = world.getTileEntity(n);
            if (!(te instanceof IPatternStorage)) {
                continue;
            }
            List<ItemStack> stored = ((IPatternStorage) te).getPatterns();
            if (stored == null) {
                continue;
            }
            for (ItemStack p : stored) {
                if (BloodPatterns.isBloodPattern(p) && !seen.contains(ItemBloodSample.getOwnerId(p))) {
                    seen.add(ItemBloodSample.getOwnerId(p));
                    result.add(p);
                }
            }
        }
        return result;
    }

    /** Сохранить и разослать клиентам (шапка и кнопки окна берут данные с клиентской копии машины). */
    private void sync() {
        markDirty();
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    // --- трубы и автоматика: только через порты (сторона null — внутренний доступ, например подсказки модов) ---

    /** Порт ли эта грань нижней части. */
    private boolean isPort(@Nullable EnumFacing side) {
        if (side == null) {
            return true;
        }
        if (world == null) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() == MRContent.replicator && BlockReplicator.isPort(state, side);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return isPort(facing);
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return isPort(facing) ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(automation) : null;
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return isPort(facing) ? CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(pipeTank) : null;
        }
        return super.getCapability(capability, facing);
    }

    /**
     * Как слот разрядки в машинах IC2 (проверено по IC2: Energy.onWorldTick → InvSlotDischarge): каждый тик до работы машины
     * неполный запас доливается из кристалла резерва. Сеть восполняет запас в конце тика, поэтому при достаточном питании
     * кристалл не тратится, а при нехватке питает всё, включая рост. Уровень разрядки — уровень машины.
     */
    private void topUpFromReserve() {
        ItemStack reserve = inventory.getStackInSlot(ReplicatorInventory.RESERVE);
        double stored = sink.getEnergyStored();
        double room = sink.getCapacity() - stored;
        if (reserve.isEmpty() || room <= 0) {
            return;
        }
        double got = ElectricItem.manager.discharge(reserve, room, sink.getSinkTier(), true, false, false);
        if (got > 0) {
            sink.setEnergyStored(stored + got);
            markDirty();
        }
    }

    /**
     * Сколько ещё энергии сети может взять кристалл резерва за тик: не больше пакета его уровня
     * (по уровню самого накопителя, а не машины: с трансформаторами уровень машины растёт до 14).
     * ReplicatorSink просит у сети это сверх места в запасе; запас наполняется первым.
     */
    public double reserveDemand() {
        ItemStack reserve = inventory.getStackInSlot(ReplicatorInventory.RESERVE);
        if (reserve.isEmpty()) {
            return 0;
        }
        double packet = ic2.api.energy.EnergyNet.instance.getPowerFromTier(ReplicatorInventory.electricTier(reserve));
        return ElectricItem.manager.charge(reserve, packet, sink.getSinkTier(), false, true);
    }

    /** Излишек сети, не влезший в полный запас, заряжает кристалл резерва. Возвращает, сколько взято. */
    public double chargeReserveFromNet(double amount) {
        ItemStack reserve = inventory.getStackInSlot(ReplicatorInventory.RESERVE);
        if (reserve.isEmpty() || amount <= 0) {
            return 0;
        }
        double moved = ElectricItem.manager.charge(reserve, Math.min(amount, reserveDemand()), sink.getSinkTier(), false, false);
        if (moved > 0) {
            markDirty();
        }
        return moved;
    }

    /** Окно может изменить стопку улучшений напрямую (докладывание мышью), без уведомления инвентаря. */
    public void onUpgradesChangedExternally() {
        onInventoryChanged();
    }

    private void onInventoryChanged() {
        applyUpgrades();
        markDirty();
    }

    /** Пересчитать запас и уровень по улучшениям в слотах. */
    private void applyUpgrades() {
        int storage = 0;
        int transformers = 0;
        for (int i = 0; i < ReplicatorInventory.UPGRADE_COUNT; i++) {
            ItemStack stack = inventory.getStackInSlot(ReplicatorInventory.UPGRADE_FIRST + i);
            switch (ReplicatorInventory.upgradeKind(stack)) {
                case ENERGY_STORAGE:
                    storage += stack.getCount();
                    break;
                case TRANSFORMER:
                    transformers += stack.getCount();
                    break;
                default:
                    break;
            }
        }
        sink.setCapacity(BASE_CAPACITY + STORAGE_PER_UPGRADE * storage);
        sink.setSinkTier(Math.min(ReplicatorSink.TIER + transformers, ReplicatorInventory.MAX_TIER));
        if (sink.getEnergyStored() > sink.getCapacity()) {
            sink.setEnergyStored(sink.getCapacity()); // убрали накопитель: лишнее пропадает, как в IC2
        }
    }

    /** Выбросить содержимое слотов (слом машины). После этого слоты пусты, повторный вызов ничего не даст. */
    public void dropContents(World world, BlockPos pos) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void invalidate() {
        sink.invalidate();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        sink.onChunkUnload();
    }

    public void setOwner(EntityPlayer player) {
        ownerId = player.getUniqueID();
        ownerName = player.getName();
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3); // имя владельца на клиент
        }
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    @Nullable
    public String getOwnerName() {
        return ownerName;
    }

    public boolean isUsableByPlayer(EntityPlayer player) {
        return world.getTileEntity(pos) == this
                && player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("Inv")) {
            inventory.deserializeNBT(tag.getCompoundTag("Inv"));
        }
        applyUpgrades(); // сначала ёмкость, потом заряд, иначе заряд обрежется до базовой ёмкости
        sink.readFromNBT(tag);
        tank.readFromNBT(tag.getCompoundTag("Tank")); // в старых мирах тега нет: бак пустой
        stage = Math.max(0, Math.min(STAGES, tag.getInteger("Stage")));
        progress = stage < STAGES ? Math.max(0, Math.min(STAGE_TICKS[stage], tag.getDouble("Progress"))) : 0;
        paid = stage < STAGES ? Math.max(0, Math.min(1, tag.getDouble("Paid"))) : 0;
        rollingFull = tag.getBoolean("RollingFull");
        charging = tag.getBoolean("Charging");
        // у готового клона без записи здоровья (мир до 4в) — полное здоровье
        health = stage >= STAGES ? (tag.hasKey("Health") ? Math.max(0, Math.min(MAX_HEALTH, tag.getDouble("Health"))) : MAX_HEALTH) : 0;
        lifeUu = Math.max(0, Math.min(1, tag.getDouble("LifeUu")));
        dead = tag.getBoolean("Dead");
        repeat = tag.getBoolean("Repeat");
        paused = tag.getBoolean("Paused");
        pattern = tag.hasKey("Pattern") ? new ItemStack(tag.getCompoundTag("Pattern")) : ItemStack.EMPTY;
        if (tag.hasUniqueId("OwnerId")) {
            ownerId = tag.getUniqueId("OwnerId");
            ownerName = tag.getString("OwnerName");
        }
    }

    // --- клиенту нужны владелец и заряд для окна ---

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        sink.writeToNBT(tag);
        tag.setTag("Inv", inventory.serializeNBT());
        tag.setTag("Tank", tank.writeToNBT(new NBTTagCompound()));
        tag.setInteger("Stage", stage);
        tag.setDouble("Progress", progress);
        tag.setDouble("Paid", paid);
        tag.setBoolean("RollingFull", rollingFull);
        tag.setBoolean("Charging", charging);
        tag.setDouble("Health", health);
        tag.setDouble("LifeUu", lifeUu);
        tag.setBoolean("Dead", dead);
        tag.setBoolean("Repeat", repeat);
        tag.setBoolean("Paused", paused);
        if (!pattern.isEmpty()) {
            tag.setTag("Pattern", pattern.writeToNBT(new NBTTagCompound()));
        }
        if (ownerId != null) {
            tag.setUniqueId("OwnerId", ownerId);
            tag.setString("OwnerName", ownerName != null ? ownerName : "");
        }
        return tag;
    }
}
