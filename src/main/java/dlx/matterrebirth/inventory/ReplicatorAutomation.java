package dlx.matterrebirth.inventory;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * Слоты репликатора для автоматики (трубы, воронки, выталкиватели соседних машин), только через порты.
 * Видны три первых слота машины: капсула UU (0), пустая капсула (1), гнилая плоть (2).
 * Положить можно только контейнер с UU в слот 0, забрать только из слотов 1 и 2.
 * Улучшения и резерв автоматике не видны: их меняет только игрок в окне.
 */
public class ReplicatorAutomation implements IItemHandler {

    private final ReplicatorInventory inv;

    public ReplicatorAutomation(ReplicatorInventory inv) {
        this.inv = inv;
    }

    @Override
    public int getSlots() {
        return 3; // UU_IN, CELL_OUT, FLESH идут первыми, номера совпадают с номерами в машине
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        return inRange(slot) ? inv.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot != ReplicatorInventory.UU_IN) {
            return stack;
        }
        return inv.insertItem(slot, stack, simulate); // там же проверка, что в контейнере UU
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != ReplicatorInventory.CELL_OUT && slot != ReplicatorInventory.FLESH) {
            return ItemStack.EMPTY;
        }
        return inv.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inRange(slot) ? inv.getSlotLimit(slot) : 0;
    }

    /** Без @Override: в одних сборках Forge 1.12.2 этот метод есть в IItemHandler, в других нет. */
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return slot == ReplicatorInventory.UU_IN && inv.isItemValid(slot, stack);
    }

    private static boolean inRange(int slot) {
        return slot >= 0 && slot < 3;
    }
}
