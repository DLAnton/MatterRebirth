package dlx.matterrebirth.tile;

import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.chunk.ReplicatorChunks;
import dlx.matterrebirth.energy.ReplicatorSink;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.inventory.ReplicatorAutomation;
import dlx.matterrebirth.inventory.ReplicatorInventory;
import ic2.api.item.ElectricItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
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
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Главная (нижняя) часть репликатора клонов: владелец (игрок, который собрал машину), запас энергии и слоты.
 * Энергию принимают обе части через порты (ReplicatorSink), хранит её эта часть.
 * Улучшения IC2: накопитель (+10000 EU запаса за штуку), трансформатор (+1 уровень напряжения за штуку).
 * Резерв (накопитель 3-го уровня и выше) заряжается из запаса машины.
 * Бак жидкой UU на 16 вёдер, как у репликатора IC2: наполняется из капсул в слоте и через порты трубами
 * (трубы могут и откачивать). Автоматика работает со слотами только через порты (ReplicatorAutomation).
 * С выталкивателем IC2 машина сама выталкивает пустые капсулы и плоть в соседние хранилища через порты.
 * Клон и его рост добавятся на этапе 4.
 */
public class TileReplicator extends TileEntity implements ITickable {

    /** Внутренний запас без улучшений, EU. */
    public static final double BASE_CAPACITY = 10000;
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
        chargeReserve();
        fillFromContainer();
        eject();
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
            return isPort(facing) ? CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(tank) : null;
        }
        return super.getCapability(capability, facing);
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
        tank.readFromNBT(tag.getCompoundTag("Tank")); // в старых мирах тега нет: бак пустой
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
        if (ownerId != null) {
            tag.setUniqueId("OwnerId", ownerId);
            tag.setString("OwnerName", ownerName != null ? ownerName : "");
        }
        return tag;
    }
}
