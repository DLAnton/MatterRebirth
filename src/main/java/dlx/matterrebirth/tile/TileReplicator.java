package dlx.matterrebirth.tile;

import dlx.matterrebirth.energy.ReplicatorSink;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Главная (нижняя) часть репликатора клонов: владелец (игрок, который собрал машину) и запас энергии.
 * Энергию принимают обе части через порты (ReplicatorSink), хранит её эта часть.
 * UU, плоть, клон и окно добавляются следующими шагами этапа 3 и этапом 4.
 */
public class TileReplicator extends TileEntity implements ITickable {

    /** Внутренний запас, EU. Временное значение до окна и улучшений-накопителей. */
    public static final double CAPACITY = 10000;

    private final ReplicatorSink sink = new ReplicatorSink(this, CAPACITY, false);

    @Nullable
    private UUID ownerId;
    @Nullable
    private String ownerName;

    public ReplicatorSink getSink() {
        return sink;
    }

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

    public void setOwner(EntityPlayer player) {
        ownerId = player.getUniqueID();
        ownerName = player.getName();
        markDirty();
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    @Nullable
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        sink.readFromNBT(tag);
        if (tag.hasUniqueId("OwnerId")) {
            ownerId = tag.getUniqueId("OwnerId");
            ownerName = tag.getString("OwnerName");
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        sink.writeToNBT(tag);
        if (ownerId != null) {
            tag.setUniqueId("OwnerId", ownerId);
            tag.setString("OwnerName", ownerName != null ? ownerName : "");
        }
        return tag;
    }
}
