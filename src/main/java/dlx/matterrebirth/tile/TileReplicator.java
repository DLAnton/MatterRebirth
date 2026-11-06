package dlx.matterrebirth.tile;

import dlx.matterrebirth.energy.ReplicatorSink;
import dlx.matterrebirth.inventory.ReplicatorInventory;
import ic2.api.item.ElectricItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Главная (нижняя) часть репликатора клонов: владелец (игрок, который собрал машину), запас энергии и слоты.
 * Энергию принимают обе части через порты (ReplicatorSink), хранит её эта часть.
 * Улучшения IC2: накопитель (+10000 EU запаса за штуку), трансформатор (+1 уровень напряжения за штуку).
 * Резерв (накопитель 3-го уровня и выше) заряжается из запаса машины.
 * UU, плоть и клон добавляются шагом 3г и этапом 4.
 */
public class TileReplicator extends TileEntity implements ITickable {

    /** Внутренний запас без улучшений, EU. */
    public static final double BASE_CAPACITY = 10000;
    /** Прибавка запаса от одного улучшения-накопителя IC2, EU. */
    public static final double STORAGE_PER_UPGRADE = 10000;

    private final ReplicatorSink sink = new ReplicatorSink(this, BASE_CAPACITY, false);
    private final ReplicatorInventory inventory = new ReplicatorInventory(this::onInventoryChanged);

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

    @Override
    public void update() {
        sink.update(); // в сеть IC2 встаём на первом тике, когда соседи уже загружены
        if (world.isRemote) {
            return;
        }
        chargeReserve();
    }

    /** Остаток энергии из запаса уходит в резерв. Уровень зарядки равен уровню машины: лазуротрон (4) только с трансформатором. */
    private void chargeReserve() {
        ItemStack reserve = inventory.getStackInSlot(ReplicatorInventory.RESERVE);
        double stored = sink.getEnergyStored();
        // в резерв уходят только излишки: пока запас машины не полон, кристалл не заряжается,
        // а за тик отдаём не больше одного пакета своего уровня, который сеть тут же восполнит
        if (reserve.isEmpty() || stored < sink.getCapacity() - 0.5) {
            return;
        }
        // пакет считаем по уровню самого накопителя, а не машины: с трансформаторами уровень машины растёт до 14,
        // и пакет такого уровня за один тик выкачивал бы в кристалл весь запас машины
        double packet = ic2.api.energy.EnergyNet.instance.getPowerFromTier(ReplicatorInventory.electricTier(reserve));
        double moved = ElectricItem.manager.charge(reserve, Math.min(stored, packet), sink.getSinkTier(), false, false);
        if (moved > 0) {
            sink.setEnergyStored(stored - moved);
            markDirty();
        }
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
        if (ownerId != null) {
            tag.setUniqueId("OwnerId", ownerId);
            tag.setString("OwnerName", ownerName != null ? ownerName : "");
        }
        return tag;
    }
}
