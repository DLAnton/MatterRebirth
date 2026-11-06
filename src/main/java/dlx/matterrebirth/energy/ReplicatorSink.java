package dlx.matterrebirth.energy;

import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.tile.TileReplicator;
import ic2.api.energy.prefab.BasicSink;
import ic2.api.energy.tile.IEnergyEmitter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Приёмник энергии IC2 для одной части репликатора. Энергию берёт только через порты: задняя грань каждой части,
 * нижняя грань нижней и верхняя грань верхней. Уровень 3 (HV): напряжение выше IC2 обрабатывает сама,
 * по своей настройке enableEnetExplosions (взрыв или нет).
 * Запас хранит нижняя часть; приёмник верхней части передаёт всё ей.
 */
public class ReplicatorSink extends BasicSink {

    public static final int TIER = 3;

    private final TileEntity owner;
    private final boolean upper;

    public ReplicatorSink(TileEntity owner, double capacity, boolean upper) {
        super(owner, capacity, TIER);
        this.owner = owner;
        this.upper = upper;
    }

    @Override
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing side) {
        World world = owner.getWorld();
        if (world == null) {
            return false;
        }
        IBlockState state = world.getBlockState(owner.getPos());
        if (state.getBlock() != MRContent.replicator) {
            return false;
        }
        EnumFacing back = state.getValue(BlockReplicator.FACING).getOpposite();
        return side == back || side == (upper ? EnumFacing.UP : EnumFacing.DOWN);
    }

    @Override
    public double getDemandedEnergy() {
        if (!upper) {
            return super.getDemandedEnergy();
        }
        ReplicatorSink main = lowerSink();
        return main != null ? main.getDemandedEnergy() : 0;
    }

    @Override
    public double injectEnergy(EnumFacing direction, double amount, double voltage) {
        if (!upper) {
            // Берём ровно до полного запаса, остаток возвращаем сети (BasicSink IC2 принял бы пакет целиком и перелил бы запас)
            double accepted = Math.min(amount, Math.max(0, getCapacity() - getEnergyStored()));
            setEnergyStored(getEnergyStored() + accepted);
            return amount - accepted;
        }
        ReplicatorSink main = lowerSink();
        return main != null ? main.injectEnergy(direction, amount, voltage) : amount;
    }

    @Nullable
    private ReplicatorSink lowerSink() {
        World world = owner.getWorld();
        if (world == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(owner.getPos().down());
        return te instanceof TileReplicator ? ((TileReplicator) te).getSink() : null;
    }
}
