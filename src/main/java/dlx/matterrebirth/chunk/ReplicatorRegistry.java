package dlx.matterrebirth.chunk;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.init.MRContent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Какие репликаторы собрал каждый игрок, во всех измерениях (для лимита машин на игрока).
 * Хранится в общих данных сохранения (data/matterrebirth_replicators.dat), одно на весь мир.
 */
public class ReplicatorRegistry extends WorldSavedData {

    private static final String NAME = MatterRebirth.MOD_ID + "_replicators";

    private final List<Entry> entries = new ArrayList<>();

    public ReplicatorRegistry(String name) {
        super(name);
    }

    public static ReplicatorRegistry get(World world) {
        MapStorage storage = world.getMapStorage(); // общее хранилище для всех измерений
        ReplicatorRegistry data = (ReplicatorRegistry) storage.getOrLoadData(ReplicatorRegistry.class, NAME);
        if (data == null) {
            data = new ReplicatorRegistry(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    /** Сколько машин у игрока. Записи о машинах, которых уже нет (в загруженных чанках), заодно удаляются. */
    public int count(UUID owner) {
        int n = 0;
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (!e.owner.equals(owner)) {
                continue;
            }
            if (isGone(e)) {
                it.remove();
                markDirty();
                continue;
            }
            n++;
        }
        return n;
    }

    public void add(UUID owner, int dim, BlockPos pos) {
        remove(dim, pos); // на одном месте одна машина
        entries.add(new Entry(owner, dim, pos.toImmutable()));
        markDirty();
    }

    public void remove(int dim, BlockPos pos) {
        if (entries.removeIf(e -> e.dim == dim && e.pos.equals(pos))) {
            markDirty();
        }
    }

    /** Машины на этом месте точно нет: измерение загружено, чанк загружен, а там не нижняя часть репликатора. */
    private static boolean isGone(Entry e) {
        WorldServer world = DimensionManager.getWorld(e.dim);
        if (world == null || !world.isBlockLoaded(e.pos)) {
            return false; // проверить нельзя, считаем, что машина на месте
        }
        IBlockState state = world.getBlockState(e.pos);
        return state.getBlock() != MRContent.replicator || state.getValue(BlockReplicator.PART) != BlockReplicator.Part.LOWER;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        entries.clear();
        NBTTagList list = tag.getTagList("Machines", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            if (t.hasUniqueId("Owner")) {
                entries.add(new Entry(t.getUniqueId("Owner"), t.getInteger("Dim"),
                        new BlockPos(t.getInteger("X"), t.getInteger("Y"), t.getInteger("Z"))));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Entry e : entries) {
            NBTTagCompound t = new NBTTagCompound();
            t.setUniqueId("Owner", e.owner);
            t.setInteger("Dim", e.dim);
            t.setInteger("X", e.pos.getX());
            t.setInteger("Y", e.pos.getY());
            t.setInteger("Z", e.pos.getZ());
            list.appendTag(t);
        }
        tag.setTag("Machines", list);
        return tag;
    }

    private static final class Entry {
        final UUID owner;
        final int dim;
        final BlockPos pos;

        Entry(UUID owner, int dim, BlockPos pos) {
            this.owner = Objects.requireNonNull(owner);
            this.dim = dim;
            this.pos = pos;
        }
    }
}
