package dlx.matterrebirth.inventory;

import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Окно репликатора: слоты по рисунку концепта (скелет окна репликатора IC2 176x184) и передача заряда на клиент.
 * Слева капсула UU, пустая капсула, гнилая плоть; справа 4 улучшения и резерв; снизу инвентарь игрока.
 */
public class ContainerReplicator extends Container {

    private static final int MACHINE_SLOTS = ReplicatorInventory.SIZE;

    private final TileReplicator tile;

    // значения на клиенте
    private int energy;
    private int capacity;
    private int tier;
    private int fluid;
    private boolean powered;
    private int stage;
    private int stageMille;
    private int status;
    private int seconds;
    private int uuShort;
    private boolean growth;
    private int flesh;
    // последние отправленные значения на сервере
    private int sentEnergy = -1;
    private int sentCapacity = -1;
    private int sentTier = -1;
    private int sentFluid = -1;
    private int sentPowered = -1;
    private int sentStage = -1;
    private int sentMille = -1;
    private int sentStatus = -1;
    private int sentSeconds = -1;
    private int sentUuShort = -1;
    private int sentGrowth = -1;
    private int sentFlesh = -1;

    public ContainerReplicator(InventoryPlayer playerInv, TileReplicator tile) {
        this.tile = tile;
        ReplicatorInventory inv = tile.getInventory();
        addSlotToContainer(new SlotItemHandler(inv, ReplicatorInventory.UU_IN, 8, 8));
        addSlotToContainer(new SlotItemHandler(inv, ReplicatorInventory.CELL_OUT, 8, 62));
        addSlotToContainer(new SlotItemHandler(inv, ReplicatorInventory.FLESH, 152, 83));
        for (int i = 0; i < ReplicatorInventory.UPGRADE_COUNT; i++) {
            // слот улучшения всегда «кликабелен»: так трансформатор можно доложить мышью и в заблокированный слот,
            // а сколько можно забрать, решает сам инвентарь (нужный резерву трансформатор не отдаётся)
            addSlotToContainer(new SlotItemHandler(inv, ReplicatorInventory.UPGRADE_FIRST + i, 152, 8 + i * 18) {
                @Override
                public boolean canTakeStack(EntityPlayer playerIn) {
                    return true;
                }

                @Override
                public void onSlotChanged() {
                    tile.onUpgradesChangedExternally();
                }
            });
        }
        addSlotToContainer(new SlotItemHandler(inv, ReplicatorInventory.RESERVE, 8, 83));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlotToContainer(new Slot(playerInv, 9 + r * 9 + c, 8 + c * 18, 114 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlotToContainer(new Slot(playerInv, c, 8 + c * 18, 172));
        }
    }

    public TileReplicator getTile() {
        return tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUsableByPlayer(player);
    }

    // --- заряд на клиент: свойства окна передаются как short, поэтому делим числа на две половины по 15 бит ---

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int e = (int) tile.getSink().getEnergyStored();
        int c = (int) tile.getSink().getCapacity();
        int t = tile.getSink().getSinkTier();
        int f = tile.getTank().getFluidAmount(); // до 16000, в short помещается целиком
        int p = tile.isPowered() ? 1 : 0; // заряд в запасе или в кристалле резерва
        // рост: стадия, прогресс стадии в тысячных, состояние, секунды до конца стадии (до 360), нехватка UU (до 300 mB)
        int st = tile.getStage();
        int mille = (int) Math.floor(tile.getStageProgress() * 1000);
        int stat = tile.getStatus().ordinal();
        int sec = Math.min(Short.MAX_VALUE, tile.getSecondsLeft());
        int uu = Math.min(Short.MAX_VALUE, tile.getUuShort());
        int g = tile.hasGrowth() ? 1 : 0;
        int fl = tile.resetFlesh(); // для подтверждения корзины: сколько плоти останется
        for (IContainerListener l : listeners) {
            if (e != sentEnergy) {
                l.sendWindowProperty(this, 0, e & 0x7FFF);
                l.sendWindowProperty(this, 1, e >>> 15);
            }
            if (c != sentCapacity) {
                l.sendWindowProperty(this, 2, c & 0x7FFF);
                l.sendWindowProperty(this, 3, c >>> 15);
            }
            if (t != sentTier) {
                l.sendWindowProperty(this, 4, t);
            }
            if (f != sentFluid) {
                l.sendWindowProperty(this, 5, f);
            }
            if (p != sentPowered) {
                l.sendWindowProperty(this, 6, p);
            }
            if (st != sentStage) {
                l.sendWindowProperty(this, 7, st);
            }
            if (mille != sentMille) {
                l.sendWindowProperty(this, 8, mille);
            }
            if (stat != sentStatus) {
                l.sendWindowProperty(this, 9, stat);
            }
            if (sec != sentSeconds) {
                l.sendWindowProperty(this, 10, sec);
            }
            if (uu != sentUuShort) {
                l.sendWindowProperty(this, 11, uu);
            }
            if (g != sentGrowth) {
                l.sendWindowProperty(this, 12, g);
            }
            if (fl != sentFlesh) {
                l.sendWindowProperty(this, 13, fl);
            }
        }
        sentEnergy = e;
        sentCapacity = c;
        sentTier = t;
        sentFluid = f;
        sentPowered = p;
        sentStage = st;
        sentMille = mille;
        sentStatus = stat;
        sentSeconds = sec;
        sentUuShort = uu;
        sentGrowth = g;
        sentFlesh = fl;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int data) {
        switch (id) {
            case 0: energy = (energy & ~0x7FFF) | data; break;
            case 1: energy = (energy & 0x7FFF) | (data << 15); break;
            case 2: capacity = (capacity & ~0x7FFF) | data; break;
            case 3: capacity = (capacity & 0x7FFF) | (data << 15); break;
            case 4: tier = data; break;
            case 5: fluid = data; break;
            case 6: powered = data != 0; break;
            case 7: stage = data; break;
            case 8: stageMille = data; break;
            case 9: status = data; break;
            case 10: seconds = data; break;
            case 11: uuShort = data; break;
            case 12: growth = data != 0; break;
            case 13: flesh = data; break;
            default: break;
        }
    }

