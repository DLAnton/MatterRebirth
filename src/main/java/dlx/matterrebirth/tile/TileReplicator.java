package dlx.matterrebirth.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Главная (нижняя) часть репликатора клонов. Пока хранит владельца — игрока, который собрал машину.
 * Энергия, UU, плоть, клон и окно добавляются следующими шагами этапа 3 и этапом 4.
 */
public class TileReplicator extends TileEntity {

    @Nullable
    private UUID ownerId;
    @Nullable
    private String ownerName;

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
        if (tag.hasUniqueId("OwnerId")) {
            ownerId = tag.getUniqueId("OwnerId");
            ownerName = tag.getString("OwnerName");
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (ownerId != null) {
            tag.setUniqueId("OwnerId", ownerId);
            tag.setString("OwnerName", ownerName != null ? ownerName : "");
        }
        return tag;
    }
}
