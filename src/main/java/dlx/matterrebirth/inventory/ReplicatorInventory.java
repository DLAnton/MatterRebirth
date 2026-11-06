package dlx.matterrebirth.inventory;

import dlx.matterrebirth.init.MRContent;
import ic2.api.item.IC2Items;
import ic2.api.item.IElectricItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;

/**
 * Слоты репликатора клонов. Правила, что куда кладётся, здесь же; окно и автоматика пользуются ими одинаково.
 */
public class ReplicatorInventory extends ItemStackHandler {

    public static final int UU_IN = 0;
    public static final int CELL_OUT = 1;
    public static final int FLESH = 2;
    public static final int UPGRADE_FIRST = 3;
    public static final int UPGRADE_COUNT = 4;
    public static final int RESERVE = 7;
    public static final int SIZE = 8;

    /** Жидкая UU-материя IC2. */
    public static final String UU_FLUID = "ic2uu_matter";

    private final Runnable onChange;

    public ReplicatorInventory(Runnable onChange) {
        super(SIZE);
        this.onChange = onChange;
    }

    public static boolean isUpgradeSlot(int slot) {
        return slot >= UPGRADE_FIRST && slot < UPGRADE_FIRST + UPGRADE_COUNT;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == UU_IN) {
            FluidStack fluid = FluidUtil.getFluidContained(stack);
            return fluid != null && fluid.getFluid() != null && UU_FLUID.equals(fluid.getFluid().getName());
        }
        if (slot == CELL_OUT) {
            return false; // сюда кладёт только машина
        }
        if (slot == FLESH) {
            return false; // гнилую плоть кладёт только машина
        }
        if (isUpgradeSlot(slot)) {
            return upgradeKind(stack) != Upgrade.NONE;
        }
        if (slot == RESERVE) {
            // накопитель 3+ уровня, но не выше уровня машины: лазуротрон (4) только с трансформатором
            int tier = electricTier(stack);
            return tier >= 3 && tier <= machineTier();
        }
        return false;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == RESERVE ? 1 : 64;
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) {
            return stack;
        }
        if (isUpgradeSlot(slot) && upgradeKind(stack) == Upgrade.TRANSFORMER) {
            // трансформаторов имеет смысл не больше, чем нужно до предельного уровня 14 (11 штук)
            int room = MAX_TRANSFORMERS - transformerCount();
            if (room <= 0) {
                return stack;
            }
            if (stack.getCount() > room) {
                ItemStack part = stack.copy();
                part.setCount(room);
                ItemStack rest = super.insertItem(slot, part, simulate);
                ItemStack left = stack.copy();
                left.setCount(stack.getCount() - room + rest.getCount());
                return left;
            }
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (isUpgradeSlot(slot) && upgradeKind(getStackInSlot(slot)) == Upgrade.TRANSFORMER) {
            // трансформаторы, без которых не работает накопитель в резерве, вынуть нельзя, пока он там
            int spare = transformerCount() - Math.max(0, electricTier(getStackInSlot(RESERVE)) - BASE_TIER);
            if (spare <= 0) {
                return ItemStack.EMPTY;
            }
            amount = Math.min(amount, spare);
        }
        return super.extractItem(slot, amount, simulate);
    }

    /** Базовый уровень машины (HV); каждый трансформатор добавляет 1, но не выше 14. */
    public static final int BASE_TIER = 3;
    public static final int MAX_TIER = 14;
    public static final int MAX_TRANSFORMERS = MAX_TIER - BASE_TIER;

    public int transformerCount() {
        int n = 0;
        for (int i = UPGRADE_FIRST; i < UPGRADE_FIRST + UPGRADE_COUNT; i++) {
            ItemStack stack = getStackInSlot(i);
            if (upgradeKind(stack) == Upgrade.TRANSFORMER) {
                n += stack.getCount();
            }
        }
        return n;
    }

    public int machineTier() {
        return Math.min(BASE_TIER + transformerCount(), MAX_TIER);
    }

    public static int electricTier(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IElectricItem ? ((IElectricItem) stack.getItem()).getTier(stack) : 0;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChange.run();
    }

    /** Машина сама кладёт в выходные слоты (капсулы, плоть) в обход правил для игрока. */
    public ItemStack insertInternal(int slot, ItemStack stack, boolean simulate) {
        return super.insertItem(slot, stack, simulate);
    }

    public enum Upgrade { NONE, OVERCLOCKER, TRANSFORMER, ENERGY_STORAGE, EJECTOR, ANCHOR }

    public static Upgrade upgradeKind(ItemStack stack) {
        if (stack.isEmpty()) {
            return Upgrade.NONE;
        }
        if (stack.getItem() == MRContent.dimensionalAnchor) {
            return Upgrade.ANCHOR;
        }
        if (same(stack, "overclocker")) {
            return Upgrade.OVERCLOCKER;
        }
        if (same(stack, "transformer")) {
            return Upgrade.TRANSFORMER;
        }
        if (same(stack, "energy_storage")) {
            return Upgrade.ENERGY_STORAGE;
        }
        if (same(stack, "ejector")) {
            return Upgrade.EJECTOR; // сама машина выталкивает гнилую плоть только с ним (шаг 3г)
        }
        return Upgrade.NONE;
    }

    private static boolean same(ItemStack stack, String type) {
        ItemStack ref = IC2Items.getItem("upgrade", type);
        return ref != null && !ref.isEmpty() && ItemStack.areItemsEqual(ref, stack);
    }
}