    public int getEnergy() {
        return energy;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTier() {
        return tier;
    }

    /** Активна ли машина (заряд в запасе или в кристалле резерва), на клиенте. */
    public boolean isPowered() {
        return powered;
    }

    /** Текущая стадия роста 0..4, TileReplicator.STAGES — клон готов (на клиенте). */
    public int getStage() {
        return stage;
    }

    /** Прогресс текущей стадии, 0..1 (на клиенте). */
    public float getStageProgress() {
        return stageMille / 1000f;
    }

    /** Состояние машины (на клиенте). */
    public TileReplicator.Status getStatus() {
        TileReplicator.Status[] all = TileReplicator.Status.values();
        return status >= 0 && status < all.length ? all[status] : TileReplicator.Status.IDLE;
    }

    /** Секунд до конца стадии (на клиенте). */
    public int getSecondsLeft() {
        return seconds;
    }

    /** Сколько UU не хватает, mB (на клиенте). */
    public int getUuShort() {
        return uuShort;
    }

    /** В камере что-то есть (на клиенте): стрелки шаблона тёмные. */
    public boolean hasGrowth() {
        return growth;
    }

    /** Сколько гнилой плоти даст корзина (на клиенте). */
    public int getResetFlesh() {
        return flesh;
    }

    /** Сколько UU в баке, mB (на клиенте). */
    public int getFluid() {
        return fluid;
    }

    /** Кнопки окна (стрелки шаблона, режим) приходят сюда штатным пакетом Minecraft, как кнопки стола зачарований. */
    @Override
    public boolean enchantItem(EntityPlayer player, int id) {
        if (!player.world.isRemote && tile.isUsableByPlayer(player)) {
            tile.onButton(id);
        }
        return true;
    }

    /**
     * Любой клик (обычный, Shift, цифры, перетаскивание): после него сервер заново шлёт клиенту слоты машины.
     * Кристалл резерва меняет заряд каждый тик, и сервер каждый тик шлёт его слот. Пакет, отправленный за тик до клика,
     * приходил на клиент уже после того, как клиент сам убрал кристалл, и возвращал его в слот «призраком»; а при
     * совпавшем клике сервер своих слотов не пересылает (правка 07.11 по проверке Антона: Shift-клик по кристаллу).
     */
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        ItemStack result = super.slotClick(slotId, dragType, clickType, player);
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP mp = (EntityPlayerMP) player;
            for (int i = 0; i < MACHINE_SLOTS; i++) {
                mp.connection.sendPacket(new SPacketSetSlot(windowId, i, inventorySlots.get(i).getStack().copy()));
            }
        }
        return result;
    }

    // --- Shift-клик ---

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            // из машины забираем через правила инвентаря (например, нужный резерву трансформатор не отдаётся)
            ReplicatorInventory inv = tile.getInventory();
            int slotIndex = slot.getSlotIndex();
            ItemStack can = inv.extractItem(slotIndex, stack.getCount(), true);
            if (can.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack moving = can.copy();
            if (!mergeItemStack(moving, MACHINE_SLOTS, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
            int moved = can.getCount() - moving.getCount();
            ItemStack taken = inv.extractItem(slotIndex, moved, false);
            slot.onTake(player, taken);
            return original;
        } else {
            // в машину: капсула UU, улучшения, резерв (выходные слоты не принимают)
            // кладём через правила инвентаря машины (лимиты, проверка уровня), а не простым слиянием стопок
            boolean moved = false;
            ReplicatorInventory inv = tile.getInventory();
            for (int i = 0; i < MACHINE_SLOTS && !stack.isEmpty(); i++) {
                int slotIndex = inventorySlots.get(i).getSlotIndex();
                ItemStack rest = inv.insertItem(slotIndex, stack.copy(), false); // копия: инвентарь может сохранить сам объект стопки
                if (rest.getCount() != stack.getCount()) {
                    moved = true;
                    stack.setCount(rest.getCount());
                }
            }
            if (!moved) {
                // между инвентарём и панелью быстрого доступа, как в обычных окнах
                int hotbarStart = inventorySlots.size() - 9;
                if (index < hotbarStart) {
                    if (!mergeItemStack(stack, hotbarStart, inventorySlots.size(), false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!mergeItemStack(stack, MACHINE_SLOTS, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
