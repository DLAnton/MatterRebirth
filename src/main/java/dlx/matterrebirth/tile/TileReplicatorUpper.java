package dlx.matterrebirth.tile;

import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.energy.ReplicatorSink;
import dlx.matterrebirth.init.MRContent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nullable;

/**
 * Верхняя часть репликатора. Своего запаса нет: принимает энергию через свои порты (сзади и сверху)
 * и сразу передаёт её нижней части. Через те же порты трубы и автоматика работают с баком UU и слотами нижней части.
 */
public class TileReplicatorUpper extends TileEntity implements ITickable {

    private final ReplicatorSink sink = new ReplicatorSink(this, 0, true);

    @Override
    public void update() {
        sink.update(); // в сеть IC2 встаём на первом тике, когда соседи уже загружены
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

    @Nullable
    private TileReplicator lower() {
        if (world == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(pos.down());
        return te instanceof TileReplicator ? (TileReplicator) te : null;
    }

    /** Порт ли эта грань верхней части (сторона null — внутренний доступ). */
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
            return isPort(facing) && lower() != null;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            TileReplicator lower = isPort(facing) ? lower() : null;
            if (lower == null) {
                return null;
            }
            return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                    ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(lower.getAutomation())
                    : CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(lower.getTank());
        }
        return super.getCapability(capability, facing);
    }
}
