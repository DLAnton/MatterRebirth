package dlx.matterrebirth.inventory;

import dlx.matterrebirth.client.GuiReplicator;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

/** Окна мода. Клиентский класс окна загружается только на клиенте, когда окно действительно открывают. */
public class MRGuiHandler implements IGuiHandler {

    public static final int REPLICATOR = 0;

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (id == REPLICATOR && te instanceof TileReplicator) {
            return new ContainerReplicator(player.inventory, (TileReplicator) te);
        }
        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (id == REPLICATOR && te instanceof TileReplicator) {
            return new GuiReplicator(player.inventory, (TileReplicator) te);
        }
        return null;
    }
}
